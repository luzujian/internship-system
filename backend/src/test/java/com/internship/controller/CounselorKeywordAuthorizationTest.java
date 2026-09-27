package com.internship.controller;

import com.internship.entity.CounselorKeyword;
import com.internship.entity.User;
import com.internship.service.CounselorKeywordService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CounselorKeywordAuthorizationTest {
    private final CounselorKeywordController controller = new CounselorKeywordController();
    private final CounselorKeywordService service = mock(CounselorKeywordService.class);

    CounselorKeywordAuthorizationTest() {
        ReflectionTestUtils.setField(controller, "counselorKeywordService", service);
    }

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
    }

    private void identify(String role, long id) {
        User user = new User();
        user.setRole(role);
        user.setId(id);
        CurrentHolder.setUser(user);
    }

    @Test
    void allRoutesRequireCounselorRoleEvenWhenAnotherTableHasSameNumericId() {
        assertEquals("hasRole('TEACHER_COUNSELOR')",
                CounselorKeywordController.class.getAnnotation(PreAuthorize.class).value());
        identify("ROLE_STUDENT", 7L);
        assertThrows(AccessDeniedException.class, () -> controller.getKeywordsByCounselorId(7L));
        assertThrows(AccessDeniedException.class, () -> controller.addKeyword(new CounselorKeyword()));
        verifyNoInteractions(service);
    }

    @Test
    void counselorCannotReadAnotherCounselorsKeywords() {
        identify("ROLE_TEACHER_COUNSELOR", 7L);
        assertThrows(AccessDeniedException.class, () -> controller.getKeywordsByCounselorId(8L));
        verifyNoInteractions(service);
    }

    @Test
    void creationBindsOwnershipToCurrentCounselor() {
        identify("ROLE_TEACHER_COUNSELOR", 7L);
        CounselorKeyword input = new CounselorKeyword();
        input.setCounselorId(8L);

        controller.addKeyword(input);

        assertEquals(7L, input.getCounselorId());
        verify(service).addKeyword(input);
    }

    @Test
    void updateAndDeleteRejectAnotherCounselorsKeywordBeforeWriting() {
        identify("ROLE_TEACHER_COUNSELOR", 7L);
        CounselorKeyword existing = new CounselorKeyword();
        existing.setCounselorId(8L);
        when(service.findById(21L)).thenReturn(existing);

        assertThrows(AccessDeniedException.class, () -> controller.updateKeyword(21L, new CounselorKeyword()));
        assertThrows(AccessDeniedException.class, () -> controller.deleteKeyword(21L));

        verify(service, never()).updateKeyword(any());
        verify(service, never()).deleteKeyword(any());
    }

    @Test
    void ownerCanUpdateAndDeleteWithoutReassigningTheRecord() {
        identify("ROLE_TEACHER_COUNSELOR", 7L);
        CounselorKeyword existing = new CounselorKeyword();
        existing.setCounselorId(7L);
        when(service.findById(21L)).thenReturn(existing);
        CounselorKeyword input = new CounselorKeyword();
        input.setCounselorId(8L);

        controller.updateKeyword(21L, input);
        controller.deleteKeyword(21L);

        assertEquals(21L, input.getId());
        assertEquals(7L, input.getCounselorId());
        verify(service).updateKeyword(input);
        verify(service).deleteKeyword(21L);
    }
}
