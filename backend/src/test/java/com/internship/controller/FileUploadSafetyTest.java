package com.internship.controller;

import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.CurrentHolder;
import com.internship.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FileUploadSafetyTest {
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 1};

    @AfterEach void clearIdentity() { CurrentHolder.remove(); }

    @Test
    void htmlFilenameCannotControlStoredExtension() throws Exception {
        var controller = new FileUploadController();
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        ReflectionTestUtils.setField(controller, "maxSize", 10_000_000L);
        when(oss.uploadWithObjectName(any(byte[].class), anyString())).thenReturn("https://example.invalid/image.png");

        controller.uploadImage(new MockMultipartFile("file", "payload.html", "image/png", PNG));

        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(oss).uploadWithObjectName(any(byte[].class), name.capture());
        assertTrue(name.getValue().endsWith(".png"));
    }

    @Test
    void claimedImageWithHtmlBytesIsRejectedBeforeStorage() throws Exception {
        var controller = new FileUploadController();
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        ReflectionTestUtils.setField(controller, "maxSize", 10_000_000L);

        controller.uploadImage(new MockMultipartFile("file", "payload.png", "image/png", "<script>".getBytes()));

        verifyNoInteractions(oss);
    }

    @Test
    void studentFileUploadUsesOwnerBoundObjectPath() throws Exception {
        var controller = new FileUploadController();
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        ReflectionTestUtils.setField(controller, "maxSize", 10_000_000L);
        User student = new User();
        student.setId(52L);
        student.setRole("ROLE_STUDENT");
        CurrentHolder.setUser(student);
        when(oss.uploadWithObjectName(any(byte[].class), anyString())).thenReturn("https://example.invalid/file.pdf");

        controller.uploadFile(new MockMultipartFile("file", "application.pdf", "application/pdf", new byte[] {1}));

        ArgumentCaptor<String> name = ArgumentCaptor.forClass(String.class);
        verify(oss).uploadWithObjectName(any(byte[].class), name.capture());
        assertTrue(name.getValue().matches("student/applications/52/resources/pdf/\\d{4}/\\d{2}/[0-9a-f-]+\\.pdf"));
    }
}
