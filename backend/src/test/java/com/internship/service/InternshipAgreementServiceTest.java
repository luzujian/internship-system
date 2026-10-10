package com.internship.service;

import com.internship.entity.FileUpload;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.InternshipAgreementModels.*;
import com.internship.exception.BusinessException;
import com.internship.mapper.FileUploadMapper;
import com.internship.mapper.InternshipAgreementMapper;
import com.internship.mapper.TeacherInternshipAppraisalMapper;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.LegacyWordPreviewConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InternshipAgreementServiceTest {
    private final InternshipAgreementMapper mapper = mock(InternshipAgreementMapper.class);
    private final FileUploadMapper uploads = mock(FileUploadMapper.class);
    private final TeacherInternshipAppraisalMapper appraisals = mock(TeacherInternshipAppraisalMapper.class);
    private final AliyunOSSOperator oss = mock(AliyunOSSOperator.class);
    private final LegacyWordPreviewConverter preview = mock(LegacyWordPreviewConverter.class);
    private InternshipAgreementService service;

    @BeforeEach void setup() {
        service = new InternshipAgreementService(mapper, uploads, appraisals, oss, preview);
    }

    @Test void missingOrDuplicateFilesCannotBeSubmitted() {
        assertThrows(BusinessException.class, () -> InternshipAgreementService.requireFileIds(null));
        assertThrows(BusinessException.class, () -> InternshipAgreementService.requireFileIds(List.of()));
        assertThrows(BusinessException.class, () -> InternshipAgreementService.requireFileIds(List.of(3L, 3L)));
        assertThrows(BusinessException.class, () -> InternshipAgreementService.requireFileIds(List.of(-1L)));
    }

    @Test void firstSubmissionStoresAllFilesWithOneVersion() {
        AgreementRecord record = record();
        when(mapper.findRecords(any())).thenReturn(List.of(record));
        when(mapper.findUploadForUpdate(10L)).thenReturn(upload(10L));
        when(mapper.findUploadForUpdate(11L)).thenReturn(upload(11L));
        doAnswer(call -> { ((AgreementVersion) call.getArgument(0)).setId(70L); return 1; }).when(mapper).insertVersion(any());
        when(mapper.insertFile(anyLong(), anyLong(), anyInt())).thenReturn(1);
        AgreementVersion result = service.bindSubmission(7L, "CONFIRMATION", 20L, List.of(11L, 10L));
        assertEquals(1, result.getVersion());
        assertEquals("单位甲", result.getCompanyName());
        verify(mapper).insertFile(70L, 11L, 0);
        verify(mapper).insertFile(70L, 10L, 1);
        verify(uploads, never()).deleteById(anyLong());
    }

    @Test void forgedOwnerCannotBindFileOrCreateVersion() {
        when(mapper.findRecords(any())).thenReturn(List.of(record()));
        FileUpload stolen = upload(10L);
        stolen.setUploaderId(8L);
        when(mapper.findUploadForUpdate(10L)).thenReturn(stolen);
        assertThrows(BusinessException.class, () -> service.bindSubmission(7L, "CONFIRMATION", 20L, List.of(10L)));
        verify(mapper, never()).insertVersion(any());
    }

    @Test void ordinaryAttachmentCannotSatisfyAgreementRequirement() {
        when(mapper.findRecords(any())).thenReturn(List.of(record()));
        FileUpload ordinary = upload(10L);
        ordinary.setBusinessType("resume");
        when(mapper.findUploadForUpdate(10L)).thenReturn(ordinary);
        assertThrows(BusinessException.class, () -> service.bindSubmission(7L, "CONFIRMATION", 20L, List.of(10L)));
        verify(mapper, never()).insertVersion(any());
    }

    @Test void boundFileCannotMoveToAnotherSourceOrChangedCompany() {
        when(mapper.findRecords(any())).thenReturn(List.of(record()));
        when(mapper.findUploadForUpdate(10L)).thenReturn(upload(10L));
        AgreementVersion bound = version(1);
        bound.setSourceId(21L);
        when(mapper.findUploadBindings(10L)).thenReturn(List.of(bound));
        assertThrows(BusinessException.class, () -> service.bindSubmission(7L, "CONFIRMATION", 20L, List.of(10L)));
        bound.setSourceId(20L);
        bound.setCompanyName("单位乙");
        assertThrows(BusinessException.class, () -> service.bindSubmission(7L, "CONFIRMATION", 20L, List.of(10L)));
        verify(mapper, never()).insertVersion(any());
    }

    @Test void correctionRequiresCurrentVersionAndRetainsPreviousFiles() {
        when(mapper.findRecords(any())).thenReturn(List.of(record()));
        AgreementVersion previous = version(1);
        when(mapper.findVersionsForUpdate("CONFIRMATION", 20L)).thenReturn(List.of(previous));
        when(mapper.findUploadForUpdate(11L)).thenReturn(upload(11L));
        doAnswer(call -> { ((AgreementVersion) call.getArgument(0)).setId(71L); return 1; }).when(mapper).insertVersion(any());
        when(mapper.insertFile(anyLong(), anyLong(), anyInt())).thenReturn(1);
        SubmitAgreementRequest request = request(List.of(11L), 1);
        assertEquals(2, service.correct(7L, "CONFIRMATION", 20L, request).getVersion());
        assertEquals(1, previous.getVersion());
        verify(uploads, never()).deleteById(anyLong());
        verifyNoInteractions(oss);
    }

    @Test void staleCorrectionStopsBeforeFileMutation() {
        when(mapper.findRecords(any())).thenReturn(List.of(record()));
        when(mapper.findVersionsForUpdate("CONFIRMATION", 20L)).thenReturn(List.of(version(2)));
        assertThrows(BusinessException.class, () -> service.correct(7L, "CONFIRMATION", 20L, request(List.of(11L), 1)));
        verify(mapper, never()).findUploadForUpdate(anyLong());
        verify(mapper, never()).insertVersion(any());
    }

    @Test void withdrawnRecordCannotBeCorrectedIndependently() {
        AgreementRecord record = record();
        record.setBusinessStatus("WITHDRAWN");
        when(mapper.findRecords(any())).thenReturn(List.of(record));
        assertThrows(BusinessException.class, () -> service.correct(7L, "CONFIRMATION", 20L, request(List.of(10L), 0)));
        verify(mapper, never()).insertVersion(any());
    }

    @Test void historicalBoundFileCannotBeDeleted() {
        when(mapper.findUploadForUpdate(10L)).thenReturn(upload(10L));
        when(mapper.findUploadBindings(10L)).thenReturn(List.of(version(1)));
        assertThrows(BusinessException.class, () -> service.deleteUnsubmittedUpload(7L, 10L));
        verifyNoInteractions(oss);
        verify(uploads, never()).deleteById(anyLong());
    }

    @Test void uploadRejectsDisguisedDocumentBeforeOssWrite() {
        var fake = new MockMultipartFile("file", "协议.pdf", "application/pdf", "not a PDF".getBytes(StandardCharsets.UTF_8));
        assertThrows(BusinessException.class, () -> service.upload(7L, fake));
        var zip = new MockMultipartFile("file", "协议.docx", "application/zip", new byte[] {'P', 'K', 0, 0, 0});
        assertThrows(BusinessException.class, () -> service.upload(7L, zip));
        verifyNoInteractions(oss);
    }

    @Test void databaseUploadFailureCleansPrivateOssObject() throws Exception {
        var file = new MockMultipartFile("file", "协议.pdf", "application/pdf", "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8));
        when(oss.uploadWithObjectName(any(), anyString())).thenReturn("private-object");
        when(uploads.insert(any())).thenThrow(new IllegalStateException("database failed"));
        assertThrows(IllegalStateException.class, () -> service.upload(7L, file));
        verify(oss).deleteFile("private-object");
    }

    @Test void uploadHasServerTimestampAndCommitFailureCleansObject() throws Exception {
        var file = new MockMultipartFile("file", "协议.pdf", "application/pdf", "%PDF-1.7 test".getBytes(StandardCharsets.UTF_8));
        when(oss.uploadWithObjectName(any(), anyString())).thenReturn("private-object");
        when(uploads.insert(any())).thenAnswer(call -> {
            FileUpload registered = call.getArgument(0);
            assertNotNull(registered.getUploadTime());
            assertEquals("internship_agreement", registered.getBusinessType());
            registered.setId(83L);
            return 1;
        });
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals(83L, service.upload(7L, file).getUploadId());
            verify(oss, never()).deleteFile(anyString());
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(sync -> sync.afterCompletion(org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(oss).deleteFile("private-object");
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test void renamedExcelOleIsNotAWordAgreement() throws Exception {
        byte[] bytes;
        try (var ole = new org.apache.poi.poifs.filesystem.POIFSFileSystem(); var output = new ByteArrayOutputStream()) {
            ole.createDocument(new java.io.ByteArrayInputStream(new byte[]{1, 2, 3}), "Workbook");
            ole.writeFilesystem(output);
            bytes = output.toByteArray();
        }
        var file = new MockMultipartFile("file", "协议.doc", "application/msword", bytes);
        assertThrows(BusinessException.class, () -> service.upload(7L, file));
        verifyNoInteractions(oss);
    }

    @Test void studentAndCounselorAuthorizationPrecedesOssRead() {
        AgreementFile file = new AgreementFile();
        file.setId(3L); file.setStudentId(8L); file.setSourceType("CONFIRMATION"); file.setSourceId(20L);
        when(mapper.findFile(3L)).thenReturn(file);
        assertThrows(AccessDeniedException.class, () -> service.studentFile(7L, 3L, false));
        TeacherUser teacher = new TeacherUser(); teacher.setId(12L); teacher.setTeacherType("COUNSELOR");
        assertThrows(AccessDeniedException.class, () -> service.teacherFile(teacher, 3L, true));
        verifyNoInteractions(oss, preview);
        verify(mapper).findRecords(argThat(q -> q.getScope() != null && q.getScope().getTeacherId().equals(12L)
                && "COUNSELOR".equals(q.getScope().getTeacherType())));
    }

    @Test void missingTeacherOrganizationFailsClosed() {
        TeacherUser teacher = new TeacherUser(); teacher.setId(12L); teacher.setTeacherType("COLLEGE");
        assertThrows(AccessDeniedException.class, () -> service.list(teacher, 1, 20, null, null, null, "ALL", "ACTIVE"));
        verifyNoInteractions(mapper);
    }

    @Test void exportRetainsFiltersWithoutPagination() {
        TeacherUser teacher = new TeacherUser(); teacher.setId(12L); teacher.setTeacherType("COUNSELOR");
        service.exportFiles(teacher, " 学号 ", 2L, "SELF_PRACTICE", "SUBMITTED", "ACTIVE");
        verify(mapper).findExportFiles(argThat(q -> q.getClassId().equals(2L) && q.getSourceType().equals("SELF_PRACTICE")
                && q.getKeyword().equals("学号") && q.getStatus().equals("SUBMITTED") && q.getPageSize() == null));
    }

    @Test void zipContainsVersionFoldersAndFailureManifest() throws Exception {
        ExportFile good = exportFile(1L, "first"); ExportFile bad = exportFile(2L, "second");
        when(oss.downloadFile("first")).thenReturn(new byte[]{1, 2, 3});
        when(oss.downloadFile("second")).thenThrow(new IOExceptionForTest());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(List.of(good, bad), output);
        try (ZipInputStream zip = new ZipInputStream(new java.io.ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8)) {
            assertEquals("测试班/20260001_测试学生/平台实习_单位甲_20/v2/1_协议.pdf", zip.getNextEntry().getName());
            assertArrayEquals(new byte[]{1, 2, 3}, zip.readAllBytes());
            assertEquals("_导出失败清单.txt", zip.getNextEntry().getName());
            String manifest = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(manifest.contains("2_协议.pdf"));
            assertFalse(manifest.contains("second"));
        }
    }

    private static class IOExceptionForTest extends java.io.IOException { }
    private static AgreementRecord record() {
        AgreementRecord record = new AgreementRecord();
        record.setStudentId(7L); record.setSourceType("CONFIRMATION"); record.setSourceId(20L);
        record.setCompanyName("单位甲"); record.setPositionName("实习岗"); record.setBusinessStatus("IN_PROGRESS");
        return record;
    }
    private static FileUpload upload(Long id) {
        FileUpload file = new FileUpload(); file.setId(id); file.setUploaderId(7L); file.setUploaderType("student");
        file.setBusinessType("internship_agreement"); file.setFileName("协议.pdf"); file.setFileType("pdf"); file.setFileSize(100L);
        return file;
    }
    private static AgreementVersion version(int number) {
        AgreementVersion version = new AgreementVersion(); version.setId(70L); version.setVersion(number);
        version.setStudentId(7L); version.setSourceType("CONFIRMATION"); version.setSourceId(20L);
        version.setCompanyName("单位甲"); version.setPositionName("实习岗"); return version;
    }
    private static SubmitAgreementRequest request(List<Long> ids, int version) {
        SubmitAgreementRequest request = new SubmitAgreementRequest(); request.setFileIds(ids); request.setExpectedVersion(version); return request;
    }
    private static ExportFile exportFile(Long id, String path) {
        ExportFile file = new ExportFile(); file.setId(id); file.setFilePath(path); file.setSourceType("CONFIRMATION");
        file.setSourceId(20L); file.setClassName("测试班"); file.setStudentNumber("20260001"); file.setStudentName("测试学生");
        file.setCompanyName("单位甲"); file.setVersion(2); file.setFileName("协议.pdf"); return file;
    }
}
