package com.internship.aop;

import com.internship.anno.Log;
import com.internship.entity.OperateLog;
import com.internship.entity.Result;
import com.internship.service.AdminUserService;
import com.internship.service.AuditLogService;
import com.internship.service.CompanyUserService;
import com.internship.service.StudentUserService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import com.internship.utils.JwtUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationLogAspectTest {

    @Mock
    private AuditLogService auditLogService;
    @Mock
    private AdminUserService adminUserService;
    @Mock
    private StudentUserService studentUserService;
    @Mock
    private TeacherUserService teacherUserService;
    @Mock
    private CompanyUserService companyUserService;
    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private ProceedingJoinPoint joinPoint;
    @Mock
    private MethodSignature signature;

    private OperationLogAspect aspect;

    @BeforeEach
    void setUp() throws Exception {
        aspect = new OperationLogAspect();
        ReflectionTestUtils.setField(aspect, "auditLogService", auditLogService);
        ReflectionTestUtils.setField(aspect, "adminUserService", adminUserService);
        ReflectionTestUtils.setField(aspect, "studentUserService", studentUserService);
        ReflectionTestUtils.setField(aspect, "teacherUserService", teacherUserService);
        ReflectionTestUtils.setField(aspect, "companyUserService", companyUserService);
        ReflectionTestUtils.setField(aspect, "jwtUtils", jwtUtils);

        Method method = AuditFixture.class.getDeclaredMethod("execute", Long.class, String.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getMethod()).thenReturn(method);
        when(joinPoint.getArgs()).thenReturn(new Object[]{42L, "private-search-text"});
    }

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void exceptionIsRethrownAfterFailureAuditIsPersisted() throws Throwable {
        RuntimeException failure = new RuntimeException("boom");
        when(joinPoint.proceed()).thenThrow(failure);

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> aspect.logOperation(joinPoint));

        assertSame(failure, thrown);
        ArgumentCaptor<OperateLog> logCaptor = ArgumentCaptor.forClass(OperateLog.class);
        verify(auditLogService).record(logCaptor.capture());
        assertEquals("FAILURE", logCaptor.getValue().getOperationResult());
    }

    @Test
    void classClaimRecordsNumericTargetsAndOutcome() throws Throwable {
        var request = new com.internship.controller.TeacherClassController.ClaimClassesRequest(java.util.List.of(11L,12L));
        when(signature.getDeclaringType()).thenReturn(com.internship.controller.TeacherClassController.class);
        when(signature.getMethod()).thenReturn(com.internship.controller.TeacherClassController.class
                .getMethod("claimClasses", com.internship.controller.TeacherClassController.ClaimClassesRequest.class));
        when(joinPoint.getArgs()).thenReturn(new Object[]{request});
        when(joinPoint.proceed()).thenReturn(Result.success("认领成功",2));
        aspect.logOperation(joinPoint);
        ArgumentCaptor<OperateLog> captured = ArgumentCaptor.forClass(OperateLog.class);
        verify(auditLogService).record(captured.capture());
        assertEquals("11", captured.getValue().getTargetId());
        assertEquals("{\"classIds\":[11, 12]}", captured.getValue().getDetailJson());
        assertEquals("SUCCESS", captured.getValue().getOperationResult());
        verify(auditLogService).recordCriticalAttempt(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void nonSuccessfulHttpResponseIsPersistedAsFailure() throws Throwable {
        ResponseEntity<Void> response = ResponseEntity.internalServerError().build();
        when(joinPoint.proceed()).thenReturn(response);

        Object returned = aspect.logOperation(joinPoint);

        assertSame(response, returned);
        ArgumentCaptor<OperateLog> logCaptor = ArgumentCaptor.forClass(OperateLog.class);
        verify(auditLogService).record(logCaptor.capture());
        assertEquals("FAILURE", logCaptor.getValue().getOperationResult());
    }

    @Test
    void arbitraryStringArgumentsAreNotCopiedIntoAuditDescription() throws Throwable {
        when(joinPoint.proceed()).thenReturn(Result.success());

        aspect.logOperation(joinPoint);

        ArgumentCaptor<OperateLog> logCaptor = ArgumentCaptor.forClass(OperateLog.class);
        verify(auditLogService).record(logCaptor.capture());
        String description = logCaptor.getValue().getDescription();
        assertTrue(description.contains("id=42"));
        assertFalse(description.contains("private-search-text"));
    }

    private static class AuditFixture {
        @Log(operationType = "UPDATE", module = "TEST", description = "测试审计")
        @SuppressWarnings("unused")
        public Result execute(Long id, String privateText) {
            return Result.success();
        }
    }
}
