package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.ProblemFeedback;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
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

class ProblemFeedbackEmailRequirementTest {
    private final ProblemFeedbackMapper feedbacks = mock(ProblemFeedbackMapper.class);
    private final StudentUserMapper students = mock(StudentUserMapper.class);
    private final TeacherUserMapper teachers = mock(TeacherUserMapper.class);
    private final CompanyUserMapper companies = mock(CompanyUserMapper.class);
    private final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    private final AnnouncementWebSocketHandler socket = mock(AnnouncementWebSocketHandler.class);
    private final ProblemFeedbackController controller = new ProblemFeedbackController();

    ProblemFeedbackEmailRequirementTest() {
        ReflectionTestUtils.setField(controller, "problemFeedbackMapper", feedbacks);
        ReflectionTestUtils.setField(controller, "studentUserMapper", students);
        ReflectionTestUtils.setField(controller, "teacherUserMapper", teachers);
        ReflectionTestUtils.setField(controller, "companyUserMapper", companies);
        ReflectionTestUtils.setField(controller, "eventPublisher", publisher);
        ReflectionTestUtils.setField(controller, "webSocketHandler", socket);
    }

    @AfterEach void clear() { CurrentHolder.remove(); }

    private void login(String role) {
        User actor = new User(); actor.setId(7L); actor.setRole(role);
        actor.setName("真实提交者"); actor.setUsername("real-account");
        CurrentHolder.setUser(actor);
    }

    private void savedEmail(String role, String email) {
        if (role.startsWith("ROLE_TEACHER")) {
            TeacherUser teacher = new TeacherUser(); teacher.setEmail(email);
            when(teachers.findById(7L)).thenReturn(teacher);
        } else if ("ROLE_COMPANY".equals(role)) {
            CompanyUser company = new CompanyUser(); company.setEmail(email);
            company.setContactEmail("contact@example.com");
            when(companies.findById(7L)).thenReturn(company);
        } else {
            StudentUser student = new StudentUser(); student.setEmail(email);
            when(students.findById(7L)).thenReturn(student);
        }
    }

    private ProblemFeedback request() {
        ProblemFeedback feedback = new ProblemFeedback();
        feedback.setTitle("反馈标题"); feedback.setContent("反馈内容");
        feedback.setUserId(99L); feedback.setUserType("company");
        return feedback;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STUDENT", "ROLE_COMPANY", "ROLE_TEACHER", "ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR"})
    void absentProfileOrInvalidSavedEmailCannotCreateFeedbackOrNotify(String role) {
        login(role);
        assertEquals(400, controller.addFeedback(request()).getCode());
        for (String email : new String[]{null, "", "  ", "not-an-email", "name@localhost", "name@example.com\nBcc: other@example.com"}) {
            savedEmail(role, email);
            var result = controller.addFeedback(request());
            assertEquals(400, result.getCode());
            assertTrue(result.getMessage().contains("邮箱"));
        }
        verifyNoInteractions(feedbacks, publisher, socket);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_STUDENT", "ROLE_COMPANY", "ROLE_TEACHER", "ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR"})
    void validSavedEmailAllowsSubmissionAndUsesCurrentActor(String role) {
        login(role);
        savedEmail(role, " user+feedback@example.com ");
        when(feedbacks.insert(any())).thenReturn(1);
        ProblemFeedback feedback = request();
        assertEquals(200, controller.addFeedback(feedback).getCode());
        assertEquals(7L, feedback.getUserId());
        assertEquals("normal", feedback.getPriority());
        assertEquals("processing", feedback.getStatus());
        assertNull(feedback.getFeedbackType());
        verify(feedbacks).insert(feedback);
        if (role.startsWith("ROLE_TEACHER")) {
            verify(teachers).findById(7L);
            verifyNoInteractions(students, companies);
        } else if ("ROLE_COMPANY".equals(role)) {
            verify(companies).findById(7L);
            verifyNoInteractions(students, teachers);
        } else {
            verify(students).findById(7L);
            verifyNoInteractions(teachers, companies);
        }
    }

    @Test void everySubmissionChecksTheLatestSavedEmail() {
        login("ROLE_STUDENT");
        savedEmail("ROLE_STUDENT", "student@example.com");
        when(feedbacks.insert(any())).thenReturn(1);
        assertEquals(200, controller.addFeedback(request()).getCode());
        savedEmail("ROLE_STUDENT", null);
        assertEquals(400, controller.addFeedback(request()).getCode());
        verify(students, times(2)).findById(7L);
        verify(feedbacks, times(1)).insert(any());
        verify(publisher, times(1)).publishEvent(any(Object.class));
        verify(socket, times(1)).sendFeedbackToAdmin(any());
    }
}
