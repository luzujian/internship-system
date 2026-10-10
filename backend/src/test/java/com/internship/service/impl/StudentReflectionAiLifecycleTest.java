package com.internship.service.impl;

import com.internship.agent.business.AgentException;
import com.internship.config.DynamicChatClientFactory;
import com.internship.entity.*;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.mapper.StudentReflectionAIAnalysisMapper;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.InternshipReflectionService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.StudentReflectionAIAnalysisService;
import com.internship.util.AiServiceCircuitBreaker;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentReflectionAiLifecycleTest {
    private final StudentReflectionAIAnalysisServiceImpl target = new StudentReflectionAIAnalysisServiceImpl();
    private final StudentReflectionAIAnalysisMapper analyses = mock(StudentReflectionAIAnalysisMapper.class);
    private final InternshipReflectionMapper reflections = mock(InternshipReflectionMapper.class);
    private final InternshipReflectionService reflectionService = mock(InternshipReflectionService.class);
    private final InternshipProgressRecordService progress = mock(InternshipProgressRecordService.class);
    private final CounselorAISettingsService settings = mock(CounselorAISettingsService.class);
    private final DynamicChatClientFactory clients = mock(DynamicChatClientFactory.class);
    private final AiServiceCircuitBreaker breaker = mock(AiServiceCircuitBreaker.class);
    private final AnnouncementWebSocketHandler notifications = mock(AnnouncementWebSocketHandler.class);
    private final ChatClient chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final TestTransactions transactions = new TestTransactions();
    private final TransactionTemplate writes = new TransactionTemplate(transactions);
    private InternshipReflection snapshot;
    private InternshipReflection current;
    private StudentReflectionAIAnalysisService service;
    private static final String VALID = """
        {"keywords":["实践"],"overallAnalysis":"完成实习任务并反思",
         "analysisReport":"有具体工作记录和改进计划", "sentimentPositive":60,
         "sentimentNeutral":30,"sentimentNegative":10,
         "scoreDetails":{"attitude":"优秀","ability":"良好","report":"中等"},
         "totalScore":1,"grade":"不及格"}
        """;

    @BeforeEach void setUp() {
        snapshot = new InternshipReflection();
        snapshot.setId(11L); snapshot.setStudentId(7L); snapshot.setContent("我的实习心得");
        snapshot.setStatus(0); snapshot.setDeleted(0);
        snapshot.setSubmitTime(new Date(1000)); snapshot.setUpdateTime(new Date(1000));
        current = copy(snapshot);
        ReflectionTestUtils.setField(target, "analysisMapper", analyses);
        ReflectionTestUtils.setField(target, "reflectionMapper", reflections);
        ReflectionTestUtils.setField(target, "progressRecordService", progress);
        ReflectionTestUtils.setField(target, "internshipReflectionService", reflectionService);
        ReflectionTestUtils.setField(target, "counselorAISettingsService", settings);
        ReflectionTestUtils.setField(target, "chatClientFactory", clients);
        ReflectionTestUtils.setField(target, "circuitBreaker", breaker);
        ReflectionTestUtils.setField(target, "webSocketHandler", notifications);
        ReflectionTestUtils.setField(target, "transactionTemplate", writes);
        when(reflectionService.findById(11L)).thenAnswer(i -> copy(snapshot));
        when(reflections.lockById(11L)).thenAnswer(i -> current);
        when(reflections.latestSubmissionEventId(11L, 7L)).thenReturn(10L);
        when(breaker.allowRequest(3L)).thenReturn(true);
        when(clients.resolveModelCode(anyString())).thenAnswer(i -> i.getArgument(0));
        when(clients.createChatClient(anyString(), anyString())).thenReturn(chat);
        when(settings.getScoringRules(3L)).thenReturn(List.of()); // exercise valid defaults
        when(analyses.insert(any())).thenReturn(1);
        when(reflections.completeAiAnalysis(eq(11L), anyBoolean(), any())).thenReturn(1);
        modelResponses("{\"isReflection\":true}", VALID);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (StudentReflectionAIAnalysisService) proxy.getProxy();
    }

    private void modelResponses(String validation, String analysis) {
        when(chat.prompt().user(anyString()).call().content()).thenReturn(validation, analysis);
    }
    private Map<String, Object> analyze() { return service.analyzeReflectionContent(snapshot.getContent(), 3L, 7L, 11L); }
    private void noWrites() {
        verify(analyses, never()).deleteByReflectionId(anyLong());
        verify(analyses, never()).insert(any());
        verify(reflections, never()).completeAiAnalysis(anyLong(), anyBoolean(), any());
    }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {" ", "not json", "{}", "{\"totalScore\":0}", "{\"keywords\":[],\"keywords\":[\"x\"]}", "{} {}", "```json\n{}"})
    void malformedAnalysisNeverPublishesAZeroScoreOrDeletesAnExistingReport(String response) {
        modelResponses("{\"isReflection\":true}", response);
        Map<String, Object> result = analyze();
        assertEquals(false, result.get("success"));
        assertEquals(true, result.get("retryable"));
        noWrites();
        verify(breaker).recordFailure(3L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"isReflection\":\"true\"}", "{\"isReflection\":true,\"isReflection\":false}", "{\"isReflection\":true} {}"})
    void invalidContentValidationDoesNotDefaultToApproved(String validation) {
        modelResponses(validation, VALID);
        assertEquals(false, analyze().get("success"));
        noWrites();
        verify(chat.prompt().user(anyString()).call(), times(1)).content();
    }

    @ParameterizedTest
    @ValueSource(strings = {"total", "sentiment", "fraction", "sum", "category", "grade", "report"})
    void impossibleScoringFieldsAreRejected(String field) {
        String invalid = switch (field) {
            case "total" -> VALID.replace("\"totalScore\":1", "\"totalScore\":101");
            case "sentiment" -> VALID.replace("\"sentimentPositive\":60", "\"sentimentPositive\":-1");
            case "fraction" -> VALID.replace("\"sentimentPositive\":60", "\"sentimentPositive\":60.1");
            case "sum" -> VALID.replace("\"sentimentNeutral\":30", "\"sentimentNeutral\":31");
            case "category" -> VALID.replace(",\"report\":\"中等\"", "");
            case "grade" -> VALID.replace("\"ability\":\"良好\"", "\"ability\":\"乱填等级\"");
            default -> VALID.replace("有具体工作记录和改进计划", "");
        };
        modelResponses("{\"isReflection\":true}", invalid);
        assertEquals(false, analyze().get("success"));
        noWrites();
    }

    @Test void validJsonUsesConfiguredWeightsAndRecordsTheResolvedModel() {
        modelResponses("```json\n{\"isReflection\":true}\n```", "```json\n" + VALID + "\n```");
        when(clients.resolveModelCode(anyString())).thenReturn("actual-model");
        Map<String, Object> result = analyze();
        assertEquals(true, result.get("success"));
        StudentReflectionAIAnalysis data = (StudentReflectionAIAnalysis) result.get("data");
        assertEquals(new BigDecimal("84.30"), data.getTotalScore());
        assertEquals("actual-model", data.getAiModelCode());
        verify(reflections).completeAiAnalysis(eq(11L), eq(true), any());
        verify(breaker).recordSuccess(3L);
        assertEquals(2, transactions.commits);
    }

    @ParameterizedTest @ValueSource(strings = {"withdraw", "review", "deleted", "content", "time", "owner", "version"})
    void lateResponseDoesNotOverwriteNewSubmissionWithdrawalOrManualReview(String change) {
        // Mutate during the API wait, after the service has captured its locked submission snapshot.
        if (change.equals("version")) {
            when(reflections.latestSubmissionEventId(11L, 7L)).thenReturn(10L, 12L);
        }
        when(chat.prompt().user(anyString()).call().content()).thenAnswer(new org.mockito.stubbing.Answer<String>() {
            int calls;
            public String answer(org.mockito.invocation.InvocationOnMock invocation) {
                if (calls++ != 0) return VALID;
                switch (change) {
                    case "withdraw" -> current.setRemark("0");
                    case "review" -> current.setStatus(2);
                    case "deleted" -> current.setDeleted(1);
                    case "content" -> current.setContent("新的心得");
                    case "time" -> current.setUpdateTime(new Date(2000));
                    case "owner" -> current.setStudentId(8L);
                    // The existing event id also distinguishes equal text and equal timestamps.
                    case "version" -> { }
                }
                return "{\"isReflection\":true}";
            }
        });
        Map<String, Object> result = analyze();
        assertEquals(true, result.get("stale"));
        assertEquals(false, result.get("success"));
        noWrites();
        verifyNoInteractions(notifications);
    }

    @Test void providerAdmissionRejectionIsDeferredAndDoesNotTripTheCircuitOrConsumeAttempts() {
        when(chat.prompt().user(anyString()).call().content()).thenThrow(new AgentException("AI_BUSY", "繁忙", 429));
        Map<String, Object> result = analyze();
        assertEquals(true, result.get("deferred"));
        noWrites();
        verify(breaker, never()).recordFailure(any());
    }

    @Test void alreadyManuallyScoredReflectionDoesNotSpendAnotherApiCall() {
        when(reflections.hasManualEvaluation(11L)).thenReturn(true);
        assertEquals(true, analyze().get("stale"));
        verify(clients, never()).createChatClient(anyString(), anyString());
        noWrites();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void lateAiResponseDoesNotChangeACommittedUnpublishedManualScore(boolean isReflection) {
        // The initial check is clear; a teacher saves a grade (including zero) during the API wait.
        when(reflections.hasManualEvaluation(11L)).thenReturn(false, true);
        modelResponses("{\"isReflection\":" + isReflection + "}", VALID);
        Map<String, Object> result = analyze();
        assertEquals(true, result.get("stale"));
        noWrites();
        verifyNoInteractions(progress, notifications);
    }

    @Test void databaseWriteFailureRollsBackAndIsNotReportedAsSuccess() {
        when(analyses.insert(any())).thenReturn(0);
        assertEquals(false, analyze().get("success"));
        assertEquals(1, transactions.rollbacks);
        assertEquals(1, transactions.commits);
        verify(reflections, never()).completeAiAnalysis(anyLong(), anyBoolean(), any());
    }

    @Test void notificationOutageDoesNotRetryACommittedAnalysis() {
        doThrow(new IllegalStateException("socket unavailable")).when(notifications)
                .sendAIAnalysisResultToUser(anyLong(), anyLong(), anyBoolean(), anyBoolean(), anyString());
        assertEquals(true, analyze().get("success"));
        assertEquals(2, transactions.commits);
        verify(breaker, never()).recordFailure(any());
    }

    @Test void negativeContentValidationReturnsToDraftOnlyIfSubmissionIsStillCurrent() {
        modelResponses("{\"isReflection\":false}", VALID);
        assertEquals(true, analyze().get("isNotReflection"));
        verify(analyses, never()).insert(any());
        verify(reflections).completeAiAnalysis(eq(11L), eq(false), any());
        var event = org.mockito.ArgumentCaptor.forClass(InternshipProgressRecord.class);
        verify(progress).saveRecord(event.capture());
        assertEquals("reflection_withdrawn", event.getValue().getEventType());
        assertEquals(11L, event.getValue().getRelatedId());
        assertEquals(7L, event.getValue().getStudentId());
    }

    @Test void rejectionLifecycleWriteFailureRollsBackTheDraftTransition() {
        modelResponses("{\"isReflection\":false}", VALID);
        doThrow(new IllegalStateException("event unavailable")).when(progress).saveRecord(any());
        assertEquals(false, analyze().get("success"));
        assertEquals(1, transactions.rollbacks);
        assertEquals(1, transactions.commits);
        verifyNoInteractions(notifications);
    }

    @Test void rejectedContentCanBeCorrectedAndResubmittedThroughTheStudentController() {
        var timeline = new java.util.ArrayList<InternshipProgressRecord>();
        InternshipProgressRecord original = new InternshipProgressRecord();
        original.setId(10L); original.setRelatedId(11L); original.setEventType("reflection_submitted");
        original.setStudentId(7L); timeline.add(original);
        doAnswer(i -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            InternshipProgressRecord event = i.getArgument(0); event.setId(11L); timeline.addFirst(event); return null;
        }).when(progress).saveRecord(any());
        when(reflections.completeAiAnalysis(eq(11L), eq(false), any())).thenAnswer(i -> {
            current.setStatus(0); current.setRemark("0"); return 1;
        });
        modelResponses("{\"isReflection\":false}", VALID);
        assertEquals(true, analyze().get("isNotReflection"));
        assertEquals("0", current.getRemark());

        // Real controller eligibility and lifecycle checks use the same stored events as the service.
        var controller = new com.internship.controller.StudentInternshipReflectionController();
        var users = mock(com.internship.service.UserService.class);
        var assignments = mock(com.internship.service.ClassCounselorRelationService.class);
        var confirmations = mock(com.internship.service.InternshipConfirmationRecordService.class);
        var placements = mock(com.internship.service.InternshipStageAssignmentService.class);
        var dispatcher = mock(com.internship.task.ReflectionAiAnalysisDispatcher.class);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "internshipReflectionService", reflectionService);
        ReflectionTestUtils.setField(controller, "progressRecordService", progress);
        ReflectionTestUtils.setField(controller, "classCounselorRelationService", assignments);
        ReflectionTestUtils.setField(controller, "confirmationRecordService", confirmations);
        ReflectionTestUtils.setField(controller, "stageAssignmentService", placements);
        ReflectionTestUtils.setField(controller, "reflectionAiDispatcher", dispatcher);
        User user = new User(); user.setId(7L); user.setUsername("s7");
        when(users.findByUsername("s7")).thenReturn(user);
        when(assignments.findCounselorIdByStudentId(7L)).thenReturn(3L);
        when(confirmations.findByStudentIdAndStatus(7L, 1)).thenReturn(List.of(new InternshipConfirmationRecord()));
        InternshipStageAssignment placement = new InternshipStageAssignment(); placement.setStatus("IN_PROGRESS");
        var date = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"));
        placement.setInternshipStartTime(Date.from(date.minusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));
        placement.setInternshipEndTime(Date.from(date.plusDays(1).atStartOfDay(java.time.ZoneId.of("Asia/Shanghai")).toInstant()));
        when(placements.findByStudentId(7L)).thenReturn(List.of(placement));
        when(reflectionService.calculatePeriodNumber(any())).thenReturn(1);
        doReturn(current).when(reflectionService).findById(11L);
        when(progress.getByStudentId(7L)).thenReturn(timeline);
        // This controller is invoked directly here; its real transactional save is covered separately.
        doNothing().when(progress).saveRecord(any());
        var principal = org.springframework.security.core.userdetails.User.withUsername("s7").password("unused").roles("STUDENT").build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        try {
            Result submitted = controller.submitReflection(Map.of("taskId", 11L, "content", "修改后的实习心得"));
            assertEquals(200, submitted.getCode(), submitted.getMessage());
            assertNull(current.getRemark());
            assertEquals("修改后的实习心得", current.getContent());
            verify(dispatcher).dispatchAfterCommit(11L);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void apiWaitSuspendsAnOuterTransactionAndTheFinalWriteIsShort() {
        when(chat.prompt().user(anyString()).call().content()).thenAnswer(new org.mockito.stubbing.Answer<String>() {
            int calls;
            public String answer(org.mockito.invocation.InvocationOnMock i) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "API call holds a DB transaction");
                return calls++ == 0 ? "{\"isReflection\":true}" : VALID;
            }
        });
        when(analyses.insert(any())).thenAnswer(i -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive()); return 1;
        });
        writes.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(true, analyze().get("success"));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "outer transaction was not resumed");
            return null;
        });
        assertEquals(3, transactions.commits);
    }

    @Test void bothSubmissionVersionsAreReadUnderTheReflectionLock() {
        when(reflections.latestSubmissionEventId(11L, 7L)).thenAnswer(i -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return 10L;
        });
        assertEquals(true, analyze().get("success"));
        var ordered = inOrder(reflections);
        ordered.verify(reflections).lockById(11L);
        ordered.verify(reflections).latestSubmissionEventId(11L, 7L);
        ordered.verify(reflections).lockById(11L);
        ordered.verify(reflections).latestSubmissionEventId(11L, 7L);
        assertEquals(2, transactions.commits);
    }

    @Test void duplicateAutomaticAndManualRequestsDoNotCallTheProviderTwice() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        when(chat.prompt().user(anyString()).call().content()).thenAnswer(new org.mockito.stubbing.Answer<String>() {
            int calls;
            public String answer(org.mockito.invocation.InvocationOnMock i) throws Exception {
                if (calls++ == 0) { entered.countDown(); assertTrue(release.await(5, TimeUnit.SECONDS)); return "{\"isReflection\":true}"; }
                return VALID;
            }
        });
        try (ExecutorService worker = Executors.newSingleThreadExecutor()) {
            Future<Map<String, Object>> first = worker.submit(this::analyze);
            try {
                assertTrue(entered.await(3, TimeUnit.SECONDS));
                assertEquals(true, analyze().get("deferred"));
                verify(chat.prompt().user(anyString()).call(), times(1)).content();
            } finally { release.countDown(); }
            assertEquals(true, first.get(3, TimeUnit.SECONDS).get("success"));
        }
        verify(analyses, times(1)).insert(any());
    }

    private static InternshipReflection copy(InternshipReflection reflection) {
        InternshipReflection copied = new InternshipReflection(); BeanUtils.copyProperties(reflection, copied); return copied;
    }
    /** Tracks Spring transaction lifecycle without accessing a database. */
    private static final class TestTransactions extends AbstractPlatformTransactionManager {
        static final class Tx { boolean active; }
        private final ThreadLocal<Tx> state = ThreadLocal.withInitial(Tx::new);
        int commits, rollbacks;
        protected Object doGetTransaction() { return state.get(); }
        protected boolean isExistingTransaction(Object tx) { return ((Tx) tx).active; }
        protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition def) { ((Tx) tx).active = true; }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        protected Object doSuspend(Object tx) { state.set(new Tx()); return tx; }
        protected void doResume(Object tx, Object suspended) { state.set((Tx) suspended); }
        protected void doCleanupAfterCompletion(Object tx) { ((Tx) tx).active = false; }
    }
}
