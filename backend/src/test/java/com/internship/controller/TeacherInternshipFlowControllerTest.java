package com.internship.controller;

import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TeacherInternshipFlowControllerTest {
    @Test
    void legacyStageRouteIsRetiredAndPlacementViewHasNoStageField() {
        InternshipStageAssignmentService service = mock(InternshipStageAssignmentService.class);
        TeacherUserMapper teachers = mock(TeacherUserMapper.class);
        TeacherAutomationScopeMapper scopes = mock(TeacherAutomationScopeMapper.class);
        TeacherUser teacher = new TeacherUser();
        teacher.setId(3L);
        teacher.setStatus(1);
        teacher.setTeacherType("COUNSELOR");
        when(teachers.findById(3L)).thenReturn(teacher);
        when(scopes.studentIds(teacher)).thenReturn(List.of(7L));
        InternshipStageAssignment assignment = new InternshipStageAssignment();
        assignment.setId(9L);
        assignment.setStudentId(7L);
        assignment.setStageNo(2);
        assignment.setSequenceNo(3);
        assignment.setStatus("IN_PROGRESS");
        assignment.setCurrentFlag(1);
        InternshipStageAssignment outsideScope = new InternshipStageAssignment();
        outsideScope.setId(10L);
        outsideScope.setStudentId(8L);
        when(service.findAll()).thenReturn(List.of(assignment, outsideScope));
        TeacherInternshipFlowController controller = new TeacherInternshipFlowController(service);
        ReflectionTestUtils.setField(controller, "teacherUserMapper", teachers);
        ReflectionTestUtils.setField(controller, "teacherScopeMapper", scopes);
        User current = new User();
        current.setId(3L);
        CurrentHolder.setUser(current);

        try {
            assertEquals(400, controller.listLegacyStageAssignments().getCode());
            Result result = controller.listPlacements();

            assertEquals(200, result.getCode());
            assertEquals(1, ((List<?>) result.getData()).size());
            Object view = ((List<?>) result.getData()).get(0);
            assertEquals(3, ((TeacherInternshipFlowController.PlacementView) view).sequenceNo());
            assertFalse(List.of(view.getClass().getRecordComponents()).stream()
                    .anyMatch(component -> "stageNo".equals(component.getName())));
        } finally {
            CurrentHolder.remove();
        }
    }

    @Test
    void placementsRejectMissingTeacherBeforeReadingAnyRecords() {
        InternshipStageAssignmentService service = mock(InternshipStageAssignmentService.class);
        TeacherInternshipFlowController controller = new TeacherInternshipFlowController(service);
        ReflectionTestUtils.setField(controller, "teacherUserMapper", mock(TeacherUserMapper.class));
        CurrentHolder.remove();
        assertThrows(AccessDeniedException.class, controller::listPlacements);
        verifyNoInteractions(service);
    }
}
