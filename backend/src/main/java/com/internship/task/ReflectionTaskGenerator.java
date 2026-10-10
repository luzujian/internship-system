package com.internship.task;

import com.internship.entity.InternshipReflection;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.User;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.InternshipCalendarService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 按全局实习日历生成心得任务。换岗不会重置期次，同一学生同一期最多一条任务。
 */
@Slf4j
@Component
public class ReflectionTaskGenerator {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> REFLECTION_ASSIGNMENT_STATUSES =
            Set.of("CONFIRMED", "IN_PROGRESS", "REPLACED", "INTERRUPTED", "ENDED");

    @Autowired
    private InternshipCalendarService calendarService;

    @Autowired
    private InternshipStageAssignmentService assignmentService;

    @Autowired
    private StudentInternshipStatusService statusService;

    @Autowired
    private ClassCounselorRelationService classCounselorRelationService;

    @Autowired
    private CounselorAISettingsService counselorAISettingsService;

    @Autowired
    private InternshipReflectionMapper reflectionMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private com.internship.service.AuditLogService auditLogService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void generateReflectionTasks() {
        PeriodWindow window = currentWindow(calendarService.getCalendar(), LocalDate.now(BUSINESS_ZONE));
        if (window == null) {
            log.info("当前不在已配置的全局实习日历内，跳过心得任务生成");
            return;
        }

        List<InternshipStageAssignment> assignments = assignmentService.findAll();
        if (assignments == null || assignments.isEmpty()) return;

        Map<Long, InternshipStageAssignment> placements = assignments.stream()
                .filter(this::isReflectionPlacement)
                .filter(item -> overlaps(item, window))
                .collect(Collectors.toMap(
                        InternshipStageAssignment::getStudentId,
                        Function.identity(),
                        this::laterPlacement));

        int generated = 0;
        for (InternshipStageAssignment placement : placements.values()) {
            Long counselorId = classCounselorRelationService
                    .findCounselorIdByStudentId(placement.getStudentId());
            if (counselorId != null && createTaskIfMissing(placement, counselorId, window)) {
                generated++;
            }
        }
        log.info("全局第 {} 期心得任务生成完成，共生成 {} 条", window.periodNumber(), generated);

        // 后台任务以 SYSTEM 身份记录自动状态变更（每批次汇总一条）
        if (generated > 0) {
            try {
                auditLogService.recordSystemOperation("INTERNSHIP_REFLECTION", "CREATE",
                        "定时生成第" + window.periodNumber() + "期心得任务", "LOW", generated);
            } catch (Exception auditEx) {
                log.error("记录心得任务生成审计失败: {}", auditEx.getMessage());
            }
        }
    }

    /**
     * 提交心得后不再按“提交时间 + 周期”滚动创建下一期；仅补齐当前全局期次的缺失任务。
     */
    public void generateNextTask(Long studentId, Long counselorId) {
        if (studentId == null || counselorId == null) return;
        PeriodWindow window = currentWindow(calendarService.getCalendar(), LocalDate.now(BUSINESS_ZONE));
        if (window == null) return;

        List<InternshipStageAssignment> assignments = assignmentService.findByStudentId(studentId);
        if (assignments == null) return;
        assignments.stream()
                .filter(this::isReflectionPlacement)
                .filter(item -> overlaps(item, window))
                .max(Comparator
                        .comparing(InternshipStageAssignment::getInternshipStartTime,
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(InternshipStageAssignment::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .ifPresent(placement -> createTaskIfMissing(placement, counselorId, window));
    }

    private boolean createTaskIfMissing(InternshipStageAssignment placement,
                                        Long counselorId,
                                        PeriodWindow window) {
        Long studentId = placement.getStudentId();
        if (reflectionMapper.existsByStudentIdAndPeriodNumber(studentId, window.periodNumber())) {
            return false;
        }
        if (!counselorAISettingsService.isAiScoringEnabled(counselorId)) {
            return false;
        }

        User student = userService.findById(studentId);
        if (student == null) {
            log.warn("学生 {} 不存在，跳过心得任务生成", studentId);
            return false;
        }
        StudentInternshipStatus status = statusService.findByStudentId(studentId);
        Date now = new Date();
        InternshipReflection reflection = new InternshipReflection();
        reflection.setStudentId(studentId);
        reflection.setStudentName(student.getName());
        reflection.setStudentUserId(student.getUsername());
        reflection.setInternshipStatusId(status == null ? null : status.getId());
        reflection.setStageAssignmentId(placement.getId());
        reflection.setPeriodNumber(window.periodNumber());
        reflection.setTaskType("auto");
        reflection.setDeadline(toDate(window.endDate(), LocalTime.MAX));
        reflection.setCounselorId(counselorId);
        reflection.setStatus(0);
        reflection.setRemark("0");
        reflection.setSubmitTime(now);
        reflection.setCreateTime(now);
        reflection.setUpdateTime(now);
        reflection.setDeleted(0);
        reflection.setContent("");
        return reflectionMapper.insert(reflection) == 1;
    }

    private PeriodWindow currentWindow(InternshipCalendar calendar, LocalDate today) {
        if (calendar == null || !hasText(calendar.getStartDate()) || !hasText(calendar.getEndDate())
                || calendar.getReportCycle() == null || calendar.getReportCycle() <= 0) {
            return null;
        }
        try {
            LocalDate globalStart = LocalDate.parse(calendar.getStartDate());
            LocalDate globalEnd = LocalDate.parse(calendar.getEndDate());
            if (today.isBefore(globalStart) || today.isAfter(globalEnd)) return null;
            int period = (int) (ChronoUnit.DAYS.between(globalStart, today)
                    / calendar.getReportCycle()) + 1;
            LocalDate start = globalStart.plusDays((long) (period - 1) * calendar.getReportCycle());
            LocalDate end = start.plusDays(calendar.getReportCycle() - 1L);
            if (end.isAfter(globalEnd)) end = globalEnd;
            return new PeriodWindow(period, start, end);
        } catch (RuntimeException e) {
            log.warn("全局实习日历配置无效，跳过心得任务生成: {}", e.getMessage());
            return null;
        }
    }

    private boolean isReflectionPlacement(InternshipStageAssignment assignment) {
        return assignment != null
                && assignment.getStudentId() != null
                && assignment.getInternshipStartTime() != null
                && REFLECTION_ASSIGNMENT_STATUSES.contains(assignment.getStatus());
    }

    private boolean overlaps(InternshipStageAssignment assignment, PeriodWindow window) {
        LocalDate start = toLocalDate(assignment.getInternshipStartTime());
        LocalDate end = assignment.getInternshipEndTime() == null
                ? window.endDate()
                : toLocalDate(assignment.getInternshipEndTime());
        return !start.isAfter(window.endDate()) && !end.isBefore(window.startDate());
    }

    private InternshipStageAssignment laterPlacement(InternshipStageAssignment first,
                                                      InternshipStageAssignment second) {
        Comparator<InternshipStageAssignment> comparator = Comparator
                .comparing(InternshipStageAssignment::getInternshipStartTime,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(InternshipStageAssignment::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder()));
        return comparator.compare(first, second) >= 0 ? first : second;
    }

    private LocalDate toLocalDate(Date value) {
        return value.toInstant().atZone(BUSINESS_ZONE).toLocalDate();
    }

    private Date toDate(LocalDate date, LocalTime time) {
        return Date.from(LocalDateTime.of(date, time).atZone(BUSINESS_ZONE).toInstant());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record PeriodWindow(int periodNumber, LocalDate startDate, LocalDate endDate) {
    }
}
