package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentCapabilityService.AutomationPolicy;
import com.internship.entity.SystemConfig;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentCapabilityServiceTest {
    private SystemConfigService config;
    private AgentCapabilityService service;
    private final Actor student = new Actor("ROLE_STUDENT:8:false:", 8L, "ROLE_STUDENT", "s001", null, null, null, false, Set.of());
    private final Actor teacher = new Actor("ROLE_TEACHER_DEPARTMENT:48:false:", 48L, "ROLE_TEACHER_DEPARTMENT", "t002", "DEPARTMENT", null, 1L, false, Set.of());
    private final Actor readOnlyAdmin = new Actor("ROLE_ADMIN:1:true:admin", 1L, "ROLE_ADMIN", "admin", null, null, null, true, Set.of());

    @BeforeEach void setup() {
        config = mock(SystemConfigService.class);
        service = new AgentCapabilityService(config, new ObjectMapper());
    }

    private void configure(String json) {
        SystemConfig row = new SystemConfig();
        row.setConfigValue(json);
        when(config.findByConfigKey(AgentCapabilityService.CONFIG_KEY)).thenReturn(row);
    }

    @Test void defaultsApplyWithoutConfiguration() {
        Map<String, Boolean> studentCaps = service.capabilitiesFor(student);
        assertTrue(studentCaps.get("form_fill"));
        assertTrue(studentCaps.get("voice_input"));
        assertTrue(studentCaps.get("internship_plan"));
        assertTrue(studentCaps.get("job_apply"));
        assertFalse(studentCaps.get("auto_task"));
        Map<String, Boolean> teacherCaps = service.capabilitiesFor(teacher);
        assertTrue(teacherCaps.get("form_fill"));
        assertTrue(teacherCaps.get("agent_chat"));
        assertTrue(teacherCaps.get("teacher_internship_summary"));
        Actor admin = new Actor("ROLE_ADMIN:1:false:", 1L, "ROLE_ADMIN", "admin", null, null, null, false, Set.of());
        assertTrue(service.capabilitiesFor(admin).get("form_fill"));
    }

    @Test void applicationCapabilityIsFixedForStudentsDespiteLegacyOverrides() {
        configure("{\"roleDefaults\":{\"student\":[],\"teacher\":[\"job_apply\"]},"
                + "\"userOverrides\":{\"ROLE_STUDENT:8\":{\"job_apply\":false}}}");
        assertTrue(service.has(student, "job_apply"));
        assertFalse(service.has(teacher, "job_apply"));
        Actor readOnlyStudent = new Actor("ROLE_STUDENT:8:true:", 8L, "ROLE_STUDENT", "s008", null, null, null, true, Set.of());
        assertFalse(service.has(readOnlyStudent, "job_apply"));
    }

    @Test void roleDefaultsFromConfigOverrideCodeDefaults() {
        configure("{\"roleDefaults\":{\"student\":[\"agent_chat\"],\"teacher\":[\"agent_chat\",\"voice_input\",\"form_fill\"]}}");
        assertFalse(service.capabilitiesFor(student).get("form_fill"));
        assertTrue(service.capabilitiesFor(teacher).get("form_fill"));
    }

    @Test void userOverridesAddAndRemoveCapabilities() {
        configure("{\"roleDefaults\":{\"student\":[\"agent_chat\",\"voice_input\",\"form_fill\"]},"
                + "\"userOverrides\":{\"ROLE_STUDENT:8\":{\"form_fill\":false,\"internship_plan\":true}}}");
        Map<String, Boolean> caps = service.capabilitiesFor(student);
        assertFalse(caps.get("form_fill"));
        assertTrue(caps.get("internship_plan"));
        assertTrue(caps.get("voice_input"));
    }

    @Test void teacherOverridesKeyIsStableAcrossSubRoles() {
        Actor counselor = new Actor("ROLE_TEACHER_COUNSELOR:48:false:", 48L, "ROLE_TEACHER_COUNSELOR", "t002", "COUNSELOR", null, null, false, Set.of());
        configure("{\"roleDefaults\":{\"teacher\":[\"agent_chat\"]},\"userOverrides\":{\"ROLE_TEACHER_DEPARTMENT:48\":{\"auto_task\":true}}}");
        assertTrue(service.capabilitiesFor(teacher).get("auto_task"));
        // 覆盖键按用户ID生效，与具体教师子角色无关
        assertTrue(service.capabilitiesFor(counselor).get("auto_task") == service.has(counselor, "auto_task"));
    }

    @Test void readOnlyActorKeepsChatOnly() {
        configure("{\"roleDefaults\":{\"admin\":[\"agent_chat\",\"voice_input\",\"form_fill\"]}}");
        Map<String, Boolean> caps = service.capabilitiesFor(readOnlyAdmin);
        assertTrue(caps.get("agent_chat"));
        assertFalse(caps.get("form_fill"));
        assertFalse(caps.get("voice_input"));
    }

    @Test void unknownCapabilityNamesAreRejected() {
        configure("{\"roleDefaults\":{\"student\":[\"agent_chat\",\"made_up\"]}}");
        Map<String, Boolean> caps = service.capabilitiesFor(student);
        assertEquals(AgentCapabilityService.KNOWN.size(), caps.size());
        assertFalse(caps.containsKey("made_up"));
        assertFalse(service.has(student, "made_up"));
        assertFalse(service.has(student, null));
    }

    @Test void brokenConfigFallsBackToCodeDefaults() {
        configure("{invalid json");
        assertTrue(service.capabilitiesFor(student).get("form_fill"));
        when(config.findByConfigKey(anyString())).thenThrow(new RuntimeException("db down"));
        assertTrue(service.capabilitiesFor(student).get("agent_chat"));
    }

    @Test void automationPolicyGrantsExplicitActionsChannelsScopesAndLimit() {
        configure("{\"roleDefaults\":{\"teacher\":[\"agent_chat\",\"auto_task\",\"auto_task_notify\"]},"
                + "\"autoTaskPolicy\":{\"version\":1,\"roleDefaults\":{\"teacher\":{"
                + "\"actions\":[\"SEND_REMINDER\"],\"channels\":[\"STATION_REMINDER\",\"EMAIL\"],"
                + "\"scopes\":[\"OWN_DIVISION\"],\"maxTargetsPerRun\":200}}}}");
        AutomationPolicy policy = service.effectiveAutomationPolicy(teacher);
        assertTrue(policy.canManage());
        assertTrue(policy.canNotify());
        assertTrue(policy.canExecute());
        assertTrue(policy.allowsChannel("EMAIL"));
        assertEquals(200, policy.maxTargetsPerRun());
    }

    @Test void automationUserOverridesReplaceListFields() {
        configure("{\"roleDefaults\":{\"teacher\":[\"agent_chat\",\"auto_task\",\"auto_task_notify\"]},"
                + "\"autoTaskPolicy\":{\"version\":1,\"roleDefaults\":{\"teacher\":{"
                + "\"actions\":[\"SEND_REMINDER\"],\"channels\":[\"STATION_REMINDER\",\"EMAIL\"],"
                + "\"scopes\":[\"OWN_DIVISION\"],\"maxTargetsPerRun\":200}},"
                + "\"userOverrides\":{\"ROLE_TEACHER_DEPARTMENT:48\":{\"channels\":[\"EMAIL\"],"
                + "\"maxTargetsPerRun\":100}}}}");
        AutomationPolicy policy = service.effectiveAutomationPolicy(teacher);
        assertTrue(policy.allowsChannel("EMAIL"));
        assertFalse(policy.allowsChannel("STATION_REMINDER"));
        assertEquals(100, policy.maxTargetsPerRun());
        assertEquals(Set.of("OWN_DIVISION"), policy.scopes());
    }

    @Test void invalidAutomationPolicyFailsClosed() {
        configure("{\"roleDefaults\":{\"teacher\":[\"agent_chat\",\"auto_task\",\"auto_task_notify\"]},"
                + "\"autoTaskPolicy\":{\"version\":1,\"roleDefaults\":{\"teacher\":{"
                + "\"actions\":[\"DELETE_STUDENTS\"],\"channels\":[\"EMAIL\"],"
                + "\"scopes\":[\"OWN_DIVISION\"],\"maxTargetsPerRun\":200}}}}");
        AutomationPolicy policy = service.effectiveAutomationPolicy(teacher);
        assertFalse(policy.canExecute());
        assertTrue(policy.channels().isEmpty());
    }

    @Test void missingAutomationPolicyFailsClosed() {
        configure("{\"roleDefaults\":{\"teacher\":[\"agent_chat\",\"auto_task\",\"auto_task_notify\"]}}");
        AutomationPolicy policy = service.effectiveAutomationPolicy(teacher);
        assertTrue(policy.canManage());
        assertFalse(policy.canExecute());
    }
    @Test void userOverrideCanBeReadFromDedicatedConfigRow() {
        SystemConfig main = new SystemConfig();
        main.setConfigValue("{\"roleDefaults\":{\"student\":[\"agent_chat\",\"voice_input\",\"form_fill\"]}}");
        SystemConfig override = new SystemConfig();
        override.setConfigValue("{\"form_fill\":false,\"internship_plan\":true}");
        when(config.findByConfigKey(AgentCapabilityService.CONFIG_KEY)).thenReturn(main);
        when(config.findByConfigKey(AgentCapabilityService.userOverrideConfigKey("ROLE_STUDENT:8")))
                .thenReturn(override);

        Map<String, Boolean> caps = service.capabilitiesFor(student);
        assertFalse(caps.get("form_fill"));
        assertTrue(caps.get("internship_plan"));
        assertTrue(caps.get("voice_input"));
    }
    @Test void previewUsesProposedCapabilitiesRatherThanCurrentlyGrantedCapabilities() throws Exception {
        configure("{\"roleDefaults\":{\"teacher\":[\"auto_task\",\"auto_task_notify\"]}}");
        var candidate=new ObjectMapper().readTree("{\"roleDefaults\":{\"teacher\":[]},\"autoTaskPolicy\":{\"version\":1,\"roleDefaults\":{\"teacher\":{\"actions\":[\"SEND_REMINDER\"],\"channels\":[\"EMAIL\"],\"scopes\":[\"OWN_DIVISION\"],\"maxTargetsPerRun\":200}}}}");
        assertFalse(service.automationPolicyFor(teacher,candidate).canExecute());
    }
}
