package com.gdmu.controller;

import com.gdmu.entity.InternshipConfirmationRecord;
import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentApplication;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.entity.StudentUser;
import com.gdmu.entity.User;
import com.gdmu.service.InternshipConfirmationRecordService;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.InternshipTimeSettingsService;
import com.gdmu.service.StudentApplicationService;
import com.gdmu.service.StudentInternshipStatusService;
import com.gdmu.service.StudentUserService;
import com.gdmu.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/student/internship-confirmation")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInternshipConfirmationController {

    @Autowired
    private UserService userService;

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private StudentInternshipStatusService internshipStatusService;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Autowired
    private StudentApplicationService studentApplicationService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private InternshipConfirmationRecordService confirmationRecordService;

    /**
     * 将Object安全转换为String，处理Integer、Number等类型
     */
    private String objectToString(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String) return (String) obj;
        if (obj instanceof Number) return String.valueOf(obj);
        return obj.toString();
    }

    /**
     * 校验当前时间是否在应聘时间范围内
     */
    private Result validateApplicationPeriod() {
        InternshipTimeSettings settings = internshipTimeSettingsService.findLatest();
        if (settings == null) {
            return Result.error("系统未设置应聘时间，请联系管理员");
        }

        LocalDate today = LocalDate.now();

        // 校验应聘开始时间
        if (settings.getApplicationStartDate() != null && !settings.getApplicationStartDate().isEmpty()) {
            LocalDate startDate = LocalDate.parse(settings.getApplicationStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            if (today.isBefore(startDate)) {
                return Result.error("应聘尚未开始，开始时间为：" + settings.getApplicationStartDate());
            }
        }

        // 校验应聘截止时间
        if (settings.getApplicationEndDate() != null && !settings.getApplicationEndDate().isEmpty()) {
            LocalDate endDate = LocalDate.parse(settings.getApplicationEndDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            if (today.isAfter(endDate)) {
                return Result.error("应聘已截止，截止时间为：" + settings.getApplicationEndDate());
            }
        }

        return null; // 验证通过
    }

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    /**
     * 将当前实习记录归档到确认记录表
     * 当实习状态变为已中断或已结束时调用
     */
    private void archiveInternshipToConfirmationRecord(StudentInternshipStatus status, User user) {
        if (status == null || status.getCompanyName() == null || status.getCompanyName().isEmpty()) {
            return;
        }
        try {
            // 获取学生信息
            StudentUser studentUser = studentUserService.findById(status.getStudentId());

            // 创建确认记录
            InternshipConfirmationRecord record = new InternshipConfirmationRecord();
            record.setStudentId(status.getStudentId());
            record.setStatus(1); // 已确认（归档）
            record.setCreateTime(LocalDateTime.now());

            if (studentUser != null) {
                record.setStudentName(studentUser.getName());
                record.setStudentUserId(studentUser.getStudentUserId());
                record.setGender(studentUser.getGender() != null ? (studentUser.getGender() == 1 ? "男" : "女") : null);
                record.setGrade(studentUser.getGrade() != null ? studentUser.getGrade().toString() : null);
                record.setMajor(studentUser.getMajor());
                record.setClassName(studentUser.getClasses() != null ? studentUser.getClasses() : studentUser.getClassName());
                record.setContactPhone(studentUser.getPhone());
                record.setEmail(studentUser.getEmail());
            } else {
                record.setStudentName(user.getName());
                record.setStudentUserId(user.getUsername());
            }

            record.setCompanyId(status.getCompanyId());
            record.setCompanyName(status.getCompanyName());
            record.setCompanyAddress(status.getCompanyAddress());
            record.setCompanyPhone(status.getCompanyPhone());
            record.setPositionId(status.getPositionId());
            record.setPositionName(status.getPositionName());

            // 转换实习时间
            if (status.getInternshipStartTime() != null) {
                record.setInternshipStartTime(status.getInternshipStartTime().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            }
            if (status.getInternshipEndTime() != null) {
                record.setInternshipEndTime(status.getInternshipEndTime().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            }
            record.setInternshipDuration(status.getInternshipDuration());
            record.setRemark(status.getRemark());

            confirmationRecordService.insert(record);
            log.info("已将学生ID: {} 的实习记录归档到确认记录表，公司: {}", status.getStudentId(), status.getCompanyName());
        } catch (Exception e) {
            log.error("归档实习记录失败: {}", e.getMessage(), e);
        }
    }

    @GetMapping
    public Result get() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            StudentUser studentUser = studentUserService.findById(studentId);
            StudentInternshipStatus status = internshipStatusService.findByStudentId(studentId);

            Map<String, Object> data = new HashMap<>();
            if (studentUser != null) {
                data.put("studentName", studentUser.getName());
                data.put("studentId", studentUser.getStudentUserId());
                data.put("gender", studentUser.getGender() != null
                        ? (studentUser.getGender() == 1 ? "男" : "女") : "");
                data.put("grade", studentUser.getGrade());
                data.put("className", studentUser.getClassName());
            }
            if (status != null) {
                data.put("internshipStatusId", status.getId());
                data.put("internshipStatus", status.getStatus());
                data.put("companyId", status.getCompanyId());
                data.put("positionId", status.getPositionId());
                data.put("internshipStartTime", status.getInternshipStartTime());
                data.put("internshipEndTime", status.getInternshipEndTime());
                data.put("internshipDuration", status.getInternshipDuration());
                data.put("feedback", status.getFeedback());
                data.put("remark", status.getRemark());
            }
            return Result.success(data);
        } catch (Exception e) {
            log.error("获取实习确认表失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @PostMapping("/save")
    public Result save(@RequestBody Map<String, Object> body) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 校验应聘时间范围
            Result validationResult = validateApplicationPeriod();
            if (validationResult != null) {
                return validationResult;
            }

            // 创建新的确认记录
            InternshipConfirmationRecord record = new InternshipConfirmationRecord();
            record.setStudentId(studentId);
            record.setStatus(0); // 待企业确认
            record.setCreateTime(LocalDateTime.now());

            // 保存所有表单字段
            if (body.containsKey("studentName")) record.setStudentName(objectToString(body.get("studentName")));
            if (body.containsKey("studentNumber")) record.setStudentUserId(objectToString(body.get("studentNumber")));
            if (body.containsKey("gender")) record.setGender(objectToString(body.get("gender")));
            if (body.containsKey("grade")) record.setGrade(objectToString(body.get("grade")));
            if (body.containsKey("major")) record.setMajor(objectToString(body.get("major")));
            if (body.containsKey("className")) record.setClassName(objectToString(body.get("className")));
            if (body.containsKey("contactPhone")) record.setContactPhone(objectToString(body.get("contactPhone")));
            if (body.containsKey("email")) record.setEmail(objectToString(body.get("email")));

            if (body.containsKey("companyName")) record.setCompanyName(objectToString(body.get("companyName")));
            if (body.containsKey("companyAddress")) record.setCompanyAddress(objectToString(body.get("companyAddress")));
            if (body.containsKey("companyPhone")) record.setCompanyPhone(objectToString(body.get("companyPhone")));

            if (body.containsKey("positionName")) record.setPositionName(objectToString(body.get("positionName")));
            if (body.containsKey("companyId")) {
                Object companyId = body.get("companyId");
                if (companyId instanceof Number) {
                    record.setCompanyId(((Number) companyId).longValue());
                }
            }
            if (body.containsKey("positionId")) {
                Object positionId = body.get("positionId");
                if (positionId instanceof Number) {
                    record.setPositionId(((Number) positionId).longValue());
                }
            }
            if (body.containsKey("internshipStartTime")) {
                Object startTime = body.get("internshipStartTime");
                if (startTime != null) {
                    if (startTime instanceof String) {
                        try {
                            record.setInternshipStartTime(LocalDateTime.parse((String) startTime + "T00:00:00"));
                        } catch (Exception e) {
                            log.warn("解析开始时间失败: {}", startTime);
                        }
                    } else if (startTime instanceof java.util.Date) {
                        record.setInternshipStartTime(((java.util.Date) startTime).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                    }
                }
            }
            if (body.containsKey("internshipEndTime")) {
                Object endTime = body.get("internshipEndTime");
                if (endTime != null) {
                    if (endTime instanceof String) {
                        try {
                            record.setInternshipEndTime(LocalDateTime.parse((String) endTime + "T00:00:00"));
                        } catch (Exception e) {
                            log.warn("解析结束时间失败: {}", endTime);
                        }
                    } else if (endTime instanceof java.util.Date) {
                        record.setInternshipEndTime(((java.util.Date) endTime).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
                    }
                }
            }
            if (body.containsKey("internshipDuration")) {
                Object duration = body.get("internshipDuration");
                if (duration instanceof Number) {
                    record.setInternshipDuration(((Number) duration).intValue());
                }
            }
            if (body.containsKey("remark")) record.setRemark(objectToString(body.get("remark")));

            // 保存到确认记录表
            confirmationRecordService.insert(record);

            // 返回新创建的记录ID
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("id", record.getId());
            return Result.success(resultData);
        } catch (Exception e) {
            log.error("保存实习确认表失败: {}", e.getMessage(), e);
            return Result.error("保存失败: " + e.getMessage());
        }
    }

    @PostMapping("/submit")
    public Result submit(@RequestBody Map<String, Object> body) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 校验应聘时间范围
            Result validationResult = validateApplicationPeriod();
            if (validationResult != null) {
                return validationResult;
            }

            // 获取recordId
            Object recordIdObj = body.get("recordId");
            if (recordIdObj == null) {
                return Result.error("记录ID不能为空");
            }
            Long recordId = ((Number) recordIdObj).longValue();

            // 获取确认记录
            InternshipConfirmationRecord record = confirmationRecordService.findById(recordId);
            if (record == null) {
                return Result.error("确认记录不存在");
            }
            if (!studentId.equals(record.getStudentId())) {
                return Result.error("无权操作此记录");
            }

            // 设置状态为待企业确认(0)
            record.setStatus(0);
            // 保存备注
            if (body.containsKey("remark")) {
                record.setRemark(objectToString(body.get("remark")));
            }

            confirmationRecordService.update(record);

            // 同步写入进度记录
            InternshipProgressRecord progressRecord = new InternshipProgressRecord();
            progressRecord.setStudentId(studentId);
            progressRecord.setEventType("internship_confirmation");
            progressRecord.setEventTitle("实习确认表提交");
            progressRecord.setDescription("已提交实习确认表，等待企业确认: " + record.getCompanyName());
            progressRecord.setStatus("pending");
            progressRecord.setRelatedId(record.getId());
            progressRecord.setEventTime(new Date());
            progressRecordService.saveRecord(progressRecord);

            // 同步更新 student_internship_status（学生实习状态变为待确认=1）
            StudentInternshipStatus status = internshipStatusService.findByStudentId(studentId);
            if (status == null) {
                status = new StudentInternshipStatus();
                status.setStudentId(studentId);
                status.setCreateTime(new java.util.Date());
            }
            // 设置为待确认状态(1)
            status.setStatus(1);
            // 更新公司信息
            status.setCompanyId(record.getCompanyId());
            status.setCompanyName(record.getCompanyName());
            status.setPositionId(record.getPositionId());
            status.setPositionName(record.getPositionName());
            status.setCompanyAddress(record.getCompanyAddress());
            status.setCompanyPhone(record.getCompanyPhone());
            // 更新实习时间（LocalDateTime转换为Date）
            if (record.getInternshipStartTime() != null) {
                status.setInternshipStartTime(Date.from(record.getInternshipStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }
            if (record.getInternshipEndTime() != null) {
                status.setInternshipEndTime(Date.from(record.getInternshipEndTime().atZone(java.time.ZoneId.systemDefault()).toInstant()));
            }
            status.setInternshipDuration(record.getInternshipDuration());
            // 设置企业确认状态为待确认(0)
            status.setCompanyConfirmStatus(0);

            if (status.getId() == null) {
                internshipStatusService.insert(status);
            } else {
                internshipStatusService.update(status);
            }

            return Result.success("提交成功");
        } catch (Exception e) {
            log.error("提交实习确认表失败: {}", e.getMessage(), e);
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取确认历史记录
     */
    @GetMapping("/history")
    public Result getHistory() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 从确认记录表查询所有记录
            List<InternshipConfirmationRecord> records = confirmationRecordService.findByStudentIdOrderByCreateTimeDesc(studentId);

            // 获取学生的实习状态
            StudentInternshipStatus internshipStatus = internshipStatusService.findByStudentId(studentId);
            int studentInternshipStatus = internshipStatus != null ? internshipStatus.getStatus() : 0;

            java.util.List<Map<String, Object>> history = new java.util.ArrayList<>();

            // 检查是否需要从student_internship_status补充当前记录
            // 如果status >= 2（已确定/实习中/已结束）但确认记录表中没有已确认的记录，则补充一条
            boolean hasConfirmedRecord = records.stream().anyMatch(r -> r.getStatus() == 1);
            if (internshipStatus != null && internshipStatus.getStatus() >= 2 && !hasConfirmedRecord
                    && internshipStatus.getCompanyName() != null && !internshipStatus.getCompanyName().isEmpty()) {
                // 从student_internship_status补充当前记录
                Map<String, Object> currentItem = new java.util.HashMap<>();
                currentItem.put("id", internshipStatus.getId());
                currentItem.put("studentName", internshipStatus.getStudentName());
                currentItem.put("studentUserId", user.getUsername());
                currentItem.put("studentNumber", user.getUsername());
                currentItem.put("gender", internshipStatus.getGender());
                currentItem.put("grade", internshipStatus.getGrade());
                currentItem.put("major", internshipStatus.getMajorName());
                currentItem.put("className", internshipStatus.getClassName());
                currentItem.put("contactPhone", internshipStatus.getContactPhone());
                currentItem.put("email", internshipStatus.getEmail());
                currentItem.put("companyName", internshipStatus.getCompanyName());
                currentItem.put("positionName", internshipStatus.getPositionName());
                currentItem.put("companyAddress", internshipStatus.getCompanyAddress());
                currentItem.put("companyPhone", internshipStatus.getCompanyPhone());
                currentItem.put("companyId", internshipStatus.getCompanyId());
                currentItem.put("positionId", internshipStatus.getPositionId());
                currentItem.put("internshipStartTime", internshipStatus.getInternshipStartTime());
                currentItem.put("internshipEndTime", internshipStatus.getInternshipEndTime());
                currentItem.put("internshipDuration", internshipStatus.getInternshipDuration());
                currentItem.put("remark", internshipStatus.getRemark());
                currentItem.put("rejectionReason", null);
                // status=1表示已确认（从student_internship_status补充的）
                currentItem.put("status", 1);
                currentItem.put("internshipStatus", studentInternshipStatus);
                // 使用updateTime确保当前记录时间最新，排在最前
                currentItem.put("createTime", internshipStatus.getUpdateTime());
                currentItem.put("isFromStatus", true); // 标记为从status表补充
                history.add(currentItem);
            }

            // 添加确认记录表中的记录
            for (InternshipConfirmationRecord record : records) {
                Map<String, Object> item = new java.util.HashMap<>();
                item.put("id", record.getId());
                // 学生信息
                item.put("studentName", record.getStudentName());
                item.put("studentUserId", record.getStudentUserId());
                item.put("studentNumber", record.getStudentUserId());
                item.put("gender", record.getGender());
                item.put("grade", record.getGrade());
                item.put("major", record.getMajor());
                item.put("className", record.getClassName());
                item.put("contactPhone", record.getContactPhone());
                item.put("email", record.getEmail());
                // 企业信息
                item.put("companyName", record.getCompanyName());
                item.put("positionName", record.getPositionName());
                item.put("companyAddress", record.getCompanyAddress());
                item.put("companyPhone", record.getCompanyPhone());
                item.put("companyId", record.getCompanyId());
                item.put("positionId", record.getPositionId());
                // 实习时间
                item.put("internshipStartTime", record.getInternshipStartTime());
                item.put("internshipEndTime", record.getInternshipEndTime());
                item.put("internshipDuration", record.getInternshipDuration());
                item.put("remark", record.getRemark());
                item.put("rejectionReason", record.getRemark());
                // status: 确认记录状态 0=待确认, 1=已确认, 2=已拒绝
                item.put("status", record.getStatus());
                // internshipStatus: 学生实习状态：0=待就业，1=待确认，2=已确定，3=实习中，4=已结束，5=已中断
                item.put("internshipStatus", studentInternshipStatus);
                item.put("createTime", record.getCreateTime());
                item.put("isFromStatus", false);
                history.add(item);
            }

            // 按isFromStatus降序（true在前）、createTime降序排序，确保当前实习状态记录优先
            history.sort((a, b) -> {
                Boolean isFromStatusA = (Boolean) a.get("isFromStatus");
                Boolean isFromStatusB = (Boolean) b.get("isFromStatus");
                if (isFromStatusA != isFromStatusB) {
                    return isFromStatusA ? -1 : 1;
                }
                Object createTimeA = a.get("createTime");
                Object createTimeB = b.get("createTime");
                if (createTimeA == null && createTimeB == null) return 0;
                if (createTimeA == null) return 1;
                if (createTimeB == null) return -1;
                return ((java.util.Date) createTimeB).compareTo((java.util.Date) createTimeA);
            });

            return Result.success(history);
        } catch (Exception e) {
            log.error("获取确认历史失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 获取当前学生的单位变更申请状态
     */
    @GetMapping("/unit-change/status")
    public Result getUnitChangeStatus() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 检查是否有待审核或被驳回的申请
            StudentApplication latestApp = studentApplicationService.findLatestUnitChangeByStudentId(studentId);

            Map<String, Object> data = new HashMap<>();
            if (latestApp != null) {
                data.put("hasPendingApplication", "pending".equals(latestApp.getStatus()));
                data.put("hasRejectedApplication", "rejected".equals(latestApp.getStatus()));
                data.put("currentApplicationId", latestApp.getId());
                data.put("application", latestApp);
            } else {
                data.put("hasPendingApplication", false);
                data.put("hasRejectedApplication", false);
                data.put("currentApplicationId", null);
                data.put("application", null);
            }

            return Result.success(data);
        } catch (Exception e) {
            log.error("获取单位变更申请状态失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /**
     * 提交单位变更申请
     */
    @PostMapping("/unit-change/submit")
    public Result submitUnitChange(@RequestBody Map<String, Object> body) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 校验应聘时间范围
            Result validationResult = validateApplicationPeriod();
            if (validationResult != null) {
                return validationResult;
            }

            // 校验学生实习状态（必须为1或2才能申请）
            StudentInternshipStatus status = internshipStatusService.findByStudentId(studentId);
            if (status == null || (status.getStatus() != 1 && status.getStatus() != 2)) {
                return Result.error("当前状态不允许申请变更");
            }

            // 检查是否有待审核的变更申请
            StudentApplication latestApp = studentApplicationService.findLatestUnitChangeByStudentId(studentId);
            if (latestApp != null && "pending".equals(latestApp.getStatus())) {
                return Result.error("您有正在审核中的变更申请，请等待审核完成");
            }

            // 创建新的单位变更申请
            StudentApplication application = new StudentApplication();
            application.setStudentId(studentId);
            application.setStudentName(user.getName());
            application.setStudentUserId(user.getUsername());

            // 设置年级、班级（从studentUser获取）
            StudentUser studentUser = studentUserService.findById(studentId);
            if (studentUser != null) {
                application.setGrade(objectToString(studentUser.getGrade()));
                application.setClassName(studentUser.getClassName());
            }

            application.setApplicationType("unitChange");
            application.setOldCompany(status.getCompanyName());  // 原单位
            application.setNewCompany(objectToString(body.get("newCompany")));
            application.setReason(objectToString(body.get("reason")));
            application.setStatus("pending");

            // 处理上传资料
            if (body.containsKey("materials")) {
                @SuppressWarnings("unchecked")
                Map<String, String> materials = (Map<String, String>) body.get("materials");
                application.setMaterials(materials);
            }

            application.setApplyTime(new Date());
            application.setCreateTime(new Date());

            studentApplicationService.insert(application);

            // 先将当前实习记录归档到确认记录表
            archiveInternshipToConfirmationRecord(status, user);

            // 更新学生实习状态为已中断（status=5）
            status.setStatus(5);
            internshipStatusService.update(status);

            // 同步写入进度记录
            InternshipProgressRecord record = new InternshipProgressRecord();
            record.setStudentId(studentId);
            record.setEventType("unit_change_application");
            record.setEventTitle("单位变更申请");
            record.setDescription("申请变更新单位: " + objectToString(body.get("newCompany")));
            record.setStatus("pending");
            record.setRelatedId(application.getId());
            record.setEventTime(new Date());
            progressRecordService.saveRecord(record);

            return Result.success("提交成功");
        } catch (Exception e) {
            log.error("提交单位变更申请失败: {}", e.getMessage(), e);
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 再次申请（更新被驳回的申请）
     */
    @PutMapping("/unit-change/resubmit/{id}")
    public Result resubmitUnitChange(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            Long studentId = user.getId();

            // 校验应聘时间范围
            Result validationResult = validateApplicationPeriod();
            if (validationResult != null) {
                return validationResult;
            }

            // 获取原申请并校验
            StudentApplication existingApp = studentApplicationService.findById(id);
            if (existingApp == null) {
                return Result.error("申请不存在");
            }
            if (!studentId.equals(existingApp.getStudentId())) {
                return Result.error("无权操作此申请");
            }
            if (!"rejected".equals(existingApp.getStatus())) {
                return Result.error("只能重新提交被驳回的申请");
            }

            // 更新申请信息
            existingApp.setNewCompany(objectToString(body.get("newCompany")));
            existingApp.setReason(objectToString(body.get("reason")));

            if (body.containsKey("materials")) {
                @SuppressWarnings("unchecked")
                Map<String, String> materials = (Map<String, String>) body.get("materials");
                existingApp.setMaterials(materials);
            }

            studentApplicationService.resubmitUnitChange(id, studentId, existingApp.getNewCompany(), existingApp.getReason(), existingApp.getMaterials());

            // 更新学生实习状态为已中断（status=5）
            StudentInternshipStatus status = internshipStatusService.findByStudentId(studentId);
            if (status != null) {
                // 先将当前实习记录归档到确认记录表
                archiveInternshipToConfirmationRecord(status, user);
                status.setStatus(5);
                internshipStatusService.update(status);
            }

            // 同步写入进度记录
            InternshipProgressRecord record = new InternshipProgressRecord();
            record.setStudentId(studentId);
            record.setEventType("unit_change_application");
            record.setEventTitle("单位变更申请");
            record.setDescription("再次申请变更新单位: " + existingApp.getNewCompany());
            record.setStatus("pending");
            record.setRelatedId(id);
            record.setEventTime(new Date());
            progressRecordService.saveRecord(record);

            return Result.success("提交成功");
        } catch (Exception e) {
            log.error("再次申请失败: {}", e.getMessage(), e);
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 撤回实习确认申请
     */
    @PostMapping("/{id}/recall")
    public Result recall(@PathVariable Long id, @RequestBody Map<String, String> body) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            // 获取确认记录
            InternshipConfirmationRecord record = confirmationRecordService.findById(id);
            if (record == null) {
                return Result.error("确认记录不存在");
            }
            // 验证归属
            if (!user.getId().equals(record.getStudentId())) {
                return Result.error("无权操作此记录");
            }
            // 检查状态：只能撤回待确认(0)的记录
            if (record.getStatus() != 0) {
                return Result.error("只能撤回待企业确认的申请");
            }
            // 检查是否已经撤回
            if (record.getRecallStatus() != null && record.getRecallStatus() == 1) {
                return Result.error("该申请已在撤回处理中");
            }

            String recallReason = body.get("recallReason");
            if (recallReason == null || recallReason.trim().isEmpty()) {
                return Result.error("请填写撤回原因");
            }

            int result = confirmationRecordService.recall(id, recallReason);
            if (result > 0) {
                return Result.success("撤回成功");
            } else {
                return Result.error("撤回失败");
            }
        } catch (Exception e) {
            log.error("撤回实习确认申请失败: {}", e.getMessage(), e);
            return Result.error("撤回失败: " + e.getMessage());
        }
    }
}
