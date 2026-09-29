package com.internship.service.automation;

import com.fasterxml.jackson.databind.JsonNode;
import com.internship.entity.TeacherUser;
import com.internship.service.AgentNotificationGuardService;
import com.internship.service.EmailService;
import com.internship.service.ReminderService;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static com.internship.service.automation.AutomationService.*;

/** MySQL outbox and leases are the authority. Redis and an open browser are not required. */
@Component
public class AutomationWorker {
    private final AutomationService s;
    private final ReminderService reminders;
    private final boolean enabled;
    private final ExecutorService pool=Executors.newFixedThreadPool(4,r->{Thread t=new Thread(r,"automation-delivery");t.setDaemon(true);return t;});
    private final Semaphore permits=new Semaphore(4);
    public AutomationWorker(AutomationService s,ReminderService reminders,@Value("${teacher.auto-task.v2.enabled:true}") boolean enabled){this.s=s;this.reminders=reminders;this.enabled=enabled;}
    @PreDestroy void close(){pool.shutdown();}
    @Scheduled(fixedDelayString="${teacher.auto-task.v2.scan-ms:60000}")
    public void scheduled(){
        if(!enabled)return;
        for(var row:s.db.queryForList("SELECT * FROM teacher_auto_task WHERE engine_version=2 AND status='ACTIVE' AND deleted_at IS NULL ORDER BY id")){
            try{
                TeacherUser teacher=s.teachers.findById(number(row.get("teacher_id")));s.require(s.actor(teacher),true);
                if(!Objects.equals(teacher.getTeacherType(),row.get("teacher_type")))throw new IllegalStateException("教师身份已变化，请重新核对任务。");
                JsonNode def=s.definition(row);LocalDateTime time=now();
                var currentPolicy=s.capabilities.effectiveAutomationPolicy(s.actor(teacher));
                for(JsonNode channel:def.path("notification").path("channels"))if(!currentPolicy.allowsChannel(channel.asText()))throw new IllegalStateException("通知渠道权限已撤销，请重新核对任务。");
                if(def.path("validity").hasNonNull("endAt")&&time.isAfter(LocalDateTime.parse(def.path("validity").path("endAt").asText()))){stop(row,"ENDED","任务已到结束时间。");continue;}
                if(row.get("next_run_at")==null)continue;LocalDateTime planned=dateTime(row.get("next_run_at"));if(planned.isAfter(time))continue;
                List<Long> ids=s.audience(teacher,def);var policy=s.capabilities.effectiveAutomationPolicy(s.actor(teacher));
                if(policy.maxTargetsPerRun()!=Integer.MAX_VALUE&&s.matching(teacher,def).size()>policy.maxTargetsPerRun())throw new IllegalStateException("符合条件人数超过管理员总人数限制。");
                s.tx.execute(status->{
                    var current=s.db.queryForMap("SELECT * FROM teacher_auto_task WHERE id=? FOR UPDATE",row.get("id"));
                    if(!Objects.equals(current.get("version"),row.get("version"))||!Objects.equals(current.get("next_run_at"),row.get("next_run_at"))||!"ACTIVE".equals(current.get("status")))return null;
                    String occurrence="v2:"+current.get("version")+":"+planned;
                    if(time.isAfter(planned.plusMinutes(60))&&!def.path("trigger").path("mode").asText().equals("CONDITION")){
                        s.db.update("INSERT IGNORE INTO teacher_auto_task_run(id,task_id,occurrence_key,task_version,status,started_at,updated_at,receipt,error_message) VALUES(?,?,?,?,'MISSED',?,?,?,?)",UUID.randomUUID().toString(),row.get("id"),occurrence,row.get("version"),System.currentTimeMillis(),System.currentTimeMillis(),"{}","已超过本轮执行宽限期，未补发。");
                    }else s.enqueue(current,def,occurrence,ids,def.path("trigger").path("mode").asText().equals("CONDITION")?time:planned);
                    LocalDateTime next=AutomationTiming.next(def.path("trigger"),time);
                    s.db.update("UPDATE teacher_auto_task SET next_run_at=?,last_run_at=NOW() WHERE id=?",stamp(next),row.get("id"));return null;
                });
            }catch(Exception e){stop(row,"PAUSED",clip(e.getMessage()==null?"业务数据暂不可用，请核对后重新启用。":e.getMessage(),200));}
        }
    }
    void stop(Map<String,Object> task,String state,String reason){
        s.tx.execute(tx->{s.db.update("UPDATE teacher_auto_task SET status=?,enabled=0,blocked_reason=?,version=version+1 WHERE id=? AND status='ACTIVE' AND version=?",state,reason,task.get("id"),task.get("version"));
            s.db.update("UPDATE teacher_auto_task_delivery SET status='CANCELLED',detail=?,updated_at=NOW(3) WHERE task_id=? AND task_version=? AND status IN ('QUEUED','CLAIMED')",reason,task.get("id"),task.get("version"));return null;});
    }
    @Scheduled(fixedDelayString="${teacher.auto-task.v2.poll-ms:1000}")
    public void poll(){
        if(!enabled)return;recover();
        for(var dispatch:s.db.queryForList("SELECT * FROM teacher_auto_task_dispatch WHERE enumerated=0 AND (lease_until IS NULL OR lease_until<NOW(3)) ORDER BY created_at LIMIT 4")){
            try{enumerate(dispatch);}catch(Exception e){s.db.update("UPDATE teacher_auto_task_run SET error_message=?,updated_at=? WHERE id=?",clip("名单核对未完成，稍后重试："+e.getClass().getSimpleName(),1000),System.currentTimeMillis(),dispatch.get("run_id"));}
        }
        for(var delivery:s.db.queryForList("SELECT id FROM teacher_auto_task_delivery WHERE status='QUEUED' AND next_attempt_at<=NOW(3) ORDER BY next_attempt_at,created_at LIMIT 4")){
            if(!permits.tryAcquire())break;
            pool.execute(()->{try{deliver(delivery.get("id").toString());}finally{permits.release();}});
        }
        for(var dispatch:s.db.queryForList("SELECT d.* FROM teacher_auto_task_dispatch d JOIN teacher_auto_task_run r ON r.id=d.run_id COLLATE utf8mb4_0900_ai_ci WHERE d.enumerated=1 AND r.status IN ('RUNNING','UNKNOWN','PARTIAL') ORDER BY d.created_at LIMIT 100"))summarize(dispatch);
    }
    void enumerate(Map<String,Object> d){
        String lease=UUID.randomUUID().toString(),run=d.get("run_id").toString();
        if(s.db.update("UPDATE teacher_auto_task_dispatch SET lease_token=?,lease_until=DATE_ADD(NOW(3),INTERVAL 120 SECOND) WHERE run_id=? AND enumerated=0 AND (lease_until IS NULL OR lease_until<NOW(3))",lease,run)!=1)return;
        if(now().isAfter(dateTime(d.get("valid_until")))) {
            s.db.update("UPDATE teacher_auto_task_dispatch SET enumerated=1,lease_until=NULL WHERE run_id=? AND lease_token=?",run,lease);
            s.db.update("UPDATE teacher_auto_task_run SET error_message='名单核对超过执行宽限期，未处理部分未发送' WHERE id=?",run);return;
        }
        var task=s.db.queryForMap("SELECT * FROM teacher_auto_task WHERE id=?",d.get("task_id"));
        if(!active(task,d)){s.db.update("UPDATE teacher_auto_task_dispatch SET enumerated=1,lease_until=NULL WHERE run_id=? AND lease_token=?",run,lease);s.db.update("UPDATE teacher_auto_task_run SET status='CANCELLED',error_message='任务已停止或更新' WHERE id=?",run);return;}
        TeacherUser teacher=s.teachers.findById(number(task.get("teacher_id")));s.require(s.actor(teacher),true);
        JsonNode def=s.decode(d.get("definition"));List<Long> all=s.json.convertValue(s.decode(d.get("audience")),s.json.getTypeFactory().constructCollectionType(List.class,Long.class));
        List<Long> page=all.stream().filter(id->id>number(d.get("cursor_id"))).sorted().limit(100).toList();
        List<Long> authorizedPage=page.stream().filter(id->s.scope.contains(teacher,id)).toList();
        Map<Long,Map<String,Object>> facts=s.facts.load(authorizedPage,now());
        s.tx.execute(tx->{
            var currentTask=s.db.queryForMap("SELECT * FROM teacher_auto_task WHERE id=? FOR UPDATE",task.get("id"));
            var locked=s.db.queryForMap("SELECT * FROM teacher_auto_task_dispatch WHERE run_id=? FOR UPDATE",run);if(!lease.equals(locked.get("lease_token")))return null;
            if(!active(currentTask,d))return null;
            s.db.update("UPDATE teacher_auto_task_run SET error_message=NULL WHERE id=? AND error_message LIKE '名单核对未完成%'",run);
            boolean forced=Objects.toString(d.get("occurrence_key"),"").startsWith("manual-force:");
            for(Long id:page){
                var f=facts.get(id);if(f==null)continue;var truth=AutomationRules.evaluate(def.path("condition"),f,now().toLocalDate());
                if(truth==AutomationRules.Truth.UNKNOWN){s.db.update("UPDATE teacher_auto_task_run SET error_message='部分条件无法核实，相关学生未发送' WHERE id=?",run);continue;}
                var old=s.db.queryForList("SELECT * FROM teacher_auto_task_match WHERE task_id=? AND student_id=? FOR UPDATE",task.get("id"),id);
                long episode=old.isEmpty()?1:number(old.getFirst().get("episode"));boolean matches=truth==AutomationRules.Truth.TRUE;
                if(matches&&!old.isEmpty()&&number(old.getFirst().get("active"))==0)episode++;
                s.db.update("INSERT INTO teacher_auto_task_match(task_id,student_id,active,episode,updated_at) VALUES(?,?,?,?,NOW(3)) ON DUPLICATE KEY UPDATE active=VALUES(active),episode=VALUES(episode),updated_at=NOW(3)",task.get("id"),id,matches?1:0,episode);
                if(!matches)continue;
                String matter=AutomationMatters.matter(def,f)+"@"+episode;
                for(JsonNode channel:def.path("notification").path("channels")){
                    String c=channel.asText();
                    if(!forced&&def.path("repeatPolicy").path("mode").asText().equals("ONCE_PER_MATTER")&&s.db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task_delivery WHERE task_id=? AND student_id=? AND matter_key=? AND channel=? AND status IN ('QUEUED','CLAIMED','SENDING','SENT','UNKNOWN','FAILED')",Long.class,task.get("id"),id,matter,c)>0)continue;
                    s.db.update("INSERT IGNORE INTO teacher_auto_task_delivery(id,run_id,task_id,task_version,student_id,student_name,channel,subject,body,matter_key,detail,status,next_attempt_at,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,'QUEUED',NOW(3),NOW(3),NOW(3))",UUID.randomUUID().toString(),run,task.get("id"),task.get("version"),id,Objects.toString(f.get("name"),""),c,def.path("notification").path("subject").asText(),def.path("notification").path("body").asText(),matter,forced?"手动强制重发，等待投递。":null);
                }
            }
            long cursor=page.isEmpty()?number(d.get("cursor_id")):page.getLast();boolean end=all.stream().noneMatch(id->id>cursor);
            s.db.update("UPDATE teacher_auto_task_dispatch SET cursor_id=?,enumerated=?,lease_until=NULL WHERE run_id=? AND lease_token=?",cursor,end?1:0,run,lease);return null;
        });
    }
    List<String> matters(Map<String,Object> delivery){String value=delivery.get("matter_key").toString();return Arrays.asList(value.substring(0,value.lastIndexOf('@')).split("\\|"));}
    void deliver(String id){
        String lease=UUID.randomUUID().toString();
        if(s.db.update("UPDATE teacher_auto_task_delivery SET status='CLAIMED',lease_token=?,lease_until=DATE_ADD(NOW(3),INTERVAL 60 SECOND),updated_at=NOW(3) WHERE id=? AND status='QUEUED' AND next_attempt_at<=NOW(3)",lease,id)!=1)return;
        var row=s.db.queryForMap("SELECT * FROM teacher_auto_task_delivery WHERE id=?",id);
        try{
            var task=s.db.queryForMap("SELECT * FROM teacher_auto_task WHERE id=?",row.get("task_id"));
            var dispatch=s.db.queryForMap("SELECT * FROM teacher_auto_task_dispatch WHERE run_id=?",row.get("run_id"));
            if(!active(task,row)){finishWithoutSend(row,lease,"CANCELLED","任务已停止或版本已更新。");return;}
            if(now().isAfter(dateTime(dispatch.get("valid_until")))){finishWithoutSend(row,lease,"SKIPPED","已超过本轮允许发送时间。");return;}
            TeacherUser teacher=s.teachers.findById(number(task.get("teacher_id")));
            try {s.require(s.actor(teacher),true);}catch(RuntimeException denied){stop(task,"PAUSED","教师账号或自动化权限已变化，请重新核对任务。");return;}
            if(!Objects.equals(teacher.getTeacherType(),task.get("teacher_type"))){stop(task,"PAUSED","教师身份已变化，请重新核对任务。");return;}
            if(!s.scope.contains(teacher,number(row.get("student_id")))){finishWithoutSend(row,lease,"SKIPPED","学生已不在当前负责范围。");return;}
            JsonNode def=s.decode(dispatch.get("definition"));String channel=row.get("channel").toString();var policy=s.capabilities.effectiveAutomationPolicy(s.actor(teacher));
            if(!policy.allowsChannel(channel)){stop(task,"PAUSED","通知渠道权限已撤销，请重新核对任务。");return;}
            if(policy.maxTargetsPerRun()!=Integer.MAX_VALUE&&s.matching(teacher,def).size()>policy.maxTargetsPerRun()){stop(task,"PAUSED","管理员调整了人数限制，请重新核对任务。");return;}
            if(def.path("validity").hasNonNull("endAt")&&now().isAfter(LocalDateTime.parse(def.path("validity").path("endAt").asText()))){stop(task,"ENDED","已到结束时间。");return;}
            var f=s.facts.load(List.of(number(row.get("student_id"))),now()).get(number(row.get("student_id")));
            if(f==null||AutomationRules.evaluate(def.path("condition"),f,now().toLocalDate())!=AutomationRules.Truth.TRUE){finishWithoutSend(row,lease,"SKIPPED","学生已完成事项、条件已变化或数据无法核实。");return;}
            boolean forced=Objects.toString(dispatch.get("occurrence_key"),"").startsWith("manual-force:");int cooldown=forced?0:Math.max(policy.globalCooldownMinutes(),def.path("repeatPolicy").path("cooldownMinutes").asInt(1440));
            Boolean claimed=s.tx.execute(tx->{
                var current=s.db.queryForMap("SELECT * FROM teacher_auto_task WHERE id=? FOR UPDATE",row.get("task_id"));if(!active(current,row))return false;
                var delivery=s.db.queryForMap("SELECT * FROM teacher_auto_task_delivery WHERE id=? FOR UPDATE",id);if(!"CLAIMED".equals(delivery.get("status"))||!lease.equals(delivery.get("lease_token")))return false;
                for(String key:matters(row)){
                    if(key.startsWith("appraisal:calendar:")&&s.db.queryForObject("SELECT COUNT(*) FROM agent_notification_guard WHERE student_id=? AND channel=? AND todo_id LIKE 'appraisal:%' AND todo_id NOT LIKE 'appraisal:calendar:%' AND (state IN ('UNKNOWN','PENDING') OR last_success_at>?)",Long.class,row.get("student_id"),channel,stamp(now().minusMinutes(cooldown)))>0){tx.setRollbackOnly();return false;}
                    String guard=AgentNotificationGuardService.key(number(row.get("student_id")),key,channel);
                    s.db.update("INSERT INTO agent_notification_guard(dedupe_key,student_id,todo_id,channel,state,updated_at) VALUES(?,?,?,?,'IDLE',NOW(3)) ON DUPLICATE KEY UPDATE dedupe_key=dedupe_key",guard,row.get("student_id"),key,channel);
                    var g=s.db.queryForMap("SELECT * FROM agent_notification_guard WHERE dedupe_key=? FOR UPDATE",guard);
                    if(Set.of("PENDING","UNKNOWN").contains(Objects.toString(g.get("state")))){tx.setRollbackOnly();return false;}
                    if(g.get("last_success_at")!=null&&dateTime(g.get("last_success_at")).plusMinutes(cooldown).isAfter(now())){tx.setRollbackOnly();return false;}
                }
                for(String key:matters(row))s.db.update("UPDATE agent_notification_guard SET state='PENDING',claim_token=?,task_id=?,updated_at=NOW(3) WHERE dedupe_key=?",id,row.get("task_id"),AgentNotificationGuardService.key(number(row.get("student_id")),key,channel));
                s.db.update("UPDATE teacher_auto_task_delivery SET status='SENDING',guard_token=?,attempts=attempts+1,lease_until=DATE_ADD(NOW(3),INTERVAL 120 SECOND),updated_at=NOW(3) WHERE id=?",id,id);
                if(channel.equals("STATION_REMINDER")){
                    reminders.sendReminder(number(row.get("student_id")),teacher.getId(),row.get("body").toString());
                    finish(row,"SENT","站内提醒已保存。");
                }
                return true;
            });
            if(!Boolean.TRUE.equals(claimed)){
                s.db.update("UPDATE teacher_auto_task_delivery SET status='QUEUED',detail='同事项已有提醒或正在核实，等待冷却结束',next_attempt_at=DATE_ADD(NOW(3),INTERVAL 1 MINUTE),lease_until=NULL WHERE id=? AND status='CLAIMED' AND lease_token=?",id,lease);return;
            }
            if(channel.equals("STATION_REMINDER"))return;
            var outcome=s.mail.send(id,Objects.toString(f.get("email"),null),row.get("subject").toString(),row.get("body").toString(),EmailService.senderDisplayName(teacher.getTeacherType(),teacher.getName()));
            s.tx.execute(tx->{
                finish(row,outcome.status(),outcome.detail());
                var latest=s.db.queryForMap("SELECT attempts FROM teacher_auto_task_delivery WHERE id=?",id);
                if(outcome.status().equals("FAILED")&&outcome.retryable()&&number(latest.get("attempts"))<3)s.db.update("UPDATE teacher_auto_task_delivery SET status='QUEUED',next_attempt_at=?,lease_until=NULL WHERE id=?",stamp(now().plusMinutes(number(latest.get("attempts"))==1?1:5)),id);
                return null;
            });
        }catch(Exception e){
            var current=s.db.queryForMap("SELECT status FROM teacher_auto_task_delivery WHERE id=?",id);
            if("SENDING".equals(current.get("status")))s.tx.execute(tx->{finish(row,"UNKNOWN","投递或回执保存未完成，需要核实；不会自动重发。");return null;});
            else finishWithoutSend(row,lease,"FAILED",clip(e.getMessage()==null?"执行检查未通过，尚未发送。":e.getMessage(),1000));
        }
    }
    void finish(Map<String,Object> row,String state,String detail){
        if(s.db.update("UPDATE teacher_auto_task_delivery SET status=?,detail=?,lease_until=NULL,updated_at=NOW(3) WHERE id=? AND status='SENDING'",state,detail,row.get("id"))!=1)throw new IllegalStateException("发送回执状态发生变化。");
        for(String key:matters(row))if(s.db.update("UPDATE agent_notification_guard SET state=?,last_success_at=CASE WHEN ?='SENT' THEN NOW(3) ELSE last_success_at END,updated_at=NOW(3) WHERE dedupe_key=? AND claim_token=? AND state='PENDING'",state,state,AgentNotificationGuardService.key(number(row.get("student_id")),key,row.get("channel").toString()),row.get("id"))!=1)throw new IllegalStateException("通知占用记录已变化。");
    }
    void finishWithoutSend(Map<String,Object> row,String lease,String state,String detail){s.db.update("UPDATE teacher_auto_task_delivery SET status=?,detail=?,lease_until=NULL,updated_at=NOW(3) WHERE id=? AND status='CLAIMED' AND lease_token=?",state,detail,row.get("id"),lease);}
    public void recover(){
        s.db.update("UPDATE teacher_auto_task_delivery SET status='QUEUED',lease_until=NULL WHERE status='CLAIMED' AND lease_until<NOW(3)");
        for(var row:s.db.queryForList("SELECT * FROM teacher_auto_task_delivery WHERE status='SENDING' AND lease_until<NOW(3) LIMIT 100"))s.tx.execute(tx->{
            var locked=s.db.queryForMap("SELECT * FROM teacher_auto_task_delivery WHERE id=? FOR UPDATE",row.get("id"));
            if("SENDING".equals(locked.get("status"))&&dateTime(locked.get("lease_until")).isBefore(now()))finish(locked,"UNKNOWN","执行中断，邮件是否被受理待核实。");return null;});
    }
    void summarize(Map<String,Object> d){
        Map<String,Long> counts=new HashMap<>();for(var r:s.db.queryForList("SELECT status,COUNT(*) AS amount FROM teacher_auto_task_delivery WHERE run_id=? GROUP BY status",d.get("run_id")))counts.put(r.get("status").toString(),number(r.get("amount")));
        if(List.of("QUEUED","CLAIMED","SENDING").stream().anyMatch(k->counts.getOrDefault(k,0L)>0))return;
        String state=counts.getOrDefault("UNKNOWN",0L)>0?"UNKNOWN":counts.getOrDefault("FAILED",0L)>0?(counts.getOrDefault("SENT",0L)>0?"PARTIAL":"FAILED"):counts.getOrDefault("CANCELLED",0L)>0?"CANCELLED":"SUCCEEDED";
        String error=s.db.queryForObject("SELECT error_message FROM teacher_auto_task_run WHERE id=?",String.class,d.get("run_id"));
        if(error!=null&&state.equals("SUCCEEDED"))state=counts.getOrDefault("SENT",0L)>0?"PARTIAL":"FAILED";
        s.db.update("UPDATE teacher_auto_task_run SET status=?,receipt=?,updated_at=? WHERE id=?",state,s.encode(Map.of("targetCount",counts.values().stream().mapToLong(Long::longValue).sum(),"successCount",counts.getOrDefault("SENT",0L),"failedCount",counts.getOrDefault("FAILED",0L),"unknownCount",counts.getOrDefault("UNKNOWN",0L),"skippedCount",counts.getOrDefault("SKIPPED",0L),"cancelledCount",counts.getOrDefault("CANCELLED",0L),"forced",d.get("occurrence_key").toString().startsWith("manual-force:"))),System.currentTimeMillis(),d.get("run_id"));
        JsonNode def=s.decode(d.get("definition"));
        if(!state.equals("UNKNOWN")&&def.path("trigger").path("mode").asText().equals("ONCE"))s.db.update("UPDATE teacher_auto_task SET status='ENDED',enabled=0 WHERE id=? AND version=? AND next_run_at IS NULL",d.get("task_id"),d.get("task_version"));
    }
    static boolean active(Map<String,Object> task,Map<String,Object> work){return "ACTIVE".equals(task.get("status"))&&task.get("deleted_at")==null&&number(task.get("version"))==number(work.get("task_version"));}
    static LocalDateTime dateTime(Object value){return value instanceof Timestamp t?t.toLocalDateTime():(LocalDateTime)value;}
}
