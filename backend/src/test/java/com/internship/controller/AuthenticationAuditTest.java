package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.aop.OperationLogAspect;
import com.internship.entity.*;
import com.internship.exception.GlobalExceptionHandler;
import com.internship.service.*;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import org.springframework.web.context.request.RequestContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthenticationAuditTest {
    private final AuditLogService audit = mock(AuditLogService.class);
    private final TeacherUserService teachers = mock(TeacherUserService.class);
    private final UserService users = mock(UserService.class);
    private final StudentUserService students = mock(StudentUserService.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private MockMvc mvc;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach void setup() {
        var reset = new PasswordResetService();
        ReflectionTestUtils.setField(reset,"teacherUserService",teachers);
        ReflectionTestUtils.setField(reset,"studentUserService",students);
        ReflectionTestUtils.setField(reset,"adminUserService",mock(AdminUserService.class));
        ReflectionTestUtils.setField(reset,"companyUserService",mock(CompanyUserService.class));
        var controller = new AuthController();
        for (var entry : java.util.Map.of("auditLogService",audit,"teacherUserService",teachers,"userService",users,
                "studentUserService",students,"loginAttemptService",attempts,"passwordEncoder",encoder,"passwordResetService",reset).entrySet()) {
            ReflectionTestUtils.setField(controller,entry.getKey(),entry.getValue());
        }
        var aspect = new OperationLogAspect();
        ReflectionTestUtils.setField(aspect,"auditLogService",audit);
        ReflectionTestUtils.setField(aspect,"passwordResetService",reset);
        var factory = new AspectJProxyFactory(controller); factory.setProxyTargetClass(true); factory.addAspect(aspect);
        var handler = new GlobalExceptionHandler(); ReflectionTestUtils.setField(handler,"auditLogService",audit);
        mvc=MockMvcBuilders.standaloneSetup((Object) factory.getProxy()).setControllerAdvice(handler).build();
        CurrentHolder.remove();
    }
    @AfterEach void clear() { CurrentHolder.remove(); RequestContextHolder.resetRequestAttributes(); }
    private void login(String body) throws Exception { mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andExpect(jsonPath("$.code").value(400)); }
    private LoginLog loginRow() { var c=ArgumentCaptor.forClass(LoginLog.class);verify(audit).recordLoginLog(c.capture());return c.getValue(); }
    private void reason(LoginLog row,String code) throws Exception {
        assertEquals("FAILURE",row.getLoginStatus()); assertEquals("UNKNOWN",row.getUserType()); assertNull(row.getUserId());
        assertEquals(code,json.readTree(row.getDetailJson()).path("failure").path("reasonCode").asText());
    }
    @Test void lockedRequestIsAuditedWithoutRetryingAuthentication() throws Exception {
        when(attempts.isLocked("owned-student")).thenReturn(true); when(attempts.getRemainingLockTime("owned-student")).thenReturn(30L);
        login("{\"username\":\"owned-student\",\"password\":\"private-password\"}");
        reason(loginRow(),"ACCOUNT_LOCKED"); verifyNoInteractions(users,teachers,encoder);verify(attempts,never()).recordFailure(anyString());
        var event=ArgumentCaptor.forClass(SecurityEventLog.class);verify(audit).recordSecurityEvent(event.capture());assertEquals("ACCOUNT_LOCK",event.getValue().getEventType());
    }
    @Test void missingPasswordIsRecordedWithoutSerializingInput() throws Exception {
        login("{\"username\":\"owned-student\"}"); reason(loginRow(),"PASSWORD_REQUIRED");verifyNoInteractions(attempts,users);
    }
    @Test void missingUsernameHasAnAuditRow() throws Exception {
        login("{\"password\":\"private-password\"}"); reason(loginRow(),"USERNAME_REQUIRED");assertFalse(loginRow().getDetailJson().contains("private-password"));
    }
    @Test void incorrectCredentialsHavePrivateDiagnosticAndUnverifiedIdentity() throws Exception {
        User user=new User();user.setId(77L);user.setUsername("owned-student");user.setRole("ROLE_STUDENT");user.setPassword("private-hash");
        when(users.findByUsername("owned-student")).thenReturn(user);when(attempts.recordFailure("owned-student")).thenReturn(1);
        login("{\"username\":\"owned-student\",\"password\":\"private-password\"}");reason(loginRow(),"PASSWORD_MISMATCH");
        assertFalse(loginRow().getDetailJson().contains("private-password"));assertFalse(loginRow().getDetailJson().contains("private-hash"));
    }
    @Test void counterFailureStillLeavesAuditableLoginFailure() throws Exception {
        when(attempts.recordFailure("owned-student")).thenThrow(new IllegalStateException("SQL password=private-password"));
        login("{\"username\":\"owned-student\",\"password\":\"private-password\"}");reason(loginRow(),"AUTH_STATE_UNAVAILABLE");
        assertFalse(loginRow().getDetailJson().contains("SQL"));assertFalse(loginRow().getDetailJson().contains("private-password"));
    }
    @Test void unexpectedLookupFailureDoesNotLeakInternalMessage() throws Exception {
        when(teachers.findByTeacherUserId("owned-student")).thenThrow(new IllegalStateException("SQL password=private-password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"owned-student\",\"password\":\"private-password\"}"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("private-password"))));
        reason(loginRow(),"INTERNAL_ERROR");
        var c=ArgumentCaptor.forClass(SecurityEventLog.class);verify(audit).recordSecurityEvent(c.capture());assertEquals("AUTH_SERVICE_FAILURE",c.getValue().getEventType());
    }
    @Test void forgotPasswordFailureUsesRealAspectAndKeepsPublicAntiEnumerationMessage() throws Exception {
        StudentUser student=new StudentUser();student.setId(77L);student.setStudentId("owned-student");student.setEmail("bound@private.invalid");student.setStatus(1);
        when(students.findByStudentId("owned-student")).thenReturn(student);
        mvc.perform(post("/api/auth/forgot-password/send-code").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"owned-student\",\"email\":\"wrong@private.invalid\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value(PasswordResetService.UNIFIED_AUTH_FAIL_MSG));
        var c=ArgumentCaptor.forClass(OperateLog.class);verify(audit).record(c.capture());var row=c.getValue();
        assertEquals("SEND_CODE",row.getOperationType());assertEquals("FAILURE",row.getOperationResult());assertEquals("400",row.getErrorCode());
        var failure=json.readTree(row.getDetailJson()).path("failure");assertEquals("EMAIL_MISMATCH",failure.path("reasonCode").asText());
        assertEquals("RESET_SEND_CODE",failure.path("validationStage").asText());assertFalse(row.getDetailJson().contains("@private.invalid"));
    }
    @Test void malformedBodyBeforeControllerProducesOneRequestEvent() throws Exception {
        mvc.perform(post("/api/auth/forgot-password/send-code").contentType(MediaType.APPLICATION_JSON).content("not-json"))
                .andExpect(status().isBadRequest());
        verify(audit,never()).record(any()); var c=ArgumentCaptor.forClass(SecurityEventLog.class);verify(audit).recordSecurityEvent(c.capture());
        assertEquals("REQUEST_FAILURE",c.getValue().getEventType());assertEquals("400",c.getValue().getErrorCode());
        assertFalse(c.getValue().getDetailJson().contains("not-json"));
    }
}
