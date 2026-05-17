package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentJobApplication;
import com.gdmu.entity.User;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.StudentJobApplicationService;
import com.gdmu.service.UserService;

import java.util.Date;
import java.util.List;
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
    private StudentJobApplicationService applicationService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private com.gdmu.websocket.AnnouncementWebSocketHandler webSocketHandler;

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
            // 转换行业英文为中文
            for (StudentJobApplication app : applications) {
                if (app.getIndustryName() != null) {
                    app.setIndustryName(convertIndustryToChinese(app.getIndustryName()));
                }
            }
            return Result.success(applications);
        } catch (Exception e) {
            log.error("获取申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取失败");
        }
    }

    @PostMapping
    @Log(operationType = "ADD", module = "APPLICATION_MANAGEMENT", description = "申请职位")
    public Result create(@RequestBody StudentJobApplication application) {
        try {
            User user = getCurrentUser();
            if (user == null) return Result.error("未登录");
            // 强制使用JWT中的studentId，不信任前端传入值
            application.setStudentId(user.getId());
            applicationService.create(application);

            // 同步写入进度记录
            InternshipProgressRecord record = new InternshipProgressRecord();
            record.setStudentId(user.getId());
            record.setEventType("job_application");
            record.setEventTitle("投递申请");
            record.setDescription(application.getPositionName() + " @ " + application.getCompanyName());
            record.setStatus("pending");
            record.setRelatedId(application.getId());
            record.setEventTime(new Date());
            progressRecordService.saveRecord(record);

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
