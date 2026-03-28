package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Announcement;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.InternshipApplicationEntity;
import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Position;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.service.AnnouncementService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.InternshipApplicationService;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.PositionService;
import com.gdmu.service.StudentInternshipStatusService;
import com.gdmu.service.StudentJobApplicationService;
import com.gdmu.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.gdmu.entity.InterviewInvitation;

@Slf4j
@RestController
@RequestMapping("/api/company")
public class CompanyController {

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private InternshipApplicationService internshipApplicationService;

    @Autowired
    private StudentJobApplicationService studentJobApplicationService;

    @Autowired
    private com.gdmu.service.AnnouncementReadRecordService announcementReadRecordService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private com.gdmu.websocket.AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private com.gdmu.service.InterviewInvitationService interviewInvitationService;

    @GetMapping("/info")
    public Result getCompanyInfo(@RequestParam Long companyId) {
        log.info("获取企业信息：{}", companyId);
        try {
            CompanyUser company = companyUserService.findById(companyId);
            if (company == null) {
                return Result.error("企业不存在");
            }

            Map<String, Object> companyInfo = new HashMap<>();
            companyInfo.put("id", company.getId());
            companyInfo.put("companyName", company.getCompanyName());
            companyInfo.put("contactPerson", company.getContactPerson());
            companyInfo.put("contactPhone", company.getContactPhone());
            companyInfo.put("contactEmail", company.getContactEmail());
            companyInfo.put("phone", company.getPhone());
            companyInfo.put("address", company.getAddress());
            companyInfo.put("introduction", company.getIntroduction());
            companyInfo.put("status", company.getStatus());
            companyInfo.put("industry", company.getIndustry());
            companyInfo.put("scale", company.getScale());
            companyInfo.put("province", company.getProvince());
            companyInfo.put("city", company.getCity());
            companyInfo.put("district", company.getDistrict());
            companyInfo.put("detailAddress", company.getDetailAddress());
            companyInfo.put("website", company.getWebsite());
            companyInfo.put("description", company.getDescription());
            companyInfo.put("cooperationMode", company.getCooperationMode());
            companyInfo.put("isInternshipBase", company.getIsInternshipBase());
            companyInfo.put("acceptBackup", company.getAcceptBackup());
            companyInfo.put("maxBackupStudents", company.getMaxBackupStudents());
            companyInfo.put("companyTag", company.getCompanyTag());
            companyInfo.put("logo", company.getLogo());
            companyInfo.put("photos", company.getPhotos());
            companyInfo.put("videos", company.getVideos());

            return Result.success(companyInfo);
        } catch (Exception e) {
            log.error("获取企业信息失败：{}", e.getMessage(), e);
            return Result.error("获取企业信息失败：" + e.getMessage());
        }
    }

    @GetMapping("/profile")
    public Result getProfile() {
        log.info("获取企业账号个人信息");
        try {
            Long companyId = getCurrentCompanyId();
            log.info("当前企业 ID: {}", companyId);

            CompanyUser company = companyUserService.findById(companyId);
            if (company == null) {
                log.warn("企业不存在，ID: {}", companyId);
                return Result.error("企业不存在");
            }

            log.info("找到企业：{}", company.getCompanyName());

            Map<String, Object> profile = new HashMap<>();
            profile.put("id", company.getId());
            profile.put("username", company.getUsername());
            profile.put("phone", company.getPhone());
            profile.put("email", company.getEmail());
            profile.put("companyName", company.getCompanyName());
            profile.put("industry", company.getIndustry());
            profile.put("scale", company.getScale());

            log.info("返回个人信息：{}", profile);
            return Result.success(profile);
        } catch (Exception e) {
            log.error("获取个人信息失败", e);
            return Result.error("获取个人信息失败：" + e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业更新个人信息")
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody Map<String, Object> profileData) {
        log.info("更新企业账号个人信息：{}", profileData);
        try {
            Long companyId = getCurrentCompanyId();
            CompanyUser company = companyUserService.findById(companyId);
            if (company == null) {
                return Result.error("企业不存在");
            }

            if (profileData.containsKey("username")) {
                company.setUsername((String) profileData.get("username"));
            }
            if (profileData.containsKey("phone")) {
                company.setPhone((String) profileData.get("phone"));
            }
            if (profileData.containsKey("email")) {
                company.setEmail((String) profileData.get("email"));
            }

            company.setUpdateTime(new java.util.Date());
            int result = companyUserService.update(company);
            if (result > 0) {
                return Result.success("个人信息更新成功");
            }
            return Result.error("个人信息更新失败");
        } catch (Exception e) {
            log.error("更新个人信息失败：{}", e.getMessage(), e);
            return Result.error("更新个人信息失败：" + e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业更新企业信息")
    @PutMapping("/info")
    public Result updateCompanyInfo(@RequestBody Map<String, Object> companyInfo) {
        log.info("更新企业信息：{}", companyInfo);
        try {
            Long companyId = getCurrentCompanyId();
            CompanyUser company = companyUserService.findById(companyId);
            if (company == null) {
                return Result.error("企业不存在");
            }

            log.info("接收到的字段：{}", companyInfo.keySet());

            if (companyInfo.containsKey("companyName")) {
                company.setCompanyName((String) companyInfo.get("companyName"));
                log.info("更新 companyName: {}", company.getCompanyName());
            }
            if (companyInfo.containsKey("contactPerson")) {
                company.setContactPerson((String) companyInfo.get("contactPerson"));
                log.info("更新 contactPerson: {}", company.getContactPerson());
            }
            if (companyInfo.containsKey("contactPhone")) {
                company.setContactPhone((String) companyInfo.get("contactPhone"));
                log.info("更新 contactPhone: {}", company.getContactPhone());
            }
            if (companyInfo.containsKey("contactEmail")) {
                company.setContactEmail((String) companyInfo.get("contactEmail"));
                log.info("更新 contactEmail: {}", company.getContactEmail());
            }
            if (companyInfo.containsKey("address")) {
                company.setAddress((String) companyInfo.get("address"));
                log.info("更新 address: {}", company.getAddress());
            }
            if (companyInfo.containsKey("introduction")) {
                company.setIntroduction((String) companyInfo.get("introduction"));
                log.info("更新 introduction: {}", company.getIntroduction());
            }
            if (companyInfo.containsKey("industry")) {
                company.setIndustry((String) companyInfo.get("industry"));
            }
            if (companyInfo.containsKey("scale")) {
                company.setScale((String) companyInfo.get("scale"));
            }
            if (companyInfo.containsKey("province")) {
                company.setProvince((String) companyInfo.get("province"));
            }
            if (companyInfo.containsKey("city")) {
                company.setCity((String) companyInfo.get("city"));
            }
            if (companyInfo.containsKey("district")) {
                company.setDistrict((String) companyInfo.get("district"));
            }
            if (companyInfo.containsKey("detailAddress")) {
                company.setDetailAddress((String) companyInfo.get("detailAddress"));
            }
            if (companyInfo.containsKey("website")) {
                company.setWebsite((String) companyInfo.get("website"));
            }
            if (companyInfo.containsKey("description")) {
                company.setDescription((String) companyInfo.get("description"));
            }
            if (companyInfo.containsKey("cooperationMode")) {
                company.setCooperationMode((String) companyInfo.get("cooperationMode"));
            }
            if (companyInfo.containsKey("isInternshipBase")) {
                company.setIsInternshipBase((Integer) companyInfo.get("isInternshipBase"));
            }
            if (companyInfo.containsKey("acceptBackup")) {
                company.setAcceptBackup((Integer) companyInfo.get("acceptBackup"));
            }
            if (companyInfo.containsKey("maxBackupStudents")) {
                Object value = companyInfo.get("maxBackupStudents");
                if (value != null) {
                    company.setMaxBackupStudents(((Number) value).longValue());
                }
            }
            if (companyInfo.containsKey("logo")) {
                String logo = (String) companyInfo.get("logo");
                if (logo != null && !logo.trim().isEmpty()) {
                    company.setLogo(logo);
                    log.info("更新 logo: {}", logo);
                } else {
                    log.info("logo 为空或 null，跳过更新");
                }
            }
            if (companyInfo.containsKey("photos")) {
                String photos = (String) companyInfo.get("photos");
                if (photos != null && !photos.trim().isEmpty()) {
                    company.setPhotos(photos);
                    log.info("更新 photos: {}", photos);
                } else {
                    log.warn("photos 为空或 null，跳过更新");
                }
            }
            if (companyInfo.containsKey("videos")) {
                String videos = (String) companyInfo.get("videos");
                if (videos != null && !videos.trim().isEmpty()) {
                    company.setVideos(videos);
                    log.info("更新 videos: {}", videos);
                } else {
                    log.warn("videos 为空或 null，跳过更新");
                }
            }

            company.setUpdateTime(new java.util.Date());
            log.info("准备更新 company 对象：{}", company);
            int result = companyUserService.update(company);
            log.info("更新结果：{}", result);
            if (result > 0) {
                return Result.success("企业信息更新成功");
            }
            return Result.error("企业信息更新失败");
        } catch (Exception e) {
            log.error("更新企业信息失败：{}", e.getMessage(), e);
            return Result.error("更新企业信息失败：" + e.getMessage());
        }
    }

    @GetMapping("/stats")
    public Result getStats() {
        log.info("获取企业统计数据");
        try {
            Long companyId = getCurrentCompanyId();
            log.info("当前登录企业 ID: {}", companyId);

            if (companyId == null) {
                log.error("未获取到当前登录企业 ID，无法返回统计数据");
                return Result.error("未获取到当前登录企业 ID");
            }

            Map<String, Object> stats = new HashMap<>();

            Long publishedPositions = positionService.countByCompanyId(companyId);
            List<StudentInternshipStatus> allStatuses = studentInternshipStatusService.list(null, null, null, null, companyId, null, null, null, null, null);
            Long totalApplications = (long) allStatuses.size();
            Long pendingApplications = allStatuses.stream().filter(s -> s.getCompanyConfirmStatus() != null && s.getCompanyConfirmStatus() == 0).count();
            Long confirmedApplications = allStatuses.stream().filter(s -> s.getCompanyConfirmStatus() != null && s.getCompanyConfirmStatus() == 1).count();

            stats.put("publishedPositions", publishedPositions);
            stats.put("totalApplications", totalApplications);
            stats.put("pendingApplications", pendingApplications);
            stats.put("confirmedApplications", confirmedApplications);

            log.info("统计数据：发布职位={}, 总申请={}, 待确认={}, 已确认={}",
                    publishedPositions, totalApplications, pendingApplications, confirmedApplications);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取统计数据失败：{}", e.getMessage(), e);
            return Result.error("获取统计数据失败：" + e.getMessage());
        }
    }

    @GetMapping("/positions/recent")
    public Result getRecentPositions() {
        log.info("获取最近发布的岗位");
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }
            PageResult result = positionService.findPageByCompanyId(companyId, 1, 5);
            return Result.success(result.getRows());
        } catch (Exception e) {
            log.error("获取最近岗位失败：{}", e.getMessage(), e);
            return Result.error("获取最近岗位失败：" + e.getMessage());
        }
    }

    @GetMapping("/positions")
    public Result getPositions(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("获取企业岗位列表：page={}, pageSize={}", page, pageSize);
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }
            PageResult result = positionService.findPageByCompanyId(companyId, page, pageSize);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取岗位列表失败：{}", e.getMessage(), e);
            return Result.error("获取岗位列表失败：" + e.getMessage());
        }
    }

    @Log(operationType = "ADD", module = "POSITION_MANAGEMENT", description = "企业创建岗位")
    @PostMapping("/positions")
    public Result createPosition(@RequestBody Position position) {
        log.info("创建岗位：{}", position.getPositionName());
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }
            position.setCompanyId(companyId);
            int result = positionService.insert(position);
            if (result > 0) {
                return Result.success("岗位创建成功");
            }
            return Result.error("岗位创建失败");
        } catch (Exception e) {
            log.error("创建岗位失败：{}", e.getMessage(), e);
            return Result.error("创建岗位失败：" + e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "POSITION_MANAGEMENT", description = "企业更新岗位")
    @PutMapping("/positions/{id}")
    public Result updatePosition(@PathVariable Long id, @RequestBody Position position) {
        log.info("更新岗位：id={}", id);
        try {
            position.setId(id);
            int result = positionService.update(position);
            if (result > 0) {
                return Result.success("岗位更新成功");
            }
            return Result.error("岗位更新失败");
        } catch (Exception e) {
            log.error("更新岗位失败：{}", e.getMessage(), e);
            return Result.error("更新岗位失败：" + e.getMessage());
        }
    }

    @Log(operationType = "DELETE", module = "POSITION_MANAGEMENT", description = "企业删除岗位")
    @DeleteMapping("/positions/{id}")
    public Result deletePosition(@PathVariable Long id) {
        log.info("删除岗位：id={}", id);
        try {
            int result = positionService.delete(id);
            if (result > 0) {
                return Result.success("岗位删除成功");
            }
            return Result.error("岗位删除失败");
        } catch (Exception e) {
            log.error("删除岗位失败：{}", e.getMessage(), e);
            return Result.error("删除岗位失败：" + e.getMessage());
        }
    }

    private Long getCurrentCompanyId() {
        Long companyId = CurrentHolder.getUserId();
        if (companyId == null) {
            log.warn("未获取到当前登录企业 ID");
            return null;
        }
        log.debug("获取到当前登录企业 ID: {}", companyId);
        return companyId;
    }

    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业修改密码")
    @PutMapping("/password")
    public Result changePassword(@RequestBody Map<String, String> passwordData) {
        log.info("修改企业账号密码");
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }
            CompanyUser company = companyUserService.findById(companyId);
            if (company == null) {
                return Result.error("企业不存在");
            }

            String oldPassword = passwordData.get("oldPassword");
            String newPassword = passwordData.get("newPassword");

            if (oldPassword == null || oldPassword.isEmpty()) {
                return Result.error("请输入原密码");
            }
            if (newPassword == null || newPassword.isEmpty()) {
                return Result.error("请输入新密码");
            }
            if (newPassword.length() < 6 || newPassword.length() > 20) {
                return Result.error("密码长度应为 6-20 位");
            }

            if (!passwordEncoder.matches(oldPassword, company.getPassword())) {
                return Result.error("原密码错误");
            }

            company.setPassword(newPassword);
            company.setUpdateTime(new java.util.Date());
            int result = companyUserService.update(company);
            if (result > 0) {
                Map<String, Object> data = new HashMap<>();
                data.put("message", "密码修改成功，请重新登录");
                data.put("requireRelogin", true);
                return Result.success(data);
            }
            return Result.error("密码修改失败");
        } catch (Exception e) {
            log.error("修改密码失败：{}", e.getMessage(), e);
            return Result.error("修改密码失败：" + e.getMessage());
        }
    }

    @GetMapping("/applications/recent")
    public Result getRecentApplications(@RequestParam(required = false) Long companyId, @RequestParam(defaultValue = "5") Integer limit) {
        log.info("获取企业最新岗位申请：companyId={}, limit={}", companyId, limit);
        try {
            // 如果没有传 companyId，使用当前登录企业的 ID
            if (companyId == null) {
                companyId = getCurrentCompanyId();
            }
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }
            // 查询岗位申请表，获取最近的申请记录
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByCompanyId(companyId);

            java.util.List<InternshipApplicationEntity> recentApplications = applications.stream()
                    .sorted((a, b) -> {
                        Date aTime = a.getCreateTime() != null ? a.getCreateTime() : new Date(0);
                        Date bTime = b.getCreateTime() != null ? b.getCreateTime() : new Date(0);
                        return bTime.compareTo(aTime);
                    })
                    .limit(limit.longValue())
                    .toList();

            java.util.List<Map<String, Object>> result = recentApplications.stream()
                    .map(app -> {
                        Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", app.getId());
                        item.put("studentName", app.getStudentName());
                        item.put("positionName", app.getPositionName());
                        item.put("applyTime", app.getCreateTime());
                        item.put("status", app.getStatus() != null ? app.getStatus() : "pending");
                        item.put("viewed", app.getViewed() != null ? app.getViewed() : false);
                        return item;
                    })
                    .toList();

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取最新岗位申请失败：{}", e.getMessage(), e);
            return Result.error("获取最新岗位申请失败：" + e.getMessage());
        }
    }

    /**
     * 标记申请为已读
     */
    @PutMapping("/applications/{id}/view")
    public Result markApplicationAsViewed(@PathVariable Long id) {
        log.info("标记申请为已读，ID: {}", id);
        try {
            boolean result = internshipApplicationService.markAsViewed(id);
            if (result) {
                return Result.success("标记成功");
            } else {
                return Result.error("标记失败");
            }
        } catch (Exception e) {
            log.error("标记申请已读失败：{}", e.getMessage(), e);
            return Result.error("标记已读失败：" + e.getMessage());
        }
    }

    @GetMapping("/notifications")
    public Result getNotifications(@RequestParam(defaultValue = "5") Integer limit) {
        log.info("获取企业通知：limit={}", limit);
        try {
            // 获取当前企业用户ID
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                log.warn("未找到企业用户ID");
                return Result.error("未找到企业用户信息");
            }
            log.info("当前企业用户ID: {}", companyId);

            // 获取已发布的公告
            List<Announcement> allAnnouncements = announcementService.findAll(null, "PUBLISHED");

            // 过滤出对企业可见的公告（targetType 包含 COMPANY）
            List<Announcement> announcements = allAnnouncements.stream()
                .filter(a -> {
                    String targetType = a.getTargetType();
                    if (targetType == null || targetType.isEmpty()) {
                        return false;
                    }
                    // 支持 JSON 数组格式或单个值
                    if (targetType.startsWith("[")) {
                        return targetType.contains("\"COMPANY\"");
                    } else {
                        return "COMPANY".equals(targetType);
                    }
                })
                .limit(limit.longValue())
                .toList();

            log.info("查询到的通知数量：{}", announcements.size());

            List<java.util.Map<String, Object>> result = announcements.stream()
                    .map(announcement -> {
                        // 检查是否已读
                        boolean isRead = announcementReadRecordService.findByAnnouncementAndUser(
                                announcement.getId(), String.valueOf(companyId), "ENTERPRISE") != null;
                        log.info("公告ID: {}, 企业ID: {}, 是否已读: {}", announcement.getId(), companyId, isRead);
                        
                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", announcement.getId());
                        item.put("type", "system"); // 公告类型统一为 system
                        item.put("title", announcement.getTitle());
                        item.put("content", announcement.getContent());
                        item.put("time", formatTime(announcement.getPublishTime()));
                        item.put("priority", announcement.getPriority() != null ? announcement.getPriority() : "info");
                        item.put("positionId", (Long) null);
                        item.put("positionName", "");
                        item.put("isRead", isRead); // 添加阅读状态
                        return item;
                    })
                    .toList();

            log.info("返回的通知数据：{}", result);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取企业通知失败：{}", e.getMessage(), e);
            return Result.error("获取企业通知失败：" + e.getMessage());
        }
    }

    private String formatTime(java.util.Date date) {
        if (date == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    private String getPositionName(Long positionId) {
        if (positionId == null) return "";
        try {
            Position position = positionService.findById(positionId);
            return position != null ? position.getPositionName() : "";
        } catch (Exception e) {
            log.error("获取岗位名称失败：positionId={}", positionId, e);
            return "";
        }
    }

    /**
     * 获取当前登录企业的岗位申请列表
     */
    @GetMapping("/applications")
    public Result getCurrentCompanyApplications() {
        log.info("获取当前登录企业的岗位申请列表");
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                log.error("未获取到当前登录企业 ID");
                return Result.error("未获取到当前登录企业 ID");
            }
            log.info("当前登录企业 ID: {}", companyId);

            List<InternshipApplicationEntity> applications = internshipApplicationService.findByCompanyId(companyId);
            log.info("获取到 {} 条岗位申请记录", applications.size());
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取岗位申请列表失败：{}", e.getMessage(), e);
            return Result.error("获取岗位申请列表失败：" + e.getMessage());
        }
    }

    /**
     * 获取当前登录企业的学生快速申请列表（从student_job_application表）
     */
    @GetMapping("/job-applications")
    public Result getJobApplications() {
        log.info("获取当前登录企业的学生快速申请列表");
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                log.error("未获取到当前登录企业 ID");
                return Result.error("未获取到当前登录企业 ID");
            }
            log.info("当前登录企业 ID: {}", companyId);

            List<com.gdmu.entity.StudentJobApplication> applications = studentJobApplicationService.findByCompanyId(companyId);
            log.info("获取到 {} 条学生快速申请记录", applications.size());
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取学生快速申请列表失败：{}", e.getMessage(), e);
            return Result.error("获取学生快速申请列表失败：" + e.getMessage());
        }
    }

    /**
     * 更新学生快速申请状态（同意/拒绝）
     */
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业更新学生快速申请状态")
    @PutMapping("/job-applications/{id}/status")
    public Result updateJobApplicationStatus(@PathVariable Long id, @RequestBody Map<String, String> statusData) {
        log.info("企业更新学生快速申请状态，ID: {}, 状态: {}", id, statusData.get("status"));
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }

            com.gdmu.entity.StudentJobApplication application = studentJobApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }

            // 验证该申请属于当前企业
            if (!companyId.equals(application.getCompanyId())) {
                return Result.error("无权操作此申请");
            }

            String newStatus = statusData.get("status");

            // 如果是面试通过或面试没通过，需要同时更新面试邀请状态和学生求职申请状态
            if ("interview_passed".equals(newStatus) || "interview_failed".equals(newStatus)) {
                // 更新面试邀请状态
                InterviewInvitation invitation = interviewInvitationService.findByStudentAndPosition(
                    application.getStudentId(), application.getPositionId());
                if (invitation != null) {
                    interviewInvitationService.updateStatus(invitation.getId(), newStatus, null);
                    log.info("更新面试邀请状态成功，invitationId: {}, status: {}", invitation.getId(), newStatus);

                    // 同时更新学生求职申请的状态
                    application.setStatus(newStatus);
                    studentJobApplicationService.update(application);
                    log.info("更新学生求职申请状态成功，applicationId: {}, status: {}", id, newStatus);

                    // 更新面试进展记录状态
                    String progressStatus = "interview_passed".equals(newStatus) ? "success" : "failed";
                    progressRecordService.updateStatusByRelatedId(invitation.getId(), "interview", progressStatus);

                    // 【修复】同时更新投递申请进展记录状态
                    progressRecordService.updateStatusByRelatedId(application.getId(), "job_application", progressStatus);

                    // WebSocket推送面试状态更新给学生（handler内部会转换statusText）
                    webSocketHandler.sendInterviewStatusUpdateToUser(application.getStudentId(), invitation.getId(), newStatus);

                    return Result.success("更新成功");
                } else {
                    return Result.error("未找到面试邀请记录");
                }
            }

            // 申请状态更新
            application.setStatus(newStatus);
            int result = studentJobApplicationService.update(application);

            // 如果是同意申请，需要创建面试邀请
            if (result > 0 && "approved".equals(newStatus)) {
                Position position = positionService.findById(application.getPositionId());
                if (position != null) {
                    createInterviewInvitationIfNotExistsForJobApplication(application, position);
                }
                // 更新投递申请进度记录为已通过
                progressRecordService.updateStatusByRelatedId(application.getId(), "job_application", "success");
            }

            // 如果是拒绝申请，更新投递申请进度记录为未通过
            if (result > 0 && "rejected".equals(newStatus)) {
                progressRecordService.updateStatusByRelatedId(application.getId(), "job_application", "failed");
            }

            if (result > 0) {
                return Result.success("更新成功");
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            log.error("更新学生快速申请状态失败：{}", e.getMessage(), e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    /**
     * 更新申请状态（同意/拒绝）
     */
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业更新申请状态")
    @PutMapping("/applications/{id}/status")
    public Result updateApplicationStatus(@PathVariable Long id, @RequestBody Map<String, String> statusData) {
        log.info("企业更新申请状态，ID: {}, 状态: {}", id, statusData.get("status"));
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }

            InternshipApplicationEntity application = internshipApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }

            // 验证该申请属于当前企业
            if (!companyId.equals(application.getCompanyId())) {
                return Result.error("无权操作此申请");
            }

            String newStatus = statusData.get("status");
            boolean result = internshipApplicationService.updateStatus(id, newStatus);

            if (!result) {
                return Result.error("更新失败");
            }

            // 如果是同意申请，需要创建或更新实习状态记录
            if ("approved".equals(newStatus)) {
                // 获取岗位信息
                Position position = positionService.findById(application.getPositionId());

                // 检查学生是否已有实习状态记录
                StudentInternshipStatus existingStatus = studentInternshipStatusService.findByStudentId(application.getStudentId());

                if (existingStatus != null) {
                    // 已存在记录，更新而不是插入
                    existingStatus.setPositionId(application.getPositionId());
                    existingStatus.setCompanyId(application.getCompanyId());
                    existingStatus.setStatus(1);
                    existingStatus.setCompanyConfirmStatus(1);
                    existingStatus.setUpdateTime(new Date());
                    existingStatus.setStudentName(application.getStudentName());
                    existingStatus.setPositionName(application.getPositionName());

                    studentInternshipStatusService.update(existingStatus);
                    log.info("更新实习状态记录成功，学生ID: {}", application.getStudentId());

                    // 【修复】更新实习状态后，也需要检查并创建面试卡片
                    createInterviewInvitationIfNotExists(application, position);
                } else {
                    // 不存在记录，创建新记录
                    StudentInternshipStatus internshipStatus = new StudentInternshipStatus();
                    internshipStatus.setStudentId(application.getStudentId());
                    internshipStatus.setPositionId(application.getPositionId());
                    internshipStatus.setCompanyId(application.getCompanyId());
                    internshipStatus.setStatus(1);
                    internshipStatus.setCompanyConfirmStatus(1);
                    internshipStatus.setCreateTime(new Date());
                    internshipStatus.setUpdateTime(new Date());
                    internshipStatus.setStudentName(application.getStudentName());
                    internshipStatus.setPositionName(application.getPositionName());

                    studentInternshipStatusService.insert(internshipStatus);
                    log.info("创建实习状态记录成功，学生ID: {}", application.getStudentId());
                }

                // 更新岗位的 recruited_count 和 remaining_quota
                if (position != null) {
                    int currentRecruited = position.getRecruitedCount() != null ? position.getRecruitedCount() : 0;
                    int currentPlanned = position.getPlannedRecruit() != null ? position.getPlannedRecruit() : 0;
                    position.setRecruitedCount(currentRecruited + 1);
                    position.setRemainingQuota(Math.max(0, currentPlanned - currentRecruited - 1));
                    positionService.update(position);
                    log.info("更新岗位已招人数: {}, 剩余名额: {}", position.getRecruitedCount(), position.getRemainingQuota());
                }
            }

            // 向学生推送申请状态更新通知
            CompanyUser company = companyUserService.findById(companyId);
            String companyName = company != null ? company.getCompanyName() : "";
            // 使用 studentUserId（用户数据库ID）进行通知
            Long studentUserId = null;
            try {
                studentUserId = Long.parseLong(application.getStudentUserId());
            } catch (NumberFormatException e) {
                log.warn("学生UserID格式不正确：{}", application.getStudentUserId());
            }
            if (studentUserId != null) {
                notifyStudentApplicationStatus(studentUserId, application.getStudentName(),
                    application.getPositionName(), newStatus, companyName);
            }

            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新申请状态失败：{}", e.getMessage(), e);
            return Result.error("更新申请状态失败：" + e.getMessage());
        }
    }

    /**
     * 通过WebSocket向学生推送申请状态更新
     */
    private void notifyStudentApplicationStatus(Long studentId, String studentName, String positionName, String status, String companyName) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("studentId", studentId);
            notification.put("studentName", studentName);
            notification.put("positionName", positionName);
            notification.put("companyName", companyName);
            notification.put("status", status);
            notification.put("statusText", "approved".equals(status) ? "已通过" : "未通过");

            webSocketHandler.sendApplicationStatusToUser(studentId, notification);
            log.info("已向学生 {} 推送申请状态更新通知", studentId);
        } catch (Exception e) {
            log.error("推送申请状态更新通知失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 创建面试邀请（如果不存在）
     * @param application 申请记录
     * @param position 岗位信息
     */
    private void createInterviewInvitationIfNotExists(InternshipApplicationEntity application, Position position) {
        try {
            // 检查是否已存在该学生和岗位的面试邀请
            InterviewInvitation existing = interviewInvitationService.findByStudentAndPosition(
                application.getStudentId(), application.getPositionId());
            if (existing != null) {
                log.debug("面试邀请已存在，无需创建，学生ID: {}, 岗位ID: {}",
                    application.getStudentId(), application.getPositionId());
                return;
            }

            // 获取企业名称
            String companyName = "";
            if (application.getCompanyId() != null) {
                CompanyUser company = companyUserService.findById(application.getCompanyId());
                if (company != null) {
                    companyName = company.getCompanyName();
                }
            }

            // 创建面试邀请
            InterviewInvitation invitation = new InterviewInvitation();
            invitation.setStudentId(application.getStudentId());
            invitation.setPositionId(application.getPositionId());
            invitation.setCompanyId(application.getCompanyId());
            invitation.setPositionName(position.getPositionName());
            invitation.setCompanyName(companyName);
            if (position.getInterviewTime() != null) {
                invitation.setInterviewTime(position.getInterviewTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            }
            invitation.setInterviewLocation(position.getInterviewLocation());
            invitation.setInterviewMethod(position.getInterviewMethod());
            invitation.setRemark(position.getInterviewRemark());
            invitation.setStatus("pending_interview");
            interviewInvitationService.create(invitation);

            // 创建面试进度记录（待处理状态）
            InternshipProgressRecord interviewRecord = new InternshipProgressRecord();
            interviewRecord.setStudentId(application.getStudentId());
            interviewRecord.setEventType("interview");
            interviewRecord.setEventTitle("面试邀请");
            interviewRecord.setDescription("收到来自 " + companyName + " 的面试邀请，岗位: " + position.getPositionName());
            interviewRecord.setStatus("pending");
            interviewRecord.setRelatedId(invitation.getId());
            interviewRecord.setEventTime(new Date());
            progressRecordService.saveRecord(interviewRecord);

            // WebSocket推送面试邀请给学生
            Map<String, Object> data = new HashMap<>();
            data.put("id", invitation.getId());
            data.put("positionId", position.getId());
            data.put("positionName", position.getPositionName());
            data.put("companyId", position.getCompanyId());
            data.put("companyName", companyName);
            data.put("interviewTime", position.getInterviewTime());
            data.put("interviewLocation", position.getInterviewLocation());
            data.put("interviewMethod", position.getInterviewMethod());
            data.put("status", "pending_interview");

            // 获取学生的 userId 用于 WebSocket 通知
            Long studentUserId = null;
            try {
                studentUserId = Long.parseLong(application.getStudentUserId());
            } catch (NumberFormatException e) {
                log.warn("学生UserID格式不正确：{}", application.getStudentUserId());
            }
            if (studentUserId != null) {
                webSocketHandler.sendInterviewCreateToUser(studentUserId, data);
                log.info("已创建面试邀请并推送WebSocket，学生ID: {}, userId: {}",
                    application.getStudentId(), studentUserId);
            } else {
                log.warn("无法获取学生userId，面试邀请创建成功但未推送WebSocket，学生ID: {}",
                    application.getStudentId());
            }
        } catch (Exception e) {
            log.error("创建面试邀请失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 为学生求职申请创建面试邀请（如果不存在）
     * @param application 学生求职申请记录
     * @param position 岗位信息
     */
    private void createInterviewInvitationIfNotExistsForJobApplication(com.gdmu.entity.StudentJobApplication application, Position position) {
        try {
            // 检查是否已存在该学生和岗位的面试邀请
            InterviewInvitation existing = interviewInvitationService.findByStudentAndPosition(
                application.getStudentId(), application.getPositionId());
            if (existing != null) {
                log.debug("面试邀请已存在，无需创建，学生ID: {}, 岗位ID: {}",
                    application.getStudentId(), application.getPositionId());
                return;
            }

            // 获取企业信息
            String companyName = application.getCompanyName() != null ? application.getCompanyName() : "";
            String contactPerson = "";
            String contactPhone = "";
            String website = "";
            if (application.getCompanyId() != null) {
                CompanyUser company = companyUserService.findById(application.getCompanyId());
                if (company != null) {
                    contactPerson = company.getContactPerson() != null ? company.getContactPerson() : "";
                    contactPhone = company.getContactPhone() != null ? company.getContactPhone() : "";
                    website = company.getWebsite() != null ? company.getWebsite() : "";
                }
            }

            // 创建面试邀请
            InterviewInvitation invitation = new InterviewInvitation();
            invitation.setStudentId(application.getStudentId());
            invitation.setPositionId(application.getPositionId());
            invitation.setCompanyId(application.getCompanyId());
            invitation.setPositionName(position.getPositionName());
            invitation.setCompanyName(companyName);
            if (position.getInterviewTime() != null) {
                invitation.setInterviewTime(position.getInterviewTime().toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
            }
            invitation.setInterviewLocation(position.getInterviewLocation());
            invitation.setInterviewMethod(position.getInterviewMethod());
            invitation.setRemark(position.getInterviewRemark());
            invitation.setStatus("pending_interview");
            invitation.setContactPerson(contactPerson);
            invitation.setContactPhone(contactPhone);
            interviewInvitationService.create(invitation);

            // WebSocket推送面试邀请给学生
            Map<String, Object> data = new HashMap<>();
            data.put("id", invitation.getId());
            data.put("positionId", position.getId());
            data.put("positionName", position.getPositionName());
            data.put("companyId", position.getCompanyId());
            data.put("companyName", companyName);
            data.put("interviewTime", position.getInterviewTime());
            data.put("interviewLocation", position.getInterviewLocation());
            data.put("interviewMethod", position.getInterviewMethod());
            data.put("status", "pending_interview");
            data.put("contactPerson", contactPerson);
            data.put("contactPhone", contactPhone);
            data.put("website", website);

            // studentId 就是学生的 userId
            Long studentUserId = application.getStudentId();
            if (studentUserId != null) {
                webSocketHandler.sendInterviewCreateToUser(studentUserId, data);
                log.info("已创建面试邀请并推送WebSocket，学生ID: {}", application.getStudentId());
            }
        } catch (Exception e) {
            log.error("创建面试邀请失败：{}", e.getMessage(), e);
        }
    }

}
