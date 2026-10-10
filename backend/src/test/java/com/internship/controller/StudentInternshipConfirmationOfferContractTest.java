package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.User;
import com.internship.service.CompanyUserService;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipConfirmationWorkflowService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.PositionService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.StudentUserService;
import com.internship.service.UserService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentInternshipConfirmationOfferContractTest {
    @Mock private UserService userService;
    @Mock private StudentUserService studentUserService;
    @Mock private InternshipConfirmationRecordService confirmationRecordService;
    @Mock private InternshipConfirmationWorkflowService workflowService;
    @Mock private InternshipStageAssignmentService assignmentService;
    @Mock private InterviewInvitationService invitationService;
    @Mock private PositionService positionService;
    @Mock private CompanyUserService companyUserService;
    @Mock private StudentInternshipStatusService statusService;
    @Mock private StudentJobApplicationService applicationService;
    @Mock private AnnouncementWebSocketHandler webSocketHandler;

    private StudentInternshipConfirmationController controller;

    @BeforeEach
    void setUp() {
        controller = new StudentInternshipConfirmationController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "studentUserService", studentUserService);
        ReflectionTestUtils.setField(controller, "confirmationRecordService", confirmationRecordService);
        ReflectionTestUtils.setField(controller, "confirmationWorkflowService", workflowService);
        ReflectionTestUtils.setField(controller, "internshipStageAssignmentService", assignmentService);
        ReflectionTestUtils.setField(controller, "interviewInvitationService", invitationService);
        ReflectionTestUtils.setField(controller, "positionService", positionService);
        ReflectionTestUtils.setField(controller, "companyUserService", companyUserService);
        ReflectionTestUtils.setField(controller, "internshipStatusService", statusService);
        ReflectionTestUtils.setField(controller, "studentJobApplicationService", applicationService);
        ReflectionTestUtils.setField(controller, "webSocketHandler", webSocketHandler);

        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User("s001", "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        User student = new User();
        student.setId(42L);
        student.setUsername("s001");
        when(userService.findByUsername("s001")).thenReturn(student);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void offersAreInvitationBackedAndDoNotRequireApplicationRows() {
        InterviewInvitation offer = offer(101L);
        when(confirmationRecordService.findByStudentId(42L)).thenReturn(List.of());
        when(assignmentService.findByStudentId(42L)).thenReturn(List.of());
        when(invitationService.findByStudentIdAndStatus(42L, "interview_passed"))
                .thenReturn(List.of(offer));
        when(applicationService.findByStudentId(42L)).thenReturn(List.of());
        when(positionService.findById(30L)).thenReturn(position());
        when(companyUserService.findById(20L)).thenReturn(company());

        Result result = controller.getConfirmableOffers();

        assertEquals(200, result.getCode());
        Map<?, ?> item = (Map<?, ?>) ((List<?>) result.getData()).get(0);
        assertEquals(101L, item.get("offerId"));
        assertEquals(101L, item.get("invitationId"));
        assertEquals("INTERVIEW_INVITATION", item.get("sourceType"));
        assertEquals(101L, item.get("sourceId"));
        assertNull(item.get("applicationId"));
        assertFalse(item.containsKey("stageNo"));
    }

    @Test
    void submissionReturnsStableSourceAndOptionalApplicationId() {
        InterviewInvitation offer = offer(101L);
        StudentJobApplication application = new StudentJobApplication();
        application.setId(501L);
        application.setCompanyId(20L);
        application.setPositionId(30L);
        application.setStatus("interview_passed");
        InternshipStageAssignment placement = new InternshipStageAssignment();
        placement.setId(90L);
        when(invitationService.findById(101L)).thenReturn(offer);
        when(positionService.findById(30L)).thenReturn(position());
        when(companyUserService.findById(20L)).thenReturn(company());
        when(applicationService.findByStudentId(42L)).thenReturn(List.of(application));
        doAnswer(invocation -> {
            InternshipConfirmationRecord record = invocation.getArgument(0);
            record.setId(70L);
            return record;
        }).when(workflowService).submitOffer(any(InternshipConfirmationRecord.class), eq(101L), eq(501L));
        when(assignmentService.findByConfirmationRecordId(70L)).thenReturn(placement);
        when(applicationService.findByCompanyId(20L)).thenReturn(List.of());
        when(confirmationRecordService.findByCompanyIdAndStatus(20L, 0)).thenReturn(List.of());

        Result result = controller.submitOffer(Map.of("offerId", 101L, "remark", "接受", "agreementFileIds", List.of(601L)));

        assertEquals(200, result.getCode());
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertEquals(70L, data.get("recordId"));
        assertEquals(90L, data.get("placementId"));
        assertEquals(101L, data.get("invitationId"));
        assertEquals(501L, data.get("applicationId"));
        assertEquals("INTERVIEW_INVITATION", data.get("sourceType"));
        verify(workflowService).submitOffer(any(InternshipConfirmationRecord.class),
                eq(101L), eq(501L));
    }

    private InterviewInvitation offer(Long id) {
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setId(id);
        invitation.setStudentId(42L);
        invitation.setCompanyId(20L);
        invitation.setPositionId(30L);
        invitation.setStatus("interview_passed");
        return invitation;
    }

    private Position position() {
        Position position = new Position();
        position.setId(30L);
        position.setCompanyId(20L);
        position.setPositionName("Java实习生");
        return position;
    }

    private CompanyUser company() {
        CompanyUser company = new CompanyUser();
        company.setId(20L);
        company.setCompanyName("甲公司");
        return company;
    }
}
