package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.ProblemFeedback;
import com.gdmu.entity.Result;
import com.gdmu.mapper.ProblemFeedbackMapper;
import com.gdmu.vo.ProblemFeedbackVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/problem-feedback")
public class ProblemFeedbackController {

    @Autowired
    private ProblemFeedbackMapper problemFeedbackMapper;

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
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public Result getFeedbackById(@PathVariable Long id, Authentication authentication) {
        log.info("根据ID获取问题反馈: {}", id);
        try {
            ProblemFeedback feedback = problemFeedbackMapper.findById(id);
            if (feedback == null) {
                return Result.error("问题反馈不存在");
            }
            
            if (isAdmin(authentication)) {
                return Result.success(feedback);
            } else {
                ProblemFeedbackVO feedbackVO = convertToVO(feedback);
                return Result.success(feedbackVO);
            }
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
            int offset = (page - 1) * pageSize;
            List<ProblemFeedback> feedbackList = problemFeedbackMapper.findPageWithConditions(
                offset, pageSize, userType, status, priority, feedbackType, userName, title);
            Long total = problemFeedbackMapper.countWithConditions(
                userType, status, priority, feedbackType, userName, title);

            Map<String, Object> result = new HashMap<>();
            result.put("list", feedbackList);
            result.put("total", total);
            result.put("page", page);
            result.put("pageSize", pageSize);

            return Result.success(result);
        } catch (Exception e) {
            log.error("分页查询问题反馈失败: {}", e.getMessage(), e);
            return Result.error("分页查询问题反馈失败: " + e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_COMPANY')")
    public Result addFeedback(@RequestBody ProblemFeedback feedback) {
        log.info("新增问题反馈: {}", feedback.getTitle());
        try {
            String userType = feedback.getUserType();
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
            return Result.success("提交问题反馈成功", result);
        } catch (Exception e) {
            log.error("提交问题反馈失败: {}", e.getMessage(), e);
            return Result.error("提交问题反馈失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_COMPANY')")
    public Result updateFeedback(@PathVariable Long id, @RequestBody ProblemFeedback feedback) {
        log.info("更新问题反馈: ID={}", id);
        try {
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
    @PreAuthorize("hasAnyRole('ROLE_STUDENT', 'ROLE_TEACHER', 'ROLE_COMPANY')")
    public Result getMyFeedback(
            @RequestParam String userType,
            @RequestParam Long userId) {
        log.info("获取我的问题反馈，用户类型: {}, 用户ID: {}", userType, userId);
        try {
            List<ProblemFeedback> feedbackList = problemFeedbackMapper.list(
                userType, null, null, null, null, null);
            
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
