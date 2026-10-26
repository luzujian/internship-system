package com.internship.service;

import com.internship.utils.AuditFailureContext;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthenticationDependencyDiagnosticsTest {
    @BeforeEach void setup() {RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));}
    @AfterEach void clear() {RequestContextHolder.resetRequestAttributes();}
    @Test void redisLockFailureIsDistinguishedFromAnOrdinaryAccountLock() {
        var redis=mock(StringRedisTemplate.class);when(redis.hasKey(anyString())).thenThrow(new IllegalStateException("private-redis-password"));
        var service=new LoginAttemptService();ReflectionTestUtils.setField(service,"stringRedisTemplate",redis);
        AuditFailureContext.beginAuthentication("LOGIN");assertTrue(service.isLocked("owned-student"));
        String detail=AuditFailureContext.authenticationDetailJson();assertTrue(detail.contains("AUTH_STATE_UNAVAILABLE"));assertFalse(detail.contains("private-redis-password"));
    }
    @Test void unavailableCodeStoreIsNotReportedAsUserEnteringAnExpiredCode() {
        var service=new MailService();AuditFailureContext.beginAuthentication("RESET_VERIFY_CODE");
        assertEquals(MailService.PwdResetVerifyResult.EXPIRED,service.preVerifyPasswordResetCode("owned-student","owned@example.invalid","private-code"));
        assertTrue(AuditFailureContext.authenticationDetailJson().contains("AUTH_STATE_UNAVAILABLE"));assertFalse(AuditFailureContext.authenticationDetailJson().contains("private-code"));
    }
    @Test void diagnosticsDoNotLeakAcrossSeparateRequests() {
        AuditFailureContext.beginAuthentication("LOGIN");AuditFailureContext.authenticationFailure("ACCOUNT_LOCKED","账号已锁定");AuditFailureContext.markRecorded();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest()));
        assertFalse(AuditFailureContext.hasAuthenticationFailure());assertFalse(AuditFailureContext.isRecorded());assertNull(AuditFailureContext.authenticationDetailJson());
    }
}
