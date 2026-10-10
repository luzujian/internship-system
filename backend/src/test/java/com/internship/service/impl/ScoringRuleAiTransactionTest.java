package com.internship.service.impl;

import com.internship.agent.business.AgentException;
import com.internship.entity.CounselorCategoryWeight;
import com.internship.entity.CounselorScoringRule;
import com.internship.entity.ScoringRule;
import com.internship.exception.BusinessException;
import com.internship.mapper.CategoryWeightMapper;
import com.internship.mapper.CounselorCategoryWeightMapper;
import com.internship.mapper.CounselorScoringRuleMapper;
import com.internship.mapper.ScoringRuleMapper;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.EnhancedDeepSeekService;
import com.internship.service.ScoringRuleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual Spring transaction boundaries, with mocked provider and database access. */
class ScoringRuleAiTransactionTest {
    private final EnhancedDeepSeekService ai = mock(EnhancedDeepSeekService.class);
    private final CounselorScoringRuleMapper counselorRules = mock(CounselorScoringRuleMapper.class);
    private final CounselorCategoryWeightMapper counselorWeights = mock(CounselorCategoryWeightMapper.class);
    private final ScoringRuleMapper rules = mock(ScoringRuleMapper.class);
    private final CategoryWeightMapper weights = mock(CategoryWeightMapper.class);
    private final TestTransactions transactions = new TestTransactions();
    private final TransactionTemplate writes = new TransactionTemplate(transactions);
    private CounselorAISettingsService counselor;
    private ScoringRuleService administrator;

    @BeforeEach
    void setUp() {
        CounselorAISettingsServiceImpl counselorTarget = new CounselorAISettingsServiceImpl();
        ReflectionTestUtils.setField(counselorTarget, "counselorScoringRuleMapper", counselorRules);
        ReflectionTestUtils.setField(counselorTarget, "counselorCategoryWeightMapper", counselorWeights);
        ReflectionTestUtils.setField(counselorTarget, "enhancedDeepSeekService", ai);
        ReflectionTestUtils.setField(counselorTarget, "transactionTemplate", writes);
        counselor = (CounselorAISettingsService) proxy(counselorTarget);
        ScoringRuleServiceImpl adminTarget = new ScoringRuleServiceImpl(rules, weights);
        ReflectionTestUtils.setField(adminTarget, "enhancedDeepSeekService", ai);
        ReflectionTestUtils.setField(adminTarget, "transactionTemplate", writes);
        administrator = (ScoringRuleService) proxy(adminTarget);
        when(ai.chatWithResources(anyString(), eq(false))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive(), "provider calls must not hold a DB transaction");
            return "  清晰完整地完成工作，主动协作。  ";
        });
        when(counselorRules.batchInsert(anyList())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return ((List<?>) invocation.getArgument(0)).size();
        });
        when(rules.batchInsert(anyList())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return ((List<?>) invocation.getArgument(0)).size();
        });
        when(rules.insert(any())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return 1;
        });
    }

    @Test
    void counselorGeneratesAllFiveOutsideTransactionThenSavesRulesAndWeightsTogether() {
        when(counselorRules.findCategoriesByCounselorId(7L)).thenReturn(List.of("custom_category"));
        when(counselorWeights.batchInsert(anyList())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            List<CounselorCategoryWeight> saved = invocation.getArgument(0);
            assertEquals("custom_category", saved.get(0).getCategoryName());
            assertEquals(100, saved.get(0).getWeight());
            return 1;
        });
        assertEquals(5, counselor.batchCreateWithAIDescription(7L, "Custom Category"));
        verify(ai, times(5)).chatWithResources(anyString(), eq(false));
        verify(counselorRules).batchInsert(argThat(saved -> saved.size() == 5
                && saved.stream().allMatch(rule -> "清晰完整地完成工作，主动协作。".equals(rule.getDescription()))));
        verify(counselorWeights).deleteByCounselorId(7L);
        assertEquals(1, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test
    void counselorProviderFailureDoesNotWritePartialRulesOrFallbackDescriptions() {
        AgentException busy = new AgentException("AI_BUSY", "AI繁忙", 429);
        AtomicInteger calls = new AtomicInteger();
        when(ai.chatWithResources(anyString(), eq(false))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            if (calls.incrementAndGet() == 3) throw busy;
            return "合法描述";
        });
        assertSame(busy, assertThrows(AgentException.class, () -> counselor.batchCreateWithAIDescription(7L, "能力")));
        verifyNoInteractions(counselorRules, counselorWeights);
        assertEquals(0, transactions.commits);
        assertEquals(0, transactions.rollbacks);
    }

    @Test
    void counselorBlankGenerationDoesNotPersistSuccessfulFallback() {
        when(ai.chatWithResources(anyString(), eq(false))).thenReturn(" ");
        assertThrows(BusinessException.class, () -> counselor.batchCreateWithAIDescription(7L, "能力"));
        verifyNoInteractions(counselorRules, counselorWeights);
    }

    @Test
    void counselorWeightFailureRollsBackTheShortSaveTransaction() {
        when(counselorRules.findCategoriesByCounselorId(7L)).thenReturn(List.of("ability"));
        when(counselorWeights.batchInsert(anyList())).thenThrow(new IllegalStateException("write failed"));
        assertThrows(IllegalStateException.class, () -> counselor.batchCreateWithAIDescription(7L, "能力"));
        verify(counselorRules).batchInsert(anyList());
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
    }

    @Test
    void existingCallerTransactionIsSuspendedDuringProviderCalls() {
        writes.execute(status -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            assertEquals(5, counselor.batchCreateWithAIDescription(7L, "能力"));
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive(), "caller transaction must resume");
            return null;
        });
        assertEquals(2, transactions.commits);
    }

    @Test
    void administratorCategoryBatchAlsoUsesOneShortSaveTransaction() {
        when(rules.findCategories()).thenReturn(List.of("custom_category"));
        when(weights.batchInsert(anyList())).thenAnswer(invocation -> {
            assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
            return 1;
        });
        assertEquals(5, administrator.batchCreateWithAIDescription("Custom Category"));
        verify(ai, times(5)).chatWithResources(anyString(), eq(false));
        verify(rules).batchInsert(anyList());
        verify(weights).batchInsert(anyList());
        assertEquals(1, transactions.commits);
    }

    @Test
    void administratorArbitraryBatchGeneratesBeforeAnyInserts() {
        when(ai.chatWithResources(anyString(), eq(false))).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            verify(rules, never()).insert(any());
            return "工作积极主动。";
        });
        assertEquals(2, administrator.batchInsert(List.of(rule("优秀"), rule("良好"))));
        verify(rules, times(2)).insert(any());
        assertEquals(1, transactions.commits);
    }

    @Test
    void administratorGenerationFailureLeavesBothBatchEntrypointsUnwritten() {
        when(ai.chatWithResources(anyString(), eq(false))).thenReturn("合法描述").thenThrow(new BusinessException("provider failed"));
        assertThrows(BusinessException.class, () -> administrator.batchInsert(List.of(rule("优秀"), rule("良好"))));
        assertThrows(BusinessException.class, () -> administrator.batchCreateWithAIDescription("能力"));
        verifyNoInteractions(rules, weights);
        assertEquals(0, transactions.commits);
    }

    @Test
    void administratorSecondInsertFailureRollsBackWholeBatch() {
        doReturn(1).doThrow(new IllegalStateException("write failed")).when(rules).insert(any());
        assertThrows(IllegalStateException.class, () -> administrator.batchInsert(List.of(rule("优秀"), rule("良好"))));
        assertEquals(0, transactions.commits);
        assertEquals(1, transactions.rollbacks);
    }

    @Test
    void emptyOrInvalidRequestsFailBeforeProviderCalls() {
        assertThrows(BusinessException.class, () -> administrator.batchInsert(null));
        assertThrows(BusinessException.class, () -> administrator.batchInsert(List.of()));
        assertThrows(BusinessException.class, () -> counselor.batchCreateWithAIDescription(0L, "能力"));
        assertThrows(BusinessException.class, () -> counselor.batchCreateWithAIDescription(7L, " "));
        verifyNoInteractions(ai, rules, weights, counselorRules, counselorWeights);
    }

    private Object proxy(Object target) {
        ProxyFactory proxy = new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(transactions, new AnnotationTransactionAttributeSource()));
        return proxy.getProxy();
    }

    private ScoringRule rule(String name) {
        ScoringRule rule = new ScoringRule();
        rule.setRuleName(name);
        rule.setCategory("professionalism");
        rule.setMinScore(80);
        rule.setMaxScore(100);
        return rule;
    }

    private static final class TestTransactions extends AbstractPlatformTransactionManager {
        static final class Tx { boolean active; }
        private final ThreadLocal<Tx> state = ThreadLocal.withInitial(Tx::new);
        int commits, rollbacks;
        protected Object doGetTransaction() { return state.get(); }
        protected boolean isExistingTransaction(Object tx) { return ((Tx) tx).active; }
        protected void doBegin(Object tx, TransactionDefinition def) { ((Tx) tx).active = true; }
        protected void doCommit(DefaultTransactionStatus status) { commits++; }
        protected void doRollback(DefaultTransactionStatus status) { rollbacks++; }
        protected Object doSuspend(Object tx) { state.set(new Tx()); return tx; }
        protected void doResume(Object tx, Object suspended) { state.set((Tx) suspended); }
        protected void doCleanupAfterCompletion(Object tx) { ((Tx) tx).active = false; }
    }
}
