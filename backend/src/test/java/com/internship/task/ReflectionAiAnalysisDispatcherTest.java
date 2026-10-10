package com.internship.task;

import com.internship.agent.business.AgentFeatureGate;
import com.internship.agent.business.AgentException;
import com.internship.entity.InternshipReflection;
import com.internship.entity.StudentReflectionAIAnalysis;
import com.internship.entity.TeacherUser;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.StudentReflectionAIAnalysisService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReflectionAiAnalysisDispatcherTest {
    private InternshipReflectionMapper reflections;
    private StudentReflectionAIAnalysisService analysis;
    private ClassCounselorRelationService assignments;
    private CounselorAISettingsService settings;
    private TeacherUserMapper teachers;
    private AgentFeatureGate gate;
    private AnnouncementWebSocketHandler notifications;
    private ReflectionAiAnalysisDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        reflections = mock(InternshipReflectionMapper.class);
        analysis = mock(StudentReflectionAIAnalysisService.class);
        assignments = mock(ClassCounselorRelationService.class);
        settings = mock(CounselorAISettingsService.class);
        teachers = mock(TeacherUserMapper.class);
        gate = mock(AgentFeatureGate.class);
        notifications = mock(AnnouncementWebSocketHandler.class);
        when(gate.enabled()).thenReturn(true);
        when(reflections.findById(anyLong())).thenAnswer(call -> submitted(call.getArgument(0)));
        when(reflections.latestSubmissionEventId(anyLong(), eq(42L))).thenReturn(44L);
        when(assignments.findCounselorIdByStudentId(42L)).thenReturn(9L);
        when(settings.isAiScoringEnabled(9L)).thenReturn(true);
        when(teachers.findById(anyLong())).thenAnswer(call -> {
            TeacherUser teacher = new TeacherUser();
            teacher.setId(call.getArgument(0)); teacher.setStatus(1); teacher.setTeacherType("COUNSELOR");
            return teacher;
        });
        when(analysis.analyzeReflectionContent(anyString(), eq(9L), eq(42L), anyLong()))
                .thenReturn(Map.of("success", true));
        dispatcher = createDispatcher();
    }

    private ReflectionAiAnalysisDispatcher createDispatcher() {
        return new ReflectionAiAnalysisDispatcher(reflections, analysis, assignments, settings, teachers, gate, notifications, 1, 1, 60);
    }

    @AfterEach
    void tearDown() {
        dispatcher.shutdown();
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void submissionWaitsForCommitBeforeAnyWorkerReadsTheDatabase() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        dispatcher.dispatchAfterCommit(10L);
        verifyNoInteractions(reflections, analysis, assignments, settings, teachers);
        var callbacks = TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, callbacks.size());
        callbacks.getFirst().afterCommit();
        verify(analysis, timeout(2000)).analyzeReflectionContent("content-10", 9L, 42L, 10L);
        idle();
        verify(notifications).sendTeacherTodoUpdate(9L, 1L);
    }

    @Test
    void rollbackAndTransactionsWithoutSynchronizationNeverLaunchBeforeCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        dispatcher.dispatchAfterCommit(10L);
        TransactionSynchronizationManager.getSynchronizations().forEach(callback ->
                callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        TransactionSynchronizationManager.clearSynchronization();
        dispatcher.dispatchAfterCommit(11L);
        verifyNoInteractions(reflections, analysis, notifications);
    }

    @Test
    void boundedQueueDeduplicatesAndRecoversRejectedSubmissionsWithoutCountingFailure() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(analysis.analyzeReflectionContent("content-10", 9L, 42L, 10L)).thenAnswer(call -> {
            entered.countDown(); assertTrue(release.await(2, TimeUnit.SECONDS)); return Map.of("success", true);
        });
        assertTrue(dispatcher.enqueue(10L)); assertTrue(entered.await(2, TimeUnit.SECONDS));
        assertFalse(dispatcher.enqueue(10L));
        assertTrue(dispatcher.enqueue(11L)); assertFalse(dispatcher.enqueue(12L));
        verify(reflections, never()).recordAiFailure(any(), anyLong(), any());
        release.countDown(); idle();
        when(reflections.findPendingAiAnalysis(1, 3, 60)).thenReturn(List.of(12L));
        dispatcher.recoverPending(); idle();
        verify(analysis).analyzeReflectionContent("content-12", 9L, 42L, 12L);
        verify(reflections, never()).recordAiFailure(any(), anyLong(), any());
    }

    @Test
    void restartRecoversDurableRowsAndRetainsTheThreeAttemptLimit() {
        AtomicInteger failures = new AtomicInteger(2);
        when(reflections.countAiFailures(10L, 42L, 44L)).thenAnswer(call -> failures.get());
        when(reflections.recordAiFailure(any(), eq(44L), any())).thenAnswer(call -> { failures.incrementAndGet(); return 1; });
        when(analysis.analyzeReflectionContent("content-10", 9L, 42L, 10L))
                .thenReturn(Map.of("success", false, "retryable", true));
        when(reflections.findPendingAiAnalysis(1, 3, 60)).thenReturn(List.of(10L));
        dispatcher.shutdown(); dispatcher = createDispatcher();
        dispatcher.recoverPending(); idle();
        assertEquals(3, failures.get());
        dispatcher.shutdown(); dispatcher = createDispatcher();
        // Even a stale recovery candidate cannot start a fourth attempt.
        dispatcher.recoverPending(); idle();
        verify(analysis, times(1)).analyzeReflectionContent("content-10", 9L, 42L, 10L);
        verify(reflections, times(1)).recordAiFailure(any(), eq(44L), any());
        verify(notifications, never()).sendTeacherTodoUpdate(anyLong(), anyLong());
        verify(notifications).sendAIAnalysisResultToUser(42L, 10L, false, false, ReflectionAiAnalysisDispatcher.FAILURE_MESSAGE);
    }

    @Test
    void deferredStaleAndRejectedContentDoNotConsumeRetryBudgetOrNotifyTeacher() {
        for (String flag : List.of("deferred", "stale", "isNotReflection")) {
            when(analysis.analyzeReflectionContent("content-10", 9L, 42L, 10L))
                    .thenReturn(Map.of("success", false, "retryable", true, flag, true));
            assertTrue(dispatcher.enqueue(10L)); idle();
        }
        verify(reflections, never()).recordAiFailure(any(), anyLong(), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void queuedWorkUsesCurrentCounselorAndSkipsWithdrawnDisabledOrAlreadyAnalyzedRows() {
        InternshipReflection withdrawn = submitted(10L); withdrawn.setRemark("0");
        when(reflections.findById(10L)).thenReturn(withdrawn);
        dispatcher.enqueue(10L); idle();
        when(analysis.findByReflectionId(11L)).thenReturn(new StudentReflectionAIAnalysis());
        dispatcher.enqueue(11L); idle();
        when(settings.isAiScoringEnabled(9L)).thenReturn(false);
        dispatcher.enqueue(12L); idle();
        when(settings.isAiScoringEnabled(19L)).thenReturn(true);
        when(assignments.findCounselorIdByStudentId(42L)).thenReturn(19L);
        when(analysis.analyzeReflectionContent("content-13", 19L, 42L, 13L)).thenReturn(Map.of("success", true));
        dispatcher.enqueue(13L); idle();
        verify(analysis, times(1)).analyzeReflectionContent(anyString(), anyLong(), anyLong(), anyLong());
        verify(analysis).analyzeReflectionContent("content-13", 19L, 42L, 13L);
        verify(notifications).sendTeacherTodoUpdate(19L, 1L);
    }

    @Test
    void queuedAndRecoveredWorkSkipsSavedManualScoresBeforePublication() {
        when(reflections.hasManualEvaluation(10L)).thenReturn(true);
        when(reflections.hasManualEvaluation(11L)).thenReturn(true);
        assertTrue(dispatcher.enqueue(10L)); idle();
        // A recovery candidate can become manually scored after its SQL scan.
        when(reflections.findPendingAiAnalysis(1, 3, 60)).thenReturn(List.of(11L));
        dispatcher.recoverPending(); idle();
        verify(reflections).hasManualEvaluation(10L);
        verify(reflections).hasManualEvaluation(11L);
        verify(reflections, never()).recordAiFailure(any(), anyLong(), any());
        verifyNoInteractions(analysis, assignments, settings, teachers, notifications);
    }

    @Test
    void missingDisabledOrChangedTeacherCannotStartAiOrConsumeAttempts() {
        TeacherUser disabled = new TeacherUser(); disabled.setStatus(0); disabled.setTeacherType("COUNSELOR");
        TeacherUser changedRole = new TeacherUser(); changedRole.setStatus(1); changedRole.setTeacherType("DEPARTMENT");
        when(teachers.findById(9L)).thenReturn(null, disabled, changedRole);
        for (long id = 10L; id <= 12L; id++) {
            assertTrue(dispatcher.enqueue(id)); idle();
        }
        verify(analysis, never()).analyzeReflectionContent(anyString(), anyLong(), anyLong(), anyLong());
        verify(reflections, never()).recordAiFailure(any(), anyLong(), any());
        verifyNoInteractions(notifications, settings);
    }

    @Test
    void resubmissionGetsItsOwnRetryBudgetAndStaleFailureDoesNotNotify() {
        when(reflections.latestSubmissionEventId(10L, 42L)).thenReturn(45L);
        when(reflections.countAiFailures(10L, 42L, 44L)).thenReturn(3);
        when(reflections.countAiFailures(10L, 42L, 45L)).thenReturn(0);
        when(analysis.analyzeReflectionContent("content-10", 9L, 42L, 10L)).thenReturn(Map.of("retryable", true));
        when(reflections.recordAiFailure(any(), eq(45L), any())).thenReturn(0);
        dispatcher.enqueue(10L); idle();
        verify(analysis).analyzeReflectionContent("content-10", 9L, 42L, 10L);
        verify(reflections).recordAiFailure(any(), eq(45L), any());
        verifyNoInteractions(notifications);
    }

    @Test
    void failureBeforeModelInvocationDoesNotExhaustAStudentsAttempts() {
        when(assignments.findCounselorIdByStudentId(42L)).thenThrow(new IllegalStateException("temporary database issue"));
        dispatcher.enqueue(10L); idle();
        verify(analysis, never()).analyzeReflectionContent(anyString(), anyLong(), anyLong(), anyLong());
        verify(reflections, never()).recordAiFailure(any(), anyLong(), any());
    }

    @Test
    void admissionRejectionIsDeferredWithoutSpendingAnAttempt() {
        when(analysis.analyzeReflectionContent("content-10", 9L, 42L, 10L))
                .thenThrow(new AgentException("AI_BUSY", "稍后重试", 429));
        dispatcher.enqueue(10L); idle();
        verify(reflections, never()).recordAiFailure(any(), anyLong(), any());
        verifyNoInteractions(notifications);
    }

    private void idle() {
        ThreadPoolExecutor pool = (ThreadPoolExecutor) ReflectionTestUtils.getField(dispatcher, "executor");
        await().atMost(Duration.ofSeconds(3)).until(() -> pool.getActiveCount() == 0 && pool.getQueue().isEmpty());
    }

    private static InternshipReflection submitted(Long id) {
        InternshipReflection reflection = new InternshipReflection();
        reflection.setId(id); reflection.setStudentId(42L); reflection.setCounselorId(2L);
        reflection.setStatus(0); reflection.setDeleted(0); reflection.setSubmitTime(new Date(1000));
        reflection.setContent("content-" + id);
        return reflection;
    }
}
