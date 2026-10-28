package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.entity.SystemConfig;
import com.internship.service.ReflectionFeatureService;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReflectionKnowledgeRetirementTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void oldStudentMenuConfigurationCannotAdvertiseRetiredBusiness() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var configs = mock(SystemConfigService.class);
        var menus = new SystemConfig();
        menus.setConfigValue("[{\"path\":\"/student/internships\",\"visible\":true},{\"path\":\"/student/internship-appraisal\",\"visible\":true}]");
        when(configs.findByConfigKey("STUDENT_MENU_CONFIG")).thenReturn(menus);
        ReflectionTestUtils.setField(knowledge, "configService", configs);
        var actor = new Actor("s1", 1L, "ROLE_STUDENT", "测试", null, null, null, false, Set.of());
        var result = knowledge.lookup(actor, "");
        var visible = (java.util.List<?>) result.facts().get("visibleStudentMenus");
        assertFalse(visible.contains("/student/internships"));
        assertTrue(visible.contains("/student/internship-appraisal"));
    }

    @Test void allRolesSeeRetiredStatusInsteadOfOldSubmissionInstructions() throws Exception {
        var knowledge = new AgentKnowledge(json);
        for (String role : new String[]{"ROLE_STUDENT", "ROLE_COMPANY", "ROLE_ADMIN", "ROLE_TEACHER_COUNSELOR"}) {
            var actor = new Actor(role, 1L, role, "测试", role.contains("TEACHER") ? "COUNSELOR" : null,
                    null, null, false, Set.of("view:reflection", "menu:evaluation"));
            var result = knowledge.lookup(actor, "实习心得怎么提交");
            assertEquals(1, result.total());
            assertEquals(false, result.facts().get("reflectionEnabled"));
            assertTrue(result.items().getFirst().get("rules").toString().contains("已停用"));
            assertFalse(result.items().getFirst().containsKey("steps"));
        }
    }

    @Test void currentOverviewExcludesLegacyBusinessEvenForAdministrator() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var admin = new Actor("a1", 1L, "ROLE_ADMIN", "测试", null, null, null, false, Set.of());
        var result = knowledge.lookup(admin, "");
        assertTrue(result.items().stream().noneMatch(card -> "reflection".equals(card.get("businessFeature"))));
        var overview = result.items().stream().filter(card -> "admin-knowledge-map".equals(card.get("id"))).findFirst().orElseThrow();
        assertTrue(overview.get("rules").toString().contains("鉴定"));
        assertFalse(overview.get("rules").toString().contains("心得"));
        assertTrue(result.items().stream().noneMatch(card -> card.containsKey("whenReflectionDisabled")));
        assertFalse(json.writeValueAsString(result.items()).contains("实习期间学生提交心得"));
        assertFalse(json.writeValueAsString(result.items()).contains("是否要交心得"));
    }

    @Test void explicitlyReenabledBusinessRestoresItsRetainedKnowledgeAndChangesVersion() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var configs = mock(SystemConfigService.class);
        ReflectionTestUtils.setField(knowledge, "configService", configs);
        String disabledVersion = knowledge.getVersion();
        var flag = new SystemConfig(); flag.setStatus(1); flag.setConfigValue("true");
        when(configs.findByConfigKey(ReflectionFeatureService.CONFIG_KEY)).thenReturn(flag);
        assertNotEquals(disabledVersion, knowledge.getVersion());
        var admin = new Actor("a1", 1L, "ROLE_ADMIN", "测试", null, null, null, false, Set.of());
        assertTrue(knowledge.lookup(admin, "实习心得").items().stream()
                .anyMatch(card -> "reflection".equals(card.get("businessFeature"))));
    }
}
