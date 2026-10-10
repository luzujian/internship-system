package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import java.util.*;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.internship.agent.business.AgentTypes.*;

class AgentTaskServiceTest {
    final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    final AgentIdentityService identity=mock(AgentIdentityService.class);
    final AgentReadTools tools=mock(AgentReadTools.class);
    final AgentStore store=mock(AgentStore.class);
    final AgentModel model=mock(AgentModel.class);
    final Actor student=new Actor("ROLE_STUDENT:1:false:",1L,"ROLE_STUDENT","测试学生",null,null,null,false,Set.of());
    final Map<String,Task> taskData=new ConcurrentHashMap<>();
    final Map<String,Session> sessions=new ConcurrentHashMap<>();
    final Map<String,String> requests=new ConcurrentHashMap<>();
    final Set<String> cancellations=ConcurrentHashMap.newKeySet();
    final SystemConfigService capsConfig=mock(SystemConfigService.class);
    AgentTaskService service;

    @BeforeEach void setup() throws Exception {
        when(identity.resolve("Bearer test")).thenReturn(student);
        when(tools.scopeFingerprint(student)).thenReturn("scope-1");
        when(tools.specs(any())).thenReturn(List.of(new AgentReadTools.Spec("student_todos","查询本人待办","{}"),new AgentReadTools.Spec("ask_user","澄清","{}")));
        when(tools.serialize(any())).thenAnswer(call->json.writeValueAsString(call.getArgument(0)));
        when(model.review(anyList())).thenAnswer(call -> {
            List<org.springframework.ai.chat.messages.Message> messages = call.getArgument(0);
            int count = json.readTree(messages.getLast().getText()).path("parts").size();
            return reactor.core.publisher.Flux.just(response(json.writeValueAsString(Map.of("keep",
                    java.util.stream.IntStream.range(0, count).boxed().toList()))));
        });
        when(store.lock(anyString(),anyString())).thenReturn(true);
        when(store.request(anyString(),anyString())).thenAnswer(call->requests.get(call.getArgument(1)));
        when(store.claimRequest(anyString(),anyString(),anyString())).thenAnswer(call->requests.putIfAbsent(call.getArgument(1),call.getArgument(2))==null);
        when(store.registerRequestTask(anyString(),anyString(),any())).thenAnswer(call->{Task t=call.getArgument(2);if(requests.putIfAbsent(call.getArgument(1),t.getId())!=null)return false;store.save(t);Session s=store.session(t.getSessionId());s.getTaskIds().add(t.getId());if(s.getTitle()==null)s.setTitle(t.getMessage());store.save(s);return true;});
        when(store.saveSessionForTask(any(),any())).thenAnswer(call->{Session s=call.getArgument(0);Task t=call.getArgument(1);if(cancellations.contains(t.getId()))return false;store.save(t);store.save(s);return true;});
        AtomicLong version=new AtomicLong();
        doAnswer(call->{Task t=call.getArgument(0);t.setVersion(version.incrementAndGet());t.setUpdatedAt(System.currentTimeMillis());taskData.put(t.getId(),copy(t,Task.class));return null;}).when(store).save(any(Task.class));
        doAnswer(call->{Session s=call.getArgument(0);sessions.put(s.getId(),copy(s,Session.class));return null;}).when(store).save(any(Session.class));
        doAnswer(call->{Session s=call.getArgument(0);sessions.put(s.getId(),copy(s,Session.class));return null;}).when(store).metadata(any(Session.class));
        when(store.task(anyString())).thenAnswer(call->copy(taskData.get(call.getArgument(0)),Task.class));
        when(store.session(anyString())).thenAnswer(call->copy(sessions.get(call.getArgument(0)),Session.class));
        // Install before workers start: late doReturn stubbing can be consumed by a background title read.
        when(store.sessions(anyString())).thenAnswer(call->{
            String owner=call.getArgument(0);List<Session> owned=new ArrayList<>();
            for(Session session:sessions.values())if(owner.equals(session.getOwnerKey()))owned.add(copy(session,Session.class));
            return owned;
        });
        when(store.cancelled(anyString())).thenAnswer(call->cancellations.contains(call.getArgument(0)));
        when(store.cancel(anyString())).thenAnswer(call->{String id=call.getArgument(0);Task task=taskData.get(id);if(task.terminal())return false;cancellations.add(id);task.setStatus("CANCELLED");return true;});
        service=new AgentTaskService(identity,tools,model,store,json,mock(SystemConfigService.class),new AgentCapabilityService(capsConfig,json),"true");
    }
    @AfterEach void close(){service.close();}
    @Test void companyCapabilityQuestionUsesLiveCompanyCatalogWithoutModelOrUnrelatedFeatures() throws Exception {
        Actor company=new Actor("company-7",7L,"ROLE_COMPANY","测试企业",null,null,null,false,Set.of());
        when(identity.resolve("Bearer test")).thenReturn(company);
        when(tools.scopeFingerprint(company)).thenReturn("company-scope");
        when(tools.specs(company)).thenReturn(List.of(
                new AgentReadTools.Spec("company_jobs","岗位","{}"),
                new AgentReadTools.Spec("company_applications","申请","{}"),
                new AgentReadTools.Spec("knowledge_lookup","系统知识","{}")));
        Task task=awaitTask(service.create("Bearer test",input(null,"你知道什么",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());
        assertEquals("我可以查询招聘岗位、收到的学生申请。也可以解答你在当前端使用系统时遇到的操作问题。",task.getAnswer());
        assertEquals(0,task.getModelCalls());
        verify(model,never()).stream(anyList(),anyList());
        assertFalse(task.getAnswer().matches(".*(教师|自动任务|未开通|待处理的实习确认).*"));
    }
    <T>T copy(T value,Class<T> type)throws Exception{return json.readValue(json.writeValueAsString(value),type);}
    ToolResult result(String name){return new ToolResult(name,"待办","OK","2026-09-14T10:00:00+08:00","本人",List.of(Map.of("title","参加面试")),1,false,Map.of(),List.of(),List.of());}
    ChatResponse response(String text){return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));}
    ChatResponse call(String name,String args){return new ChatResponse(List.of(new Generation(new AssistantMessage("",Map.of(),List.of(new AssistantMessage.ToolCall(UUID.randomUUID().toString(),"function",name,args))))));}
    CreateTask input(String session,String text,String objectId){return new CreateTask(session,UUID.randomUUID().toString(),text,new PageContext("applications","/student/applications","我的申请","detail",objectId==null?null:new BusinessObject("job_application",objectId),List.of(),Map.of(),1));}
    Task awaitTask(String id)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);Task task;do{task=store.task(id);if(task.terminal())return task;Thread.sleep(10);}while(System.nanoTime()<end);fail("Task did not finish");return task;}

    @Test void burstOfStreamChunksDoesNotResolveIdentityForEveryToken() throws Exception {
        when(model.stream(anyList(),anyList())).thenReturn(reactor.core.publisher.Flux.fromIterable(
                Collections.nCopies(100,response("x"))));
        Task task=new Task();task.setId("stream-test");task.setActor(student);task.setOwnerKey(student.ownerKey());
        task.setScopeFingerprint("scope-1");task.setCreatedAt(System.currentTimeMillis()-91_000);
        task.setStartedAt(System.currentTimeMillis());
        var method=AgentTaskService.class.getDeclaredMethod("streamModel",String.class,Task.class,List.class,List.class);
        method.setAccessible(true);
        assertNotNull(method.invoke(service,"Bearer test",task,List.of(),List.of()));
        // Includes a full initial permission check. The old path made 100 of each read.
        verify(identity,atMost(2)).resolve("Bearer test");
        verify(store,atMost(2)).cancelled(task.getId());
        verify(capsConfig,atMost(6)).findByConfigKey(anyString());
    }

    @Test void queuedTaskExpiresWhileTheOnlyWorkerIsStillBusyAndFreesItsSlot() throws Exception {
        service.close();
        var limits=new com.internship.config.AiRuntimeProperties();limits.setAgentWorkers(1);
        limits.setAgentQueueCapacity(1);limits.setAgentQueueTimeoutMillis(3000);
        service=new AgentTaskService(identity,tools,model,store,json,mock(SystemConfigService.class),
                new AgentCapabilityService(capsConfig,json),new AgentFeatureGate(null,"true"),limits);
        CountDownLatch entered=new CountDownLatch(1);
        when(model.stream(anyList(),anyList())).thenReturn(reactor.core.publisher.Flux.defer(()->{
            entered.countDown();return reactor.core.publisher.Flux.never();
        }));
        Task active=service.create("Bearer test",input(null,"查询",null));
        assertTrue(entered.await(3,TimeUnit.SECONDS));
        Task queued=service.create("Bearer test",input(null,"查询",null));
        assertEquals("QUEUED",queued.getStatus());
        limits.setAgentQueueTimeoutMillis(100);
        Thread.sleep(130);service.expireQueuedTasks();
        Task expired=store.task(queued.getId());
        assertEquals("QUEUE_TIMEOUT",expired.getErrorCode());assertEquals("FAILED",expired.getStatus());
        assertEquals(0,expired.getModelCalls());
        assertEquals("RUNNING",store.task(active.getId()).getStatus());
        Task replacement=service.create("Bearer test",input(null,"查询",null));
        assertEquals("QUEUED",replacement.getStatus());
        service.cancel(student,replacement.getId());service.cancel(student,active.getId());
    }

    @Test void streamRechecksRevokedPermissionsAfterTheRefreshInterval() throws Exception {
        service.close();
        var limits=new com.internship.config.AiRuntimeProperties();limits.setStreamAuthRefreshMillis(10);
        service=new AgentTaskService(identity,tools,model,store,json,mock(SystemConfigService.class),
                new AgentCapabilityService(capsConfig,json),new AgentFeatureGate(null,"true"),limits);
        when(model.stream(anyList(),anyList())).thenReturn(reactor.core.publisher.Flux.concat(
                reactor.core.publisher.Flux.just(response("first")),
                reactor.core.publisher.Flux.defer(()->{
                    when(identity.resolve("Bearer test")).thenThrow(AgentIdentityService.forbidden());
                    return reactor.core.publisher.Flux.just(response("second"));
                }).delaySubscription(Duration.ofMillis(40))));
        Task task=new Task();task.setId("stream-test");task.setActor(student);task.setOwnerKey(student.ownerKey());
        task.setScopeFingerprint("scope-1");task.setCreatedAt(System.currentTimeMillis());task.setStartedAt(System.currentTimeMillis());
        var method=AgentTaskService.class.getDeclaredMethod("streamModel",String.class,Task.class,List.class,List.class);
        method.setAccessible(true);
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,
                ()->method.invoke(service,"Bearer test",task,List.of(),List.of()));
        assertEquals("FORBIDDEN",((AgentException)failure.getCause()).getCode());
    }

    @Test void titleBacklogIsBoundedAndFailedTasksNeverEnqueueTitles() throws Exception {
        service.close();
        var limits=new com.internship.config.AiRuntimeProperties();limits.setTitleQueueCapacity(1);
        service=new AgentTaskService(identity,tools,model,store,json,mock(SystemConfigService.class),
                new AgentCapabilityService(capsConfig,json),new AgentFeatureGate(null,"true"),limits);
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        when(store.taskCount(anyString())).thenReturn(1);
        when(model.call(anyList(),anyList())).thenAnswer(invocation->{entered.countDown();release.await(3,TimeUnit.SECONDS);return response("AI标题");});
        var method=AgentTaskService.class.getDeclaredMethod("upgradeTitleInBackground",Task.class);method.setAccessible(true);
        var field=AgentTaskService.class.getDeclaredField("titleWorkers");field.setAccessible(true);
        var titles=(ThreadPoolExecutor)field.get(service);
        try{
            for(int index=0;index<3;index++){
                String id=UUID.randomUUID().toString();Session session=new Session();session.setId(id);session.setOwnerKey(student.ownerKey());session.setTitle("首问");store.save(session);
                Task task=new Task();task.setSessionId(id);task.setMessage("首问");task.setAnswer("回答");task.setModelCalls(1);task.setStatus("SUCCEEDED");
                method.invoke(service,task);if(index==0)assertTrue(entered.await(2,TimeUnit.SECONDS));
            }
            assertEquals(1,titles.getQueue().size());
            Task failed=new Task();failed.setSessionId(UUID.randomUUID().toString());failed.setModelCalls(1);failed.setStatus("FAILED");
            method.invoke(service,failed);assertEquals(1,titles.getQueue().size());
        }finally{release.countDown();}
    }

    @Test void queryFailureStopsInsteadOfAllowingAnInventedZero()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("没有待办"));
        when(tools.execute(student,"student_todos","{}")).thenThrow(new IllegalStateException("database unavailable"));
        Task task=service.create("Bearer test",input(null,"我的待办",null));
        task=awaitTask(task.getId());
        assertEquals("FAILED",task.getStatus());assertEquals("TOOL_UNAVAILABLE",task.getErrorCode());
        assertNotEquals("没有待办",task.getAnswer());verify(model,times(1)).call(anyList(),anyList());
    }

    @Test void countingApplicationsRequiresDedicatedEvidenceEvenAfterKnowledge()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("knowledge_lookup","{}"),response("没有申请"),call("student_applications","{}"),response("你投递了3个岗位。"));
        when(tools.execute(student,"knowledge_lookup","{}")).thenReturn(result("knowledge_lookup"));
        when(tools.execute(student,"student_applications","{}")).thenReturn(result("student_applications"));
        Task task=awaitTask(service.create("Bearer test",input(null,"我申请了几个岗位",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("你投递了3个岗位。",task.getAnswer());
        verify(model,times(4)).call(anyList(),anyList());
    }

    @Test void performsNativeToolLoopAndReturnsEvidence()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("有1项面试待办。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task finalTask=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("SUCCEEDED",finalTask.getStatus());assertEquals(1,finalTask.getCards().getFirst().total());assertEquals(3,finalTask.getModelCalls());
        var messages=org.mockito.ArgumentCaptor.forClass(List.class);verify(model,times(2)).call(messages.capture(),anyList());
        assertTrue(messages.getAllValues().getLast().stream().anyMatch(item->item instanceof ToolResponseMessage));
    }
    @Test void identityAndSessionCannotBeBorrowed()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("ask_user","{\"question\":\"哪个岗位？\"}"));
        when(tools.execute(any(),eq("ask_user"),anyString())).thenReturn(new ToolResult("ask_user","澄清","OK","now","本人",List.of(),0,false,Map.of("question","哪个岗位？"),List.of(),List.of()));
        Task t=awaitTask(service.create("Bearer test",input(null,"分析岗位",null)).getId());
        assertEquals("WAITING_INPUT",t.getStatus());
        Actor other=new Actor("ROLE_STUDENT:2:false:",2L,"ROLE_STUDENT","其他学生",null,null,null,false,Set.of());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.get(other,t.getId())).getCode());
        when(identity.resolve("Bearer other")).thenReturn(other);
        assertThrows(AgentException.class,()->service.create("Bearer other",input(t.getSessionId(),"继续",null)));
    }
    @Test void followUpRetainsHistoryButUsesTheNewObject()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_progress","{\"objectId\":\"10\"}"),response("先处理面试。"),call("student_progress","{\"objectId\":\"20\"}"),response("新岗位需要另行核对。"));
        when(tools.execute(eq(student),eq("student_progress"),anyString())).thenAnswer(c->new ToolResult("student_progress","申请","OK","now","本人",List.of(Map.of("id",json.readTree(c.<String>getArgument(2)).path("objectId").asText(),"objectType","job_application")),1,false,Map.of(),List.of(),List.of()));
        Task first=awaitTask(service.create("Bearer test",input(null,"这个岗位", "10")).getId());
        Task second=awaitTask(service.create("Bearer test",input(first.getSessionId(),"这个呢", "20")).getId());
        assertEquals("10",first.getPageContext().object().id());assertEquals("20",second.getPageContext().object().id());
        var messages=org.mockito.ArgumentCaptor.forClass(List.class);verify(model,times(4)).call(messages.capture(),anyList());
        String prompt=messages.getAllValues().getLast().toString();assertTrue(prompt.contains("先处理面试"));assertTrue(prompt.contains("20"));
    }
    @Test void lostCreateResponseCanRetryWithoutExecutingTwice()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("已查询"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        CreateTask input=input(null,"待办",null);Task first=awaitTask(service.create("Bearer test",input).getId());
        assertEquals(first.getId(),service.create("Bearer test",input).getId());verify(model,times(2)).call(anyList(),anyList());
    }
    @Test void cancellationStopsBeforeAnyToolAfterProviderReturns()throws Exception{
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        when(model.call(anyList(),anyList())).thenAnswer(c->{started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return call("student_todos","{}");});
        Task task=service.create("Bearer test",input(null,"待办",null));assertTrue(started.await(2,TimeUnit.SECONDS));
        service.cancel(student,task.getId());release.countDown();assertEquals("CANCELLED",awaitTask(task.getId()).getStatus());verify(tools,never()).execute(any(),anyString(),anyString());
    }
    @Test void changedScopeInvalidatesCachedAnswersAndFollowUps()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("已查询"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"查询",null)).getId());
        when(tools.scopeFingerprint(student)).thenReturn("scope-2");
        assertEquals("SCOPE_CHANGED",assertThrows(AgentException.class,()->service.get(student,task.getId())).getCode());
        assertEquals("SCOPE_CHANGED",assertThrows(AgentException.class,()->service.create("Bearer test",input(task.getSessionId(),"继续",null))).getCode());
    }
    @Test void ungroundedTextCannotBecomeASuccessfulBusinessAnswer()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(response("你已经录取了。"));
        Task task=awaitTask(service.create("Bearer test",input(null,"申请进度",null)).getId());
        assertEquals("FAILED",task.getStatus());assertTrue(task.getAnswer().isEmpty());assertEquals("BUDGET_EXCEEDED",task.getErrorCode());
    }
    @Test void repeatedReadReusesEvidenceInsteadOfExecutingAgain()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),call("student_todos","{}"),response("已核验待办"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(1,task.getCards().size());
        verify(tools,times(1)).execute(student,"student_todos","{}");
    }
    @Test void companyWideQuestionDoesNotRequireUnrelatedOpenJobEvidence()throws Exception{
        Actor company=new Actor("ROLE_COMPANY:1:false:",1L,"ROLE_COMPANY","企业",null,null,null,false,Set.of());
        when(identity.resolve("Bearer test")).thenReturn(company);when(tools.scopeFingerprint(company)).thenReturn("scope-company");
        when(model.call(anyList(),anyList())).thenReturn(call("company_applications","{}"),response("可以查询本企业收到的学生申请。"));
        when(tools.execute(company,"company_applications","{}")).thenReturn(result("company_applications"));
        var page=new PageContext("jobs","/company/jobs","招聘管理","detail",new BusinessObject("company_job","10"),List.of(),Map.of(),1);
        Task task=awaitTask(service.create("Bearer test",new CreateTask(null,UUID.randomUUID().toString(),"你能获取到申请了我公司岗位的学生的信息吗",page)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(3,task.getModelCalls());
    }
    @Test void cachedReadMustBeReauthorized()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),call("student_todos","{}"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        doNothing().doThrow(AgentIdentityService.forbidden()).when(tools).requireAvailable(student,"student_todos");
        Task task=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("FORBIDDEN",task.getErrorCode());
        verify(tools,times(1)).execute(student,"student_todos","{}");
    }
    @Test void malformedArgumentsCanBeCorrectedWithoutClaimingServiceFailure()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{"),call("student_todos","{}"),response("已核验待办"));
        when(tools.execute(student,"student_todos","{")).thenThrow(new AgentException("INVALID_TOOL_INPUT","参数无效",400));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(1,task.getCards().size());
    }
    @Test void unknownOrWriteToolFailsClosed()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("submit_application","{}"));
        when(tools.execute(student,"submit_application","{}")).thenThrow(AgentIdentityService.forbidden());
        Task task=awaitTask(service.create("Bearer test",input(null,"提交申请",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("FORBIDDEN",task.getErrorCode());assertTrue(task.getCards().isEmpty());
    }
    @Test void chatCapabilityDisabledYieldsForbidden(){
        com.internship.entity.SystemConfig row=new com.internship.entity.SystemConfig();
        row.setConfigValue("{\"roleDefaults\":{\"student\":[]}}");
        when(capsConfig.findByConfigKey(anyString())).thenReturn(row);
        AgentException e=assertThrows(AgentException.class,()->service.create("Bearer test",input(null,"你好",null)));
        assertEquals("CAPABILITY_DISABLED",e.getCode());
    }
    @Test void teacherSummaryToolResultPersistsLastScopeForFollowUps()throws Exception{
        Actor teacher=new Actor("ROLE_TEACHER_COLLEGE:47:false:",47L,"ROLE_TEACHER_COLLEGE","学院教师","COLLEGE",24L,null,false,Set.of("view:student_tracking"));
        when(identity.resolve("Bearer teacher")).thenReturn(teacher);
        when(tools.scopeFingerprint(teacher)).thenReturn("teacher-scope");
        when(model.call(anyList(),anyList())).thenReturn(call("teacher_internship_summary","{\"scopeType\":\"COLLEGE\",\"view\":\"summary\"}"),response("共205人，30人尚无Offer。"));
        when(tools.execute(eq(teacher),eq("teacher_internship_summary"),anyString(),any(Session.class))).thenAnswer(call->{
            Session session=call.getArgument(3);
            session.setLastTeacherSummaryQuery(new TeacherSummaryQuery("COLLEGE",24L,"生物医学工程学院","summary",null));
            return result("teacher_internship_summary");
        });
        Task task=awaitTask(service.create("Bearer teacher",input(null,"我们学院实习情况怎么样",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());
        Session saved=sessions.get(task.getSessionId());
        assertNotNull(saved.getLastTeacherSummaryQuery());
        assertEquals("COLLEGE",saved.getLastTeacherSummaryQuery().scopeType());
        verify(tools).execute(eq(teacher),eq("teacher_internship_summary"),anyString(),any(Session.class));
    }

@Test void cancelPersistsCancellingAndIsIdempotent()throws Exception{
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        when(model.call(anyList(),anyList())).thenAnswer(c->{started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return call("student_todos","{}");});
        Task task=service.create("Bearer test",input(null,"待办",null));assertTrue(started.await(2,TimeUnit.SECONDS));
        Task first=service.cancel(student,task.getId());
        assertEquals("CANCELLED",first.getStatus());
        // 其他观察者经 GET 可见请求停止态（持久化版本），重复取消结果一致且不叠加版本。
        Task visible=service.get(student,task.getId());
        assertEquals("CANCELLED",visible.getStatus());
        Task second=service.cancel(student,task.getId());
        assertEquals("CANCELLED",second.getStatus());
        assertEquals(visible.getVersion(),second.getVersion());
        release.countDown();assertEquals("CANCELLED",awaitTask(task.getId()).getStatus());
    }
    @Test void streamingKeepsDraftPrivateAndStopCancelsProviderWithoutWaiting()throws Exception{
        AtomicInteger calls=new AtomicInteger();AtomicBoolean providerCancelled=new AtomicBoolean();
        when(model.stream(anyList(),anyList())).thenAnswer(invocation->calls.getAndIncrement()==0
                ?reactor.core.publisher.Flux.just(call("student_todos","{}"))
                :reactor.core.publisher.Flux.concat(reactor.core.publisher.Flux.just(response("已核对，")),
                    reactor.core.publisher.Flux.<ChatResponse>never()).doOnCancel(()->providerCancelled.set(true)));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task started=service.create("Bearer test",input(null,"我的待办",null));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(calls.get()<2&&System.nanoTime()<deadline)Thread.sleep(10);
        assertEquals(2,calls.get());
        assertEquals("",store.task(started.getId()).getAnswer());
        Task stopped=service.cancel(student,started.getId());
        assertEquals("CANCELLED",stopped.getStatus());assertEquals("",stopped.getAnswer());
        deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(!providerCancelled.get()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertTrue(providerCancelled.get(),"Stopping must dispose the provider stream");
        assertEquals("",awaitTask(started.getId()).getAnswer());
        verify(model,never()).call(anyList(),anyList());
    }

    @Test void rejectedDraftNeverReachesSnapshotsOrHistory() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("没有待办。教师简报未开通。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        doReturn(reactor.core.publisher.Flux.just(response("{\"keep\":[0]}"))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("没有待办。",task.getAnswer());
        assertEquals("没有待办。",store.session(task.getSessionId()).getTurns().getLast().answer());
        assertEquals(3,task.getModelCalls());
    }

    @Test void malformedReviewFailsClosedWithoutRememberingDraft() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("没有待办。教师简报未开通。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        doReturn(reactor.core.publisher.Flux.just(response("回答看起来没问题"))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("ANSWER_CHECK_FAILED",task.getErrorCode());
        assertEquals("",task.getAnswer());assertTrue(store.session(task.getSessionId()).getTurns().isEmpty());
        verify(model,times(2)).review(anyList());
    }

    @Test void malformedReviewRetriesOnlyTheReviewAndPublishesOnlyTheAcceptedAnswer() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("没有待办。教师简报未开通。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        doReturn(reactor.core.publisher.Flux.just(response("{\"keep\":")),
                reactor.core.publisher.Flux.just(response("{\"keep\":[0]}"))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("没有待办。",task.getAnswer());
        assertEquals(4,task.getModelCalls());assertEquals(1,task.getToolCalls());
        assertEquals("没有待办。",store.session(task.getSessionId()).getTurns().getLast().answer());
        assertEquals(1,task.getSteps().stream().filter(step->"answer_review".equals(step.tool())).count());
        verify(model,times(2)).review(anyList());verify(tools,times(1)).execute(student,"student_todos","{}");
    }

    @Test void truncatedReviewIsRetriedEvenWhenItsPartialJsonIsParseable() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("尚未提交，只有点击确认后才会发送。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        var truncated=new ChatResponse(List.of(new Generation(new AssistantMessage("{\"keep\":[1]}"),
                org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("length").build())));
        doReturn(reactor.core.publisher.Flux.just(truncated),
                reactor.core.publisher.Flux.just(response("{\"keep\":[0,1]}"))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"发送了吗",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("尚未提交，只有点击确认后才会发送。",task.getAnswer());
        verify(model,times(2)).review(anyList());
    }

    @Test void emptyReviewRetriesOnceButSemanticRejectionDoesNot() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("本企业有两个岗位。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        doReturn(reactor.core.publisher.Flux.empty(),
                reactor.core.publisher.Flux.just(response("{\"keep\":[]}"))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("ANSWER_CHECK_FAILED",task.getErrorCode());
        assertEquals("",task.getAnswer());assertTrue(store.session(task.getSessionId()).getTurns().isEmpty());
        verify(model,times(2)).review(anyList());
    }

    @Test void irrelevantDraftCannotBecomePublishableThroughAnotherReviewAttempt() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("本企业有两个岗位。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        doReturn(reactor.core.publisher.Flux.just(response("{\"keep\":[]}"))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("",task.getAnswer());
        verify(model,times(1)).review(anyList());
    }

    @Test void revokedAuthorizationPreventsReviewRetryAndPublication() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("没有待办。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        AtomicBoolean revoked=new AtomicBoolean();
        when(identity.resolve("Bearer test")).thenAnswer(call->{if(revoked.get())throw AgentIdentityService.forbidden();return student;});
        doReturn(reactor.core.publisher.Flux.<ChatResponse>empty().doOnComplete(()->revoked.set(true))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("FORBIDDEN",task.getErrorCode());
        assertEquals("",task.getAnswer());assertTrue(store.session(task.getSessionId()).getTurns().isEmpty());
        verify(model,times(1)).review(anyList());
    }

    @Test void reviewDoesNotRetryProviderFailures() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("没有待办。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        doReturn(reactor.core.publisher.Flux.error(new AgentException("MODEL_UNAVAILABLE","模型暂时不可用",503))).when(model).review(anyList());
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("MODEL_UNAVAILABLE",task.getErrorCode());
        assertEquals("",task.getAnswer());verify(model,times(1)).review(anyList());
    }

    @Test void expiredTaskBudgetPreventsReviewRetry() throws Exception {
        Task task=new Task();task.setId("review-budget");task.setActor(student);task.setOwnerKey(student.ownerKey());
        task.setScopeFingerprint("scope-1");task.setMessage("我的待办");task.setCreatedAt(System.currentTimeMillis());
        task.setStartedAt(System.currentTimeMillis());
        doReturn(reactor.core.publisher.Flux.<ChatResponse>empty().doOnComplete(()->task.setStartedAt(System.currentTimeMillis()-120_001)))
                .when(model).review(anyList());
        var method=AgentTaskService.class.getDeclaredMethod("reviewAnswer",String.class,Task.class,Session.class,String.class);
        method.setAccessible(true);
        var failure=assertThrows(java.lang.reflect.InvocationTargetException.class,
                ()->method.invoke(service,"Bearer test",task,new Session(),"没有待办。"));
        assertEquals("BUDGET_EXCEEDED",((AgentException)failure.getCause()).getCode());
        assertEquals("",task.getAnswer());verify(model,times(1)).review(anyList());
    }

    @Test void cancellingReviewDisposesProviderAndPublishesNoDraft() throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("未经检查的回答。"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        CountDownLatch reviewing=new CountDownLatch(1);AtomicBoolean cancelled=new AtomicBoolean();
        doReturn(reactor.core.publisher.Flux.<ChatResponse>never()
                .doOnSubscribe(s->reviewing.countDown()).doOnCancel(()->cancelled.set(true))).when(model).review(anyList());
        Task started=service.create("Bearer test",input(null,"我的待办",null));
        assertTrue(reviewing.await(3,TimeUnit.SECONDS));assertEquals("",store.task(started.getId()).getAnswer());
        service.cancel(student,started.getId());
        assertEquals("CANCELLED",awaitTask(started.getId()).getStatus());assertTrue(cancelled.get());
        assertTrue(store.session(started.getSessionId()).getTurns().isEmpty());
    }

    @Test void verifiedFieldAnswerBypassesGenerationAndReview() throws Exception {
        Actor company=new Actor("company-7",7L,"ROLE_COMPANY","测试企业",null,null,null,false,Set.of());
        when(identity.resolve("Bearer test")).thenReturn(company);when(tools.scopeFingerprint(company)).thenReturn("company-scope");
        AgentKnowledge knowledge=new AgentKnowledge(json);
        when(tools.execute(eq(company),eq("knowledge_lookup"),anyString())).thenAnswer(call->
                knowledge.lookup(company,json.readTree(call.<String>getArgument(2)).path("topic").asText()));
        Task task=awaitTask(service.create("Bearer test",input(null,"发布岗位的时候可以选择招多少学生吗",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("可以，在招聘管理发布或编辑岗位时填写「计划招聘人数」即可。",task.getAnswer());
        assertEquals(0,task.getModelCalls());assertEquals(1,task.getToolCalls());
        verify(tools).requireAvailable(company,"knowledge_lookup");verifyNoInteractions(model);
    }
    @Test void businessQueriesExposeRefreshInsteadOfRegenerate()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("有待办"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        assertEquals("QUERY",task.getResponseKind());assertEquals(List.of("EDIT","REFRESH_QUERY"),task.getAllowedOperations());
        assertEquals("OPERATION_UNAVAILABLE",assertThrows(AgentException.class,()->service.resend("Bearer test",task.getId(),new ResendTask(UUID.randomUUID().toString(),null,"REGENERATE"))).getCode());
    }
    @Test void cancelAfterSuccessKeepsRealResult()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("已查询"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task done=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        assertEquals("SUCCEEDED",done.getStatus());
        Task after=service.cancel(student,done.getId());
        assertEquals("SUCCEEDED",after.getStatus());
    }
    @Test void resendRereadsLiveStateAndDropsOldBranch()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("A结果"),call("student_todos","{}"),response("B结果"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task first=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        assertEquals("SUCCEEDED",first.getStatus());
        assertNotNull(first.getTurnIndex());
        Task second=awaitTask(service.resend("Bearer test",first.getId(),new ResendTask(UUID.randomUUID().toString(),null)).getId());
        assertEquals("SUCCEEDED",second.getStatus());
        assertEquals("B结果",second.getAnswer());
        assertEquals(first.getId(),second.getEditOf());
        var messages=org.mockito.ArgumentCaptor.forClass(List.class);verify(model,times(4)).call(messages.capture(),anyList());
        assertFalse(messages.getAllValues().get(2).toString().contains("A结果"));
    }
    @Test void resendWhileRunningIsRejected()throws Exception{
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        when(model.call(anyList(),anyList())).thenAnswer(c->{started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return call("student_todos","{}");});
        Task task=service.create("Bearer test",input(null,"待办",null));assertTrue(started.await(2,TimeUnit.SECONDS));
        AgentException e=assertThrows(AgentException.class,()->service.resend("Bearer test",task.getId(),new ResendTask(UUID.randomUUID().toString(),null)));
        assertEquals("TASK_RUNNING",e.getCode());
        release.countDown();awaitTask(task.getId());
    }
    @Test void resendKeepsOriginalObject()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_progress","{}"),response("旧申请已通过。"),call("student_progress","{}"),response("旧申请仍在审核。"));
        when(tools.execute(eq(student),eq("student_progress"),anyString())).thenAnswer(c->new ToolResult("student_progress","申请","OK","now","本人",List.of(Map.of("id","10","objectType","job_application")),1,false,Map.of(),List.of(),List.of()));
        Task first=awaitTask(service.create("Bearer test",input(null,"这个岗位","10")).getId());
        Task second=awaitTask(service.resend("Bearer test",first.getId(),new ResendTask(UUID.randomUUID().toString(),"这个呢")).getId());
        assertEquals("SUCCEEDED",second.getStatus());
        assertEquals("10",second.getPageContext().object().id());
        assertEquals("这个呢",second.getMessage());
        assertEquals("旧申请仍在审核。",second.getAnswer());
    }
    @Test void sessionHistoryListsOwnSessionsWithMetadata()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("已查询"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"我的待办",null)).getId());
        var rows=service.sessions(student);
        assertEquals(1,rows.size());
        assertEquals(task.getSessionId(),rows.get(0).get("id"));
        assertEquals("我的待办",rows.get(0).get("title"));
        assertEquals(false,rows.get(0).get("archived"));
        assertEquals(false,rows.get(0).get("hasRunningTask"));
    }

    @Test void sessionRenameAndArchiveOnlyTouchSessionMetadata()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("已查询"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        Session stored=sessions.get(task.getSessionId());
        service.renameSession(student,stored.getId(),"毕业季找工作");
        service.archiveSession(student,stored.getId(),true);
        Session after=sessions.get(stored.getId());
        assertEquals("毕业季找工作",after.getTitle());
        assertTrue(after.isArchived());
        // 消息与任务标识不受重命名/归档影响
        assertEquals(stored.getTaskIds().size(),after.getTaskIds().size());
    }

    @Test void sessionDeleteRefusesWhileForegroundTaskIsRunning()throws Exception{
        CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
        when(model.call(anyList(),anyList())).thenAnswer(c->{started.countDown();assertTrue(release.await(3,TimeUnit.SECONDS));return call("student_todos","{}");});
        Task task=service.create("Bearer test",input(null,"待办",null));assertTrue(started.await(2,TimeUnit.SECONDS));
        AgentException e=assertThrows(AgentException.class,()->service.deleteSession(student,task.getSessionId()));
        assertEquals("TASK_RUNNING",e.getCode());
        verify(store,never()).removeSession(anyString(),anyString());
        release.countDown();awaitTask(task.getId());
    }

    @Test void sessionDeleteRemovesOnlyTheSessionRecord()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("已查询"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        Session stored=sessions.get(task.getSessionId());
        doReturn(true).when(store).removeSession(student.ownerKey(),stored.getId());
        var receipt=service.deleteSession(student,stored.getId());
        assertEquals(true,receipt.get("deleted"));
        verify(store).removeSession(student.ownerKey(),stored.getId());
        // 任务本身不被删除：业务记录与审计由各自服务管理，不存在级联删除。
        assertNotNull(store.task(task.getId()));
    }

    @Test void anotherActorCannotRenameOrDeleteSomeoneElsesSession()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_todos","{}"),response("已查询"));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task task=awaitTask(service.create("Bearer test",input(null,"待办",null)).getId());
        Actor other=new Actor("ROLE_STUDENT:2:false:",2L,"ROLE_STUDENT","别人",null,null,null,false,Set.of());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.renameSession(other,task.getSessionId(),"x")).getCode());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.deleteSession(other,task.getSessionId())).getCode());
    }
    @Test void priorObjectEvidenceCannotCompleteACurrentObjectQuestion()throws Exception{
        when(model.call(anyList(),anyList())).thenReturn(call("student_progress","{\"objectId\":\"10\"}"),response("旧申请已通过。"),call("student_progress","{\"objectId\":\"20\"}"),response("当前申请待审核。"));
        when(tools.execute(eq(student),eq("student_progress"),anyString())).thenAnswer(c->new ToolResult("student_progress","申请","OK","now","本人",List.of(Map.of("id",json.readTree(c.<String>getArgument(2)).path("objectId").asText(),"objectType","job_application")),1,false,Map.of(),List.of(),List.of()));
        Task task=awaitTask(service.create("Bearer test",input(null,"这份申请呢？","20")).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("当前申请待审核。",task.getAnswer());assertEquals(5,task.getModelCalls());
    }
    @Test void calendarQuestionRequiresTeacherSettingsRatherThanMissingPersonalDates()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_progress","{}"),response("个人记录没有日期，系统未设置。"),
                call("internship_calendar","{}"),response("统一结束日期是2026年12月31日。"));
        when(tools.execute(student,"student_progress","{}")).thenReturn(result("student_progress"));
        when(tools.execute(student,"internship_calendar","{}")).thenReturn(result("internship_calendar"));
        Task task=awaitTask(service.create("Bearer test",input(null,"我的实习什么时候结束？",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("统一结束日期是2026年12月31日。",task.getAnswer());
        assertEquals(5,task.getModelCalls());
    }
    @Test void calendarQuestionDoesNotRequireUnrelatedPageObject()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("internship_calendar","{}"),response("统一结束日期是2026年12月31日。"));
        when(tools.execute(student,"internship_calendar","{}")).thenReturn(result("internship_calendar"));
        Task task=awaitTask(service.create("Bearer test",input(null,"实习什么时候结束？","20")).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(3,task.getModelCalls());
        verify(tools,never()).execute(any(),eq("student_progress"),anyString());
    }
    @Test void failedCalendarReadEndsClearlyInsteadOfInventingMissingDates()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("internship_calendar","{}"));
        when(tools.execute(student,"internship_calendar","{}")).thenThrow(new AgentException("CALENDAR_UNAVAILABLE","暂时无法读取统一实习时间。",503));
        Task task=awaitTask(service.create("Bearer test",input(null,"实习什么时候结束？",null)).getId());
        assertEquals("FAILED",task.getStatus());assertEquals("CALENDAR_UNAVAILABLE",task.getErrorCode());
        assertTrue(task.getErrorMessage().contains("暂时无法读取"));assertEquals(1,task.getModelCalls());
    }
    @Test void systemOverviewDoesNotRequireUnrelatedCompanyJobEvidence()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("knowledge_lookup","{}"),response("主线是岗位对接、实习确认与过程管理。"));
        when(tools.execute(student,"knowledge_lookup","{}")).thenReturn(result("knowledge_lookup"));
        var page=new PageContext("jobs","/company/jobs","岗位管理","position-form",new BusinessObject("company_job","24"),List.of(),Map.of(),1);
        Task task=awaitTask(service.create("Bearer test",new CreateTask(null,UUID.randomUUID().toString(),"该系统的核心业务逻辑是什么",page)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(3,task.getModelCalls());assertEquals(1,task.getToolCalls());
        assertEquals("ANSWER",task.getResponseKind());
        verify(tools,never()).execute(any(),eq("company_jobs"),anyString());
    }
    @Test void administratorCanExplainAnotherModuleWithoutQueryingTheOpenEnterprise()throws Exception {
        Actor admin=new Actor("ROLE_ADMIN:1:false:",1L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        when(identity.resolve("Bearer test")).thenReturn(admin);
        when(tools.scopeFingerprint(admin)).thenReturn("admin-scope");
        when(model.call(anyList(),anyList())).thenReturn(call("knowledge_lookup","{}"),response("在备份管理查看备份并按页面操作恢复。"));
        when(tools.execute(admin,"knowledge_lookup","{}")).thenReturn(result("knowledge_lookup"));
        var page=new PageContext("companies","/admin/companies","企业管理","detail",new BusinessObject("admin_company","24"),List.of(),Map.of(),1);
        Task task=awaitTask(service.create("Bearer test",new CreateTask(null,UUID.randomUUID().toString(),"备份恢复怎么操作",page)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(3,task.getModelCalls());
        verify(tools,never()).execute(any(),eq("admin_review_queue"),anyString());
        var messages=org.mockito.ArgumentCaptor.forClass(List.class);verify(model,times(2)).call(messages.capture(),anyList());
        String prompt=messages.getAllValues().getFirst().toString();
        assertTrue(prompt.contains("知识可见"));assertTrue(prompt.contains("教师类型"));
        assertTrue(prompt.contains("不授予跨角色操作或他人数据访问"));
    }
    @Test void administratorSpecificEnterpriseQuestionStillRequiresThatEnterpriseEvidence()throws Exception {
        Actor admin=new Actor("ROLE_ADMIN:1:false:",1L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        when(identity.resolve("Bearer test")).thenReturn(admin);
        when(tools.scopeFingerprint(admin)).thenReturn("admin-scope");
        when(model.call(anyList(),anyList())).thenReturn(call("knowledge_lookup","{}"),response("根据通常规则解释。"),
                call("admin_review_queue","{}"),response("该企业记录仍在待审核队列，请核对资质材料。"));
        when(tools.execute(admin,"knowledge_lookup","{}")).thenReturn(result("knowledge_lookup"));
        when(tools.execute(admin,"admin_review_queue","{}")).thenReturn(new ToolResult("admin_review_queue","企业","OK","now","管理员",
                List.of(Map.of("id",24)),1,false,Map.of(),List.of(),List.of()));
        var page=new PageContext("companies","/admin/companies","企业管理","detail",new BusinessObject("admin_company","24"),List.of(),Map.of(),1);
        Task task=awaitTask(service.create("Bearer test",new CreateTask(null,UUID.randomUUID().toString(),"这个企业怎么还没有审核通过",page)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(5,task.getModelCalls());
        verify(tools).execute(admin,"admin_review_queue","{}");
    }
    @Test void studentGeneralKnowledgeDoesNotRequireAnUnrelatedApplicationRecord()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("knowledge_lookup","{}"),response("在首页自主实习入口填写单位资料和材料后提交。"));
        when(tools.execute(student,"knowledge_lookup","{}")).thenReturn(result("knowledge_lookup"));
        Task task=awaitTask(service.create("Bearer test",input(null,"自主实习需要哪些条件","20")).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(3,task.getModelCalls());
        verify(tools,never()).execute(any(),eq("student_progress"),anyString());
    }
    @Test void focusedCompanyJobUsesCompanyEvidenceAndRejectsOtherJob()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("company_jobs","{}"),response("旧岗位"),call("company_jobs","{\"page\":2}"),response("当前岗位还缺两人。"));
        when(tools.execute(student,"company_jobs","{}")).thenReturn(new ToolResult("company_jobs","岗位","OK","now","本企业",List.of(Map.of("id",6)),1,false,Map.of(),List.of(),List.of()));
        when(tools.execute(student,"company_jobs","{\"page\":2}")).thenReturn(new ToolResult("company_jobs","岗位","OK","now","本企业",List.of(Map.of("id",24)),1,false,Map.of(),List.of(),List.of()));
        var page=new PageContext("jobs","/company/jobs","岗位管理","detail",new BusinessObject("company_job","24"),List.of(),Map.of(),1);
        Task task=awaitTask(service.create("Bearer test",new CreateTask(null,UUID.randomUUID().toString(),"这个岗位还缺几个人",page)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(5,task.getModelCalls());assertEquals("当前岗位还缺两人。",task.getAnswer());
    }

}
