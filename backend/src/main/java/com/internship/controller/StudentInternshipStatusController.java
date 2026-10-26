package com.internship.controller;

import com.internship.utils.ExcelExports;

import com.internship.entity.Result;
import com.internship.entity.StudentInternshipStatus;
import com.internship.entity.PageResult;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.service.StudentInternshipStatusService;
import com.internship.service.TeacherAutomationScopeService;
import com.internship.service.TeacherUserService;
import com.internship.service.UserService;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipConfirmationWorkflowService;
import com.internship.service.StudentJobApplicationService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import com.internship.anno.Log;
import com.internship.exception.BusinessException;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * 学生实习状态控制器
 * 处理实习状态相关的所有接口请求
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/internship-status")
public class StudentInternshipStatusController {

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private TeacherAutomationScopeMapper teacherScopeMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private InternshipConfirmationRecordService confirmationRecordService;

    @Autowired
    private StudentJobApplicationService studentJobApplicationService;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private InternshipConfirmationWorkflowService confirmationWorkflowService;

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

            // 待确认数以正式提交的确认记录为准；pending 不再覆盖当前实习状态投影。
            long pendingConfirmations = confirmationRecordService
                    .findByCompanyIdAndStatus(companyId, 0).size();

            webSocketHandler.sendCompanyTodoUpdate(companyId, pendingApplications, pendingConfirmations);
        } catch (Exception e) {
            log.error("推送待办数据失败: {}", e.getMessage());
        }
    }

    /**
     * 获取实习状态列表（分页）
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result getInternshipStatusList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String className) {
        log.info("分页获取实习状态列表，页码：{}, 每页条数：{}, 学生 ID: {}, 学生姓名：{}, 性别：{}, 状态：{}, 企业 ID: {}, 企业名称：{}, 年级：{}, 专业：{}, 班级：{}",
                page, pageSize, studentId, name, gender, status, companyId, companyName, grade, major, className);
        PageResult<StudentInternshipStatus> pageResult = studentInternshipStatusService.findPage(page, pageSize, studentId, name, gender, status, companyId, companyName, grade, major, className);
        return Result.success(pageResult);
    }

    /**
     * 获取实习状态详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getInternshipStatusById(@PathVariable Long id) {
        log.info("获取实习状态详情，ID: {}", id);
        StudentInternshipStatus status = studentInternshipStatusService.findById(id);
        if (status == null) {
            return Result.error("实习状态不存在");
        }
        log.info("实习状态详情数据：internshipStartTime={}, internshipEndTime={}, internshipDuration={}",
            status.getInternshipStartTime(), status.getInternshipEndTime(), status.getInternshipDuration());
        return Result.success(status);
    }

    /**
     * 添加实习状态
     */
    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "CREATE", module = "STUDENT_INTERNSHIP", description = "新增实习状态", riskLevel = "LOW")
    public Result addInternshipStatus(@RequestBody StudentInternshipStatus status) {
        log.info("添加学生实习状态，学生 ID: {}", status.getStudentId());
        int result = studentInternshipStatusService.insert(status);
        if (result > 0) {
            return Result.success("添加成功");
        }
        return Result.error("添加失败");
    }

    /**
     * 更新实习状态
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "UPDATE", module = "STUDENT_INTERNSHIP", description = "更新实习状态", riskLevel = "MEDIUM")
    public Result updateInternshipStatus(@PathVariable Long id, @RequestBody StudentInternshipStatus status) {
        log.info("更新学生实习状态，ID: {}", id);
        status.setId(id);
        int result = studentInternshipStatusService.update(status);
        if (result > 0) {
            return Result.success("更新成功");
        }
        return Result.error("更新失败");
    }

    /**
     * 删除实习状态
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "DELETE", module = "STUDENT_INTERNSHIP", description = "删除实习状态", riskLevel = "MEDIUM")
    public Result deleteInternshipStatus(@PathVariable Long id) {
        log.info("删除学生实习状态，ID: {}", id);
        int result = studentInternshipStatusService.delete(id);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }

    /**
     * 批量删除实习状态
     */
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "DELETE", module = "STUDENT_INTERNSHIP", description = "批量删除实习状态", riskLevel = "HIGH", critical = true)
    public Result batchDeleteInternshipStatus(@RequestBody List<Long> ids) {
        log.info("批量删除学生实习状态，IDs: {}", ids);
        int result = studentInternshipStatusService.batchDelete(ids);
        if (result > 0) {
            return Result.success("批量删除成功");
        }
        return Result.error("批量删除失败");
    }

    /**
     * 导出实习状态数据（Excel格式）
     */
    @Log(operationType = "EXPORT", module = "STUDENT_INTERNSHIP", description = "导出实习状态数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    @PreAuthorize("hasRole('ADMIN')")
    public void exportInternshipStatus(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName,
            HttpServletResponse response) throws IOException {
        log.info("导出实习状态数据，学生 ID: {}, 学生姓名：{}, 性别：{}, 状态：{}, 企业 ID: {}, 企业名称：{}",
                studentId, name, gender, status, companyId, companyName);

        List<StudentInternshipStatus> statusList = studentInternshipStatusService.list(studentId, name, gender, status, companyId, companyName, null, null, null, null);

        ExcelExports.internships(response, "学生实习状态", statusList);
    }

    /**
     * 批量导出实习状态数据（Excel格式）
     */
    @Log(operationType = "EXPORT", module = "STUDENT_INTERNSHIP", description = "批量导出实习状态数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public void exportBatchInternshipStatus(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Integer status,
            HttpServletResponse response) throws IOException {
        log.info("批量导出实习状态数据，导出范围: {}, 班级: {}, 年级: {}, 状态: {}",
                scope, className, grade, status);

        List<StudentInternshipStatus> statusList = studentInternshipStatusService.list(null, null, null, status, null, null, grade, null, className, null);

        ExcelExports.internships(response, "学生实习状态", statusList);
    }

    private String getGenderText(Integer gender) {
        if (gender == null) return "";
        return gender == 1 ? "男" : (gender == 2 ? "女" : "未知");
    }

    /**
     * 下载实习确认申请表（Word 表格格式）
     */
    @Log(operationType = "DOWNLOAD", module = "STUDENT_INTERNSHIP", description = "下载实习申请表", riskLevel = "MEDIUM")
    @GetMapping("/download-application/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public void downloadInternshipApplication(@PathVariable Long id, HttpServletResponse response) throws IOException {
        log.info("下载实习确认申请表，ID: {}", id);
        StudentInternshipStatus status = studentInternshipStatusService.findById(id);
        if (status == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        requireInternshipApplicationAccess(status);

        XWPFDocument document = new XWPFDocument();

        // 标题
        XWPFParagraph titleParagraph = document.createParagraph();
        titleParagraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun titleRun = titleParagraph.createRun();
        titleRun.setBold(true);
        titleRun.setFontSize(18);
        titleRun.setFontFamily("SimSun");
        titleRun.setText("实习确认申请表");

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // 创建表格 - 14 行 2 列
        XWPFTable table = document.createTable(14, 2);
        table.setWidth("100%");

        // 填充表格数据
        setTableRow(table, 0, "学号", status.getStudent() != null ? status.getStudent().getStudentUserId() : "");
        setTableRow(table, 1, "姓名", status.getStudent() != null ? status.getStudent().getName() : "");
        setTableRow(table, 2, "性别", status.getStudent() != null ? ExcelExports.gender(status.getStudent().getGender()) : "");
        setTableRow(table, 3, "企业名称", ExcelExports.company(status));
        setTableRow(table, 4, "岗位名称", ExcelExports.position(status));
        setTableRow(table, 5, "实习状态", exportStatusText(status));
        setTableRow(table, 6, "企业确认状态", getCompanyConfirmStatusText(status.getCompanyConfirmStatus()));
        setTableRow(table, 7, "实习开始时间", status.getInternshipStartTime() != null ? dateFormat.format(status.getInternshipStartTime()) : "");
        setTableRow(table, 8, "实习结束时间", status.getInternshipEndTime() != null ? dateFormat.format(status.getInternshipEndTime()) : "");
        setTableRow(table, 9, "实习时长 (月)", status.getInternshipDuration() != null ? String.valueOf(status.getInternshipDuration()) : "");
        setTableRow(table, 10, "创建时间", status.getCreateTime() != null ? dateFormat.format(status.getCreateTime()) : "");
        setTableRow(table, 11, "更新时间", status.getUpdateTime() != null ? dateFormat.format(status.getUpdateTime()) : "");
        setTableRow(table, 12, "反馈信息", status.getFeedback() != null ? status.getFeedback() : "无");
        setTableRow(table, 13, "备注", status.getRemark() != null ? status.getRemark() : "无");

        String fileName = (status.getStudent() != null ? status.getStudent().getStudentUserId() : "") + "_" +
                        (status.getStudent() != null ? status.getStudent().getName() : "") + "_实习确认申请表.docx";
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");

        try (OutputStream outputStream = response.getOutputStream()) {
            document.write(outputStream);
        }

        document.close();

        log.info("实习确认申请表下载成功，ID: {}", id);
    }

    private void requireInternshipApplicationAccess(StudentInternshipStatus status) {
        User actor = CurrentHolder.getUser();
        if (actor == null || actor.getId() == null || actor.getRole() == null) {
            throw new AccessDeniedException("登录身份不可用");
        }
        if ("ROLE_ADMIN".equals(actor.getRole())) return;
        TeacherUser teacher;
        try {
            teacher = teacherUserService.findById(actor.getId());
            TeacherAutomationScopeService.requireTeacher(teacher);
        } catch (Exception e) {
            throw new AccessDeniedException("教师身份或管辖范围不可用", e);
        }
        if (actor.getUsername() == null || !actor.getUsername().equals(teacher.getTeacherUserId())
                || !("ROLE_TEACHER_" + teacher.getTeacherType()).equals(actor.getRole())) {
            throw new AccessDeniedException("教师身份不匹配");
        }
        try {
            if (status.getStudentId() != null && teacherScopeMapper.contains(teacher, status.getStudentId()) > 0) return;
        } catch (Exception e) {
            throw new AccessDeniedException("教师管辖范围不可用", e);
        }
        throw new AccessDeniedException("无权下载该学生的实习申请表");
    }

    private void setTableRow(XWPFTable table, int rowNum, String label, String value) {
        XWPFTableRow row = table.getRow(rowNum);

        // 第一列：标签
        XWPFTableCell labelCell = row.getCell(0);
        labelCell.removeParagraph(0);
        XWPFParagraph labelParagraph = labelCell.addParagraph();
        XWPFRun labelRun = labelParagraph.createRun();
        labelRun.setBold(true);
        labelRun.setFontSize(12);
        labelRun.setFontFamily("SimSun");
        labelRun.setText(label);

        // 第二列：值
        XWPFTableCell valueCell = row.getCell(1);
        valueCell.removeParagraph(0);
        XWPFParagraph valueParagraph = valueCell.addParagraph();
        XWPFRun valueRun = valueParagraph.createRun();
        valueRun.setFontSize(12);
        valueRun.setFontFamily("SimSun");
        valueRun.setText(value != null ? value : "");
    }

    private String getStatusText(Integer status) {
        if (status == null) return "状态待核实";
        switch (status) {
            case 0: return "尚未获录用";
            case 1: return "已录用待确认";
            case 2: return "已定单位待实习";
            case 3: return "实习进行中";
            case 4: return "实习已结束";
            case 5: return "实习已中断";
            case 6: return "已延期实习";
            default: return "状态待核实";
        }
    }

    private String exportStatusText(StudentInternshipStatus status) {
        String text = status.getJobSearchStatusText();
        if (text == null || text.isBlank()) text = getStatusText(status.getStatus());
        if (status.getPendingApplicationTexts() != null && !status.getPendingApplicationTexts().isEmpty()) {
            text += " " + String.join(" ", status.getPendingApplicationTexts());
        }
        return text;
    }

    private String getCompanyConfirmStatusText(Integer status) {
        if (status == null) return "未确认";
        switch (status) {
            case 0: return "未确认";
            case 1: return "已确认";
            case 2: return "已拒绝";
            default: return "未确认";
        }
    }

    /**
     * 根据学生 ID 获取实习状态
     */
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getInternshipStatusByStudentId(@PathVariable Long studentId) {
        log.info("根据学生 ID 获取实习状态，学生 ID: {}", studentId);
        StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(studentId);
        return Result.success(status);
    }

    /**
     * 根据学生 ID 删除实习状态（解绑）
     */
    @DeleteMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "DELETE", module = "STUDENT_INTERNSHIP", description = "按学生删除实习状态", riskLevel = "MEDIUM")
    public Result deleteInternshipStatusByStudentId(@PathVariable Long studentId) {
        log.info("根据学生 ID 删除实习状态，学生 ID: {}", studentId);
        StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(studentId);
        if (status == null) {
            return Result.error("该学生未绑定实习状态");
        }
        int result = studentInternshipStatusService.delete(status.getId());
        if (result > 0) {
            return Result.success("解绑成功");
        }
        return Result.error("解绑失败");
    }

    /**
     * 批量解绑学生实习状态
     */
    @DeleteMapping("/student/batch")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "DELETE", module = "STUDENT_INTERNSHIP", description = "批量按学生删除实习状态", riskLevel = "HIGH", critical = true)
    public Result batchDeleteInternshipStatusByStudentIds(@RequestBody List<Long> studentIds) {
        log.info("批量解绑学生实习状态，学生 IDs: {}", studentIds);
        int successCount = 0;
        int failCount = 0;

        for (Long studentId : studentIds) {
            StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(studentId);
            if (status != null) {
                int result = studentInternshipStatusService.delete(status.getId());
                if (result > 0) {
                    successCount++;
                } else {
                    failCount++;
                }
            } else {
                failCount++;
            }
        }

        if (successCount > 0) {
            return Result.success(String.format("批量解绑成功 %d/%d 人", successCount, studentIds.size()));
        }
        return Result.error("批量解绑失败");
    }

    /**
     * 绑定或更新学生实习状态
     * 如果学生已有实习状态记录则更新，否则插入新记录
     */
    @PostMapping("/bind")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    @Log(operationType = "UPDATE", module = "STUDENT_INTERNSHIP", description = "绑定或更新实习状态", riskLevel = "MEDIUM")
    public Result bindOrUpdateInternshipStatus(@RequestBody StudentInternshipStatus status) {
        log.info("绑定或更新学生实习状态，学生 ID: {}, 企业 ID: {}, 岗位 ID: {}",
                status.getStudentId(), status.getCompanyId(), status.getPositionId());

        StudentInternshipStatus existingStatus = studentInternshipStatusService.findByStudentId(status.getStudentId());

        if (existingStatus != null) {
            existingStatus.setStatus(status.getStatus());
            existingStatus.setCompanyId(status.getCompanyId());
            existingStatus.setPositionId(status.getPositionId());
            int result = studentInternshipStatusService.update(existingStatus);
            if (result > 0) {
                return Result.success("更新成功");
            }
            return Result.error("更新失败");
        } else {
            int result = studentInternshipStatusService.insert(status);
            if (result > 0) {
                return Result.success("绑定成功");
            }
            return Result.error("绑定失败");
        }
    }

    @GetMapping("/recall/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getPendingRecallAuditInternships(@RequestParam(defaultValue = "1") Integer page,
                                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                                     @RequestParam(required = false) Long studentId,
                                                     @RequestParam(required = false) String name,
                                                     @RequestParam(required = false) Integer gender,
                                                     @RequestParam(required = false) Long companyId,
                                                     @RequestParam(required = false) String companyName) {
        log.info("获取待审核撤回申请列表，页码：{}, 每页条数：{}, 学生 ID: {}, 学生姓名：{}, 性别：{}, 企业 ID: {}, 企业名称：{}",
                page, pageSize, studentId, name, gender, companyId, companyName);
        try {
            PageResult<StudentInternshipStatus> pageResult = studentInternshipStatusService
                .findPendingRecallAuditPage(page, pageSize, studentId, name, gender, companyId, companyName);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取待审核撤回申请列表失败：{}", e.getMessage(), e);
            return Result.error("获取待审核撤回申请列表失败");
        }
    }

    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "审核实习确认撤回申请")
    @PutMapping("/{id}/recall-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Result auditRecallApplication(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核实习确认撤回申请：id={}", id);
        try {
            Integer recallStatus = Integer.valueOf(params.get("recallStatus").toString());
            String recallAuditRemark = params.get("recallAuditRemark") != null ?
                params.get("recallAuditRemark").toString() : "";

            StudentInternshipStatus status = studentInternshipStatusService.findById(id);
            if (status == null) {
                return Result.error("实习状态不存在");
            }

            int result = studentInternshipStatusService.auditRecallApplication(
                id, recallStatus, recallAuditRemark, getCurrentUserId());

            if (result > 0) {
                if (recallStatus == 2) {
                    return Result.success("撤回申请已批准");
                } else if (recallStatus == 3) {
                    return Result.success("撤回申请已拒绝");
                } else {
                    return Result.error("无效的撤回审核状态");
                }
            } else {
                return Result.error("审核失败");
            }
        } catch (BusinessException e) {
            log.warn("审核撤回申请失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核撤回申请时发生异常：{}", e.getMessage(), e);
            return Result.error("审核失败：" + e.getMessage());
        }
    }

    @GetMapping("/recall/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getRecallStatistics() {
        log.info("获取撤回申请统计数据");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("pending", studentInternshipStatusService.countByRecallStatus(1));
            statistics.put("approved", studentInternshipStatusService.countByRecallStatus(2));
            statistics.put("rejected", studentInternshipStatusService.countByRecallStatus(3));
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取撤回申请统计数据失败：{}", e.getMessage(), e);
            return Result.error("获取撤回申请统计数据失败");
        }
    }

    /**
     * 导出学生撤回申请记录到 Excel
     */
    @Log(operationType = "EXPORT", module = "STUDENT_INTERNSHIP", description = "导出学生撤回申请记录", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/recall/export")
    @PreAuthorize("hasRole('ADMIN')")
    public void exportRecallRecords(HttpServletResponse response,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName) throws IOException {
        List<Object[]> rows = studentInternshipStatusService.findPendingRecallAuditList(studentId,name,gender,companyId,companyName)
                .stream().map(r -> new Object[]{r.getStudent()==null?"":r.getStudent().getStudentUserId(),
                        ExcelExports.first(r.getStudent()==null?null:r.getStudent().getName(),r.getStudentName()),
                        r.getStudent()==null?ExcelExports.genderText(r.getGender()):ExcelExports.gender(r.getStudent().getGender()),
                        r.getStudent()==null?ExcelExports.text(r.getGrade()):(r.getStudent().getGrade()==null?"":r.getStudent().getGrade()+"级"),
                        ExcelExports.first(r.getMajor()==null?null:r.getMajor().getName(),r.getMajorName()),
                        ExcelExports.first(r.getCls()==null?null:r.getCls().getName(),r.getClassName()),ExcelExports.status(r),
                        ExcelExports.company(r),ExcelExports.position(r),r.getContactPhone(),r.getEmail(),
                        r.getInternshipStartTime(),r.getInternshipEndTime(),r.getRecallReason(),r.getRecallApplyTime()}).toList();
        ExcelExports.write(response,"学生撤回申请记录",new String[]{"学号","姓名","性别","年级","专业","班级","实习状态","企业名称","岗位名称","联系电话","邮箱","实习开始时间","实习结束时间","撤回原因","申请时间"},rows);
    }

    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
            String username = ((org.springframework.security.core.userdetails.User) authentication.getPrincipal()).getUsername();
            return userService.findByUsername(username).getId();
        }
        return null;
    }

    @DeleteMapping("/recall/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "DELETE", module = "STUDENT_INTERNSHIP", description = "清除撤回申请数据")
    public Result clearRecallData() {
        log.info("清除学生实习撤回申请数据");
        try {
            int count = studentInternshipStatusService.clearRecallData();
            log.info("清除学生实习撤回申请数据成功，共清除{}条记录", count);
            return Result.success("清除成功，共清除" + count + "条记录");
        } catch (Exception e) {
            log.error("清除学生实习撤回申请数据失败：{}", e.getMessage(), e);
            return Result.error("清除失败");
        }
    }

    /**
     * 根据企业 ID 获取实习状态列表（企业端使用）
     */
    @GetMapping("/company/{companyId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY')")
    public Result getInternshipStatusByCompanyId(@PathVariable Long companyId) {
        log.info("根据企业 ID 获取实习状态列表，企业 ID: {}", companyId);
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null) {
                log.error("未获取到认证信息");
                return Result.error("未获取到认证信息");
            }
            
            boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            
            if (!isAdmin) {
                Long currentCompanyId = CurrentHolder.getUserId();
                if (currentCompanyId == null) {
                    log.error("未获取到当前登录企业 ID");
                    return Result.error("未获取到当前登录企业 ID");
                }
                
                if (!currentCompanyId.equals(companyId)) {
                    log.warn("企业用户尝试访问其他企业的数据，当前企业 ID: {}, 请求企业 ID: {}", currentCompanyId, companyId);
                    throw new org.springframework.security.access.AccessDeniedException("无权访问该企业的数据");
                }
                
                log.info("企业用户身份验证通过，企业 ID: {}", currentCompanyId);
            }
            
            List<StudentInternshipStatus> statusList = studentInternshipStatusService.list(null, null, null, null, companyId, null, null, null, null, null);
            // 过滤掉已撤回的记录（recall_status=2表示已撤回，企业端不应看到）
            statusList = statusList.stream()
                .filter(s -> s.getRecallStatus() == null || s.getRecallStatus() != 2)
                .collect(java.util.stream.Collectors.toList());
            // 企业确认前 remark 尚未同步到本表，从待确认确认记录中补充学生填写的补充说明
            List<InternshipConfirmationRecord> pendingRecords = confirmationRecordService.findByCompanyIdAndStatus(companyId, 0);
            if (!pendingRecords.isEmpty()) {
                for (StudentInternshipStatus status : statusList) {
                    if (status.getRemark() == null || status.getRemark().isBlank()) {
                        pendingRecords.stream()
                                .filter(record -> status.getStudentId().equals(record.getStudentId())
                                        && status.getCompanyId().equals(record.getCompanyId())
                                        && status.getPositionId().equals(record.getPositionId()))
                                .findFirst()
                                .ifPresent(record -> status.setRemark(record.getRemark()));
                    }
                }
            }
            return Result.success(statusList);
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取企业实习状态列表失败：{}", e.getMessage(), e);
            return Result.error("获取实习状态列表失败：" + e.getMessage());
        }
    }
    /**
     * 兼容旧客户端：路径参数仍是旧 student_internship_status.id，实际处理统一委托确认工作流。
     */
    @PutMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY')")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "企业确认实习状态")
    @Transactional(rollbackFor = Exception.class)
    public Result approveInternshipStatus(@PathVariable Long id) {
        log.info("兼容端点确认实习状态，statusId={}", id);
        try {
            StudentInternshipStatus legacyStatus = studentInternshipStatusService.findById(id);
            if (legacyStatus == null) return Result.error("实习状态不存在");
            if (!Integer.valueOf(0).equals(legacyStatus.getCompanyConfirmStatus())) {
                return Result.error("该实习确认不是待处理状态");
            }
            Result authorization = authorizeLegacyConfirmation(legacyStatus);
            if (authorization != null) return authorization;
            InternshipConfirmationRecord confirmationRecord = findPendingConfirmation(legacyStatus);
            if (confirmationRecord == null) return Result.error("未找到待处理的实习确认记录");

            InternshipConfirmationWorkflowService.ConfirmationOutcome outcome =
                    confirmationWorkflowService.confirm(
                            legacyStatus.getCompanyId(), confirmationRecord.getId(), new Date());
            InternshipConfirmationRecord resolved = outcome.record();
            runAfterCommit(() -> {
                pushTodoUpdate(resolved.getCompanyId());
                webSocketHandler.sendConfirmationResultToStudent(
                        resolved.getStudentId(), 1,
                        resolved.getCompanyName(), resolved.getPositionName());
            });
            return Result.success("确认成功");
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("确认实习状态失败：{}", e.getMessage(), e);
            return Result.error("确认失败：" + e.getMessage());
        }
    }
    /** 兼容旧客户端的拒绝入口，状态变更统一委托确认工作流。 */
    @PutMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'COMPANY')")
    @Log(operationType = "UPDATE", module = "INTERNSHIP_MANAGEMENT", description = "企业拒绝实习状态")
    @Transactional(rollbackFor = Exception.class)
    public Result rejectInternshipStatus(@PathVariable Long id,
                                         @RequestBody(required = false) Map<String, String> body) {
        log.info("兼容端点拒绝实习状态，statusId={}", id);
        try {
            StudentInternshipStatus legacyStatus = studentInternshipStatusService.findById(id);
            if (legacyStatus == null) return Result.error("实习状态不存在");
            if (!Integer.valueOf(0).equals(legacyStatus.getCompanyConfirmStatus())) {
                return Result.error("该实习确认不是待处理状态");
            }
            Result authorization = authorizeLegacyConfirmation(legacyStatus);
            if (authorization != null) return authorization;
            InternshipConfirmationRecord confirmationRecord = findPendingConfirmation(legacyStatus);
            if (confirmationRecord == null) return Result.error("未找到待处理的实习确认记录");
            String reason = body == null || body.get("rejectReason") == null
                    ? "" : body.get("rejectReason").trim();

            InternshipConfirmationWorkflowService.ConfirmationOutcome outcome =
                    confirmationWorkflowService.reject(
                            legacyStatus.getCompanyId(), confirmationRecord.getId(), reason);
            InternshipConfirmationRecord resolved = outcome.record();
            runAfterCommit(() -> {
                pushTodoUpdate(resolved.getCompanyId());
                webSocketHandler.sendConfirmationResultToStudent(
                        resolved.getStudentId(), 2,
                        resolved.getCompanyName(), resolved.getPositionName());
            });
            return Result.success("拒绝成功");
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("拒绝实习状态失败：{}", e.getMessage(), e);
            return Result.error("拒绝失败：" + e.getMessage());
        }
    }

    private Result authorizeLegacyConfirmation(StudentInternshipStatus status) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) return Result.error("未获取到认证信息");
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        if (isAdmin) return null;
        Long currentCompanyId = CurrentHolder.getUserId();
        if (currentCompanyId == null) return Result.error("未获取到当前登录企业 ID");
        if (!currentCompanyId.equals(status.getCompanyId())) {
            return Result.error("无权操作该实习状态");
        }
        return null;
    }

    private InternshipConfirmationRecord findPendingConfirmation(StudentInternshipStatus status) {
        return confirmationRecordService.findByCompanyIdAndStatus(status.getCompanyId(), 0).stream()
                .filter(record -> status.getStudentId().equals(record.getStudentId())
                        && status.getCompanyId().equals(record.getCompanyId())
                        && status.getPositionId().equals(record.getPositionId()))
                .findFirst()
                .orElse(null);
    }

    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void rollbackCurrentTransaction() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Direct controller unit tests do not run through a transactional proxy.
        }
    }
}
