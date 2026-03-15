package com.gdmu.controller;

import com.gdmu.entity.Announcement;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.InternshipApplicationEntity;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Position;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.service.AnnouncementService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.InternshipApplicationService;
import com.gdmu.service.PositionService;
import com.gdmu.service.StudentInternshipStatusService;
import com.gdmu.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private com.gdmu.service.AnnouncementReadRecordService announcementReadRecordService;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
            List<StudentInternshipStatus> allStatuses = studentInternshipStatusService.list(null, null, null, null, companyId, null);
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
        log.info("获取企业最新实习确认申请：companyId={}, limit={}", companyId, limit);
        try {
            // 如果没有传 companyId，使用当前登录企业的 ID
            if (companyId == null) {
                companyId = getCurrentCompanyId();
            }
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }
            List<StudentInternshipStatus> statuses = studentInternshipStatusService.list(null, null, null, null, companyId, null);

            java.util.List<StudentInternshipStatus> recentStatuses = statuses.stream()
                    .sorted((a, b) -> b.getCreateTime().compareTo(a.getCreateTime()))
                    .limit(limit)
                    .toList();

            java.util.List<Map<String, Object>> result = recentStatuses.stream()
                    .map(status -> {
                        Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", status.getId());
                        item.put("studentName", status.getStudent() != null ? status.getStudent().getName() : "");
                        item.put("positionName", status.getPosition() != null ? status.getPosition().getPositionName() : "");
                        item.put("applyTime", status.getCreateTime());
                        item.put("status", status.getCompanyConfirmStatus() != null ?
                            (status.getCompanyConfirmStatus() == 0 ? "pending" :
                             status.getCompanyConfirmStatus() == 1 ? "confirmed" : "rejected") : "pending");
                        return item;
                    })
                    .toList();

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取最新实习确认申请失败：{}", e.getMessage(), e);
            return Result.error("获取最新实习确认申请失败：" + e.getMessage());
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
                .limit(limit)
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
}
