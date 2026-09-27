package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import java.util.*;
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
        when(store.cancelled(anyString())).thenAnswer(call->cancellations.contains(call.getArgument(0)));
        when(store.cancel(anyString())).thenAnswer(call->{String id=call.getArgument(0);Task task=taskData.get(id);if(task.terminal())return false;cancellations.add(id);task.setStatus("CANCELLED");return true;});
        service=new AgentTaskService(identity,tools,model,store,json,mock(SystemConfigService.class),new AgentCapabilityService(capsConfig,json),"true");
    }
    @AfterEach void close(){service.close();}
    <T>T copy(T value,Class<T> type)throws Exception{return json.readValue(json.writeValueAsString(value),type);}
    ToolResult result(String name){return new ToolResult(name,"待办","OK","2026-09-14T10:00:00+08:00","本人",List.of(Map.of("title","参加面试")),1,false,Map.of(),List.of(),List.of());}
    ChatResponse response(String text){return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));}
    ChatResponse call(String name,String args){return new ChatResponse(List.of(new Generation(new AssistantMessage("",Map.of(),List.of(new AssistantMessage.ToolCall(UUID.randomUUID().toString(),"function",name,args))))));}
    CreateTask input(String session,String text,String objectId){return new CreateTask(session,UUID.randomUUID().toString(),text,new PageContext("applications","/student/applications","我的申请","detail",objectId==null?null:new BusinessObject("job_application",objectId),List.of(),Map.of(),1));}
    Task awaitTask(String id)throws Exception{long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(6);Task task;do{task=store.task(id);if(task.terminal())return task;Thread.sleep(10);}while(System.nanoTime()<end);fail("Task did not finish");return task;}

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
        assertEquals("SUCCEEDED",finalTask.getStatus());assertEquals(1,finalTask.getCards().getFirst().total());assertEquals(2,finalTask.getModelCalls());
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
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(2,task.getModelCalls());
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
    @Test void streamingPublishesPartialTextAndStopCancelsProviderWithoutWaiting()throws Exception{
        AtomicInteger calls=new AtomicInteger();AtomicBoolean providerCancelled=new AtomicBoolean();
        when(model.stream(anyList(),anyList())).thenAnswer(invocation->calls.getAndIncrement()==0
                ?reactor.core.publisher.Flux.just(call("student_todos","{}"))
                :reactor.core.publisher.Flux.concat(reactor.core.publisher.Flux.just(response("已核对，")),
                    reactor.core.publisher.Flux.<ChatResponse>never()).doOnCancel(()->providerCancelled.set(true)));
        when(tools.execute(student,"student_todos","{}")).thenReturn(result("student_todos"));
        Task started=service.create("Bearer test",input(null,"我的待办",null));
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(store.task(started.getId()).getAnswer().isEmpty()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertEquals("已核对，",store.task(started.getId()).getAnswer());
        Task stopped=service.cancel(student,started.getId());
        assertEquals("CANCELLED",stopped.getStatus());assertEquals("已核对，",stopped.getAnswer());
        deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
        while(!providerCancelled.get()&&System.nanoTime()<deadline)Thread.sleep(10);
        assertTrue(providerCancelled.get(),"Stopping must dispose the provider stream");
        assertEquals("已核对，",awaitTask(started.getId()).getAnswer());
        verify(model,never()).call(anyList(),anyList());
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
        Session stored=sessions.get(task.getSessionId());
        doReturn(List.of(stored)).when(store).sessions(student.ownerKey());
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
        doReturn(List.of(stored)).when(store).sessions(student.ownerKey());
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
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("当前申请待审核。",task.getAnswer());assertEquals(4,task.getModelCalls());
    }
    @Test void calendarQuestionRequiresTeacherSettingsRatherThanMissingPersonalDates()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("student_progress","{}"),response("个人记录没有日期，系统未设置。"),
                call("internship_calendar","{}"),response("统一结束日期是2026年12月31日。"));
        when(tools.execute(student,"student_progress","{}")).thenReturn(result("student_progress"));
        when(tools.execute(student,"internship_calendar","{}")).thenReturn(result("internship_calendar"));
        Task task=awaitTask(service.create("Bearer test",input(null,"我的实习什么时候结束？",null)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals("统一结束日期是2026年12月31日。",task.getAnswer());
        assertEquals(4,task.getModelCalls());
    }
    @Test void calendarQuestionDoesNotRequireUnrelatedPageObject()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("internship_calendar","{}"),response("统一结束日期是2026年12月31日。"));
        when(tools.execute(student,"internship_calendar","{}")).thenReturn(result("internship_calendar"));
        Task task=awaitTask(service.create("Bearer test",input(null,"实习什么时候结束？","20")).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(2,task.getModelCalls());
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
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(2,task.getModelCalls());assertEquals(1,task.getToolCalls());
        assertEquals("ANSWER",task.getResponseKind());
        verify(tools,never()).execute(any(),eq("company_jobs"),anyString());
    }
    @Test void focusedCompanyJobUsesCompanyEvidenceAndRejectsOtherJob()throws Exception {
        when(model.call(anyList(),anyList())).thenReturn(call("company_jobs","{}"),response("旧岗位"),call("company_jobs","{\"page\":2}"),response("当前岗位还缺两人。"));
        when(tools.execute(student,"company_jobs","{}")).thenReturn(new ToolResult("company_jobs","岗位","OK","now","本企业",List.of(Map.of("id",6)),1,false,Map.of(),List.of(),List.of()));
        when(tools.execute(student,"company_jobs","{\"page\":2}")).thenReturn(new ToolResult("company_jobs","岗位","OK","now","本企业",List.of(Map.of("id",24)),1,false,Map.of(),List.of(),List.of()));
        var page=new PageContext("jobs","/company/jobs","岗位管理","detail",new BusinessObject("company_job","24"),List.of(),Map.of(),1);
        Task task=awaitTask(service.create("Bearer test",new CreateTask(null,UUID.randomUUID().toString(),"这个岗位还缺几个人",page)).getId());
        assertEquals("SUCCEEDED",task.getStatus());assertEquals(4,task.getModelCalls());assertEquals("当前岗位还缺两人。",task.getAnswer());
    }

}
