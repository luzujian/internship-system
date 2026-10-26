package com.internship.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.anno.Log;
import com.internship.aop.OperationLogAspect;
import com.internship.entity.*;
import com.internship.service.AuditLogService;
import com.internship.utils.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RequestFailureAuditTest {
    private final AuditLogService audit=mock(AuditLogService.class);
    private final GlobalExceptionHandler handler=new GlobalExceptionHandler();
    @BeforeEach void setup() { ReflectionTestUtils.setField(handler,"auditLogService",audit);CurrentHolder.remove(); }
    @AfterEach void clear() {RequestContextHolder.resetRequestAttributes();CurrentHolder.remove();}
    @RestController public static class Fixture {
        @GetMapping("/api/owned/fail") @Log(operationType="READ",module="TEST",description="测试异常")
        public Result fail() {throw new IllegalStateException("SQL password=private-secret");}
    }
    @Test void aspectAndFallbackDoNotDuplicateTheSameFailure() throws Exception {
        var aspect=new OperationLogAspect();ReflectionTestUtils.setField(aspect,"auditLogService",audit);
        var factory=new AspectJProxyFactory(new Fixture());factory.setProxyTargetClass(true);factory.addAspect(aspect);
        var mvc=MockMvcBuilders.standaloneSetup((Object) factory.getProxy()).setControllerAdvice(handler).build();
        mvc.perform(get("/api/owned/fail")).andExpect(status().isInternalServerError());
        verify(audit).record(any());verify(audit,never()).recordSecurityEvent(any());
    }
    @Test void uploadRejectedBeforeControllerHasSizeAndTrustedIdentityButNoFilename() throws Exception {
        var request=new MockHttpServletRequest("POST","/api/oss/upload");request.setContent(new byte[123]);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        var user=new User();user.setId(77L);user.setUsername("owned-student");user.setRole("ROLE_STUDENT");CurrentHolder.setUser(user);
        var result=handler.handleMaxUploadSizeExceededException(new MaxUploadSizeExceededException(20));assertEquals(413,result.getStatusCode().value());
        var c=ArgumentCaptor.forClass(SecurityEventLog.class);verify(audit).recordSecurityEvent(c.capture());var row=c.getValue();
        assertEquals("77",row.getUserId());assertEquals("ROLE_STUDENT",row.getUserRole());assertEquals("413",row.getErrorCode());
        var failure=new ObjectMapper().readTree(row.getDetailJson()).path("failure");
        assertEquals(123,failure.path("requestSize").asInt());assertEquals("MULTIPART_PARSE",failure.path("validationStage").asText());
    }
    @Test void unknownExceptionKeepsOnlySafeReasonAndExceptionType() {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest("GET","/api/owned/fail")));
        var result=handler.handleRuntimeException(new IllegalStateException("SQL password=private-secret"));
        var c=ArgumentCaptor.forClass(SecurityEventLog.class);verify(audit).recordSecurityEvent(c.capture());
        assertFalse(result.getBody().getMessage().contains("private-secret"));assertFalse(c.getValue().getDetailJson().contains("private-secret"));
        assertTrue(c.getValue().getDetailJson().contains("IllegalStateException"));
    }
}
