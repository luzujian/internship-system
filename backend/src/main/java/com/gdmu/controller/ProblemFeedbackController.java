package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.ProblemFeedback;
import com.gdmu.entity.Result;
import com.gdmu.mapper.ProblemFeedbackMapper;
import com.gdmu.vo.ProblemFeedbackVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
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
    private ProblemFeedbackMapper problemFeedbackMapper;

    @Autowired
    private com.gdmu.websocket.AnnouncementWebSocketHandler webSocketHandler;

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
            
            if (result > 0) {
                pushFeedbackNotification(feedback);
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
    @Log(operationType = "EXPORT", module = "PROBLEM_FEEDBACK", description = "导出问题反馈")
    @GetMapping("/export")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void exportFeedbackData(HttpServletResponse response) {
        log.info("导出问题反馈 Excel 数据");
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("问题反馈" + System.currentTimeMillis(), StandardCharsets.UTF_8) + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);

            List<ProblemFeedback> feedbackList = problemFeedbackMapper.findAll();

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("问题反馈");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"反馈标题", "反馈内容", "用户类型", "用户姓名", "用户账号", "问题类型", "优先级", "状态", "反馈时间", "回复内容"};
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.autoSizeColumn(i);
            }

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (int i = 0; i < feedbackList.size(); i++) {
                ProblemFeedback feedback = feedbackList.get(i);
                Row dataRow = sheet.createRow(i + 1);

                Cell titleCell = dataRow.createCell(0);
                titleCell.setCellValue(feedback.getTitle() != null ? feedback.getTitle() : "");
                titleCell.setCellStyle(dataStyle);

                Cell contentCell = dataRow.createCell(1);
                contentCell.setCellValue(feedback.getContent() != null ? feedback.getContent() : "");
                contentCell.setCellStyle(dataStyle);

                Cell userTypeCell = dataRow.createCell(2);
                userTypeCell.setCellValue(getUserTypeText(feedback.getUserType()));
                userTypeCell.setCellStyle(dataStyle);

                Cell userNameCell = dataRow.createCell(3);
                userNameCell.setCellValue(feedback.getUserName() != null ? feedback.getUserName() : "");
                userNameCell.setCellStyle(dataStyle);

                Cell userAccountCell = dataRow.createCell(4);
                userAccountCell.setCellValue(feedback.getUserAccount() != null ? feedback.getUserAccount() : "");
                userAccountCell.setCellStyle(dataStyle);

                Cell feedbackTypeCell = dataRow.createCell(5);
                feedbackTypeCell.setCellValue(feedback.getFeedbackType() != null ? feedback.getFeedbackType() : "");
                feedbackTypeCell.setCellStyle(dataStyle);

                Cell priorityCell = dataRow.createCell(6);
                priorityCell.setCellValue(getPriorityText(feedback.getPriority()));
                priorityCell.setCellStyle(dataStyle);

                Cell statusCell = dataRow.createCell(7);
                statusCell.setCellValue(getStatusText(feedback.getStatus()));
                statusCell.setCellStyle(dataStyle);

                Cell createTimeCell = dataRow.createCell(8);
                createTimeCell.setCellValue(feedback.getCreateTime() != null ? sdf.format(feedback.getCreateTime()) : "");
                createTimeCell.setCellStyle(dataStyle);

                Cell replyCell = dataRow.createCell(9);
                replyCell.setCellValue(feedback.getAdminReply() != null ? feedback.getAdminReply() : "");
                replyCell.setCellStyle(dataStyle);
            }

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("导出问题反馈 Excel 数据成功，共导出 {} 条数据", feedbackList.size());
            } catch (IOException e) {
                log.error("导出问题反馈 Excel 数据失败（流操作异常）: {}", e.getMessage(), e);
                throw new RuntimeException("导出问题反馈 Excel 数据失败：" + e.getMessage());
            } finally {
                try {
                    if (workbook != null) {
                        workbook.close();
                    }
                } catch (IOException e) {
                    log.error("关闭工作簿失败：{}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("导出问题反馈数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("导出问题反馈数据失败：" + e.getMessage());
        }
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
            case "normal": return "普通";
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
            case "closed": return "已关闭";
            default: return status;
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
