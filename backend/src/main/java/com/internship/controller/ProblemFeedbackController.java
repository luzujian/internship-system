package com.internship.controller;

import com.internship.utils.ExcelExports;

import com.internship.anno.Log;
import com.internship.entity.PageResult;
import com.internship.entity.ProblemFeedback;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.entity.CompanyUser;
import com.internship.service.ProblemFeedbackSubmittedEvent;
import com.internship.service.ProblemFeedbackEmailReplyService;
import com.internship.entity.dto.ProblemFeedbackEmailReplyModels;
import com.internship.exception.BusinessException;
import jakarta.validation.Valid;
import org.springframework.context.ApplicationEventPublisher;
import com.internship.entity.Result;
import com.internship.mapper.ProblemFeedbackMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.mapper.CompanyUserMapper;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/problem-feedback")
public class ProblemFeedbackController {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ProblemFeedbackMapper problemFeedbackMapper;

    @Autowired
    private ProblemFeedbackEmailReplyService emailReplies;

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private CompanyUserMapper companyUserMapper;

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
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String title) {
        log.info("分页查询问题反馈，页码: {}, 每页大小: {}", page, pageSize);
        try {
            // 使用 PageHelper 进行分页查询
            PageHelper.startPage(page, pageSize);
            List<ProblemFeedback> feedbackList = problemFeedbackMapper.list(
                userType, status, null, null, userName, title);

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
            String email = currentFeedbackEmail(userType, actor.getId());
            if (email == null || !EMAIL_PATTERN.matcher(email.trim()).matches()) {
                return Result.error("请先在个人资料中填写有效邮箱，再提交问题反馈");
            }
            feedback.setId(null);
            feedback.setUserType(userType);
            feedback.setUserId(actor.getId());
            feedback.setUserName(actor.getName());
            feedback.setUserAccount(actor.getUsername());
            // 兼容保留历史字段，处理顺序不再按提交者身份区分。
            feedback.setPriority("normal");
            feedback.setFeedbackType(null);
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
            if (feedback.getStatus() != null) {
                String status = normalizeStatus(feedback.getStatus());
                if (status == null) return Result.error("请选择待处理或已处理");
                feedback.setStatus(status);
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
    @PreAuthorize("hasRole('ROLE_ADMIN') and hasAuthority('problem:edit')")
    @Log(operationType = "AUDIT", module = "PROBLEM_FEEDBACK", description = "处理问题反馈", riskLevel = "MEDIUM")
    public Result updateStatus(@PathVariable Long id, @RequestBody Map<String, String> request) {
        log.info("更新问题反馈状态: ID={}, 状态={}", id, request.get("status"));
        try {
            String status = normalizeStatus(request.get("status"));
            if (status == null) return Result.error("请选择待处理或已处理");
            int result = problemFeedbackMapper.updateStatus(id, status);
            if (result == 0) return Result.error("反馈不存在或已删除");
            return Result.success("更新状态成功", result);
        } catch (Exception e) {
            log.error("更新问题反馈状态失败: {}", e.getMessage(), e);
            return Result.error("更新问题反馈状态失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/email-reply")
    @PreAuthorize("hasRole('ROLE_ADMIN') and hasAnyAuthority('problem:view', 'problem:edit')")
    public Result getEmailReplyContext(@PathVariable Long id) {
        try {
            return Result.success(emailReplies.context(id));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("读取邮件回复记录失败: feedbackId={}", id, e);
            return Result.error("暂时无法读取邮件回复信息，请稍后重试");
        }
    }

    @PutMapping("/{id}/reply")
    @Log(operationType = "UPDATE", module = "PROBLEM_FEEDBACK", description = "邮件回复问题反馈")
    @PreAuthorize("hasRole('ROLE_ADMIN') and hasAuthority('problem:edit')")
    public Result replyFeedback(@PathVariable Long id, @Valid @RequestBody ProblemFeedbackEmailReplyModels.Request request) {
        try {
            return Result.success("邮件发送结果已记录", emailReplies.send(id, request));
        } catch (BusinessException e) {
            return Result.error(e.getMessage());
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("邮件回复请求未完成: feedbackId={}", id, e);
            return Result.error("邮件回复请求未完成，请重新检查发送记录后再操作");
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
        log.info("获取待处理的问题反馈数量");
        try {
            Long count = problemFeedbackMapper.countByStatus("processing");
            return Result.success(count);
        } catch (Exception e) {
            log.error("获取待处理的问题反馈数量失败: {}", e.getMessage(), e);
            return Result.error("获取待处理的问题反馈数量失败: " + e.getMessage());
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
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) String title) throws IOException {
        List<Object[]> rows = problemFeedbackMapper.list(userType,status,null,null,userName,title).stream()
                .map(r -> new Object[]{r.getTitle(),getStatusText(r.getStatus()),r.getUserName(),r.getUserAccount(),
                        getUserTypeText(r.getUserType()),r.getCreateTime(),r.getContent(),r.getAdminReply()}).toList();
        ExcelExports.write(response,"问题反馈",new String[]{"标题","处理状态","用户姓名","用户账号","用户类型","提交时间","反馈内容","最近回复内容"},rows);
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

    private String normalizeStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case "pending", "processing" -> "processing";
            case "resolved", "closed" -> "resolved";
            default -> null;
        };
    }

    private String getStatusText(String status) {
        if (status == null) return "";
        switch (status) {
            case "pending":
            case "processing": return "待处理";
            case "resolved":
            case "closed": return "已处理";
            default: return "未知状态";
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

    private String currentFeedbackEmail(String userType, Long userId) {
        return switch (userType) {
            case "student" -> {
                StudentUser student = studentUserMapper.findById(userId);
                yield student == null ? null : student.getEmail();
            }
            case "teacher" -> {
                TeacherUser teacher = teacherUserMapper.findById(userId);
                yield teacher == null ? null : teacher.getEmail();
            }
            case "company" -> {
                CompanyUser company = companyUserMapper.findById(userId);
                yield company == null ? null : company.getEmail();
            }
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
