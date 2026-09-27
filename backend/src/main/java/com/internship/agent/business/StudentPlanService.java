package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipAppraisalSubmission;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.StudentApplication;
import com.internship.entity.SystemConfig;
import com.internship.entity.StudentInternshipPlan;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.StudentUser;
import com.internship.entity.dto.StudentTodoItemDTO;
import com.internship.mapper.StudentInternshipPlanMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipAppraisalService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.StudentApplicationService;
import com.internship.service.StudentHomeTodoService;
import com.internship.service.SystemConfigService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.internship.agent.business.AgentTypes.Actor;

/**
 * 学生实习计划：每次按真实业务状态重算（已完成不重复安排），快照持久化供重登继续。
 * 不设后台推进；阶段与事项全部由 Java 确定性推导，模型只负责转述。
 */
@Service
@Slf4j
public class StudentPlanService {

    /** 与 InternshipStatusEnum 0-6 对齐的展示阶段；PREPARE 为资料未齐时的前置阶段。 */
    private static final Map<String, String> STAGE_LABELS = Map.of(
            "PREPARE", "资料准备",
            "SEARCH", "寻找实习",
            "OFFER", "申请与面试",
            "CONFIRM", "实习确认",
            "ONBOARD", "待到岗",
            "IN_PROGRESS", "实习进行",
            "FINISHED", "实习结束",
            "INTERRUPTED", "中断处理",
            "DELAYED", "延期安排",
            "UNKNOWN", "状态待核实");

    public record PlanItem(String key, String title, String state, String detail, String link) {}
    public enum SnapshotStatus { SAVED, UNCHANGED, FAILED }
    /** Only business facts, never model-generated progress or a stored guidance preference. */
    public record JourneyBasis(boolean reliable, boolean hasHistory, boolean profileDone,
                               boolean established, boolean selfPractice, boolean selfPending,
                               boolean delayPending, boolean confirming, boolean passed,
                               boolean appraisalSubmitted, boolean reflectionEnabled, boolean reflectionDue,
                               String observedRoute, String selfApplicationStatus, String selfRejectReason) {
        public JourneyBasis(boolean reliable, boolean hasHistory, boolean profileDone,
                            boolean established, boolean selfPractice, boolean selfPending,
                            boolean delayPending, boolean confirming, boolean passed,
                            boolean appraisalSubmitted, boolean reflectionEnabled, boolean reflectionDue,
                            String observedRoute) {
            this(reliable, hasHistory, profileDone, established, selfPractice, selfPending, delayPending,
                    confirming, passed, appraisalSubmitted, reflectionEnabled, reflectionDue, observedRoute, "", "");
        }

        public JourneyBasis(boolean reliable, boolean hasHistory, boolean profileDone,
                            boolean established, boolean selfPractice, boolean selfPending,
                            boolean delayPending, boolean confirming, boolean passed,
                            boolean appraisalSubmitted, boolean reflectionEnabled, boolean reflectionDue) {
            this(reliable, hasHistory, profileDone, established, selfPractice, selfPending, delayPending,
                    confirming, passed, appraisalSubmitted, reflectionEnabled, reflectionDue,
                    fallbackObservedRoute(hasHistory, established, selfPractice, selfPending, delayPending));
        }
        private static String fallbackObservedRoute(boolean hasHistory, boolean established, boolean selfPractice,
                                                    boolean selfPending, boolean delayPending) {
            if (established) return selfPractice ? "SELF" : "NORMAL";
            if (delayPending) return "DELAY";
            if (selfPending || selfPractice) return "SELF";
            return hasHistory ? "NORMAL" : "";
        }
    }
    public record PlanView(String stage, String stageLabel, List<PlanItem> items,
                           String nextTitle, String nextHow, int version, boolean persisted,
                           SnapshotStatus snapshotStatus, Map<String, Object> currentPlacement, JourneyBasis journeyBasis) {
        public PlanView(String stage, String stageLabel, List<PlanItem> items,
                        String nextTitle, String nextHow, int version, boolean persisted,
                        SnapshotStatus snapshotStatus, Map<String, Object> currentPlacement) {
            this(stage, stageLabel, items, nextTitle, nextHow, version, persisted, snapshotStatus, currentPlacement, null);
        }
        public PlanView(String stage, String stageLabel, List<PlanItem> items,
                        String nextTitle, String nextHow, int version, boolean persisted, SnapshotStatus snapshotStatus) {
            this(stage, stageLabel, items, nextTitle, nextHow, version, persisted, snapshotStatus, Map.of());
        }
        public PlanView(String stage, String stageLabel, List<PlanItem> items,
                        String nextTitle, String nextHow, int version, boolean persisted) {
            this(stage, stageLabel, items, nextTitle, nextHow, version, persisted,
                    persisted ? SnapshotStatus.SAVED : SnapshotStatus.FAILED);
        }
    }

    private final StudentInternshipStatusService statusService;
    private final StudentHomeTodoService todos;
    private final StudentJobApplicationService applications;
    private final InterviewInvitationService interviews;
    private final InternshipConfirmationRecordService confirmations;
    private final InternshipStageAssignmentService placements;
    private final StudentApplicationService studentApplications;
    private final StudentUserMapper students;
    private final StudentInternshipPlanMapper plans;
    private final ObjectMapper json;
    private final SystemConfigService systemConfigs;
    private InternshipAppraisalService appraisalService;

    @Autowired
    public void setAppraisalService(InternshipAppraisalService appraisalService) {
        this.appraisalService = appraisalService;
    }

    public StudentPlanService(StudentInternshipStatusService statusService,
                              StudentHomeTodoService todos,
                              StudentJobApplicationService applications,
                              InterviewInvitationService interviews,
                              InternshipConfirmationRecordService confirmations,
                              InternshipStageAssignmentService placements,
                              StudentApplicationService studentApplications,
                              StudentUserMapper students,
                              StudentInternshipPlanMapper plans,
                              ObjectMapper json,
                              SystemConfigService systemConfigs) {
        this.statusService = statusService;
        this.todos = todos;
        this.applications = applications;
        this.interviews = interviews;
        this.confirmations = confirmations;
        this.placements = placements;
        this.studentApplications = studentApplications;
        this.students = students;
        this.plans = plans;
        this.json = json;
        this.systemConfigs = systemConfigs;
    }

    /** 心得页隐藏时计划不再出现心得；找不到配置时维持原有可见行为。 */
    private boolean isStudentMenuVisible(String menuPath) {
        try {
            SystemConfig config = systemConfigs.findByConfigKey(
                    "STUDENT_MENU_CONFIG");
            if (config == null || config.getConfigValue() == null) return true;
            java.util.List<java.util.Map<String, Object>> menus = json.readValue(
                    config.getConfigValue(),
                    new TypeReference<java.util.List<java.util.Map<String, Object>>>() { });
            for (java.util.Map<String, Object> menu : menus) {
                if (menuPath.equals(String.valueOf(menu.get("path")))) {
                    return !Boolean.FALSE.equals(menu.get("visible"));
                }
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    /** 独立可写事务：经只读 Agent 工具调用时挂起外层只读事务，保证快照可持久化。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    /** 重算并按需持久化；persisted=true 表示本次写了快照。 */
    public PlanView refresh(Actor actor) {
        Long studentId = actor.id();
        Integer code = null;
        StudentInternshipStatus currentStatus = null;
        AtomicBoolean degraded = new AtomicBoolean(false);
        try {
            currentStatus = statusService.findByStudentId(studentId);
            if (currentStatus != null) code = currentStatus.getStatus();
        } catch (Exception e) {
            log.warn("读取实习状态失败 student={}", studentId);
            degraded.set(true);
        }
        List<StudentTodoItemDTO> todoList = fetchOrEmpty(() -> todos.getActionableTodos(studentId), degraded);
        Set<String> todoKeys = new java.util.HashSet<>();
        for (StudentTodoItemDTO t : todoList) todoKeys.add(t.key());
        List<StudentJobApplication> apps = fetchOrEmpty(() -> applications.findByStudentId(studentId), degraded);
        List<InterviewInvitation> invites = fetchOrEmpty(() -> interviews.findByStudentId(studentId), degraded);
        List<InternshipConfirmationRecord> confirms = fetchOrEmpty(() -> confirmations.findByStudentId(studentId), degraded);
        List<StudentApplication> selfApps = fetchOrEmpty(() -> studentApplications.findByStudentId(studentId), degraded);
        List<InternshipStageAssignment> assignmentList = fetchOrEmpty(() -> placements.findByStudentId(studentId), degraded);
        List<InternshipStageAssignment> currentAssignments = assignmentList.stream()
                .filter(a -> Integer.valueOf(1).equals(a.getCurrentFlag()))
                .filter(a -> Set.of("CONFIRMED", "IN_PROGRESS", "ENDED", "INTERRUPTED").contains(Objects.toString(a.getStatus(), "")))
                .toList();
        // currentFlag 是现行流程的实际经历标识；已停用的双阶段设置不能用于推测新阶段。
        InternshipStageAssignment current = currentAssignments.size() == 1 ? currentAssignments.get(0) : null;
        if (currentAssignments.size() > 1) degraded.set(true);
        boolean selfPracticeApproved = current != null && "SELF_PRACTICE".equals(current.getSourceType());
        boolean ended = Integer.valueOf(4).equals(code) || (current != null && "ENDED".equals(current.getStatus()));
        boolean interrupted = Integer.valueOf(5).equals(code) || (current != null && "INTERRUPTED".equals(current.getStatus()));
        boolean established = current != null || Set.of(2, 3, 4).contains(code == null ? -1 : code);
        boolean appraisalSubmitted = false;
        if (appraisalService != null) {
            try {
                InternshipAppraisalSubmission appraisal = appraisalService.findExistingSubmission(studentId);
                appraisalSubmitted = appraisal != null && "SUBMITTED".equals(appraisal.getStatus());
            } catch (Exception e) { degraded.set(true); }
        }

        List<String> missingProfile = missingProfileFields(studentId, degraded);
        boolean profileDone = missingProfile.isEmpty() && !degraded.get();
        Set<Long> archivedAssignments = assignmentList.stream().filter(a -> !Integer.valueOf(1).equals(a.getCurrentFlag()))
                .filter(a -> !Set.of("PENDING_CONFIRMATION", "REJECTED").contains(Objects.toString(a.getStatus(), "")))
                .map(InternshipStageAssignment::getId).filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        List<StudentJobApplication> activeApps = apps.stream()
                .filter(a -> !Set.of("rejected", "withdrawn").contains(Objects.toString(a.getStatus(), "")))
                .filter(a -> a.getStageAssignmentId() == null || !archivedAssignments.contains(a.getStageAssignmentId()))
                .filter(a -> assignmentList.stream().noneMatch(p -> archivedAssignments.contains(p.getId())
                        && sameTarget(a.getCompanyId(), a.getPositionId(), p.getCompanyId(), p.getPositionId())
                        && (a.getCreateTime() == null || p.getInternshipEndTime() == null || !a.getCreateTime().after(p.getInternshipEndTime()))))
                .toList();
        boolean hasApplied = activeApps.stream().anyMatch(a -> a.getPositionName() != null);
        // Offer 只从仍可操作的待办或有效申请推导，历史实习用过的 Offer 不能污染当前计划。
        boolean hasPassed = todoKeys.contains("OFFER_CONFIRMATION_REQUIRED") || invites.stream()
                .filter(i -> "interview_passed".equals(i.getStatus()))
                .anyMatch(i -> activeApps.stream().anyMatch(a -> sameTarget(a.getCompanyId(), a.getPositionId(), i.getCompanyId(), i.getPositionId()))
                        && assignmentList.stream().noneMatch(a -> sameTarget(a.getCompanyId(), a.getPositionId(), i.getCompanyId(), i.getPositionId())
                        && !Set.of("PENDING_CONFIRMATION", "REJECTED").contains(Objects.toString(a.getStatus(), ""))));
        boolean hasWaitingInterview = todoKeys.contains("INTERVIEW_SCHEDULED");
        boolean confirmed = established;
        boolean confirming = confirms.stream().anyMatch(a -> Integer.valueOf(0).equals(a.getStatus())
                && Integer.valueOf(0).equals(a.getRecallStatus()));
        boolean rejected = todoKeys.contains("CONFIRMATION_RESUBMISSION_REQUIRED");
        StudentApplication latestSelfPractice = selfApps.stream()
                .filter(a -> "selfPractice".equals(a.getApplicationType()))
                .max(Comparator.comparing((StudentApplication a) -> latestMillis(a.getApplyTime(), a.getCreateTime(), a.getUpdateTime(), a.getReviewTime()))
                        .thenComparing(StudentApplication::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        String selfPracticeStatus = latestSelfPractice == null ? "" : Objects.toString(latestSelfPractice.getStatus(), "");
        boolean selfPracticePending = "pending".equals(selfPracticeStatus);
        StudentApplication latestDelay = selfApps.stream()
                .filter(a -> "delay".equals(a.getApplicationType()))
                .max(Comparator.comparing((StudentApplication a) -> latestMillis(a.getApplyTime(), a.getCreateTime(), a.getUpdateTime()))
                        .thenComparing(StudentApplication::getId, Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);        boolean delayPending = latestDelay != null && "pending".equals(latestDelay.getStatus());
        boolean changing = established && (confirming || activeApps.stream().anyMatch(a -> current != null
                && !sameTarget(a.getCompanyId(), a.getPositionId(), current.getCompanyId(), current.getPositionId())
                && (a.getCreateTime() == null || current.getCreateTime() == null || !a.getCreateTime().before(current.getCreateTime()))));
        boolean reflectionEnabled = isStudentMenuVisible("/student/internships");
        boolean materialTodo = (reflectionEnabled && todoKeys.contains("REFLECTION_SUBMISSION_REQUIRED"))
                || todoKeys.contains("APPRAISAL_SUBMISSION_REQUIRED");

        List<PlanItem> items = new ArrayList<>();
        if (current != null) items.add(new PlanItem("placement", "当前实习经历", "DONE",
                Objects.toString(current.getCompanyName(), "单位待核实") + " · " + Objects.toString(current.getPositionName(), "岗位待核实")
                        + (selfPracticeApproved ? "（自主实习）" : ""), "OPEN_CONFIRMATION"));
        items.add(new PlanItem("profile", "完善个人资料",
                profileDone ? "DONE" : "TODO",
                profileDone ? "姓名、学号、电话和邮箱已齐备。" : "请到个人中心补齐：" + String.join("、", missingProfile) + "。",
                "OPEN_PROFILE"));
        items.add(new PlanItem("apply", established ? "落实实习岗位" : "投递岗位",
                established || hasApplied ? "DONE" : (selfPracticePending ? "WAITING" : "TODO"),
                established ? (selfPracticeApproved ? "自主实习已通过，本次无需投递系统岗位。" : "当前实习已落实，不要求重新投递。")
                        : selfPracticePending ? "自主实习申请正在审核，无需为本申请重复投递岗位。"
                        : hasApplied ? "已有当前有效投递，可在我的申请中核对。" : "去职位浏览按意向投递，或在首页申请自主实习。",
                selfPracticeApproved || selfPracticePending ? "OPEN_APPLICATION" : "OPEN_JOBS"));
        items.add(new PlanItem("interview", selfPracticeApproved ? "自主实习审核" : "面试与录用",
                established || hasPassed ? "DONE" : hasWaitingInterview ? "TODO" : "WAITING",
                selfPracticeApproved ? "自主实习审核已通过，无需办理企业面试。"
                        : established ? "当前实习已经确认，不要求重走面试流程。"
                        : hasPassed ? "有当前可处理的 Offer，可去确认页核对。"
                        : (hasWaitingInterview ? "有待参加的面试，按邀约时间参加。"
                        : (selfPracticePending ? "等待自主实习审核。" : hasApplied ? "等待企业邀约；也可继续投递。" : "先落实岗位或提交自主实习申请。")),
                selfPracticeApproved ? "OPEN_APPLICATION" : "OPEN_INTERVIEWS"));
        String confirmState = "TODO", confirmDetail = "获得 Offer 后在实习确认页提交确认表。";
        if (confirmed) { confirmState = "DONE"; confirmDetail = selfPracticeApproved ? "自主实习审核已完成，无需再次让企业确认。" : "当前实习已确认，按当前进度继续办理。"; }
        else if (confirming) { confirmState = "WAITING"; confirmDetail = "确认表已提交，等待企业确认，无需重复提交。"; }
        else if (rejected || todoKeys.contains("CONFIRMATION_RESUBMISSION_REQUIRED")) {
            confirmDetail = "确认表被企业拒绝，可换 Offer 重新提交。";
        } else if (hasPassed || todoKeys.contains("OFFER_CONFIRMATION_REQUIRED")) {
            confirmDetail = "有可确认的 Offer，去实习确认页提交。";
        } else { confirmState = "WAITING"; confirmDetail = selfPracticePending ? "自主实习审核通过后按实际结果继续，无需提前提交企业确认。" : "先获得可用 Offer，再填写确认表；保存草稿不等于已提交。";
        }
        items.add(new PlanItem("confirm", "实习确认", confirmState, confirmDetail, "OPEN_CONFIRMATION"));
        if (latestSelfPractice != null && Set.of("pending", "rejected", "withdrawn").contains(selfPracticeStatus)) {
            String selfPracticeState = switch (selfPracticeStatus) {
                case "rejected" -> "REJECTED";
                case "withdrawn" -> "WITHDRAWN";
                default -> "WAITING";
            };
            String selfPracticeDetail = switch (selfPracticeStatus) {
                case "rejected" -> "自主实习申请已驳回。教师填写的理由："
                        + Objects.toString(latestSelfPractice.getRejectReason(), "未填写") + "。可修改后重新提交。";
                case "withdrawn" -> "你已撤回自主实习申请，可重新填写并提交。";
                default -> "自主实习申请已提交，等待教师审核；教师尚未通过或驳回，可撤回后重新提交。";
            };
            items.add(0, new PlanItem("self_practice",
                    "rejected".equals(selfPracticeStatus) ? "自主实习申请已驳回"
                            : "withdrawn".equals(selfPracticeStatus) ? "自主实习申请已撤回" : "自主实习审核",
                    selfPracticeState, selfPracticeDetail,
                    selfPracticePending ? "OPEN_APPLICATION" : "OPEN_SELF_PRACTICE"));
        }
        if (latestDelay != null) {
            String delayStatus = Objects.toString(latestDelay.getStatus(), "");
            String delayState = switch (delayStatus) {
                case "approved" -> "DONE";
                case "rejected" -> "REJECTED";
                case "withdrawn" -> "WITHDRAWN";
                default -> "WAITING";
            };
            String delayDetail = switch (delayStatus) {
                case "approved" -> "考研延迟申请已通过，请按延期安排与辅导员核对后续时间。";
                case "rejected" -> "考研延迟申请被驳回。教师填写的理由："
                        + Objects.toString(latestDelay.getRejectReason(), "未填写") + "。可修改后重新提交。";
                case "withdrawn" -> "你已撤回考研延迟申请，可重新填写并提交。";
                default -> "考研延迟申请已提交，教师尚未通过或驳回，可撤回后重新提交。";
            };
            items.add(new PlanItem("delay", "考研延迟申请", delayState, delayDetail, "OPEN_DELAY"));
        }
        if (changing) items.add(0, new PlanItem("change", "跟进新岗位", confirming ? "WAITING" : "TODO",
                confirming ? "新岗位确认表已提交，等待企业确认；当前实习在新确认成功前保持原状。" : "已申请新岗位，可在我的申请中跟进；新岗位确认成功后才切换当前实习。", "OPEN_APPLICATION"));
        String materialState = "WAITING";
        String materialDetail = reflectionEnabled
                ? "实习开始后按周期提交心得，鉴定表按教师统一设置的提交时间办理。"
                : "鉴定表按教师统一设置的提交时间办理。";
        if (materialTodo) {
            materialState = "TODO";
            materialDetail = todoList.stream()
                    .filter(t -> "APPRAISAL_SUBMISSION_REQUIRED".equals(t.key())
                            || (reflectionEnabled && "REFLECTION_SUBMISSION_REQUIRED".equals(t.key())))
                    .map(StudentTodoItemDTO::title).distinct()
                    .reduce((a, b) -> a + "；" + b).orElse("有过程材料待提交");
        } else if (ended) {
            materialState = appraisalSubmitted ? "DONE" : "TODO";
            materialDetail = appraisalSubmitted ? "鉴定表已提交，当前没有系统提示的材料待办。" : "实习已结束，尚未查到已提交的鉴定表，请到鉴定页核对材料。";
        }
        items.add(new PlanItem("materials", "过程材料", materialState, materialDetail,
                reflectionEnabled ? "OPEN_REFLECTIONS" : "OPEN_APPRAISAL"));
        items.add(new PlanItem("finish", "实习结束",
                ended ? "DONE" : "WAITING",
                ended ? "实习已结束，可查看评价结果。" : "按实习日历推进，结束后查看评价。",
                "OPEN_HOME"));

        String stage;
        if (interrupted) stage = "INTERRUPTED";
        else if (Integer.valueOf(6).equals(code)) stage = "DELAYED";
        else if (ended) stage = "FINISHED";
        else if (Integer.valueOf(3).equals(code) || (current != null && "IN_PROGRESS".equals(current.getStatus()))) stage = "IN_PROGRESS";
        else if (Integer.valueOf(2).equals(code) || current != null) stage = "ONBOARD";
        else if (code == null || code < 0 || code > 6) stage = "UNKNOWN";
        else if (!profileDone) stage = "PREPARE";
        else if (Integer.valueOf(1).equals(code) || hasPassed || confirming) stage = "CONFIRM";
        else if (hasApplied || hasWaitingInterview) stage = "OFFER";
        else stage = "SEARCH";
        if (interrupted) {
            items.add(0, new PlanItem("interrupt", "中断后续", "TODO", "实习已中断，联系辅导员核对后续安排；换岗需申请新岗位并提交新的确认表。", "OPEN_HOME"));
        }
        if (Integer.valueOf(6).equals(code)) items.add(0, new PlanItem("delay_arrangement", "核对延期安排", "TODO", "当前为延期状态，按已批准的安排与辅导员核对时间；不推测新的截止日期。", "OPEN_APPLICATION"));
        if ("UNKNOWN".equals(stage)) items.add(0, new PlanItem("status_check", "核对实习状态", "TODO", "尚无可核验的当前实习状态，请在首页核对登记或联系辅导员。", "OPEN_HOME"));

        if (degraded.get()) {
            if (currentAssignments.size() > 1) stage = "UNKNOWN";
            items.add(0, new PlanItem("data_sync", "数据同步中",
                    "WAITING", "部分业务数据暂时读取失败，当前待办可能不全，请稍后刷新重试。", "OPEN_HOME"));
        }
        String nextTitle = "全部完成", nextHow = "当前没有待办，关注后续通知即可。";
        for (PlanItem item : items) {
            if (requiresPlanAction(item.state())) { nextTitle = item.title(); nextHow = item.detail(); break; }
        }
        if ("全部完成".equals(nextTitle)) {
            for (PlanItem item : items) {
                if ("WAITING".equals(item.state())) { nextTitle = item.title(); nextHow = item.detail(); break; }
            }
        }
        if (degraded.get()) { nextTitle = "核对最新业务数据"; nextHow = "部分信息尚未读取完整，请稍后刷新；不要依据缺失信息重复提交。"; }

        SnapshotStatus snapshotStatus;
        int version;
        if (degraded.get()) {
            // P5：降级视图不覆盖库里上一次的好快照，重登继续从好快照走；
            // 返回 FAILED 让调用方展示“仅实时结果、请稍后重试”，不谎称已保存。
            log.warn("计划数据不完整，跳过快照持久化 student={}", studentId);
            snapshotStatus = SnapshotStatus.FAILED;
            version = versionOf(studentId);
        } else {
            snapshotStatus = persist(studentId, stage, items);
            version = versionOf(studentId);
        }
        // Ended assignments lose current_flag. Recover the route only from the latest actual ended experience.
        InternshipStageAssignment routePlacement = current;
        if (routePlacement == null && (ended || interrupted)) routePlacement = assignmentList.stream()
                .filter(a -> (ended ? "ENDED" : "INTERRUPTED").equals(a.getStatus()))
                .max(java.util.Comparator.comparing(InternshipStageAssignment::getInternshipEndTime,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                        .thenComparing(InternshipStageAssignment::getId,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
        boolean hasHistory = established || interrupted || Integer.valueOf(6).equals(code)
                || !apps.isEmpty() || !invites.isEmpty() || !confirms.isEmpty() || !selfApps.isEmpty() || !assignmentList.isEmpty();
        boolean selfPracticeRoute = routePlacement != null && "SELF_PRACTICE".equals(routePlacement.getSourceType());
        JourneyBasis basis = new JourneyBasis(!degraded.get(), hasHistory,
                profileDone, established, selfPracticeRoute,
                selfPracticePending, delayPending, confirming, hasPassed || Integer.valueOf(1).equals(code),
                appraisalSubmitted, reflectionEnabled, reflectionEnabled && todoKeys.contains("REFLECTION_SUBMISSION_REQUIRED"),
                observedRoute(routePlacement, code, established, selfPracticeRoute, selfPracticePending, delayPending,
                        confirming, hasPassed || Integer.valueOf(1).equals(code), hasApplied, hasWaitingInterview,
                        apps, invites, confirms, selfApps, assignmentList),
                selfPracticeStatus,
                latestSelfPractice == null ? "" : Objects.toString(latestSelfPractice.getRejectReason(), ""));        return new PlanView(stage, STAGE_LABELS.getOrDefault(stage, stage), items, nextTitle, nextHow,
                version, snapshotStatus == SnapshotStatus.SAVED, snapshotStatus, placementFacts(current), basis);
    }

    /** Derive the route from real student operations when no sidebar preference was saved. */
    static String observedRoute(InternshipStageAssignment routePlacement, Integer code, boolean established,
                                boolean selfPracticeRoute, boolean selfPending, boolean delayPending,
                                boolean confirming, boolean passed, boolean applied, boolean waitingInterview,
                                List<StudentJobApplication> apps, List<InterviewInvitation> invites,
                                List<InternshipConfirmationRecord> confirms, List<StudentApplication> selfApps,
                                List<InternshipStageAssignment> assignments) {
        if (routePlacement != null) return selfPracticeRoute ? "SELF" : "NORMAL";
        if (Integer.valueOf(6).equals(code) || delayPending) return "DELAY";
        if (selfPending) return "SELF";
        if (established || confirming || passed || applied || waitingInterview) return "NORMAL";

        String route = "";
        long latest = Long.MIN_VALUE;
        for (StudentApplication application : selfApps) {
            String type = application.getApplicationType();
            if (!"selfPractice".equals(type) && !"delay".equals(type)) continue;
            long at = latestMillis(application.getApplyTime(), application.getCreateTime(), application.getUpdateTime(), application.getReviewTime());
            if (at >= latest) { latest = at; route = "delay".equals(type) ? "DELAY" : "SELF"; }
        }
        for (StudentJobApplication application : apps) {
            long at = latestMillis(application.getApplyDate(), application.getCreateTime(), application.getUpdateTime());
            if (at >= latest) { latest = at; route = "NORMAL"; }
        }
        for (InterviewInvitation invitation : invites) {
            long at = latestMillis(invitation.getInterviewTime(), invitation.getCreateTime(), invitation.getUpdateTime());
            if (at >= latest) { latest = at; route = "NORMAL"; }
        }
        for (InternshipConfirmationRecord confirmation : confirms) {
            long at = latestMillis(confirmation.getCreateTime(), confirmation.getUpdateTime());
            if (at >= latest) { latest = at; route = "NORMAL"; }
        }
        for (InternshipStageAssignment assignment : assignments) {
            String source = assignment.getSourceType();
            if (source == null || source.isBlank()) continue;
            long at = latestMillis(assignment.getCreateTime(), assignment.getUpdateTime());
            if (at >= latest) { latest = at; route = "SELF_PRACTICE".equals(source) ? "SELF" : "NORMAL"; }
        }
        return route;
    }

    private static long latestMillis(Date... dates) {
        long latest = Long.MIN_VALUE;
        for (Date date : dates) if (date != null) latest = Math.max(latest, date.getTime());
        return latest;
    }

    private static long latestMillis(LocalDateTime... dates) {
        long latest = Long.MIN_VALUE;
        for (LocalDateTime date : dates) if (date != null)
            latest = Math.max(latest, date.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return latest;
    }

    public Map<String, Object> factsOf(PlanView plan) {
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("stage", plan.stage());
        facts.put("stageLabel", plan.stageLabel());
        facts.put("nextTitle", plan.nextTitle());
        facts.put("nextHow", plan.nextHow());
        facts.put("planVersion", plan.version());
        facts.put("snapshotSaved", plan.persisted());
        facts.put("snapshotStatus", plan.snapshotStatus().name());
        facts.put("currentPlacement", plan.currentPlacement());
        return facts;
    }

    private static boolean requiresPlanAction(String state) {
        return "TODO".equals(state) || "REJECTED".equals(state) || "WITHDRAWN".equals(state);
    }

    private static boolean sameTarget(Long companyA, Long positionA, Long companyB, Long positionB) {
        return companyA != null && positionA != null && companyA.equals(companyB) && positionA.equals(positionB);
    }

    private static Map<String, Object> placementFacts(InternshipStageAssignment placement) {
        if (placement == null) return Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", placement.getId()); values.put("stageNo", placement.getStageNo());
        values.put("sequenceNo", placement.getSequenceNo()); values.put("source", placement.getSourceType());
        values.put("status", placement.getStatus()); values.put("companyName", placement.getCompanyName());
        values.put("positionName", placement.getPositionName());
        return values;
    }

    // P5：资料读失败按未知处理（降级、不标完成），调用方据此展示“数据同步中”。
    private List<String> missingProfileFields(Long studentId, AtomicBoolean degraded) {
        try {
            StudentUser u = students.findById(studentId);
            if (u == null) { degraded.set(true); return List.of("个人资料待核实"); }
            List<String> missing = new ArrayList<>();
            String[] values = {u.getName(), u.getStudentId(), u.getPhone(), u.getEmail()};
            String[] labels = {"姓名", "学号", "电话", "邮箱"};
            for(int i=0;i<values.length;i++)if(values[i]==null||values[i].isBlank())missing.add(labels[i]);
            return missing;
        } catch (Exception e) {
            log.warn("读取学生资料失败 student={}", studentId);
            degraded.set(true);
            return List.of("个人资料待核实");
        }
    }

    // P5：任一业务源读取失败只降级、不抛错、不伪装成“没有待办”。
    private <T> List<T> fetchOrEmpty(java.util.function.Supplier<List<T>> call, AtomicBoolean degraded) {
        try {
            List<T> list = call.get();
            return list == null ? List.of() : list;
        } catch (Exception e) {
            degraded.set(true);
            return List.of();
        }
    }

    /** 快照有变化才写；版本冲突时重读一次后覆盖，保证最终一致。 */
    private SnapshotStatus persist(Long studentId, String stage, List<PlanItem> items) {
        String jsonItems;
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (PlanItem item : items) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("key", item.key());
                row.put("title", item.title());
                row.put("state", item.state());
                row.put("detail", item.detail());
                rows.add(row);
            }
            jsonItems = json.writeValueAsString(rows);
        } catch (Exception e) {
            log.warn("计划快照序列化失败 student={}", studentId);
            return SnapshotStatus.FAILED;
        }
        try {
            var expectedItems = json.readTree(jsonItems);
            StudentInternshipPlan stored = plans.findByStudentId(studentId);
            Date now = new Date();
            if (stored == null) {
                StudentInternshipPlan row = new StudentInternshipPlan();
                row.setStudentId(studentId);
                row.setCurrentStage(stage);
                row.setItems(jsonItems);
                row.setVersion(0);
                row.setCreateTime(now);
                row.setUpdateTime(now);
                return plans.insert(row) == 1 ? SnapshotStatus.SAVED : SnapshotStatus.FAILED;
            }
            if (stage.equals(stored.getCurrentStage())
                    && expectedItems.equals(json.readTree(String.valueOf(stored.getItems())))) {
                return SnapshotStatus.UNCHANGED;
            }
            stored.setCurrentStage(stage);
            stored.setItems(jsonItems);
            stored.setUpdateTime(now);
            if (plans.updateByVersion(stored) == 1) return SnapshotStatus.SAVED;
            StudentInternshipPlan fresh = plans.findByStudentId(studentId);
            if (fresh == null) return SnapshotStatus.FAILED;
            if (stage.equals(fresh.getCurrentStage())
                    && expectedItems.equals(json.readTree(String.valueOf(fresh.getItems())))) {
                return SnapshotStatus.UNCHANGED;
            }
            fresh.setCurrentStage(stage);
            fresh.setItems(jsonItems);
            fresh.setUpdateTime(now);
            return plans.updateByVersion(fresh) == 1 ? SnapshotStatus.SAVED : SnapshotStatus.FAILED;
        } catch (Exception e) {
            log.warn("计划快照持久化失败 student={} err={}", studentId, e.getMessage());
            return SnapshotStatus.FAILED;
        }
    }

    private int versionOf(Long studentId) {
        try {
            StudentInternshipPlan stored = plans.findByStudentId(studentId);
            return stored == null || stored.getVersion() == null ? 0 : stored.getVersion();
        } catch (Exception e) {
            return 0;
        }
    }
}
