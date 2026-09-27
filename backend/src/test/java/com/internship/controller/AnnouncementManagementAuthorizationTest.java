package com.internship.controller;

import com.internship.entity.Announcement;
import com.internship.entity.User;
import com.internship.service.AnnouncementService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementManagementAuthorizationTest {
    @InjectMocks private AnnouncementController controller;
    @Mock private AnnouncementService announcementService;
    @Mock private AnnouncementWebSocketHandler webSocketHandler;

    @AfterEach
    void clearUser() {
        CurrentHolder.remove();
    }

    private void user(long id, String name, String role) {
        User user = new User();
        user.setId(id);
        user.setName(name);
        user.setRole(role);
        CurrentHolder.setUser(user);
    }

    private Announcement announcement(long id, String publisher, String publisherRole) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle("公告" + id);
        announcement.setContent("内容");
        announcement.setPublisher(publisher);
        announcement.setPublisherRole(publisherRole);
        announcement.setStatus("PUBLISHED");
        announcement.setTargetType("[\"STUDENT\"]");
        return announcement;
    }

    @Test
    void teacherCanEditOnlyOwnAnnouncement() {
        Announcement own = announcement(1L, "张老师", "COLLEGE");
        Announcement changed = announcement(1L, "伪造发布人", "ADMIN");
        user(9L, "张老师", "ROLE_TEACHER_COLLEGE");
        when(announcementService.findById(1L)).thenReturn(own);
        when(announcementService.update(changed)).thenReturn(1);

        assertEquals(200, controller.updateAnnouncement(1L, changed).getCode());
        assertEquals("张老师", changed.getPublisher());
        assertEquals("COLLEGE", changed.getPublisherRole());

        Announcement other = announcement(2L, "李老师", "DEPARTMENT");
        Announcement changedForOther = announcement(2L, "伪造发布人", "ADMIN");
        when(announcementService.findById(2L)).thenReturn(other);
        assertThrows(AccessDeniedException.class, () -> controller.updateAnnouncement(2L, changedForOther));
        verify(announcementService, never()).update(changedForOther);
    }

    @Test
    void adminCanEditAndDeleteAnyAnnouncement() {
        Announcement target = announcement(2L, "李老师", "DEPARTMENT");
        Announcement changed = announcement(2L, "平台管理员", "ADMIN");
        user(1L, "平台管理员", "ROLE_ADMIN");
        when(announcementService.findById(2L)).thenReturn(target);
        when(announcementService.update(changed)).thenReturn(1);
        when(announcementService.delete(2L)).thenReturn(1);

        assertEquals(200, controller.updateAnnouncement(2L, changed).getCode());
        assertEquals("平台管理员", changed.getPublisher());
        assertEquals(200, controller.deleteAnnouncement(2L).getCode());
    }

    @Test
    void batchDeleteRejectsAnyAnnouncementNotOwnedByTeacher() {
        Announcement own = announcement(1L, "张老师", "COLLEGE");
        Announcement other = announcement(2L, "李老师", "DEPARTMENT");
        user(9L, "张老师", "ROLE_TEACHER_COLLEGE");
        when(announcementService.findById(1L)).thenReturn(own);
        when(announcementService.findById(2L)).thenReturn(other);

        assertThrows(AccessDeniedException.class,
                () -> controller.batchDeleteAnnouncement(java.util.Map.of("ids", List.of(1L, 2L))));
        verify(announcementService, never()).batchDelete(List.of(1L, 2L));
    }
}
