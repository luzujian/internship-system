package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.StudentUser;
import com.internship.entity.User;
import com.internship.entity.dto.StudentPositionKey;
import com.internship.service.DirectHireService;
import com.internship.service.PositionService;
import com.internship.service.StudentJobApplicationService;
import com.internship.service.UserService;
import com.internship.service.StudentUserService;
import com.internship.utils.StudentProfileCompleteness;

import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/student/job-applications")
@PreAuthorize("hasRole('STUDENT')")
public class StudentJobApplicationController {

    @Autowired
    private UserService userService;

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private StudentJobApplicationService applicationService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private DirectHireService directHireService;

    @Autowired
    private com.internship.websocket.AnnouncementWebSocketHandler webSocketHandler;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    @GetMapping
    public Result list() {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            List<StudentJobApplication> applications = applicationService.findByStudentId(user.getId());
            Set<StudentPositionKey> directHirePairs = directHireService.findDirectHirePairs(applications.stream()
                    .map(app -> new StudentPositionKey(app.getStudentId(), app.getPositionId())).toList());
            // 转换行业英文为中文
            for (StudentJobApplication app : applications) {
                if (app.getIndustryName() != null) {
                    app.setIndustryName(convertIndustryToChinese(app.getIndustryName()));
                }
                app.setDirectHire(directHirePairs.contains(new StudentPositionKey(app.getStudentId(), app.getPositionId())));
            }
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    /** Return one application only when owned by the authenticated student. */
    @GetMapping("/{id}")
    public Result getById(@PathVariable Long id) {
        User user = getCurrentUser();
        if (user == null) return Result.error("未登录");
        try {
            StudentJobApplication application = applicationService.findById(id);
            if (application == null) return Result.error("申请不存在");
            if (!user.getId().equals(application.getStudentId())) {
                throw new org.springframework.security.access.AccessDeniedException("无权查看此申请");
            }
            if (application.getIndustryName() != null) {
                application.setIndustryName(convertIndustryToChinese(application.getIndustryName()));
            }
            application.setDirectHire(directHireService.isDirectHire(
                    application.getStudentId(), application.getPositionId()));
            return Result.success(application);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取申请详情失败: {}", e.getMessage(), e);
            return Result.error("获取申请详情失败");
        }
    }
    @PostMapping
    @Log(operationType = "ADD", module = "APPLICATION_MANAGEMENT", description = "申请职位")
    public Result create(@RequestBody StudentJobApplication application) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            // 岗位申请前置校验：姓名、学号、电话、邮箱必须全部完善
            StudentUser studentUser = studentUserService.findById(user.getId());
            List<String> profileMissing = StudentProfileCompleteness.missingRequiredFields(studentUser);
            if (!profileMissing.isEmpty()) {
                return Result.error(StudentProfileCompleteness.incompleteMessage(profileMissing));
            }
            // 强制使用JWT中的studentId，不信任前端传入值
            application.setStudentId(user.getId());
            // 用已校验的最新资料生成申请快照，不能依赖浏览器中可能过期的用户缓存。
            application.setStudentName(studentUser.getName());
            application.setStudentNo(studentUser.getStudentId());
            application.setPhone(studentUser.getPhone());
            application.setEmail(studentUser.getEmail());
            application.setMajor(studentUser.getMajor());
            application.setGrade(studentUser.getGrade() == null ? null : String.valueOf(studentUser.getGrade()));
            Position position = positionService.findById(application.getPositionId());
            if (position == null) {
                return Result.error("职位不存在");
            }
            if (!"active".equalsIgnoreCase(position.getStatus())) {
                return Result.error("该岗位已暂停招聘，无法申请");
            }
            if (position.getInternshipEndDate() != null
                    && java.time.Instant.ofEpochMilli(position.getInternshipEndDate().getTime())
                    .atZone(java.time.ZoneId.of("Asia/Shanghai")).toLocalDate()
                    .isBefore(java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")))) {
                return Result.error("该岗位实习期已结束,无法申请");
            }
            applicationService.create(application);

            // 推送给企业端静默更新最新动态
            if (application.getCompanyId() != null) {
                webSocketHandler.sendNewApplicationToCompany(
                    application.getCompanyId(),
                    application.getStudentName(),
                    application.getPositionName()
                );
                // 推送待办数据更新（待处理申请数加一）
                webSocketHandler.sendCompanyTodoUpdateForApplication(application.getCompanyId());
            }

            return Result.success("申请成功");
        } catch (Exception e) {
            log.error("创建申请失败: {}", e.getMessage(), e);
            return Result.error("申请失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @Log(operationType = "DELETE", module = "APPLICATION_MANAGEMENT", description = "删除职位申请")
    public Result delete(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");

            // 获取申请信息用于后续推送
            StudentJobApplication existingApp = applicationService.findById(id);
            Long companyId = null;
            if (existingApp != null) {
                companyId = existingApp.getCompanyId();
            }

            applicationService.deleteById(id, user.getId());

            // 推送待办数据更新给企业（待处理申请数减一）
            if (companyId != null) {
                webSocketHandler.sendCompanyTodoUpdateForApplication(companyId);
            }

            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除申请失败: {}", e.getMessage(), e);
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 将英文行业名称转换为中文
     */
    private String convertIndustryToChinese(String industry) {
        if (industry == null) {
            return null;
        }
        switch (industry.toLowerCase()) {
            case "internet":
                return "互联网";
            case "finance":
            case "financial":
                return "金融";
            case "manufacturing":
                return "制造业";
            case "education":
                return "教育";
            case "healthcare":
            case "medical":
            case "health":
                return "医疗健康";
            case "retail":
                return "零售";
            case "media":
                return "传媒";
            case "telecom":
            case "telecommunication":
                return "通信";
            case "real_estate":
            case "property":
                return "房地产";
            case "logistics":
            case "supply_chain":
                return "物流";
            case "energy":
                return "能源";
            case "construction":
                return "建筑";
            case "government":
                return "政府";
            case "nonprofit":
            case "ngo":
                return "非营利组织";
            case "other":
            default:
                return "其他";
        }
    }
}
