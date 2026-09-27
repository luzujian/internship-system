package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.controller.AgentCapabilityAdminController;
import com.internship.entity.*;
import com.internship.mapper.*;
import com.internship.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class Perm01CapabilityAdminTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SystemConfigService configs = mock(SystemConfigService.class);
    private final Map<String, SystemConfig> rows = new HashMap<>();
    private final Set<Long> deleted = new HashSet<>();
    private final StudentUserMapper students = mock(StudentUserMapper.class);
    private final AgentCapabilityParseService parser = mock(AgentCapabilityParseService.class);
    private final AgentCapabilityUserMapper lookup = mock(AgentCapabilityUserMapper.class);
    private AgentActionService actions;
    private AgentCapabilityAdminController controller;
    private AgentCapabilityService capabilities;
    private final AgentTypes.Actor student = new AgentTypes.Actor("ROLE_STUDENT:8:false:", 8L,
            "ROLE_STUDENT", "student", null, null, null, false, Set.of());

    @BeforeEach void setup() {
        when(configs.findByConfigKey(anyString())).thenAnswer(call -> {
            SystemConfig row = rows.get(call.getArgument(0));
            return row == null || deleted.contains(row.getId()) ? null : row;
        });
        when(configs.findAll()).thenAnswer(call -> rows.values().stream()
                .filter(row -> !deleted.contains(row.getId())).toList());
        when(configs.insert(any())).thenAnswer(call -> {
            SystemConfig row = call.getArgument(0);
            if (rows.containsKey(row.getConfigKey())) throw new DuplicateKeyException("config_key");
            row.setId((long) rows.size() + 1);
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        when(configs.update(any())).thenAnswer(call -> {
            SystemConfig row = call.getArgument(0);
            rows.put(row.getConfigKey(), row);
            return 1;
        });
        when(configs.deleteById(anyLong())).thenAnswer(call -> {
            deleted.add(call.getArgument(0));
            return 1;
        });
        StudentUser user = new StudentUser();
        user.setId(8L);
        when(students.findById(8L)).thenReturn(user);
        capabilities = new AgentCapabilityService(configs, json);
        controller = new AgentCapabilityAdminController(parser, capabilities,
                mock(AgentFeatureGate.class), mock(TeacherAutoTaskService.class), configs,
                mock(TeacherAutoTaskMapper.class), students, mock(TeacherUserMapper.class),
                mock(CompanyUserMapper.class), mock(AdminUserMapper.class), json);
        AgentIdentityService identities = mock(AgentIdentityService.class);
        when(identities.resolve("Bearer admin")).thenReturn(new AgentTypes.Actor("ROLE_ADMIN:1:false:",1L,"ROLE_ADMIN","admin",null,null,null,false,Set.of()));
        SystemConfigMapper locked = mock(SystemConfigMapper.class);
        when(locked.lockByConfigKey(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        actions = mock(AgentActionService.class);
        when(actions.preview(any(),anyString(),anyString(),anyMap(),anyString())).thenAnswer(call ->
                new AgentActionService.ActionView("00000000-0000-0000-0000-000000000001",call.getArgument(1),call.getArgument(2),call.getArgument(3),call.getArgument(4),"READY",0,Long.MAX_VALUE,null,null,null));
        when(actions.execute(any(),anyString(),anyString(),anyMap(),any())).thenAnswer(call ->
                ((java.util.function.Supplier<Map<String,Object>>)call.getArgument(4)).get());
        controller.configureConfirmation(actions,identities,locked,lookup);
        put(AgentCapabilityService.CONFIG_KEY, "{\"userOverrides\":{\"ROLE_STUDENT:8\":{\"form_fill\":false}}}");
    }

    @Test void unsupportedStudentAutomationDoesNotCreateAConfirmableAction() {
        var direct = assertThrows(com.internship.exception.BusinessException.class,
                () -> controller.parse("Bearer admin", Map.of("message", "帮我给学生赋予自动化任务的权限")));
        assertTrue(direct.getMessage().contains("仅支持教师"), direct.getMessage());
        verifyNoInteractions(parser);
        var error = assertThrows(com.internship.exception.BusinessException.class,
                () -> controller.previewConfig("Bearer admin", Map.of("roleDefaults", Map.of("student", List.of("auto_task"), "teacher", List.of(), "company", List.of(), "admin", List.of()), "userChanges", List.of())));
        assertTrue(error.getMessage().contains("仅支持教师"));
        verify(actions, never()).preview(any(), anyString(), anyString(), anyMap(), anyString());
        verify(configs, never()).update(any());
    }

    @Test void dedicatedAndLegacyReadsAgreeWithRuntime() throws Exception {
        assertFalse(overrides().path("form_fill").asBoolean());
        assertFalse(capabilities.has(student, "form_fill"));
        put(AgentCapabilityService.userOverrideConfigKey("ROLE_STUDENT:8"), "{\"voice_input\":false}");
        assertFalse(overrides().has("form_fill"));
        assertTrue(capabilities.has(student, "form_fill"));
        assertFalse(capabilities.has(student, "voice_input"));
        JsonNode root = json.valueToTree(controller.getConfig().getData());
        assertEquals(overrides(), root.path("userOverrides").path("ROLE_STUDENT:8"));
    }

    @Test void clearLegacyRestoresDefaultsAndRoleSaveDoesNotResurrectIt() throws Exception {
        controller.deleteUserOverrides("student", 8L);
        assertTrue(overrides().isEmpty());
        assertTrue(capabilities.has(student, "form_fill"));
        saveRoles();
        assertTrue(overrides().isEmpty());
        assertTrue(capabilities.has(student, "form_fill"));
    }

    @Test void saveClearSaveDoesNotHitSoftDeletedUniqueKey() throws Exception {
        saveOverride(false);
        controller.deleteUserOverrides("student", 8L);
        assertDoesNotThrow(() -> saveOverride(true));
        assertTrue(overrides().path("form_fill").asBoolean());
        assertTrue(capabilities.has(student, "form_fill"));
        verify(configs, never()).deleteById(anyLong());
    }

    @Test void clearIsIdempotentAndRemainsDefaultAfterRoleSave() throws Exception {
        saveOverride(false);
        controller.deleteUserOverrides("student", 8L);
        controller.deleteUserOverrides("student", 8L);
        saveRoles();
        assertTrue(overrides().isEmpty());
        assertTrue(capabilities.has(student, "form_fill"));
    }

    @Test void staleConfirmationDoesNotOverwriteAnotherAdminChange() throws Exception {
        Map<String,Object> preview = (Map<String,Object>)controller.previewConfig("Bearer admin",Map.of()).getData();
        preview.put("confirmed",true);
        put(AgentCapabilityService.userOverrideConfigKey("ROLE_STUDENT:8"), "{\"voice_input\":false}");
        AgentException error = assertThrows(AgentException.class, () -> controller.saveConfig("Bearer admin",preview));
        assertEquals("CONFIG_CHANGED",error.getCode());
        assertFalse(capabilities.has(student,"voice_input"));
    }

    @Test void emptyUserReplacementRestoresRoleDefaults() throws Exception {
        Map<String,Object> preview = (Map<String,Object>)controller.previewConfig("Bearer admin",Map.of("userChanges",List.of(
                Map.of("role","student","userId",8,"overrides",Map.of(),"replace",true)))).getData();
        preview.put("confirmed",true); controller.saveConfig("Bearer admin",preview);
        assertTrue(capabilities.has(student,"form_fill"));
    }

    @Test void ambiguousUserNameProducesQuestionsAndNoExecutablePreview() throws Exception {
        stubUserRequest("同名学生");
        when(lookup.findExact("student","同名学生")).thenReturn(List.of(Map.of("id",8L,"account","S008"),Map.of("id",9L,"account","S009")));
        var response=json.valueToTree(controller.parse("Bearer admin",Map.of("message","关闭同名学生语音")).getData());
        assertFalse(response.has("actionId"));assertTrue(response.path("questions").get(0).asText().contains("S008"));
        verify(actions,never()).preview(any(),anyString(),anyString(),anyMap(),anyString());
    }
    @Test void exactAccountProducesOneUserChangeWithoutExpandingRoleDefaults() throws Exception {
        stubUserRequest("S008");when(lookup.findExact("student","S008")).thenReturn(List.of(Map.of("id",8L,"account","S008")));
        var response=json.valueToTree(controller.parse("Bearer admin",Map.of("message","关闭学生S008语音")).getData());
        assertTrue(response.has("actionId"));assertEquals(1,response.path("userChanges").size());
        assertEquals(8,response.path("userChanges").get(0).path("userId").asInt());
        assertEquals(false,response.path("userChanges").get(0).path("overrides").path("voice_input").asBoolean());
        assertEquals(json.valueToTree(capabilities.defaultRoleDefaults()),response.path("proposedRoleDefaults"));
        verify(configs,never()).update(any());
    }
    private void stubUserRequest(String query) throws Exception {
        Map<String,Object> current=(Map<String,Object>)controller.getConfig().getData();
        Map<String,Object> parsed=new LinkedHashMap<>();parsed.put("proposedRoleDefaults",current.get("roleDefaults"));parsed.put("proposedAutoTaskPolicy",current.get("autoTaskPolicy"));parsed.put("questions",List.of());
        parsed.put("userRequests",List.of(Map.of("role","student","query",query,"overrides",Map.of("voice_input",false))));
        when(parser.parse(anyString(),anyMap(),anyMap())).thenReturn(parsed);
    }

    private void saveOverride(boolean enabled) throws Exception {
        controller.updateUserOverrides(Map.of("role", "student", "userId", 8,
                "overrides", Map.of("form_fill", enabled)));
    }

    private JsonNode overrides() {
        return json.valueToTree(controller.getUserOverrides("student", 8L).getData()).path("overrides");
    }

    private void saveRoles() throws Exception {
        Map<String,Object> preview = (Map<String,Object>)controller.previewConfig("Bearer admin",Map.of("roleDefaults", capabilities.defaultRoleDefaults(),
                "autoTaskPolicy", Map.of("version", 1, "roleDefaults", Map.of("teacher", Map.of(
                        "actions", List.of("SEND_REMINDER"), "channels", List.of("STATION_REMINDER"),
                        "scopes", List.of("OWN_CLASSES"), "maxTargetsPerRun", 200))))).getData();
        preview.put("confirmed",true);
        controller.saveConfig("Bearer admin",preview);
    }

    private void put(String key, String value) {
        SystemConfig row = new SystemConfig();
        row.setId((long) rows.size() + 1);
        row.setConfigKey(key);
        row.setConfigValue(value);
        rows.put(key, row);
    }
}
