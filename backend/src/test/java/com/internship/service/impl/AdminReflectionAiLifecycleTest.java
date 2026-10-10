package com.internship.service.impl;

import com.internship.agent.business.AgentException;
import com.internship.config.DynamicChatClientFactory;
import com.internship.controller.InternshipReflectionController;
import com.internship.entity.CategoryWeight;
import com.internship.entity.InternshipReflection;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.service.*;
import com.internship.utils.FileParserUtil;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.BeanUtils;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.*;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AdminReflectionAiLifecycleTest {
    private final InternshipReflectionServiceImpl target = new InternshipReflectionServiceImpl();
    private final InternshipReflectionMapper reflections = mock(InternshipReflectionMapper.class);
    private final DynamicChatClientFactory clients = mock(DynamicChatClientFactory.class);
    private final CategoryWeightService weights = mock(CategoryWeightService.class);
    private final ChatClient chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final TestTransactions transactions = new TestTransactions();
    private final TransactionTemplate writes = new TransactionTemplate(transactions);
    private InternshipReflection snapshot, current;
    private InternshipReflectionService service;
    private static final String VALID = """
        {"keywords":["实践"],"comment":"有具体实习任务和改进计划", "improvements":["加强沟通"],
         "score":85.5,"aspects":{"attitude":80,"learning":90},"weightedScore":1}
        """;

    @BeforeEach void setUp() {
        snapshot = new InternshipReflection();
        snapshot.setId(11L); snapshot.setStudentId(7L); snapshot.setContent("我的实习心得");
        snapshot.setStatus(0); snapshot.setDeleted(0);
        snapshot.setSubmitTime(new Date(1000)); snapshot.setUpdateTime(new Date(1000));
        current = copy(snapshot);
        ReflectionTestUtils.setField(target, "internshipReflectionMapper", reflections);
        ReflectionTestUtils.setField(target, "chatClientFactory", clients);
        ReflectionTestUtils.setField(target, "categoryWeightService", weights);
        ReflectionTestUtils.setField(target, "scoringRuleService", mock(ScoringRuleService.class));
        ReflectionTestUtils.setField(target, "keywordLibraryService", mock(KeywordLibraryService.class));
        ReflectionTestUtils.setField(target, "systemConfigService", mock(SystemConfigService.class));
        ReflectionTestUtils.setField(target, "aiModelService", mock(AIModelService.class));
        ReflectionTestUtils.setField(target, "transactionTemplate", writes);
        when(reflections.lockById(11L)).thenAnswer(new org.mockito.stubbing.Answer<InternshipReflection>() {
            int calls;
            public InternshipReflection answer(org.mockito.invocation.InvocationOnMock invocation) {
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                return calls++ == 0 ? copy(snapshot) : current;
            }
        });
        when(reflections.latestSubmissionEventId(11L, 7L)).thenReturn(10L);
        when(reflections.updateById(any())).thenReturn(1);
        when(clients.createChatClient(anyString(), anyString())).thenReturn(chat);
        when(weights.findActive()).thenReturn(List.of(weight("attitude", 40), weight("learning", 60)));
        modelResponses("{\"isReflection\":true}", VALID);
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        service = (InternshipReflectionService) proxy.getProxy();
    }

    private void modelResponses(String validation, String analysis) {
        when(chat.prompt().user(anyString()).call().content()).thenReturn(validation, analysis);
    }
    private Map<String, Object> analyze() { return service.analyzeReflection(11L); }
    private void noWrites() { verify(reflections, never()).updateById(any()); }

    @ParameterizedTest @NullAndEmptySource
    @ValueSource(strings = {" ", "not json", "{}", "{} {}", "```json\n{}", "{\"score\":0}",
            "{\"keywords\":[],\"keywords\":[\"x\"]}"})
    void malformedAnalysisIsNeverSavedAsSuccessfulZeroScore(String response) {
        modelResponses("{\"isReflection\":true}", response);
        assertEquals(false, analyze().get("success"));
        noWrites();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"isReflection\":\"true\"}", "{\"isReflection\":true} {}",
            "{\"isReflection\":true,\"isReflection\":false}"})
    void malformedContentValidationDoesNotDefaultToApproved(String validation) {
        modelResponses(validation, VALID);
        assertEquals(false, analyze().get("success"));
        verify(chat.prompt().user(anyString()).call(), times(1)).content();
        noWrites();
    }

    @ParameterizedTest @ValueSource(strings = {"score", "stringScore", "missingScore", "comment", "keywords", "improvements", "aspect", "dimension", "weighted"})
    void impossibleOrMissingScoringFieldsAreRejected(String field) {
        String invalid = switch (field) {
            case "score" -> VALID.replace("\"score\":85.5", "\"score\":101");
            case "stringScore" -> VALID.replace("\"score\":85.5", "\"score\":\"85.5\"");
            case "missingScore" -> VALID.replace("\"score\":85.5,", "");
            case "comment" -> VALID.replace("有具体实习任务和改进计划", "");
            case "keywords" -> VALID.replace("[\"实践\"]", "[null]");
            case "improvements" -> VALID.replace("[\"加强沟通\"]", "\"加强沟通\"");
            case "aspect" -> VALID.replace("\"learning\":90", "\"learning\":-1");
            case "dimension" -> VALID.replace(",\"learning\":90", "");
            default -> VALID.replace("\"weightedScore\":1", "\"weightedScore\":null");
        };
        modelResponses("{\"isReflection\":true}", invalid);
        assertEquals(false, analyze().get("success"));
        noWrites();
    }

    @Test void validFencedJsonUsesSystemWeightsAndPreservesLegacyStoredScore() {
        modelResponses("```JSON\n{\"isReflection\":true}\n```", "```json\n" + VALID + "\n```");
        Map<String, Object> result = analyze();
        assertEquals(true, result.get("success"));
        Map<?, ?> data = (Map<?, ?>) result.get("data");
        assertEquals(86.0, data.get("weightedScore"));
        assertEquals(new BigDecimal("85.5"), current.getAiScore());
        assertEquals("[加强沟通]", current.getAiImprovements());
        assertEquals(1, current.getStatus());
        verify(weights, times(1)).findActive();
        verify(reflections).updateById(current);
        assertEquals(2, transactions.commits);
    }

    @Test void legitimateZeroScoreIsSavedOnlyFromCompleteValidJson() {
        modelResponses("{\"isReflection\":true}", VALID.replace("\"score\":85.5", "\"score\":0"));
        assertEquals(true, analyze().get("success"));
        assertEquals(BigDecimal.ZERO, current.getAiScore());
    }

    @ParameterizedTest @ValueSource(strings = {"withdraw", "review", "deleted", "content", "time", "submitTime", "owner", "version"})
    void lateResponseCannotOverwriteWithdrawalResubmissionOrManualReview(String change) {
        switch (change) {
            case "withdraw" -> current.setRemark("0");
            case "review" -> current.setStatus(2);
            case "deleted" -> current.setDeleted(1);
            case "content" -> current.setContent("新心得");
            case "time" -> current.setUpdateTime(new Date(2000));
            case "submitTime" -> current.setSubmitTime(new Date(2000));
            case "owner" -> current.setStudentId(8L);
            // Same text and second still have a different submission event.
            case "version" -> when(reflections.latestSubmissionEventId(11L, 7L)).thenReturn(10L, 12L);
        }
        assertEquals(true, analyze().get("stale"));
        noWrites();
    }

    @Test void alreadyWithdrawnOrUnsubmittedDraftDoesNotCallProvider() {
        snapshot.setRemark("0");
        assertEquals(true, analyze().get("stale"));
        verifyNoInteractions(clients);
        noWrites();
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void manualScoresIncludingUnpublishedGradesAreProtected(boolean beforeApi) {
        when(reflections.hasManualEvaluation(11L)).thenReturn(beforeApi, true);
        assertEquals(true, analyze().get("stale"));
        if (beforeApi) verifyNoInteractions(clients);
        noWrites();
    }

    @Test void databaseWriteFailureRollsBackAndDoesNotReportSuccess() {
        when(reflections.updateById(any())).thenReturn(0);
        assertEquals(false, analyze().get("success"));
        assertEquals(1, transactions.rollbacks);
        assertEquals(1, transactions.commits); // Only the initial snapshot transaction committed.
    }

    @Test void bothProviderCallsSuspendAnOuterTransactionAndRestoreItAfterwards() {
        when(chat.prompt().user(anyString()).call().content()).thenAnswer(new org.mockito.stubbing.Answer<String>() {
            int calls;
            public String answer(org.mockito.invocation.InvocationOnMock invocation) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                return calls++ == 0 ? "{\"isReflection\":true}" : VALID;
            }
        });
        writes.execute(status -> {
            assertEquals(true, analyze().get("success"));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        });
        assertEquals(3, transactions.commits);
    }

    @Test void providerAdmissionRejectionKeepsIts429Status() {
        AgentException busy = new AgentException("AI_BUSY", "繁忙", 429);
        when(chat.prompt().user(anyString()).call().content()).thenThrow(busy);
        assertSame(busy, assertThrows(AgentException.class, this::analyze));
        assertSame(busy, assertThrows(AgentException.class, () -> service.analyzeReflectionContent("内容")));
        noWrites();
    }

    @Test void controllerReturnsCommittedServiceDataWithoutASecondReadOrWrite() {
        InternshipReflectionService mocked = mock(InternshipReflectionService.class);
        when(mocked.analyzeReflection(11L)).thenReturn(Map.of("success", true, "data", Map.of("score", 85.5)));
        InternshipReflectionController controller = new InternshipReflectionController();
        ReflectionTestUtils.setField(controller, "internshipReflectionService", mocked);
        assertEquals(200, controller.analyzeInternshipReflection(11L).getCode());
        verify(mocked, never()).findById(any());
        verify(mocked, never()).updateById(any());
    }

    @Test void everyAdminAiRoutePreservesAdmissionRejection() throws Exception {
        InternshipReflectionService mocked = mock(InternshipReflectionService.class);
        AgentException busy = new AgentException("AI_BUSY", "繁忙", 429);
        when(mocked.analyzeReflection(any())).thenThrow(busy);
        when(mocked.analyzeReflectionContent(anyString())).thenThrow(busy);
        when(mocked.analyzeReflectionContent(anyString(), nullable(String.class))).thenThrow(busy);
        InternshipReflectionController controller = new InternshipReflectionController();
        FileParserUtil parser = mock(FileParserUtil.class);
        when(parser.isSupportedFile("reflection.txt")).thenReturn(true);
        MockMultipartFile file = new MockMultipartFile("file", "reflection.txt", "text/plain", "心得".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(parser.parseFile(file)).thenReturn("心得");
        ReflectionTestUtils.setField(controller, "internshipReflectionService", mocked);
        ReflectionTestUtils.setField(controller, "fileParserUtil", parser);
        assertSame(busy, assertThrows(AgentException.class, () -> controller.analyzeInternshipReflection(11L)));
        assertSame(busy, assertThrows(AgentException.class, () -> controller.analyzeReflectionContent(Map.of("content", "心得"))));
        assertSame(busy, assertThrows(AgentException.class, () -> controller.analyzeFile(file)));
        assertSame(busy, assertThrows(AgentException.class, () -> controller.uploadAndAnalyze(file, null, null)));
        verify(mocked, never()).insert(any());
    }

    @Test void improvementsAreBoundForInsertUpdateAndReadInActualMapperXml() throws Exception {
        String resource = "com/internship/mapper/InternshipReflectionMapper.xml";
        Configuration config = new Configuration();
        try (var input = getClass().getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(input);
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        current.setAiImprovements("[加强沟通]");
        String mapper = "com.internship.mapper.InternshipReflectionMapper.";
        for (String method : List.of("insert", "updateById")) {
            var sql = config.getMappedStatement(mapper + method).getBoundSql(current);
            assertTrue(sql.getSql().contains("ai_improvements"));
            assertEquals(1, sql.getParameterMappings().stream().filter(p -> p.getProperty().equals("aiImprovements")).count());
        }
        assertTrue(config.getResultMap(mapper + "InternshipReflectionResultMap").getResultMappings().stream()
                .anyMatch(mapping -> mapping.getProperty().equals("aiImprovements") && mapping.getColumn().equals("ai_improvements")));
    }

    private static CategoryWeight weight(String category, int weight) {
        CategoryWeight value = new CategoryWeight(); value.setCategoryCode(category); value.setWeight(weight); return value;
    }
    private static InternshipReflection copy(InternshipReflection reflection) {
        InternshipReflection copied = new InternshipReflection(); BeanUtils.copyProperties(reflection, copied); return copied;
    }
    private static final class TestTransactions extends AbstractPlatformTransactionManager {
        static final class Tx { boolean active; }
        private final ThreadLocal<Tx> state = ThreadLocal.withInitial(Tx::new);
        int commits, rollbacks;
        protected Object doGetTransaction() { return state.get(); }
        protected boolean isExistingTransaction(Object tx) { return ((Tx) tx).active; }
        protected void doBegin(Object tx, org.springframework.transaction.TransactionDefinition definition) { ((Tx) tx).active = true; }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        protected Object doSuspend(Object tx) { state.set(new Tx()); return tx; }
        protected void doResume(Object tx, Object suspended) { state.set((Tx) suspended); }
        protected void doCleanupAfterCompletion(Object tx) { ((Tx) tx).active = false; }
    }
}
