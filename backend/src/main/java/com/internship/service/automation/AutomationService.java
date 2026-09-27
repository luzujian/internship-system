package com.internship.service.automation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.entity.TeacherUser;
import com.internship.service.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class AutomationService {
    final JdbcTemplate db;
    final ObjectMapper json;
    final TransactionTemplate tx;
    final AutomationDefinitions definitions;
    final AutomationFacts facts;
    final TeacherAutomationScopeService scope;
    final TeacherUserService teachers;
    final AgentCapabilityService capabilities;
    final AgentFeatureGate gate;
    final AgentActionService actions;
    final AutomationMailTransport mail;
    final TeacherPermissionService permissions;
    public AutomationService(JdbcTemplate db,ObjectMapper json,PlatformTransactionManager manager,
            AutomationDefinitions definitions,AutomationFacts facts,TeacherAutomationScopeService scope,
            TeacherUserService teachers,AgentCapabilityService capabilities,AgentFeatureGate gate,AgentActionService actions,AutomationMailTransport mail,TeacherPermissionService permissions){
        this.db=db;this.json=json;this.tx=new TransactionTemplate(manager);this.definitions=definitions;this.facts=facts;
        this.scope=scope;this.teachers=teachers;this.capabilities=capabilities;this.gate=gate;this.actions=actions;this.mail=mail;this.permissions=permissions;
    }
    public static LocalDateTime now(){return LocalDateTime.now(AutomationRules.ZONE);}
    public static long number(Object n){return n==null?0:n instanceof Boolean b?(b?1:0):((Number)n).longValue();}
    public String encode(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("任务数据无法保存。",e);}}
    public JsonNode decode(Object value){try{return value==null?json.createObjectNode():json.readTree(value.toString());}catch(Exception e){throw new IllegalStateException("任务数据暂时无法读取。",e);}}
    public Actor actor(TeacherUser teacher){
        if(teacher==null)throw AgentIdentityService.forbidden();
        var codes=permissions.getPermissionCodesByRoleCode(teacher.getTeacherType());
        return new Actor("ROLE_TEACHER_"+teacher.getTeacherType()+":"+teacher.getId()+":false:",teacher.getId(),"ROLE_TEACHER_"+teacher.getTeacherType(),teacher.getName(),teacher.getTeacherType(),positive(teacher.getDepartmentId()),positive(teacher.getDivisionId()),false,codes==null?Set.of():Set.copyOf(codes));
    }
    private Long positive(String value){try{long n=Long.parseLong(value);return n>0?n:null;}catch(Exception e){return null;}}
    public TeacherUser require(Actor actor,boolean execute){
        AgentIdentityService.requireTeacher(actor);if(actor.readOnly())throw AgentIdentityService.forbidden();gate.requireEnabled();
        TeacherUser teacher=teachers.findById(actor.id());scope.requireAuthorization(teacher);
        var policy=capabilities.effectiveAutomationPolicy(actor(teacher));
        if(!policy.canManage()||(execute&&!policy.canExecute()))throw new AgentException("FORBIDDEN","当前账号没有相应自动化权限。",403);
        return teacher;
    }
    public Map<String,Object> catalog(Actor actor){
        TeacherUser teacher=require(actor,false);Map<String,Object> result=new LinkedHashMap<>(capabilities.effectiveAutomationPolicy(actor(teacher)).toMap());
        result.put("templates",new AutomationTemplates(db,json).list(teacher));result.put("fields",AutomationRules.FIELDS);result.put("operators",AutomationRules.OPERATORS);
        result.put("recordSources",AutomationRules.RECORD_SCHEMA);result.put("scopeLabel",scope.label(teacher));result.put("timezone","Asia/Shanghai");
        result.put("emailConfigured",mail.available());result.put("deliveryMode",mail.isolated()?"LOCAL_TEST":"LIVE");return result;
    }
    public Map<String,Object> previewDraft(Actor actor,Map<String,Object> input){
        TeacherUser teacher=require(actor,false);String message=Objects.toString(input.get("message"),"");
        String draftId=input.get("draftId")==null?UUID.randomUUID().toString():input.get("draftId").toString();
        Map<String,Object> old=null;
        if(input.get("draftId")!=null){
            old=draft(actor,draftId);if(number(old.get("revision"))!=number(input.get("revision")))throw conflict("草稿已在别处更新，请重新核对后再确认；当前内容仍保留。");
            if(input.containsKey("sessionId")&&!Objects.equals(old.get("session_id"),input.get("sessionId")))throw conflict("当前会话与草稿所属会话不一致，请重新打开该任务后再试。");
            JsonNode prior=decode(old.get("definition"));
            if(prior.path("_closed").asBoolean())throw conflict("该草稿已经确认或已关闭，请重新发起任务。");
            if(prior.hasNonNull("_actionId")) {
                var state=actions.get(actor,prior.path("_actionId").asText());
                if(Set.of("RUNNING","UNKNOWN").contains(state.status()))throw new AgentException("ACTION_PENDING","上次确认结果正在核实，请到任务管理查看。",409);
                if("SUCCEEDED".equals(state.status()))throw conflict("该草稿对应的任务已经创建成功，请到任务管理查看。");
            }
        }
        JsonNode previous=old==null?null:decode(old.get("definition"));
        if (previous==null && input.get("taskId") instanceof Number taskId) previous=definition(owned(actor,taskId.longValue()));
        ObjectNode definition=input.get("definition")!=null?json.valueToTree(input.get("definition")):definitions.parse(message,previous);
        if(definition.path("audience").isObject())((ObjectNode)definition.path("audience")).remove("frozenStudentIds");
        List<String> issues=new ArrayList<>(definitions.issues(definition));
        resolveTargets(teacher,definition,issues);
        long revision=old==null?1:number(old.get("revision"))+1;
        definition.remove(List.of("_actionId","_closed"));definition.put("_draftId",draftId);definition.put("_draftRevision",revision);
        if(input.get("taskId") instanceof Number taskId)definition.put("_taskId",taskId.longValue());
        String session=Objects.toString(input.get("sessionId"),"manager");if(session.length()>100)throw conflict("会话标识过长，请重新打开页面后再试。");
        if(old==null)db.update("INSERT INTO teacher_auto_task_draft(id,teacher_id,teacher_type,session_id,revision,source_text,definition,issues,updated_at) VALUES(?,?,?,?,?,?,?,?,NOW(3))",draftId,teacher.getId(),teacher.getTeacherType(),session,revision,message,encode(definition),encode(issues));
        else if(db.update("UPDATE teacher_auto_task_draft SET revision=?,source_text=?,definition=?,issues=?,updated_at=NOW(3) WHERE id=? AND teacher_id=? AND revision=?",revision,message,encode(definition),encode(issues),draftId,teacher.getId(),revision-1)!=1)throw conflict("草稿刚刚被其他操作更新，请重新核对后再确认。");
        Map<String,Object> result=describe(definition);result.put("draftId",draftId);result.put("revision",revision);result.put("definition",definition);result.put("questions",issues);
        result.put("message",message);result.put("channelAvailable",true);result.put("needsConfirm",issues.isEmpty());
        if(issues.isEmpty())result.putAll(preview(actor,definition,input.get("taskId"),"CREATE",false,false));
        if(result.get("actionId")!=null){definition.put("_actionId",result.get("actionId").toString());db.update("UPDATE teacher_auto_task_draft SET definition=? WHERE id=? AND revision=?",encode(definition),draftId,revision);}
        return result;
    }
    private void resolveTargets(TeacherUser teacher,ObjectNode def,List<String> issues){
        ObjectNode audience=def.path("audience").isObject()?(ObjectNode)def.path("audience"):json.createObjectNode();def.set("audience",audience);
        Set<Long> ids=new LinkedHashSet<>();
        List<String> unresolved=new ArrayList<>();
        if(audience.path("studentIds").isArray())audience.path("studentIds").forEach(v->{if(v.canConvertToLong()&&v.asLong()>0)ids.add(v.asLong());else issues.add("学生选择无效。");});
        if(audience.path("queries").isArray())for(JsonNode q:audience.path("queries")){
            var matches=scope.matchStudents(teacher,q.asText());
            if(matches.size()!=1){issues.add(matches.isEmpty()?"负责范围内没有匹配的学生："+q.asText():"学生重名，请改用用户名或学号："+q.asText());unresolved.add(q.asText());}
            else ids.add(matches.getFirst().getId());
        }
        for(Long id:ids)if(!scope.contains(teacher,id))issues.add("所选学生不在当前负责范围内。");
        audience.set("studentIds",json.valueToTree(ids));audience.set("queries",json.valueToTree(unresolved));
    }
    public Map<String,Object> draft(Actor actor,String id){
        require(actor,false);var rows=db.queryForList("SELECT * FROM teacher_auto_task_draft WHERE id=? AND teacher_id=?",id,actor.id());
        if(rows.isEmpty())throw new AgentException("NOT_FOUND","任务草稿不存在。",404);return rows.getFirst();
    }
    public List<Map<String,Object>> drafts(Actor actor,String session){
        require(actor,false);var rows=db.queryForList("SELECT id,revision,definition,issues,source_text,session_id,updated_at FROM teacher_auto_task_draft WHERE teacher_id=? AND session_id=? AND COALESCE(JSON_UNQUOTE(JSON_EXTRACT(definition,'$._closed')),'false')='false' ORDER BY updated_at DESC LIMIT 20",actor.id(),session);
        rows.forEach(r->{r.put("definition",decode(r.get("definition")));r.put("issues",decode(r.get("issues")));});return rows;
    }
    public List<Long> audience(TeacherUser teacher,JsonNode def){
        Set<Long> selected=ids(def.path("audience").path("studentIds")),excluded=ids(def.path("audience").path("excludeStudentIds"));
        JsonNode frozen=def.path("audience").path("frozenStudentIds");Set<Long> snapshot=ids(frozen);
        return scope.studentIds(teacher).stream().filter(id->(selected.isEmpty()||selected.contains(id))&&!excluded.contains(id)&&(!frozen.isArray()||snapshot.contains(id))).sorted().toList();
    }
    private Set<Long> ids(JsonNode array){Set<Long> result=new HashSet<>();if(array.isArray())array.forEach(v->{if(!v.canConvertToLong()||v.asLong()<1)AutomationRules.fail("学生选择不正确。");result.add(v.asLong());});return result;}
    public List<Map<String,Object>> matching(TeacherUser teacher,JsonNode def){
        List<Long> ids=audience(teacher,def);List<Map<String,Object>> result=new ArrayList<>();LocalDateTime time=now();
        for(int at=0;at<ids.size();at+=100)for(var f:facts.load(ids.subList(at,Math.min(at+100,ids.size())),time).values()){
            var match=AutomationRules.evaluate(def.path("condition"),f,time.toLocalDate());
            if(match==AutomationRules.Truth.UNKNOWN)throw new AgentException("DATA_UNAVAILABLE","部分条件数据无法确认，请核对业务配置；未创建或发送任务。",409);
            if(match==AutomationRules.Truth.TRUE)result.add(f);
        }
        return result;
    }
    public Map<String,Object> preview(Actor actor,JsonNode def,Object taskId,String operation,boolean saveOnly,boolean force){
        TeacherUser teacher=require(actor,false);List<String> errors=new ArrayList<>(definitions.issues(def));
        if(def.isObject())resolveTargets(teacher,(ObjectNode)def,errors);
        var policy=capabilities.effectiveAutomationPolicy(actor(teacher));
        for(JsonNode c:def.path("notification").path("channels"))if(!policy.allowsChannel(c.asText()))errors.add("当前权限不允许使用所选渠道。");
        if(!saveOnly&&def.path("notification").path("channels").toString().contains("EMAIL")&&!mail.available())errors.add("邮件服务尚未配置，任务可保存草稿。");
        if(def.path("condition").toString().contains("appraisal.deadline")&&(facts.calendar()==null||facts.calendar().getAppraisalDeadline()==null))errors.add("请先设置实习鉴定表提交截止日期。");
        Map<String,Object> task=taskId==null?null:owned(actor,((Number)taskId).longValue());
        if(task!=null&&"CREATE".equals(operation))operation="UPDATE";
        if(!Set.of("CREATE","UPDATE","ENABLE","RUN","RETRY").contains(operation))throw conflict("操作类型不正确，请重新打开页面后再试。");
        if(Set.of("RUN","RETRY").contains(operation) && (task==null||!("ACTIVE".equals(task.get("status"))||("RETRY".equals(operation)&&"ENDED".equals(task.get("status")))))) errors.add("请先启用任务，再运行或重试。");
        Map<String,Object> result=describe(def);result.put("questions",errors);result.put("channelAvailable",errors.isEmpty());result.put("definition",def);result.put("scopeLabel",scope.label(teacher));
        if(!errors.isEmpty())return result;
        LocalDateTime first=firstTime(def,now());
        if(!"RUN".equals(operation)&&!"RETRY".equals(operation)&&first==null)AutomationRules.fail("计划时间已经过去，请重新选择。");
        List<Map<String,Object>> matched=matching(teacher,def);
        List<String> retryIds = List.of();
        if("RETRY".equals(operation)) {
            var failures=db.queryForList("SELECT id,student_id FROM teacher_auto_task_delivery WHERE task_id=? AND task_version=? AND status='FAILED'",taskId,task.get("version"));
            Set<Long> failedStudents=new HashSet<>();failures.forEach(f->failedStudents.add(number(f.get("student_id"))));
            matched=matched.stream().filter(f->failedStudents.contains(number(f.get("id")))).toList();
            Set<Long> eligible=new HashSet<>();matched.forEach(f->eligible.add(number(f.get("id"))));
            retryIds=failures.stream().filter(f->eligible.contains(number(f.get("student_id")))).map(f->f.get("id").toString()).toList();
            if(retryIds.isEmpty())AutomationRules.fail("没有可以重试的明确失败项。待核实结果须由管理员先核实。");
        }
        if(matched.size()>policy.maxTargetsPerRun())AutomationRules.fail("符合条件人数超过管理员设置的总人数限制，请调整范围或权限。");
        List<Map<String,Object>> sample=matched.stream().limit(20).map(f->Map.<String,Object>of("id",f.get("id"),"name",f.get("name"),"username",f.get("student.username"),"hasEmail",f.get("email")!=null&&!f.get("email").toString().isBlank())).toList();

        boolean forceRun="RUN".equals(operation)&&force&&task!=null;
        List<Map<String,Object>> skipped=new ArrayList<>();
        int forcePairs=0;
        if("RUN".equals(operation)&&task!=null){
            int cooldown=Math.max(policy.globalCooldownMinutes(),def.path("repeatPolicy").path("cooldownMinutes").asInt(1440));
            boolean appraisalRule=def.path("condition").toString().contains("appraisal");
            for(Map<String,Object> f:matched){
                long sid=number(f.get("id"));
                var oldMatch=db.queryForList("SELECT episode FROM teacher_auto_task_match WHERE task_id=? AND student_id=?",task.get("id"),sid);
                long episode=oldMatch.isEmpty()?1:number(oldMatch.getFirst().get("episode"));
                String matterBase=AutomationMatters.matter(def,f);
                String matter=matterBase+"@"+episode;
                for(JsonNode ch:def.path("notification").path("channels")){
                    String c=ch.asText();
                    boolean sentBefore=def.path("repeatPolicy").path("mode").asText().equals("ONCE_PER_MATTER")
                        &&db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task_delivery WHERE task_id=? AND student_id=? AND matter_key=? AND channel=? AND status IN ('QUEUED','CLAIMED','SENDING','SENT','UNKNOWN','FAILED')",Long.class,task.get("id"),sid,matter,c)>0;
                    var guards=db.queryForList("SELECT state,last_success_at FROM agent_notification_guard WHERE dedupe_key=?",AgentNotificationGuardService.key(sid,matterBase,c));
                    boolean verifying=!guards.isEmpty()&&Set.of("PENDING","UNKNOWN").contains(Objects.toString(guards.getFirst().get("state")));
                    boolean cooling=!verifying&&!guards.isEmpty()&&guards.getFirst().get("last_success_at")!=null
                        &&AutomationWorker.dateTime(guards.getFirst().get("last_success_at")).plusMinutes(cooldown).isAfter(now());
                    if(appraisalRule&&!verifying){
                        long appraisalBlock=db.queryForObject("SELECT COUNT(*) FROM agent_notification_guard WHERE student_id=? AND channel=? AND todo_id LIKE 'appraisal:%' AND todo_id NOT LIKE 'appraisal:calendar:%' AND (state IN ('UNKNOWN','PENDING') OR last_success_at>?)",Long.class,sid,c,stamp(now().minusMinutes(forceRun?0:cooldown)));
                        if(appraisalBlock>0){
                            boolean hard=db.queryForObject("SELECT COUNT(*) FROM agent_notification_guard WHERE student_id=? AND channel=? AND todo_id LIKE 'appraisal:%' AND todo_id NOT LIKE 'appraisal:calendar:%' AND state IN ('UNKNOWN','PENDING')",Long.class,sid,c)>0;
                            if(hard)verifying=true;
                            else if(!sentBefore)cooling=true;
                        }
                    }
                    if(verifying||sentBefore||cooling){
                        skipped.add(Map.of("name",Objects.toString(f.get("name"),""),"username",Objects.toString(f.get("student.username"),""),"channel",c,
                            "reason",verifying?"VERIFYING":sentBefore?"SENT_BEFORE":"COOLDOWN","forceable",!verifying));
                    }
                    if(!verifying)forcePairs++;
                }
            }
        }
        Map<String,Object> parameters=new LinkedHashMap<>();parameters.put("definition",def);parameters.put("operation",operation);parameters.put("taskId",taskId);
        parameters.put("taskVersion",task==null?null:task.get("version"));parameters.put("policy",policy.toMap());parameters.put("saveOnly",saveOnly);
        parameters.put("retryIds",retryIds);parameters.put("force",forceRun);
        String mode=def.path("trigger").path("mode").asText();
        if(mode.equals("ONCE")||Set.of("RUN","RETRY").contains(operation))parameters.put("audienceSnapshot",matched.stream().map(f->f.get("id")).toList());
        var action=actions.preview(actor,"TEACHER_AUTOMATION_CONFIRM",taskId==null?"teacher:"+actor.id():"teacher-task:"+taskId,parameters,task==null?null:task.get("version").toString());
        result.put("actionId",action.id());result.put("operation",operation);result.put("taskId",taskId);result.put("saveOnly",saveOnly);result.put("targetCount",matched.size());result.put("skipped",skipped);result.put("forceArmed",forceRun);result.put("forceTargetCount",forcePairs);result.put("targetLabel",scope.label(teacher));result.put("sample",sample);
        result.put("nextRunAt",first==null?null:first.toString());result.put("deliveryMode",mail.isolated()?"LOCAL_TEST":"LIVE");result.put("needsConfirm",true);return result;
    }
    public Map<String,Object> confirm(Actor actor,String actionId){
        require(actor,true);var action=actions.get(actor,actionId);Map<String,Object> p=action.parameters();
        return actions.execute(actor,actionId,"TEACHER_AUTOMATION_CONFIRM",p,()->{
            TeacherUser teacher=require(actor,true);var currentPolicy=capabilities.effectiveAutomationPolicy(actor(teacher));
            if(!json.valueToTree(currentPolicy.toMap()).equals(json.valueToTree(p.get("policy"))))throw new AgentException("POLICY_CHANGED","权限配置已变化，请重新预览。",409);
            JsonNode def=json.valueToTree(p.get("definition"));String operation=p.get("operation").toString();
            if(def.hasNonNull("_draftId")) {
                var drafts=db.queryForList("SELECT revision,definition FROM teacher_auto_task_draft WHERE id=? AND teacher_id=? FOR UPDATE",def.path("_draftId").asText(),actor.id());
                if(drafts.isEmpty()||number(drafts.getFirst().get("revision"))!=def.path("_draftRevision").asLong()||decode(drafts.getFirst().get("definition")).path("_closed").asBoolean())throw conflict("草稿已经变化或被关闭，请重新核对后再确认。");
            }
            Long id=p.get("taskId")==null?null:((Number)p.get("taskId")).longValue();
            Map<String,Object> task=id==null?null:ownedLocked(actor,id);
            if(task!=null&&number(task.get("version"))!=number(p.get("taskVersion")))throw conflict("任务刚刚被修改、暂停或重新启用，请重新打开后再试。");
            if(!definitions.issues(def).isEmpty())throw conflict("任务内容校验未通过，请重新核对后再确认。");
            if(Set.of("RUN","RETRY").contains(operation)) {
                if(task==null||!("ACTIVE".equals(task.get("status"))||("RETRY".equals(operation)&&"ENDED".equals(task.get("status")))))throw conflict("RUN".equals(operation)?"任务当前不是启用状态，请先启用后再运行。":"任务当前状态不允许重试，请刷新任务记录后再试。");
                if("RUN".equals(operation)) {
                    List<Long> snapshot=json.convertValue(p.getOrDefault("audienceSnapshot",List.of()),json.getTypeFactory().constructCollectionType(List.class,Long.class));
                    String run=enqueue(task,def,(Boolean.TRUE.equals(p.get("force"))?"manual-force:":"manual:")+actionId,snapshot,now());
                    Map<String,Object> result=view(task);result.put("runId",run);result.put("executionState","QUEUED");return result;
                }
                List<String> retryIds=json.convertValue(p.get("retryIds"),json.getTypeFactory().constructCollectionType(List.class,String.class));
                db.update("UPDATE teacher_auto_task SET status='ACTIVE',enabled=1 WHERE id=?",id);
                for(String deliveryId:retryIds) {
                    var rows=db.queryForList("SELECT * FROM teacher_auto_task_delivery WHERE id=? AND task_id=? AND task_version=? AND status='FAILED' FOR UPDATE",deliveryId,id,task.get("version"));
                    if(rows.isEmpty())throw conflict("要重试的记录状态已变化，请刷新任务记录后再试。");
                    db.update("UPDATE teacher_auto_task_delivery SET status='QUEUED',attempts=0,next_attempt_at=NOW(3),detail='已确认重试明确失败项',updated_at=NOW(3) WHERE id=?",deliveryId);
                    db.update("UPDATE teacher_auto_task_dispatch SET valid_until=DATE_ADD(NOW(3),INTERVAL 60 MINUTE) WHERE run_id=?",rows.getFirst().get("run_id"));
                    db.update("UPDATE teacher_auto_task_run SET status='RUNNING' WHERE id=?",rows.getFirst().get("run_id"));
                }
                Map<String,Object> result=view(task);result.put("executionState","QUEUED");return result;
            }
            boolean saveOnly=Boolean.TRUE.equals(p.get("saveOnly"));LocalDateTime next=firstTime(def,now());
            String mode=def.path("trigger").path("mode").asText();
            if(!saveOnly&&next==null)throw conflict("计划执行时间已经过去，请重新选择执行时间后再确认。");
            if(mode.equals("ONCE"))((ObjectNode)def.withObject("/audience")).set("frozenStudentIds",json.valueToTree(p.getOrDefault("audienceSnapshot",List.of())));
            if(saveOnly)next=null;
            if(id==null){
                db.update("INSERT INTO teacher_auto_task(teacher_id,teacher_type,name,trigger_condition,action,message_template,status,enabled,engine_version,definition,activated_at,next_run_at,create_time,update_time,version) VALUES(?,?,?,'COMPOSITE',?,?,?, ?,2,?,?,?,NOW(),NOW(),1)",teacher.getId(),teacher.getTeacherType(),def.path("name").asText(),def.path("notification").path("channels").get(0).asText(),clip(def.path("notification").path("body").asText(),500),saveOnly?"PAUSED":"ACTIVE",saveOnly?0:1,encode(def),stamp(now()),stamp(next));
                id=db.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
            }else{
                db.update("UPDATE teacher_auto_task SET name=?,definition=?,engine_version=2,status=?,enabled=?,activated_at=NOW(3),next_run_at=?,blocked_reason=NULL,version=version+1,update_time=NOW() WHERE id=?",def.path("name").asText(),encode(def),saveOnly?"PAUSED":"ACTIVE",saveOnly?0:1,stamp(next),id);
                db.update("UPDATE teacher_auto_task_delivery SET status='CANCELLED',detail='任务版本已更新',updated_at=NOW(3) WHERE task_id=? AND status IN ('QUEUED','CLAIMED')",id);
            }
            Map<String,Object> updated=owned(actor,id);long version=number(updated.get("version"));
            db.update("INSERT INTO teacher_auto_task_revision(task_id,version,definition,created_at) VALUES(?,?,?,NOW(3))",id,version,encode(def));
            if(def.hasNonNull("_draftId"))db.update("UPDATE teacher_auto_task_draft SET definition=JSON_SET(definition,'$._closed',true,'$._taskId',?),updated_at=NOW(3) WHERE id=?",id,def.path("_draftId").asText());
            return view(owned(actor,id));
        });
    }
    public LocalDateTime firstTime(JsonNode def,LocalDateTime time){
        JsonNode validity=def.path("validity");LocalDateTime start=validity.hasNonNull("startAt")?LocalDateTime.parse(validity.path("startAt").asText()):time;
        if(start.isAfter(time))time=start.minusNanos(1);
        String mode=def.path("trigger").path("mode").asText();
        LocalDateTime result=mode.equals("CONDITION")?time:AutomationTiming.next(def.path("trigger"),time);
        if(validity.hasNonNull("endAt")&&result!=null&&result.isAfter(LocalDateTime.parse(validity.path("endAt").asText())))return null;return result;
    }
    public String enqueue(Map<String,Object> task,JsonNode def,String occurrence,List<Long> audience,LocalDateTime planned){
        long id=number(task.get("id"));var existing=db.queryForList("SELECT run_id FROM teacher_auto_task_dispatch WHERE task_id=? AND occurrence_key=?",id,occurrence);
        if(!existing.isEmpty())return existing.getFirst().get("run_id").toString();
        String run=UUID.randomUUID().toString();long millis=System.currentTimeMillis();
        db.update("INSERT INTO teacher_auto_task_run(id,task_id,occurrence_key,task_version,status,started_at,updated_at,receipt) VALUES(?,?,?,?,'RUNNING',?,?,?)",run,id,occurrence,task.get("version"),millis,millis,"{}");
        db.update("INSERT INTO teacher_auto_task_dispatch(run_id,task_id,task_version,occurrence_key,definition,audience,valid_until,created_at) VALUES(?,?,?,?,?,?,?,NOW(3))",run,id,task.get("version"),occurrence,encode(def),encode(audience),stamp(planned.plusMinutes(60)));return run;
    }
    public Map<String,Object> owned(Actor actor,long id){require(actor,false);var rows=db.queryForList("SELECT * FROM teacher_auto_task WHERE id=? AND teacher_id=? AND deleted_at IS NULL",id,actor.id());if(rows.isEmpty())throw new AgentException("NOT_FOUND","任务不存在。",404);return rows.getFirst();}
    private Map<String,Object> ownedLocked(Actor actor,long id){var row=owned(actor,id);return db.queryForMap("SELECT * FROM teacher_auto_task WHERE id=? FOR UPDATE",row.get("id"));}
    public List<Map<String,Object>> list(Actor actor){require(actor,false);return db.queryForList("SELECT * FROM teacher_auto_task WHERE teacher_id=? AND deleted_at IS NULL ORDER BY id DESC",actor.id()).stream().map(this::view).toList();}
    public Map<String,Object> get(Actor actor,long id){return view(owned(actor,id));}
    public void dismissDraft(Actor actor,String id){draft(actor,id);db.update("UPDATE teacher_auto_task_draft SET definition=JSON_SET(definition,'$._closed',true),revision=revision+1 WHERE id=? AND teacher_id=?",id,actor.id());}
    public AgentActionService.ActionView receipt(Actor actor,String id){require(actor,false);return actions.get(actor,id);}
    private void requireAdmin(Actor actor){AgentIdentityService.requireAdmin(actor);if(actor.readOnly())throw AgentIdentityService.forbidden();}
    public List<Map<String,Object>> uncertain(Actor actor,int offset){requireAdmin(actor);return db.queryForList("SELECT d.id,d.task_id,d.student_name,d.subject,d.channel,d.detail,d.updated_at,t.name AS task_name FROM teacher_auto_task_delivery d JOIN teacher_auto_task t ON t.id=d.task_id WHERE d.status='UNKNOWN' ORDER BY d.updated_at LIMIT 50 OFFSET ?",Math.max(offset,0));}
    public AgentActionService.ActionView verifyPreview(Actor actor,String id,String outcome,String evidence){
        requireAdmin(actor);if(!Set.of("ACCEPTED","NOT_ACCEPTED").contains(outcome)||evidence==null||evidence.isBlank()||evidence.length()>2000)AutomationRules.fail("请依据邮件服务记录选择核实结果，并填写凭据说明。");
        var rows=db.queryForList("SELECT id,updated_at FROM teacher_auto_task_delivery WHERE id=? AND status='UNKNOWN'",id);
        if(rows.isEmpty())throw conflict("该记录已不在待核实状态，请刷新后重新核实。");
        return actions.preview(actor,"AUTOMATION_VERIFY_DELIVERY","delivery:"+id,Map.of("deliveryId",id,"outcome",outcome,"evidence",evidence,"version",rows.getFirst().get("updated_at").toString()),null);
    }
    public Map<String,Object> verifyConfirm(Actor actor,String actionId){
        requireAdmin(actor);var action=actions.get(actor,actionId);
        return actions.execute(actor,actionId,"AUTOMATION_VERIFY_DELIVERY",action.parameters(),()->{
            requireAdmin(actor);var p=action.parameters();var row=db.queryForMap("SELECT * FROM teacher_auto_task_delivery WHERE id=? FOR UPDATE",p.get("deliveryId"));
            if(!"UNKNOWN".equals(row.get("status"))||!row.get("updated_at").toString().equals(p.get("version")))throw conflict("该记录刚刚被其他人处理，请刷新后重新核实。");
            boolean accepted="ACCEPTED".equals(p.get("outcome"));String state=accepted?"SENT":"FAILED";
            db.update("UPDATE teacher_auto_task_delivery SET status=?,evidence=?,detail=?,updated_at=NOW(3) WHERE id=?",state,p.get("evidence"),accepted?"管理员核实：邮件服务已受理。":"管理员核实：邮件服务未受理，可由任务所有者确认重试。",row.get("id"));
            db.update("UPDATE agent_notification_guard SET state=?,last_success_at=CASE WHEN ?='SENT' THEN NOW(3) ELSE last_success_at END,updated_at=NOW(3) WHERE claim_token=? AND state='UNKNOWN'",state,state,row.get("id"));
            return Map.of("id",row.get("id"),"status",state,"detail",accepted?"邮件服务已受理":"确认未受理，尚未重发");
        });
    }
    public Map<String,Object> pause(Actor actor,long id,boolean delete){require(actor,false);return tx.execute(s->{ownedLocked(actor,id);db.update("UPDATE teacher_auto_task SET status=?,enabled=0,version=version+1,deleted_at="+(delete?"NOW(3)":"NULL")+",update_time=NOW() WHERE id=?",delete?"ENDED":"PAUSED",id);db.update("UPDATE teacher_auto_task_delivery SET status='CANCELLED',detail='任务已停止',updated_at=NOW(3) WHERE task_id=? AND status IN ('QUEUED','CLAIMED')",id);return Map.of("id",id,"status",delete?"DELETED":"PAUSED");});}
    public List<Map<String,Object>> runs(Actor actor,long id,int offset){owned(actor,id);return db.queryForList("SELECT * FROM teacher_auto_task_run WHERE task_id=? ORDER BY started_at DESC LIMIT 20 OFFSET ?",id,Math.max(0,offset));}
    public List<Map<String,Object>> deliveries(Actor actor,long id,int offset){owned(actor,id);return db.queryForList("SELECT id,run_id,student_name,channel,status,attempts,detail,created_at,updated_at FROM teacher_auto_task_delivery WHERE task_id=? ORDER BY created_at DESC,id LIMIT 50 OFFSET ?",id,Math.max(0,offset));}
    public Map<String,Object> view(Map<String,Object> row){
        JsonNode def=definition(row);Map<String,Object> result=describe(def);result.put("id",row.get("id"));result.put("version",row.get("version"));result.put("definition",def);
        result.put("enabled",row.get("enabled"));result.put("status",row.get("status"));result.put("blockedReason",row.get("blocked_reason"));result.put("nextRunAt",row.get("next_run_at")==null?null:row.get("next_run_at").toString().replace(' ','T'));result.put("engineVersion",row.get("engine_version"));
        Long attention=db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task_delivery WHERE task_id=? AND status IN ('UNKNOWN','FAILED')",Long.class,row.get("id"));result.put("attentionCount",attention);return result;
    }
    public Map<String,Object> describe(JsonNode def){Map<String,Object> r=new LinkedHashMap<>();r.put("name",def.path("name").asText());r.put("triggerLabel",AutomationRules.describe(def.path("condition")));try{r.put("scheduleLabel",AutomationTiming.describe(def.path("trigger")));}catch(RuntimeException invalid){r.put("scheduleLabel","时间待完善");}r.put("actionLabel",def.path("notification").path("channels").toString().replace("EMAIL","邮件").replace("STATION_REMINDER","站内提醒").replaceAll("[\\[\\]\"]",""));r.put("messageTemplate",def.path("notification").path("body").asText());return r;}
    public JsonNode definition(Map<String,Object> row){
        if(row.get("definition")!=null){ObjectNode clean=(ObjectNode)decode(row.get("definition"));clean.remove(List.of("_draftId","_draftRevision","_actionId","_taskId","_closed"));return clean;}
        ObjectNode def=((ObjectNode)definitions.templates().getFirst().get("definition")).deepCopy();def.remove("templateId");def.put("name",Objects.toString(row.get("name"),"自动提醒"));
        ObjectNode notify=(ObjectNode)def.path("notification");notify.put("subject",def.path("name").asText());notify.put("body",Objects.toString(row.get("message_template"),""));notify.set("channels",json.valueToTree(List.of(Objects.toString(row.get("action"),"STATION_REMINDER"))));
        String trigger=Objects.toString(row.get("trigger_condition"),"");
        def.set("condition",switch(trigger){case "APPRAISAL_MISSING"->AutomationRules.leaf(json,"appraisal.required","EQ",true);case "OFFER_PENDING"->AutomationRules.leaf(json,"internship.status","EQ","PENDING_CONFIRMATION");case "REFLECTION_NOT_SUBMITTED"->json.createObjectNode().put("exists","reflections").set("where",AutomationRules.leaf(json,"required","EQ",true));default->json.createObjectNode().put("always",true);});
        if(Set.of("APPRAISAL_MISSING","REFLECTION_NOT_SUBMITTED").contains(trigger)&&row.get("trigger_days")!=null) {
            JsonNode condition=def.path("condition");
            if(trigger.equals("APPRAISAL_MISSING"))def.set("condition",json.createObjectNode().set("all",json.valueToTree(List.of(condition,AutomationRules.leaf(json,"appraisal.deadline","DAYS_UNTIL",List.of(0,number(row.get("trigger_days"))))))));
            else ((ObjectNode)condition).set("where",json.createObjectNode().set("all",json.valueToTree(List.of(condition.path("where"),AutomationRules.leaf(json,"dueDate","DAYS_UNTIL",List.of(0,number(row.get("trigger_days"))))))));
        }
        ((ObjectNode)def.path("repeatPolicy")).put("cooldownMinutes",Math.max(1,number(row.get("frequency_minutes"))));
        if(row.get("end_at")!=null)((ObjectNode)def.path("validity")).put("endAt",((Timestamp)row.get("end_at")).toLocalDateTime().toString());
        ObjectNode timing=(ObjectNode)decode(row.get("schedule_config"));String mode=timing.path("mode").asText("WINDOW");timing.put("mode",mode.equals("WINDOW")?"CONDITION":mode);def.set("trigger",timing);
        var target=TeacherAutoTaskTargets.decode(Objects.toString(row.get("target_scope"),null));((ObjectNode)def.path("audience")).set("studentIds",json.valueToTree(target));return def;
    }
    /** Additive conversion keeps original legacy fields and all receipts for rollback. */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void migrateLegacy(){
        for(var row:db.queryForList("SELECT * FROM teacher_auto_task WHERE engine_version=1 AND deleted_at IS NULL")) {
            if(!Set.of("APPRAISAL_MISSING","REFLECTION_NOT_SUBMITTED","OFFER_PENDING","SCHEDULED_REMINDER").contains(Objects.toString(row.get("trigger_condition"))))continue;
            JsonNode def=definition(row);if(!definitions.issues(def).isEmpty())continue;
            tx.execute(tx->{var current=db.queryForMap("SELECT * FROM teacher_auto_task WHERE id=? FOR UPDATE",row.get("id"));if(number(current.get("engine_version"))!=1)return null;
                LocalDateTime next="ACTIVE".equals(row.get("status"))?firstTime(def,now()):null;
                db.update("UPDATE teacher_auto_task SET engine_version=2,definition=?,next_run_at=? WHERE id=?",encode(def),stamp(next),row.get("id"));
                db.update("INSERT IGNORE INTO teacher_auto_task_revision(task_id,version,definition,created_at) VALUES(?,?,?,NOW(3))",row.get("id"),row.get("version"),encode(def));return null;});
        }
    }
    public static Timestamp stamp(LocalDateTime time){return time==null?null:Timestamp.valueOf(time);}
    public static String clip(String value,int max){return value.substring(0,Math.min(value.length(),max));}
    static AgentException conflict(){return conflict("任务或草稿已变化，请重新核对；当前内容仍保留。");}
    static AgentException conflict(String message){return new AgentException("VERSION_CONFLICT",message,409);}
}
