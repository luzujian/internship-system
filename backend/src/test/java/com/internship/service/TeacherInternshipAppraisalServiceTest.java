package com.internship.service;

import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherAppraisalDetail;
import com.internship.entity.dto.TeacherAppraisalExportFile;
import com.internship.entity.dto.TeacherAppraisalListResponse;
import com.internship.entity.dto.TeacherAppraisalStudent;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.entity.dto.TeacherAppraisalScope;
import com.internship.mapper.TeacherInternshipAppraisalMapper;
import com.internship.mapper.InternshipAppraisalMapper;
import com.internship.service.impl.TeacherInternshipAppraisalServiceImpl;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.LegacyWordPreviewConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherInternshipAppraisalServiceTest {
    @Mock private TeacherInternshipAppraisalMapper mapper;
    @Mock private InternshipAppraisalMapper appraisalMapper;
    @Mock private AliyunOSSOperator ossOperator;
    @Mock private LegacyWordPreviewConverter converter;

    private TeacherInternshipAppraisalServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TeacherInternshipAppraisalServiceImpl(mapper, appraisalMapper, ossOperator, converter);
    }

    @Test
    void listUsesCollegeScopeAndFilteredSummaryIgnoringStatus() {
        TeacherUser teacher = teacher("COLLEGE", "8", null);
        when(mapper.countResponsibleStudents(any(), eq("张"), eq(20L))).thenReturn(6L);
        when(mapper.countSubmittedStudents(any(), eq("张"), eq(20L))).thenReturn(4L);
        when(mapper.countStudents(any(), eq("张"), eq(20L), eq("NOT_SUBMITTED"))).thenReturn(2L);
        when(mapper.findStudents(any(), eq("张"), eq(20L), eq("NOT_SUBMITTED"), eq(0), eq(10)))
                .thenReturn(List.of(new TeacherAppraisalStudent()));
        when(mapper.findClasses(any())).thenReturn(List.of());

        TeacherAppraisalListResponse result = service.list(
                teacher, 1, 10, " 张 ", 20L, "NOT_SUBMITTED");

        assertEquals(2L, result.getTotal());
        assertEquals(6L, result.getResponsibleStudentCount());
        assertEquals(4L, result.getSubmittedCount());
        assertEquals(2L, result.getNotSubmittedCount());
        ArgumentCaptor<TeacherAppraisalScope> scope = ArgumentCaptor.forClass(TeacherAppraisalScope.class);
        verify(mapper).countResponsibleStudents(scope.capture(), eq("张"), eq(20L));
        assertEquals("COLLEGE", scope.getValue().getTeacherType());
        assertEquals(8L, scope.getValue().getDepartmentId());
    }

    @Test
    void missingDepartmentRelationReturnsEmptyWithoutBroadQuery() {
        TeacherUser teacher = teacher("DEPARTMENT", "8", null);
        TeacherAppraisalListResponse result = service.list(teacher, 1, 20, null, null, "ALL");
        assertEquals(0L, result.getTotal());
        assertTrue(result.getRows().isEmpty());
        verify(mapper, never()).countStudents(any(), any(), any(), any());
    }

    @Test
    void detailOnlyReturnsSafeMetadataForLatestSubmittedRecord() {
        TeacherUser teacher = teacher("COUNSELOR", null, null);
        TeacherAppraisalStudent student = new TeacherAppraisalStudent();
        student.setStudentId(30L);
        student.setStudentNumber("20260001");
        student.setStudentName("张三");
        student.setSubmissionId(40L);
        student.setSubmittedAt(new Date());
        when(mapper.findSubmittedStudent(any(), eq(30L))).thenReturn(student);
        InternshipAppraisalFile file = new InternshipAppraisalFile();
        file.setId(50L);
        file.setFileName("鉴定表.pdf");
        file.setFilePath("https://secret/path.pdf");
        when(appraisalMapper.findFilesBySubmissionId(40L)).thenReturn(List.of(file));

        TeacherAppraisalDetail detail = service.findSubmittedDetail(teacher, 30L);

        assertNotNull(detail);
        assertEquals("SUBMITTED", detail.getStatus());
        assertEquals("鉴定表.pdf", detail.getFiles().getFirst().getFileName());
    }

    @Test
    void unauthorizedOrNonSubmittedFileIsIndistinguishableFromMissingFile() throws Exception {
        TeacherUser teacher = teacher("COUNSELOR", null, null);
        when(mapper.findAuthorizedSubmittedFile(any(), eq(99L))).thenReturn(null);
        assertNull(service.loadFile(teacher, 99L, false));
        verify(ossOperator, never()).downloadFile(any());
    }

    @Test
    void legacyDocPreviewUsesPdfConverterAfterScopeAuthorization() throws Exception {
        TeacherUser teacher = teacher("DEPARTMENT", null, "18");
        InternshipAppraisalFile file = new InternshipAppraisalFile();
        file.setId(99L);
        file.setFileName("鉴定表.doc");
        file.setFileType("doc");
        file.setFilePath("private.doc");
        when(mapper.findAuthorizedSubmittedFile(any(), eq(99L))).thenReturn(file);
        when(ossOperator.downloadFile("private.doc")).thenReturn(new byte[]{1, 2});
        when(converter.convertToPdf(any(byte[].class))).thenReturn("%PDF-".getBytes(StandardCharsets.US_ASCII));

        TeacherAppraisalFileContent result = service.loadFile(teacher, 99L, true);

        assertNotNull(result);
        assertEquals("鉴定表.pdf", result.getFileName());
        assertEquals("application/pdf", result.getFileType());
        ArgumentCaptor<TeacherAppraisalScope> scope = ArgumentCaptor.forClass(TeacherAppraisalScope.class);
        verify(mapper).findAuthorizedSubmittedFile(scope.capture(), eq(99L));
        assertEquals(18L, scope.getValue().getDivisionId());
    }

    @Test
    void zipStreamsValidFilesAndIncludesFailureManifest() throws Exception {
        TeacherAppraisalExportFile first = exportFile(1L, "软件/一班", "2026:01", "张三", "鉴定表.pdf", "ok");
        TeacherAppraisalExportFile duplicate = exportFile(2L, "软件/一班", "2026:01", "张三", "鉴定表.pdf", "bad");
        when(ossOperator.downloadFile("ok")).thenReturn("PDF".getBytes(StandardCharsets.UTF_8));
        when(ossOperator.downloadFile("bad")).thenThrow(new RuntimeException("OSS unavailable"));

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        service.writeZip(List.of(first, duplicate), output);

        boolean foundFile = false;
        boolean foundFailureManifest = false;
        String failureText = "";
        try (ZipInputStream zip = new ZipInputStream(
                new java.io.ByteArrayInputStream(output.toByteArray()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.getName().endsWith("鉴定表.pdf")) foundFile = true;
                if (entry.getName().equals("_导出失败清单.txt")) {
                    foundFailureManifest = true;
                    failureText = new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        assertTrue(foundFile);
        assertTrue(foundFailureManifest);
        assertTrue(failureText.contains("2_鉴定表.pdf"));
        assertFalse(failureText.contains("bad"));
        assertFalse(failureText.contains("OSS unavailable"));
    }

    private TeacherUser teacher(String type, String departmentId, String divisionId) {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(9L);
        teacher.setTeacherType(type);
        teacher.setDepartmentId(departmentId);
        teacher.setDivisionId(divisionId);
        return teacher;
    }

    private TeacherAppraisalExportFile exportFile(Long id, String className, String number,
                                                     String name, String fileName, String path) {
        TeacherAppraisalExportFile file = new TeacherAppraisalExportFile();
        file.setFileId(id);
        file.setClassName(className);
        file.setStudentNumber(number);
        file.setStudentName(name);
        file.setFileName(fileName);
        file.setFilePath(path);
        return file;
    }
}
