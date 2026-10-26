package com.internship.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.OperateLog;
import com.internship.entity.Result;
import com.internship.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.*;

class AuditFailureContextTest {
    private final ObjectMapper json = new ObjectMapper();
    private OperateLog failed() { OperateLog row = new OperateLog(); row.setOperationResult("FAILURE"); return row; }
    @AfterEach void clear() { RequestContextHolder.resetRequestAttributes(); }

    @Test void preservesBusinessCodeAndClassContext() throws Exception {
        var row = failed(); row.setDetailJson("{\"classIds\":[166]}");
        AuditFailureContext.apply(row, new Result(422, "请上传相关材料", null), null, new Object[0]);
        assertEquals("422", row.getErrorCode());
        var detail = json.readTree(row.getDetailJson());
        assertEquals(166, detail.path("classIds").get(0).intValue());
        assertEquals("请上传相关材料", detail.path("failure").path("reason").asText());
    }

    @Test void recordsHttpStatusWithoutSerializingResponsePayload() {
        var row = failed();
        AuditFailureContext.apply(row, ResponseEntity.status(409).body(Result.error("申请已经处理")), null, null);
        assertEquals("409", row.getErrorCode()); assertTrue(row.getDetailJson().contains("申请已经处理"));
    }

    @Test void expectedExceptionsKeepSafeReasonButUnknownExceptionsKeepOnlyTheirType() {
        var row = failed();
        AuditFailureContext.apply(row, null, new BusinessException("申请已处理"), null);
        assertEquals("BUSINESS_EXCEPTION", row.getErrorCode()); assertTrue(row.getDetailJson().contains("申请已处理"));
        AuditFailureContext.apply(row, null, new IllegalStateException("SQL password=secret-value"), null);
        assertEquals("EXCEPTION", row.getErrorCode()); assertTrue(row.getDetailJson().contains("IllegalStateException"));
        assertFalse(row.getDetailJson().contains("secret-value")); assertFalse(row.getDetailJson().contains("SQL"));
    }

    @Test void uploadDiagnosticsHaveNoFullFilenameOrBytes() throws Exception {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        AuditFailureContext.uploadStage("MEDIA_TYPE"); var row = failed();
        var file = new MockMultipartFile("file", "private-student-name.PDF", "application/octet-stream", new byte[]{1,2,3});
        AuditFailureContext.apply(row, Result.error("不支持的文件格式"), null, new Object[]{file});
        var detail = json.readTree(row.getDetailJson()).path("failure");
        assertEquals("MEDIA_TYPE", detail.path("validationStage").asText());
        assertEquals(3, detail.path("files").get(0).path("size").intValue());
        assertEquals("pdf", detail.path("files").get(0).path("extension").asText());
        assertFalse(row.getDetailJson().contains("private-student-name"));
    }

    @Test void redactsCredentialsPersonalDataAndInternalErrorsAndBoundsReason() {
        String value = AuditFailureContext.sanitizeReason("password=secret-password token=secret-token a@private.invalid 13812345678 https://private.invalid/a?key=secret-url");
        for (String secret : new String[]{"secret-password", "secret-token", "a@private.invalid", "13812345678", "secret-url"}) assertFalse(value.contains(secret), secret);
        assertFalse(AuditFailureContext.sanitizeReason("添加失败 Unknown column 'private_column' in field list").contains("private_column"));
        assertEquals(512, AuditFailureContext.sanitizeReason("因".repeat(1000)).length());
    }

    @Test void successIsNotReclassifiedAsFailure() {
        var row = new OperateLog(); row.setOperationResult("SUCCESS");
        AuditFailureContext.apply(row, Result.success(), null, null);
        assertNull(row.getErrorCode()); assertNull(row.getDetailJson());
    }
}
