package com.internship.controller;

import com.internship.entity.InternshipReflection;
import com.internship.entity.Result;
import com.internship.entity.StudentReflectionAIAnalysis;
import com.internship.entity.User;
import com.internship.service.InternshipReflectionService;
import com.internship.service.StudentReflectionAIAnalysisService;
import com.internship.service.UserService;
import com.internship.task.ReflectionAiAnalysisDispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentReflectionAiStatusTest {
    private final StudentInternshipReflectionController controller = new StudentInternshipReflectionController();
    private final UserService users = mock(UserService.class);
    private final InternshipReflectionService reflections = mock(InternshipReflectionService.class);
    private final StudentReflectionAIAnalysisService analysis = mock(StudentReflectionAIAnalysisService.class);
    private final ReflectionAiAnalysisDispatcher dispatcher = mock(ReflectionAiAnalysisDispatcher.class);
    private InternshipReflection reflection;

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "internshipReflectionService", reflections);
        ReflectionTestUtils.setField(controller, "studentReflectionAIAnalysisService", analysis);
        ReflectionTestUtils.setField(controller, "reflectionAiDispatcher", dispatcher);
        var principal = org.springframework.security.core.userdetails.User.withUsername("s42").password("unused").roles("STUDENT").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        User user = new User(); user.setId(42L); when(users.findByUsername("s42")).thenReturn(user);
        reflection = new InternshipReflection(); reflection.setId(10L); reflection.setStudentId(42L); reflection.setStatus(0);
        when(reflections.findById(10L)).thenReturn(reflection);
    }

    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test
    void exhaustedAiAttemptsAreObservableWithoutRevertingTheSubmittedReflection() {
        when(dispatcher.failureCount(10L, 42L)).thenReturn(3);
        Map<?, ?> result = data();
        assertEquals("failed", result.get("status"));
        assertEquals(3, result.get("attempts"));
        assertEquals(false, result.get("retryable"));
        assertEquals(ReflectionAiAnalysisDispatcher.FAILURE_MESSAGE, result.get("message"));
        assertEquals(0, reflection.getStatus()); assertNull(reflection.getRemark());
        verify(reflections, never()).updateById(any());
    }

    @Test
    void successfulAnalysisAndHumanReviewedOrWithdrawnStatesTakePrecedence() {
        when(dispatcher.failureCount(10L, 42L)).thenReturn(3);
        when(analysis.findByReflectionId(10L)).thenReturn(new StudentReflectionAIAnalysis());
        assertEquals("analysed", data().get("status"));
        reflection.setRemark("1"); assertEquals("reviewed", data().get("status"));
        reflection.setRemark("0"); assertEquals("draft", data().get("status"));
    }

    @Test
    void pendingAttemptsRemainRetryableAndAnotherStudentsStatusIsRejected() {
        when(dispatcher.failureCount(10L, 42L)).thenReturn(2);
        assertEquals("pending", data().get("status"));
        assertEquals(true, data().get("retryable"));
        clearInvocations(dispatcher, analysis);
        reflection.setStudentId(43L);
        assertEquals(400, controller.getAIAnalysisStatus(10L).getCode());
        verifyNoInteractions(dispatcher, analysis);
    }

    private Map<?, ?> data() {
        Result result = controller.getAIAnalysisStatus(10L);
        assertEquals(200, result.getCode());
        return (Map<?, ?>) result.getData();
    }
}
