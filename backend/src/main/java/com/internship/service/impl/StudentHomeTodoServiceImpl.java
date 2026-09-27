package com.internship.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.InternshipAppraisalSubmission;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipReflection;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.InterviewInvitation;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.SystemConfig;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.entity.dto.RecentActivityActionDTO;
import com.internship.entity.dto.StudentTodoItemDTO;
import com.internship.service.InternshipAppraisalService;
import com.internship.service.InternshipCalendarService;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipReflectionService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.InterviewInvitationService;
import com.internship.service.StudentHomeTodoService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.SystemConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 学生首页可操作待办。岗位申请和换岗不受阶段或时间窗口限制；心得与鉴定表只使用全局实习日历。
 */
@Service
public class StudentHomeTodoServiceImpl implements StudentHomeTodoService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private static final Map<String, Integer> PRIORITY_ORDER = Map.of(
            "urgent", 0,
            "high", 1,
            "normal", 2);
    private static final Set<String> PLACEMENT_STATUSES =
            Set.of("CONFIRMED", "REPLACED", "INTERRUPTED", "ENDED");

    private final InterviewInvitationService interviewInvitationService;
    private final StudentJobApplicationService jobApplicationService;
    private final InternshipConfirmationRecordService confirmationRecordService;
    private final InternshipStageAssignmentService stageAssignmentService;
    private final InternshipCalendarService calendarService;
    private final InternshipReflectionService reflectionService;
    private final InternshipAppraisalService appraisalService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public StudentHomeTodoServiceImpl(InterviewInvitationService interviewInvitationService,
                                      StudentJobApplicationService jobApplicationService,
                                      InternshipConfirmationRecordService confirmationRecordService,
                                      InternshipStageAssignmentService stageAssignmentService,
                                      InternshipCalendarService calendarService,
                                      InternshipReflectionService reflectionService,
                                      InternshipAppraisalService appraisalService,
                                      SystemConfigService systemConfigService) {
        this.interviewInvitationService = interviewInvitationService;
        this.jobApplicationService = jobApplicationService;
        this.confirmationRecordService = confirmationRecordService;
        this.stageAssignmentService = stageAssignmentService;
        this.calendarService = calendarService;
        this.reflectionService = reflectionService;
        this.appraisalService = appraisalService;
        this.systemConfigService = systemConfigService;
    }

    /** 找不到菜单配置或配置解析失败时维持原有的默认可见行为。 */
    private boolean isStudentMenuVisible(String menuPath) {
        try {
            SystemConfig config = systemConfigService.findByConfigKey("STUDENT_MENU_CONFIG");
            if (config == null || config.getConfigValue() == null) return true;
            List<Map<String, Object>> menus = objectMapper.readValue(
                    config.getConfigValue(), new TypeReference<List<Map<String, Object>>>() { });
            for (Map<String, Object> menu : menus) {
                if (menuPath.equals(String.valueOf(menu.get("path")))) {
                    return !Boolean.FALSE.equals(menu.get("visible"));
                }
            }
        } catch (Exception ignored) {
            // 菜单配置不应阻断首页待办。
        }
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentTodoItemDTO> getActionableTodos(Long studentId) {
        if (studentId == null) return List.of();

        OffsetDateTime now = OffsetDateTime.now(BUSINESS_ZONE);
        List<InternshipStageAssignment> placements = safeList(stageAssignmentService.findByStudentId(studentId));
        List<InternshipConfirmationRecord> confirmations = safeList(
                confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(studentId));
        List<StudentJobApplication> applications = safeList(jobApplicationService.findByStudentId(studentId));
        List<InterviewInvitation> invitations = safeList(interviewInvitationService.findByStudentId(studentId));
        InternshipCalendar calendar = calendarService.getCalendar();

        List<StudentTodoItemDTO> todos = new ArrayList<>();
        addPendingInterviews(todos, invitations, now);

        InternshipConfirmationRecord pending = confirmations.stream()
                .filter(this::isSubmittedPendingConfirmation)
                .max(Comparator.comparing(InternshipConfirmationRecord::getCreateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (pending != null) {
            addPendingConfirmation(todos, pending);
        } else {
            addOfferTodos(todos, invitations, applications, confirmations, placements, now);
        }

        addReflectionSubmission(todos, studentId, placements, confirmations, calendar, now);
        addAppraisalSubmission(todos, studentId, placements, calendar, now);

        LinkedHashMap<String, StudentTodoItemDTO> deduplicated = new LinkedHashMap<>();
        todos.stream()
                .filter(item -> item.action() != null && item.action().key() != null)
                .sorted(todoComparator())
                .forEach(item -> deduplicated.putIfAbsent(item.id(), item));
        return List.copyOf(deduplicated.values());
    }

    private void addPendingInterviews(List<StudentTodoItemDTO> todos,
                                      List<InterviewInvitation> invitations,
                                      OffsetDateTime now) {
        Set<String> seen = new HashSet<>();
        invitations.stream()
                .filter(item -> "pending_interview".equals(item.getStatus()))
                .filter(this::hasValidInterviewTarget)
                .sorted(Comparator.comparing(InterviewInvitation::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(item -> seen.add(companyPositionKey(item)))
                .filter(item -> item.getInterviewTime() == null
                        || !toOffset(item.getInterviewTime()).isBefore(now))
                .forEach(invitation -> {
                    OffsetDateTime dueAt = toOffset(invitation.getInterviewTime());
                    todos.add(new StudentTodoItemDTO(
                            "interview:" + invitation.getId(),
                            "INTERVIEW_SCHEDULED",
                            "参加面试",
                            placementSummary(invitation.getCompanyName(), invitation.getPositionName()),
                            priorityForDeadline(dueAt, now, 1),
                            dueAt,
                            null,
                            action("VIEW_INTERVIEW", invitation.getId(), Map.of())));
                });
    }

    private void addPendingConfirmation(List<StudentTodoItemDTO> todos,
                                        InternshipConfirmationRecord record) {
        if (!hasValidConfirmationTarget(record)) return;
        todos.add(new StudentTodoItemDTO(
                "confirmation-pending:" + record.getId(),
                "CONFIRMATION_PENDING",
                "实习确认表待企业确认",
                placementSummary(record.getCompanyName(), record.getPositionName()),
                "normal",
                null,
                null,
                action("VIEW_CONFIRMATION", record.getId(), Map.of())));
    }

    private void addOfferTodos(List<StudentTodoItemDTO> todos,
                               List<InterviewInvitation> invitations,
                               List<StudentJobApplication> applications,
                               List<InternshipConfirmationRecord> confirmations,
                               List<InternshipStageAssignment> placements,
                               OffsetDateTime now) {
        Map<String, StudentJobApplication> latestApplications = applications.stream()
                .filter(this::hasValidApplicationTarget)
                .collect(Collectors.toMap(this::companyPositionKey, Function.identity(),
                        StudentHomeTodoServiceImpl::newerApplication));
        Map<String, InternshipConfirmationRecord> latestConfirmations = confirmations.stream()
                .filter(this::hasValidConfirmationTarget)
                .filter(item -> !Integer.valueOf(2).equals(item.getRecallStatus()))
                .collect(Collectors.toMap(this::companyPositionKey, Function.identity(),
                        StudentHomeTodoServiceImpl::newerConfirmation));
        Set<String> handledPlacements = placements.stream()
                .filter(item -> item.getCompanyId() != null && item.getPositionId() != null)
                .filter(item -> item.getStatus() != null
                        && !"PENDING_CONFIRMATION".equals(item.getStatus())
                        && !"REJECTED".equals(item.getStatus()))
                .map(this::companyPositionKey)
                .collect(Collectors.toSet());

        Set<String> seen = new HashSet<>();
        invitations.stream()
                .filter(item -> "interview_passed".equals(item.getStatus()))
                .filter(this::hasValidInterviewTarget)
                .sorted(Comparator.comparing(InterviewInvitation::getId,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .filter(item -> seen.add(companyPositionKey(item)))
                .forEach(invitation -> {
                    String offerKey = companyPositionKey(invitation);
                    InternshipConfirmationRecord latest = latestConfirmations.get(offerKey);
                    if (latest != null && Integer.valueOf(1).equals(latest.getStatus())) return;
                    if (latest == null && handledPlacements.contains(offerKey)) return;

                    if (latest != null && isRejectedConfirmation(latest)) {
                        String description = isBlank(latest.getRemark())
                                ? placementSummary(latest.getCompanyName(), latest.getPositionName())
                                : "驳回原因：" + latest.getRemark();
                        todos.add(new StudentTodoItemDTO(
                                "confirmation-rejected:" + latest.getId(),
                                "CONFIRMATION_RESUBMISSION_REQUIRED",
                                "修改并重新提交实习确认表",
                                description,
                                "high",
                                null,
                                null,
                                action("RESUBMIT_CONFIRMATION", latest.getId(), Map.of())));
                        return;
                    }

                    StudentJobApplication application = latestApplications.get(offerKey);
                    Map<String, Object> params = new HashMap<>();
                    params.put("offerId", invitation.getId());
                    params.put("interviewId", invitation.getId());
                    params.put("companyId", invitation.getCompanyId());
                    params.put("positionId", invitation.getPositionId());
                    if (application != null && application.getId() != null) {
                        params.put("applicationId", application.getId());
                    }
                    todos.add(new StudentTodoItemDTO(
                            "offer:" + invitation.getId(),
                            "OFFER_CONFIRMATION_REQUIRED",
                            "确认可选 Offer",
                            placementSummary(invitation.getCompanyName(), invitation.getPositionName()),
                            priorityForDeadline(null, now, 3),
                            null,
                            null,
                            action("START_CONFIRMATION", invitation.getId(), params)));
                });
    }

    private void addReflectionSubmission(List<StudentTodoItemDTO> todos,
                                         Long studentId,
                                         List<InternshipStageAssignment> placements,
                                         List<InternshipConfirmationRecord> confirmations,
                                         InternshipCalendar calendar,
                                         OffsetDateTime now) {
        if (!isStudentMenuVisible("/student/internships")) return;
        if (confirmations.stream().noneMatch(this::isConfirmed)) return;
        Integer periodNumber = reflectionService.calculatePeriodNumber(Date.from(now.toInstant()));
        if (periodNumber == null) return;

        InternshipStageAssignment currentPlacement = placementForDate(placements, now.toLocalDate());
        if (currentPlacement == null || currentPlacement.getId() == null) return;

        List<InternshipReflection> currentPeriod = safeList(
                reflectionService.list(studentId, null, null, null)).stream()
                .filter(item -> Objects.equals(periodNumber, item.getPeriodNumber()))
                .filter(item -> !Integer.valueOf(1).equals(item.getDeleted()))
                .toList();
        InternshipReflection draft = currentPeriod.stream()
                .filter(this::isSubmittableDraft)
                .max(Comparator.comparing(InternshipReflection::getUpdateTime,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
        if (draft == null && !currentPeriod.isEmpty()) return;

        OffsetDateTime dueAt = draft == null
                ? reflectionDueAt(calendar, periodNumber)
                : toOffset(draft.getDeadline());
        Long targetId = draft == null ? null : draft.getId();
        Map<String, Object> params = new HashMap<>();
        params.put("periodNumber", periodNumber);
        params.put("placementId", currentPlacement.getId());
        todos.add(new StudentTodoItemDTO(
                "reflection:" + periodNumber,
                "REFLECTION_SUBMISSION_REQUIRED",
                "第" + periodNumber + "期实习心得待提交",
                placementSummary(currentPlacement.getCompanyName(), currentPlacement.getPositionName()),
                priorityForDeadline(dueAt, now, 2),
                dueAt,
                null,
                action("SUBMIT_REFLECTION", targetId, params)));
    }

    private void addAppraisalSubmission(List<StudentTodoItemDTO> todos,
                                        Long studentId,
                                        List<InternshipStageAssignment> placements,
                                        InternshipCalendar calendar,
                                        OffsetDateTime now) {
        if (!isStudentMenuVisible("/student/internship-appraisal")) return;
        CalendarDates dates = calendarDates(calendar);
        if (dates == null || now.toLocalDate().isBefore(dates.endDate())) return;
        if (dates.appraisalDeadline() != null
                && now.toLocalDate().isAfter(dates.appraisalDeadline())) return;

        Date evidenceBoundary = Date.from(dates.endDate().atTime(LocalTime.MAX)
                .atZone(BUSINESS_ZONE).toInstant());
        InternshipStageAssignment latestPlacement = placements.stream()
                .filter(item -> isRatablePlacement(item, evidenceBoundary))
                .max(Comparator
                        .comparing(InternshipStageAssignment::getInternshipStartTime)
                        .thenComparing(InternshipStageAssignment::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (latestPlacement == null || latestPlacement.getId() == null) return;

        InternshipAppraisalSubmission submission = appraisalService.findExistingSubmission(studentId);
        if (submission != null && !"DRAFT".equals(submission.getStatus())
                && !"RETURNED".equals(submission.getStatus())) return;
        Long targetId = submission == null || submission.getId() == null
                ? latestPlacement.getId() : submission.getId();
        int fileCount = submission == null || submission.getFiles() == null
                ? 0 : submission.getFiles().size();
        String description = fileCount > 0
                ? "已上传" + fileCount + "份材料，请检查后提交"
                : "请上传鉴定材料并完成实际实习企业评分";
        OffsetDateTime dueAt = endOfDay(dates.appraisalDeadline());
        todos.add(new StudentTodoItemDTO(
                "appraisal:" + targetId,
                "APPRAISAL_SUBMISSION_REQUIRED",
                submission != null && "RETURNED".equals(submission.getStatus())
                        ? "修改并重新提交实习鉴定表" : "提交实习鉴定表",
                description,
                priorityForDeadline(dueAt, now, 2),
                dueAt,
                null,
                action("SUBMIT_APPRAISAL", targetId,
                        Map.of("placementId", latestPlacement.getId()))));
    }

    private InternshipStageAssignment placementForDate(List<InternshipStageAssignment> placements,
                                                        LocalDate date) {
        return placements.stream()
                .filter(item -> item.getInternshipStartTime() != null
                        && PLACEMENT_STATUSES.contains(item.getStatus()))
                .filter(item -> {
                    LocalDate start = toLocalDate(item.getInternshipStartTime());
                    LocalDate end = item.getInternshipEndTime() == null
                            ? null : toLocalDate(item.getInternshipEndTime());
                    return !date.isBefore(start) && (end == null || !date.isAfter(end));
                })
                .max(Comparator
                        .comparing(InternshipStageAssignment::getInternshipStartTime)
                        .thenComparing(InternshipStageAssignment::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private boolean isRatablePlacement(InternshipStageAssignment placement, Date evidenceBoundary) {
        Date effectiveEnd = placement == null ? null : placement.getInternshipEndTime();
        if (effectiveEnd == null || effectiveEnd.after(evidenceBoundary)) {
            effectiveEnd = evidenceBoundary;
        }
        return placement != null
                && placement.getCompanyId() != null
                && PLACEMENT_STATUSES.contains(placement.getStatus())
                && placement.getInternshipStartTime() != null
                && effectiveEnd.after(placement.getInternshipStartTime());
    }

    private boolean isSubmittedPendingConfirmation(InternshipConfirmationRecord record) {
        return record != null && Integer.valueOf(0).equals(record.getStatus())
                && Integer.valueOf(0).equals(record.getRecallStatus());
    }

    private boolean isConfirmed(InternshipConfirmationRecord record) {
        return record != null && Integer.valueOf(1).equals(record.getStatus())
                && !Integer.valueOf(2).equals(record.getRecallStatus());
    }

    private boolean isRejectedConfirmation(InternshipConfirmationRecord record) {
        return record != null && Integer.valueOf(2).equals(record.getStatus())
                && !Integer.valueOf(2).equals(record.getRecallStatus());
    }

    private boolean isSubmittableDraft(InternshipReflection reflection) {
        return reflection != null && reflection.getId() != null
                && Integer.valueOf(0).equals(reflection.getStatus())
                && "0".equals(reflection.getRemark());
    }

    private boolean hasValidInterviewTarget(InterviewInvitation invitation) {
        return invitation != null && invitation.getId() != null && invitation.getCompanyId() != null
                && invitation.getPositionId() != null && !isBlank(invitation.getCompanyName())
                && !isBlank(invitation.getPositionName());
    }

    private boolean hasValidApplicationTarget(StudentJobApplication application) {
        return application != null && application.getId() != null && application.getCompanyId() != null
                && application.getPositionId() != null;
    }

    private boolean hasValidConfirmationTarget(InternshipConfirmationRecord record) {
        return record != null && record.getId() != null && record.getCompanyId() != null
                && record.getPositionId() != null;
    }

    private CalendarDates calendarDates(InternshipCalendar calendar) {
        if (calendar == null) return null;
        LocalDate start = parseDate(calendar.getStartDate());
        LocalDate end = parseDate(calendar.getEndDate());
        if (start == null || end == null || end.isBefore(start)) return null;
        return new CalendarDates(start, end, parseDate(calendar.getAppraisalDeadline()));
    }

    private OffsetDateTime reflectionDueAt(InternshipCalendar calendar, int periodNumber) {
        CalendarDates dates = calendarDates(calendar);
        if (dates == null || calendar.getReportCycle() == null || calendar.getReportCycle() <= 0) {
            return null;
        }
        LocalDate dueDate = dates.startDate()
                .plusDays((long) calendar.getReportCycle() * periodNumber - 1L);
        if (dueDate.isAfter(dates.endDate())) dueDate = dates.endDate();
        return endOfDay(dueDate);
    }

    private OffsetDateTime endOfDay(LocalDate date) {
        return date == null ? null
                : date.atTime(LocalTime.MAX).atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private LocalDate parseDate(String value) {
        if (isBlank(value)) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private LocalDate toLocalDate(Date value) {
        return value.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
    }

    private OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private OffsetDateTime toOffset(Date value) {
        return value == null ? null : value.toInstant().atZone(BUSINESS_ZONE).toOffsetDateTime();
    }

    private String priorityForDeadline(OffsetDateTime dueAt, OffsetDateTime now, int urgentWithinDays) {
        if (dueAt == null) return "high";
        long hours = ChronoUnit.HOURS.between(now, dueAt);
        return hours <= urgentWithinDays * 24L ? "urgent" : "high";
    }

    private Comparator<StudentTodoItemDTO> todoComparator() {
        return Comparator
                .comparingInt((StudentTodoItemDTO item) -> PRIORITY_ORDER.getOrDefault(item.priority(), 99))
                .thenComparing(StudentTodoItemDTO::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(StudentTodoItemDTO::key)
                .thenComparing(StudentTodoItemDTO::id);
    }

    private RecentActivityActionDTO action(String key, Long targetId, Map<String, Object> params) {
        return new RecentActivityActionDTO(key, targetId, Map.copyOf(params));
    }

    private String placementSummary(String companyName, String positionName) {
        if (isBlank(companyName)) return positionName == null ? "" : positionName;
        if (isBlank(positionName)) return companyName;
        return companyName + " · " + positionName;
    }

    private String companyPositionKey(InterviewInvitation invitation) {
        return invitation.getCompanyId() + ":" + invitation.getPositionId();
    }

    private String companyPositionKey(StudentJobApplication application) {
        return application.getCompanyId() + ":" + application.getPositionId();
    }

    private String companyPositionKey(InternshipConfirmationRecord record) {
        return record.getCompanyId() + ":" + record.getPositionId();
    }

    private String companyPositionKey(InternshipStageAssignment placement) {
        return placement.getCompanyId() + ":" + placement.getPositionId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static StudentJobApplication newerApplication(StudentJobApplication first,
                                                           StudentJobApplication second) {
        if (first.getId() == null) return second;
        if (second.getId() == null) return first;
        return first.getId() >= second.getId() ? first : second;
    }

    private static InternshipConfirmationRecord newerConfirmation(InternshipConfirmationRecord first,
                                                                   InternshipConfirmationRecord second) {
        if (first.getId() == null) return second;
        if (second.getId() == null) return first;
        return first.getId() >= second.getId() ? first : second;
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    private record CalendarDates(LocalDate startDate,
                                 LocalDate endDate,
                                 LocalDate appraisalDeadline) {
    }
}
