package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.controller.AgentCapabilityAdminController;
import com.internship.entity.StudentUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.*;
import com.internship.service.AgentCapabilityParseService;
import com.internship.service.SystemConfigService;
import com.internship.service.TeacherAutoTaskService;
import com.internship.service.automation.AutomationService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.context.SecurityContextHolder;
import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Isolated business/AOP tests; no provider calls, database connections, or capability mutations. */
class AgentCapabilityPreviewServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AgentCapabilityParseService parser = mock(AgentCapabilityParseService.class);
    private final AgentCapabilityService capabilities = mock(AgentCapabilityService.class);
    private final AgentFeatureGate gate = mock(AgentFeatureGate.class);
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final TeacherAutoTaskMapper autoTasks = mock(TeacherAutoTaskMapper.class);
    private final StudentUserMapper students = mock(StudentUserMapper.class);
    private final TeacherUserMapper teachers = mock(TeacherUserMapper.class);
    private final CompanyUserMapper companies = mock(CompanyUserMapper.class);
    private final AdminUserMapper admins = mock(AdminUserMapper.class);
    private final AgentActionService actions = mock(AgentActionService.class);
    private final AgentCapabilityUserMapper lookup = mock(AgentCapabilityUserMapper.class);
    private final Actor admin = new Actor("ROLE_ADMIN:1:false:",1L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
    private final Map<String,List<String>> defaults = Map.of("student",List.of("agent_chat","voice_input"),
            "teacher",List.of("agent_chat"),"company",List.of("agent_chat"),"admin",List.of("agent_chat"));
    private final Map<String,Object> policy = Map.of("version",1,"globalCooldownMinutes",1440,
            "roleDefaults",Map.of("teacher",Map.of("actions",List.of(),"channels",List.of(),"scopes",List.of(),"maxTargetsPerRun",200)),
            "userOverrides",Map.of());
    private AgentCapabilityPreviewService service;

    @BeforeEach void setup() throws Exception {
        SecurityContextHolder.clearContext();
        when(capabilities.defaultRoleDefaults()).thenReturn(defaults);
        when(capabilities.has(any(),eq("agent_chat"))).thenReturn(true);
        when(configs.findAll()).thenReturn(List.of());
        when(autoTasks.findActiveIncludingExpired()).thenReturn(List.of());
        when(parser.parse(anyString(),anyMap(),anyMap())).thenAnswer(call -> draft(List.of()));
        when(actions.preview(any(),anyString(),anyString(),anyMap(),anyString())).thenAnswer(call ->
                new AgentActionService.ActionView("preview-1",call.getArgument(1),call.getArgument(2),call.getArgument(3),
                        call.getArgument(4),"READY",0,Long.MAX_VALUE,null,null,null));
        service = new AgentCapabilityPreviewService(parser,capabilities,gate,configs,autoTasks,
                students,teachers,companies,admins,json,actions,lookup);
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void workerWithoutSecurityContextUsesActorWhileExternalControllerRemainsSecured() throws Exception {
        AgentCapabilityAdminController controller = new AgentCapabilityAdminController(parser,capabilities,gate,
                mock(TeacherAutoTaskService.class),configs,autoTasks,students,teachers,companies,admins,json);
        controller.configurePreviewService(service);
        ProxyFactory proxy = new ProxyFactory(controller);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        AgentCapabilityAdminController secured = (AgentCapabilityAdminController)proxy.getProxy();
        assertThrows(AuthenticationCredentialsNotFoundException.class,
                () -> secured.parse("Bearer ignored",Map.of("message","关闭学生语音输入")));
        verifyNoInteractions(parser);

        AgentUiTools tools = new AgentUiTools(json,capabilities,mock(FillIntentService.class),mock(AutomationService.class),service);
        Task task = new Task(); task.setMessage("关闭学生语音输入");
        task.setPageContext(new PageContext("admin","/admin/permissions","权限管理",null,null,List.of(),Map.of(),1));
        Map<String,Object> result = tools.execute("Bearer ignored",admin,task,"admin_capabilities_preview",
                json.readTree("{\"message\":\"关闭学生语音输入\"}"));
        assertEquals("CAPABILITIES_PREVIEW",result.get("type"));
        assertEquals("preview-1",((Map<?,?>)result.get("preview")).get("actionId"));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(actions).preview(eq(admin),eq("CAPABILITY_SAVE"),eq("AGENT_CAPABILITIES_CONFIG"),anyMap(),anyString());
        assertNoConfigurationWrite();
    }

    @Test void wrongRoleReadOnlyAndMissingActorAreRejectedBeforePrivateReadsOrModel() {
        Actor student = new Actor("ROLE_STUDENT:8:false:",8L,"ROLE_STUDENT","学生",null,null,null,false,Set.of());
        Actor readOnly = new Actor("ROLE_ADMIN:1:true:",1L,"ROLE_ADMIN","管理员",null,null,null,true,Set.of());
        for(Actor actor:new Actor[]{student,readOnly,null}) {
            assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.parse(actor,"权限变更")).getCode());
            assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.preview(actor,Map.of())).getCode());
        }
        verifyNoInteractions(parser,gate,configs,actions,lookup);
    }

    @Test void ambiguousIdentityAsksQuestionWithoutCreatingConfirmationAction() throws Exception {
        when(parser.parse(anyString(),anyMap(),anyMap())).thenAnswer(call -> draft(List.of(
                Map.of("role","student","query","同名学生","overrides",Map.of("voice_input",false)))));
        when(lookup.findExact("student","同名学生")).thenReturn(List.of(Map.of("id",8L,"account","S008"),Map.of("id",9L,"account","S009")));
        Map<String,Object> result = service.parse(admin,"关闭同名学生语音输入");
        assertFalse(result.containsKey("actionId"));
        assertTrue(String.valueOf(result.get("questions")).contains("S008"));
        assertEquals(List.of(),result.get("userChanges"));
        verifyNoInteractions(actions);
        assertNoConfigurationWrite();
    }

    @Test void exactIdentityAndForbiddenAutomationKeepOriginalPreviewBoundaries() throws Exception {
        StudentUser student = new StudentUser(); student.setId(8L); student.setUsername("S008");
        when(students.findById(8L)).thenReturn(student);
        when(lookup.findExact("student","S008")).thenReturn(List.of(Map.of("id",8L,"account","S008")));
        when(parser.parse(anyString(),anyMap(),anyMap())).thenAnswer(call -> draft(List.of(
                Map.of("role","student","query","S008","overrides",Map.of("voice_input",false)))));
        Map<String,Object> result = service.parse(admin,"关闭学生S008语音输入");
        assertEquals(defaults,result.get("proposedRoleDefaults"));
        Map<?,?> change = (Map<?,?>)((List<?>)result.get("userChanges")).getFirst();
        assertEquals(8L,change.get("userId"));
        assertEquals("ROLE_STUDENT:8",change.get("roleKey"));
        assertEquals(json.valueToTree(Map.of("voice_input",false)),json.valueToTree(change.get("overrides")));
        reset(actions,parser);
        assertThrows(BusinessException.class,()->service.parse(admin,"给学生开启自动化任务"));
        verifyNoInteractions(actions,parser);
        assertNoConfigurationWrite();
    }

    private Map<String,Object> draft(List<Map<String,Object>> requests) {
        Map<String,Object> draft = new LinkedHashMap<>();
        draft.put("proposedRoleDefaults",defaults); draft.put("proposedAutoTaskPolicy",policy);
        draft.put("questions",List.of()); draft.put("userRequests",requests); return draft;
    }
    private void assertNoConfigurationWrite() {
        verify(configs,never()).insert(any()); verify(configs,never()).update(any());
        verify(configs,never()).deleteById(anyLong());
        verify(actions,never()).execute(any(),anyString(),anyString(),anyMap(),any());
    }
}
