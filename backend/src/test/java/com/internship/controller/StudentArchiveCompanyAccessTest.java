package com.internship.controller;

import com.internship.entity.StudentArchive;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.User;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.service.StudentArchiveService;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StudentArchiveCompanyAccessTest {
    private StudentArchiveController controller;
    private StudentArchiveService archiveService;
    private StudentJobApplicationMapper applicationMapper;
    private AliyunOSSOperator oss;

    @BeforeEach
    void setUp() {
        controller = new StudentArchiveController();
        archiveService = mock(StudentArchiveService.class);
        applicationMapper = mock(StudentJobApplicationMapper.class);
        oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "studentArchiveService", archiveService);
        ReflectionTestUtils.setField(controller, "studentJobApplicationMapper", applicationMapper);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        User company = new User();
        company.setId(10L);
        company.setRole("ROLE_COMPANY");
        CurrentHolder.setUser(company);
    }

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
    }

    @Test
    void companySeesOnlyApprovedArchivesForItsActiveApplicant() throws Exception {
        when(applicationMapper.findByStudentId(20L)).thenReturn(List.of(application(10L, "pending")));
        StudentArchive approved = archive(1L, 20L, 1);
        when(archiveService.findByStudentId(20L)).thenReturn(List.of(
                approved, archive(2L, 20L, 0), archive(3L, 20L, 2), archive(4L, 21L, 1)));
        assertEquals(List.of(approved), controller.getCompanyStudentArchives(20L).getData());

        when(archiveService.findById(1L)).thenReturn(approved);
        when(oss.downloadFile("oss-object")).thenReturn(new byte[]{1, 2});
        assertArrayEquals(new byte[]{1, 2}, controller.downloadArchiveForCompany(1L).getBody());
        assertArrayEquals(new byte[]{1, 2}, controller.previewArchiveForCompany(1L).getBody());
        verify(oss, times(2)).downloadFile("oss-object");
    }

    @Test
    void anotherCompanyOrWithdrawnApplicationCannotReadApplicantArchives() {
        StudentArchive approved = archive(1L, 20L, 1);
        when(archiveService.findById(1L)).thenReturn(approved);
        when(applicationMapper.findByStudentId(20L)).thenReturn(List.of(application(11L, "pending")));
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyStudentArchives(20L));
        assertThrows(AccessDeniedException.class, () -> controller.downloadArchiveForCompany(1L));
        assertThrows(AccessDeniedException.class, () -> controller.previewArchiveForCompany(1L));

        when(applicationMapper.findByStudentId(20L)).thenReturn(List.of(application(10L, "withdrawn")));
        assertThrows(AccessDeniedException.class, () -> controller.downloadArchiveForCompany(1L));
        verifyNoInteractions(oss);
    }

    @Test
    void unapprovedArchiveAndMissingCompanyIdentityFailClosed() {
        when(applicationMapper.findByStudentId(20L)).thenReturn(List.of(application(10L, "approved")));
        when(archiveService.findById(2L)).thenReturn(archive(2L, 20L, 0));
        assertThrows(AccessDeniedException.class, () -> controller.downloadArchiveForCompany(2L));
        assertThrows(AccessDeniedException.class, () -> controller.previewArchiveForCompany(2L));
        CurrentHolder.remove();
        assertThrows(AccessDeniedException.class, () -> controller.getCompanyStudentArchives(20L));
        verifyNoInteractions(oss);
    }

    private StudentJobApplication application(Long companyId, String status) {
        StudentJobApplication application = new StudentJobApplication();
        application.setStudentId(20L);
        application.setCompanyId(companyId);
        application.setStatus(status);
        return application;
    }

    private StudentArchive archive(Long id, Long studentId, int status) {
        StudentArchive archive = new StudentArchive();
        archive.setId(id);
        archive.setStudentId(studentId);
        archive.setStatus(status);
        archive.setFileUrl("oss-object");
        archive.setFileName("file.pdf");
        return archive;
    }
}
