package com.internship.service;

import com.internship.entity.StudentArchive;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.dto.JobApplicationMaterialModels.*;
import com.internship.exception.BusinessException;
import com.internship.mapper.*;
import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationMaterialServiceTest {
    @Test void materialMetadataDoesNotExposeThePrivateStoragePath() throws Exception {
        var file = new MaterialFile(); file.setId(81L); file.setFileName("resume.pdf"); file.setFilePath("private/secret-object.pdf");
        var json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(file);
        assertFalse(json.contains("filePath"));
        assertFalse(json.contains("secret-object"));
    }
    @Mock JobApplicationMaterialMapper mapper;
    @Mock StudentJobApplicationMapper applications;
    @Mock InternshipApplicationMapper companyApplications;
    @Mock StudentArchiveService archives;
    @Mock AliyunOSSOperator oss;
    @InjectMocks JobApplicationMaterialService service;
    private static final byte[] PDF = "%PDF-1.7 original application".getBytes(StandardCharsets.UTF_8);

    @AfterEach void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void copiesSurviveDeletingAndReplacingThePersonalLibrary() throws Exception {
        StudentArchive source = archive(11L, 42L, "resume");
        when(archives.findById(11L)).thenReturn(source);
        Map<String, byte[]> storage = new HashMap<>();
        storage.put(source.getFileUrl(), PDF);
        when(oss.downloadFile(anyString())).thenAnswer(call -> storage.get(call.getArgument(0)));
        when(oss.uploadWithObjectName(any(), anyString())).thenAnswer(call -> {
            String path = "private/" + call.getArgument(1);
            storage.put(path, ((byte[]) call.getArgument(0)).clone());
            return path;
        });
        Submission submission = new Submission();
        List<MaterialFile> files = new ArrayList<>();
        when(mapper.insertSubmission(any())).thenAnswer(call -> {
            Submission saved = call.getArgument(0); saved.setId(5L);
            submission.setId(saved.getId()); submission.setSubmittedAt(saved.getSubmittedAt()); return 1;
        });
        when(mapper.insertFile(any())).thenAnswer(call -> { MaterialFile file = call.getArgument(0); file.setId(81L); files.add(file); return 1; });
        var app = application(101L, "pending");
        var selected = service.prepare(42L, List.of(11L), null, List.of());
        service.save(app, 501L, selected);
        assertNotEquals(source.getFileUrl(), files.getFirst().getFilePath());
        assertTrue(files.getFirst().getFilePath().contains("job-application/42/101/"));
        storage.remove(source.getFileUrl());
        storage.put("personal/new-resume.pdf", "%PDF-new".getBytes(StandardCharsets.UTF_8));
        when(applications.findById(101L)).thenReturn(app);
        when(mapper.findByJobApplicationId(101L)).thenReturn(submission);
        when(mapper.findFiles(5L)).thenReturn(files);
        when(mapper.findFile(81L)).thenReturn(files.getFirst());
        clearInvocations(archives);
        assertArrayEquals(PDF, service.studentFile(42L, 101L, 81L).getContent());
        assertArrayEquals(PDF, service.companyFile(7L, 101L, 81L, false).getContent());
        verifyNoInteractions(archives);
        verify(oss, never()).deleteFile(anyString());
    }

    @Test void customFilesAndCertificateSelectionNeverModifyThePersonalLibrary() throws Exception {
        var cert = archive(12L, 42L, "certificate");
        when(archives.findById(12L)).thenReturn(cert);
        when(oss.downloadFile(cert.getFileUrl())).thenReturn(PDF);
        var result = service.prepare(42L, List.of(12L), upload("resume", "custom.pdf"), List.of(upload("certificates", "extra.pdf")));
        assertEquals(List.of("certificate", "resume", "certificate"), result.stream().map(JobApplicationMaterialService.PreparedFile::fileType).toList());
        assertEquals("custom.pdf", result.get(1).fileName());
        verify(archives, never()).delete(anyLong());
        verify(archives, never()).insert(any());
        verify(archives).findById(12L);
        verifyNoMoreInteractions(archives);
    }

    @Test void missingSelectedFileAbortsInsteadOfFallingBackToNewLibraryMaterials() {
        assertThrows(BusinessException.class, () -> service.prepare(42L, List.of(999L), null, List.of()));
        verifyNoInteractions(oss);
        verify(archives, never()).findByStudentId(anyLong());
    }

    @Test void foreignPersonalFilesAreRejectedBeforeReadingAnyBytes() {
        when(archives.findById(11L)).thenReturn(archive(11L, 99L, "resume"));
        assertThrows(AccessDeniedException.class, () -> service.prepare(42L, List.of(11L), null, List.of()));
        verifyNoInteractions(oss);
    }

    @Test void duplicateSelectionsAndTwoResumesAreRejected() {
        assertThrows(BusinessException.class, () -> service.prepare(42L, List.of(11L, 11L), null, List.of()));
        when(archives.findById(11L)).thenReturn(archive(11L, 42L, "resume"));
        assertThrows(BusinessException.class, () -> service.prepare(42L, List.of(11L), upload("resume", "another.pdf"), List.of()));
        verifyNoInteractions(oss);
    }

    @Test void invalidUploadCannotBeSilentlySubmittedWithoutMaterials() {
        var invalid = new MockMultipartFile("resume", "resume.pdf", "application/pdf", "not a pdf".getBytes(StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> service.prepare(42L, List.of(), invalid, List.of()));
        verifyNoInteractions(oss);
    }

    @Test void legacyJsonSubmissionCapturesLibraryOnlyAtSubmissionTime() throws Exception {
        var resume = archive(11L, 42L, "resume");
        when(archives.findByStudentId(42L)).thenReturn(List.of(resume));
        when(oss.downloadFile(resume.getFileUrl())).thenReturn(PDF);
        assertEquals(1, service.prepare(42L, null, null, List.of()).size());
        verify(archives).findByStudentId(42L);
    }

    @Test void originalFilesAreNotDeletedWhenSavingCopiesFails() throws Exception {
        when(mapper.insertSubmission(any())).thenAnswer(call -> { ((Submission) call.getArgument(0)).setId(5L); return 1; });
        when(oss.uploadWithObjectName(any(), anyString())).thenReturn("private/copy.pdf");
        when(mapper.insertFile(any())).thenThrow(new IllegalStateException("database unavailable"));
        TransactionSynchronizationManager.initSynchronization();
        assertThrows(BusinessException.class, () -> service.save(application(101L, "pending"), 501L,
                List.of(new JobApplicationMaterialService.PreparedFile("resume", "resume.pdf", PDF))));
        verify(oss, never()).deleteFile(anyString());
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(oss).deleteFile("private/copy.pdf");
    }

    @Test void commitKeepsTheIndependentCopies() throws Exception {
        when(mapper.insertSubmission(any())).thenAnswer(call -> { ((Submission) call.getArgument(0)).setId(5L); return 1; });
        when(oss.uploadWithObjectName(any(), anyString())).thenReturn("private/copy.pdf");
        when(mapper.insertFile(any())).thenReturn(1);
        TransactionSynchronizationManager.initSynchronization();
        service.save(application(101L, "pending"), 501L, List.of(new JobApplicationMaterialService.PreparedFile("resume", "resume.pdf", PDF)));
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
        verify(oss, never()).deleteFile(anyString());
    }

    @Test void legacyApplicationNeverExposesTheStudentsCurrentMaterials() {
        when(applications.findById(101L)).thenReturn(application(101L, "pending"));
        View result = service.studentView(42L, 101L);
        assertFalse(result.snapshotAvailable());
        assertTrue(result.files().isEmpty());
        verifyNoInteractions(archives, oss);
    }

    @Test void aSubmittedEmptyMaterialListIsDifferentFromAnUnrecoverableLegacyApplication() {
        when(applications.findById(101L)).thenReturn(application(101L, "pending"));
        var header = new Submission(); header.setId(5L);
        when(mapper.findByJobApplicationId(101L)).thenReturn(header);
        when(mapper.findFiles(5L)).thenReturn(List.of());
        assertTrue(service.studentView(42L, 101L).snapshotAvailable());
    }

    @Test void filesFromAnotherAttemptCannotBeReadUsingThisApplicationId() {
        when(applications.findById(102L)).thenReturn(application(102L, "pending"));
        var header = new Submission(); header.setId(6L);
        when(mapper.findByJobApplicationId(102L)).thenReturn(header);
        var file = new MaterialFile(); file.setId(82L);
        when(mapper.findFiles(6L)).thenReturn(List.of(file));
        assertThrows(AccessDeniedException.class, () -> service.studentFile(42L, 102L, 81L));
        assertThrows(AccessDeniedException.class, () -> service.companyFile(7L, 102L, 81L, false));
        verifyNoInteractions(oss);
    }

    @Test void anotherStudentOrCompanyCannotReadApplicationMaterials() {
        when(applications.findById(101L)).thenReturn(application(101L, "pending"));
        assertThrows(AccessDeniedException.class, () -> service.studentView(99L, 101L));
        assertThrows(AccessDeniedException.class, () -> service.companyView(99L, 101L, false));
        verifyNoInteractions(mapper, oss);
    }

    @ParameterizedTest @ValueSource(strings = {"withdrawn", "cancelled", "rejected", "interview_failed"})
    void companyCannotReadMaterialsForAnInactiveApplication(String status) {
        when(applications.findById(101L)).thenReturn(application(101L, status));
        assertTrue(service.companyView(7L, 101L, false).accessRestricted());
        assertThrows(AccessDeniedException.class, () -> service.companyFile(7L, 101L, 81L, false));
        verifyNoInteractions(oss);
    }

    @Test void companyActivityUsesItsExplicitBindingInsteadOfTheLatestApplicationForAStudent() {
        var activity = new InternshipApplicationEntity(); activity.setId(501L); activity.setCompanyId(7L); activity.setStatus("pending");
        when(companyApplications.findById(501L)).thenReturn(activity);
        var header = new Submission(); header.setId(5L);
        when(mapper.findByCompanyApplicationId(501L)).thenReturn(header);
        when(mapper.findFiles(5L)).thenReturn(List.of());
        assertTrue(service.companyView(7L, 501L, true).snapshotAvailable());
        verifyNoInteractions(applications, archives);
    }

    private static MockMultipartFile upload(String field, String name) { return new MockMultipartFile(field, name, "application/pdf", PDF); }
    private static StudentArchive archive(Long id, Long studentId, String type) {
        var result = new StudentArchive(); result.setId(id); result.setStudentId(studentId); result.setFileType(type);
        result.setFileName(type + ".pdf"); result.setFileUrl("personal/" + id + ".pdf"); return result;
    }
    private static StudentJobApplication application(Long id, String status) {
        var result = new StudentJobApplication(); result.setId(id); result.setStudentId(42L); result.setCompanyId(7L);
        result.setStatus(status); result.setCreateTime(new Date()); return result;
    }
}
