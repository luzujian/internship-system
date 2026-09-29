package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.SystemConfig;
import com.internship.service.AgentCapabilityParseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AgentCapabilityParseService 离线单测：聚焦应用侧白名单校验与差异计算（不调用模型）。
 * 覆盖：非法角色/能力被过滤、未提及角色保持原值、changes 差异正确、questions 透传。
 */
class AgentCapabilityParseServiceTest {

    private AgentCapabilityParseService service;

    @BeforeEach void setup() {
        service = new AgentCapabilityParseService(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, List<String>> invokeMerge(Map<String, List<String>> current, Object proposed) throws Exception {
        Method m = AgentCapabilityParseService.class.getDeclaredMethod("merge", Map.class, Object.class);
        m.setAccessible(true);
        return (Map<String, List<String>>) m.invoke(service, current, proposed);
    }

    @SuppressWarnings("unchecked")
    private List<String> invokeReadQuestions(Object raw) throws Exception {
        Method m = AgentCapabilityParseService.class.getDeclaredMethod("readQuestions", Object.class);
        m.setAccessible(true);
        return (List<String>) m.invoke(service, raw);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeMergePolicy(Map<String, Object> current, Object proposed) throws Exception {
        Method m = AgentCapabilityParseService.class.getDeclaredMethod("mergePolicy", Map.class, Object.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(service, current, proposed);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> invokePolicyChanges(Map<String, Object> current,
                                                          Map<String, Object> proposed) throws Exception {
        Method m = AgentCapabilityParseService.class.getDeclaredMethod("policyChanges", Map.class, Map.class);
        m.setAccessible(true);
        return (List<Map<String, Object>>) m.invoke(service, current, proposed);
    }

    private Map<String, Object> basePolicy() {
        Map<String, Object> teacher = new LinkedHashMap<>();
        teacher.put("actions", List.of("SEND_REMINDER"));
        teacher.put("channels", List.of("STATION_REMINDER", "EMAIL"));
        teacher.put("scopes", List.of("OWN_CLASSES"));
        teacher.put("maxTargetsPerRun", 200);
        Map<String, Object> roleDefaults = new LinkedHashMap<>();
        roleDefaults.put("teacher", teacher);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 1);
        result.put("roleDefaults", roleDefaults);
        result.put("userOverrides", Map.of());
        return result;
    }

    private Map<String, List<String>> baseMatrix() {
        Map<String, List<String>> current = new LinkedHashMap<>();
        current.put("student", List.of("agent_chat", "voice_input", "form_fill", "internship_plan"));
        current.put("teacher", List.of("agent_chat", "voice_input", "form_fill"));
        current.put("company", List.of("agent_chat", "voice_input", "form_fill"));
        current.put("admin", List.of("agent_chat", "voice_input", "form_fill"));
        return current;
    }

    @Test void unknownCapabilityAndRoleAreFilteredOut() throws Exception {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("teacher", List.of("agent_chat", "auto_task", "sms_notify", "hack_system"));
        proposed.put("hacker", List.of("agent_chat"));
        Map<String, List<String>> merged = invokeMerge(baseMatrix(), proposed);

        assertEquals(List.of("agent_chat", "auto_task"), merged.get("teacher"),
                "只保留白名单内能力，非法能力与非法角色被过滤");
        assertEquals(baseMatrix().get("student"), merged.get("student"), "未提及角色保持原值");
        assertEquals(4, merged.size(), "只输出四个合法角色");
    }

    @Test void unmentionedRolesKeepCurrentValues() throws Exception {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("student", List.of("agent_chat"));
        Map<String, List<String>> merged = invokeMerge(baseMatrix(), proposed);

        assertEquals(List.of("agent_chat"), merged.get("student"));
        assertEquals(baseMatrix().get("teacher"), merged.get("teacher"));
        assertEquals(baseMatrix().get("company"), merged.get("company"));
        assertEquals(baseMatrix().get("admin"), merged.get("admin"));
    }

    @Test void emptyOrInvalidProposalFallsBackToCurrent() throws Exception {
        assertEquals(baseMatrix(), invokeMerge(baseMatrix(), null));
        assertEquals(baseMatrix(), invokeMerge(baseMatrix(), "not-a-map"));
        assertEquals(baseMatrix(), invokeMerge(baseMatrix(), List.of("agent_chat")));
    }

    @Test void duplicateCapabilitiesAreDeduplicated() throws Exception {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("teacher", List.of("agent_chat", "agent_chat", "auto_task"));
        Map<String, List<String>> merged = invokeMerge(baseMatrix(), proposed);
        assertEquals(List.of("agent_chat", "auto_task"), merged.get("teacher"));
    }

    @Test void previewPolicyCarriesVersionSoSaveSideAcceptsIt() throws Exception {
        Map<String, Object> current = basePolicy();
        current.put("version", 1);
        Map<String, Object> proposed = new LinkedHashMap<>();
        Map<String, Object> teacher = new LinkedHashMap<>();
        teacher.put("actions", List.of("SEND_REMINDER"));
        teacher.put("channels", List.of("EMAIL"));
        teacher.put("scopes", List.of("OWN_CLASSES"));
        teacher.put("maxTargetsPerRun", 200);
        Map<String, Object> roles = new LinkedHashMap<>();
        roles.put("teacher", teacher);
        proposed.put("roleDefaults", roles);
        Map<String, Object> merged = invokeMergePolicy(current, proposed);
        assertEquals(1, merged.get("version"),
                "预览产物必须带 version，否则保存端 version==1 校验会拒绝（回归：AI解析→保存 400）");
    }
    @Test void questionsAreTrimmedAndBlankRemoved() throws Exception {
        List<String> questions = invokeReadQuestions(List.of("  该角色是学生还是教师？  ", "", "   ", 123));
        assertEquals(List.of("该角色是学生还是教师？", "123"), questions);
        assertTrue(invokeReadQuestions(null).isEmpty());
        assertTrue(invokeReadQuestions("not-a-list").isEmpty());
    }

    @Test void configurableCapabilitiesExcludeFixedStudentApplicationCapability() {
        var configurable = new java.util.HashSet<>(AgentCapabilityService.KNOWN);
        configurable.remove("job_apply");
        assertEquals(configurable, AgentCapabilityParseService.CAPABILITIES,
                "解析服务只允许修改可配置能力，学生投递为固定能力");
    }

    @Test void automationPolicyProposalKeepsCurrentWhenInvalid() throws Exception {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("roleDefaults", Map.of("teacher", Map.of(
                "actions", List.of("SEND_REMINDER", "DELETE_STUDENTS"),
                "channels", List.of("EMAIL"),
                "scopes", List.of("OWN_CLASSES"),
                "maxTargetsPerRun", 100
        )));
        assertEquals(basePolicy(), invokeMergePolicy(basePolicy(), proposed));
    }

    @Test void automationPolicyProposalAcceptsWhitelistedValues() throws Exception {
        Map<String, Object> proposed = new LinkedHashMap<>();
        proposed.put("roleDefaults", Map.of("teacher", Map.of(
                "actions", List.of("SEND_REMINDER"),
                "channels", List.of("EMAIL"),
                "scopes", List.of("OWN_CLASSES"),
                "maxTargetsPerRun", 100
        )));
        Map<String, Object> merged = invokeMergePolicy(basePolicy(), proposed);
        Map<String, Object> teacher = (Map<String, Object>) ((Map<String, Object>) merged.get("roleDefaults")).get("teacher");
        assertEquals(List.of("EMAIL"), teacher.get("channels"));
        assertEquals(100, teacher.get("maxTargetsPerRun"));
        assertEquals(2, invokePolicyChanges(basePolicy(), merged).size());
    }
    @Test void userRequestsKeepExactAccountAndWhitelistBooleanChanges() throws Exception {
        Method m=AgentCapabilityParseService.class.getDeclaredMethod("readUserRequests",Object.class);m.setAccessible(true);
        var result=(List<Map<String,Object>>)m.invoke(service,List.of(Map.of("role","teacher","query","T001","userId",999,"overrides",Map.of("voice_input",false),"automationPolicy",Map.of("channels",List.of("STATION_REMINDER")))));
        assertEquals("T001",result.getFirst().get("query"));assertFalse(result.getFirst().containsKey("userId"));
        assertEquals(Map.of("voice_input",false),result.getFirst().get("overrides"));
        assertThrows(java.lang.reflect.InvocationTargetException.class,()->m.invoke(service,List.of(Map.of("role","teacher","query","T001","overrides",Map.of("voice_input","false")))));
    }
    @Test void globalCooldownHasAnExplicitPreviewDiff() throws Exception {
        var current=basePolicy();var proposed=new LinkedHashMap<>(current);proposed.put("globalCooldownMinutes",2880);
        assertEquals(List.of(Map.of("role","teacher","field","globalCooldownMinutes","from",1440,"to",2880)),invokePolicyChanges(current,proposed));
    }
}
