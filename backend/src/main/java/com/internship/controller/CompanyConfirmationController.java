package com.internship.controller;

import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipProgressRecord;
import com.internship.entity.InternshipTimeSettings;
import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.User;
import com.internship.mapper.PositionMapper;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.InternshipTimeSettingsService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.UserService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/company/confirmation")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyConfirmationController {

    @Autowired
    private UserService userService;

    @Autowired
    private InternshipConfirmationRecordService confirmationRecordService;

    @Autowired
    private StudentInternshipStatusService internshipStatusService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private StudentJobApplicationService studentJobApplicationService;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    /**
     * 推送企业待办数据更新
     */
    private void pushTodoUpdate(Long companyId) {
        try {
            // 待处理申请数
            List<com.internship.entity.StudentJobApplication> applications = studentJobApplicationService.findByCompanyId(companyId);
            long pendingApplications = applications.stream()
                    .filter(a -> "pending".equals(a.getStatus()))
                    .count();

            // 待确认实习表数 - 从学生实习状态表查询（company_confirm_status=0表示待确认）
            List<StudentInternshipStatus> allStatuses = internshipStatusService.list(null, null, null, null, companyId, null, null, null, null, null);
            long pendingConfirmations = allStatuses.stream()
                    .filter(s -> s.getCompanyConfirmStatus() != null && s.getCompanyConfirmStatus() == 0)
                    .count();

            webSocketHandler.sendCompanyTodoUpdate(companyId, pendingApplications, pendingConfirmations);
        } catch (Exception e) {
            log.error("推送待办数据失败: {}", e.getMessage());
        }
    }

    /**
     * 计算并设置学生实习的开始和结束时间
     * 开始时间 = max(岗位的 internshipStartDate, 当前时刻)，岗位无日期时用教师 startDate 兜底
     * 结束时间 = 教师设置的 endDate 作为上界
     * 若该生有前一段实习（同 studentId，status ≥ 2，不同 id），则将其结束时间截断为本段开始时间
     */
    private void calculateAndSetInternshipTimes(StudentInternshipStatus status) {
        try {
            // 1. 查询岗位信息
            Position position = positionMapper.findById(status.getPositionId());

            // 2. 查询教师时间设置
            InternshipTimeSettings settings = internshipTimeSettingsService.findLatest();

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            Date now = new Date();

            // 3. 计算开始时间：max(岗位开始日期, 当前时刻)，岗位无日期时用教师 startDate 兜底
            Date startTime;
            if (position != null && position.getInternshipStartDate() != null) {
                Date positionStart = position.getInternshipStartDate();
                startTime = positionStart.after(now) ? positionStart : now;
                log.info("使用岗位开始日期计算: positionStart={}, now={}, result={}", positionStart, now, startTime);
            } else if (settings != null && settings.getStartDate() != null && !settings.getStartDate().isEmpty()) {
                Date teacherStartDate = sdf.parse(settings.getStartDate());
                startTime = teacherStartDate.after(now) ? teacherStartDate : now;
                log.info("岗位无开始日期，使用教师 startDate 兜底: teacherStartDate={}, now={}, result={}", teacherStartDate, now, startTime);
            } else {
                startTime = now;
                log.info("无岗位日期也无教师设置，使用当前时间: {}", now);
            }
            status.setInternshipStartTime(startTime);

            // 4. 计算结束时间（上界）：教师设置的 endDate
            Date teacherEndDate = null;
            if (settings != null && settings.getEndDate() != null && !settings.getEndDate().isEmpty()) {
                teacherEndDate = sdf.parse(settings.getEndDate());
                status.setInternshipEndTime(teacherEndDate);
                log.info("设置实习结束时间上界: {}", teacherEndDate);
            }

            // 5. 查询该生是否有前一段实习（同 studentId，status ≥ 2，不同 id），若有则截断其结束时间
            if (teacherEndDate != null) {
                List<StudentInternshipStatus> allStatuses = internshipStatusService.list(
                    status.getStudentId(), null, null, null, null, null, null, null, null, null);
                if (allStatuses != null) {
                    for (StudentInternshipStatus prev : allStatuses) {
                        if (!prev.getId().equals(status.getId())
                            && prev.getStatus() != null && prev.getStatus() >= 2) {
                            // 前一段 endTime = min(本段 startTime, 教师 endDate)
                            Date prevEndTime = startTime.before(teacherEndDate) ? startTime : teacherEndDate;
                            prev.setInternshipEndTime(prevEndTime);
                            internshipStatusService.update(prev);
                            log.info("截断前一段实习 {} 的结束时间为 {}（本段开始={}, 教师上界={}）",
                                prev.getId(), prevEndTime, startTime, teacherEndDate);
                        }
                    }
                }
            }

            // 6. 保存本段实习的时间
            internshipStatusService.update(status);
            log.info("实习时间计算完成: statusId={}, studentId={}, startTime={}, endTime={}",
                status.getId(), status.getStudentId(), status.getInternshipStartTime(), status.getInternshipEndTime());
        } catch (Exception e) {
            log.error("计算实习时间失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 获取待确认的实习确认记录列表
     */
    @GetMapping("/pending")
    public Result getPendingConfirmations() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            Long companyId = user.getId();
            // 查询该公司下的所有待确认(status=0)的记录
            List<InternshipConfirmationRecord> records = confirmationRecordService.findByCompanyIdAndStatus(companyId, 0);

            return Result.success(records);
        } catch (Exception e) {
            log.error("获取待确认记录失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 获取所有实习确认记录（不分状态）
     */
    @GetMapping("/all")
    public Result getAllConfirmations() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            Long companyId = user.getId();
            // 查询该公司下的所有记录
            List<InternshipConfirmationRecord> records = confirmationRecordService.findByCompanyId(companyId);

            return Result.success(records);
        } catch (Exception e) {
            log.error("获取确认记录失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 确认实习记录
     */
    @PostMapping("/confirm/{id}")
    public Result confirm(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            InternshipConfirmationRecord record = confirmationRecordService.findById(id);
            if (record == null) {
                return Result.error("记录不存在");
            }

            // 验证是否为该公司的记录
            if (!user.getId().equals(record.getCompanyId())) {
                return Result.error("无权操作此记录");
            }

            // 更新确认记录状态为已确认(1)
            record.setStatus(1);
            confirmationRecordService.update(record);

            // 同步更新 student_internship_status（当前实习状态）
            StudentInternshipStatus status = internshipStatusService.findByStudentId(record.getStudentId());
            if (status == null) {
                status = new StudentInternshipStatus();
                status.setStudentId(record.getStudentId());
                status.setCreateTime(new java.util.Date());
            }
            // 更新实习状态为已确定(2)
            status.setStatus(2);
            // 更新公司信息
            status.setCompanyId(record.getCompanyId());
            status.setCompanyName(record.getCompanyName());
            status.setPositionId(record.getPositionId());
            status.setPositionName(record.getPositionName());
            status.setCompanyAddress(record.getCompanyAddress());
            status.setCompanyPhone(record.getCompanyPhone());
            status.setInternshipDuration(record.getInternshipDuration());
            // 重置企业确认状态
            status.setCompanyConfirmStatus(1);

            if (status.getId() == null) {
                internshipStatusService.insert(status);
            } else {
                internshipStatusService.update(status);
            }

            // 计算并设置实习开始/结束时间（替代原来的直接拷贝确认表时间）
            calculateAndSetInternshipTimes(status);

            // 更新实习进展记录状态为已确认
            progressRecordService.updateStatusByRelatedId(record.getId(), "internship_confirmation", "success");

            // 推送待办数据更新
            pushTodoUpdate(user.getId());

            // 推送实习确认结果给学生
            log.info("准备向学生推送确认结果, studentId={}, company={}, position={}",
                record.getStudentId(), record.getCompanyName(), record.getPositionName());
            webSocketHandler.sendConfirmationResultToStudent(
                record.getStudentId(),
                1, // 1=已确认
                record.getCompanyName(),
                record.getPositionName()
            );
            log.info("已调用sendConfirmationResultToStudent");

            return Result.success("确认成功");
        } catch (Exception e) {
            log.error("确认失败: {}", e.getMessage(), e);
            return Result.error("确认失败");
        }
    }

    /**
     * 拒绝实习记录
     */
    @PostMapping("/reject/{id}")
    public Result reject(@PathVariable Long id, @RequestParam String reason) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            InternshipConfirmationRecord record = confirmationRecordService.findById(id);
            if (record == null) {
                return Result.error("记录不存在");
            }

            // 验证是否为该公司的记录
            if (!user.getId().equals(record.getCompanyId())) {
                return Result.error("无权操作此记录");
            }

            // 更新状态为已拒绝(2)，备注存储拒绝原因
            record.setStatus(2);
            record.setRemark(reason);
            confirmationRecordService.update(record);

            // 更新实习进展记录状态为已拒绝
            progressRecordService.updateStatusByRelatedId(record.getId(), "internship_confirmation", "failed");

            // 推送待办数据更新
            pushTodoUpdate(user.getId());

            // 推送实习确认结果给学生（拒绝）
            webSocketHandler.sendConfirmationResultToStudent(
                record.getStudentId(),
                2, // 2=已拒绝
                record.getCompanyName(),
                record.getPositionName()
            );

            return Result.success("已拒绝");
        } catch (Exception e) {
            log.error("拒绝失败: {}", e.getMessage(), e);
            return Result.error("拒绝失败");
        }
    }
}
