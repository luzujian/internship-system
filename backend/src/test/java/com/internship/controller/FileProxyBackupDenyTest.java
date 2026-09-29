package com.internship.controller;

import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.HandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class FileProxyBackupDenyTest {
    @Test
    void allGenericUploadProxiesRejectBackupKeysBeforeOssRead() {
        var controller = new FileUploadController();
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);

        var preview = request("/api/upload/preview/backups/backup_internship_20260923_120000.sql");
        assertEquals(HttpStatus.FORBIDDEN, controller.previewFile(preview).getStatusCode());

        var docx = request("/api/upload/preview-docx/school-prod/backups/backup.sql");
        assertEquals(HttpStatus.FORBIDDEN, controller.previewDocxFile(docx).getStatusCode());

        var download = request("/api/upload/download/backups/backup.sql");
        assertEquals(HttpStatus.FORBIDDEN, controller.downloadFile(download).getStatusCode());
        verifyNoInteractions(oss);
    }

    @Test
    void genericStaticProxyRejectsBackupKeysBeforeUrlConstruction() {
        var controller = new FileAccessController();
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        var request = new MockHttpServletRequest("GET", "/api/uploads/backups/backup.sql");
        var response = new MockHttpServletResponse();

        controller.accessFile(request, response);

        assertEquals(403, response.getStatus());
        verifyNoInteractions(oss);
    }

    @Test
    void genericProxiesRejectOrdinaryObjectKeysBeforeAnyOssRead() {
        var controller = new FileUploadController();
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);

        assertEquals(HttpStatus.FORBIDDEN, controller.previewFile(
                request("/api/upload/preview/students/resumes/2026/09/a.pdf")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.previewDocxFile(
                request("/api/upload/preview-docx/resources/doc/2026/09/a.docx")).getStatusCode());
        assertEquals(HttpStatus.FORBIDDEN, controller.downloadFile(
                request("/api/upload/download/images/2026/09/a.png")).getStatusCode());

        var staticController = new FileAccessController();
        ReflectionTestUtils.setField(staticController, "aliyunOSSOperator", oss);
        var response = new MockHttpServletResponse();
        staticController.accessFile(new MockHttpServletRequest("GET", "/api/uploads/resources/doc/2026/09/a.docx"), response);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(oss);
    }

    private MockHttpServletRequest request(String path) {
        var request = new MockHttpServletRequest("GET", path);
        request.setAttribute(HandlerMapping.PATH_WITHIN_HANDLER_MAPPING_ATTRIBUTE, path);
        return request;
    }
}
