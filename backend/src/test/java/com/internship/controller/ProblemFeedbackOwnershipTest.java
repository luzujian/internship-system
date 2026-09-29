package com.internship.controller;

import com.internship.entity.ProblemFeedback;
import com.internship.entity.User;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProblemFeedbackOwnershipTest {
    private final ProblemFeedbackMapper mapper = mock(ProblemFeedbackMapper.class);
    private final ProblemFeedbackController controller = new ProblemFeedbackController();

    ProblemFeedbackOwnershipTest() {
        ReflectionTestUtils.setField(controller, "problemFeedbackMapper", mapper);
        ReflectionTestUtils.setField(controller, "webSocketHandler", mock(AnnouncementWebSocketHandler.class));
    }

    @AfterEach void clear() { CurrentHolder.remove(); SecurityContextHolder.clearContext(); }

    private void login(String role, long id) {
        User user = new User();
        user.setId(id); user.setRole(role); user.setName("真实用户"); user.setUsername("real-user");
        CurrentHolder.setUser(user);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "real-user", "unused", List.of(new SimpleGrantedAuthority(role))));
    }

    private ProblemFeedback row(String type, long userId) {
        ProblemFeedback row = new ProblemFeedback(); row.setId(10L);
        row.setUserType(type); row.setUserId(userId); row.setDeleted(0);
        return row;
    }

    @Test void sameNumericIdAcrossRolesCannotReadOrEdit() {
        login("ROLE_STUDENT", 7);
        when(mapper.findById(10L)).thenReturn(row("company", 7));
        assertEquals(400, controller.getFeedbackById(10L, SecurityContextHolder.getContext().getAuthentication()).getCode());
        assertEquals(400, controller.updateFeedback(10L, new ProblemFeedback()).getCode());
        verify(mapper, never()).update(any());
    }

    @Test void absentPrincipalCannotRead() {
        when(mapper.findById(10L)).thenReturn(row("student", 7));
        assertEquals(400, controller.getFeedbackById(10L, null).getCode());
    }

    @Test void creationBindsIdentityAndUpdateCannotForgeAdminFields() {
        login("ROLE_STUDENT", 7);
        ProblemFeedback request = row("company", 99);
        request.setUserName("伪造"); request.setUserAccount("forged");
        request.setTitle("合法反馈"); request.setContent("内容");
        when(mapper.insert(any())).thenReturn(1);
        assertEquals(200, controller.addFeedback(request).getCode());
        assertEquals("student", request.getUserType()); assertEquals(7L, request.getUserId());
        assertEquals("真实用户", request.getUserName()); assertEquals("real-user", request.getUserAccount());
        assertEquals("low", request.getPriority());
        when(mapper.findById(10L)).thenReturn(row("student", 7));
        request.setStatus("resolved"); request.setPriority("high"); request.setAdminReply("伪造回复");
        request.setAdminId(1L); request.setAdminName("管理员");
        request.setReplyTime(new java.util.Date()); request.setResolveTime(new java.util.Date());
        controller.updateFeedback(10L, request);
        var captor = org.mockito.ArgumentCaptor.forClass(ProblemFeedback.class);
        verify(mapper).update(captor.capture());
        ProblemFeedback saved = captor.getValue();
        assertEquals("合法反馈", saved.getTitle()); assertEquals("内容", saved.getContent());
        assertNull(saved.getStatus()); assertNull(saved.getPriority()); assertNull(saved.getAdminReply());
        assertNull(saved.getAdminId()); assertNull(saved.getAdminName());
        assertNull(saved.getReplyTime()); assertNull(saved.getResolveTime());
    }

    @Test void administratorRetainsManagementAccess() {
        login("ROLE_ADMIN", 1);
        when(mapper.findById(10L)).thenReturn(row("student", 7));
        assertEquals(200, controller.getFeedbackById(10L, SecurityContextHolder.getContext().getAuthentication()).getCode());
        ProblemFeedback edit = new ProblemFeedback(); edit.setStatus("resolved");
        assertEquals(200, controller.updateFeedback(10L, edit).getCode());
        verify(mapper).update(edit);
    }

    @Test void myListIgnoresForgedAndEmptyQueryIdentity() {
        login("ROLE_TEACHER_COUNSELOR", 7);
        when(mapper.findByOwner("teacher", 7L)).thenReturn(List.of(row("teacher", 7)));
        assertEquals(200, controller.getMyFeedback("", 999L).getCode());
        assertEquals(200, controller.getMyFeedback("company", 99L).getCode());
        verify(mapper, times(2)).findByOwner("teacher", 7L);
        verify(mapper, never()).list(any(), any(), any(), any(), any(), any());
    }
}
