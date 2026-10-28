package com.internship.service;

import com.internship.entity.InternshipProgressRecord;
import com.internship.entity.dto.RecentActivityPageDTO;
import com.internship.entity.dto.RecentActivityRecordRow;
import com.internship.mapper.InternshipProgressRecordMapper;
import com.internship.service.impl.InternshipProgressRecordServiceImpl;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternshipProgressRecordServiceImplTest {

    @Mock
    private InternshipProgressRecordMapper mapper;

    @Mock
    private AnnouncementWebSocketHandler webSocketHandler;

    @InjectMocks
    private InternshipProgressRecordServiceImpl service;

    @org.junit.jupiter.api.BeforeEach
    void enableRetainedBusiness() { com.internship.support.ReflectionFeatureTestSupport.enable(service); }

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void recentActivitiesUseStableCursorAndCanonicalContract() {
        RecentActivityRecordRow directOffer = row(
                30L, "direct_hire", "success", new Date(3_000L), null, 500L);
        RecentActivityRecordRow confirmation = row(
                20L, "confirmation_submitted", "pending", new Date(2_000L), 2, 600L);
        RecentActivityRecordRow extra = row(
                10L, "reflection_scored", "success", new Date(1_000L), 1, 700L);
        when(mapper.selectRecentPage(eq(42L), isNull(), isNull(), eq(3), eq(true)))
                .thenReturn(List.of(directOffer, confirmation, extra));

        RecentActivityPageDTO page = service.getRecentActivities(42L, 2, null);

        assertTrue(page.hasMore());
        assertNotNull(page.nextCursor());
        assertEquals(2, page.items().size());
        assertEquals("direct_offer", page.items().get(0).eventType());
        assertEquals("获得 Offer", page.items().get(0).statusText());
        assertEquals("START_CONFIRMATION", page.items().get(0).action().key());
        assertNull(page.items().get(0).stageNo());
        assertEquals("confirmation", page.items().get(1).category());
        assertEquals("VIEW_CONFIRMATION", page.items().get(1).action().key());
        assertEquals("+08:00", page.items().get(0).occurredAt().getOffset().toString());

        when(mapper.selectRecentPage(eq(42L), eq(new Date(2_000L)), eq(20L), eq(3), eq(true)))
                .thenReturn(List.of(extra));
        RecentActivityPageDTO secondPage = service.getRecentActivities(42L, 2, page.nextCursor());

        assertFalse(secondPage.hasMore());
        assertNull(secondPage.nextCursor());
        assertEquals(10L, secondPage.items().get(0).id());
    }

    @Test
    void cursorKeepsIdTieBreakerWhenActivitiesShareTheSameTime() {
        Date sameTime = new Date(5_000L);
        RecentActivityRecordRow first = row(3L, "job_applied", "pending", sameTime, 1, 31L);
        RecentActivityRecordRow second = row(2L, "job_applied", "pending", sameTime, 1, 32L);
        RecentActivityRecordRow extra = row(1L, "job_applied", "pending", sameTime, 1, 33L);
        when(mapper.selectRecentPage(eq(42L), isNull(), isNull(), eq(3), eq(true)))
                .thenReturn(List.of(first, second, extra));

        RecentActivityPageDTO page = service.getRecentActivities(42L, 2, null);
        when(mapper.selectRecentPage(eq(42L), eq(sameTime), eq(2L), eq(3), eq(true)))
                .thenReturn(List.of(extra));

        RecentActivityPageDTO next = service.getRecentActivities(42L, 2, page.nextCursor());

        assertEquals(1L, next.items().get(0).id());
    }

    @Test
    void legacyMutableRowsAlwaysKeepTheirOriginalActionSemantics() {
        RecentActivityRecordRow legacyJob = row(
                1L, "job_application", "success", new Date(1_000L), null, 11L);
        legacyJob.setEventTitle("投递申请");
        RecentActivityRecordRow legacyInterview = row(
                2L, "interview", "failed", new Date(2_000L), null, 12L);
        RecentActivityRecordRow legacyConfirmation = row(
                3L, "internship_confirmation", "success", new Date(3_000L), 1, 13L);
        RecentActivityRecordRow legacyReflection = row(
                4L, "reflection_submit", "success", new Date(4_000L), 1, 14L);
        when(mapper.selectRecentPage(eq(42L), isNull(), isNull(), eq(5), eq(true)))
                .thenReturn(List.of(legacyReflection, legacyConfirmation, legacyInterview, legacyJob));

        RecentActivityPageDTO page = service.getRecentActivities(42L, 4, null);

        // reflection_submit + rawStatus="success" → 信任数据库，映射为 info（reflection 类事件）
        assertEquals("reflection_submitted", page.items().get(0).eventType());
        assertEquals("info", page.items().get(0).status());
        // confirmation_submitted + rawStatus="success" → 信任数据库，显示"已确认"
        assertEquals("confirmation_submitted", page.items().get(1).eventType());
        assertEquals("success", page.items().get(1).status());
        assertEquals("已确认", page.items().get(1).statusText());
        // interview_invited + rawStatus="failed" → 信任数据库，显示"已结束"
        assertEquals("interview_invited", page.items().get(2).eventType());
        assertEquals("failed", page.items().get(2).status());
        assertEquals("已结束", page.items().get(2).statusText());
        // job_applied + rawStatus="success" → 信任数据库，显示"已处理"
        assertEquals("job_applied", page.items().get(3).eventType());
        assertEquals("success", page.items().get(3).status());
        assertEquals("已处理", page.items().get(3).statusText());
    }

    @Test
    void unknownEventIsNeutralAndNotClickable() {
        RecentActivityRecordRow unknown = row(
                1L, "future_event", "warning", new Date(1_000L), null, 99L);
        unknown.setEventTitle("未来事件");
        when(mapper.selectRecentPage(eq(42L), isNull(), isNull(), eq(11), eq(true)))
                .thenReturn(List.of(unknown));

        RecentActivityPageDTO page = service.getRecentActivities(42L, 10, null);

        assertEquals("future_event", page.items().get(0).eventType());
        assertEquals("info", page.items().get(0).status());
        assertEquals("info", page.items().get(0).category());
        assertEquals("未来事件", page.items().get(0).title());
        assertNull(page.items().get(0).action());
    }

    @Test
    void canonicalEventWithoutItsDeletedSourceRemainsNonClickable() {
        RecentActivityRecordRow rejected = row(
                1L, "job_rejected", "failed", new Date(1_000L), null, 99L);
        rejected.setTargetExists(false);
        when(mapper.selectRecentPage(eq(42L), isNull(), isNull(), eq(11), eq(true)))
                .thenReturn(List.of(rejected));

        RecentActivityPageDTO page = service.getRecentActivities(42L, 10, null);

        assertEquals("job_rejected", page.items().get(0).eventType());
        assertNull(page.items().get(0).stageNo());
        assertNull(page.items().get(0).action());
    }

    @Test
    void unitChangeActionTargetsTheConfirmationWorkflow() {
        RecentActivityRecordRow unitChange = row(
                1L, "unit_change_rejected", "failed", new Date(1_000L), null, 88L);
        when(mapper.selectRecentPage(eq(42L), isNull(), isNull(), eq(11), eq(true)))
                .thenReturn(List.of(unitChange));

        RecentActivityPageDTO page = service.getRecentActivities(42L, 10, null);

        assertEquals("MANAGE_UNIT_CHANGE", page.items().get(0).action().key());
        assertEquals(88L, page.items().get(0).action().targetId());
    }

    @Test
    void rejectsInvalidLimitAndCursorBeforeQuerying() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getRecentActivities(42L, 0, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.getRecentActivities(42L, 21, null));
        assertThrows(IllegalArgumentException.class,
                () -> service.getRecentActivities(42L, 10, "not-a-valid-cursor"));

        verify(mapper, never()).selectRecentPage(eq(42L), isNull(), isNull(), eq(11), eq(true));
    }

    @Test
    void saveRecordNotifiesOnlyAfterActiveTransactionCommits() {
        doAnswer(invocation -> {
            InternshipProgressRecord record = invocation.getArgument(0);
            record.setId(123L);
            return 1;
        }).when(mapper).insert(org.mockito.ArgumentMatchers.any(InternshipProgressRecord.class));
        TransactionSynchronizationManager.initSynchronization();

        InternshipProgressRecord record = new InternshipProgressRecord();
        record.setStudentId(42L);
        record.setEventType("job_applied");
        service.saveRecord(record);

        verify(webSocketHandler, never()).sendStudentRecentActivityChanged(42L, 123L);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        assertEquals(1, synchronizations.size());
        synchronizations.forEach(TransactionSynchronization::afterCommit);
        verify(webSocketHandler).sendStudentRecentActivityChanged(42L, 123L);
        assertNotNull(record.getEventTime());
        assertNotNull(record.getCreateTime());
    }

    @Test
    void saveRecordNotifiesImmediatelyWithoutTransactionSynchronization() {
        doAnswer(invocation -> {
            InternshipProgressRecord record = invocation.getArgument(0);
            record.setId(321L);
            return 1;
        }).when(mapper).insert(org.mockito.ArgumentMatchers.any(InternshipProgressRecord.class));

        InternshipProgressRecord record = new InternshipProgressRecord();
        record.setStudentId(42L);
        record.setEventType("job_applied");
        service.saveRecord(record);

        verify(webSocketHandler).sendStudentRecentActivityChanged(42L, 321L);
    }

    private RecentActivityRecordRow row(Long id,
                                        String eventType,
                                        String status,
                                        Date occurredAt,
                                        Integer stageNo,
                                        Long relatedId) {
        RecentActivityRecordRow row = new RecentActivityRecordRow();
        row.setId(id);
        row.setStudentId(42L);
        row.setEventType(eventType);
        row.setEventTitle("原始标题");
        row.setDescription("测试企业 · 测试岗位");
        row.setStatus(status);
        row.setRelatedId(relatedId);
        row.setOccurredAt(occurredAt);
        row.setStageNo(stageNo);
        row.setTargetExists(true);
        return row;
    }
}
