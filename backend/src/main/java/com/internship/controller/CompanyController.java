package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Announcement;
import com.internship.entity.CompanyUser;
import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.PageResult;
import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.InternshipStageAssignment;
import com.internship.exception.BusinessException;
import com.internship.service.AnnouncementService;
import com.internship.service.CompanyUserService;
import com.internship.service.DirectHireService;
import com.internship.service.InternshipApplicationService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.PositionCacheService;
import com.internship.service.PositionService;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.InternshipFlowRules;
import com.internship.service.JobApplicationResultNotification;
import com.internship.service.JobApplicationResultNotifier;
import com.internship.utils.CurrentHolder;
import com.internship.utils.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.internship.entity.InterviewInvitation;

import static com.internship.service.InternshipProgressEvents.append;

@Slf4j
@RestController
@RequestMapping("/api/company")
@PreAuthorize("hasRole('COMPANY')")
public class CompanyController {

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private PositionCacheService positionCacheService;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private InternshipApplicationService internshipApplicationService;

    @Autowired
    private StudentJobApplicationService studentJobApplicationService;

    @Autowired
    private InternshipStageAssignmentService internshipStageAssignmentService;

    @Autowired
    private com.internship.service.AnnouncementReadRecordService announcementReadRecordService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private com.internship.websocket.AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private com.internship.service.InterviewInvitationService interviewInvitationService;

    @Autowired
    private DirectHireService directHireService;

    @Autowired
    private com.internship.service.CompanyTagService companyTagService;

    @Autowired
    private JobApplicationResultNotifier jobApplicationResultNotifier;

    @Autowired(required = false)
    private com.internship.service.UserCacheService userCacheService;

    @GetMapping("/info")
    public Result getCompanyInfo(@RequestParam Long companyId) {
        Long currentCompanyId = requireCurrentCompanyId();
        if (!currentCompanyId.equals(companyId)) {
            throw new AccessDeniedException("无权访问其他企业资料");
        }
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
            companyInfo.put("auditStatus", company.getAuditStatus());
            companyInfo.put("isInternshipBase", company.getIsInternshipBase());
            companyInfo.put("acceptBackup", company.getAcceptBackup());
            companyInfo.put("companyTag", company.getCompanyTag());
            companyInfo.put("logo", company.getLogo());
            companyInfo.put("photos", company.getPhotos());
            companyInfo.put("videos", company.getVideos());
            companyInfo.put("isAlumniUnit", company.getIsAlumniUnit());
            companyInfo.put("alumniMembers", company.getAlumniMembers());
            companyInfo.put("hasTrainingBaseAgreement", company.getHasTrainingBaseAgreement());

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

            log.info("企业个人信息查询成功");
            return Result.success(profile);
        } catch (Exception e) {
            log.error("获取个人信息失败", e);
            return Result.error("获取个人信息失败：" + e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业更新个人信息")
    @PutMapping("/profile")
    public Result updateProfile(@RequestBody Map<String, Object> profileData) {
        log.info("收到企业账号个人信息更新请求");
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
                String email = profileData.get("email") == null ? "" : profileData.get("email").toString().trim();
                if (!email.isEmpty()) {
                    if (!email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                        return Result.error("邮箱格式不正确");
                    }
                    company.setEmail(email);
                }
            }

            company.setUpdateTime(new java.util.Date());
            int result = companyUserService.updateProfile(company);
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
                String website = (String) companyInfo.get("website");
                if (website != null && !website.isEmpty() && !website.matches("^https?://.*")) {
                    return Result.error("企业网站必须以 http:// 或 https:// 开头");
                }
                company.setWebsite(website);
            }
            if (companyInfo.containsKey("description")) {
                company.setDescription((String) companyInfo.get("description"));
            }
            // 合作模式由准入审核确定，企业资料修改不得改写。
            if (companyInfo.containsKey("isInternshipBase")) {
                company.setIsInternshipBase((Integer) companyInfo.get("isInternshipBase"));
            }
            if (companyInfo.containsKey("acceptBackup")) {
                company.setAcceptBackup((Integer) companyInfo.get("acceptBackup"));
            }
            if (companyInfo.containsKey("isAlumniUnit")) {
                Integer isAlumniUnit = parseBinaryFlag(companyInfo.get("isAlumniUnit"), "是否校友单位");
                company.setIsAlumniUnit(isAlumniUnit);
                if (isAlumniUnit == 0 && !companyInfo.containsKey("alumniMembers")) {
                    company.setAlumniMembers(new ArrayList<>());
                }
            }
            if (companyInfo.containsKey("alumniMembers")) {
                company.setAlumniMembers(parseAlumniMembers(companyInfo.get("alumniMembers")));
            }
            if (companyInfo.containsKey("hasTrainingBaseAgreement")) {
                company.setHasTrainingBaseAgreement(parseBinaryFlag(
                        companyInfo.get("hasTrainingBaseAgreement"), "是否签订实习基地协议"));
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
                // 企业更新后同步重建自动标签（接受兜底/合作方式），保留手动标签
                companyTagService.updateCompanyTag(companyId);
                // 企业信息影响岗位展示（实习基地标签等），清除岗位列表缓存
                positionCacheService.clearPositionsCache();
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

            // 从student_job_application表获取待处理申请数
            List<StudentJobApplication> applications = studentJobApplicationService.findByCompanyId(companyId);
            Long pendingApplications = applications.stream()
                    .filter(a -> "pending".equals(a.getStatus()))
                    .count();

            // 从student_internship_status表获取待确认实习表数
            List<StudentInternshipStatus> allStatuses = studentInternshipStatusService.list(null, null, null, null, companyId, null, null, null, null, null);
            // 过滤掉已撤回的记录（recall_status=2表示已撤回）
            allStatuses = allStatuses.stream()
                    .filter(s -> s.getRecallStatus() == null || s.getRecallStatus() != 2)
                    .collect(java.util.stream.Collectors.toList());
            Long totalConfirmations = (long) allStatuses.size();
            Long pendingConfirmations = allStatuses.stream()
                    .filter(s -> s.getCompanyConfirmStatus() != null && s.getCompanyConfirmStatus() == 0)
                    .count();
            Long confirmedConfirmations = allStatuses.stream()
                    .filter(s -> s.getCompanyConfirmStatus() != null && s.getCompanyConfirmStatus() == 1)
                    .count();

            stats.put("publishedPositions", publishedPositions);
            stats.put("totalApplications", (long) applications.size());
            stats.put("pendingApplications", pendingApplications);
            stats.put("pendingConfirmations", pendingConfirmations);
            stats.put("totalConfirmations", totalConfirmations);
            stats.put("confirmedConfirmations", confirmedConfirmations);

            log.info("统计数据：发布职位={}, 待处理申请={}, 待确认实习表={}, 已确认实习表={}",
                    publishedPositions, pendingApplications, pendingConfirmations, confirmedConfirmations);

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
                // 清除职位列表缓存，确保学生端立即看到新职位
                positionCacheService.clearPositionsCache();
                // 向所有学生推送新职位数据，实时更新职位列表
                webSocketHandler.sendPositionUpdateToAll(position);
                log.info("岗位创建成功，已清除职位缓存并推送更新");
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
    @PreAuthorize("hasRole('COMPANY')")
    public Result updatePosition(@PathVariable Long id, @RequestBody Position position) {
        log.info("更新岗位：id={}", id);
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                throw new org.springframework.security.access.AccessDeniedException("未获取到当前登录企业 ID");
            }
            position.setId(id);
            int result = positionService.updateForCompany(position, companyId);
            if (result > 0) {
                // 清除职位列表缓存
                positionCacheService.clearPositionsCache();
                log.info("岗位更新成功，已清除职位缓存");
                return Result.success("岗位更新成功");
            }
            return Result.error("岗位更新失败");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新岗位失败：{}", e.getMessage(), e);
            return Result.error("更新岗位失败：" + e.getMessage());
        }
    }

    @Log(operationType = "DELETE", module = "POSITION_MANAGEMENT", description = "企业删除岗位")
    @DeleteMapping("/positions/{id}")
    @PreAuthorize("hasRole('COMPANY')")
    public Result deletePosition(@PathVariable Long id) {
        log.info("删除岗位：id={}", id);
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                throw new org.springframework.security.access.AccessDeniedException("未获取到当前登录企业 ID");
            }
            int result = positionService.deleteForCompany(id, companyId);
            if (result > 0) {
                // 清除职位列表缓存
                positionCacheService.clearPositionsCache();
                // 向所有学生推送岗位删除消息
                webSocketHandler.sendPositionDeleteToAll(id);
                log.info("岗位删除成功，已清除职位缓存并推送删除消息");
                return Result.success("岗位删除成功");
            }
            return Result.error("岗位删除失败");
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除岗位失败：{}", e.getMessage(), e);
            return Result.error("删除岗位失败：" + e.getMessage());
        }
    }

    private Integer parseBinaryFlag(Object value, String fieldName) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue ? 1 : 0;
        }
        if (value instanceof Number number) {
            int result = number.intValue();
            if (result == 0 || result == 1) {
                return result;
            }
        }
        if (value instanceof String text && ("0".equals(text) || "1".equals(text))) {
            return Integer.valueOf(text);
        }
        throw new BusinessException(fieldName + "必须为0或1");
    }

    private List<CompanyAlumniMember> parseAlumniMembers(Object value) {
        if (!(value instanceof List<?> rawMembers)) {
            throw new BusinessException("校友列表格式不正确");
        }
        List<CompanyAlumniMember> members = new ArrayList<>();
        for (Object rawMember : rawMembers) {
            if (!(rawMember instanceof Map<?, ?> memberMap)) {
                throw new BusinessException("校友列表格式不正确");
            }
            CompanyAlumniMember member = new CompanyAlumniMember();
            member.setName(asNullableString(memberMap.get("name")));
            member.setPhone(asNullableString(memberMap.get("phone")));
            member.setPosition(asNullableString(memberMap.get("position")));
            members.add(member);
        }
        return members;
    }

    private String asNullableString(Object value) {
        return value == null ? null : value.toString();
    }

    private Long getCurrentCompanyId() {
        Long companyId = CurrentHolder.getUserId();
        if (companyId == null || !"ROLE_COMPANY".equals(CurrentHolder.getUserRole())) {
            log.warn("当前身份不是企业或未获取到企业 ID");
            return null;
        }
        log.debug("获取到当前登录企业 ID: {}", companyId);
        return companyId;
    }

    private Long requireCurrentCompanyId() {
        Long companyId = getCurrentCompanyId();
        if (companyId == null) {
            throw new AccessDeniedException("当前身份不是企业");
        }
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

            if (company.getEmail() == null || company.getEmail().trim().isEmpty()) {
                return Result.error("请先在个人信息中绑定邮箱后再修改密码，绑定后可使用忘记密码找回");
            }

            String oldPassword = passwordData.get("oldPassword");
            String newPassword = passwordData.get("newPassword");

            if (oldPassword == null || oldPassword.isEmpty()) {
                return Result.error("请输入原密码");
            }
            if (newPassword == null || newPassword.isEmpty()) {
                return Result.error("请输入新密码");
            }

            if (!passwordEncoder.matches(oldPassword, company.getPassword())) {
                return Result.error("原密码错误");
            }

            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
            if (!validationResult.isValid()) {
                return Result.error(validationResult.getMessage());
            }

            company.setPassword(newPassword);
            company.setUpdateTime(new java.util.Date());
            int result = companyUserService.update(company);
            if (result > 0) {
                // 清除该用户在 Redis 中的缓存，避免登录时仍命中旧密码哈希
                if (userCacheService != null && company.getUsername() != null) {
                    userCacheService.evictUserCache(company.getUsername());
                }
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
        Long currentCompanyId = requireCurrentCompanyId();
        if (companyId != null && !currentCompanyId.equals(companyId)) {
            throw new AccessDeniedException("无权访问其他企业的申请");
        }
        log.info("获取企业最新岗位申请：companyId={}, limit={}", currentCompanyId, limit);
        try {
            // 查询岗位申请表，获取最近的申请记录
            List<InternshipApplicationEntity> applications = internshipApplicationService.findByCompanyId(currentCompanyId);

            java.util.List<InternshipApplicationEntity> recentApplications = applications.stream()
                    .filter(application -> currentCompanyId.equals(application.getCompanyId()))
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
        Long companyId = requireCurrentCompanyId();
        log.info("标记申请为已读，ID: {}", id);
        try {
            InternshipApplicationEntity application = internshipApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }
            if (!companyId.equals(application.getCompanyId())) {
                throw new AccessDeniedException("无权操作其他企业的申请");
            }
            boolean result = internshipApplicationService.markAsViewed(id);
            if (result) {
                return Result.success("标记成功");
            } else {
                return Result.error("标记失败");
            }
        } catch (AccessDeniedException e) {
            throw e;
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
                    // 支持 JSON 数组格式或单个值（包含 COMPANY 或 ALL 类型）
                    if (targetType.startsWith("[")) {
                        return targetType.contains("\"COMPANY\"") || targetType.contains("\"ALL\"");
                    } else {
                        return "COMPANY".equals(targetType) || "ALL".equals(targetType);
                    }
                })
                .limit(limit.longValue())
                .toList();

            log.info("查询到的通知数量：{}", announcements.size());

            // 优化：批量查询该企业的所有已读记录，避免N+1问题
            java.util.Map<Long, Boolean> readStatusMap = new java.util.HashMap<>();
            try {
                java.util.List<com.internship.entity.AnnouncementReadRecord> readRecords =
                    announcementReadRecordService.findByUserId(String.valueOf(companyId), "ENTERPRISE");
                for (com.internship.entity.AnnouncementReadRecord record : readRecords) {
                    if (record.getAnnouncementId() != null) {
                        readStatusMap.put(record.getAnnouncementId(), true);
                    }
                }
            } catch (Exception e) {
                log.warn("批量查询已读记录失败: {}", e.getMessage());
            }

            List<java.util.Map<String, Object>> result = announcements.stream()
                    .map(announcement -> {
                        // 从预查询的Map中获取已读状态
                        boolean isRead = readStatusMap.getOrDefault(announcement.getId(), false);
                        log.debug("公告ID: {}, 企业ID: {}, 是否已读: {}", announcement.getId(), companyId, isRead);

                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", announcement.getId());
                        item.put("type", "system"); // 公告类型统一为 system
                        item.put("title", announcement.getTitle());
                        item.put("content", announcement.getContent());
                        item.put("time", formatTime(announcement.getPublishTime()));
                        item.put("publishTime", announcement.getPublishTime());
                        item.put("publisher", announcement.getPublisher());
                        item.put("publisherRole", announcement.getPublisherRole());
                        item.put("positionId", (Long) null);
                        item.put("positionName", "");
                        item.put("isRead", isRead); // 添加阅读状态
                        return item;
                    })
                    .toList();

            log.info("企业通知查询完成：count={}", result.size());
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
            List<InternshipApplicationEntity> ownedApplications = applications.stream()
                    .filter(application -> companyId.equals(application.getCompanyId()))
                    .toList();
            log.info("获取到 {} 条岗位申请记录", ownedApplications.size());
            return Result.success(ownedApplications);
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

            List<com.internship.entity.StudentJobApplication> applications = studentJobApplicationService.findByCompanyId(companyId);
            for (com.internship.entity.StudentJobApplication application : applications) {
                application.setDirectHire(directHireService.isDirectHire(
                        application.getStudentId(), application.getPositionId()));
            }
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
    @Transactional(rollbackFor = Exception.class)
    public synchronized Result updateJobApplicationStatus(@PathVariable Long id, @RequestBody Map<String, String> statusData) {
        log.info("企业更新学生快速申请状态，ID: {}, 状态: {}", id, statusData.get("status"));
        try {
            Long companyId = getCurrentCompanyId();
            if (companyId == null) {
                return Result.error("未获取到当前登录企业 ID");
            }

            com.internship.entity.StudentJobApplication application = studentJobApplicationService.findById(id);
            if (application == null) {
                return Result.error("申请不存在");
            }

            // 验证该申请属于当前企业
            if (!companyId.equals(application.getCompanyId())) {
                return Result.error("无权操作此申请");
            }

            String newStatus = statusData.get("status");
            String rejectReason = com.internship.service.StudentJobApplicationStatuses
                    .normalizeRejectReason(newStatus, statusData.get("rejectReason"));

            String currentStatus = application.getStatus();
            if (!com.internship.service.StudentJobApplicationStatuses.isActive(currentStatus)) {
                return Result.error("该申请已处理，请勿重复操作");
            }

            // 如果是面试通过或面试没通过，需要同时更新面试邀请状态和学生求职申请状态
            if ("interview_passed".equals(newStatus) || "interview_failed".equals(newStatus)) {
                // 更新面试邀请状态
                InterviewInvitation invitation = interviewInvitationService.findByStudentAndPosition(
                    application.getStudentId(), application.getPositionId());
                if (invitation != null) {
                    // 先更新申请状态(轻量操作, 不会失败)
                    if (studentJobApplicationService.updateStatusIfActive(
                            application.getId(), newStatus, rejectReason) <= 0) {
                        return Result.error("该申请已被处理，请刷新后重试");
                    }
                    application.setStatus(newStatus);
                    syncLatestLegacyApplication(
                            application.getStudentId(), application.getPositionId(), newStatus);

                    // 再更新邀请状态
                    interviewInvitationService.updateStatus(invitation.getId(), newStatus, null);
                    log.info("更新面试邀请状态成功，invitationId: {}, status: {}", invitation.getId(), newStatus);

                    appendInterviewOutcome(application.getStudentId(), invitation.getId(),
                            application.getCompanyName(), application.getPositionName(),
                            newStatus);
                    queueJobResultEmail(
                            application.getId(), application.getStudentId(), application.getCompanyId(),
                            application.getCompanyName(), application.getPositionId(),
                            application.getPositionName(), newStatus, false, rejectReason);

                    // 面试通过时，创建或更新学生实习状态为待确认
                    if ("interview_passed".equals(newStatus)) {
                        InternshipStageAssignment stageAssignment = new InternshipStageAssignment();
                        stageAssignment.setStudentId(application.getStudentId());
                        stageAssignment.setStageNo(application.getStageNo() == null ? 1 : application.getStageNo());
                        stageAssignment.setApplicationId(application.getId());
                        stageAssignment.setSourceType(application.getSourceType() == null
                                ? InternshipFlowRules.SOURCE_NEW_APPLICATION : application.getSourceType());
                        stageAssignment.setCompanyId(application.getCompanyId());
                        stageAssignment.setCompanyName(application.getCompanyName());
                        stageAssignment.setPositionId(application.getPositionId());
                        stageAssignment.setPositionName(application.getPositionName());
                        stageAssignment.setStatus("PENDING_CONFIRMATION");
                        internshipStageAssignmentService.createFromApplication(stageAssignment);

                    }

                    // WebSocket推送面试状态更新给学生（handler内部会转换statusText）
                    webSocketHandler.sendInterviewStatusUpdateToUser(application.getStudentId(), invitation.getId(), newStatus);

                    return Result.success("更新成功");
                } else {
                    return Result.error("未找到面试邀请记录");
                }
            }

            // 申请状态更新
            int result = studentJobApplicationService.updateStatusIfActive(
                    application.getId(), newStatus, rejectReason);
            application.setRejectReason(rejectReason);
            application.setStatus(newStatus);
            if (result > 0) {
                syncLatestLegacyApplication(
                        application.getStudentId(), application.getPositionId(), newStatus);
            }

            // 如果是同意申请，需要创建面试邀请
            if (result > 0 && "approved".equals(newStatus)) {
                Position position = positionService.findById(application.getPositionId());
                if (position != null) {
                    createInterviewInvitationIfNotExistsForJobApplication(application, position);
                }
            }

            // 如果是拒绝申请，更新投递申请进度记录为未通过
            if (result > 0 && "rejected".equals(newStatus)) {
                append(progressRecordService,
                        application.getStudentId(),
                        "job_rejected",
                        "岗位申请未通过",
                        application.getCompanyName() + " · " + application.getPositionName(),
                        "failed",
                        application.getId(),
                        new Date());
            }

            if (result > 0) {
                queueJobResultEmail(
                        application.getId(), application.getStudentId(), application.getCompanyId(),
                        application.getCompanyName(), application.getPositionId(),
                        application.getPositionName(), newStatus, false, rejectReason);
                // 推送待办数据更新
                pushTodoUpdate(companyId);
                // 向学生推送申请状态更新通知
                Long studentUserId = application.getStudentId();
                if (studentUserId != null) {
                    notifyStudentApplicationStatus(studentUserId, application.getStudentName(),
                        application.getPositionName(), newStatus, application.getCompanyName() != null ? application.getCompanyName() : "");
                }
                return Result.success("更新成功");
            } else {
                return Result.error("更新失败");
            }
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("更新学生快速申请状态失败：{}", e.getMessage(), e);
            return Result.error("更新失败：" + e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业直接录取学生")
    @PostMapping("/job-applications/{id}/direct-hire")
    @PreAuthorize("hasRole('COMPANY')")
    @Transactional(rollbackFor = Exception.class)
    public synchronized Result directHire(@PathVariable Long id) {
        Long companyId = getCurrentCompanyId();
        if (companyId == null) {
            return Result.error("未获取到当前登录企业 ID");
        }

        try {
            DirectHireService.DirectHireResult directHire = directHireService.directHire(companyId, id);
            syncLatestLegacyApplication(
                    directHire.studentId(), directHire.positionId(), "interview_passed");

            com.internship.entity.StudentJobApplication directHireApplication =
                    studentJobApplicationService.findById(directHire.applicationId());
            if (directHireApplication != null) {
                InternshipStageAssignment stageAssignment = new InternshipStageAssignment();
                stageAssignment.setStudentId(directHireApplication.getStudentId());
                stageAssignment.setStageNo(directHireApplication.getStageNo() == null ? 1 : directHireApplication.getStageNo());
                stageAssignment.setApplicationId(directHireApplication.getId());
                stageAssignment.setSourceType(directHireApplication.getSourceType() == null
                        ? InternshipFlowRules.SOURCE_NEW_APPLICATION : directHireApplication.getSourceType());
                stageAssignment.setCompanyId(directHireApplication.getCompanyId());
                stageAssignment.setCompanyName(directHireApplication.getCompanyName());
                stageAssignment.setPositionId(directHireApplication.getPositionId());
                stageAssignment.setPositionName(directHireApplication.getPositionName());
                stageAssignment.setStatus("PENDING_CONFIRMATION");
                internshipStageAssignmentService.createFromApplication(stageAssignment);
            }

            queueJobResultEmail(
                    directHire.applicationId(), directHire.studentId(), directHire.companyId(),
                    directHire.companyName(), directHire.positionId(), directHire.positionName(),
                    "interview_passed", true, null);

            Map<String, Object> applicationEvent = new HashMap<>();
            applicationEvent.put("studentId", directHire.studentId());
            applicationEvent.put("positionName", directHire.positionName());
            applicationEvent.put("companyName", directHire.companyName());
            applicationEvent.put("status", "interview_passed");
            applicationEvent.put("statusText", "直接录取（免面试）");
            applicationEvent.put("directHire", true);
            webSocketHandler.sendApplicationStatusToUser(directHire.studentId(), applicationEvent);

            Map<String, Object> interviewEvent = new HashMap<>();
            interviewEvent.put("id", directHire.invitationId());
            interviewEvent.put("positionId", directHire.positionId());
            interviewEvent.put("positionName", directHire.positionName());
            interviewEvent.put("companyId", directHire.companyId());
            interviewEvent.put("companyName", directHire.companyName());
            interviewEvent.put("interviewMethod", DirectHireService.DIRECT_HIRE_METHOD);
            interviewEvent.put("status", "interview_passed");
            interviewEvent.put("statusText", "直接录取（免面试）");
            interviewEvent.put("directHire", true);
            webSocketHandler.sendInterviewCreateToUser(directHire.studentId(), interviewEvent);

            pushTodoUpdate(companyId);

            Map<String, Object> data = new HashMap<>();
            data.put("applicationId", directHire.applicationId());
            data.put("invitationId", directHire.invitationId());
            data.put("status", "interview_passed");
            data.put("statusText", "直接录取（免面试）");
            data.put("directHire", true);
            return Result.success("直接录取成功", data);
        } catch (com.internship.exception.BusinessException e) {
            rollbackCurrentTransaction();
            return Result.error(e.getMessage());
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("直接录取学生失败，applicationId={}: {}", id, e.getMessage(), e);
            return Result.error("直接录取失败，请稍后重试");
        }
    }

    private void queueJobResultEmail(Long applicationId,
                                     Long studentId,
                                     Long companyId,
                                     String companyName,
                                     Long positionId,
                                     String positionName,
                                     String status,
                                     boolean directHire,
                                     String rejectReason) {
        jobApplicationResultNotifier.afterCommit(new JobApplicationResultNotification(
                applicationId, studentId, companyId, companyName,
                positionId, positionName, status, directHire, rejectReason));
    }

    private void syncLatestLegacyApplication(Long studentId, Long positionId, String status) {
        if (!internshipApplicationService.updateLatestStatusByStudentAndPosition(
                studentId, positionId, status)) {
            log.warn("兼容申请记录未匹配到活动镜像: studentId={}, positionId={}, status={}",
                    studentId, positionId, status);
        }
    }

    /**
     * 更新申请状态（同意/拒绝）
     */
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "企业更新申请状态")
    @PutMapping("/applications/{id}/status")
    @Transactional(rollbackFor = Exception.class)
    public synchronized Result updateApplicationStatus(@PathVariable Long id, @RequestBody Map<String, String> statusData) {
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
            String rejectReason = com.internship.service.StudentJobApplicationStatuses
                    .normalizeRejectReason(newStatus, statusData.get("rejectReason"));

            String curStatus2 = application.getStatus();
            if (!com.internship.service.StudentJobApplicationStatuses.isActive(curStatus2)) {
                return Result.error("该申请已处理，请勿重复操作");
            }

            boolean result = internshipApplicationService.updateStatus(id, newStatus);

            if (!result) {
                return Result.error("更新失败");
            }

            int synced = studentJobApplicationService.updateLatestStatusIfActive(
                    application.getStudentId(), application.getPositionId(), newStatus, rejectReason);
            if (synced <= 0) {
                log.warn("兼容申请记录未匹配到活动主记录: mirrorId={}, studentId={}, positionId={}",
                        id, application.getStudentId(), application.getPositionId());
            }

            if ("rejected".equals(newStatus)) {
                CompanyUser eventCompany = companyUserService.findById(application.getCompanyId());
                append(progressRecordService,
                        application.getStudentId(),
                        "job_rejected",
                        "岗位申请未通过",
                        (eventCompany == null ? "" : eventCompany.getCompanyName() + " · ")
                                + application.getPositionName(),
                        "failed",
                        resolveJobApplicationId(application),
                        new Date());
            }

            // 同意申请只代表获得面试机会，不提前进入待确认状态
            if ("approved".equals(newStatus)) {
                Position position = positionService.findById(application.getPositionId());
                if (position != null) {
                    createInterviewInvitationIfNotExists(application, position);
                }
            }

            if ("interview_passed".equals(newStatus) || "interview_failed".equals(newStatus)) {
                if ("interview_failed".equals(newStatus)
                        && application.getStudentId() != null && application.getPositionId() != null) {
                    studentInternshipStatusService.rollbackPendingConfirmationForFailedApplication(
                            application.getStudentId(), application.getPositionId());
                }
                InterviewInvitation invitation = interviewInvitationService.findByStudentAndPosition(
                    application.getStudentId(), application.getPositionId());
                if (invitation != null) {
                    interviewInvitationService.updateStatus(invitation.getId(), newStatus, null);
                    CompanyUser outcomeCompany = companyUserService.findById(application.getCompanyId());
                    appendInterviewOutcome(application.getStudentId(), invitation.getId(),
                            outcomeCompany == null ? "" : outcomeCompany.getCompanyName(),
                            application.getPositionName(), newStatus);
                    webSocketHandler.sendInterviewStatusUpdateToUser(
                        application.getStudentId(), invitation.getId(), newStatus);
                }
            }

            Long emailApplicationId = resolveJobApplicationId(application);
            CompanyUser company = companyUserService.findById(companyId);
            queueJobResultEmail(
                    emailApplicationId == null ? application.getId() : emailApplicationId,
                    application.getStudentId(), application.getCompanyId(),
                    company == null ? "" : company.getCompanyName(), application.getPositionId(),
                    application.getPositionName(), newStatus, false, rejectReason);

            // 向学生推送申请状态更新通知
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

            // 推送待办数据更新
            pushTodoUpdate(companyId);

            return Result.success("更新成功");
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("更新申请状态失败：{}", e.getMessage(), e);
            return Result.error("更新申请状态失败：" + e.getMessage());
        }
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
            List<StudentInternshipStatus> allStatuses = studentInternshipStatusService.list(null, null, null, null, companyId, null, null, null, null, null);
            long pendingConfirmations = allStatuses.stream()
                    .filter(s -> s.getCompanyConfirmStatus() != null && s.getCompanyConfirmStatus() == 0)
                    .count();

            webSocketHandler.sendCompanyTodoUpdate(companyId, pendingApplications, pendingConfirmations);
        } catch (Exception e) {
            log.error("推送待办数据失败: {}", e.getMessage());
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
            if (existing != null
                    && !com.internship.service.StudentJobApplicationStatuses.canRetry(existing.getStatus())) {
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

            append(progressRecordService,
                    application.getStudentId(),
                    "interview_invited",
                    "收到面试邀请",
                    companyName + " · " + position.getPositionName(),
                    "pending",
                    invitation.getId(),
                    new Date());

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
            throw new BusinessException("创建面试邀请失败: " + e.getMessage());
        }
    }

    /**
     * 为学生求职申请创建面试邀请（如果不存在）
     * @param application 学生求职申请记录
     * @param position 岗位信息
     */
    private void createInterviewInvitationIfNotExistsForJobApplication(com.internship.entity.StudentJobApplication application, Position position) {
        try {
            // 检查是否已存在该学生和岗位的面试邀请
            InterviewInvitation existing = interviewInvitationService.findByStudentAndPosition(
                application.getStudentId(), application.getPositionId());
            if (existing != null
                    && !com.internship.service.StudentJobApplicationStatuses.canRetry(existing.getStatus())) {
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

            append(progressRecordService,
                    application.getStudentId(),
                    "interview_invited",
                    "收到面试邀请",
                    companyName + " · " + position.getPositionName(),
                    "pending",
                    invitation.getId(),
                    new Date());

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
            throw new BusinessException("创建面试邀请失败: " + e.getMessage());
        }
    }

    private void appendInterviewOutcome(Long studentId,
                                        Long invitationId,
                                        String companyName,
                                        String positionName,
                                        String outcome) {
        boolean passed = "interview_passed".equals(outcome);
        append(progressRecordService,
                studentId,
                passed ? "interview_passed" : "interview_failed",
                passed ? "面试已通过" : "面试未通过",
                (companyName == null || companyName.isBlank() ? "" : companyName + " · ")
                        + positionName,
                passed ? "success" : "failed",
                invitationId,
                new Date());
    }

    private Long resolveJobApplicationId(InternshipApplicationEntity application) {
        List<StudentJobApplication> applications = studentJobApplicationService
                .findByStudentId(application.getStudentId());
        if (applications == null) return null;
        return applications.stream()
                .filter(item -> application.getPositionId().equals(item.getPositionId()))
                .filter(item -> application.getStageNo() == null || item.getStageNo() == null
                        || application.getStageNo().equals(item.getStageNo()))
                .map(StudentJobApplication::getId)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .orElse(null);
    }

    private void rollbackCurrentTransaction() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Direct controller unit tests do not run through a transactional proxy.
        }
    }

}
