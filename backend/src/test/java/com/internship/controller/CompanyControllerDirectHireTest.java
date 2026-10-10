package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.User;
import com.internship.entity.dto.StudentPositionKey;
import com.internship.service.DirectHireService;
import com.internship.service.InternshipApplicationService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.JobApplicationResultNotification;
import com.internship.service.JobApplicationResultNotifier;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerDirectHireTest {

    @Mock
    private DirectHireService directHireService;
    @Mock
    private StudentJobApplicationService applicationService;
    @Mock
    private StudentInternshipStatusService internshipStatusService;
    @Mock
    private InternshipApplicationService mirrorApplicationService;
    @Mock
    private InternshipProgressRecordService progressRecordService;
    @Mock
    private AnnouncementWebSocketHandler webSocketHandler;
    @Mock
    private JobApplicationResultNotifier jobApplicationResultNotifier;

    private CompanyController controller;

    @BeforeEach
    void setUp() {
        controller = new CompanyController();
        ReflectionTestUtils.setField(controller, "directHireService", directHireService);
        ReflectionTestUtils.setField(controller, "studentJobApplicationService", applicationService);
        ReflectionTestUtils.setField(controller, "studentInternshipStatusService", internshipStatusService);
        ReflectionTestUtils.setField(controller, "internshipApplicationService", mirrorApplicationService);
        ReflectionTestUtils.setField(controller, "progressRecordService", progressRecordService);
        ReflectionTestUtils.setField(controller, "webSocketHandler", webSocketHandler);
        ReflectionTestUtils.setField(controller, "jobApplicationResultNotifier", jobApplicationResultNotifier);

        User company = new User();
        company.setId(200L);
        company.setRole("ROLE_COMPANY");
        CurrentHolder.setUser(company);
    }

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
    }

    @Test
    void directHireReturnsDisplayMarkerAndPushesStudentRefreshEvents() {
        DirectHireService.DirectHireResult serviceResult = new DirectHireService.DirectHireResult(
                100L, 500L, 400L, 200L, 300L,
                "测试企业", "Java开发实习生", true);
        when(directHireService.directHire(200L, 100L)).thenReturn(serviceResult);
        when(applicationService.findByCompanyId(200L)).thenReturn(List.of());
        when(internshipStatusService.list(
                null, null, null, null, 200L, null, null, null, null, null))
                .thenReturn(List.of());

        Result result = controller.directHire(100L);

        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(true, data.get("directHire"));
        assertEquals("直接录取（免面试）", data.get("statusText"));

        ArgumentCaptor<Map<String, Object>> applicationEvent = ArgumentCaptor.forClass(Map.class);
        verify(webSocketHandler).sendApplicationStatusToUser(
                org.mockito.ArgumentMatchers.eq(400L), applicationEvent.capture());
        assertEquals("interview_passed", applicationEvent.getValue().get("status"));
        assertEquals(true, applicationEvent.getValue().get("directHire"));
        assertEquals("直接录取（免面试）", applicationEvent.getValue().get("statusText"));

        ArgumentCaptor<Map<String, Object>> interviewEvent = ArgumentCaptor.forClass(Map.class);
        verify(webSocketHandler).sendInterviewCreateToUser(
                org.mockito.ArgumentMatchers.eq(400L), interviewEvent.capture());
        assertEquals(500L, interviewEvent.getValue().get("id"));
        assertEquals("interview_passed", interviewEvent.getValue().get("status"));
        assertTrue((Boolean) interviewEvent.getValue().get("directHire"));

        ArgumentCaptor<JobApplicationResultNotification> emailNotification =
                ArgumentCaptor.forClass(JobApplicationResultNotification.class);
        verify(jobApplicationResultNotifier).afterCommit(emailNotification.capture());
        assertEquals(100L, emailNotification.getValue().applicationId());
        assertEquals(400L, emailNotification.getValue().studentId());
        assertEquals("interview_passed", emailNotification.getValue().status());
        assertTrue(emailNotification.getValue().directHire());

        verify(webSocketHandler).sendCompanyTodoUpdate(200L, 0L, 0L);
        verify(mirrorApplicationService)
                .updateLatestStatusByStudentAndPosition(400L, 300L, "interview_passed");
    }

    @Test
    void normalAuditUpdatesMainAndLatestMirrorStatus() {
        StudentJobApplication application = new StudentJobApplication();
        application.setId(100L);
        application.setStudentId(400L);
        application.setStudentName("测试学生");
        application.setPositionId(300L);
        application.setPositionName("Java开发实习生");
        application.setCompanyId(200L);
        application.setCompanyName("测试企业");
        application.setStatus("pending");
        when(applicationService.findById(100L)).thenReturn(application);
        when(applicationService.updateStatusIfActive(
                100L, "rejected", "专业背景与岗位要求不匹配")).thenReturn(1);
        when(mirrorApplicationService.updateLatestStatusByStudentAndPosition(
                400L, 300L, "rejected")).thenReturn(true);
        when(applicationService.findByCompanyId(200L)).thenReturn(List.of());
        when(internshipStatusService.list(
                null, null, null, null, 200L, null, null, null, null, null))
                .thenReturn(List.of());

        Result result = controller.updateJobApplicationStatus(
                100L, Map.of("status", "rejected", "rejectReason", "专业背景与岗位要求不匹配"));

        assertEquals(200, result.getCode());
        verify(applicationService)
                .updateStatusIfActive(100L, "rejected", "专业背景与岗位要求不匹配");
        verify(mirrorApplicationService)
                .updateLatestStatusByStudentAndPosition(400L, 300L, "rejected");
    }

    @Test
    void rejectionRequiresReason() {
        StudentJobApplication application = new StudentJobApplication();
        application.setId(100L);
        application.setStudentId(400L);
        application.setPositionId(300L);
        application.setCompanyId(200L);
        application.setStatus("pending");
        when(applicationService.findById(100L)).thenReturn(application);

        Result result = controller.updateJobApplicationStatus(100L, Map.of("status", "rejected"));

        assertEquals(400, result.getCode());
        assertTrue(result.getMessage().contains("拒绝学生申请时必须填写拒绝理由"));
        verify(applicationService, never()).updateStatusIfActive(anyLong(), any(), any());
    }

    @Test
    void companyApplicationListMarksDirectHireApplications() {
        StudentJobApplication application = new StudentJobApplication();
        application.setId(100L);
        application.setStudentId(400L);
        application.setPositionId(300L);
        application.setCompanyId(200L);
        application.setStatus("interview_passed");
        when(applicationService.findByCompanyId(200L)).thenReturn(List.of(application));
        when(directHireService.findDirectHirePairs(List.of(new StudentPositionKey(400L, 300L))))
                .thenReturn(Set.of(new StudentPositionKey(400L, 300L)));

        Result result = controller.getJobApplications();
        StudentJobApplication returned = ((List<StudentJobApplication>) result.getData()).get(0);

        assertTrue(returned.getDirectHire());
        verify(directHireService, never()).isDirectHire(any(), any());
    }
}
