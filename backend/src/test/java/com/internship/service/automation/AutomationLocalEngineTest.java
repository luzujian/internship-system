package com.internship.service.automation;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.entity.TeacherUser;
import com.internship.mapper.AgentActionMapper;
import com.internship.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Actual local MySQL transactions/unique indexes/leases. Business facts and mail transport are controlled fixtures. */
@EnabledIfEnvironmentVariable(named="AGENT_LOCAL_DB_TESTS",matches="true")
class AutomationLocalEngineTest {
    JdbcTemplate db; AutomationService service; AutomationWorker worker; ObjectMapper json=new ObjectMapper();
    Actor actor; long teacherId; List<Long> students; Set<Long> excluded=ConcurrentHashMap.newKeySet();
    List<Long> taskIds=new ArrayList<>();String owner;AtomicInteger sends=new AtomicInteger();
    AutomationMailTransport mail;AutomationFacts facts;AgentCapabilityService caps;
    @BeforeEach void setup() throws Exception {
        var source=new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/internship?serverTimezone=Asia/Shanghai&characterEncoding=utf8","user",System.getenv("LOCAL_DB_PASSWORD"));
        db=new JdbcTemplate(source);var manager=new DataSourceTransactionManager(source);
        var configuration=new org.apache.ibatis.session.Configuration();configuration.addMapper(AgentActionMapper.class);
        var factory=new SqlSessionFactoryBean();factory.setDataSource(source);factory.setConfiguration(configuration);
        var sql=new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        teacherId=900000000L+Math.abs(UUID.randomUUID().getLeastSignificantBits()%100000000L);owner="AUTOMATION_LOCAL_TEST:"+UUID.randomUUID();
        var teacher=new TeacherUser();teacher.setId(teacherId);teacher.setTeacherType("COUNSELOR");teacher.setStatus(1);teacher.setName("本地自动化测试");
        students=new ArrayList<>();for(long i=1;i<=205;i++)students.add(teacherId+i);
        var teachers=mock(TeacherUserService.class);when(teachers.findById(teacherId)).thenReturn(teacher);
        var scope=mock(TeacherAutomationScopeService.class);when(scope.studentIds(any())).thenAnswer(x->List.copyOf(students));when(scope.contains(any(),anyLong())).thenAnswer(x->students.contains(x.getArgument(1)));when(scope.label(any())).thenReturn("本地测试范围");
        var permissions=mock(TeacherPermissionService.class);when(permissions.getPermissionCodesByRoleCode(anyString())).thenReturn(List.of("view:student_tracking"));
        caps=mock(AgentCapabilityService.class);when(caps.effectiveAutomationPolicy(any())).thenReturn(new AgentCapabilityService.AutomationPolicy(true,true,true,Set.of("SEND_REMINDER"),Set.of("EMAIL"),Set.of("OWN_CLASSES"),Integer.MAX_VALUE,1440));
        facts=mock(AutomationFacts.class);when(facts.load(anyList(),any())).thenAnswer(x->{Map<Long,Map<String,Object>> result=new LinkedHashMap<>();for(Long id:x.<List<Long>>getArgument(0)){var f=new LinkedHashMap<String,Object>();f.put("id",id);f.put("name","测试学生");f.put("student.username","qa"+id);f.put("email","student@example.test");f.put("internship.status",excluded.contains(id)?"NO_OFFER":"NOT_FOUND");f.put("matterKeys",List.of());result.put(id,f);}return result;});
        mail=mock(AutomationMailTransport.class);when(mail.available()).thenReturn(true);when(mail.isolated()).thenReturn(true);
        when(mail.send(anyString(),anyString(),anyString(),anyString())).thenAnswer(x->{sends.incrementAndGet();return new AutomationMailTransport.Outcome("SENT","隔离测试邮箱已受理",false);});
        var actions=new AgentActionService(sql.getMapper(AgentActionMapper.class),json,manager);
        service=new AutomationService(db,json,manager,new AutomationDefinitions(json,null),facts,scope,teachers,caps,mock(AgentFeatureGate.class),actions,mail,permissions);
        actor=new Actor(owner,teacherId,"ROLE_TEACHER_COUNSELOR","测试教师","COUNSELOR",null,null,false,Set.of("view:student_tracking"));
        worker=new AutomationWorker(service,mock(ReminderService.class),false);
    }
    @AfterEach void cleanup(){
        if(worker!=null)worker.close();if(db==null)return;
        for(Long task:taskIds){db.update("DELETE FROM teacher_auto_task_delivery WHERE task_id=?",task);db.update("DELETE FROM teacher_auto_task_dispatch WHERE task_id=?",task);db.update("DELETE FROM teacher_auto_task_match WHERE task_id=?",task);db.update("DELETE FROM teacher_auto_task_revision WHERE task_id=?",task);db.update("DELETE FROM teacher_auto_task_run WHERE task_id=?",task);db.update("DELETE FROM teacher_auto_task WHERE id=?",task);}
        db.update("DELETE FROM teacher_auto_task WHERE teacher_id=?",teacherId);db.update("DELETE FROM teacher_auto_task_draft WHERE teacher_id=?",teacherId);db.update("DELETE FROM agent_action WHERE owner_key=?",owner);
        for(Long id:students)db.update("DELETE FROM agent_notification_guard WHERE student_id=?",id);
    }
    ObjectNode definition(){ObjectNode d=((ObjectNode)service.definitions.templates().get(1).get("definition")).deepCopy();d.put("name",owner);return d;}
    Map<String,Object> create(ObjectNode def){var preview=service.preview(actor,def,null,"CREATE",false,false);var result=service.confirm(actor,preview.get("actionId").toString());taskIds.add(AutomationService.number(result.get("id")));return result;}
    String queue(Map<String,Object> task){return service.tx.execute(t->service.enqueue(service.owned(actor,AutomationService.number(task.get("id"))),service.definition(service.owned(actor,AutomationService.number(task.get("id")))),UUID.randomUUID().toString(),List.copyOf(students),AutomationService.now()));}
    void enumerate(String run){while(db.queryForObject("SELECT enumerated FROM teacher_auto_task_dispatch WHERE run_id=?",Integer.class,run)==0)worker.enumerate(db.queryForMap("SELECT * FROM teacher_auto_task_dispatch WHERE run_id=?",run));}
    List<Map<String,Object>> deliveries(String run){return db.queryForList("SELECT * FROM teacher_auto_task_delivery WHERE run_id=? ORDER BY student_id",run);}
    @Test void moreThanTwoHundredArePagedAndTeacherTextIsImmutable(){
        var d=definition();((ObjectNode)d.path("notification")).put("subject","教师标题").put("body","教师原文\n不会被改写");
        var task=create(d);String run=queue(task);enumerate(run);var rows=deliveries(run);assertEquals(205,rows.size());
        rows.forEach(r->worker.deliver(r.get("id").toString()));assertEquals(205,sends.get());
        assertEquals(205L,db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task_delivery WHERE run_id=? AND status='SENT' AND body=?",Long.class,run,"教师原文\n不会被改写"));
        verify(mail,times(205)).send(anyString(),anyString(),eq("教师标题"),eq("教师原文\n不会被改写"));
    }
    @Test void parallelConfirmationAndReceiptRetryCreateOnlyOneTask() throws Exception {
        var d=definition();var preview=service.preview(actor,d,null,"CREATE",false,false);String action=preview.get("actionId").toString();
        try(var pool=Executors.newFixedThreadPool(6)){
            var results=new ArrayList<Future<?>>();for(int i=0;i<6;i++)results.add(pool.submit(()->{try{service.confirm(actor,action);}catch(AgentException pending){assertEquals("ACTION_PENDING",pending.getCode());}}));for(var result:results)result.get();
        }
        var receipt=service.confirm(actor,action);long id=AutomationService.number(receipt.get("id"));taskIds.add(id);
        assertEquals(1L,db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task WHERE teacher_id=?",Long.class,teacherId));
        assertEquals(id,AutomationService.number(service.confirm(actor,action).get("id")));assertEquals(0,sends.get());
    }
    @Test void manualRunKeepsScheduleAndVersionAndPauseStopsPending(){
        var task=create(definition());long id=AutomationService.number(task.get("id"));var before=service.owned(actor,id);
        var preview=service.preview(actor,definition(),id,"RUN",false,false);var result=service.confirm(actor,preview.get("actionId").toString());
        assertEquals(before.get("version"),service.owned(actor,id).get("version"));assertEquals(before.get("next_run_at"),service.owned(actor,id).get("next_run_at"));
        String run=result.get("runId").toString();enumerate(run);var rows=deliveries(run);worker.deliver(rows.getFirst().get("id").toString());
        service.pause(actor,id,false);rows.forEach(r->worker.deliver(r.get("id").toString()));assertEquals(1,sends.get());
        assertEquals(204L,db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task_delivery WHERE run_id=? AND status='CANCELLED'",Long.class,run));
    }
    @Test void conditionChangesAndScopeChangesExcludeStudentBeforeSend(){
        var task=create(definition());String run=queue(task);enumerate(run);var rows=deliveries(run);
        excluded.add(students.getFirst());worker.deliver(rows.getFirst().get("id").toString());assertEquals(0,sends.get());
        Long removed=students.remove(1);worker.deliver(rows.get(1).get("id").toString());assertEquals(0,sends.get());students.add(removed);
        assertEquals(2L,db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task_delivery WHERE run_id=? AND status='SKIPPED'",Long.class,run));
    }
    @Test void unknownOutcomeSurvivesRecoveryAndCannotBeRetried(){
        students=new ArrayList<>(students.subList(0,1));doAnswer(x->{sends.incrementAndGet();return new AutomationMailTransport.Outcome("UNKNOWN","SMTP响应超时",false);}).when(mail).send(anyString(),anyString(),anyString(),anyString());
        var task=create(definition());String run=queue(task);enumerate(run);worker.deliver(deliveries(run).getFirst().get("id").toString());worker.recover();
        String second=queue(task);enumerate(second);worker.deliver(deliveries(second).getFirst().get("id").toString());assertEquals(1,sends.get());
        assertEquals("UNKNOWN",deliveries(run).getFirst().get("status"));assertThrows(RuntimeException.class,()->service.preview(actor,definition(),task.get("id"),"RETRY",false,false));
    }
    @Test void safeTemporaryFailuresHaveThreeAttemptsAtOneAndFiveMinutes(){
        students=new ArrayList<>(students.subList(0,1));when(mail.send(anyString(),anyString(),anyString(),anyString())).thenReturn(new AutomationMailTransport.Outcome("FAILED","连接未建立",true));
        var task=create(definition());String run=queue(task);enumerate(run);String id=deliveries(run).getFirst().get("id").toString();
        for(int attempt=1;attempt<=3;attempt++){
            worker.deliver(id);var row=deliveries(run).getFirst();assertEquals(attempt,AutomationService.number(row.get("attempts")));
            if(attempt<3){long seconds=db.queryForObject("SELECT TIMESTAMPDIFF(SECOND,NOW(3),next_attempt_at) FROM teacher_auto_task_delivery WHERE id=?",Long.class,id);assertTrue(seconds>=(attempt==1?58:298));db.update("UPDATE teacher_auto_task_delivery SET next_attempt_at=NOW(3) WHERE id=?",id);}
        }
        assertEquals("FAILED",deliveries(run).getFirst().get("status"));verify(mail,times(3)).send(anyString(),anyString(),anyString(),anyString());
    }
    @Test void personalTemplatesAreSeededOnceAndEditsAndDeletionArePrivate(){
        var templates=new AutomationTemplates(db,json);var teacher=service.teachers.findById(teacherId);
        var first=templates.list(teacher);assertEquals(2,first.size());
        var original=first.getFirst();String id=original.get("id").toString();
        templates.update(teacher,id,1,"我的截止提醒","我自定义的提示词",false);
        var updated=templates.list(teacher);assertEquals(2,updated.size());assertEquals("我自定义的提示词",updated.stream().filter(r->r.get("id").equals(id)).findFirst().orElseThrow().get("prompt"));
        assertThrows(RuntimeException.class,()->templates.update(teacher,id,1,"旧版本","旧内容",false));
        var other=new TeacherUser();other.setId(teacherId+10000);other.setTeacherType("COLLEGE");
        assertThrows(RuntimeException.class,()->templates.update(other,id,2,null,null,true));
        templates.update(teacher,id,2,null,null,true);assertEquals(1,templates.list(teacher).size());templates.initialize(teacher);assertEquals(1,templates.list(teacher).size());
        assertEquals(0L,db.queryForObject("SELECT COUNT(*) FROM teacher_auto_task WHERE teacher_id=?",Long.class,teacherId));
    }
    @Test void channelRevocationPausesRemainingWorkAndDoesNotResumeAutomatically(){
        students=new ArrayList<>(students.subList(0,2));var task=create(definition());String run=queue(task);enumerate(run);
        worker.deliver(deliveries(run).getFirst().get("id").toString());assertEquals(1,sends.get());
        when(caps.effectiveAutomationPolicy(any())).thenReturn(new AgentCapabilityService.AutomationPolicy(true,true,true,Set.of("SEND_REMINDER"),Set.of("STATION_REMINDER"),Set.of("OWN_CLASSES"),Integer.MAX_VALUE,1440));
        worker.deliver(deliveries(run).get(1).get("id").toString());assertEquals("PAUSED",service.owned(actor,AutomationService.number(task.get("id"))).get("status"));
        when(caps.effectiveAutomationPolicy(any())).thenReturn(new AgentCapabilityService.AutomationPolicy(true,true,true,Set.of("SEND_REMINDER"),Set.of("EMAIL"),Set.of("OWN_CLASSES"),Integer.MAX_VALUE,1440));
        worker.deliver(deliveries(run).get(1).get("id").toString());assertEquals(1,sends.get());assertEquals("CANCELLED",deliveries(run).get(1).get("status"));
    }
    @Test void aShortDailyCooldownDelayIsHandledInsideGraceWindow(){
        students=new ArrayList<>(students.subList(0,1));var task=create(definition());String first=queue(task);enumerate(first);worker.deliver(deliveries(first).getFirst().get("id").toString());
        db.update("UPDATE agent_notification_guard SET last_success_at=DATE_SUB(NOW(3),INTERVAL 1439 MINUTE) WHERE student_id=?",students.getFirst());
        String next=queue(task);enumerate(next);String id=deliveries(next).getFirst().get("id").toString();worker.deliver(id);assertEquals("QUEUED",deliveries(next).getFirst().get("status"));assertEquals(1,sends.get());
        db.update("UPDATE agent_notification_guard SET last_success_at=DATE_SUB(NOW(3),INTERVAL 1441 MINUTE) WHERE student_id=?",students.getFirst());db.update("UPDATE teacher_auto_task_delivery SET next_attempt_at=NOW(3) WHERE id=?",id);
        worker.deliver(id);assertEquals("SENT",deliveries(next).getFirst().get("status"));assertEquals(2,sends.get());
    }
    @Test void restartRecoversQueuedWorkAndFreezesInterruptedEmail(){
        students=new ArrayList<>(students.subList(0,2));var task=create(definition());String run=queue(task);enumerate(run);var rows=deliveries(run);
        db.update("UPDATE teacher_auto_task_delivery SET status='CLAIMED',lease_until=DATE_SUB(NOW(3),INTERVAL 1 MINUTE) WHERE id=?",rows.getFirst().get("id"));
        worker.close();worker=new AutomationWorker(service,mock(ReminderService.class),false);worker.recover();worker.deliver(rows.getFirst().get("id").toString());assertEquals(1,sends.get());
        String second=rows.get(1).get("id").toString();String guard=AgentNotificationGuardService.key(students.get(1),"internship:application","EMAIL");
        db.update("INSERT INTO agent_notification_guard(dedupe_key,student_id,todo_id,channel,state,claim_token,updated_at) VALUES(?,?,?,'EMAIL','PENDING',?,NOW(3))",guard,students.get(1),"internship:application",second);
        db.update("UPDATE teacher_auto_task_delivery SET status='SENDING',lease_until=DATE_SUB(NOW(3),INTERVAL 1 MINUTE) WHERE id=?",second);worker.recover();worker.deliver(second);
        assertEquals("UNKNOWN",deliveries(run).get(1).get("status"));assertEquals(1,sends.get());
    }
}
