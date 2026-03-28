package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.InternshipApplicationEntity;
import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.entity.Position;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.exception.BusinessException;
import com.gdmu.service.InternshipApplicationService;
import com.gdmu.service.InternshipTimeSettingsService;
import com.gdmu.service.InterviewInvitationService;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.PositionService;
import com.gdmu.service.StudentInternshipStatusService;
import com.gdmu.websocket.AnnouncementWebSocketHandler;
import com.gdmu.entity.InterviewInvitation;
import com.gdmu.mapper.StudentUserMapper;
import com.gdmu.mapper.PositionMapper;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.MajorMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 实习申请控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/applications")
public class InternshipApplicationController {

    @Autowired
    private InternshipApplicationService internshipApplicationService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private PositionMapper positionMapper;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private InterviewInvitationService interviewService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    /**
     * 根据公司ID获取申请列表
     */
    @GetMapping("/company/{companyId}")
    public Result getApplications(@PathVariable Long companyId) {
        log.info("根据公司ID获取申请列表，公司ID: {}", companyId);
        try {
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByCompanyId(companyId);
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取申请列表失败: {}", e.getMessage());
            return Result.error("获取申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取申请详情
     */
    @GetMapping("/{id}")
    public Result getApplicationById(@PathVariable Long id) {
        log.info("获取申请详情，ID: {}", id);
        try {
            InternshipApplicationEntity application = internshipApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }
            return Result.success(application);
        } catch (Exception e) {
            log.error("获取申请详情失败: {}", e.getMessage());
            return Result.error("获取申请详情失败: " + e.getMessage());
        }
    }

    /**
     * 添加申请
     */
    @Log(operationType = "ADD", module = "APPLICATION_MANAGEMENT", description = "添加实习申请")
    @PostMapping
    public Result addApplication(@RequestBody InternshipApplicationEntity application) {
        log.info("添加实习申请");
        try {
            // 从数据库获取学生完整信息并填充到申请记录中
            if (application.getStudentId() != null) {
                var student = studentUserMapper.findById(application.getStudentId());
                if (student != null) {
                    application.setGender(student.getGender() != null ? student.getGender().toString() : null);
                    application.setGrade(student.getGrade() != null ? student.getGrade().toString() : null);
                    // 获取专业名称
                    if (student.getMajorId() != null) {
                        var major = majorMapper.findById(student.getMajorId());
                        if (major != null) {
                            application.setMajor(major.getName());
                        }
                    }
                                        // 设置学生用户ID
                    application.setStudentUserId(student.getStudentId());
                }
            }
            int result = internshipApplicationService.insert(application);
            if (result > 0) {
                return Result.success("添加成功");
            }
            return Result.error("添加失败");
        } catch (Exception e) {
            log.error("添加申请失败: {}", e.getMessage());
            return Result.error("添加申请失败: " + e.getMessage());
        }
    }

    /**
     * 校验企业确认学生的时间是否在截止日期前
     */
    private void validateCompanyConfirmationDeadline() {
        InternshipTimeSettings settings = internshipTimeSettingsService.findLatest();
        if (settings == null) {
            throw new BusinessException("系统未设置时间节点，请联系管理员");
        }

        LocalDate today = LocalDate.now();

        // 校验企业确认截止日期
        if (settings.getCompanyConfirmationDeadline() != null && !settings.getCompanyConfirmationDeadline().isEmpty()) {
            LocalDate deadline = LocalDate.parse(settings.getCompanyConfirmationDeadline(), DateTimeFormatter.ISO_LOCAL_DATE);
            if (today.isAfter(deadline)) {
                throw new BusinessException("企业确认已超过截止日期，截止日期为：" + settings.getCompanyConfirmationDeadline());
            }
        }
    }

    /**
     * 更新申请状态
     * 当企业同意申请时，需要：
     * 1. 更新 internship_application 的状态为 approved
     * 2. 创建 student_internship_status 记录
     * 3. 更新 position 的 recruited_count 和 remaining_quota
     */
    @Log(operationType = "UPDATE", module = "APPLICATION_MANAGEMENT", description = "更新申请状态")
    @PutMapping("/{id}/status")
    public Result updateApplyStatus(@PathVariable Long id, @RequestBody StatusUpdateRequest statusRequest) {
        log.info("更新申请状态，ID: {}, 状态: {}", id, statusRequest.getStatus());
        try {
            InternshipApplicationEntity application = internshipApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }

            String newStatus = statusRequest.getStatus();

            // 如果是同意申请，需要校验确认截止日期
            if ("approved".equals(newStatus)) {
                try {
                    validateCompanyConfirmationDeadline();
                } catch (BusinessException e) {
                    return Result.error(e.getMessage());
                }
            }

            boolean result = internshipApplicationService.updateStatus(id, newStatus);

            if (!result) {
                return Result.error("更新失败");
            }

            // 如果是同意申请，需要创建实习状态记录
            if ("approved".equals(newStatus)) {
                // 创建 student_internship_status 记录
                StudentInternshipStatus internshipStatus = new StudentInternshipStatus();
                internshipStatus.setStudentId(application.getStudentId());
                internshipStatus.setPositionId(application.getPositionId());
                internshipStatus.setCompanyId(application.getCompanyId());
                internshipStatus.setStatus(1); // 状态1表示进行中
                internshipStatus.setCompanyConfirmStatus(1); // 企业已确认
                internshipStatus.setCreateTime(new Date());

                // 填充学生信息
                if (application.getStudentName() != null) {
                    internshipStatus.setStudentName(application.getStudentName());
                }
                if (application.getStudentUserId() != null) {
                    try {
                        Long studentId = Long.parseLong(application.getStudentUserId());
                        var student = studentUserMapper.findById(studentId);
                        if (student != null) {
                            if (internshipStatus.getStudentName() == null) {
                                internshipStatus.setStudentName(student.getName());
                            }
                            internshipStatus.setGender(student.getGender() != null ? student.getGender().toString() : null);
                            internshipStatus.setGrade(student.getGrade() != null ? student.getGrade().toString() : null);
                            internshipStatus.setMajorName(student.getMajorId() != null ? student.getMajorId().toString() : null);
                            internshipStatus.setContactPhone(student.getPhone());
                            internshipStatus.setEmail(student.getEmail());
                        }
                    } catch (NumberFormatException e) {
                        log.warn("解析学生ID失败: {}", application.getStudentUserId());
                    }
                }

                // 填充岗位信息
                if (application.getPositionName() != null) {
                    internshipStatus.setPositionName(application.getPositionName());
                }
                if (application.getPositionId() != null) {
                    Position position = positionMapper.findById(application.getPositionId());
                    if (position != null) {
                        if (internshipStatus.getPositionName() == null) {
                            internshipStatus.setPositionName(position.getPositionName());
                        }
                        StringBuilder address = new StringBuilder();
                        if (position.getProvince() != null) address.append(position.getProvince());
                        if (position.getCity() != null) address.append(position.getCity());
                        if (position.getDistrict() != null) address.append(position.getDistrict());
                        if (position.getDetailAddress() != null) address.append(position.getDetailAddress());
                        internshipStatus.setCompanyAddress(address.toString());
                    }
                }

                // 填充公司信息
                if (application.getCompanyId() != null) {
                    var company = companyUserMapper.findById(application.getCompanyId());
                    if (company != null) {
                        internshipStatus.setCompanyName(company.getCompanyName());
                        internshipStatus.setCompanyPhone(company.getContactPhone());
                    }
                }

                studentInternshipStatusService.insert(internshipStatus);
                log.info("创建实习状态记录成功，学生ID: {}", application.getStudentId());

                // 更新岗位的 recruited_count 和 remaining_quota
                if (application.getPositionId() != null) {
                    Position position = positionMapper.findById(application.getPositionId());
                    if (position != null) {
                        int currentRecruited = position.getRecruitedCount() != null ? position.getRecruitedCount() : 0;
                        int currentPlanned = position.getPlannedRecruit() != null ? position.getPlannedRecruit() : 0;
                        position.setRecruitedCount(currentRecruited + 1);
                        position.setRemainingQuota(Math.max(0, currentPlanned - currentRecruited - 1));
                        positionService.update(position);
                        log.info("更新岗位已招人数: {}, 剩余名额: {}", position.getRecruitedCount(), position.getRemainingQuota());
                    }
                }
            }

            // 如果是面试结果（interview_passed 或 interview_failed），更新面试邀请状态并推送WebSocket通知
            if ("interview_passed".equals(newStatus) || "interview_failed".equals(newStatus)) {
                if (application.getStudentId() != null && application.getPositionId() != null) {
                    InterviewInvitation invitation = interviewService.findByStudentAndPosition(
                            application.getStudentId(), application.getPositionId());
                    if (invitation != null) {
                        interviewService.updateStatus(invitation.getId(), newStatus, null);
                        webSocketHandler.sendInterviewStatusUpdateToUser(
                                application.getStudentId(), invitation.getId(), newStatus);
                        log.info("更新面试邀请状态并推送WebSocket，学生ID: {}, 状态: {}",
                                application.getStudentId(), newStatus);
                    }
                }
                // 更新实习进展记录状态
                String progressStatus = "interview_passed".equals(newStatus) ? "success" : "failed";
                progressRecordService.updateStatusByRelatedId(id, "interview", progressStatus);
            }

            // 如果是申请被批准，更新求职申请的进展记录状态
            if ("approved".equals(newStatus)) {
                progressRecordService.updateStatusByRelatedId(id, "job_application", "success");
            }

            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新申请状态失败: {}", e.getMessage(), e);
            return Result.error("更新申请状态失败: " + e.getMessage());
        }
    }

    /**
     * 标记已读
     */
    @Log(operationType = "UPDATE", module = "APPLICATION_MANAGEMENT", description = "标记申请已读")
    @PutMapping("/{id}/view")
    public Result markAsViewed(@PathVariable Long id) {
        log.info("标记申请已读，ID: {}", id);
        try {
            boolean result = internshipApplicationService.markAsViewed(id);
            if (result) {
                return Result.success("标记成功");
            }
            return Result.error("标记失败");
        } catch (Exception e) {
            log.error("标记已读失败: {}", e.getMessage());
            return Result.error("标记已读失败: " + e.getMessage());
        }
    }

    /**
     * 删除申请
     */
    @Log(operationType = "DELETE", module = "APPLICATION_MANAGEMENT", description = "删除实习申请")
    @DeleteMapping("/{id}")
    public Result deleteApplication(@PathVariable Long id) {
        log.info("删除申请，ID: {}", id);
        try {
            boolean result = internshipApplicationService.deleteById(id);
            if (result) {
                return Result.success("删除成功");
            }
            return Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除申请失败: {}", e.getMessage());
            return Result.error("删除申请失败: " + e.getMessage());
        }
    }

    /**
     * 根据岗位ID获取申请列表
     */
    @GetMapping("/position/{positionId}")
    public Result getApplicationsByPosition(@PathVariable Long positionId) {
        log.info("根据岗位ID获取申请列表，岗位ID: {}", positionId);
        try {
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByPositionId(positionId);
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取岗位申请列表失败: {}", e.getMessage());
            return Result.error("获取岗位申请列表失败: " + e.getMessage());
        }
    }

    /**
     * 按状态获取申请
     */
    @GetMapping
    public Result getApplicationsByStatus(@RequestParam(required = false) String status) {
        log.info("按状态获取申请，状态: {}", status);
        try {
            List<InternshipApplicationEntity> applications;
            if (status != null) {
                applications = internshipApplicationService.findByStatus(status);
            } else {
                applications = internshipApplicationService.findAll();
            }
            return Result.success(applications);
        } catch (Exception e) {
            log.error("按状态获取申请失败: {}", e.getMessage());
            return Result.error("按状态获取申请失败: " + e.getMessage());
        }
    }

    /**
     * 状态更新请求参数
     */
    private static class StatusUpdateRequest {
        private String status;

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }
    }
}
