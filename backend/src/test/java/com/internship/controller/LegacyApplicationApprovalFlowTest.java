package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.User;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.MajorMapper;
import com.internship.mapper.PositionMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.service.CompanyUserService;
import com.internship.service.InternshipApplicationService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.JobApplicationResultNotifier;
import com.internship.service.InterviewInvitationService;
import com.internship.service.PositionService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@ExtendWith(MockitoExtension.class)
class LegacyApplicationApprovalFlowTest {

    @Mock
    private InternshipApplicationService applicationService;
    @Mock
    private PositionService positionService;
    @Mock
    private StudentInternshipStatusService internshipStatusService;
    @Mock
    private InterviewInvitationService invitationService;
    @Mock
    private CompanyUserService companyUserService;
    @Mock
    private InternshipProgressRecordService progressRecordService;
    @Mock
    private AnnouncementWebSocketHandler webSocketHandler;
    @Mock
    private JobApplicationResultNotifier jobApplicationResultNotifier;
    @Mock
    private StudentJobApplicationService jobApplicationService;
    @Mock
    private PositionMapper positionMapper;
    @Mock
    private CompanyUserMapper companyUserMapper;
    @Mock
    private StudentUserMapper studentUserMapper;
    @Mock
    private MajorMapper majorMapper;

    private CompanyController companyController;

    @BeforeEach
    void setUp() {
        companyController = new CompanyController();
        ReflectionTestUtils.setField(companyController, "internshipApplicationService", applicationService);
        ReflectionTestUtils.setField(companyController, "positionService", positionService);
        ReflectionTestUtils.setField(companyController, "studentInternshipStatusService", internshipStatusService);
        ReflectionTestUtils.setField(companyController, "interviewInvitationService", invitationService);
        ReflectionTestUtils.setField(companyController, "companyUserService", companyUserService);
        ReflectionTestUtils.setField(companyController, "progressRecordService", progressRecordService);
        ReflectionTestUtils.setField(companyController, "webSocketHandler", webSocketHandler);
        ReflectionTestUtils.setField(companyController, "jobApplicationResultNotifier", jobApplicationResultNotifier);
        ReflectionTestUtils.setField(companyController, "studentJobApplicationService", jobApplicationService);

        User company = new User();
        company.setId(200L);
        company.setRole("ROLE_COMPANY");
        CurrentHolder.setUser(company);
    }

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @CsvSource({"pending,unknown", "pending,interview_passed", "interview_passed,approved",
            "interview_passed,interview_failed", "approved,pending"})
    void companyMainReviewRejectsInvalidTransitionsBeforeAnyWrite(String current, String next) {
        StudentJobApplication application = new StudentJobApplication();
        application.setId(10L);
        application.setStudentId(400L);
        application.setPositionId(300L);
        application.setCompanyId(200L);
        application.setStatus(current);
        when(jobApplicationService.findById(10L)).thenReturn(application);

        Result result = companyController.updateJobApplicationStatus(10L, Map.of("status", next));

        assertNotEquals(200, result.getCode());
        verify(jobApplicationService, never()).updateStatusIfActive(anyLong(), any(), any());
        verify(invitationService, never()).updateStatus(anyLong(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"pending,unknown", "pending,interview_passed", "interview_passed,approved",
            "interview_passed,interview_failed", "approved,pending"})
    void companyLegacyReviewRejectsInvalidTransitionsBeforeAnyWrite(String current, String next) {
        when(applicationService.findById(10L)).thenReturn(legacyApplication(current));

        Result result = companyController.updateApplicationStatus(10L, Map.of("status", next));

        assertNotEquals(200, result.getCode());
        verify(applicationService, never()).updateStatus(anyLong(), any());
        verify(jobApplicationService, never()).updateLatestStatusIfActive(anyLong(), anyLong(), any(), any());
    }

    @ParameterizedTest
    @CsvSource({"pending,unknown", "pending,interview_passed", "interview_passed,approved",
            "interview_passed,interview_failed", "approved,pending"})
    void adminLegacyReviewRejectsInvalidTransitionsBeforeAnyWrite(String current, String next) throws Exception {
        InternshipApplicationController controller = adminController();
        when(applicationService.findById(10L)).thenReturn(legacyApplication(current));
        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(put("/api/admin/applications/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"" + next + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.not(200)));
        verify(applicationService, never()).updateStatus(anyLong(), any());
        verify(jobApplicationService, never()).updateLatestStatusIfActive(anyLong(), anyLong(), any(), any());
    }

    @Test
    void companyLegacyApprovalCreatesInterviewWithoutPendingConfirmationStatus() {
        InternshipApplicationEntity application = legacyApplication("pending");
        Position position = position();
        CompanyUser company = company();
        when(applicationService.findById(10L)).thenReturn(application);
        when(applicationService.updateStatus(10L, "approved")).thenReturn(true);
        when(positionService.findById(300L)).thenReturn(position);
        when(companyUserService.findById(200L)).thenReturn(company);
        when(jobApplicationService.findByCompanyId(200L)).thenReturn(List.of());
        when(internshipStatusService.list(
                null, null, null, null, 200L, null, null, null, null, null))
                .thenReturn(List.of());

        Result result = companyController.updateApplicationStatus(10L, Map.of("status", "approved"));

        assertEquals(200, result.getCode());
        verify(internshipStatusService, never()).findByStudentId(anyLong());
        verify(internshipStatusService, never()).insert(any());
        verify(internshipStatusService, never()).update(any());
        verify(positionService, never()).updateRecruitedCount(anyLong());
        ArgumentCaptor<InterviewInvitation> invitation = ArgumentCaptor.forClass(InterviewInvitation.class);
        verify(invitationService).create(invitation.capture());
        assertEquals("pending_interview", invitation.getValue().getStatus());
    }

    @Test
    void companyLegacyInterviewPassKeepsFinalInternshipUnchanged() {
        InternshipApplicationEntity application = legacyApplication("approved");
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setId(30L);
        when(applicationService.findById(10L)).thenReturn(application);
        when(applicationService.updateStatus(10L, "interview_passed")).thenReturn(true);
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(invitation);
        when(companyUserService.findById(200L)).thenReturn(company());
        when(jobApplicationService.findByCompanyId(200L)).thenReturn(List.of());
        when(internshipStatusService.list(
                null, null, null, null, 200L, null, null, null, null, null))
                .thenReturn(List.of());

        Result result = companyController.updateApplicationStatus(
                10L, Map.of("status", "interview_passed"));

        assertEquals(200, result.getCode());
        verify(internshipStatusService, never()).findByStudentId(anyLong());
        verify(internshipStatusService, never()).update(any());
        verify(invitationService).updateStatus(30L, "interview_passed", null);
    }

    @Test
    void companyLegacyInterviewFailureRollsBackOnlyMatchingPendingConfirmation() {
        InternshipApplicationEntity application = legacyApplication("approved");
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setId(30L);
        when(applicationService.findById(10L)).thenReturn(application);
        when(applicationService.updateStatus(10L, "interview_failed")).thenReturn(true);
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(invitation);
        when(companyUserService.findById(200L)).thenReturn(company());
        when(jobApplicationService.findByCompanyId(200L)).thenReturn(List.of());
        when(internshipStatusService.list(
                null, null, null, null, 200L, null, null, null, null, null))
                .thenReturn(List.of());

        Result result = companyController.updateApplicationStatus(
                10L, Map.of("status", "interview_failed"));

        assertEquals(200, result.getCode());
        verify(internshipStatusService)
                .rollbackPendingConfirmationForFailedApplication(400L, 300L);
        verify(invitationService).updateStatus(30L, "interview_failed", null);
    }

    @Test
    void adminLegacyApprovalCreatesInterviewWithoutPendingConfirmationStatus() throws Exception {
        InternshipApplicationController controller = adminController();
        when(applicationService.findById(10L)).thenReturn(legacyApplication("pending"));
        when(applicationService.updateStatus(10L, "approved")).thenReturn(true);
        when(positionMapper.findById(300L)).thenReturn(position());
        when(companyUserMapper.findById(200L)).thenReturn(company());

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(put("/api/admin/applications/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"approved\"}"))
                .andExpect(status().isOk());

        verify(internshipStatusService, never()).findByStudentId(anyLong());
        verify(internshipStatusService, never()).insert(any());
        verify(internshipStatusService, never()).update(any());
        ArgumentCaptor<InterviewInvitation> invitation = ArgumentCaptor.forClass(InterviewInvitation.class);
        verify(invitationService).create(invitation.capture());
        assertEquals("pending_interview", invitation.getValue().getStatus());
        verify(jobApplicationService)
                .updateLatestStatusIfActive(400L, 300L, "approved", null);
    }

    @Test
    void adminLegacyInterviewPassKeepsFinalInternshipUnchanged() throws Exception {
        InternshipApplicationController controller = adminController();
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setId(30L);
        when(applicationService.findById(10L)).thenReturn(legacyApplication("approved"));
        when(applicationService.updateStatus(10L, "interview_passed")).thenReturn(true);
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(invitation);
        when(companyUserMapper.findById(200L)).thenReturn(company());

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(put("/api/admin/applications/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interview_passed\"}"))
                .andExpect(status().isOk());

        verify(internshipStatusService, never()).findByStudentId(anyLong());
        verify(internshipStatusService, never()).update(any());
        verify(invitationService).updateStatus(30L, "interview_passed", null);
    }

    @Test
    void adminLegacyInterviewFailureRollsBackOnlyMatchingPendingConfirmation() throws Exception {
        InternshipApplicationController controller = adminController();
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setId(30L);
        when(applicationService.findById(10L)).thenReturn(legacyApplication("approved"));
        when(applicationService.updateStatus(10L, "interview_failed")).thenReturn(true);
        when(invitationService.findByStudentAndPosition(400L, 300L)).thenReturn(invitation);

        MockMvcBuilders.standaloneSetup(controller).build()
                .perform(put("/api/admin/applications/10/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"interview_failed\"}"))
                .andExpect(status().isOk());

        verify(internshipStatusService)
                .rollbackPendingConfirmationForFailedApplication(400L, 300L);
        verify(invitationService).updateStatus(30L, "interview_failed", null);
    }

    private InternshipApplicationController adminController() {
        InternshipApplicationController controller = new InternshipApplicationController();
        ReflectionTestUtils.setField(controller, "internshipApplicationService", applicationService);
        ReflectionTestUtils.setField(controller, "studentInternshipStatusService", internshipStatusService);
        ReflectionTestUtils.setField(controller, "interviewService", invitationService);
        ReflectionTestUtils.setField(controller, "progressRecordService", progressRecordService);
        ReflectionTestUtils.setField(controller, "webSocketHandler", webSocketHandler);
        ReflectionTestUtils.setField(controller, "jobApplicationResultNotifier", jobApplicationResultNotifier);
        ReflectionTestUtils.setField(controller, "studentUserMapper", studentUserMapper);
        ReflectionTestUtils.setField(controller, "positionMapper", positionMapper);
        ReflectionTestUtils.setField(controller, "companyUserMapper", companyUserMapper);
        ReflectionTestUtils.setField(controller, "majorMapper", majorMapper);
        ReflectionTestUtils.setField(controller, "studentJobApplicationService", jobApplicationService);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "admin", "password", List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        return controller;
    }

    private InternshipApplicationEntity legacyApplication(String status) {
        InternshipApplicationEntity application = new InternshipApplicationEntity();
        application.setId(10L);
        application.setStudentId(400L);
        application.setStudentUserId(null);
        application.setPositionId(300L);
        application.setPositionName("Java实习生");
        application.setCompanyId(200L);
        application.setStudentName("测试学生");
        application.setStatus(status);
        return application;
    }

    private Position position() {
        Position position = new Position();
        position.setId(300L);
        position.setCompanyId(200L);
        position.setPositionName("Java实习生");
        return position;
    }

    private CompanyUser company() {
        CompanyUser company = new CompanyUser();
        company.setId(200L);
        company.setCompanyName("测试企业");
        return company;
    }
}
