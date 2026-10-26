package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.mapper.*;
import com.internship.service.AgentNotificationGuardService;
import com.internship.service.TeacherAutoTaskRunService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.TestConfiguration;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Opt-in, real local MySQL tests. No DDL, no business users, no mail, no remote host. */
@EnabledIfEnvironmentVariable(named="AGENT_LOCAL_DB_TESTS", matches="true")
class AgentLocalPersistenceTest {
    @TestConfiguration @EnableTransactionManagement(proxyTargetClass=true)
    static class Config {
        @Bean DataSource dataSource() {
            String password=System.getenv("LOCAL_DB_PASSWORD");
            if(password==null)throw new IllegalStateException("LOCAL_DB_PASSWORD is required");
            return new DriverManagerDataSource("jdbc:mysql://127.0.0.1:3306/internship?serverTimezone=Asia/Shanghai&characterEncoding=utf8", "user",password);
        }
        @Bean PlatformTransactionManager transactionManager(DataSource data){return new DataSourceTransactionManager(data);}
        @Bean JdbcTemplate jdbc(DataSource data){return new JdbcTemplate(data);}
        @Bean SqlSessionTemplate sql(DataSource data)throws Exception{
            var config=new org.apache.ibatis.session.Configuration();config.setMapUnderscoreToCamelCase(true);
            config.addMapper(AgentHistoryMapper.class);config.addMapper(AgentActionMapper.class);
            config.addMapper(TeacherAutomationScopeMapper.class);config.addMapper(AgentCapabilityUserMapper.class);
            config.addMapper(TeacherAutoTaskRunMapper.class);
            var factory=new SqlSessionFactoryBean();factory.setDataSource(data);factory.setConfiguration(config);
            return new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()));
        }
        @Bean AgentHistoryMapper history(SqlSessionTemplate sql){return sql.getMapper(AgentHistoryMapper.class);}
        @Bean AgentActionMapper actionMapper(SqlSessionTemplate sql){return sql.getMapper(AgentActionMapper.class);}
        @Bean AgentStore store(AgentHistoryMapper mapper){return new AgentStore(mock(StringRedisTemplate.class),new ObjectMapper(),mapper);}
        @Bean AgentActionService actions(AgentActionMapper mapper,PlatformTransactionManager tx){return new AgentActionService(mapper,new ObjectMapper(),tx);}
        @Bean AgentNotificationGuardService guard(JdbcTemplate jdbc){return new AgentNotificationGuardService(jdbc);}
        @Bean TeacherAutoTaskRunMapper runMapper(SqlSessionTemplate sql){return sql.getMapper(TeacherAutoTaskRunMapper.class);}
        @Bean TeacherAutoTaskRunService runs(TeacherAutoTaskRunMapper mapper,PlatformTransactionManager tx){return new TeacherAutoTaskRunService(mapper,new ObjectMapper(),tx);}
    }
    AnnotationConfigApplicationContext context;
    JdbcTemplate jdbc; AgentStore store; AgentActionService actions; AgentNotificationGuardService guard;
    String owner; Actor actor; long studentId;
    @BeforeEach void open(){
        context=new AnnotationConfigApplicationContext(Config.class);
        jdbc=context.getBean(JdbcTemplate.class); store=context.getBean(AgentStore.class);
        actions=context.getBean(AgentActionService.class);guard=context.getBean(AgentNotificationGuardService.class);
        owner="TEST_AGENT:"+UUID.randomUUID();
        studentId=-Math.abs(UUID.randomUUID().getLeastSignificantBits()/2);
        actor=new Actor(owner,studentId,"ROLE_STUDENT","local-test",null,null,null,false,Set.of());
    }
    @AfterEach void cleanup(){
        if(jdbc!=null&&owner!=null){
            for(String table:List.of("agent_session_event","agent_request","agent_task","agent_session","agent_draft","agent_action"))
                jdbc.update("DELETE FROM "+table+" WHERE owner_key=?",owner);
            jdbc.update("DELETE FROM agent_notification_guard WHERE student_id=?",studentId);
            jdbc.update("DELETE FROM teacher_auto_task_run WHERE task_id=?",studentId);
            jdbc.update("DELETE FROM teacher_auto_task WHERE id=? AND name=?",studentId,owner);
        }
        if(context!=null)context.close();
    }
    Session session(){Session s=new Session();s.setId(UUID.randomUUID().toString());s.setOwnerKey(owner);s.setTitle("local persistence");store.save(s);return s;}
    @Test void historyAndDraftSurviveNewStoreWithoutRedisAndHaveNoTwoHourOrTwentyTurnExpiry(){
        Session s=session();
        for(int i=0;i<63;i++){
            Task t=new Task();t.setId(UUID.randomUUID().toString());t.setSessionId(s.getId());t.setOwnerKey(owner);
            t.setCreatedAt(System.currentTimeMillis()-4*3600_000L+i);t.setStatus("SUCCEEDED");t.setAnswer("message "+i);store.registerRequestTask(owner,UUID.randomUUID().toString(),t);
        }
        store.saveDraft(owner,new DraftRequest(s.getId(),"unsent local draft",0));
        AgentStore restarted=new AgentStore(mock(StringRedisTemplate.class),new ObjectMapper(),context.getBean(AgentHistoryMapper.class));
        var page1=restarted.history(s.getId(),null,50);var page2=restarted.history(s.getId(),page1.getLast(),50);
        assertEquals(50,page1.size());assertEquals(13,page2.size());
        assertEquals("unsent local draft",restarted.draft(owner,s.getId()).get("text"));
        assertEquals("DRAFT_CONFLICT",assertThrows(AgentException.class,()->store.saveDraft(owner,new DraftRequest(s.getId(),"stale",0))).getCode());
        s.setArchived(true);store.metadata(s);assertTrue(restarted.session(s.getId()).isArchived());
        assertEquals(50,restarted.history(s.getId(),null,50).size());
    }
    @Test void duplicateConfirmationCommitsBusinessProbeAndReceiptOnce()throws Exception{
        store.saveDraft(owner,new DraftRequest("new","probe",0));
        var preview=actions.preview(actor,"LOCAL_TEST","probe",Map.of("value",1),"v1");
        AtomicInteger callbacks=new AtomicInteger();CountDownLatch start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(6)){
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<6;i++)futures.add(workers.submit(()->{
                try{start.await(); actions.execute(actor,preview.id(),"LOCAL_TEST",preview.parameters(),()->{
                    callbacks.incrementAndGet();jdbc.update("UPDATE agent_draft SET revision=revision+1 WHERE owner_key=? AND draft_key='new'",owner);
                    return Map.of("saved",true);
                });}catch(AgentException e){assertEquals("ACTION_PENDING",e.getCode());}catch(InterruptedException e){throw new RuntimeException(e);}
            }));
            start.countDown();for(Future<?> future:futures)future.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,callbacks.get());
        assertEquals(2L,((Number)store.draft(owner,"new").get("revision")).longValue());
        var restarted=new AgentActionService(context.getBean(AgentActionMapper.class),new ObjectMapper(),context.getBean(PlatformTransactionManager.class));
        assertEquals(true,restarted.execute(actor,preview.id(),"LOCAL_TEST",preview.parameters(),()->{fail("must not repeat after reconnect");return Map.of();}).get("saved"));
        Session s=session();store.removeSession(owner,s.getId());assertEquals("SUCCEEDED",restarted.get(actor,preview.id()).status());
    }
    @Test void failedBusinessCallbackRollsBackDataAndCannotBeReplayed(){
        store.saveDraft(owner,new DraftRequest("new","original",0));
        var preview=actions.preview(actor,"LOCAL_TEST","probe",Map.of(),"v1");
        assertThrows(AgentException.class,()->actions.execute(actor,preview.id(),"LOCAL_TEST",preview.parameters(),()->{
            jdbc.update("UPDATE agent_draft SET text='changed' WHERE owner_key=? AND draft_key='new'",owner);
            throw new AgentException("RULE_DENIED","rule denied",409);
        }));
        assertEquals("original",store.draft(owner,"new").get("text"));assertEquals("FAILED",actions.get(actor,preview.id()).status());
        assertThrows(AgentException.class,()->actions.execute(actor,preview.id(),"LOCAL_TEST",preview.parameters(),()->{fail("failed action must require new preview");return Map.of();}));
    }
    @Test void concurrentTeachersShareClaimAndUnknownNeverAutomaticallyRetries()throws Exception{
        CountDownLatch start=new CountDownLatch(1);List<AgentNotificationGuardService.Claim> claims=new CopyOnWriteArrayList<>();
        try(var workers=Executors.newFixedThreadPool(6)){
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<6;i++){long taskId=i+1;futures.add(workers.submit(()->{try{start.await();claims.add(guard.claim(studentId,"placement:7:period:2","EMAIL",taskId,1440));}catch(InterruptedException e){throw new RuntimeException(e);}}));}
            start.countDown();for(Future<?> future:futures)future.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,claims.stream().filter(AgentNotificationGuardService.Claim::acquired).count());
        guard.finish(claims.stream().filter(AgentNotificationGuardService.Claim::acquired).findFirst().orElseThrow(),"UNKNOWN",null);
        jdbc.update("UPDATE agent_notification_guard SET updated_at=DATE_SUB(NOW(),INTERVAL 3 DAY) WHERE student_id=?",studentId);
        assertFalse(guard.claim(studentId,"placement:7:period:2","EMAIL",99L,1440).acquired());
        assertTrue(guard.claim(studentId,"placement:7:period:3","EMAIL",99L,1440).acquired());
    }
    @Test void successfulReminderRespectsTwentyFourHourCooldownAndChannelIdentity(){
        var claim=guard.claim(studentId,"offer:12","EMAIL",1L,1440);guard.finish(claim,"SENT",null);
        assertFalse(guard.claim(studentId,"offer:12","EMAIL",2L,1440).acquired());
        assertTrue(guard.claim(studentId,"offer:12","STATION_REMINDER",2L,1440).acquired());
        jdbc.update("UPDATE agent_notification_guard SET last_success_at=DATE_SUB(NOW(),INTERVAL 25 HOUR) WHERE dedupe_key=?",claim.key());
        assertTrue(guard.claim(studentId,"offer:12","EMAIL",2L,1440).acquired());
    }
    @Test void actualScopeAndAccountMapperSqlCannotFallBackToAllStudents(){
        var sql=context.getBean(SqlSessionTemplate.class);var mapper=sql.getMapper(TeacherAutomationScopeMapper.class);
        var teacher=new com.internship.entity.TeacherUser();teacher.setId(-1L);teacher.setTeacherType("COUNSELOR");
        assertTrue(mapper.studentIds(teacher).isEmpty());teacher.setTeacherType("DEPARTMENT");teacher.setDivisionId("-1");assertTrue(mapper.studentIds(teacher).isEmpty());
        teacher.setTeacherType("COLLEGE");teacher.setDepartmentId("-1");assertTrue(mapper.studentIds(teacher).isEmpty());
        teacher.setTeacherType("UNKNOWN");assertTrue(mapper.studentIds(teacher).isEmpty());
        for(String role:List.of("student","teacher","company","admin","unknown"))assertTrue(sql.getMapper(AgentCapabilityUserMapper.class).findExact(role,owner).isEmpty());
    }

    private void taskFixture() {
        jdbc.update("INSERT INTO teacher_auto_task(id,teacher_id,teacher_type,name,trigger_condition,target_scope,action,message_template,enabled,status,version,create_time,update_time) VALUES(?,?,'COUNSELOR',?,'OFFER_PENDING','OWN_CLASSES','STATION_REMINDER','local fixture only',0,'PAUSED',3,NOW(),NOW())", studentId,studentId,owner);
    }
    @Test void durableTeacherRunClaimsSameOccurrenceOnceAcrossConcurrentProcesses()throws Exception {
        taskFixture();
        var runs=context.getBean(TeacherAutoTaskRunService.class);
        List<TeacherAutoTaskRunService.RunView> claims=new CopyOnWriteArrayList<>();
        CountDownLatch start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(6)) {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<6;i++) futures.add(workers.submit(()->{try{start.await();claims.add(runs.claim(studentId,"scheduled:2026-09-20T01:00:00Z",3));}catch(InterruptedException e){throw new RuntimeException(e);}}));
            start.countDown();for(var future:futures)future.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,claims.stream().filter(TeacherAutoTaskRunService.RunView::claimed).count());
        String runId=claims.getFirst().runId();
        assertTrue(claims.stream().allMatch(c->runId.equals(c.runId())));
        runs.complete(runId,"SUCCEEDED",Map.of("successCount",2),null);
        var restarted=new TeacherAutoTaskRunService(context.getBean(TeacherAutoTaskRunMapper.class),new ObjectMapper(),context.getBean(PlatformTransactionManager.class));
        var replay=restarted.claim(studentId,"scheduled:2026-09-20T01:00:00Z",4);
        assertFalse(replay.claimed());assertEquals("SUCCEEDED",replay.status());assertEquals(2,replay.receipt().get("successCount"));
    }
    @Test void teacherRunReceiptRollbackAndUnknownCannotReleaseClaim() {
        taskFixture();var runs=context.getBean(TeacherAutoTaskRunService.class);
        var mapper=context.getBean(TeacherAutoTaskRunMapper.class);
        var run=runs.claim(studentId,"manual:local-request-0001",3);
        runs.heartbeat(run.runId());
        var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        assertThrows(IllegalStateException.class,()->tx.executeWithoutResult(ignored->{mapper.finish(run.runId(),"SUCCEEDED","{}",null,System.currentTimeMillis());throw new IllegalStateException("rollback fixture");}));
        assertEquals("RUNNING",runs.find(studentId,run.occurrenceKey()).status());
        runs.complete(run.runId(),"UNKNOWN",Map.of("unknownCount",1),"local fixture interruption");
        assertEquals("PAUSED",jdbc.queryForObject("SELECT status FROM teacher_auto_task WHERE id=?",String.class,studentId));
        assertEquals(TeacherAutoTaskRunService.UNCERTAIN_REASON,jdbc.queryForObject("SELECT blocked_reason FROM teacher_auto_task WHERE id=?",String.class,studentId));
        assertEquals("RUN_PENDING",assertThrows(AgentException.class,()->runs.claim(studentId,"manual:local-request-0002",3)).getCode());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM teacher_auto_task_run WHERE task_id=?",Integer.class,studentId));
    }
    @Test void concurrentDraftsUseRevisionConflictInsteadOfLockUpgradeDeadlock()throws Exception {
        store.saveDraft(owner,new DraftRequest("new","initial",0));
        CountDownLatch start=new CountDownLatch(1);AtomicInteger saved=new AtomicInteger(),conflicts=new AtomicInteger();
        try(var workers=Executors.newFixedThreadPool(4)) {
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<4;i++)futures.add(workers.submit(()->{try{start.await();store.saveDraft(owner,new DraftRequest("new","edited",1));saved.incrementAndGet();}catch(AgentException e){assertEquals("DRAFT_CONFLICT",e.getCode());conflicts.incrementAndGet();}catch(InterruptedException e){throw new RuntimeException(e);}}));
            start.countDown();for(var future:futures)future.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,saved.get());assertEquals(3,conflicts.get());
    }
    @Test void cancellationFreezesLastPersistedPartialAnswerAndRejectsLateOutput() {
        Session s=session();Task t=newTask(s);t.setStatus("RUNNING");t.setAnswer("第一段");store.registerRequestTask(owner,UUID.randomUUID().toString(),t);
        t.setAnswer("第一段，停止前已输出的内容");store.save(t);
        assertTrue(store.cancel(t.getId()));
        Task stopped=store.task(t.getId());long stoppedVersion=stopped.getVersion();
        t.setAnswer("第一段，接续的完整内容");store.save(t);
        Task visible=store.task(t.getId());assertEquals("CANCELLED",visible.getStatus());assertEquals(stopped.getAnswer(),visible.getAnswer());assertEquals(stoppedVersion,visible.getVersion());
        assertFalse(store.cancel(t.getId()));assertEquals(1,store.taskCount(s.getId()));
    }
    Task newTask(Session session){Task t=new Task();t.setId(UUID.randomUUID().toString());t.setOwnerKey(owner);t.setSessionId(session.getId());t.setCreatedAt(System.currentTimeMillis());t.setMessage("local fixture");return t;}
    @Test void databasePreventsConcurrentSessionRunsEvenWithoutRedis()throws Exception {
        Session s=session();CountDownLatch start=new CountDownLatch(1);AtomicInteger accepted=new AtomicInteger(),blocked=new AtomicInteger();
        try(var workers=Executors.newFixedThreadPool(4)){
            List<Future<?>> futures=new ArrayList<>();
            for(int i=0;i<4;i++)futures.add(workers.submit(()->{try{start.await();store.registerRequestTask(owner,UUID.randomUUID().toString(),newTask(s));accepted.incrementAndGet();}catch(AgentException e){assertEquals("TASK_RUNNING",e.getCode());blocked.incrementAndGet();}catch(InterruptedException e){throw new RuntimeException(e);}}));
            start.countDown();for(var future:futures)future.get(15,TimeUnit.SECONDS);
        }
        assertEquals(1,accepted.get());assertEquals(3,blocked.get());assertEquals(1,store.taskCount(s.getId()));
    }
    @Test void stoppedWorkerCannotOverwriteNextTurnOrResurrectDeletedHistory(){
        Session s=session();Task first=newTask(s);store.registerRequestTask(owner,UUID.randomUUID().toString(),first);
        Session stale=store.session(s.getId());assertTrue(store.cancel(first.getId()));
        Task next=newTask(s);store.registerRequestTask(owner,UUID.randomUUID().toString(),next);
        Session latest=store.session(s.getId());latest.getTurns().add(new Turn("new question","new answer",null));
        assertTrue(store.saveSessionForTask(latest,next));
        stale.getTurns().add(new Turn("stale question","late answer",null));
        assertFalse(store.saveSessionForTask(stale,first));
        assertEquals("new question",store.session(s.getId()).getTurns().getLast().question());
        assertTrue(store.session(s.getId()).getTaskIds().contains(next.getId()));
        assertEquals("TASK_RUNNING",assertThrows(AgentException.class,()->store.removeSession(owner,s.getId())).getCode());
        assertTrue(store.cancel(next.getId()));assertTrue(store.removeSession(owner,s.getId()));
        assertFalse(store.saveSessionForTask(stale,first));
        assertThrows(AgentException.class,()->store.save(first));
        assertEquals(0,store.taskCount(s.getId()));assertNull(store.find(s.getId()));
    }
    @Test void editingAnOlderVersionAllocatesTheNextVersionWithoutReplacingExistingAnswers(){
        Session s=session();Task original=newTask(s);original.setRootMessageId(original.getId());original.setStatus("SUCCEEDED");original.setAnswer("first");
        store.registerRequestTask(owner,UUID.randomUUID().toString(),original);
        for(int expected=2;expected<=4;expected++){
            Task revision=newTask(s);revision.setRootMessageId(original.getId());revision.setEditOf(original.getId());revision.setRevisionNumber(2);revision.setStatus("SUCCEEDED");revision.setAnswer("version "+expected);
            store.registerRequestTask(owner,UUID.randomUUID().toString(),revision);
            assertEquals(expected,store.task(revision.getId()).getRevisionNumber());
        }
        assertEquals(4,store.taskCount(s.getId()));assertEquals("first",store.task(original.getId()).getAnswer());
    }
    @Test void interruptionCannotOverwriteNewOutputOrReviveTerminalWorker(){
        Session s=session();Task old=newTask(s);store.registerRequestTask(owner,UUID.randomUUID().toString(),old);
        Task stale=store.task(old.getId());old.setAnswer("newer partial");store.save(old);
        stale.setStatus("FAILED");assertFalse(store.interrupt(stale,Long.MAX_VALUE));
        assertEquals("newer partial",store.task(old.getId()).getAnswer());
        Task expired=store.task(old.getId());expired.setStatus("FAILED");expired.setErrorCode("TASK_INTERRUPTED");
        assertTrue(store.interrupt(expired,Long.MAX_VALUE));
        Task next=newTask(s);store.registerRequestTask(owner,UUID.randomUUID().toString(),next);
        old.setStatus("RUNNING");old.setAnswer("late old worker");store.save(old);
        assertEquals("FAILED",store.task(old.getId()).getStatus());assertEquals("newer partial",store.task(old.getId()).getAnswer());
        assertFalse(store.saveSessionForTask(s,old));
        next.setStatus("SUCCEEDED");next.setAnswer("finished");store.save(next);
        next.setStatus("FAILED");next.setAnswer("late failure");store.save(next);
        assertEquals("SUCCEEDED",store.task(next.getId()).getStatus());assertEquals("finished",store.task(next.getId()).getAnswer());
    }
    @Test void deletingSessionPreventsLateDraftAndTimelineFromLeavingOrphanedContent(){
        Session s=session();TimelineEvent event=new TimelineEvent(UUID.randomUUID().toString(),"FILL",System.currentTimeMillis(),"local test",Map.of("applied",false));
        store.saveDraft(owner,new DraftRequest(s.getId(),"private draft",0));store.event(owner,s.getId(),event);
        assertTrue(store.removeSession(owner,s.getId()));
        assertThrows(AgentException.class,()->store.saveDraft(owner,new DraftRequest(s.getId(),"late private draft",0)));
        assertThrows(AgentException.class,()->store.event(owner,s.getId(),event));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_draft WHERE owner_key=? AND draft_key=?",Integer.class,owner,s.getId()));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM agent_session_event WHERE session_id=?",Integer.class,s.getId()));
    }
    @Test void waitingCancellationPersistsTombstoneAndNeverCancelsNewerQuestion(){
        Session s=session();Task t=newTask(s);store.registerRequestTask(owner,UUID.randomUUID().toString(),t);t.setHarness("langgraph");
        String questionId=UUID.randomUUID().toString();Map<String,Object> runtime=new HashMap<>(Map.of("threadId",UUID.randomUUID().toString(),"generation",3,"pendingQuestion",Map.of("id",questionId,"revision",2,"workflowId","workflow-old")));
        t.setRuntime(runtime);t.setStatus("WAITING_INPUT");store.save(t);s=store.session(s.getId());s.setRuntime(new HashMap<>(runtime));store.save(s);
        assertTrue(store.cancelWaiting(s,t));Session restored=store.session(s.getId());assertNull(restored.getRuntime().get("pendingQuestion"));
        var intent=jsonTree(restored.getRuntime().get("pendingCancellation"));assertEquals(t.getId(),intent.path("runId").asText());assertEquals(questionId,intent.path("questionId").asText());assertEquals("CANCELLED",store.task(t.getId()).getStatus());
        restored.getRuntime().put("pendingQuestion",Map.of("id",UUID.randomUUID().toString(),"revision",3));store.save(restored);
        assertFalse(store.cancelWaiting(s,t));assertNotNull(store.session(s.getId()).getRuntime().get("pendingQuestion"));
    }
    @Test void delayedUiFeedbackCannotBecomeATaskAfterNewUserMessage(){
        Session s=session();Task parent=newTask(s);parent.setStatus("SUCCEEDED");store.registerRequestTask(owner,UUID.randomUUID().toString(),parent);
        Task newer=newTask(s);newer.setStatus("SUCCEEDED");store.registerRequestTask(owner,UUID.randomUUID().toString(),newer);
        Task late=newTask(s);late.setParentTaskId(parent.getId());String request=UUID.randomUUID().toString();
        assertEquals("UI_CONTINUATION_STALE",assertThrows(AgentException.class,()->store.registerRequestTask(owner,request,late)).getCode());assertNull(store.request(owner,request));assertEquals(2,store.taskCount(s.getId()));
    }
    @Test void sameGenerationHeartbeatKeepsCancellationUntilRuntimeAcknowledgesIt(){
        Session s=session();Task question=newTask(s);store.registerRequestTask(owner,UUID.randomUUID().toString(),question);
        Map<String,Object> state=new HashMap<>(Map.of("threadId",UUID.randomUUID().toString(),"generation",1,"pendingQuestion",Map.of("id",UUID.randomUUID().toString(),"revision",1,"workflowId","workflow-cancel")));
        question.setHarness("langgraph");question.setRuntime(new HashMap<>(state));question.setStatus("WAITING_INPUT");store.save(question);s=store.session(s.getId());s.setRuntime(new HashMap<>(state));store.save(s);
        Task active=newTask(s);active.setRuntime(new HashMap<>(state));active.getRuntime().put("generation",2);store.registerRequestTask(owner,UUID.randomUUID().toString(),active);active.setHarness("langgraph");
        Session worker=store.session(s.getId());worker.setRuntime(new HashMap<>(active.getRuntime()));assertTrue(store.saveSessionForTask(worker,active));
        assertTrue(store.cancelWaiting(store.session(s.getId()),question));
        assertTrue(store.saveSessionForTask(worker,active));assertNotNull(store.session(s.getId()).getRuntime().get("pendingCancellation"));assertNull(store.session(s.getId()).getRuntime().get("pendingQuestion"));
        worker.getRuntime().remove("pendingCancellation");active.setRuntime(new HashMap<>(worker.getRuntime()));assertTrue(store.saveSessionForTask(worker,active));assertNotNull(store.session(s.getId()).getRuntime().get("pendingCancellation"));
        active.setReconciledCancellationRunId(question.getId());worker.getRuntime().remove("pendingCancellation");active.setRuntime(new HashMap<>(worker.getRuntime()));assertTrue(store.saveSessionForTask(worker,active));assertNull(store.session(s.getId()).getRuntime().get("pendingCancellation"));
    }
    private com.fasterxml.jackson.databind.JsonNode jsonTree(Object value){return new ObjectMapper().valueToTree(value);}
}
