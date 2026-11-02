package com.internship.controller;

import com.internship.mapper.ProblemFeedbackMapper;
import com.github.pagehelper.PageHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemFeedbackWorkflowTest {
    private final ProblemFeedbackMapper feedbacks = mock(ProblemFeedbackMapper.class);
    private final ProblemFeedbackController controller = new ProblemFeedbackController();

    ProblemFeedbackWorkflowTest() {
        ReflectionTestUtils.setField(controller, "problemFeedbackMapper", feedbacks);
    }

    @AfterEach void clearPagination() { PageHelper.clearPage(); }

    @ParameterizedTest
    @CsvSource({"processing,processing", "pending,processing", "resolved,resolved", "closed,resolved"})
    void processingStateCanBeChangedWithoutSendingMail(String requested, String saved) {
        when(feedbacks.updateStatus(7L, saved)).thenReturn(1);
        assertEquals(200, controller.updateStatus(7L, Map.of("status", requested)).getCode());
        verify(feedbacks).updateStatus(7L, saved);
        verifyNoMoreInteractions(feedbacks);
    }

    @Test void invalidOrMissingStateCannotBeSavedAndMissingFeedbackDoesNotReportSuccess() {
        assertEquals(400, controller.updateStatus(7L, Map.of("status", "SENT")).getCode());
        assertEquals(400, controller.updateStatus(7L, Map.of()).getCode());
        verifyNoInteractions(feedbacks);
        assertEquals(400, controller.updateStatus(7L, Map.of("status", "resolved")).getCode());
    }

    @Test void listOnlyUsesActiveFiltersAndStateChangesRequireEditPermission() throws Exception {
        when(feedbacks.list("student", "processing", null, null, "姓名", "标题")).thenReturn(List.of());
        assertEquals(200, controller.getFeedbackByPage(1, 10, "student", "processing", "姓名", "标题").getCode());
        verify(feedbacks).list("student", "processing", null, null, "姓名", "标题");
        String permission = ProblemFeedbackController.class.getMethod("updateStatus", Long.class, Map.class)
                .getAnnotation(PreAuthorize.class).value();
        assertTrue(permission.contains("ROLE_ADMIN") && permission.contains("problem:edit"));
    }
}
