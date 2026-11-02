package com.internship.service;

import com.internship.entity.*;
import com.internship.entity.dto.ProblemFeedbackEmailReplyModels.*;
import com.internship.exception.BusinessException;
import com.internship.mapper.ProblemFeedbackEmailReplyMapper;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ProblemFeedbackEmailReplyServiceTest {
    private final ProblemFeedbackMapper feedbacks = mock(ProblemFeedbackMapper.class);
    private final ProblemFeedbackEmailReplyMapper replies = mock(ProblemFeedbackEmailReplyMapper.class);
    private final EmailService email = mock(EmailService.class);
    private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
    private final ProblemFeedbackEmailReplyService service = new ProblemFeedbackEmailReplyService(
            feedbacks, replies, email, transactions, "system@deepintern.example");
    private final ProblemFeedback feedback = new ProblemFeedback();

    @BeforeEach void setup() {
        User admin = new User(); admin.setId(3L); admin.setRole("ROLE_ADMIN");
        admin.setName("真实管理员"); admin.setUsername("admin-account"); CurrentHolder.setUser(admin);
        feedback.setId(7L); feedback.setUserType("student"); feedback.setUserId(99L);
        feedback.setUserName("测试用户"); feedback.setTitle("问题反馈"); feedback.setContent("反馈内容");
        feedback.setStatus("processing"); feedback.setUpdateTime(new Date(1000));
        when(transactions.getTransaction(any())).thenAnswer(call -> new SimpleTransactionStatus());
        when(feedbacks.findById(7L)).thenReturn(feedback);
        when(feedbacks.findByIdForUpdate(7L)).thenReturn(feedback);
        when(feedbacks.findRecipientEmail(7L)).thenReturn("student@example.com");
        when(feedbacks.reply(anyLong(), anyString(), anyLong(), anyString(), anyString())).thenReturn(1);
        when(replies.findHistory(7L)).thenReturn(List.of());
        when(replies.insert(any())).thenAnswer(call -> { call.getArgument(0, ProblemFeedbackEmailReply.class).setId(41L); return 1; });
        when(replies.finish(anyLong(), anyString(), anyString(), any())).thenReturn(1);
        when(email.isConfigured()).thenReturn(true);
        when(email.sendWithReceipt(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new EmailService.DeliveryOutcome("SENT", "accepted", false));
    }

    @AfterEach void clear() { CurrentHolder.remove(); }

    private Request request(Long previous) {
        return new Request(UUID.randomUUID(), "student@example.com", "  已核实，请联系企业跟进。  ", "processing", 1000L, previous);
    }

    private ProblemFeedbackEmailReply old(String status) {
        ProblemFeedbackEmailReply row = new ProblemFeedbackEmailReply();
        row.setId(40L); row.setFeedbackId(7L); row.setRequestId(UUID.randomUUID().toString());
        row.setRecipientEmail("student@example.com"); row.setReplyContent("已核实，请联系企业跟进。");
        row.setFeedbackStatus("processing"); row.setDeliveryStatus(status);
        row.setCreatedAt(new Date()); row.setAdminName("原管理员");
        return row;
    }

    @Test void contextReadsCurrentAccountEmailAndRetainsDeliveryRecords() {
        when(replies.findHistory(7L)).thenReturn(List.of(old("SENT")));
        Context context = service.context(7L);
        assertEquals("student@example.com", context.recipientEmail());
        assertEquals("DeepIntern管理员", context.senderName());
        assertEquals("system@deepintern.example", context.senderEmail());
        assertEquals(1000L, context.feedbackVersion());
        assertEquals("SENT", context.history().getFirst().deliveryStatus());
        verifyNoInteractions(transactions);
    }

    @Test void claimCommitsBeforeSmtpAndSenderAndOperatorAreBoundByServer() {
        Request request = request(null);
        Receipt receipt = service.send(7L, request);
        assertEquals("SENT", receipt.deliveryStatus());
        assertEquals("真实管理员", receipt.adminName());
        assertEquals("已核实，请联系企业跟进。", receipt.replyContent());
        var order = inOrder(replies, feedbacks, transactions, email);
        order.verify(replies).insert(any());
        order.verify(feedbacks).reply(7L, receipt.replyContent(), 3L, "真实管理员", "processing");
        order.verify(transactions).commit(any());
        order.verify(email).sendWithReceipt(eq("student@example.com"), contains("问题反馈回复 #7"),
                contains("\n\nDeepIntern管理员\n"), contains(request.requestId().toString()), eq("DeepIntern管理员"));
        order.verify(replies).finish(eq(41L), eq("SENT"), anyString(), any());
        order.verify(transactions).commit(any());
    }

    @ParameterizedTest @ValueSource(strings = {"FAILED", "UNKNOWN"})
    void providerFailureAndUncertainResultsAreRecordedAsSuch(String state) {
        when(email.sendWithReceipt(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new EmailService.DeliveryOutcome(state, "邮件账号认证失败", false));
        Receipt receipt = service.send(7L, request(null));
        assertEquals(state, receipt.deliveryStatus());
        verify(replies).finish(eq(41L), eq(state), anyString(), any());
        assertNotNull(receipt.finishedAt());
    }

    @Test void missingOrUnexpectedProviderReceiptIsUncertain() {
        when(email.sendWithReceipt(anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(null);
        assertEquals("UNKNOWN", service.send(7L, request(null)).deliveryStatus());
    }

    @Test void unexpectedTransportExceptionNeverBecomesSuccess() {
        when(email.sendWithReceipt(anyString(), anyString(), anyString(), anyString(), anyString())).thenThrow(new IllegalStateException("timeout"));
        assertEquals("UNKNOWN", service.send(7L, request(null)).deliveryStatus());
    }

    @Test void sameRequestIdReturnsSavedReceiptWithoutResending() {
        Request request = request(null);
        ProblemFeedbackEmailReply row = old("SENT"); row.setRequestId(request.requestId().toString());
        when(replies.findByRequestId(request.requestId().toString())).thenReturn(row);
        when(feedbacks.findRecipientEmail(7L)).thenReturn("changed@example.com");
        assertEquals("SENT", service.send(7L, request).deliveryStatus());
        verifyNoInteractions(email);
        verify(replies, never()).insert(any());
    }

    @Test void requestIdCannotBeReusedForDifferentContent() {
        Request request = request(null);
        ProblemFeedbackEmailReply row = old("SENT"); row.setReplyContent("其他内容");
        when(replies.findByRequestId(request.requestId().toString())).thenReturn(row);
        assertThrows(BusinessException.class, () -> service.send(7L, request));
        verifyNoInteractions(email);
    }

    @ParameterizedTest @ValueSource(strings = {"SENDING", "UNKNOWN"})
    void unresolvedAttemptBlocksNewRequests(String state) {
        when(replies.findHistory(7L)).thenReturn(List.of(old(state)));
        assertThrows(BusinessException.class, () -> service.send(7L, request(40L)));
        verify(replies, never()).insert(any());
        verifyNoInteractions(email);
    }

    @Test void interruptedSendIsDisplayedAsUncertainAndStillBlocksRetries() {
        ProblemFeedbackEmailReply row = old("SENDING"); row.setCreatedAt(new Date(System.currentTimeMillis() - 180_000));
        when(replies.findHistory(7L)).thenReturn(List.of(row));
        assertEquals("UNKNOWN", service.context(7L).history().getFirst().deliveryStatus());
        assertThrows(BusinessException.class, () -> service.send(7L, request(40L)));
        verify(replies, never()).finish(anyLong(), anyString(), anyString(), any());
    }

    @Test void knownFailureCanBeRetriedWithANewRequestAndKeepsOldRecord() {
        ProblemFeedbackEmailReply failed = old("FAILED");
        when(replies.findHistory(7L)).thenReturn(List.of(failed));
        assertEquals("SENT", service.send(7L, request(40L)).deliveryStatus());
        assertEquals("FAILED", failed.getDeliveryStatus());
        verify(replies).insert(any());
    }

    @Test void completedSameContentCannotBeSentAgainButNewContentCan() {
        when(replies.findHistory(7L)).thenReturn(List.of(old("SENT")));
        assertThrows(BusinessException.class, () -> service.send(7L, request(40L)));
        Request next = new Request(UUID.randomUUID(), "student@example.com", "有新的处理进展。", "resolved", 1000L, 40L);
        assertEquals("SENT", service.send(7L, next).deliveryStatus());
        verify(email, times(1)).sendWithReceipt(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test void concurrentTabWithStaleHistoryCannotStartAnotherSend() {
        when(replies.findHistory(7L)).thenReturn(List.of(old("FAILED")));
        assertThrows(BusinessException.class, () -> service.send(7L, request(null)));
        verifyNoInteractions(email);
    }

    @ParameterizedTest @ValueSource(strings = {"", "not-an-email", "changed@example.com"})
    void missingInvalidOrChangedRecipientNeverSends(String address) {
        when(feedbacks.findRecipientEmail(7L)).thenReturn(address);
        assertThrows(BusinessException.class, () -> service.send(7L, request(null)));
        verify(replies, never()).insert(any());
        verifyNoInteractions(email);
    }

    @Test void changedOrDeletedFeedbackCannotSend() {
        feedback.setUpdateTime(new Date(2000));
        assertThrows(BusinessException.class, () -> service.send(7L, request(null)));
        feedback.setDeleted(1);
        assertThrows(BusinessException.class, () -> service.send(7L, request(null)));
        verifyNoInteractions(email);
    }

    @Test void invalidTextAndStatusCannotSend() {
        assertThrows(BusinessException.class, () -> service.send(7L, new Request(UUID.randomUUID(), "student@example.com", "  ", "resolved", 1000L, null)));
        assertThrows(BusinessException.class, () -> service.send(7L, new Request(UUID.randomUUID(), "student@example.com", "内容", "forged", 1000L, null)));
        assertThrows(BusinessException.class, () -> service.send(7L, new Request(UUID.randomUUID(), "student@example.com", "内容", "closed", 1000L, null)));
        verifyNoInteractions(feedbacks, replies, email, transactions);
    }

    @Test void unsuccessfulClaimRollsBackBeforeSmtp() {
        when(feedbacks.reply(anyLong(), anyString(), anyLong(), anyString(), anyString())).thenReturn(0);
        assertThrows(BusinessException.class, () -> service.send(7L, request(null)));
        verify(transactions).rollback(any());
        verifyNoInteractions(email);
    }

    @Test void failureToSaveFinalReceiptIsReportedAsUncertainAndNeverRetriesTransport() {
        when(replies.finish(anyLong(), anyString(), anyString(), any())).thenThrow(new IllegalStateException("database unavailable"));
        assertEquals("UNKNOWN", service.send(7L, request(null)).deliveryStatus());
        verify(email, times(1)).sendWithReceipt(anyString(), anyString(), anyString(), anyString(), anyString());
        verify(transactions).rollback(any());
    }

    @Test void nonAdminCannotReadRecipientsOrSend() {
        CurrentHolder.getUser().setRole("ROLE_STUDENT");
        assertThrows(AccessDeniedException.class, () -> service.context(7L));
        assertThrows(AccessDeniedException.class, () -> service.send(7L, request(null)));
        verifyNoInteractions(feedbacks, replies, email, transactions);
    }
}
