package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.*;
import com.internship.exception.BusinessException;
import com.internship.utils.AuditFailureContext;
import com.internship.utils.JwtUtils;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordResetDiagnosticsTest {
    private PasswordResetService service;
    private final StudentUserService students=mock(StudentUserService.class);
    private final AdminUserService admins=mock(AdminUserService.class);
    private final TeacherUserService teachers=mock(TeacherUserService.class);
    private final MailService mail=mock(MailService.class);
    private final UserService users=mock(UserService.class);
    private final AuditLogService audit=mock(AuditLogService.class);
    private final PasswordEncoder encoder=mock(PasswordEncoder.class);
    private StudentUser student;
    private final ObjectMapper json=new ObjectMapper();
    @BeforeEach void setup() {
        service=new PasswordResetService();
        for(var e: java.util.Map.of("studentUserService",students,"adminUserService",admins,"teacherUserService",teachers,
                "companyUserService",mock(CompanyUserService.class),"mailService",mail,"userService",users,"auditLogService",audit,
                "passwordEncoder",encoder,"jwtUtils",mock(JwtUtils.class),"loginAttemptService",mock(LoginAttemptService.class)).entrySet()) {
            ReflectionTestUtils.setField(service,e.getKey(),e.getValue());
        }
        student=new StudentUser();student.setId(77L);student.setStudentId("owned-student");student.setStatus(1);student.setEmail("bound@private.invalid");student.setPassword("private-hash");
        when(students.findByStudentId("owned-student")).thenReturn(student);
        var request=new MockHttpServletRequest("POST","/api/auth/forgot-password/verify-code");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }
    @AfterEach void clear() {RequestContextHolder.resetRequestAttributes();}
    private void fail(String code,Runnable operation) throws Exception {
        assertThrows(BusinessException.class,operation::run);
        String detail=AuditFailureContext.authenticationDetailJson();assertNotNull(detail);
        assertEquals(code,json.readTree(detail).path("failure").path("reasonCode").asText());
        assertFalse(detail.contains("@private.invalid"));assertFalse(detail.contains("private-hash"));
    }
    @Test void identityFailuresSharePublicMessageButRetainDifferentDiagnostics() throws Exception {
        for(String code:new String[]{"EMAIL_MISMATCH","EMAIL_UNBOUND","ACCOUNT_DISABLED","ACCOUNT_NOT_FOUND"}) {
            student.setEmail("bound@private.invalid");student.setStatus(1);
            String email="wrong@private.invalid";
            if(code.equals("EMAIL_UNBOUND"))student.setEmail(null);
            if(code.equals("ACCOUNT_DISABLED"))student.setStatus(0);
            if(code.equals("ACCOUNT_NOT_FOUND"))when(students.findByStudentId("owned-student")).thenReturn(null);
            var e=assertThrows(BusinessException.class,()->service.sendResetCode("owned-student",email,"127.0.0.1"));
            assertEquals(PasswordResetService.UNIFIED_AUTH_FAIL_MSG,e.getMessage());
            assertEquals(code,json.readTree(AuditFailureContext.authenticationDetailJson()).path("failure").path("reasonCode").asText());
        }
        verifyNoInteractions(mail);
    }
    @Test void queryFailureIsNotMisreportedAsMissingAccount() throws Exception {
        when(students.findByStudentId("owned-student")).thenThrow(new IllegalStateException("SQL private-hash"));
        fail("ACCOUNT_LOOKUP_FAILED",()->service.sendResetCode("owned-student","bound@private.invalid","127.0.0.1"));
        assertFalse(AuditFailureContext.authenticationDetailJson().contains("SQL"));
    }
    @Test void rateLimitAndSendFailureHaveSeparateReasons() throws Exception {
        fail("RATE_LIMITED",()->service.sendResetCode("owned-student","bound@private.invalid","127.0.0.1"));
        when(mail.checkPasswordResetRateLimit(anyString(),anyString(),anyString())).thenReturn(true);
        fail("MAIL_SEND_FAILED",()->service.sendResetCode("owned-student","bound@private.invalid","127.0.0.1"));
    }
    @Test void verifyFailureUsesActualRequestPathAndDoesNotConsumeCode() throws Exception {
        when(mail.checkPasswordResetVerifyRateLimit(anyString(),anyString(),anyString())).thenReturn(true);
        when(mail.preVerifyPasswordResetCode(anyString(),anyString(),anyString())).thenReturn(MailService.PwdResetVerifyResult.CODE_ERROR);
        fail("CODE_INVALID_OR_EXPIRED",()->service.verifyResetCode("owned-student","bound@private.invalid","private-code","127.0.0.1"));
        var c=ArgumentCaptor.forClass(SecurityEventLog.class);verify(audit).recordSecurityEvent(c.capture());var event=c.getValue();
        assertEquals("/api/auth/forgot-password/verify-code",event.getRequestPath());assertEquals("UNKNOWN",event.getUserRole());assertNull(event.getUserId());
        assertFalse(event.getDetailJson().contains("private-code"));verify(mail,never()).verifyPasswordResetCode(anyString(),anyString(),anyString());
    }
    @Test void exceededCodeAttemptsAndMissingCodeAreDistinguished() throws Exception {
        when(mail.checkPasswordResetVerifyRateLimit(anyString(),anyString(),anyString())).thenReturn(true);
        fail("CODE_REQUIRED",()->service.verifyResetCode("owned-student","bound@private.invalid","","127.0.0.1"));
        when(mail.preVerifyPasswordResetCode(anyString(),anyString(),anyString())).thenReturn(MailService.PwdResetVerifyResult.TOO_MANY_ATTEMPTS);
        fail("CODE_ATTEMPTS_EXCEEDED",()->service.verifyResetCode("owned-student","bound@private.invalid","private-code","127.0.0.1"));
    }
    @Test void resetSuccessKeepsTrustedAccountIdAndDoesNotRetainCredentials() {
        when(mail.checkPasswordResetVerifyRateLimit(anyString(),anyString(),anyString())).thenReturn(true);
        when(mail.verifyPasswordResetCode(anyString(),anyString(),anyString())).thenReturn(MailService.PwdResetVerifyResult.SUCCESS);
        service.resetPassword("owned-student","bound@private.invalid","private-code","SafeTest@2026Ab!","127.0.0.1");
        verify(users).updatePassword("owned-student","SafeTest@2026Ab!");
        var c=ArgumentCaptor.forClass(SecurityEventLog.class);verify(audit).recordSecurityEvent(c.capture());
        assertEquals("77",c.getValue().getUserId());assertEquals("ROLE_STUDENT",c.getValue().getUserRole());assertEquals("SUCCESS",c.getValue().getResult());assertNull(c.getValue().getDetailJson());
    }
}
