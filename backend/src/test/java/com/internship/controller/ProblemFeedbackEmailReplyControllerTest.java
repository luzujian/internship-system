package com.internship.controller;

import com.internship.entity.dto.ProblemFeedbackEmailReplyModels.*;
import com.internship.exception.BusinessException;
import com.internship.service.ProblemFeedbackEmailReplyService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemFeedbackEmailReplyControllerTest {
    private final ProblemFeedbackEmailReplyService replies = mock(ProblemFeedbackEmailReplyService.class);
    private final ProblemFeedbackController controller = new ProblemFeedbackController();

    ProblemFeedbackEmailReplyControllerTest() { ReflectionTestUtils.setField(controller, "emailReplies", replies); }

    @Test void deliveryResultRemainsStructuredAndOnlyServiceReceivesTheValidatedRequest() {
        Request request = new Request(UUID.randomUUID(), "user@example.com", "回复内容", "processing", 1000L, null);
        Receipt failed = new Receipt(12L, request.requestId().toString(), request.recipientEmail(), "邮件主题", request.adminReply(),
                "实际管理员", "processing", "FAILED", "无法连接邮件服务", null, null);
        when(replies.send(7L, request)).thenReturn(failed);
        var result = controller.replyFeedback(7L, request);
        assertEquals(200, result.getCode());
        assertSame(failed, result.getData());
        verify(replies).send(7L, request);
    }

    @Test void staleRecipientValidationIsReturnedWithoutExposingInternalErrors() {
        when(replies.context(7L)).thenThrow(new IllegalStateException("jdbc-private-configuration"));
        assertFalse(controller.getEmailReplyContext(7L).getMessage().contains("jdbc"));
        Request request = new Request(UUID.randomUUID(), "user@example.com", "回复", "processing", 1000L, null);
        when(replies.send(7L, request)).thenThrow(new BusinessException("收件邮箱已变更"));
        assertEquals("收件邮箱已变更", controller.replyFeedback(7L, request).getMessage());
    }

    @Test void recipientHistoryAndSendEndpointsRequireAdministratorPermissions() throws Exception {
        String view = ProblemFeedbackController.class.getMethod("getEmailReplyContext", Long.class).getAnnotation(PreAuthorize.class).value();
        String send = ProblemFeedbackController.class.getMethod("replyFeedback", Long.class, Request.class).getAnnotation(PreAuthorize.class).value();
        assertTrue(view.contains("ROLE_ADMIN") && view.contains("problem:view"));
        assertTrue(send.contains("ROLE_ADMIN") && send.contains("problem:edit"));
    }
}
