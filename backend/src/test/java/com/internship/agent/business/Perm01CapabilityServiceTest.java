package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.SystemConfig;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.internship.agent.business.AgentTypes.Actor;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class Perm01CapabilityServiceTest {
    @Test void dedicatedEmptyOverrideReplacesLegacyAndRestoresRoleDefault() {
        SystemConfigService configs = mock(SystemConfigService.class);
        SystemConfig main = new SystemConfig();
        main.setConfigValue("{\"roleDefaults\":{\"student\":[\"agent_chat\",\"form_fill\"]},"
                + "\"userOverrides\":{\"ROLE_STUDENT:8\":{\"form_fill\":false}}}");
        SystemConfig dedicated = new SystemConfig();
        dedicated.setConfigValue("{}");
        when(configs.findByConfigKey(AgentCapabilityService.CONFIG_KEY)).thenReturn(main);
        when(configs.findByConfigKey(AgentCapabilityService.userOverrideConfigKey("ROLE_STUDENT:8")))
                .thenReturn(dedicated);
        AgentCapabilityService service = new AgentCapabilityService(configs, new ObjectMapper());
        Actor student = new Actor("ROLE_STUDENT:8:false:", 8L, "ROLE_STUDENT", "student",
                null, null, null, false, Set.of());

        assertTrue(service.has(student, "form_fill"), "独立空覆盖必须替代旧内嵌覆盖并恢复角色默认");
    }
}
