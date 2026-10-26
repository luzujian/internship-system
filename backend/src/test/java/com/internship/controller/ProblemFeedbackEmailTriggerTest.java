package com.internship.controller;

import com.internship.entity.ProblemFeedback;
import com.internship.entity.User;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.service.ProblemFeedbackSubmittedEvent;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemFeedbackEmailTriggerTest {
    private final ProblemFeedbackMapper mapper = mock(ProblemFeedbackMapper.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final AnnouncementWebSocketHandler socket = mock(AnnouncementWebSocketHandler.class);
    private final ProblemFeedbackController controller = new ProblemFeedbackController();

    ProblemFeedbackEmailTriggerTest() {
        ReflectionTestUtils.setField(controller, "problemFeedbackMapper", mapper);
        ReflectionTestUtils.setField(controller, "eventPublisher", publisher);
        ReflectionTestUtils.setField(controller, "webSocketHandler", socket);
        User actor = new User(); actor.setId(8L); actor.setRole("ROLE_STUDENT");
        actor.setName("真实提交者"); actor.setUsername("real-account"); CurrentHolder.setUser(actor);
    }
    @AfterEach void clear() { CurrentHolder.remove(); }

    private ProblemFeedback request() {
        ProblemFeedback feedback = new ProblemFeedback(); feedback.setTitle("测试问题反馈"); feedback.setContent("反馈正文");
        feedback.setId(999L); feedback.setUserId(999L); feedback.setUserType("company");
        return feedback;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STUDENT", "ROLE_COMPANY", "ROLE_TEACHER", "ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR"})
    void onlySuccessfulInsertPublishesPersistedIdentity(String role) {
        CurrentHolder.getUser().setRole(role);
        ProblemFeedback feedback = request();
        when(mapper.insert(any())).thenAnswer(call -> { ((ProblemFeedback) call.getArgument(0)).setId(12L); return 1; });
        assertEquals(200, controller.addFeedback(feedback).getCode());
        verify(publisher).publishEvent(new ProblemFeedbackSubmittedEvent(12L));
        String expectedType = role.startsWith("ROLE_TEACHER") ? "teacher" : role.equals("ROLE_COMPANY") ? "company" : "student";
        assertEquals(8L, feedback.getUserId()); assertEquals(expectedType, feedback.getUserType());
        assertEquals("真实提交者", feedback.getUserName());
        verify(socket).sendFeedbackToAdmin(any());
    }

    @Test void failedInsertAndUnavailableIdentityDoNotNotify() {
        when(mapper.insert(any())).thenReturn(0);
        controller.addFeedback(request());
        CurrentHolder.remove(); controller.addFeedback(request());
        verifyNoInteractions(publisher, socket);
    }

    @Test void mailTaskStartupFailureDoesNotUndoSuccessfulFeedback() {
        when(mapper.insert(any())).thenAnswer(call -> { ((ProblemFeedback) call.getArgument(0)).setId(12L); return 1; });
        doThrow(new IllegalStateException("executor unavailable")).when(publisher).publishEvent(any(ProblemFeedbackSubmittedEvent.class));
        assertEquals(200, controller.addFeedback(request()).getCode());
        verify(mapper).insert(any());
    }

    @Test void updatingExistingFeedbackDoesNotSendNewSubmissionMail() {
        ProblemFeedback existing = request(); existing.setUserId(8L); existing.setUserType("student");
        when(mapper.findById(12L)).thenReturn(existing);
        controller.updateFeedback(12L, request());
        verifyNoInteractions(publisher);
    }
}
