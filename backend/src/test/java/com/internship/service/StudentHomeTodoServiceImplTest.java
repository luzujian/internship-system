package com.internship.service;

import com.internship.entity.InternshipAppraisalSubmission;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipReflection;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.entity.dto.StudentTodoItemDTO;
import com.internship.service.impl.StudentHomeTodoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentHomeTodoServiceImplTest {
    private static final Long STUDENT_ID = 42L;
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock private InterviewInvitationService interviewInvitationService;
    @Mock private StudentJobApplicationService jobApplicationService;
    @Mock private InternshipConfirmationRecordService confirmationRecordService;
    @Mock private InternshipStageAssignmentService stageAssignmentService;
    @Mock private InternshipCalendarService calendarService;
    @Mock private InternshipReflectionService reflectionService;
    @Mock private InternshipAppraisalService appraisalService;
    @Mock private SystemConfigService systemConfigService;

    private StudentHomeTodoServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new StudentHomeTodoServiceImpl(
                interviewInvitationService,
                jobApplicationService,
                confirmationRecordService,
                stageAssignmentService,
                calendarService,
                reflectionService,
                appraisalService,
                systemConfigService);
        when(stageAssignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of());
        when(confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(STUDENT_ID))
                .thenReturn(List.of());
        when(jobApplicationService.findByStudentId(STUDENT_ID)).thenReturn(List.of());
        when(interviewInvitationService.findByStudentId(STUDENT_ID)).thenReturn(List.of());
        when(calendarService.getCalendar()).thenReturn(calendar(-10, 20, 30));
    }

    @Test
    void offersRemainActionableWithoutApplicationRowsOrStageWindows() {
        InterviewInvitation first = invitation(2L, 20L, 200L, "乙公司", "测试", "interview_passed");
        InterviewInvitation second = invitation(3L, 30L, 300L, "丙公司", "产品", "interview_passed");
        when(interviewInvitationService.findByStudentId(STUDENT_ID)).thenReturn(List.of(first, second));

        List<StudentTodoItemDTO> todos = service.getActionableTodos(STUDENT_ID);

        assertEquals(2, todos.stream()
                .filter(item -> "OFFER_CONFIRMATION_REQUIRED".equals(item.key())).count());
        assertTrue(todos.stream().allMatch(item -> item.stageNo() == null));
        assertTrue(todos.stream().anyMatch(item -> ObjectsEqual(2L,
                item.action().params().get("offerId"))));
    }

    @Test
    void pendingConfirmationSuppressesOtherOffersUntilEnterpriseResponds() {
        InternshipConfirmationRecord pending = confirmation(50L, 0, 0,
                20L, 200L, "乙公司", "测试");
        when(confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(STUDENT_ID))
                .thenReturn(List.of(pending));
        when(interviewInvitationService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                invitation(2L, 20L, 200L, "乙公司", "测试", "interview_passed"),
                invitation(3L, 30L, 300L, "丙公司", "产品", "interview_passed")));

        List<StudentTodoItemDTO> todos = service.getActionableTodos(STUDENT_ID);

        assertEquals(1, todos.size());
        assertEquals("CONFIRMATION_PENDING", todos.get(0).key());
        assertEquals("VIEW_CONFIRMATION", todos.get(0).action().key());
    }

    @Test
    void confirmedPlacementDoesNotHideACompanyChangeOffer() {
        InternshipConfirmationRecord confirmed = confirmation(52L, 1, 0,
                20L, 200L, "乙公司", "测试");
        when(confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(STUDENT_ID))
                .thenReturn(List.of(confirmed));
        when(interviewInvitationService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                invitation(2L, 20L, 200L, "乙公司", "测试", "interview_passed"),
                invitation(3L, 30L, 300L, "丙公司", "产品", "interview_passed")));

        List<StudentTodoItemDTO> todos = service.getActionableTodos(STUDENT_ID);

        assertEquals(1, todos.stream()
                .filter(item -> "OFFER_CONFIRMATION_REQUIRED".equals(item.key())).count());
        assertEquals(3L, todos.stream()
                .filter(item -> "OFFER_CONFIRMATION_REQUIRED".equals(item.key()))
                .findFirst().orElseThrow().action().targetId());
    }

    @Test
    void rejectedConfirmationCreatesResubmissionWithoutStageDeadline() {
        InternshipConfirmationRecord rejected = confirmation(51L, 2, 0,
                20L, 200L, "乙公司", "测试");
        rejected.setRemark("材料需要补充");
        when(confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(STUDENT_ID))
                .thenReturn(List.of(rejected));
        when(interviewInvitationService.findByStudentId(STUDENT_ID)).thenReturn(List.of(
                invitation(2L, 20L, 200L, "乙公司", "测试", "interview_passed")));

        StudentTodoItemDTO todo = service.getActionableTodos(STUDENT_ID).get(0);

        assertEquals("CONFIRMATION_RESUBMISSION_REQUIRED", todo.key());
        assertEquals("RESUBMIT_CONFIRMATION", todo.action().key());
        assertNull(todo.stageNo());
    }

    @Test
    void noStageTwoDecisionOrSelectionTodoIsEverGenerated() {
        InternshipStageAssignment oldStageOne = placement(9L, "REPLACED", -20, -1);
        oldStageOne.setStageNo(1);
        when(stageAssignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(oldStageOne));

        List<StudentTodoItemDTO> todos = service.getActionableTodos(STUDENT_ID);

        assertFalse(todos.stream().anyMatch(item -> item.key().contains("STAGE_TWO")));
        assertFalse(todos.stream().anyMatch(item -> item.action().key().contains("STAGE_TWO")));
    }

    @Test
    void reflectionDraftSurvivesCompanySwitchAndUsesGlobalPeriod() {
        InternshipStageAssignment oldPlacement = placement(10L, "REPLACED", -10, -1);
        InternshipStageAssignment currentPlacement = placement(20L, "CONFIRMED", 0, 20);
        currentPlacement.setCompanyName("丙公司");
        currentPlacement.setPositionName("产品");
        when(stageAssignmentService.findByStudentId(STUDENT_ID))
                .thenReturn(List.of(oldPlacement, currentPlacement));
        when(confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(STUDENT_ID))
                .thenReturn(List.of(confirmation(60L, 1, 0, 30L, 300L, "丙公司", "产品")));
        when(reflectionService.calculatePeriodNumber(any(Date.class))).thenReturn(4);
        InternshipReflection withdrawnDraft = reflection(77L, 10L, 4, "0");
        when(reflectionService.list(STUDENT_ID, null, null, null))
                .thenReturn(List.of(withdrawnDraft));

        StudentTodoItemDTO todo = service.getActionableTodos(STUDENT_ID).stream()
                .filter(item -> "REFLECTION_SUBMISSION_REQUIRED".equals(item.key()))
                .findFirst().orElseThrow();

        assertEquals(77L, todo.action().targetId());
        assertEquals(4, todo.action().params().get("periodNumber"));
        assertEquals(20L, todo.action().params().get("placementId"));
        assertNull(todo.stageNo());
    }

    @Test
    void anExistingSubmissionInTheGlobalPeriodPreventsDuplicateAfterSwitch() {
        InternshipStageAssignment currentPlacement = placement(20L, "CONFIRMED", 0, 20);
        when(stageAssignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(currentPlacement));
        when(confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(STUDENT_ID))
                .thenReturn(List.of(confirmation(60L, 1, 0, 30L, 300L, "丙公司", "产品")));
        when(reflectionService.calculatePeriodNumber(any(Date.class))).thenReturn(4);
        when(reflectionService.list(STUDENT_ID, null, null, null))
                .thenReturn(List.of(reflection(70L, 10L, 4, null)));

        List<StudentTodoItemDTO> todos = service.getActionableTodos(STUDENT_ID);

        assertFalse(todos.stream()
                .anyMatch(item -> "REFLECTION_SUBMISSION_REQUIRED".equals(item.key())));
    }

    @Test
    void appraisalUsesGlobalEndAndPlacementId() {
        InternshipStageAssignment ended = placement(30L, "ENDED", -30, -1);
        ended.setCompanyId(300L);
        when(stageAssignmentService.findByStudentId(STUDENT_ID)).thenReturn(List.of(ended));
        when(calendarService.getCalendar()).thenReturn(calendar(-30, -1, 5));

        StudentTodoItemDTO todo = service.getActionableTodos(STUDENT_ID).stream()
                .filter(item -> "SUBMIT_APPRAISAL".equals(item.action().key()))
                .findFirst().orElseThrow();
        assertEquals(30L, todo.action().params().get("placementId"));
        assertNull(todo.stageNo());

        InternshipAppraisalSubmission submitted = new InternshipAppraisalSubmission();
        submitted.setId(88L);
        submitted.setStatus("SUBMITTED");
        when(appraisalService.findExistingSubmission(STUDENT_ID)).thenReturn(submitted);
        assertFalse(service.getActionableTodos(STUDENT_ID).stream()
                .anyMatch(item -> "SUBMIT_APPRAISAL".equals(item.action().key())));
    }

    @Test
    void pastInterviewIsNotATodoButUnscheduledInterviewRemainsVisible() {
        InterviewInvitation past = invitation(1L, 10L, 100L, "甲公司", "Java", "pending_interview");
        past.setInterviewTime(LocalDateTime.now(SHANGHAI).minusMinutes(1));
        InterviewInvitation unscheduled = invitation(2L, 20L, 200L, "乙公司", "测试", "pending_interview");
        when(interviewInvitationService.findByStudentId(STUDENT_ID))
                .thenReturn(List.of(past, unscheduled));

        List<StudentTodoItemDTO> todos = service.getActionableTodos(STUDENT_ID);

        assertEquals(1, todos.size());
        assertEquals(2L, todos.get(0).action().targetId());
    }

    private InternshipCalendar calendar(int startOffset, int endOffset, int deadlineOffset) {
        InternshipCalendar calendar = new InternshipCalendar();
        calendar.setStartDate(LocalDate.now(SHANGHAI).plusDays(startOffset).toString());
        calendar.setEndDate(LocalDate.now(SHANGHAI).plusDays(endOffset).toString());
        calendar.setReportCycle(7);
        calendar.setAppraisalDeadline(LocalDate.now(SHANGHAI).plusDays(deadlineOffset).toString());
        return calendar;
    }

    private InterviewInvitation invitation(Long id, Long companyId, Long positionId,
                                            String company, String position, String status) {
        InterviewInvitation invitation = new InterviewInvitation();
        invitation.setId(id);
        invitation.setStudentId(STUDENT_ID);
        invitation.setCompanyId(companyId);
        invitation.setPositionId(positionId);
        invitation.setCompanyName(company);
        invitation.setPositionName(position);
        invitation.setStatus(status);
        return invitation;
    }

    private InternshipConfirmationRecord confirmation(Long id, int status, Integer recallStatus,
                                                       Long companyId, Long positionId,
                                                       String company, String position) {
        InternshipConfirmationRecord record = new InternshipConfirmationRecord();
        record.setId(id);
        record.setStudentId(STUDENT_ID);
        record.setStatus(status);
        record.setRecallStatus(recallStatus);
        record.setCompanyId(companyId);
        record.setPositionId(positionId);
        record.setCompanyName(company);
        record.setPositionName(position);
        record.setCreateTime(LocalDateTime.now(SHANGHAI));
        return record;
    }

    private InternshipStageAssignment placement(Long id, String status,
                                                 int startOffset, int endOffset) {
        InternshipStageAssignment placement = new InternshipStageAssignment();
        placement.setId(id);
        placement.setStudentId(STUDENT_ID);
        placement.setCompanyId(id * 10);
        placement.setCompanyName("公司" + id);
        placement.setPositionId(id * 100);
        placement.setPositionName("岗位" + id);
        placement.setStatus(status);
        placement.setCurrentFlag("CONFIRMED".equals(status) ? 1 : 0);
        placement.setInternshipStartTime(atStartOfDay(startOffset));
        placement.setInternshipEndTime(atStartOfDay(endOffset));
        return placement;
    }

    private InternshipReflection reflection(Long id, Long placementId, int period, String remark) {
        InternshipReflection reflection = new InternshipReflection();
        reflection.setId(id);
        reflection.setStudentId(STUDENT_ID);
        reflection.setStageAssignmentId(placementId);
        reflection.setPeriodNumber(period);
        reflection.setStatus(0);
        reflection.setRemark(remark);
        reflection.setDeleted(0);
        reflection.setUpdateTime(new Date());
        return reflection;
    }

    private Date atStartOfDay(int offset) {
        return Date.from(LocalDate.now(SHANGHAI).plusDays(offset)
                .atStartOfDay(SHANGHAI).toInstant());
    }

    private boolean ObjectsEqual(Object expected, Object actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }
}
