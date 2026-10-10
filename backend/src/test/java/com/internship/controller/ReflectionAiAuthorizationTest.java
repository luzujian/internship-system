package com.internship.controller;

import com.internship.entity.*;
import com.internship.exception.GlobalExceptionHandler;
import com.internship.mapper.CounselorAISettingsMapper;
import com.internship.mapper.CounselorScoringRuleMapper;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.service.*;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReflectionAiAuthorizationTest {
    private final TeacherUserMapper teachers = mock(TeacherUserMapper.class);
    private final TeacherAutomationScopeMapper scope = mock(TeacherAutomationScopeMapper.class);
    private final InternshipReflectionService reflections = mock(InternshipReflectionService.class);
    private final InternshipReflectionMapper batchReflections = mock(InternshipReflectionMapper.class);
    private final Map<Long, InternshipReflection> storedReflections = new java.util.HashMap<>();
    private final ClassCounselorRelationService assignments = mock(ClassCounselorRelationService.class);
    private final CounselorAISettingsMapper settingsMapper = mock(CounselorAISettingsMapper.class);
    private final CounselorScoringRuleMapper rules = mock(CounselorScoringRuleMapper.class);
    private final StudentReflectionAIAnalysisService analyses = mock(StudentReflectionAIAnalysisService.class);
    private final CounselorAISettingsService settings = mock(CounselorAISettingsService.class);
    private final ReflectionAiAccessPolicy access = new ReflectionAiAccessPolicy(
            new EvaluationAccessPolicy(teachers, scope, reflections), reflections, assignments, teachers, settingsMapper, rules, batchReflections);
    private final StudentReflectionAIAnalysisController student = new StudentReflectionAIAnalysisController();
    private final CounselorAIAnalysisController counselor = new CounselorAIAnalysisController();
    private final CounselorAISettingsController configuration = new CounselorAISettingsController();

    @BeforeEach
    void wireControllers() {
        when(batchReflections.findByIds(anyList())).thenAnswer(i -> ((List<Long>) i.getArgument(0)).stream().map(storedReflections::get).filter(java.util.Objects::nonNull).toList());
        ReflectionTestUtils.setField(student, "aiAnalysisService", analyses);
        ReflectionTestUtils.setField(student, "aiAccess", access);
        ReflectionTestUtils.setField(student, "counselorAISettingsService", settings);
        ReflectionTestUtils.setField(counselor, "studentReflectionAIAnalysisService", analyses);
        ReflectionTestUtils.setField(counselor, "aiAccess", access);
        ReflectionTestUtils.setField(counselor, "internshipReflectionService", reflections);
        ReflectionTestUtils.setField(counselor, "counselorAISettingsService", settings);
        ReflectionTestUtils.setField(configuration, "counselorAISettingsService", settings);
        ReflectionTestUtils.setField(configuration, "aiAccess", access);
    }

    @AfterEach
    void clearActor() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentCannotReadAnotherStudentsReportThroughAnyAliasOrDownload() {
        login(7L, "S007", "ROLE_STUDENT");
        report(99L, 8L);
        for (Executable request : List.<Executable>of(
                () -> student.getAnalysisById(199L),
                () -> student.getAnalysisByReflectionId(99L),
                () -> student.getAnalysisByStudentId(8L),
                () -> student.downloadAnalysisReport(199L))) {
            assertThrows(AccessDeniedException.class, request);
        }
    }

    @Test
    void ownershipDenialIsHttp403AndOwnStudentReportRemainsReadable() throws Exception {
        login(7L, "S007", "ROLE_STUDENT");
        report(99L, 8L);
        ProxyFactory proxy = new ProxyFactory(student);
        proxy.setProxyTargetClass(true);
        proxy.addAdvisor(AuthorizationManagerBeforeMethodInterceptor.preAuthorize());
        var secured = (StudentReflectionAIAnalysisController) proxy.getProxy();
        var mvc = MockMvcBuilders.standaloneSetup(secured).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/api/reflection/ai-analysis/199")).andExpect(status().isForbidden());
        login(8L, "S008", "ROLE_STUDENT");
        mvc.perform(get("/api/reflection/ai-analysis/199")).andExpect(status().isOk());
        assertEquals(200, student.downloadAnalysisReport(199L).getStatusCode().value());
        login(8L, "C008", "ROLE_COMPANY");
        mvc.perform(get("/api/reflection/ai-analysis/199")).andExpect(status().isForbidden());
    }

    @Test
    void analysisStudentMustMatchTheStoredReflectionEvenForAdmin() {
        login(1L, "admin", "ROLE_ADMIN");
        var report = report(99L, 8L);
        report.setStudentId(7L);
        assertThrows(AccessDeniedException.class, () -> student.getAnalysisById(199L));
        assertThrows(AccessDeniedException.class, () -> counselor.downloadAnalysisReport(99L, new MockHttpServletResponse()));
    }

    @Test
    void teacherCannotReadDeleteOrTriggerOutsideCurrentStudentScope() {
        teacher();
        report(99L, 8L);
        for (Executable request : List.<Executable>of(
                () -> student.deleteAnalysis(199L),
                () -> counselor.getAnalysisByReflectionId(99L),
                () -> counselor.getReflectionDetail(99L),
                () -> counselor.getAnalysisByStudentId(8L, null),
                () -> counselor.downloadAnalysisReport(99L, new MockHttpServletResponse()),
                () -> counselor.triggerAnalysis(99L, Map.of("counselorId", 999L)),
                () -> student.analyzeReflection(Map.of("reflectionId", 99L, "counselorId", 999L,
                        "studentId", 7L, "content", "forged")))) {
            assertThrows(AccessDeniedException.class, request);
        }
        verify(analyses, never()).deleteById(anyLong());
        verify(analyses, never()).analyzeReflectionContent(anyString(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void authorizedManualAnalysisDerivesAllInputsFromStoredReflection() {
        allowStudent(7L);
        report(99L, 7L);
        when(assignments.findCounselorIdByStudentId(7L)).thenReturn(23L);
        when(settings.isAiScoringEnabled(23L)).thenReturn(true);
        when(analyses.analyzeReflectionContent("stored-content", 23L, 7L, 99L))
                .thenReturn(Map.of("success", true, "data", "approved"));
        assertEquals(200, student.analyzeReflection(Map.of("reflectionId", 99L, "studentId", 8L,
                "counselorId", 999L, "content", "forged")).getCode());
        assertEquals(200, counselor.triggerAnalysis(99L, Map.of("counselorId", 999L)).getCode());
        verify(analyses, times(2)).analyzeReflectionContent("stored-content", 23L, 7L, 99L);
    }

    @Test
    void adminCanTriggerCurrentAssignedCounselorAndFailureStaysFailure() {
        login(1L, "admin", "ROLE_ADMIN");
        report(99L, 7L);
        when(assignments.findCounselorIdByStudentId(7L)).thenReturn(23L);
        when(settings.isAiScoringEnabled(23L)).thenReturn(true);
        when(analyses.analyzeReflectionContent("stored-content", 23L, 7L, 99L))
                .thenReturn(Map.of("success", false, "message", "provider unavailable"));
        assertEquals(400, counselor.triggerAnalysis(99L, Map.of()).getCode());
        assertEquals(400, student.analyzeReflection(Map.of("reflectionId", 99L)).getCode());
    }

    @Test
    void exactReflectionSelectionDoesNotFallBackToLatestStudentReport() {
        allowStudent(7L);
        var expected = report(99L, 7L);
        var response = counselor.getAnalysisByStudentId(7L, 99L);
        assertEquals(200, response.getCode());
        assertEquals(expected.getReflectionId(), ((Map<?, ?>) response.getData()).get("reflectionId"));
        verify(analyses, never()).findByStudentId(anyLong());
        report(100L, 8L);
        assertThrows(AccessDeniedException.class, () -> counselor.getAnalysisByStudentId(7L, 100L));
    }

    @Test
    void counselorListsFilterStudentsWhoHaveMovedOutOfScope() {
        allowStudent(7L);
        var retained = report(99L, 7L);
        var moved = report(100L, 8L);
        when(analyses.findByCounselorId(23L)).thenReturn(List.of(retained, moved));
        assertEquals(List.of(retained), student.getAnalysesByCounselorId(23L).getData());
        assertEquals(List.of(retained), counselor.getAnalysesByCounselorId(23L).getData());
        assertThrows(AccessDeniedException.class, () -> counselor.getAnalysesByCounselorId(24L));
        assertThrows(AccessDeniedException.class, () -> counselor.checkAiScoringEnabled(24L));
    }

    @Test
    void everyCounselorKeyedSettingsRouteRejectsAnotherCounselorBeforeServiceCalls() {
        teacher();
        for (Executable request : List.<Executable>of(
                () -> configuration.getSettings(24L),
                () -> configuration.saveSettings(Map.of("counselorId", 24L)),
                () -> configuration.getScoringRules(24L),
                () -> configuration.getScoringRulesByCategory(24L, "attitude"),
                () -> configuration.getCategories(24L),
                () -> configuration.saveScoringRules(Map.of("counselorId", 24L)),
                () -> configuration.deleteScoringRulesByCategory(24L, "attitude"),
                () -> configuration.batchCreateWithAIDescription(Map.of("counselorId", 24L)),
                () -> configuration.getCategoryWeights(24L),
                () -> configuration.getActiveCategoryWeights(24L),
                () -> configuration.batchUpdateCategoryWeights(List.of(Map.of("counselorId", 24L))),
                () -> configuration.deleteCategoryWeightByCategoryCode(24L, "attitude"))) {
            assertThrows(AccessDeniedException.class, request);
        }
        verifyNoInteractions(settings);
    }

    @Test
    void settingsAndRuleIdsAreAuthorizedAgainstTheirStoredOwner() {
        teacher();
        var stored = new CounselorAISettings(); stored.setId(51L); stored.setCounselorId(24L);
        when(settingsMapper.findById(51L)).thenReturn(stored);
        var update = new CounselorAISettings(); update.setId(51L); update.setCounselorId(23L);
        assertThrows(AccessDeniedException.class, () -> configuration.updateSettings(update));
        update.setCounselorId(null);
        assertThrows(AccessDeniedException.class, () -> configuration.updateSettings(update));
        var storedRule = new CounselorScoringRule(); storedRule.setId(61L); storedRule.setCounselorId(24L);
        when(rules.findById(61L)).thenReturn(storedRule);
        var ruleUpdate = new CounselorScoringRule(); ruleUpdate.setCounselorId(23L);
        assertThrows(AccessDeniedException.class, () -> configuration.updateScoringRule(61L, ruleUpdate));
        assertThrows(AccessDeniedException.class, () -> configuration.deleteScoringRule(61L));
        verifyNoInteractions(settings);
    }

    @Test
    void responsibleCounselorCanUpdateAndDeleteOwnRuleWithoutClaimingAnOwner() {
        teacher();
        var stored = new CounselorScoringRule(); stored.setId(61L); stored.setCounselorId(23L);
        when(rules.findById(61L)).thenReturn(stored);
        when(settings.updateScoringRule(any())).thenReturn(1);
        when(settings.deleteScoringRule(61L)).thenReturn(1);
        var update = new CounselorScoringRule(); update.setId(999L); update.setDescription("updated");
        assertEquals(200, configuration.updateScoringRule(61L, update).getCode());
        assertEquals(61L, update.getId());
        assertEquals(23L, update.getCounselorId());
        assertEquals(200, configuration.deleteScoringRule(61L).getCode());
    }

    @Test
    void mixedOwnerWeightBatchIsRejectedBeforeAnyWriteIncludingForAdmin() {
        teacher();
        var rows = List.of(weight(23L), weight(24L));
        assertThrows(AccessDeniedException.class, () -> configuration.batchUpdateCategoryWeights(rows));
        login(1L, "admin", "ROLE_ADMIN");
        assertThrows(AccessDeniedException.class, () -> configuration.batchUpdateCategoryWeights(rows));
        verifyNoInteractions(settings);
    }

    @Test
    void selfSettingsAndAdministratorUpdateOfStoredOwnerRemainSupported() {
        teacher();
        assertEquals(200, configuration.saveSettings(Map.of("counselorId", 23L, "enableAiScoring", 1)).getCode());
        assertEquals(200, configuration.batchUpdateCategoryWeights(List.of(weight(23L))).getCode());
        var stored = new CounselorAISettings(); stored.setId(51L); stored.setCounselorId(23L);
        when(settingsMapper.findById(51L)).thenReturn(stored);
        when(settings.update(any())).thenReturn(1);
        var update = new CounselorAISettings(); update.setId(51L);
        assertEquals(200, configuration.updateSettings(update).getCode());
        assertEquals(23L, update.getCounselorId());
        login(1L, "admin", "ROLE_ADMIN");
        stored.setCounselorId(24L); update.setCounselorId(null);
        assertEquals(200, configuration.updateSettings(update).getCode());
        assertEquals(24L, update.getCounselorId());
    }

    @Test
    void disabledOrReassignedTeacherIdentityCannotManageSettings() {
        TeacherUser live = teacher();
        live.setStatus(0);
        assertThrows(AccessDeniedException.class, () -> configuration.getSettings(23L));
        live.setStatus(1); live.setTeacherUserId("different-account");
        assertThrows(AccessDeniedException.class, () -> configuration.getSettings(23L));
        verifyNoInteractions(settings);
    }

    @Test
    void sevenHundredReportRowsUseBatchesAndStillRejectWrongOrDeletedOwnership() {
        allowStudent(7L);
        var reports = new java.util.ArrayList<StudentReflectionAIAnalysis>();
        for (long id = 1; id <= 700; id++) reports.add(report(id, 7L));
        storedReflections.get(3L).setStudentId(8L);
        storedReflections.get(4L).setDeleted(1);
        when(analyses.findByCounselorId(23L)).thenReturn(reports);
        clearInvocations(teachers, scope, reflections, batchReflections);
        var result = (List<?>) counselor.getAnalysesByCounselorId(23L).getData();
        assertEquals(698, result.size());
        verify(batchReflections, times(2)).findByIds(anyList());
        verify(scope, times(1)).studentIds(any());
        verify(scope, never()).contains(any(), anyLong());
        verify(reflections, never()).findById(anyLong());
        // Controller counselor ownership and current scope each check the teacher once.
        verify(teachers, times(2)).findById(23L);
    }

    private Map<String, Object> weight(Long owner) {
        return Map.of("counselorId", owner, "categoryCode", "attitude", "categoryName", "attitude", "weight", 100);
    }

    private StudentReflectionAIAnalysis report(Long reflectionId, Long owner) {
        var reflection = new InternshipReflection();
        reflection.setId(reflectionId); reflection.setStudentId(owner); reflection.setDeleted(0); reflection.setContent("stored-content");
        when(reflections.findById(reflectionId)).thenReturn(reflection);
        storedReflections.put(reflectionId, reflection);
        var report = new StudentReflectionAIAnalysis();
        report.setId(reflectionId + 100); report.setReflectionId(reflectionId); report.setStudentId(owner);
        report.setCounselorId(23L); report.setAnalysisReport("{}");
        when(analyses.findById(report.getId())).thenReturn(report);
        when(analyses.findByReflectionId(reflectionId)).thenReturn(report);
        when(analyses.findByStudentId(owner)).thenReturn(report);
        return report;
    }

    private void allowStudent(Long studentId) {
        TeacherUser live = teacher();
        when(scope.contains(live, studentId)).thenReturn(1);
        when(scope.studentIds(live)).thenReturn(List.of(studentId));
    }

    private TeacherUser teacher() {
        login(23L, "T023", "ROLE_TEACHER_COUNSELOR");
        var live = new TeacherUser();
        live.setId(23L); live.setTeacherUserId("T023"); live.setTeacherType("COUNSELOR"); live.setStatus(1);
        when(teachers.findById(23L)).thenReturn(live);
        return live;
    }

    private void login(Long id, String username, String role) {
        var actor = new User(); actor.setId(id); actor.setUsername(username); actor.setRole(role);
        CurrentHolder.setUser(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, "unused", List.of(new SimpleGrantedAuthority(role))));
    }
}
