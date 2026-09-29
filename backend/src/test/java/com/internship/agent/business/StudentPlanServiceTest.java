package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.StudentUser;
import com.internship.mapper.StudentInternshipPlanMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.StudentApplicationService;
import com.internship.service.StudentHomeTodoService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StudentPlanServiceTest {
    private StudentInternshipStatusService statusService;
    private StudentInternshipPlanMapper plans;
    private com.internship.service.SystemConfigService systemConfigs;
    private StudentApplicationService studentApplications;
    private StudentHomeTodoService todos;
    private StudentJobApplicationService applications;
    private InterviewInvitationService interviews;
    private StudentUserMapper students;
    private InternshipConfirmationRecordService confirmations;
    private InternshipStageAssignmentService placements;
    private com.internship.service.InternshipAppraisalService appraisals;
    private StudentPlanService service;
    private final Actor student = new Actor("ROLE_STUDENT:8:false:", 8L, "ROLE_STUDENT", "s001", null, null, null, false, Set.of());

    @BeforeEach void setup() {
        statusService = mock(StudentInternshipStatusService.class);
        todos = mock(StudentHomeTodoService.class);
        applications = mock(StudentJobApplicationService.class);
        interviews = mock(InterviewInvitationService.class);
        confirmations = mock(InternshipConfirmationRecordService.class);
        placements = mock(InternshipStageAssignmentService.class);
        appraisals = mock(com.internship.service.InternshipAppraisalService.class);
        studentApplications = mock(StudentApplicationService.class);
        students = mock(StudentUserMapper.class);
        plans = mock(StudentInternshipPlanMapper.class);
        systemConfigs = mock(com.internship.service.SystemConfigService.class);
        when(todos.getActionableTodos(anyLong())).thenReturn(List.of());
        when(applications.findByStudentId(anyLong())).thenReturn(List.of());
        when(interviews.findByStudentId(anyLong())).thenReturn(List.of());
        when(confirmations.findByStudentId(anyLong())).thenReturn(List.of());
        when(placements.findByStudentId(anyLong())).thenReturn(List.of());
        when(studentApplications.findByStudentId(anyLong())).thenReturn(List.of());
        when(plans.findByStudentId(anyLong())).thenReturn(null);
        StudentUser user = new StudentUser();
        user.setPhone("13800000000");
        user.setName("测试学生");
        user.setStudentId("test8");
        user.setEmail("student8@example.test");
        user.setMajor("软件工程");
        when(students.findById(anyLong())).thenReturn(user);
        service = new StudentPlanService(statusService, todos, applications, interviews,
                confirmations, placements, studentApplications, students, plans, new ObjectMapper(),
                systemConfigs);
        service.setAppraisalService(appraisals);
    }

    @Test void zeroRowInsertMustNotReportSaved() {
        when(plans.insert(any())).thenReturn(0);
        StudentPlanService.PlanView plan = service.refresh(student);
        assertFalse(plan.persisted());
        assertEquals("FAILED", service.factsOf(plan).get("snapshotStatus"));
    }

    @Test void failedInsertMustReportFailure() {
        when(plans.insert(any())).thenThrow(new IllegalStateException("local test failure"));
        StudentPlanService.PlanView plan = service.refresh(student);
        assertFalse(plan.persisted());
        assertEquals("FAILED", service.factsOf(plan).get("snapshotStatus"));
    }

    @Test void freshStudentStartsAtSearchAndPersists() {
        when(plans.insert(any())).thenReturn(1);
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(0);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        StudentPlanService.PlanView plan = service.refresh(student);
        assertEquals("SEARCH", plan.stage());
        assertEquals("寻找实习", plan.stageLabel());
        assertTrue(plan.persisted());
        assertTrue(plan.items().stream().anyMatch(i -> "apply".equals(i.key()) && "TODO".equals(i.state())));
        assertEquals("投递岗位", plan.nextTitle());
        verify(plans).insert(any());
    }

    @Test void unchangedSnapshotIsNotRewritten() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(4);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        com.internship.entity.StudentInternshipPlan stored = new com.internship.entity.StudentInternshipPlan();
        stored.setStudentId(8L);
        stored.setVersion(3);
        // 先算一次得到签名，再伪造相同快照
        StudentPlanService.PlanView first = service.refresh(student);
        stored.setCurrentStage(first.stage());
        try {
            List<java.util.Map<String, Object>> rows = new java.util.ArrayList<>();
            for (StudentPlanService.PlanItem item : first.items()) {
                java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
                row.put("key", item.key());
                row.put("title", item.title());
                row.put("state", item.state());
                row.put("detail", item.detail());
                rows.add(row);
            }
            stored.setItems(new ObjectMapper().writeValueAsString(rows));
        } catch (Exception e) {
            fail("snapshot serialize");
        }
        when(plans.findByStudentId(8L)).thenReturn(stored);
        StudentPlanService.PlanView second = service.refresh(student);
        assertFalse(second.persisted());
        assertEquals("UNCHANGED", service.factsOf(second).get("snapshotStatus"));
        assertEquals(3, second.version());
        verify(plans, never()).updateByVersion(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"keys", "whitespace", "both"})
    void normalizedSnapshotIsUnchanged(String format) throws Exception {
        var stored = normalizedSnapshot(format);
        when(plans.findByStudentId(8L)).thenReturn(stored);
        when(plans.updateByVersion(any())).thenReturn(1);

        var plan = service.refresh(student);

        assertEquals(StudentPlanService.SnapshotStatus.UNCHANGED, plan.snapshotStatus());
        assertFalse(plan.persisted());
        assertEquals(0, plan.version());
        verify(plans, never()).insert(any());
        verify(plans, never()).updateByVersion(any());
    }

    @Test void normalizedConflictReadIsUnchangedWithoutSecondWrite() throws Exception {
        var fresh = normalizedSnapshot("both");
        fresh.setVersion(4);
        when(plans.findByStudentId(8L)).thenReturn(storedSnapshot(), fresh, fresh);
        when(plans.updateByVersion(any())).thenReturn(0, 1);

        var plan = service.refresh(student);

        assertEquals(StudentPlanService.SnapshotStatus.UNCHANGED, plan.snapshotStatus());
        assertFalse(plan.persisted());
        assertEquals(4, plan.version());
        verify(plans, times(1)).updateByVersion(any());
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"arrayOrder", "value", "stage"})
    void semanticSnapshotChangesStillWrite(String change) throws Exception {
        var stored = normalizedSnapshot("both");
        var mapper = new ObjectMapper();
        var items = (com.fasterxml.jackson.databind.node.ArrayNode) mapper.readTree(stored.getItems());
        switch (change) {
            case "arrayOrder" -> items.add(items.remove(0));
            case "value" -> ((com.fasterxml.jackson.databind.node.ObjectNode) items.get(0)).put("detail", "内容已变化");
            case "stage" -> stored.setCurrentStage("PREPARE");
            default -> fail("unknown change");
        }
        stored.setItems(mapper.writeValueAsString(items));
        when(plans.findByStudentId(8L)).thenReturn(stored);
        when(plans.updateByVersion(any())).thenReturn(1);

        var plan = service.refresh(student);

        assertEquals(StudentPlanService.SnapshotStatus.SAVED, plan.snapshotStatus());
        assertTrue(plan.persisted());
        verify(plans, times(1)).updateByVersion(any());
    }

    private com.internship.entity.StudentInternshipPlan normalizedSnapshot(String format) throws Exception {
        when(plans.insert(any())).thenReturn(1);
        service.refresh(student);
        var captured = org.mockito.ArgumentCaptor.forClass(com.internship.entity.StudentInternshipPlan.class);
        verify(plans).insert(captured.capture());
        var stored = captured.getValue();
        var mapper = new ObjectMapper();
        var original = mapper.readTree(stored.getItems());
        var normalized = mapper.createArrayNode();
        for (var item : original) {
            if ("whitespace".equals(format)) {
                normalized.add(item);
            } else {
                var reordered = mapper.createObjectNode();
                for (String key : List.of("key", "state", "title", "detail")) {
                    reordered.set(key, item.get(key));
                }
                normalized.add(reordered);
            }
        }
        String serialized = "keys".equals(format) ? mapper.writeValueAsString(normalized)
                : mapper.writerWithDefaultPrettyPrinter().writeValueAsString(normalized);
        assertNotEquals(stored.getItems(), serialized);
        assertEquals(original, mapper.readTree(serialized));
        stored.setItems(serialized);
        clearInvocations(plans);
        return stored;
    }

    @Test void updateConflictExhaustionReportsFailure() {
        when(plans.findByStudentId(8L)).thenAnswer(invocation -> storedSnapshot());
        when(plans.updateByVersion(any())).thenReturn(0);
        StudentPlanService.PlanView plan = service.refresh(student);
        assertFalse(plan.persisted());
        assertEquals("FAILED", service.factsOf(plan).get("snapshotStatus"));
        verify(plans, times(2)).updateByVersion(any());
    }

    @Test void updateConflictRetriesOnceAndReportsActualWrite() {
        when(plans.findByStudentId(8L)).thenAnswer(invocation -> storedSnapshot());
        when(plans.updateByVersion(any())).thenReturn(0, 1);
        StudentPlanService.PlanView plan = service.refresh(student);
        assertTrue(plan.persisted());
        assertEquals("SAVED", service.factsOf(plan).get("snapshotStatus"));
        verify(plans, times(2)).updateByVersion(any());
    }

    @Test void refreshRetainsIndependentWritableTransaction() throws Exception {
        var transaction = StudentPlanService.class.getMethod("refresh", Actor.class)
                .getAnnotation(org.springframework.transaction.annotation.Transactional.class);
        assertNotNull(transaction);
        assertEquals(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW, transaction.propagation());
        assertFalse(transaction.readOnly());
    }

    private com.internship.entity.StudentInternshipPlan storedSnapshot() {
        var stored = new com.internship.entity.StudentInternshipPlan();
        stored.setStudentId(8L);
        stored.setVersion(3);
        stored.setCurrentStage("PREPARE");
        stored.setItems("[]");
        return stored;
    }

    @Test void confirmedStudentReachesOnboard() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(2);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        StudentPlanService.PlanView plan = service.refresh(student);
        assertEquals("ONBOARD", plan.stage());
    }

    @Test void selfPracticePendingAddsWaitingItemWithoutChangingRoute() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(0);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        com.internship.entity.StudentApplication application = new com.internship.entity.StudentApplication();
        application.setApplicationType("selfPractice");
        application.setStatus("pending");
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));

        StudentPlanService.PlanView plan = service.refresh(student);
        assertEquals("SEARCH", plan.stage());
        assertTrue(plan.items().stream()
                .anyMatch(i -> "self_practice".equals(i.key()) && "WAITING".equals(i.state())));
    }

    @Test void rejectedSelfPracticeShowsReasonAndResubmitItem() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(0);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        com.internship.entity.StudentApplication application = new com.internship.entity.StudentApplication();
        application.setApplicationType("selfPractice");
        application.setStatus("rejected");
        application.setRejectReason("请补充实习安排");
        application.setApplyTime(new java.util.Date());
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));

        StudentPlanService.PlanView plan = service.refresh(student);
        var item = plan.items().stream().filter(i -> "self_practice".equals(i.key())).findFirst().orElseThrow();
        assertEquals("REJECTED", item.state());
        assertTrue(item.detail().contains("请补充实习安排"));
        assertEquals("OPEN_SELF_PRACTICE", item.link());
    }

    @Test void withdrawnSelfPracticeShowsResubmitItem() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(0);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        com.internship.entity.StudentApplication application = new com.internship.entity.StudentApplication();
        application.setApplicationType("selfPractice");
        application.setStatus("withdrawn");
        application.setApplyTime(new java.util.Date());
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));

        StudentPlanService.PlanView plan = service.refresh(student);
        var item = plan.items().stream().filter(i -> "self_practice".equals(i.key())).findFirst().orElseThrow();
        assertEquals("WITHDRAWN", item.state());
        assertEquals("OPEN_SELF_PRACTICE", item.link());
    }
    @Test void delayPendingAddsWaitingItem() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(0);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        com.internship.entity.StudentApplication application = new com.internship.entity.StudentApplication();
        application.setApplicationType("delay");
        application.setStatus("pending");
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));

        StudentPlanService.PlanView plan = service.refresh(student);
        assertTrue(plan.items().stream()
                .anyMatch(i -> "delay".equals(i.key()) && "WAITING".equals(i.state())));
    }

    @Test void interruptedStatusRoutesToInterruptedStageWithNextAction() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(5);
        when(statusService.findByStudentId(8L)).thenReturn(status);

        StudentPlanService.PlanView plan = service.refresh(student);
        assertEquals("INTERRUPTED", plan.stage());
        assertEquals("中断处理", plan.stageLabel());
        assertTrue(plan.items().stream()
                .anyMatch(i -> "interrupt".equals(i.key()) && "TODO".equals(i.state())));
    }

    @Test void reflectionTodoTurnsMaterialItemActionable() {
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(3);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        com.internship.entity.dto.StudentTodoItemDTO todo = new com.internship.entity.dto.StudentTodoItemDTO(
                "t1", "REFLECTION_SUBMISSION_REQUIRED", "本期实习心得待提交", "请按期提交",
                "HIGH", null, 3, null);
        when(todos.getActionableTodos(8L)).thenReturn(List.of(todo));

        StudentPlanService.PlanView plan = service.refresh(student);
        assertEquals("IN_PROGRESS", plan.stage());
        assertTrue(plan.items().stream()
                .anyMatch(i -> "materials".equals(i.key()) && "TODO".equals(i.state())));
    }

    @Test void allLifecycleCodesMapToStableStages() {
        for (int code = 0; code <= 6; code++) {
            StudentInternshipStatus status = new StudentInternshipStatus();
            status.setStatus(code);
            when(statusService.findByStudentId(8L)).thenReturn(status);
            StudentPlanService.PlanView plan = service.refresh(student);
            String expected = switch (code) {
                case 1 -> "CONFIRM";
                case 2 -> "ONBOARD";
                case 3 -> "IN_PROGRESS";
                case 4 -> "FINISHED";
                case 5 -> "INTERRUPTED";
                case 6 -> "DELAYED";
                default -> "SEARCH";
            };
            assertEquals(expected, plan.stage(), "status=" + code);
        }

    }
    private void hideReflectionMenu() {
        com.internship.entity.SystemConfig cfg = new com.internship.entity.SystemConfig();
        cfg.setConfigValue(
                "[{\"path\":\"/student/internships\",\"visible\":false}]");
        when(systemConfigs.findByConfigKey("STUDENT_MENU_CONFIG")).thenReturn(cfg);
    }

    private StudentPlanService.PlanItem materialsOf(StudentPlanService.PlanView plan) {
        return plan.items().stream().filter(i -> "materials".equals(i.key())).findFirst()
                .orElseThrow(() -> new AssertionError("materials item missing"));
    }

    @Test void hiddenReflectionTodoIsIgnoredInPlan() {
        hideReflectionMenu();
        com.internship.entity.dto.StudentTodoItemDTO todo =
                new com.internship.entity.dto.StudentTodoItemDTO(
                        "t1", "REFLECTION_SUBMISSION_REQUIRED", "本期实习心得待提交", "请按期提交",
                        "HIGH", null, 12, null);
        when(todos.getActionableTodos(8L)).thenReturn(List.of(todo));
        StudentPlanService.PlanView plan = service.refresh(student);
        StudentPlanService.PlanItem m = materialsOf(plan);
        assertEquals("WAITING", m.state());
        assertFalse(m.detail().contains("心得"));
        assertEquals("OPEN_APPRAISAL", m.link());
    }

    @Test void hiddenReflectionKeepsAppraisalInPlan() {
        hideReflectionMenu();
        com.internship.entity.dto.StudentTodoItemDTO todo =
                new com.internship.entity.dto.StudentTodoItemDTO(
                        "a1", "APPRAISAL_SUBMISSION_REQUIRED", "提交实习鉴定表", "请上传材料",
                        "HIGH", null, null, null);
        when(todos.getActionableTodos(8L)).thenReturn(List.of(todo));
        StudentPlanService.PlanView plan = service.refresh(student);
        StudentPlanService.PlanItem m = materialsOf(plan);
        assertEquals("TODO", m.state());
        assertTrue(m.detail().contains("提交实习鉴定表"));
        assertFalse(m.detail().contains("心得"));
        assertEquals("OPEN_APPRAISAL", m.link());
    }

    @Test void visibleReflectionKeepsOldPlanBehavior() {
        com.internship.entity.SystemConfig cfg = new com.internship.entity.SystemConfig();
        cfg.setConfigValue(
                "[{\"path\":\"/student/internships\",\"visible\":true}]");
        when(systemConfigs.findByConfigKey("STUDENT_MENU_CONFIG")).thenReturn(cfg);
        com.internship.entity.dto.StudentTodoItemDTO todo =
                new com.internship.entity.dto.StudentTodoItemDTO(
                        "t1", "REFLECTION_SUBMISSION_REQUIRED", "本期实习心得待提交", "请按期提交",
                        "HIGH", null, 12, null);
        when(todos.getActionableTodos(8L)).thenReturn(List.of(todo));
        StudentPlanService.PlanView plan = service.refresh(student);
        StudentPlanService.PlanItem m = materialsOf(plan);
        assertEquals("TODO", m.state());
        assertTrue(m.detail().contains("本期实习心得待提交"));
        assertEquals("OPEN_REFLECTIONS", m.link());
    }

    // P5：任一业务源读取失败只降级、不抛错、不伪装成“没有待办”，且不覆盖好快照。
    @Test void applicationSourceFailureMarksDataSyncAndSkipsPersist() {
        when(applications.findByStudentId(anyLong())).thenThrow(new IllegalStateException("downstream down"));
        StudentPlanService.PlanView plan = service.refresh(student);
        assertTrue(plan.items().stream().anyMatch(i -> "data_sync".equals(i.key()) && "WAITING".equals(i.state())));
        assertNotEquals("全部完成", plan.nextTitle());
        assertEquals(StudentPlanService.SnapshotStatus.FAILED, plan.snapshotStatus());
        assertFalse(plan.persisted());
        verify(plans, never()).insert(any());
        verify(plans, never()).updateByVersion(any());
    }

    // P5：资料读失败按未知处理，不谎称已完成。
    @Test void profileReadFailureDoesNotClaimDone() {
        when(students.findById(anyLong())).thenThrow(new IllegalStateException("db down"));
        StudentPlanService.PlanView plan = service.refresh(student);
        assertTrue(plan.items().stream().anyMatch(i -> "profile".equals(i.key()) && "TODO".equals(i.state())));
        assertTrue(plan.items().stream().anyMatch(i -> "data_sync".equals(i.key())));
        assertEquals("UNKNOWN", plan.stage());
    }

    // P5：健康全完成仍报全部完成，且无降级标记（与降级分支对照）。
    @Test void healthyAllDoneStillReportsAllDone() {
        when(plans.insert(any())).thenReturn(1);
        var appraisal = new com.internship.entity.InternshipAppraisalSubmission();
        appraisal.setStatus("SUBMITTED");
        when(appraisals.findExistingSubmission(8L)).thenReturn(appraisal);
        StudentInternshipStatus status = new StudentInternshipStatus();
        status.setStatus(4);
        when(statusService.findByStudentId(8L)).thenReturn(status);
        com.internship.entity.StudentJobApplication app = new com.internship.entity.StudentJobApplication();
        app.setPositionName("测试岗");
        when(applications.findByStudentId(anyLong())).thenReturn(List.of(app));
        com.internship.entity.InterviewInvitation invite = new com.internship.entity.InterviewInvitation();
        invite.setStatus("interview_passed");
        when(interviews.findByStudentId(anyLong())).thenReturn(List.of(invite));
        com.internship.entity.InternshipConfirmationRecord confirm = new com.internship.entity.InternshipConfirmationRecord();
        confirm.setStatus(1);
        when(confirmations.findByStudentId(anyLong())).thenReturn(List.of(confirm));
        StudentPlanService.PlanView plan = service.refresh(student);
        assertTrue(plan.items().stream().noneMatch(i -> "data_sync".equals(i.key())));
        assertTrue(plan.items().stream().allMatch(i -> "DONE".equals(i.state())));
        assertEquals("全部完成", plan.nextTitle());
    }

    // P5：已完成事项只向前流转，不回退为 TODO。
    @Test void doneItemsNeverFlipBackToTodo() {
        StudentPlanService.PlanView first = service.refresh(student);
        assertTrue(first.items().stream().anyMatch(i -> "profile".equals(i.key()) && "DONE".equals(i.state())));
        com.internship.entity.StudentJobApplication app = new com.internship.entity.StudentJobApplication();
        app.setPositionName("测试岗");
        when(applications.findByStudentId(anyLong())).thenReturn(List.of(app));
        StudentPlanService.PlanView second = service.refresh(student);
        assertTrue(second.items().stream().anyMatch(i -> "profile".equals(i.key()) && "DONE".equals(i.state())));
        assertTrue(second.items().stream().anyMatch(i -> "apply".equals(i.key()) && "DONE".equals(i.state())));
    }

    @Test void approvedSelfPracticeSkipsJobAndInterviewAndCarriesCurrentPlacement() {
        var current = placement(10L, 1, "IN_PROGRESS", "SELF_PRACTICE");
        current.setCompanyName("自主实习单位"); current.setPositionName("实践岗位");
        when(placements.findByStudentId(8L)).thenReturn(List.of(current));
        status(3);
        var plan = service.refresh(student);
        assertEquals("IN_PROGRESS", plan.stage());
        assertEquals(10L, plan.currentPlacement().get("id"));
        assertEquals(1, plan.currentPlacement().get("stageNo"));
        assertTrue(plan.items().stream().filter(i -> Set.of("apply", "interview", "confirm").contains(i.key())).allMatch(i -> "DONE".equals(i.state())));
        assertTrue(plan.items().stream().anyMatch(i -> i.detail().contains("无需办理企业面试")));
        assertFalse(plan.nextTitle().contains("面试"));
    }

    @Test void historicalOfferAndArchivedConfirmationCannotAdvanceCurrentSearch() {
        status(0);
        var old = placement(9L, 0, "REPLACED", "NEW_APPLICATION");
        old.setCompanyId(1L); old.setPositionId(2L);
        when(placements.findByStudentId(8L)).thenReturn(List.of(old));
        var app = new com.internship.entity.StudentJobApplication();
        app.setPositionName("旧岗位"); app.setStageAssignmentId(9L); app.setCompanyId(1L); app.setPositionId(2L); app.setStatus("approved");
        when(applications.findByStudentId(8L)).thenReturn(List.of(app));
        var invite = new com.internship.entity.InterviewInvitation();
        invite.setStatus("interview_passed"); invite.setCompanyId(1L); invite.setPositionId(2L);
        when(interviews.findByStudentId(8L)).thenReturn(List.of(invite));
        var confirmation = new com.internship.entity.InternshipConfirmationRecord(); confirmation.setStatus(1);
        when(confirmations.findByStudentId(8L)).thenReturn(List.of(confirmation));
        var plan = service.refresh(student);
        assertEquals("SEARCH", plan.stage());
        assertEquals("投递岗位", plan.nextTitle());
        assertTrue(plan.items().stream().anyMatch(i -> "confirm".equals(i.key()) && !"DONE".equals(i.state())));
    }

    @Test void draftConfirmationIsNotReportedAsSubmitted() {
        status(0);
        var draft = new com.internship.entity.InternshipConfirmationRecord(); draft.setStatus(0); draft.setRecallStatus(null);
        when(confirmations.findByStudentId(8L)).thenReturn(List.of(draft));
        var plan = service.refresh(student);
        assertEquals("SEARCH", plan.stage());
        assertTrue(plan.items().stream().noneMatch(i -> i.detail().contains("确认表已提交")));
    }

    @Test void changingJobKeepsCurrentPlacementUntilNewConfirmation() {
        status(3);
        var current = placement(11L, 1, "CONFIRMED", "NEW_APPLICATION"); current.setCompanyId(1L); current.setPositionId(2L);
        when(placements.findByStudentId(8L)).thenReturn(List.of(current));
        var pending = new com.internship.entity.InternshipConfirmationRecord(); pending.setStatus(0); pending.setRecallStatus(0); pending.setCompanyId(3L); pending.setPositionId(4L);
        when(confirmations.findByStudentId(8L)).thenReturn(List.of(pending));
        var plan = service.refresh(student);
        assertEquals("IN_PROGRESS", plan.stage());
        assertEquals(11L, plan.currentPlacement().get("id"));
        assertTrue(plan.items().stream().anyMatch(i -> "change".equals(i.key()) && "WAITING".equals(i.state()) && i.detail().contains("保持原状")));
    }

    @Test void delayedOrInterruptedNextStepIsNotHistoricalJobSearch() {
        status(6);
        assertEquals("核对延期安排", service.refresh(student).nextTitle());
        status(5);
        assertEquals("中断后续", service.refresh(student).nextTitle());
    }

    @Test void endedStatusDoesNotInventCompletedAppraisal() {
        status(4);
        var plan = service.refresh(student);
        assertEquals("FINISHED", plan.stage());
        assertEquals("TODO", materialsOf(plan).state());
        assertEquals("过程材料", plan.nextTitle());
    }

    @Test void absentStatusAndAmbiguousPlacementsStayUnknown() {
        assertEquals("UNKNOWN", service.refresh(student).stage());
        status(3);
        when(placements.findByStudentId(8L)).thenReturn(List.of(placement(1L, 1, "CONFIRMED", "NEW_APPLICATION"), placement(2L, 1, "CONFIRMED", "NEW_APPLICATION")));
        var plan = service.refresh(student);
        assertEquals("UNKNOWN", plan.stage());
        assertEquals(StudentPlanService.SnapshotStatus.FAILED, plan.snapshotStatus());
        assertTrue(plan.currentPlacement().isEmpty());
    }

    private void status(int code) {
        var status = new StudentInternshipStatus(); status.setStatus(code);
        when(statusService.findByStudentId(8L)).thenReturn(status);
    }

    @Test void archivedEndedSelfPracticeKeepsItsFiveStageRoute() {
        status(4);
        when(placements.findByStudentId(8L)).thenReturn(List.of(
                placement(10L, 0, "ENDED", "NEW_APPLICATION"), placement(11L, 0, "ENDED", "SELF_PRACTICE")));
        var plan = service.refresh(student);
        assertTrue(plan.currentPlacement().isEmpty());
        assertTrue(plan.journeyBasis().selfPractice());
        assertTrue(plan.journeyBasis().established());
    }

    @Test void profileProgressMatchesHomeAndDoesNotMistakeMissingEmailForComplete() {
        status(0);
        var user = students.findById(8L); user.setEmail(null);
        assertFalse(service.refresh(student).journeyBasis().profileDone());
    }

    @Test void realJobOperationInfersNormalRouteWithoutSavedChoice() {
        status(0);
        var application = new com.internship.entity.StudentJobApplication();
        application.setPositionName("测试岗位");
        application.setApplyDate(new java.util.Date(1_000L));
        when(applications.findByStudentId(8L)).thenReturn(List.of(application));
        assertEquals("NORMAL", service.refresh(student).journeyBasis().observedRoute());
    }

    @Test void realSelfPracticeOperationInfersSelfRouteWithoutSavedChoice() {
        status(0);
        var application = new com.internship.entity.StudentApplication();
        application.setApplicationType("selfPractice");
        application.setStatus("rejected");
        application.setCreateTime(new java.util.Date(1_000L));
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));
        assertEquals("SELF", service.refresh(student).journeyBasis().observedRoute());
    }

    @Test void realDelayOperationInfersDelayRouteWithoutSavedChoice() {
        status(0);
        var application = new com.internship.entity.StudentApplication();
        application.setApplicationType("delay");
        application.setStatus("rejected");
        application.setCreateTime(new java.util.Date(1_000L));
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));
        assertEquals("DELAY", service.refresh(student).journeyBasis().observedRoute());
    }

    @Test void latestRealOperationWinsWhenNoRouteWasSelected() {
        status(0);
        var selfApplication = new com.internship.entity.StudentApplication();
        selfApplication.setApplicationType("selfPractice");
        selfApplication.setStatus("rejected");
        selfApplication.setCreateTime(new java.util.Date(1_000L));
        var jobApplication = new com.internship.entity.StudentJobApplication();
        jobApplication.setPositionName("后端实习");
        jobApplication.setApplyDate(new java.util.Date(2_000L));
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(selfApplication));
        when(applications.findByStudentId(8L)).thenReturn(List.of(jobApplication));
        assertEquals("NORMAL", service.refresh(student).journeyBasis().observedRoute());
    }

    @Test void rejectedDelayApplicationIsShownAsResubmittableWithTeacherReason() {
        status(0);
        var application = new com.internship.entity.StudentApplication();
        application.setId(31L);
        application.setApplicationType("delay");
        application.setStatus("rejected");
        application.setRejectReason("请补充完整考研计划");
        application.setCreateTime(new java.util.Date(1_000L));
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));

        var delay = service.refresh(student).items().stream().filter(item -> "delay".equals(item.key())).findFirst().orElseThrow();
        assertEquals("REJECTED", delay.state());
        assertTrue(delay.detail().contains("请补充完整考研计划"));
        assertEquals("OPEN_DELAY", delay.link());
    }

    @Test void withdrawnDelayApplicationIsShownAsReadyForResubmission() {
        status(0);
        var application = new com.internship.entity.StudentApplication();
        application.setId(32L);
        application.setApplicationType("delay");
        application.setStatus("withdrawn");
        application.setCreateTime(new java.util.Date(1_000L));
        when(studentApplications.findByStudentId(8L)).thenReturn(List.of(application));

        var delay = service.refresh(student).items().stream().filter(item -> "delay".equals(item.key())).findFirst().orElseThrow();
        assertEquals("WITHDRAWN", delay.state());
        assertTrue(delay.detail().contains("重新填写并提交"));
        assertEquals("OPEN_DELAY", delay.link());
    }

    private com.internship.entity.InternshipStageAssignment placement(Long id, int current, String status, String source) {
        var assignment = new com.internship.entity.InternshipStageAssignment();
        assignment.setId(id); assignment.setStudentId(8L); assignment.setStageNo(1); assignment.setCurrentFlag(current); assignment.setStatus(status); assignment.setSourceType(source);
        return assignment;
    }
}
