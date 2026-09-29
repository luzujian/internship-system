package com.internship.controller;

import com.internship.entity.Announcement;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.StudentUser;
import com.internship.entity.User;
import com.internship.mapper.StudentUserMapper;
import com.internship.service.AnnouncementReadRecordService;
import com.internship.service.AnnouncementService;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementVisibilitySecurityTest {
    @InjectMocks private AnnouncementController controller;
    @Mock private AnnouncementService announcementService;
    @Mock private AnnouncementReadRecordService readRecordService;
    @Mock private StudentUserMapper studentUserMapper;
    @Mock private AliyunOSSOperator aliyunOSSOperator;

    @AfterEach
    void clearUser() {
        CurrentHolder.remove();
    }

    private void user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        CurrentHolder.setUser(user);
    }

    private Announcement announcement(long id, String status, String targetType) {
        Announcement announcement = new Announcement();
        announcement.setId(id);
        announcement.setTitle("公告" + id);
        announcement.setStatus(status);
        announcement.setTargetType(targetType);
        announcement.setAttachments("[{\"name\":\"notice.pdf\",\"url\":\"https://example.invalid/notice.pdf\"}]");
        return announcement;
    }

    private List<Long> ids(Result result) {
        @SuppressWarnings("unchecked")
        List<Announcement> rows = (List<Announcement>) result.getData();
        return rows.stream().map(Announcement::getId).toList();
    }

    @Test
    void studentListExcludesDraftWrongAudienceAndFutureItemsButIgnoresLegacyExpiry() {
        user(7L, "ROLE_STUDENT");
        Announcement draft = announcement(1L, "DRAFT", "STUDENT");
        Announcement student = announcement(2L, "PUBLISHED", "STUDENT");
        Announcement teacher = announcement(3L, "PUBLISHED", "TEACHER");
        Announcement expired = announcement(4L, "PUBLISHED", "ALL");
        expired.setValidTo(new Date(System.currentTimeMillis() - 60_000));
        Announcement scheduled = announcement(5L, "PUBLISHED", "ALL");
        scheduled.setValidFrom(new Date(System.currentTimeMillis() + 60_000));
        Announcement missingTarget = announcement(6L, "PUBLISHED", "MAJOR");
        when(announcementService.findAll()).thenReturn(List.of(draft, student, teacher,
                expired, scheduled, missingTarget));

        assertEquals(List.of(2L, 4L), ids(controller.getAllAnnouncements()));
    }

    @Test
    void adminRetainsDraftManagementVisibility() {
        user(1L, "ROLE_ADMIN");
        Announcement draft = announcement(1L, "DRAFT", "TEACHER");
        when(announcementService.findAll()).thenReturn(List.of(draft));
        assertEquals(List.of(1L), ids(controller.getAllAnnouncements()));
    }

    @Test
    void searchAndPageNeverExposeDraftsOrHiddenTotalsToStudent() {
        user(7L, "ROLE_STUDENT");
        assertTrue(ids(controller.searchAnnouncements(null, "DRAFT")).isEmpty());
        verifyNoInteractions(announcementService);

        Announcement publicItem = announcement(2L, "PUBLISHED", "STUDENT");
        Announcement other = announcement(3L, "PUBLISHED", "TEACHER");
        when(announcementService.listForViewer(null, null, null, null, "7", "STUDENT"))
                .thenReturn(List.of(publicItem, other));
        @SuppressWarnings("unchecked")
        PageResult<Announcement> page = (PageResult<Announcement>)
                controller.getAnnouncementsByPage(1, 10, null, null, null, null, false).getData();
        assertEquals(1L, page.getTotal());
        assertEquals(List.of(2L), page.getRows().stream().map(Announcement::getId).toList());
        verify(announcementService, never()).findPage(1, 10, null, null, null, null, "7", "STUDENT");
    }

    @Test
    void spoofedUserQueryParametersAreIgnoredAndAudienceIsRechecked() {
        user(7L, "ROLE_STUDENT");
        StudentUser student = new StudentUser();
        student.setMajorId(5L);
        when(studentUserMapper.findById(7L)).thenReturn(student);
        Announcement ownMajor = announcement(2L, "PUBLISHED", "MAJOR");
        ownMajor.setTargetValue("{\"majorIds\":[\"5\"]}");
        Announcement otherMajor = announcement(3L, "PUBLISHED", "MAJOR");
        otherMajor.setTargetValue("{\"majorIds\":[\"50\"]}");
        when(announcementService.getAnnouncementsForUser("STUDENT", "5", "7", "STUDENT"))
                .thenReturn(List.of(ownMajor, otherMajor));

        assertEquals(List.of(2L), ids(controller.getAnnouncementsForUser("TEACHER", "COLLEGE", "999")));
        verify(announcementService).getAnnouncementsForUser("STUDENT", "5", "7", "STUDENT");
    }

    @Test
    void teacherOwnStudentAnnouncementCanBeReadAndPersistsReadCount() {
        User teacher = new User();
        teacher.setId(9L);
        teacher.setName("张老师");
        teacher.setRole("ROLE_TEACHER_COLLEGE");
        CurrentHolder.setUser(teacher);

        Announcement own = announcement(11L, "PUBLISHED", "STUDENT");
        own.setPublisher("张老师");
        own.setPublisherRole("COLLEGE");
        own.setPublisherUserId(9L);
        own.setPublisherUserType("TEACHER");
        when(announcementService.findById(11L)).thenReturn(own);
        when(readRecordService.findByAnnouncementAndUser(11L, "9", "TEACHER")).thenReturn(null);
        when(readRecordService.countReadByAnnouncementId(11L)).thenReturn(3L);

        Result result = controller.getAnnouncementById(11L);

        assertEquals(200, result.getCode());
        assertEquals(Boolean.TRUE, own.getIsRead());
        assertEquals(3, own.getReadCount());
        verify(readRecordService).insert(org.mockito.ArgumentMatchers.argThat(record ->
                record.getAnnouncementId() == 11L
                        && "9".equals(record.getUserId())
                        && "TEACHER".equals(record.getUserType())));
        verify(announcementService, never()).incrementReadCount(11L);
    }

    @Test
    void directDetailAndAttachmentRejectDraftAndWrongAudienceBeforeDownload() {
        user(7L, "ROLE_STUDENT");
        Announcement draft = announcement(1L, "DRAFT", "STUDENT");
        Announcement teacher = announcement(2L, "PUBLISHED", "TEACHER");
        when(announcementService.findById(1L)).thenReturn(draft);
        when(announcementService.findById(2L)).thenReturn(teacher);

        assertThrows(AccessDeniedException.class, () -> controller.getAnnouncementById(1L));
        assertThrows(AccessDeniedException.class, () -> controller.getAnnouncementById(2L));
        assertThrows(AccessDeniedException.class, () -> controller.downloadAttachment("1", "notice.pdf"));
        assertThrows(AccessDeniedException.class, () -> controller.downloadAttachment("2", "notice.pdf"));
        verifyNoInteractions(aliyunOSSOperator);
        verifyNoInteractions(readRecordService);
    }

    @Test
    void visibleAttachmentDownloadsOnlyTheStoredFile() throws Exception {
        var policy = new com.internship.service.AnnouncementAttachmentPolicy("synthetic-test-key");
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "attachmentPolicy", policy);
        user(1L, "ROLE_ADMIN");
        String receipt = policy.issue("notice.pdf", "https://example.invalid/notice.pdf");
        user(7L, "ROLE_STUDENT");
        Announcement notice = announcement(2L, "PUBLISHED", "STUDENT");
        notice.setAttachments(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(List.of(
                java.util.Map.of("name", "notice.pdf", "url", "https://example.invalid/notice.pdf", "receipt", receipt))));
        when(announcementService.findById(2L)).thenReturn(notice);
        when(aliyunOSSOperator.downloadFile("https://example.invalid/notice.pdf"))
                .thenReturn(new byte[] {1, 2, 3});

        ResponseEntity<InputStreamResource> result = controller.downloadAttachment("2", "notice.pdf");
        assertEquals(200, result.getStatusCode().value());
        assertEquals(3, result.getBody().getInputStream().readAllBytes().length);
        verify(aliyunOSSOperator).downloadFile("https://example.invalid/notice.pdf");
    }

    @Test
    void visibleButUnsignedAttachmentNeverReachesStorage() {
        var policy = new com.internship.service.AnnouncementAttachmentPolicy("synthetic-test-key");
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "attachmentPolicy", policy);
        user(7L, "ROLE_STUDENT");
        when(announcementService.findById(2L)).thenReturn(announcement(2L, "PUBLISHED", "STUDENT"));
        assertThrows(AccessDeniedException.class, () -> controller.downloadAttachment("2", "notice.pdf"));
        verifyNoInteractions(aliyunOSSOperator);
    }

    @Test
    void legacyManifestDoesNotBypassAudienceAndDownloadsStoredBinding(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        var policy = new com.internship.service.AnnouncementAttachmentPolicy("synthetic-test-key");
        var manifest = temp.resolve("legacy.json");
        java.nio.file.Files.writeString(manifest, """
                [{"announcementId":2,"name":"notice.pdf","url":"https://example.invalid/notice.pdf"}]
                """);
        org.springframework.test.util.ReflectionTestUtils.setField(policy, "legacyManifest", manifest.toString());
        org.springframework.test.util.ReflectionTestUtils.invokeMethod(policy, "loadLegacyManifest");
        org.springframework.test.util.ReflectionTestUtils.setField(controller, "attachmentPolicy", policy);
        user(7L, "ROLE_STUDENT");
        Announcement notice = announcement(2L, "PUBLISHED", "TEACHER");
        when(announcementService.findById(2L)).thenReturn(notice);
        assertThrows(AccessDeniedException.class, () -> controller.downloadAttachment("2", "notice.pdf"));
        verifyNoInteractions(aliyunOSSOperator);
        notice.setTargetType("STUDENT");
        when(aliyunOSSOperator.downloadFile("https://example.invalid/notice.pdf")).thenReturn(new byte[]{1,2,3});
        assertEquals(200, controller.downloadAttachment("2", "notice.pdf").getStatusCode().value());
        assertEquals(404, controller.downloadAttachment("2", "other.pdf").getStatusCode().value());
        verify(aliyunOSSOperator).downloadFile("https://example.invalid/notice.pdf");
    }

    @Test
    void missingServerIdentityCannotEnumerateAnnouncementsOrAttachments() {
        assertThrows(AccessDeniedException.class, controller::getAllAnnouncements);
        assertThrows(AccessDeniedException.class, () -> controller.searchAnnouncements(null, null));
        assertThrows(AccessDeniedException.class, () -> controller.getAnnouncementsForUser("STUDENT", "5", "7"));
        Announcement notice = announcement(2L, "PUBLISHED", "ALL");
        when(announcementService.findById(2L)).thenReturn(notice);
        assertThrows(AccessDeniedException.class, () -> controller.downloadAttachment("2", "notice.pdf"));
        verifyNoInteractions(aliyunOSSOperator);
    }
}
