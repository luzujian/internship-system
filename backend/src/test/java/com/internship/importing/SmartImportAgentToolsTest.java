package com.internship.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.entity.Permission;
import com.internship.service.AdminPermissionService;
import org.junit.jupiter.api.*;
import java.util.*;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.internship.importing.SmartImportConversationStore.*;
import static com.internship.importing.SmartImportValidationTest.*;

class SmartImportAgentToolsTest {
    final ObjectMapper json=new ObjectMapper();
    final Actor admin=new Actor("ROLE_ADMIN:2:false:",2L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of("user:student:add"));
    final String contextId=UUID.randomUUID().toString(),sessionId=UUID.randomUUID().toString();
    final Map<String,AgentActionService.ActionView> ledger=new HashMap<>();
    SmartImportAgentTools tools;State state;SmartImportExecutor executor;AgentCapabilityService caps;SmartImportConversationStore store;SmartImportConversationStore.Lease lease;
    @BeforeEach void setup() {
        store=mock(SmartImportConversationStore.class);lease=mock(SmartImportConversationStore.Lease.class);var actions=mock(AgentActionService.class);
        var identity=mock(AgentIdentityService.class);caps=mock(AgentCapabilityService.class);executor=mock(SmartImportExecutor.class);
        var catalog=mock(SmartImportCatalog.class);var recognition=mock(SmartImportRecognition.class);var gate=mock(AgentFeatureGate.class);
        var permissions=mock(AdminPermissionService.class);var sessions=mock(AgentStore.class);
        var session=new AgentTypes.Session();session.setId(sessionId);session.setOwnerKey(admin.ownerKey());when(sessions.session(sessionId)).thenReturn(session);
        Permission permission=new Permission();permission.setPermissionCode("user:student:add");when(permissions.getRolePermissionsByCode("ROLE_ADMIN")).thenReturn(List.of(permission));
        when(identity.resolve("auth")).thenReturn(admin);when(caps.has(any(),eq("agent_chat"))).thenReturn(true);when(gate.enabled()).thenReturn(true);
        when(catalog.load(eq(SmartImportType.STUDENT),anyList())).thenReturn(students());
        when(recognition.recognize(any(),anyList(),any(),any(),any())).thenReturn(new SmartImportRecognition.Recognition(SmartImportType.STUDENT,
                List.of(new SmartImportRecognition.Layout("0:0",2,Map.of("学号",1,"姓名",2,"班级",3))),Map.of(),List.of()));
        when(actions.preview(any(),eq("SMART_IMPORT"),eq("student"),anyMap(),isNull())).thenAnswer(call->{
            String id=UUID.randomUUID().toString();var parameters=json.convertValue(call.getArgument(3),new com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>(){});
            var action=new AgentActionService.ActionView(id,"SMART_IMPORT","student",parameters,null,"READY",System.currentTimeMillis(),Long.MAX_VALUE,null,null,null);ledger.put(id,action);return action;
        });
        when(actions.get(any(),anyString())).thenAnswer(call->ledger.get(call.getArgument(1)));
        when(actions.execute(any(),anyString(),eq("SMART_IMPORT"),anyMap(),any())).thenAnswer(call->{
            var result=(Map<String,Object>)((Supplier<?>)call.getArgument(4)).get();String id=call.getArgument(1);result.put("actionId",id);result.put("actionStatus","SUCCEEDED");var old=ledger.get(id);
            ledger.put(id,new AgentActionService.ActionView(id,old.actionType(),old.targetKey(),old.parameters(),null,"SUCCEEDED",old.previewedAt(),old.expiresAt(),result,null,null));return result;
        });
        when(executor.execute(any(),eq(SmartImportType.STUDENT),anyList(),any())).thenAnswer(call->new LinkedHashMap<>(Map.of("successCount",((List<?>)call.getArgument(2)).size(),"initialCredentials",List.of(Map.of("username","NEW","password","secret")))));
        var imports=new SmartImportService(new SmartWorkbookReader(),recognition,catalog,executor,actions,identity,json);
        tools=new SmartImportAgentTools(store,new SmartWorkbookReader(),imports,catalog,identity,actions,sessions,gate,caps,permissions,json);
        state=new State();state.setId(contextId);state.setSessionId(sessionId);state.setOwnerKey(admin.ownerKey());
        state.setSource(List.of(new SmartWorkbookReader.SourceSheet("0:0","教师名单.xlsx","名单",List.of(
                new SmartWorkbookReader.SourceRow(1,List.of("学生导入名单")),new SmartWorkbookReader.SourceRow(2,List.of("学号","姓名","班级")),
                new SmartWorkbookReader.SourceRow(3,List.of("0012345","测试甲","23级生工7班")),new SmartWorkbookReader.SourceRow(4,List.of("","测试乙","23级生工7班"))))));
        when(store.owned(any(),eq(contextId),eq(sessionId))).thenReturn(state);when(store.lock(eq(contextId),anyString())).thenReturn(lease);
    }
    SmartImportAgentTools.Prepared turn(String message){return tools.prepare("auth",contextId,sessionId,UUID.randomUUID().toString(),message);}
    Object execute(SmartImportAgentTools.Prepared p,String name,String args)throws Exception{return p.execute(name,json.readTree(args));}
    void preview()throws Exception{try(var p=turn("帮我导入学生")){execute(p,"check_import_plan","{\"type\":\"student\"}");p.finish(p.answer("测试乙缺学号，请补充。"));}}
    String action(){return state.getPreview().get("actionId").toString();}
    String confirmation(String id,boolean partial,String quote){return "{\"actionId\":\""+id+"\",\"allowPartial\":"+partial+",\"quote\":\""+quote+"\"}";}
    @Test void groundedReplyAndFreshConfirmationCreateOnceWithoutLeakingCredentials()throws Exception{
        preview();verifyNoInteractions(executor);
        try(var p=turn("测试乙的学号是0098765")){execute(p,"update_import_plan","{\"rowUpdates\":[{\"rowId\":\"0:0:4\",\"values\":{\"学号\":\"0098765\"}}]}");p.finish(p.answer("两人通过，请确认。"));}
        assertEquals("WAITING_CONFIRM",state.getPhase());assertEquals("",state.getSource().getFirst().rows().getLast().cells().getFirst());
        try(var p=turn("确认导入")){execute(p,"confirm_import_plan",confirmation(action(),false,"确认导入"));p.finish(p.answer("done"));assertFalse(json.writeValueAsString(p.receipt()).contains("secret"));}
        assertEquals("COMPLETED",state.getPhase());verify(executor,times(1)).execute(any(),any(),anyList(),any());
        try(var p=turn("再确认一次")){assertFalse(p.specs().stream().anyMatch(s->s.name().equals("confirm_import_plan")));execute(p,"read_import_receipt","{}");p.finish(p.answer("已完成。"));}
        verify(executor,times(1)).execute(any(),any(),anyList(),any());
    }
    @Test void guessedStudentNumberIsRejected()throws Exception{
        preview();try(var p=turn("你看着处理")){assertThrows(AgentException.class,()->execute(p,"update_import_plan","{\"rowUpdates\":[{\"rowId\":\"0:0:4\",\"values\":{\"学号\":\"AI-GUESSED\"}}]}"));}
        assertNull(state.getOptions().rowOverrides());verifyNoInteractions(executor);
    }
    @Test void firstTurnCannotCommitEvenWithModelSuppliedConfirmation()throws Exception{
        try(var p=turn("确认导入")){execute(p,"check_import_plan","{\"type\":\"student\"}");assertThrows(AgentException.class,()->execute(p,"confirm_import_plan",confirmation(action(),true,"确认导入")));}
        verifyNoInteractions(executor);
    }
    @Test void questionsAndNegationDoNotAuthorizeWrites(){
        for(String text:List.of("确认导入需要注意什么？","不要导入，先确认班级","怎么确认导入","是否可以导入"))assertThrows(AgentException.class,()->SmartImportAgentTools.requireConfirmation(text,text,false,false));
        assertDoesNotThrow(()->SmartImportAgentTools.requireConfirmation("好的","好的",false,false));
        assertDoesNotThrow(()->SmartImportAgentTools.requireConfirmation("确认导入，已有学生不要覆盖","确认导入",false,false));
        assertThrows(AgentException.class,()->SmartImportAgentTools.requireConfirmation("不要覆盖，也不要导入","不要导入",false,false));
    }
    @Test void partialImportRequiresExplicitScope()throws Exception{
        preview();try(var p=turn("确认导入")){assertThrows(AgentException.class,()->execute(p,"confirm_import_plan",confirmation(action(),true,"确认导入")));p.finish(p.answer("请明确范围。"));}verifyNoInteractions(executor);
        try(var p=turn("只导入正常的，问题行先保留")){execute(p,"confirm_import_plan",confirmation(action(),true,"只导入正常的"));p.finish(p.answer("done"));}
        assertEquals(1,state.getReceipt().get("successCount"));assertEquals(1L,state.getReceipt().get("blockedCount"));
    }
    @Test void editingDuringConfirmationRequiresAnotherHumanTurn()throws Exception{
        preview();String old=action();try(var p=turn("测试乙学号是0098765，确认导入")){execute(p,"update_import_plan","{\"rowUpdates\":[{\"rowId\":\"0:0:4\",\"values\":{\"学号\":\"0098765\"}}]}");assertThrows(AgentException.class,()->execute(p,"confirm_import_plan",confirmation(old,true,"确认导入")));p.finish(p.answer("请核对新预览。"));}verifyNoInteractions(executor);
    }
    @Test void liveCapabilityRevocationBlocksNextTool()throws Exception{
        preview();try(var p=turn("确认导入")){when(caps.has(any(),eq("agent_chat"))).thenReturn(false);assertThrows(AgentException.class,()->execute(p,"confirm_import_plan",confirmation(action(),true,"确认导入")));}verifyNoInteractions(executor);
    }
    @Test void interruptedTurnReleasesLeaseWithoutMarkingUnshownPreviewReviewed()throws Exception{
        try(var p=turn("帮我导入学生")){execute(p,"check_import_plan","{\"type\":\"student\"}");}assertNull(state.getReviewedActionId());assertNull(state.getActiveTurnId());verify(lease).close();
    }
    @Test void unrelatedQueriesKeepAttachmentAndReviewedPlan()throws Exception{
        preview();String previous=action();try(var p=turn("公告怎么发布")){p.finish(p.answer("从公告页面发布。"));}assertEquals(previous,action());assertEquals(previous,state.getReviewedActionId());verifyNoInteractions(executor);
    }
    @Test void duplicateNamesNeedCoordinates()throws Exception{
        var source=state.getSource().getFirst();var rows=new ArrayList<>(source.rows());rows.set(2,new SmartWorkbookReader.SourceRow(3,List.of("","测试甲","23级生工7班")));rows.set(3,new SmartWorkbookReader.SourceRow(4,List.of("","测试甲","23级生工7班")));state.setSource(List.of(new SmartWorkbookReader.SourceSheet(source.id(),source.fileName(),source.name(),rows)));preview();
        try(var p=turn("测试甲的学号是NEW001")){assertThrows(AgentException.class,()->execute(p,"update_import_plan","{\"rowUpdates\":[{\"rowId\":\"0:0:4\",\"values\":{\"学号\":\"NEW001\"}}]}"));}assertNull(state.getOptions().rowOverrides());verifyNoInteractions(executor);
    }
}
