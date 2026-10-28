package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.controller.StudentInternshipReflectionController;
import com.internship.controller.CounselorAIAnalysisController;
import com.internship.entity.Result;
import com.internship.entity.SystemConfig;
import com.internship.interceptor.ReflectionFeatureInterceptor;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.task.ReflectionTaskGenerator;
import com.internship.task.ReflectionAiAnalysisDispatcher;
import com.internship.agent.business.AgentFeatureGate;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReflectionRetirementTest {
    @Test void retiredActivityIsFilteredBeforePaginationWithoutDeletingHistory() throws Exception {
        var configuration = new org.apache.ibatis.session.Configuration();
        String resource = "com/internship/mapper/InternshipProgressRecordMapper.xml";
        try (var stream = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(stream);
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(stream, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement("com.internship.mapper.InternshipProgressRecordMapper.selectRecentPage");
        var arguments = new java.util.HashMap<String,Object>();
        arguments.put("studentId", 42L); arguments.put("limit", 3); arguments.put("includeReflections", false);
        String disabledSql = statement.getBoundSql(arguments).getSql();
        assertTrue(disabledSql.contains("LOWER(r.event_type) NOT LIKE 'reflection%'"));
        assertTrue(disabledSql.indexOf("LOWER(r.event_type)") < disabledSql.lastIndexOf("LIMIT"));
        arguments.put("includeReflections", true);
        assertFalse(statement.getBoundSql(arguments).getSql().contains("NOT LIKE 'reflection%'"));
    }
    @Test void onlyAnExplicitActiveTrueConfigurationEnablesRetainedBusiness() {
        SystemConfigService configs = mock(SystemConfigService.class);
        ReflectionFeatureService feature = new ReflectionFeatureService(configs);
        assertFalse(feature.enabled());
        SystemConfig config = new SystemConfig();
        when(configs.findByConfigKey(ReflectionFeatureService.CONFIG_KEY)).thenReturn(config);
        for (String value : new String[]{null, "", "false", "1", "invalid", "true"}) {
            config.setConfigValue(value);
            assertFalse(feature.enabled(), "a disabled/uninitialized row cannot enable business");
        }
        config.setStatus(1);
        config.setConfigValue("true");
        assertTrue(feature.enabled());
        config.setStatus(0);
        assertFalse(feature.enabled());
        when(configs.findByConfigKey(ReflectionFeatureService.CONFIG_KEY)).thenThrow(new RuntimeException("offline"));
        assertFalse(feature.enabled());
        verify(configs, never()).insert(any());
        verify(configs, never()).update(any());
    }

    @Test void legacyReadsWritesAndDownloadsAreGoneWhileCurrentFlowsContinue() throws Exception {
        ReflectionFeatureService feature = mock(ReflectionFeatureService.class);
        var student = mock(StudentInternshipReflectionController.class);
        var analysis = mock(CounselorAIAnalysisController.class);
        var grading = mock(com.internship.controller.StudentReflectionEvaluationController.class);
        var rules = mock(com.internship.controller.ScoringRuleController.class);
        var keywords = mock(com.internship.controller.KeywordLibraryController.class);
        var counselorSettings = mock(com.internship.controller.CounselorAISettingsController.class);
        var gradeManagement = mock(com.internship.controller.EvaluationManagementController.class);
        var mvc = MockMvcBuilders.standaloneSetup(student, analysis, grading, rules, keywords,
                        counselorSettings, gradeManagement, new CurrentBusiness())
                .addInterceptors(new ReflectionFeatureInterceptor(feature, new ObjectMapper())).build();
        mvc.perform(get("/api/student/internship-reflection/list"))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value(410));
        mvc.perform(post("/api/student/internship-reflection/submit").contentType("application/json").content("{}"))
                .andExpect(status().isGone());
        mvc.perform(get("/api/counselor/ai-analysis/download/9")).andExpect(status().isGone());
        mvc.perform(post("/api/reflection/evaluation").contentType("application/json").content("{}"))
                .andExpect(status().isGone());
        for (String path : new String[]{"/api/admin/scoring-rule", "/api/admin/keyword-library",
                "/api/counselor/ai-settings/9", "/api/evaluation/students"})
            mvc.perform(get(path)).andExpect(status().isGone());
        verifyNoInteractions(student, analysis, grading, rules, keywords, counselorSettings, gradeManagement);
        for (String path : new String[]{"/api/current/appraisal", "/api/current/calendar", "/api/current/ai"})
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        when(feature.enabled()).thenReturn(true);
        when(student.getMyReflections()).thenReturn(Result.success("retained"));
        mvc.perform(get("/api/student/internship-reflection/list"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").value("retained"));
    }

    @Test void disabledGeneratorsDoNotReadOrCreateAnyLegacyTasks() {
        ReflectionFeatureService feature = mock(ReflectionFeatureService.class);
        var generator = new ReflectionTaskGenerator();
        var calendar = mock(InternshipCalendarService.class);
        var rows = mock(InternshipReflectionMapper.class);
        ReflectionTestUtils.setField(generator, "reflectionFeature", feature);
        ReflectionTestUtils.setField(generator, "calendarService", calendar);
        ReflectionTestUtils.setField(generator, "reflectionMapper", rows);
        generator.generateReflectionTasks();
        generator.generateNextTask(1L, 2L);
        verifyNoInteractions(calendar, rows);
    }

    @Test void disabledDispatcherNeverResumesOrCallsTheModelForHistoricalBacklog() {
        var rows = mock(InternshipReflectionMapper.class);
        var model = mock(StudentReflectionAIAnalysisService.class);
        var dispatcher = new ReflectionAiAnalysisDispatcher(rows, model, mock(ClassCounselorRelationService.class),
                mock(CounselorAISettingsService.class), mock(TeacherUserMapper.class), mock(AgentFeatureGate.class),
                mock(AnnouncementWebSocketHandler.class), 1, 1, 60);
        ReflectionTestUtils.setField(dispatcher, "reflectionFeature", mock(ReflectionFeatureService.class));
        try {
            dispatcher.dispatchAfterCommit(9L);
            dispatcher.recoverPending();
            verifyNoInteractions(rows, model);
        } finally { dispatcher.shutdown(); }
    }

    @RestController static class CurrentBusiness {
        @GetMapping({"/api/current/appraisal", "/api/current/calendar", "/api/current/ai"})
        public Result current() { return Result.success(); }
    }
}
