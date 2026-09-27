package com.internship.controller;

import com.internship.entity.InterviewInvitation;
import com.internship.entity.Result;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.User;
import com.internship.service.DirectHireService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.UserService;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDirectHirePresentationTest {

    @Mock
    private UserService userService;
    @Mock
    private InterviewInvitationService invitationService;
    @Mock
    private StudentJobApplicationService applicationService;
    @Mock
    private DirectHireService directHireService;

    private User student;

    @BeforeEach
    void setUp() {
        org.springframework.security.core.userdetails.User principal =
                new org.springframework.security.core.userdetails.User(
                        "student", "password", List.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

        student = new User();
        student.setId(400L);
        student.setUsername("student");
        when(userService.findByUsername("student")).thenReturn(student);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void interviewListMarksDirectHireInvitations() {
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setId(500L);
        invitation.setStudentId(400L);
        invitation.setPositionId(300L);
        invitation.setInterviewMethod("direct_hire");
        invitation.setStatus("interview_passed");
        when(invitationService.findByStudentId(400L)).thenReturn(List.of(invitation));
        when(directHireService.isDirectHire(400L, 300L)).thenReturn(true);

        StudentInterviewController controller = new StudentInterviewController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "interviewInvitationService", invitationService);
        ReflectionTestUtils.setField(controller, "directHireService", directHireService);

        Result result = controller.list(null);
        InterviewInvitation returned = ((List<InterviewInvitation>) result.getData()).get(0);

        assertTrue(returned.getDirectHire());
    }

    @Test
    void applicationListMarksDirectHireApplications() {
        StudentJobApplication application = new StudentJobApplication();
        application.setId(100L);
        application.setStudentId(400L);
        application.setPositionId(300L);
        application.setStatus("interview_passed");
        when(applicationService.findByStudentId(400L)).thenReturn(List.of(application));
        when(directHireService.isDirectHire(400L, 300L)).thenReturn(true);

        StudentJobApplicationController controller = new StudentJobApplicationController();
        ReflectionTestUtils.setField(controller, "userService", userService);
        ReflectionTestUtils.setField(controller, "applicationService", applicationService);
        ReflectionTestUtils.setField(controller, "directHireService", directHireService);

        Result result = controller.list();
        StudentJobApplication returned = ((List<StudentJobApplication>) result.getData()).get(0);

        assertTrue(returned.getDirectHire());
    }
}
