package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.User;
import com.gdmu.entity.GroupTable;
import com.gdmu.entity.Assignment;
import com.gdmu.entity.Submission;
import com.gdmu.entity.FinalScore;
import com.gdmu.entity.GroupMember;
import com.gdmu.entity.Class;
import com.gdmu.entity.Course;
import com.gdmu.entity.Result;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.entity.Announcement;
import com.gdmu.exception.BusinessException;
import com.gdmu.service.*;
import com.gdmu.service.MajorService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.TeacherUserService;
import com.gdmu.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师控制器
 * 处理教师相关的所有接口请求，包括实习管理、成绩管理、学生管理等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
@Validated
public class TeacherController {

    @Autowired
    private UserService userService;

    @Autowired
    private ClassService classService;

    @Autowired
    private MajorService majorService;

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementReadRecordService announcementReadRecordService;

    @Autowired
    private StudentUserService studentUserService;

    @GetMapping("/current")
    public Result getCurrentTeacherInfo() {
        log.info("获取当前登录教师信息");
        try {
            Long userId = CurrentHolder.getUserId();
            if (userId == null) {
                log.warn("未获取到当前登录用户ID");
                return Result.error("未登录或登录已过期");
            }
            
            User user = CurrentHolder.getUser();
            if (user == null || user.getUsername() == null) {
                log.warn("未获取到当前登录用户信息");
                return Result.error("未登录或登录已过期");
            }
            
            com.gdmu.entity.TeacherUser teacher = teacherUserService.findByTeacherUserId(user.getUsername());
            if (teacher == null) {
                log.warn("教师信息不存在，username: {}", user.getUsername());
                return Result.error("教师信息不存在");
            }
            return Result.success(teacher);
        } catch (Exception e) {
            log.error("获取当前教师信息失败: {}", e.getMessage(), e);
            return Result.error("获取教师信息失败");
        }
    }

    @GetMapping("/notifications")
    public Result getNotifications(@RequestParam(defaultValue = "5") Integer limit) {
        log.info("获取教师通知：limit={}", limit);
        try {
            Long teacherId = getCurrentUserId();
            if (teacherId == null) {
                log.warn("未找到教师用户ID");
                return Result.error("未找到教师用户信息");
            }
            log.info("当前教师用户ID: {}", teacherId);

            List<Announcement> allAnnouncements = announcementService.findAll(null, "PUBLISHED");

            // 过滤出目标群体包含TEACHER或ALL的公告
            List<Announcement> filteredAnnouncements = allAnnouncements.stream()
                .filter(a -> {
                    String targetType = a.getTargetType();
                    if (targetType == null || targetType.isEmpty()) {
                        return false;
                    }
                    if (targetType.startsWith("[")) {
                        return targetType.contains("\"TEACHER\"") || targetType.contains("\"ALL\"");
                    } else {
                        return "TEACHER".equals(targetType) || "ALL".equals(targetType);
                    }
                })
                .toList();

            // 分别获取未读和已读公告
            List<Announcement> unreadAnnouncements = new java.util.ArrayList<>();
            List<Announcement> readAnnouncements = new java.util.ArrayList<>();

            for (Announcement announcement : filteredAnnouncements) {
                boolean isRead = announcementReadRecordService.findByAnnouncementAndUser(
                        announcement.getId(), String.valueOf(teacherId), "TEACHER") != null;
                if (isRead) {
                    readAnnouncements.add(announcement);
                } else {
                    unreadAnnouncements.add(announcement);
                }
            }

            // 合并：未读优先，未读不足limit时用已读补充
            List<Announcement> resultList = new java.util.ArrayList<>();
            resultList.addAll(unreadAnnouncements);
            int remaining = limit - unreadAnnouncements.size();
            if (remaining > 0 && !readAnnouncements.isEmpty()) {
                resultList.addAll(readAnnouncements.stream().limit(remaining).toList());
            }

            // 如果还是不足limit，补充更多未读（如果全部未读已显示）
            if (resultList.size() < limit && unreadAnnouncements.size() > resultList.size()) {
                // 已有全部未读，不需额外处理
            }

            log.info("查询到的通知数量：未读={}, 已读(补充)={}", unreadAnnouncements.size(), limit - unreadAnnouncements.size());

            // 只返回未读公告
            List<java.util.Map<String, Object>> result = unreadAnnouncements.stream()
                    .map(announcement -> {
                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", announcement.getId());
                        item.put("type", "system");
                        item.put("title", announcement.getTitle());
                        item.put("content", announcement.getContent());
                        item.put("time", formatTime(announcement.getPublishTime()));
                        item.put("priority", announcement.getPriority() != null ? announcement.getPriority() : "info");
                        item.put("isRead", false);
                        return item;
                    })
                    .limit(limit.longValue())
                    .toList();

            log.info("返回的通知数据：{}", result);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取教师通知失败：{}", e.getMessage(), e);
            return Result.error("获取教师通知失败：" + e.getMessage());
        }
    }

    private String formatTime(java.util.Date date) {
        if (date == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    @GetMapping("/majors")
    public Result getMajors() {
        log.info("教师端获取专业列表");
        try {
            List<com.gdmu.entity.Major> majors = majorService.findAll();
            log.info("查询到的专业数量: {}", majors != null ? majors.size() : 0);
            if (majors != null && !majors.isEmpty()) {
                log.info("第一个专业: id={}, name={}", majors.get(0).getId(), majors.get(0).getName());
            }
            return Result.success(majors);
        } catch (Exception e) {
            log.error("获取专业列表失败：{}", e.getMessage(), e);
            return Result.error("获取专业列表失败");
        }
    }

    @GetMapping("/grades")
    public Result getGrades() {
        log.info("教师端获取年级列表");
        try {
            List<Integer> grades = userService.findDistinctGrades();
            log.info("查询到的年级数量: {}", grades != null ? grades.size() : 0);
            if (grades != null && !grades.isEmpty()) {
                log.info("第一个年级: {}", grades.get(0));
            }
            return Result.success(grades);
        } catch (Exception e) {
            log.error("获取年级列表失败：{}", e.getMessage(), e);
            return Result.error("获取年级列表失败");
        }
    }

    @GetMapping("/all-classes")
    public Result getClasses() {
        log.info("教师端获取所有班级列表");
        try {
            List<com.gdmu.entity.Class> classes = classService.findAll();
            log.info("查询到的班级数量: {}", classes != null ? classes.size() : 0);
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取班级列表失败：{}", e.getMessage(), e);
            return Result.error("获取班级列表失败");
        }
    }

    @GetMapping("/companies")
    public Result getCompanies(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "1000") Integer pageSize,
                               @RequestParam(required = false) String companyName,
                               @RequestParam(required = false) Integer status,
                               @RequestParam(required = false) Integer recallStatus,
                               @RequestParam(required = false) String companyTag) {
        log.info("教师端获取企业列表（用于下拉框），页码：{}, 每页条数：{}, 企业名称：{}", page, pageSize, companyName);
        try {
            PageResult<com.gdmu.entity.CompanyUser> pageResult = companyUserService.findPage(page, pageSize, companyName, status, recallStatus, companyTag);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取企业列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业列表失败");
        }
    }

    @GetMapping("/students")
    public Result getStudents(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(required = false) String searchName,
                              @RequestParam(required = false) Long classId) {
        log.info("教师端获取学生列表，页码：{}, 每页条数：{}, 搜索姓名：{}, 班级ID：{}", page, pageSize, searchName, classId);
        try {
            PageResult<com.gdmu.entity.StudentUser> pageResult = studentUserService.findPage(
                page, 
                pageSize, 
                null, 
                searchName, 
                null, 
                null, 
                classId != null ? classId.toString() : null
            );
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取学生列表失败：{}", e.getMessage(), e);
            return Result.error("获取学生列表失败");
        }
    }

    @GetMapping("/companies/{id}")
    public Result getCompanyById(@PathVariable Long id) {
        log.info("教师端根据 ID 获取企业信息：{}", id);
        try {
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            return Result.success(company);
        } catch (Exception e) {
            log.error("获取企业信息失败: {}", e.getMessage(), e);
            return Result.error("获取企业信息失败");
        }
    }

    @GetMapping("/companies/audit/pending")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getPendingAuditCompanies(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String companyName,
                                           @RequestParam(required = false) String contactPerson,
                                           @RequestParam(required = false) String contactPhone) {
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("当前用户: {}, 权限列表: {}", authentication.getName(), authentication.getAuthorities());
        
        log.info("获取待审核企业列表，页码: {}, 每页条数: {}, 企业名称: {}, 联系人: {}, 联系电话: {}", 
                page, pageSize, companyName, contactPerson, contactPhone);
        try {
            PageResult<CompanyUser> pageResult = companyUserService.findPendingAuditPage(page, pageSize, companyName, contactPerson, contactPhone);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取待审核企业列表失败: {}", e.getMessage(), e);
            return Result.error("获取待审核企业列表失败");
        }
    }

    @GetMapping("/companies/statistics")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getCompanyStatistics() {
        log.info("获取企业审核统计数据");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("pending", companyUserService.countByAuditStatus(0));
            statistics.put("approved", companyUserService.countByAuditStatus(1));
            statistics.put("rejected", companyUserService.countByAuditStatus(2));
            statistics.put("total", companyUserService.count());
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取统计数据失败");
        }
    }

    @PutMapping("/companies/{id}/audit")
    @PreAuthorize("hasAuthority('user:company:audit')")
    @Log(operationType = "AUDIT", module = "COMPANY_MANAGEMENT", description = "审核企业注册申请")
    public Result auditCompany(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核企业注册，ID: {}, 审核参数: {}", id, params);
        try {
            Integer auditStatus = Integer.valueOf(params.get("auditStatus").toString());
            String auditRemark = params.get("auditRemark") != null ? params.get("auditRemark").toString() : "";
            
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            
            company.setAuditStatus(auditStatus);
            company.setAuditRemark(auditRemark);
            company.setAuditTime(new Date());
            company.setUpdateTime(new Date());
            
            if (auditStatus == 1) {
                company.setStatus(1);
                company.setReviewerId(getCurrentUserId());
                
                String username = company.getCompanyName();
                String password = "123456";
                company.setUsername(username);
                company.setPassword(password);
                company.setRole("ROLE_COMPANY");
                
                companyUserService.update(company);
                
                return Result.success("审核通过");
            } else if (auditStatus == 2) {
                company.setStatus(3);
                company.setReviewerId(getCurrentUserId());
                
                companyUserService.update(company);
                
                return Result.success("审核拒绝");
            } else {
                return Result.error("无效的审核状态");
            }
        } catch (BusinessException e) {
            log.warn("审核企业注册失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核企业注册时发生异常: {}", e.getMessage(), e);
            return Result.error("审核失败: " + e.getMessage());
        }
    }

    @GetMapping("/internship-status")
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
        log.info("教师端分页获取实习状态列表，页码：{}, 每页条数：{}, 学生 ID: {}, 学生姓名：{}, 性别：{}, 状态：{}, 企业 ID: {}, 企业名称：{}, 年级：{}, 专业：{}, 班级：{}",
                page, pageSize, studentId, name, gender, status, companyId, companyName, grade, major, className);
        try {
            PageResult<com.gdmu.entity.StudentInternshipStatus> pageResult = studentInternshipStatusService.findPage(page, pageSize, studentId, name, gender, status, companyId, companyName, grade, major, className);
            log.info("查询结果总数：{}", pageResult.getTotal());
            if (pageResult.getRows() != null && !pageResult.getRows().isEmpty()) {
                com.gdmu.entity.StudentInternshipStatus firstStatus = pageResult.getRows().get(0);
                log.info("第一条数据 - 学生ID：{}, 状态：{}, 企业ID：{}, 企业对象：{}", 
                    firstStatus.getStudentId(), firstStatus.getStatus(), firstStatus.getCompanyId(), firstStatus.getCompany());
                if (firstStatus.getCompany() != null) {
                    log.info("企业名称：{}", firstStatus.getCompany().getCompanyName());
                } else {
                    log.warn("企业对象为null");
                }
            }
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取实习状态列表失败：{}", e.getMessage(), e);
            return Result.error("获取实习状态列表失败");
        }
    }

    /**
     * 获取学生实习状态统计数据（所有状态的学生数量）
     * 状态说明：0=无offer, 1=待确认, 2=已确定, 3=实习中, 4=已结束, 5=已中断, 6=延期
     */
    @GetMapping("/internship-status/statistics")
    public Result getInternshipStatusStatistics() {
        log.info("获取学生实习状态统计数据");
        try {
            Long total = studentInternshipStatusService.count();

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("studentCount", total);
            statistics.put("noOfferCount", studentInternshipStatusService.countByStatus(0)); // 无offer
            statistics.put("pendingCount", studentInternshipStatusService.countByStatus(1)); // 待确认
            statistics.put("confirmedCount", studentInternshipStatusService.countByStatus(2)); // 已确定
            statistics.put("interningCount", studentInternshipStatusService.countByStatus(3)); // 实习中
            statistics.put("finishedCount", studentInternshipStatusService.countByStatus(4)); // 已结束
            statistics.put("interruptedCount", studentInternshipStatusService.countByStatus(5)); // 已中断
            statistics.put("delayedCount", studentInternshipStatusService.countByStatus(6)); // 延期

            log.info("统计数据：总数={}, 无offer={}, 待确认={}, 已确定={}, 实习中={}, 已结束={}, 已中断={}, 延期={}",
                    total, statistics.get("noOfferCount"), statistics.get("pendingCount"),
                    statistics.get("confirmedCount"), statistics.get("interningCount"),
                    statistics.get("finishedCount"), statistics.get("interruptedCount"), statistics.get("delayedCount"));
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败：{}", e.getMessage(), e);
            return Result.error("获取统计数据失败");
        }
    }

    @GetMapping("/internship-status/{id}")
    public Result getInternshipStatusById(@PathVariable Long id) {
        log.info("教师端获取实习状态详情，ID: {}", id);
        try {
            com.gdmu.entity.StudentInternshipStatus status = studentInternshipStatusService.findById(id);
            if (status == null) {
                return Result.error("实习状态不存在");
            }
            return Result.success(status);
        } catch (Exception e) {
            log.error("获取实习状态详情失败：{}", e.getMessage(), e);
            return Result.error("获取实习状态详情失败");
        }
    }

    @GetMapping("/internship-status/export")
    public void exportInternshipStatus(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String studentIds,
            HttpServletResponse response) throws IOException {
        log.info("教师端导出实习状态数据，学生 ID: {}, 学生姓名：{}, 性别：{}, 状态：{}, 企业 ID: {}, 企业名称：{}, 选中学生IDs: {}",
                studentId, name, gender, status, companyId, companyName, studentIds);

        List<StudentInternshipStatus> statusList;
        if (studentIds != null && !studentIds.isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (String idStr : studentIds.split(",")) {
                try {
                    ids.add(Long.parseLong(idStr.trim()));
                } catch (NumberFormatException e) {
                    log.warn("无效的ID格式: {}", idStr);
                }
            }
            statusList = studentInternshipStatusService.findByIds(ids);
            log.info("根据选中ID导出，ID数量: {}", ids.size());
        } else {
            statusList = studentInternshipStatusService.list(studentId, name, gender, status, companyId, companyName, null, null, null, null);
            log.info("根据条件导出，记录数: {}", statusList.size());
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("学生实习状态");

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] headers = {"学号", "姓名", "性别", "年级", "专业", "班级", "实习状态", "企业名称", "岗位名称", "实习开始时间", "实习结束时间", "更新时间"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (StudentInternshipStatus item : statusList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getStudent() != null ? item.getStudent().getStudentUserId() : "");
                row.createCell(1).setCellValue(item.getStudent() != null ? item.getStudent().getName() : "");
                row.createCell(2).setCellValue(item.getStudent() != null ? getGenderText(item.getStudent().getGender()) : "");
                row.createCell(3).setCellValue(item.getStudent() != null ? (item.getStudent().getGrade() != null ? item.getStudent().getGrade() + "级" : "") : "");
                row.createCell(4).setCellValue(item.getMajor() != null ? item.getMajor().getName() : "");
                row.createCell(5).setCellValue(item.getCls() != null ? item.getCls().getName() : "");
                row.createCell(6).setCellValue(getStatusText(item.getStatus()));
                row.createCell(7).setCellValue(item.getCompany() != null ? item.getCompany().getCompanyName() : "");
                row.createCell(8).setCellValue(item.getPosition() != null ? item.getPosition().getPositionName() : "");
                row.createCell(9).setCellValue(item.getInternshipStartTime() != null ? dateFormat.format(item.getInternshipStartTime()) : "");
                row.createCell(10).setCellValue(item.getInternshipEndTime() != null ? dateFormat.format(item.getInternshipEndTime()) : "");
                row.createCell(11).setCellValue(item.getUpdateTime() != null ? dateFormat.format(item.getUpdateTime()) : "");
            }

            workbook.write(out);
            byte[] excelData = out.toByteArray();

            String fileName = "学生实习状态导出_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setContentLength(excelData.length);

            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelData);
                outputStream.flush();
            }
        }

        log.info("教师端实习状态数据导出成功，共 {} 条记录", statusList.size());
    }

    @GetMapping("/internship-status/export/batch")
    public void exportBatchInternshipStatus(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Integer status,
            HttpServletResponse response) throws IOException {
        log.info("教师端批量导出实习状态数据，导出范围: {}, 班级: {}, 年级: {}, 状态: {}",
                scope, className, grade, status);

        List<StudentInternshipStatus> statusList = studentInternshipStatusService.list(null, null, null, status, null, null, grade, null, className, null);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("学生实习状态汇总");

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);

            String[] headers = {"学号", "姓名", "性别", "年级", "专业", "班级", "实习状态", "企业名称", "岗位名称", "实习开始时间", "实习结束时间", "更新时间"};

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (StudentInternshipStatus item : statusList) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(item.getStudent() != null ? item.getStudent().getStudentUserId() : "");
                row.createCell(1).setCellValue(item.getStudent() != null ? item.getStudent().getName() : "");
                row.createCell(2).setCellValue(item.getStudent() != null ? getGenderText(item.getStudent().getGender()) : "");
                row.createCell(3).setCellValue(item.getStudent() != null ? (item.getStudent().getGrade() != null ? item.getStudent().getGrade() + "级" : "") : "");
                row.createCell(4).setCellValue(item.getMajor() != null ? item.getMajor().getName() : "");
                row.createCell(5).setCellValue(item.getCls() != null ? item.getCls().getName() : "");
                row.createCell(6).setCellValue(getStatusText(item.getStatus()));
                row.createCell(7).setCellValue(item.getCompany() != null ? item.getCompany().getCompanyName() : "");
                row.createCell(8).setCellValue(item.getPosition() != null ? item.getPosition().getPositionName() : "");
                row.createCell(9).setCellValue(item.getInternshipStartTime() != null ? dateFormat.format(item.getInternshipStartTime()) : "");
                row.createCell(10).setCellValue(item.getInternshipEndTime() != null ? dateFormat.format(item.getInternshipEndTime()) : "");
                row.createCell(11).setCellValue(item.getUpdateTime() != null ? dateFormat.format(item.getUpdateTime()) : "");
            }

            workbook.write(out);
            byte[] excelData = out.toByteArray();

            String scopeText = "";
            if ("class".equals(scope) && className != null) {
                scopeText = className + "_";
            } else if ("grade".equals(scope) && grade != null) {
                scopeText = grade + "级_";
            }
            String fileName = scopeText + "学生实习状态汇总_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setContentLength(excelData.length);

            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelData);
                outputStream.flush();
            }
        }

        log.info("教师端批量实习状态数据导出成功，共 {} 条记录", statusList.size());
    }

    private String getGenderText(Integer gender) {
        if (gender == null) return "";
        return gender == 1 ? "男" : (gender == 2 ? "女" : "未知");
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "未找到实习";
            case 1 -> "有Offer未确定";
            case 2 -> "已确定实习";
            case 3 -> "实习结束";
            default -> "未知";
        };
    }

    private Long getCurrentUserId() {
        Long userId = CurrentHolder.getUserId();
        if (userId == null) {
            log.warn("未获取到当前登录用户 ID，返回默认值");
            return 1L; // 默认值，仅用于兼容
        }
        log.debug("获取到当前登录用户 ID: {}", userId);
        return userId;
    }

    /**
     * 获取教师可见的资源列表（只显示已发布的资源）
     */
    @GetMapping("/resources")
    public Result getPublishedResources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("教师端获取已发布资源列表，页码：{}, 每页条数：{}", page, pageSize);
        try {
            Object result = resourceService.getPublishedResources(page, pageSize);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取已发布资源列表失败：{}", e.getMessage(), e);
            return Result.error("获取已发布资源列表失败");
        }
    }

    /**
     * 获取教师自己上传的资源列表（包括草稿状态的）
     */
    @GetMapping("/resources/my")
    public Result getMyResources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("教师端获取自己上传的资源列表，页码：{}, 每页条数：{}", page, pageSize);
        try {
            Long currentUserId = getCurrentUserId();
            List<com.gdmu.entity.LearningResource> myResources = resourceService.getResourcesByUploaderId(currentUserId);
            
            // 构建分页结果
            int total = myResources.size();
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, total);
            
            List<com.gdmu.entity.LearningResource> pageResources = myResources.subList(startIndex, endIndex);
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", pageResources);
            result.put("total", total);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取自己上传的资源列表失败：{}", e.getMessage(), e);
            return Result.error("获取自己上传的资源列表失败");
        }
    }

    /**
     * 更新资源（只能更新自己上传的）
     */
    @PutMapping("/resources/{id}")
    @Log(operationType = "UPDATE", module = "RESOURCE_MANAGEMENT", description = "教师更新学习资源")
    public Result updateResource(@PathVariable Long id, @RequestBody com.gdmu.entity.LearningResource resource) {
        log.info("教师端更新资源，ID: {}, 标题: {}", id, resource.getTitle());
        try {
            Long currentUserId = getCurrentUserId();
            com.gdmu.entity.LearningResource existingResource = resourceService.getResourceById(id);
            
            if (existingResource == null) {
                return Result.error("资源不存在");
            }
            
            // 检查是否是自己上传的资源
            if (!existingResource.getUploaderId().equals(currentUserId)) {
                log.warn("用户 {} 尝试更新非自己上传的资源 {}", currentUserId, id);
                return Result.error("只能编辑自己上传的资源");
            }
            
            // 更新资源信息
            existingResource.setTitle(resource.getTitle());
            existingResource.setDescription(resource.getDescription());
            if (resource.getFileType() != null) {
                existingResource.setFileType(resource.getFileType());
            }
            
            // 保存更新
            resourceService.updateResource(existingResource);
            
            log.info("资源更新成功，ID: {}", id);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.error("更新资源失败：{}", e.getMessage(), e);
            return Result.error("更新资源失败");
        }
    }

    /**
     * 删除资源（只能删除自己上传的）
     */
    @DeleteMapping("/resources/{id}")
    @Log(operationType = "DELETE", module = "RESOURCE_MANAGEMENT", description = "教师删除学习资源")
    public Result deleteResource(@PathVariable Long id) {
        log.info("教师端删除资源，ID: {}", id);
        try {
            Long currentUserId = getCurrentUserId();
            com.gdmu.entity.LearningResource existingResource = resourceService.getResourceById(id);
            
            if (existingResource == null) {
                return Result.error("资源不存在");
            }
            
            // 检查是否是自己上传的资源
            if (!existingResource.getUploaderId().equals(currentUserId)) {
                log.warn("用户 {} 尝试删除非自己上传的资源 {}", currentUserId, id);
                return Result.error("只能删除自己上传的资源");
            }
            
            // 删除资源
            resourceService.deleteResource(id);
            
            log.info("资源删除成功，ID: {}", id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除资源失败：{}", e.getMessage(), e);
            return Result.error("删除资源失败");
        }
    }

}