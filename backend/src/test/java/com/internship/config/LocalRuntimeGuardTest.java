package com.internship.config;

import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.controller.FileAccessController;
import com.internship.scheduler.InternshipStatusScheduler;
import com.internship.service.InternshipLifecycleService;
import com.internship.service.MailService;
import com.internship.service.impl.BackupServiceImpl;
import com.internship.service.impl.EmailServiceImpl;
import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.config.TaskManagementConfigUtils;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class LocalRuntimeGuardTest {
    public static class PeriodicService {
        @Scheduled(initialDelay=3_600_000,fixedDelay=3_600_000) public void background() { throw new AssertionError("unexpected background execution"); }
        public String manualOperation() { return "available"; }
    }

    @Test void localDisablesEveryAnnotationScheduleWhilePreservingManualServiceMethods() {
        try(var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.register(SchedulingConfig.class, PeriodicService.class);
            context.refresh();
            assertFalse(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
            assertEquals("available",context.getBean(PeriodicService.class).manualOperation());
        }
        assertFalse(SystemSettingsConfig.class.isAnnotationPresent(EnableScheduling.class), "a second enabler would bypass local isolation");
    }

    @Test void productionStillRegistersAnnotationScheduler() {
        try(var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod");
            context.register(SchedulingConfig.class, PeriodicService.class);
            context.refresh();
            assertTrue(context.containsBean(TaskManagementConfigUtils.SCHEDULED_ANNOTATION_PROCESSOR_BEAN_NAME));
        }
    }

    @Test void localDoesNotRegisterStartupLifecycleRunner() {
        try(var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("local");
            context.registerBean(StudentInternshipStatusMapper.class, () -> mock(StudentInternshipStatusMapper.class));
            context.registerBean(InternshipLifecycleService.class, () -> mock(InternshipLifecycleService.class));
            context.register(InternshipStatusScheduler.class);
            context.refresh();
            assertTrue(context.getBeansOfType(InternshipStatusScheduler.class).isEmpty());
        }
    }

    @Test void localBlocksMailBeforeWritingCodesOrCreatingSender() {
        var mail = guarded(new MailService());
        assertFalse(mail.sendVerifyCode("valid@example.test"));
        assertFalse(mail.sendPasswordResetCode("student", "valid@example.test"));
        var email = guarded(new EmailServiceImpl());
        assertFalse(email.isConfigured());
        assertFalse(email.sendEmail("valid@example.test", "test", "test"));
        assertFalse(email.sendTestEmail());
    }

    @Test void localBackupInitSkipsRemoteAndDatabaseSynchronization() {
        assertDoesNotThrow(() -> guarded(new BackupServiceImpl()).init());
    }

    @Test void localProxyAppliesTheSameGenericObjectDeny() {
        var response = new MockHttpServletResponse();
        var controller = new FileAccessController();
        var oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        controller.accessFile(new MockHttpServletRequest("GET", "/api/uploads/file.pdf"), response);
        assertEquals(403, response.getStatus());
        verifyNoInteractions(oss);
    }

    @Test void productionDoesNotAcquireLocalRestrictions() {
        var environment = new MockEnvironment();environment.setActiveProfiles("prod");
        var guard = new LocalRuntimeGuard(environment);
        assertFalse(guard.isLocal());
        assertDoesNotThrow(() -> guard.requireExternalService("对象存储"));
    }

    private <T> T guarded(T object) {
        var environment = new MockEnvironment();environment.setActiveProfiles("local");
        ReflectionTestUtils.setField(object, "localRuntimeGuard", new LocalRuntimeGuard(environment));
        return object;
    }
}
