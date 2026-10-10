package com.internship.controller;

import com.internship.entity.FileUpload;
import com.internship.entity.InternshipAppraisalFile;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.exception.BusinessException;
import com.internship.service.InternshipAppraisalService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.UserService;
import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentInternshipAppraisalControllerTest {
    private static final long MAX_SIZE = 20L * 1024 * 1024;

    @Mock private UserService userService;
    @Mock private InternshipAppraisalService appraisalService;
    @Mock private InternshipStageAssignmentService assignmentService;
    @Mock private AliyunOSSOperator ossOperator;

    private StudentInternshipAppraisalController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentInternshipAppraisalController(
                userService, appraisalService, assignmentService, ossOperator);
        ReflectionTestUtils.setField(controller, "maxSize", MAX_SIZE);

        org.springframework.security.core.userdetails.UserDetails principal =
                org.springframework.security.core.userdetails.User
                        .withUsername("student01")
                        .password("password")
                        .roles("STUDENT")
                        .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal, principal.getPassword(), principal.getAuthorities()));
        User user = new User();
        user.setId(11L);
        user.setUsername("student01");
        when(userService.findByUsername("student01")).thenReturn(user);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void docxUploadStoresShortExtensionInsteadOfLongMimeType() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "鉴定表.DOCX",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "content".getBytes());
        when(ossOperator.uploadWithObjectName(any(byte[].class), any(String.class)))
                .thenReturn("https://bucket.example/internship-appraisal/file.docx");
        when(appraisalService.addUploadedFile(anyLong(), any(FileUpload.class), any(InternshipAppraisalFile.class)))
                .thenAnswer(invocation -> {
                    InternshipAppraisalFile appraisalFile = invocation.getArgument(2);
                    appraisalFile.setId(99L);
                    return appraisalFile;
                });

        Result result = controller.upload(file, null, null);

        ArgumentCaptor<FileUpload> uploadCaptor = ArgumentCaptor.forClass(FileUpload.class);
        ArgumentCaptor<InternshipAppraisalFile> fileCaptor =
                ArgumentCaptor.forClass(InternshipAppraisalFile.class);
        verify(appraisalService).addUploadedFile(
                org.mockito.ArgumentMatchers.eq(11L), uploadCaptor.capture(), fileCaptor.capture());
        assertEquals(200, result.getCode());
        assertEquals("docx", uploadCaptor.getValue().getFileType());
        assertEquals("docx", fileCaptor.getValue().getFileType());
    }

    @Test
    void rejectsFilesOverTwentyMegabytesBeforeOssUpload() throws Exception {
        MultipartFile file = org.mockito.Mockito.mock(MultipartFile.class);
        when(file.isEmpty()).thenReturn(false);
        when(file.getSize()).thenReturn(MAX_SIZE + 1);

        Result result = controller.upload(file, null, null);

        assertEquals(400, result.getCode());
        assertEquals("文件大小不能超过20MB", result.getMessage());
        verify(ossOperator, never()).uploadWithObjectName(any(), any());
    }

    @Test
    void removesOssObjectWhenDatabaseOperationFails() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "鉴定表.pdf", "application/pdf", "content".getBytes());
        ArgumentCaptor<String> objectNameCaptor = ArgumentCaptor.forClass(String.class);
        when(ossOperator.uploadWithObjectName(any(byte[].class), objectNameCaptor.capture()))
                .thenReturn("https://bucket.example/internship-appraisal/file.pdf");
        when(appraisalService.addUploadedFile(anyLong(), any(), any()))
                .thenThrow(new BusinessException("数据库保存失败"));

        Result result = controller.upload(file, null, null);

        assertEquals(400, result.getCode());
        assertEquals("数据库保存失败", result.getMessage());
        verify(ossOperator).deleteFile(objectNameCaptor.getValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"PENDING_CONFIRMATION", "REJECTED", "WITHDRAWN"})
    void rejectsUnconfirmedPlacementBeforeUploadingFile(String placementStatus) throws Exception {
        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setId(22L);
        assignment.setStudentId(11L);
        assignment.setStatus(placementStatus);
        when(assignmentService.findById(22L)).thenReturn(assignment);
        MockMultipartFile file = new MockMultipartFile(
                "file", "鉴定表.pdf", "application/pdf", "content".getBytes());

        Result result = controller.upload(file, 22L, null);

        assertEquals(400, result.getCode());
        assertEquals("只能关联已确认的实习经历", result.getMessage());
        verify(ossOperator, never()).uploadWithObjectName(any(), any());
        verify(appraisalService, never()).addUploadedFile(anyLong(), any(), any());
    }
}
