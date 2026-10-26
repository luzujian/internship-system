package com.internship.controller;

import com.internship.utils.ExcelExports;

import com.internship.anno.Log;
import com.internship.entity.PageResult;
import com.internship.entity.ProblemFeedback;
import com.internship.service.ProblemFeedbackSubmittedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.internship.entity.Result;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.utils.CurrentHolder;
import com.internship.vo.ProblemFeedbackVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/problem-feedback")
public class ProblemFeedbackController {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProblemFeedbackMapper problemFeedbackMapper;

    @Autowired
    private com.internship.websocket.AnnouncementWebSocketHandler webSocketHandler;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getAllFeedback() {
        log.info("获取所有问题反馈列表");
        try {
            List<ProblemFeedback> feedbackList = problemFeedbackMapper.findAll();
            return Result.success(feedbackList);
        } catch (Exception e) {
            log.error("获取问题反馈列表失败: {}", e.getMessage(), e);
            return Result.error("获取问题反馈列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public Result getFeedbackById(@PathVariable Long id, Authentication authentication) {
        log.info("根据ID获取问题反馈: {}", id);
        try {
            ProblemFeedback feedback = problemFeedbackMapper.findById(id);
            if (feedback == null) {
                return Result.error("问题反馈不存在");
            }
            
            // 安全校验：非管理员只能查看自己的反馈
            if (isAdmin(authentication)) {
                return Result.success(feedback);
            }
            Long currentUserId = CurrentHolder.getUserId();
            if (!ownsFeedback(feedback)) {
                log.warn("用户尝试查看他人的问题反馈: currentUserId={}, feedbackUserId={}", currentUserId, feedback.getUserId());
                return Result.error("无权查看他人的问题反馈");
            }
            ProblemFeedbackVO feedbackVO = convertToVO(feedback);
            return Result.success(feedbackVO);
        } catch (Exception e) {
            log.error("获取问题反馈详情失败: {}", e.getMessage(), e);
            return Result.error("获取问题反馈详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/page")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getFeedbackByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String title) {
        log.info("分页查询问题反馈，页码: {}, 每页大小: {}", page, pageSize);
        try {
            // 使用 PageHelper 进行分页查询
            PageHelper.startPage(page, pageSize);
            List<ProblemFeedback> feedbackList = problemFeedbackMapper.list(
                userType, status, priority, feedbackType, userName, title);

            // 构建 PageInfo 对象
            PageInfo<ProblemFeedback> pageInfo = new PageInfo<>(feedbackList);

            // 返回 PageResult 对象，与其他 Controller 保持一致
            PageResult<ProblemFeedback> result = PageResult.build(
                pageInfo.getTotal(),
                pageInfo.getList(),
                pageInfo.getPages(),
                pageInfo.getPageNum(),
                pageInfo.getPageSize());

            return Result.success(result);
        } catch (Exception e) {
            log.error("分页查询问题反馈失败: {}", e.getMessage(), e);
            return Result.error("分页查询问题反馈失败: " + e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_COMPANY')")
    @Log(operationType = "ADD", module = "PROBLEM_FEEDBACK", description = "提交问题反馈")
    public Result addFeedback(@RequestBody ProblemFeedback feedback) {
        log.info("新增问题反馈: {}", feedback.getTitle());
        try {
            com.internship.entity.User actor = CurrentHolder.getUser();
            String userType = currentFeedbackType();
            if (actor == null || actor.getId() == null || userType == null) {
                return Result.error("当前用户身份不可用");
            }
            feedback.setId(null);
            feedback.setUserType(userType);
            feedback.setUserId(actor.getId());
            feedback.setUserName(actor.getName());
            feedback.setUserAccount(actor.getUsername());
            String priority = "normal";
            
            if ("company".equals(userType)) {
                priority = "high";
            } else if ("teacher".equals(userType)) {
                priority = "normal";
            } else if ("student".equals(userType)) {
                priority = "low";
            }
            
            feedback.setPriority(priority);
            feedback.setStatus("processing");
            
            int result = problemFeedbackMapper.insert(feedback);
            
            if (result > 0) {
                pushFeedbackNotification(feedback);
                try {
                    eventPublisher.publishEvent(new ProblemFeedbackSubmittedEvent(feedback.getId()));
                } catch (RuntimeException notificationFailure) {
                    log.warn("问题反馈邮件任务启动失败: feedbackId={}", feedback.getId(), notificationFailure);
                }
            }
            
            return Result.success("提交问题反馈成功", result);
        } catch (Exception e) {
            log.error("提交问题反馈失败: {}", e.getMessage(), e);
            return Result.error("提交问题反馈失败: " + e.getMessage());
        }
    }

    private void pushFeedbackNotification(ProblemFeedback feedback) {
        try {
            Map<String, Object> feedbackData = new HashMap<>();
            feedbackData.put("id", feedback.getId());
            feedbackData.put("title", feedback.getTitle());
            feedbackData.put("content", feedback.getContent());
            feedbackData.put("userType", feedback.getUserType());
            feedbackData.put("userName", feedback.getUserName());
            feedbackData.put("priority", feedback.getPriority());
            feedbackData.put("feedbackType", feedback.getFeedbackType());
            feedbackData.put("createTime", feedback.getCreateTime());
            feedbackData.put("status", feedback.getStatus());
            
            webSocketHandler.sendFeedbackToAdmin(feedbackData);
            log.info("已推送问题反馈通知到管理员");
        } catch (Exception e) {
            log.error("推送问题反馈通知失败: {}", e.getMessage(), e);
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_STUDENT', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_COMPANY')")
    @Log(operationType = "UPDATE", module = "PROBLEM_FEEDBACK", description = "更新问题反馈")
    public Result updateFeedback(@PathVariable Long id, @RequestBody ProblemFeedback feedback) {
        log.info("更新问题反馈: ID={}", id);
        try {
            // 安全校验：非管理员只能更新自己的反馈
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (!isAdmin(authentication)) {
                ProblemFeedback existing = problemFeedbackMapper.findById(id);
                if (existing == null) {
                    return Result.error("问题反馈不存在");
                }
                if (!ownsFeedback(existing)) {
                    return Result.error("无权修改他人的问题反馈");
                }
                // 普通提交者仅能编辑反馈正文，处理状态与回复由管理员管理。
                ProblemFeedback editable = new ProblemFeedback();
                editable.setTitle(feedback.getTitle());
                editable.setContent(feedback.getContent());
                editable.setFeedbackType(feedback.getFeedbackType());
                editable.setAttachmentUrl(feedback.getAttachmentUrl());
                feedback = editable;
            }
            feedback.setId(id);
            int result = problemFeedbackMapper.update(feedback);
            return Result.success("更新问题反馈成功", result);
        } catch (Exception e) {
            log.error("更新问题反馈失败: {}", e.getMessage(), e);
            return Result.error("更新问题反馈失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "DELETE", module = "PROBLEM_FEEDBACK", description = "删除问题反馈")
    public Result deleteFeedback(@PathVariable Long id) {
        log.info("删除问题反馈: ID={}", id);
        try {
            int result = problemFeedbackMapper.deleteById(id);
            return Result.success("删除问题反馈成功", result);
        } catch (Exception e) {
            log.error("删除问题反馈失败: {}", e.getMessage(), e);
            return Result.error("删除问题反馈失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "DELETE", module = "PROBLEM_FEEDBACK", description = "批量删除问题反馈", riskLevel = "HIGH", critical = true)
    public Result batchDeleteFeedback(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        log.info("批量删除问题反馈，IDs: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("问题反馈ID列表不能为空");
            }
            int result = problemFeedbackMapper.batchDeleteByIds(ids);
            return Result.success("批量删除问题反馈成功", result);
        } catch (Exception e) {
            log.error("批量删除问题反馈失败: {}", e.getMessage(), e);
            return Result.error("批量删除问题反馈失败: " + e.getMessage());
        }
    }

    @GetMapping("/my-feedback")
    @PreAuthorize("hasAnyRole('ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_COMPANY')")
    public Result getMyFeedback(
            @RequestParam String userType,
            @RequestParam Long userId) {
        log.info("获取我的问题反馈，用户类型: {}, 用户ID: {}", userType, userId);
        try {
            String actorType = currentFeedbackType();
            Long actorId = CurrentHolder.getUserId();
            if (actorType == null || actorId == null) return Result.error("当前用户身份不可用");
            List<ProblemFeedback> feedbackList = problemFeedbackMapper.findByOwner(actorType, actorId);
            
            List<ProblemFeedbackVO> feedbackVOList = feedbackList.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
            
            return Result.success(feedbackVOList);
        } catch (Exception e) {
            log.error("获取我的问题反馈失败: {}", e.getMessage(), e);
            return Result.error("获取我的问题反馈失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "AUDIT", module = "PROBLEM_FEEDBACK", description = "处理问题反馈", riskLevel = "MEDIUM")
    public Result updateStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        log.info("更新问题反馈状态: ID={}, 状态={}", id, request.get("status"));
        try {
            String status = request.get("status");
            int result = problemFeedbackMapper.updateStatus(id, status);
            return Result.success("更新状态成功", result);
        } catch (Exception e) {
            log.error("更新问题反馈状态失败: {}", e.getMessage(), e);
            return Result.error("更新问题反馈状态失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}/reply")
    @Log(operationType = "UPDATE", module = "PROBLEM_FEEDBACK", description = "回复问题反馈")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result replyFeedback(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        log.info("回复问题反馈: ID={}", id);
        try {
            String adminReply = (String) request.get("adminReply");
            Long adminId = ((Number) request.get("adminId")).longValue();
            String adminName = (String) request.get("adminName");
            String status = (String) request.get("status");

            int result = problemFeedbackMapper.reply(id, adminReply, adminId, adminName, status);
            return Result.success("回复成功", result);
        } catch (Exception e) {
            log.error("回复问题反馈失败: {}", e.getMessage(), e);
            return Result.error("回复问题反馈失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getStatistics() {
        log.info("获取问题反馈统计数据");
        try {
            Map<String, Object> statistics = problemFeedbackMapper.getStatistics();
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取问题反馈统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取问题反馈统计数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/processing-count")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getProcessingCount() {
        log.info("获取处理中的问题反馈数量");
        try {
            Long count = problemFeedbackMapper.countByStatus("processing");
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取处理中的问题反馈数量失败: {}", e.getMessage(), e);
            return Result.error("获取处理中的问题反馈数量失败: " + e.getMessage());
        }
    }

    @GetMapping("/recent")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getRecentFeedback() {
        log.info("获取最近的问题反馈");
        try {
            List<ProblemFeedback> feedbackList = problemFeedbackMapper.findRecent(5);
            return Result.success(feedbackList);
        } catch (Exception e) {
            log.error("获取最近的问题反馈失败: {}", e.getMessage(), e);
            return Result.error("获取最近的问题反馈失败: " + e.getMessage());
        }
    }

    /**
     * 导出问题反馈到 Excel
     */
    @Log(operationType = "EXPORT", module = "PROBLEM_FEEDBACK", description = "导出问题反馈", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void exportFeedbackData(HttpServletResponse response,
            @RequestParam(required = false) String userType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String feedbackType,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String title) throws IOException {
        Map<String,String> types = Map.of("system","系统问题","feature","功能建议","bug","程序错误","data","数据问题","other","其他");
        List<Object[]> rows = problemFeedbackMapper.list(userType,status,priority,feedbackType,userName,title).stream()
                .map(r -> new Object[]{r.getTitle(),getStatusText(r.getStatus()),r.getUserName(),r.getUserAccount(),
                        getUserTypeText(r.getUserType()),types.getOrDefault(ExcelExports.text(r.getFeedbackType()),"未知类型"),
                        getPriorityText(r.getPriority()),r.getCreateTime(),r.getContent(),r.getAdminReply()}).toList();
        ExcelExports.write(response,"问题反馈",new String[]{"标题","状态","用户姓名","用户账号","用户类型","反馈类型","优先级","提交时间","反馈内容","回复内容"},rows);
    }

    private String getUserTypeText(String userType) {
        if (userType == null) return "";
        switch (userType) {
            case "student": return "学生";
            case "teacher": return "教师";
            case "company": return "企业";
            default: return userType;
        }
    }

    private String getPriorityText(String priority) {
        if (priority == null) return "";
        switch (priority) {
            case "low": return "低";
            case "normal": return "中";
            case "high": return "高";
            default: return priority;
        }
    }

    private String getStatusText(String status) {
        if (status == null) return "";
        switch (status) {
            case "pending": return "待处理";
            case "processing": return "处理中";
            case "resolved": return "已解决";
            case "closed": return "感谢您的反馈";
            default: return status;
        }
    }

    private String currentFeedbackType() {
        String role = CurrentHolder.getUserRole();
        if (role == null) return null;
        return switch (role) {
            case "ROLE_STUDENT" -> "student";
            case "ROLE_COMPANY" -> "company";
            case "ROLE_TEACHER", "ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR" -> "teacher";
            default -> null;
        };
    }

    private boolean ownsFeedback(ProblemFeedback feedback) {
        Long id = CurrentHolder.getUserId();
        String type = currentFeedbackType();
        return id != null && type != null && id.equals(feedback.getUserId())
                && type.equals(feedback.getUserType()) && !Integer.valueOf(1).equals(feedback.getDeleted());
    }

    private boolean isAdmin(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
    
    private ProblemFeedbackVO convertToVO(ProblemFeedback feedback) {
        ProblemFeedbackVO vo = new ProblemFeedbackVO();
        BeanUtils.copyProperties(feedback, vo);
        return vo;
    }
}
