package com.internship.agent.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.SystemConfig;
import com.internship.mapper.AgentKnowledgeMapper;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Pattern;

import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentKnowledgeCoverageTest {
    private final ObjectMapper json = new ObjectMapper();
    private final Actor admin = actor("admin", null, Set.of());
    private final Actor student = actor("student", null, Set.of());
    private final Actor company = actor("company", null, Set.of());
    private final Set<String> fullTeacherPermissions = Set.of("menu:home", "menu:dashboard",
            "menu:student-tracking", "view:student_tracking", "menu:approval", "user:company:audit",
            "menu:evaluation", "view:reflection", "menu:appraisal-collection", "menu:company-list",
            "menu:announcements", "menu:resources", "menu:reports", "menu:classes",
            "menu:settings", "menu:teacherAiScoringConfig");

    private Actor actor(String role, String teacherType, Set<String> permissions) {
        String authority = "ROLE_" + role.toUpperCase(Locale.ROOT);
        if (teacherType != null) authority += "_" + teacherType;
        return new Actor(authority + ":1", 1L, authority, "测试", teacherType, 1L, 1L, false, permissions);
    }

    private List<Map<String,Object>> bundled(String resource) throws Exception {
        try (var input = new ClassPathResource("agent/" + resource).getInputStream()) {
            return json.readValue(input, new TypeReference<>() {});
        }
    }

    private Set<String> ids(ToolResult result) {
        Set<String> ids = new HashSet<>();
        result.items().forEach(card -> ids.add(String.valueOf(card.get("id"))));
        return ids;
    }

    private SystemConfigService menuConfig(AgentKnowledge knowledge, String value) {
        var service = mock(SystemConfigService.class);
        var config = new SystemConfig();
        config.setConfigValue(value);
        when(service.findByConfigKey("STUDENT_MENU_CONFIG")).thenReturn(config);
        ReflectionTestUtils.setField(knowledge, "configService", service);
        return service;
    }

    private void enableAllStudentMenus(AgentKnowledge knowledge) {
        menuConfig(knowledge, "[" + String.join(",", List.of("home", "jobs", "applications",
                "interviews", "internship-confirmation-form", "internships", "internship-appraisal",
                "profile").stream().map(path -> "{\"path\":\"/student/" + path + "\"}").toList()) + "]");
    }

    @Test void bundledSystemKnowledgeCoversEveryCurrentRoleRouteAndHasExistingSources() throws Exception {
        Path root = Path.of("").toAbsolutePath();
        if (!Files.isDirectory(root.resolve("frontend"))) root = root.getParent();
        var cards = bundled("system-knowledge.json");
        Set<String> cardIds = new HashSet<>(), routes = new HashSet<>();
        for (var card : cards) {
            assertTrue(cardIds.add(String.valueOf(card.get("id"))), "duplicate id: " + card.get("id"));
            assertEquals("CODE_TRACED", card.get("verifyLevel"));
            assertEquals("2026-10-08", card.get("verifiedAt"));
            assertFalse(String.valueOf(card.get("title")).isBlank());
            assertFalse(((List<?>) card.get("roles")).isEmpty());
            assertFalse(Objects.toString(card.get("steps"), "").isBlank()
                    && Objects.toString(card.get("rules"), "").isBlank());
            for (String source : String.valueOf(card.get("source")).split(";"))
                assertTrue(Files.isRegularFile(root.resolve(source.trim())), source);
            for (Object route : (List<?>) card.getOrDefault("routes", List.of())) routes.add(route.toString());
        }
        var pattern = Pattern.compile("path:\\s*'([^']*)'");
        for (String role : List.of("admin", "student", "teacher", "company")) {
            var matcher = pattern.matcher(Files.readString(root.resolve("frontend/src/router/" + role + "Router.ts")));
            while (matcher.find()) {
                String path = matcher.group(1);
                if (path.isEmpty()) continue;
                String route = "/" + role + "/" + path;
                if (route.equals("/admin/active-statistics")) route = "/admin/data-statistics/active-statistics";
                assertTrue(routes.contains(route), "missing route documentation: " + route);
            }
        }
    }

    @Test void administratorCanRetrieveAllPackagedKnowledgeIncludingOtherRoles() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var expected = new HashSet<String>();
        for (String resource : List.of("knowledge.json", "system-knowledge.json")) {
            for (var card : bundled(resource)) expected.add(String.valueOf(card.get("id")));
        }
        assertEquals(expected, ids(knowledge.lookup(admin, "")));
        for (var card : bundled("system-knowledge.json")) {
            String query = String.valueOf(card.get("title"));
            assertTrue(ids(knowledge.lookup(admin, query)).contains(card.get("id")), query);
        }
    }

    @Test void administratorNaturalQuestionsFindTheRelevantModuleWithinTheResultLimit() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var questions = Map.ofEntries(
                Map.entry("系统日志在哪里查看和导出", "admin-log-center"),
                Map.entry("备份恢复的操作流程是什么", "admin-backup-restore"),
                Map.entry("教师账号怎么导入", "admin-teacher-accounts"),
                Map.entry("学生账号怎么批量导入", "admin-student-accounts"),
                Map.entry("企业资质审核如何办理", "admin-company-admission"),
                Map.entry("岗位类别在哪里维护", "admin-position-categories"),
                Map.entry("怎样设置AI模型网关地址", "admin-ai-model-management"),
                Map.entry("学生自主实习申请需要什么材料", "admin-self-practice-workflow"),
                Map.entry("实习确认表如何选择Offer", "admin-offer-confirmation-workflow"),
                Map.entry("辅导员认领班级怎么操作", "admin-teacher-class-claim"),
                Map.entry("企业实习学生名单在哪里查看", "admin-company-internship-students"));
        List<String> missing = new ArrayList<>();
        questions.forEach((question, id) -> {
            var result = knowledge.lookup(admin, question);
            if (!ids(result).contains(id)) missing.add(question + " -> " + ids(result));
            assertTrue(result.items().size() <= 8);
        });
        assertTrue(missing.isEmpty(), missing.toString());
    }

    @Test void nonAdministratorCannotRetrieveAdministrativeOperations() throws Exception {
        var knowledge = new AgentKnowledge(json);
        enableAllStudentMenus(knowledge);
        var restricted = Set.of("admin-student-accounts", "admin-teacher-accounts", "admin-administrator-accounts",
                "admin-log-center", "admin-backup-restore", "admin-system-data-clear",
                "admin-business-permissions", "admin-ai-model-management", "admin-system-security-settings");
        for (Actor actor : List.of(student, company, actor("teacher", "COUNSELOR", fullTeacherPermissions))) {
            assertTrue(Collections.disjoint(restricted, ids(knowledge.lookup(actor, ""))), actor.role());
            for (String question : List.of("系统日志如何导出", "备份恢复操作流程", "清空系统数据怎么操作"))
                assertTrue(Collections.disjoint(restricted, ids(knowledge.lookup(actor, question))), actor.role() + question);
        }
        assertTrue(ids(knowledge.lookup(admin, "")).containsAll(restricted));
    }

    @Test void teacherKnowledgeRequiresCurrentModulePermissionAndSupportedTeacherType() throws Exception {
        var knowledge = new AgentKnowledge(json);
        Actor noPermissions = actor("teacher", "COUNSELOR", Set.of());
        var moduleIds = Set.of("admin-teacher-tracking", "admin-teacher-evaluation", "admin-teacher-reports",
                "admin-teacher-appraisal-collection", "teacher-announcement-management", "teacher-resource-management",
                "teacher-personal-scoring-rules", "teacher-personal-keywords", "teacher-reminder-automation");
        assertTrue(Collections.disjoint(moduleIds, ids(knowledge.lookup(noPermissions, ""))));
        assertTrue(ids(knowledge.lookup(actor("teacher", "COUNSELOR", fullTeacherPermissions), "")).containsAll(moduleIds));
        Actor counselorSettings = actor("teacher", "COUNSELOR", Set.of("menu:settings"));
        Actor departmentSettings = actor("teacher", "DEPARTMENT", Set.of("menu:settings"));
        assertFalse(ids(knowledge.lookup(counselorSettings, "")).contains("admin-unified-calendar-settings"));
        assertTrue(ids(knowledge.lookup(departmentSettings, "")).contains("admin-unified-calendar-settings"));
        assertFalse(ids(knowledge.lookup(departmentSettings, "")).contains("admin-teacher-scoring-configuration"));
        assertTrue(ids(knowledge.lookup(counselorSettings, "")).contains("admin-teacher-scoring-configuration"));
        assertFalse(ids(knowledge.lookup(counselorSettings, "")).contains("teacher-personal-scoring-rules"));
    }

    @Test void enterpriseAuditExplanationRequiresBothMenuAndAuditPermission() throws Exception {
        var knowledge = new AgentKnowledge(json);
        for (Set<String> permissions : List.of(Set.<String>of(), Set.of("menu:approval"), Set.of("user:company:audit")))
            assertFalse(ids(knowledge.lookup(actor("teacher", "COLLEGE", permissions), "")).contains("teacher-company-audit"));
        assertTrue(ids(knowledge.lookup(actor("teacher", "COLLEGE", Set.of("menu:approval", "user:company:audit")), ""))
                .contains("teacher-company-audit"));
    }

    @Test void studentKnowledgeTracksMenuChangesAndFallsBackToBasicPages() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var jobIds = Set.of("admin-student-job-application", "job-apply-entry", "faq-duplicate-apply");
        assertTrue(Collections.disjoint(jobIds, ids(knowledge.lookup(student, ""))));
        var configService = menuConfig(knowledge, "[{\"path\":\"/student/jobs\"}]");
        assertTrue(ids(knowledge.lookup(student, "")).containsAll(jobIds));
        assertTrue(((List<?>) knowledge.lookup(student, "").facts().get("visibleStudentMenus")).contains("/student/jobs"));
        menuConfig(knowledge, "[{\"path\":\"/student/jobs\",\"visible\":false}]");
        assertTrue(Collections.disjoint(jobIds, ids(knowledge.lookup(student, ""))));
        for (String invalid : List.of("", "[]", "{}", "invalid json")) {
            menuConfig(knowledge, invalid);
            var visible = ids(knowledge.lookup(student, ""));
            assertTrue(Collections.disjoint(jobIds, visible), invalid);
            assertTrue(visible.containsAll(Set.of("admin-student-home", "student-profile-completeness", "admin-account-login-profile")));
            assertEquals(List.of("/student/home", "/student/profile"), knowledge.lookup(student, "").facts().get("visibleStudentMenus"));
        }
        ReflectionTestUtils.setField(knowledge, "configService", configService);
        when(configService.findByConfigKey("STUDENT_MENU_CONFIG")).thenThrow(new IllegalStateException("unavailable"));
        assertTrue(Collections.disjoint(jobIds, ids(knowledge.lookup(student, ""))));
    }

    @Test void otherRolesRetrieveTheirOwnModuleKnowledgeAndExcludeOtherRolePrivateModules() throws Exception {
        var knowledge = new AgentKnowledge(json);
        enableAllStudentMenus(knowledge);
        var studentQuestions = Map.of("怎样提交实习心得", "admin-student-reflections",
                "我的个人资料要补齐哪些必填项", "student-profile-completeness",
                "面试邀请怎么接受", "admin-student-interviews",
                "鉴定模板在哪里下载", "student-messages-resources-feedback");
        List<String> missing = new ArrayList<>();
        studentQuestions.forEach((q, id) -> {
            if (!ids(knowledge.lookup(student, q)).contains(id)) missing.add("student: " + q);
        });
        var companyQuestions = Map.of("招聘岗位怎么发布", "admin-company-recruitment",
                "企业收到的岗位申请怎么处理", "admin-company-applications",
                "企业如何最终确认学生实习", "admin-company-confirmations",
                "学校联络人在哪里查看", "company-school-liaisons",
                "企业注册被拒绝后怎么重新申请", "company-registration-status");
        companyQuestions.forEach((q, id) -> {
            if (!ids(knowledge.lookup(company, q)).contains(id)) missing.add("company: " + q + " -> " + ids(knowledge.lookup(company, q)));
        });
        assertTrue(missing.isEmpty(), missing.toString());
        assertFalse(ids(knowledge.lookup(company, "")).contains("teacher-personal-scoring-rules"));
        assertFalse(ids(knowledge.lookup(student, "")).contains("admin-company-applications"));
    }

    @Test void databaseGuidesCannotErasePackagedCoverageOrRestoreOutdatedInstructions() throws Exception {
        var knowledge = new AgentKnowledge(json);
        String initialVersion = knowledge.getVersion();
        var mapper = mock(AgentKnowledgeMapper.class);
        when(mapper.findAllCards()).thenReturn(List.of(
                Map.of("id", "reflection-howto", "roles", "[\"student\",\"teacher\"]", "title", "旧说明",
                        "steps", "支持保存草稿", "verifiedAt", "2026-09-15"),
                Map.of("id", "db-extra", "roles", "[\"company\"]", "title", "企业新增说明",
                        "rules", "数据库维护的扩展知识", "verifiedAt", "2026-10-08")));
        ReflectionTestUtils.setField(knowledge, "dbMapper", mapper);
        knowledge.load();
        assertTrue(ids(knowledge.lookup(admin, "")).containsAll(Set.of("admin-log-center", "teacher-personal-keywords",
                "internship-status-guide", "db-extra")));
        assertNotEquals(initialVersion, knowledge.getVersion());
        enableAllStudentMenus(knowledge);
        var reflection = knowledge.lookup(student, "reflection-howto").items().getFirst();
        assertEquals("reflection-howto", reflection.get("id"));
        assertTrue(reflection.get("steps").toString().contains("没有单独保存草稿按钮"));
        assertFalse(ids(knowledge.lookup(student, "")).contains("db-extra"));
        verify(mapper, times(1)).findAllCards();
    }

    @Test void newerDatabaseGuideStillRespectsPackagedPermissionPolicy() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var mapper = mock(AgentKnowledgeMapper.class);
        when(mapper.findAllCards()).thenReturn(List.of(Map.of("id", "teacher-auto-task-howto",
                "roles", "[\"student\",\"teacher\",\"company\",\"admin\"]", "title", "新增跟进说明",
                "steps", "较新的配置说明", "verifiedAt", "2026-10-09")));
        ReflectionTestUtils.setField(knowledge, "dbMapper", mapper);
        knowledge.load();
        assertFalse(ids(knowledge.lookup(student, "")).contains("teacher-auto-task-howto"));
        assertFalse(ids(knowledge.lookup(company, "")).contains("teacher-auto-task-howto"));
        assertFalse(ids(knowledge.lookup(actor("teacher", "COUNSELOR", Set.of()), "")).contains("teacher-auto-task-howto"));
        var visible = knowledge.lookup(actor("teacher", "COUNSELOR", Set.of("menu:student-tracking")), "teacher-auto-task-howto");
        assertEquals("新增跟进说明", visible.items().getFirst().get("title"));
    }

    @Test void databaseReadFailureRetainsPackagedKnowledge() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var mapper = mock(AgentKnowledgeMapper.class);
        when(mapper.findAllCards()).thenThrow(new IllegalStateException("unavailable"));
        ReflectionTestUtils.setField(knowledge, "dbMapper", mapper);
        String version = knowledge.getVersion();
        knowledge.load();
        assertEquals(version, knowledge.getVersion());
        assertTrue(ids(knowledge.lookup(admin, "备份恢复的操作流程")).contains("admin-backup-restore"));
    }

    @Test void generalKnowledgeClassificationDoesNotBypassSpecificRecordEvidence() {
        for (String question : List.of("实习心得怎么提交", "备份恢复的操作流程", "教师评分规则在哪里配置",
                "自主实习需要哪些条件", "企业如何确认学生实习"))
            assertTrue(AgentKnowledge.isGeneralKnowledgeQuestion(question), question);
        for (String question : List.of("这个企业为什么不能通过审核", "当前岗位如何提交", "这份申请怎么被拒绝了",
                "选中的实习确认如何处理", "学生还有多少人没交心得", "我的实习确认到哪一步了"))
            assertFalse(AgentKnowledge.isGeneralKnowledgeQuestion(question), question);
        assertFalse(AgentKnowledge.isGeneralKnowledgeQuestion("我的岗位在哪里"));
        assertFalse(AgentKnowledge.isGeneralKnowledgeQuestion("本企业的录用结果如何"));
    }
}
