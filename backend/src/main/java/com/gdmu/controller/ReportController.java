package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.dto.ApprovalDetailDTO;
import com.gdmu.entity.dto.CompanyDetailDTO;
import com.gdmu.entity.dto.CompanyTrendDTO;
import com.gdmu.entity.dto.CoreMetricsDTO;
import com.gdmu.entity.dto.StudentInternshipDetailDTO;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.ReportMapper;
import com.gdmu.mapper.StudentApplicationMapper;
import com.gdmu.mapper.StudentInternshipStatusMapper;
import com.gdmu.mapper.StudentUserMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计报表控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/reports")
public class ReportController {

    @Autowired
    private ReportMapper reportMapper;

    @Autowired
    private CompanyUserMapper companyUserMapper;

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private StudentInternshipStatusMapper internshipStatusMapper;

    @Autowired
    private StudentApplicationMapper studentApplicationMapper;

    /**
     * 获取核心指标数据
     */
    @GetMapping("/metrics")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getCoreMetrics(
            @RequestParam(defaultValue = "month") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        log.info("获取核心指标数据，时间范围: {}, 开始日期: {}, 结束日期: {}", timeRange, startDate, endDate);
        try {
            Date[] dateRange = calculateDateRange(timeRange, startDate, endDate);
            Date currentStartDate = dateRange[0];
            Date currentEndDate = dateRange[1];
            Date previousStartDate = dateRange[2];
            Date previousEndDate = dateRange[3];

            CoreMetricsDTO currentMetrics = new CoreMetricsDTO();

            int companyCount = 0;
            try {
                companyCount = companyUserMapper.countApproved();
            } catch (Exception e) {
                log.warn("获取企业数量失败: {}", e.getMessage());
            }
            currentMetrics.setCompanyCount(companyCount);

            long totalStudents = 0;
            try {
                totalStudents = studentUserMapper.count() != null ? studentUserMapper.count() : 0;
            } catch (Exception e) {
                log.warn("获取学生总数失败: {}", e.getMessage());
            }

            long internshipStudents = 0;
            try {
                internshipStudents = internshipStatusMapper.countByStatus(1) + internshipStatusMapper.countByStatus(2) + internshipStatusMapper.countByStatus(3);
            } catch (Exception e) {
                log.warn("获取实习学生数失败: {}", e.getMessage());
            }

            double internshipRate = totalStudents > 0 ? (double) internshipStudents / totalStudents * 100 : 0;
            currentMetrics.setInternshipRate(Math.round(internshipRate * 10.0) / 10.0);

            int approvalCount = getApprovalCount(currentStartDate, currentEndDate);
            currentMetrics.setApprovalCount(approvalCount);

            int resourceDownloads = 0;
            try {
                resourceDownloads = reportMapper.getResourceDownloads(currentStartDate, currentEndDate);
            } catch (Exception e) {
                log.warn("获取资源下载量失败: {}", e.getMessage());
            }
            currentMetrics.setResourceDownloads(resourceDownloads);

            currentMetrics.setCompanyChange(0);
            currentMetrics.setInternshipRateChange(0);
            currentMetrics.setApprovalCountChange(0);
            currentMetrics.setResourceDownloadsChange(0);

            return Result.success(currentMetrics);
        } catch (Exception e) {
            log.error("获取核心指标数据失败: {}", e.getMessage(), e);
            CoreMetricsDTO defaultMetrics = new CoreMetricsDTO();
            defaultMetrics.setCompanyCount(0);
            defaultMetrics.setCompanyChange(0);
            defaultMetrics.setInternshipRate(0.0);
            defaultMetrics.setInternshipRateChange(0);
            defaultMetrics.setApprovalCount(0);
            defaultMetrics.setApprovalCountChange(0);
            defaultMetrics.setResourceDownloads(0);
            defaultMetrics.setResourceDownloadsChange(0);
            return Result.success(defaultMetrics);
        }
    }

    /**
     * 获取企业入驻趋势数据
     */
    @GetMapping("/company-trend")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getCompanyTrend(@RequestParam(defaultValue = "2026") Integer year) {
        log.info("获取企业入驻趋势数据，年份: {}", year);
        try {
            List<CompanyTrendDTO> trend = reportMapper.getCompanyTrendByQuarter(year);
            if (trend.isEmpty()) {
                CompanyTrendDTO q1 = new CompanyTrendDTO();
                q1.setLabel("第一季度");
                q1.setValue(0);
                CompanyTrendDTO q2 = new CompanyTrendDTO();
                q2.setLabel("第二季度");
                q2.setValue(0);
                CompanyTrendDTO q3 = new CompanyTrendDTO();
                q3.setLabel("第三季度");
                q3.setValue(0);
                CompanyTrendDTO q4 = new CompanyTrendDTO();
                q4.setLabel("第四季度");
                q4.setValue(0);
                trend = Arrays.asList(q1, q2, q3, q4);
            }
            return Result.success(trend);
        } catch (Exception e) {
            log.error("获取企业入驻趋势数据失败: {}", e.getMessage(), e);
            CompanyTrendDTO q1 = new CompanyTrendDTO();
            q1.setLabel("第一季度");
            q1.setValue(0);
            CompanyTrendDTO q2 = new CompanyTrendDTO();
            q2.setLabel("第二季度");
            q2.setValue(0);
            CompanyTrendDTO q3 = new CompanyTrendDTO();
            q3.setLabel("第三季度");
            q3.setValue(0);
            CompanyTrendDTO q4 = new CompanyTrendDTO();
            q4.setLabel("第四季度");
            q4.setValue(0);
            return Result.success(Arrays.asList(q1, q2, q3, q4));
        }
    }

    /**
     * 获取企业入驻详情列表
     */
    @GetMapping("/companies")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getCompanyDetails(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "5") Integer pageSize) {
        log.info("获取企业入驻详情列表，页码: {}, 每页大小: {}", page, pageSize);
        try {
            int offset = (page - 1) * pageSize;
            List<CompanyDetailDTO> companies = reportMapper.getCompanyDetails(offset, pageSize);

            for (CompanyDetailDTO company : companies) {
                List<String> tags = new ArrayList<>();
                String companyTag = company.getCompanyTag();
                if (companyTag != null) {
                    if (companyTag.contains("双向")) {
                        tags.add("双向");
                    }
                    if (companyTag.contains("自主")) {
                        tags.add("自主");
                    }
                    if (companyTag.contains("兜底")) {
                        tags.add("兜底");
                    }
                }
                if (tags.isEmpty()) {
                    tags.add("自主");
                }
                company.setTags(tags);
            }

            return Result.success(companies);
        } catch (Exception e) {
            log.error("获取企业入驻详情列表失败: {}", e.getMessage(), e);
            return Result.success(new ArrayList<>());
        }
    }

    /**
     * 获取企业入驻详情总数
     */
    @GetMapping("/companies/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getCompanyDetailsCount() {
        log.info("获取企业入驻详情总数");
        try {
            Long count = reportMapper.getCompanyDetailsCount();
            return Result.success(count != null ? count : 0);
        } catch (Exception e) {
            log.error("获取企业入驻详情总数失败: {}", e.getMessage(), e);
            return Result.success(0);
        }
    }

    /**
     * 获取学生实习详情列表
     */
    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getStudentInternshipDetails(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "5") Integer pageSize) {
        log.info("获取学生实习详情列表，页码: {}, 每页大小: {}", page, pageSize);
        try {
            int offset = (page - 1) * pageSize;
            List<StudentInternshipDetailDTO> students = reportMapper.getStudentInternshipDetails(offset, pageSize);
            return Result.success(students);
        } catch (Exception e) {
            log.error("获取学生实习详情列表失败: {}", e.getMessage(), e);
            return Result.success(new ArrayList<>());
        }
    }

    /**
     * 获取学生实习详情总数
     */
    @GetMapping("/students/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getStudentInternshipDetailsCount() {
        log.info("获取学生实习详情总数");
        try {
            Long count = reportMapper.getStudentInternshipDetailsCount();
            return Result.success(count != null ? count : 0);
        } catch (Exception e) {
            log.error("获取学生实习详情总数失败: {}", e.getMessage(), e);
            return Result.success(0);
        }
    }

    /**
     * 获取申请审核详情列表
     */
    @GetMapping("/approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getApprovalDetails(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "5") Integer pageSize) {
        log.info("获取申请审核详情列表，页码: {}, 每页大小: {}", page, pageSize);
        try {
            int offset = (page - 1) * pageSize;
            List<ApprovalDetailDTO> approvals = reportMapper.getApprovalDetails(offset, pageSize);
            return Result.success(approvals);
        } catch (Exception e) {
            log.error("获取申请审核详情列表失败: {}", e.getMessage(), e);
            return Result.success(new ArrayList<>());
        }
    }

    /**
     * 获取申请审核详情总数
     */
    @GetMapping("/approvals/count")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result getApprovalDetailsCount() {
        log.info("获取申请审核详情总数");
        try {
            Long count = reportMapper.getApprovalDetailsCount();
            return Result.success(count != null ? count : 0);
        } catch (Exception e) {
            log.error("获取申请审核详情总数失败: {}", e.getMessage(), e);
            return Result.success(0);
        }
    }

    /**
     * 导出综合统计报表
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public void exportReport(
            @RequestParam(defaultValue = "month") String timeRange,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate,
            HttpServletResponse response) throws IOException {
        log.info("导出综合统计报表，时间范围: {}, 开始日期: {}, 结束日期: {}", timeRange, startDate, endDate);

        Date[] dateRange = calculateDateRange(timeRange, startDate, endDate);
        Date currentStartDate = dateRange[0];
        Date currentEndDate = dateRange[1];

        List<CompanyDetailDTO> companies = reportMapper.getCompanyDetails(0, 1000);
        List<StudentInternshipDetailDTO> students = reportMapper.getStudentInternshipDetails(0, 1000);
        List<ApprovalDetailDTO> approvals = reportMapper.getApprovalDetails(0, 1000);

        // 设置企业标签
        for (CompanyDetailDTO company : companies) {
            List<String> tags = new ArrayList<>();
            String companyTag = company.getCompanyTag();
            if (companyTag != null) {
                if (companyTag.contains("双向")) {
                    tags.add("双向");
                }
                if (companyTag.contains("自主")) {
                    tags.add("自主");
                }
                if (companyTag.contains("兜底")) {
                    tags.add("兜底");
                }
            }
            if (tags.isEmpty()) {
                tags.add("自主");
            }
            company.setTags(tags);
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle titleStyle = createTitleStyle(workbook);

            // 企业入驻详情Sheet
            Sheet companySheet = workbook.createSheet("企业入驻详情");
            Row companyTitleRow = companySheet.createRow(0);
            Cell companyTitleCell = companyTitleRow.createCell(0);
            companyTitleCell.setCellValue("企业入驻详情报表");
            companyTitleCell.setCellStyle(titleStyle);
            companySheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));

            String[] companyHeaders = {"企业名称", "入驻时间", "提供岗位数", "已录取学生数", "企业状态", "企业标签"};
            Row companyHeaderRow = companySheet.createRow(1);
            for (int i = 0; i < companyHeaders.length; i++) {
                Cell cell = companyHeaderRow.createCell(i);
                cell.setCellValue(companyHeaders[i]);
                cell.setCellStyle(headerStyle);
                companySheet.setColumnWidth(i, 4000);
            }

            int companyRowNum = 2;
            for (CompanyDetailDTO company : companies) {
                Row row = companySheet.createRow(companyRowNum++);
                row.createCell(0).setCellValue(company.getName() != null ? company.getName() : "");
                row.createCell(1).setCellValue(company.getJoinDate() != null ? dateFormat.format(company.getJoinDate()) : "");
                row.createCell(2).setCellValue(company.getPositionCount());
                row.createCell(3).setCellValue(company.getAdmittedCount());
                row.createCell(4).setCellValue(company.getStatus() != null ? company.getStatus() : "");
                row.createCell(5).setCellValue(company.getTags() != null ? String.join(", ", company.getTags()) : "");
            }

            // 学生实习详情Sheet
            Sheet studentSheet = workbook.createSheet("学生实习详情");
            Row studentTitleRow = studentSheet.createRow(0);
            Cell studentTitleCell = studentTitleRow.createCell(0);
            studentTitleCell.setCellValue("学生实习详情报表");
            studentTitleCell.setCellStyle(titleStyle);
            studentSheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));

            String[] studentHeaders = {"学院", "专业", "学生总数", "已实习人数", "实习率(%)", "备注"};
            Row studentHeaderRow = studentSheet.createRow(1);
            for (int i = 0; i < studentHeaders.length; i++) {
                Cell cell = studentHeaderRow.createCell(i);
                cell.setCellValue(studentHeaders[i]);
                cell.setCellStyle(headerStyle);
                studentSheet.setColumnWidth(i, 4000);
            }

            int studentRowNum = 2;
            for (StudentInternshipDetailDTO student : students) {
                Row row = studentSheet.createRow(studentRowNum++);
                row.createCell(0).setCellValue(student.getCollege() != null ? student.getCollege() : "");
                row.createCell(1).setCellValue(student.getMajor() != null ? student.getMajor() : "");
                row.createCell(2).setCellValue(student.getTotalCount());
                row.createCell(3).setCellValue(student.getInternshipCount());
                row.createCell(4).setCellValue(student.getInternshipRate());
                row.createCell(5).setCellValue("");
            }

            // 申请审核详情Sheet
            Sheet approvalSheet = workbook.createSheet("申请审核详情");
            Row approvalTitleRow = approvalSheet.createRow(0);
            Cell approvalTitleCell = approvalTitleRow.createCell(0);
            approvalTitleCell.setCellValue("申请审核详情报表");
            approvalTitleCell.setCellStyle(titleStyle);
            approvalSheet.addMergedRegion(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, 5));

            String[] approvalHeaders = {"申请类型", "申请数量", "通过数量", "驳回数量", "通过率(%)", "备注"};
            Row approvalHeaderRow = approvalSheet.createRow(1);
            for (int i = 0; i < approvalHeaders.length; i++) {
                Cell cell = approvalHeaderRow.createCell(i);
                cell.setCellValue(approvalHeaders[i]);
                cell.setCellStyle(headerStyle);
                approvalSheet.setColumnWidth(i, 4000);
            }

            int approvalRowNum = 2;
            for (ApprovalDetailDTO approval : approvals) {
                Row row = approvalSheet.createRow(approvalRowNum++);
                row.createCell(0).setCellValue(approval.getType() != null ? approval.getType() : "");
                row.createCell(1).setCellValue(approval.getTotalCount());
                row.createCell(2).setCellValue(approval.getApprovedCount());
                row.createCell(3).setCellValue(approval.getRejectedCount());
                row.createCell(4).setCellValue(approval.getApprovalRate());
                row.createCell(5).setCellValue("");
            }

            workbook.write(out);
            byte[] excelData = out.toByteArray();

            String fileName = "综合统计报表_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setContentLength(excelData.length);

            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelData);
                outputStream.flush();
            }
        }

        log.info("综合统计报表导出成功");
    }

    /**
     * 导出企业入驻报表
     */
    @GetMapping("/export/companies")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public void exportCompanyReport(HttpServletResponse response) throws IOException {
        log.info("导出企业入驻报表");

        List<CompanyDetailDTO> companies = reportMapper.getCompanyDetails(0, 10000);

        // 设置企业标签
        for (CompanyDetailDTO company : companies) {
            List<String> tags = new ArrayList<>();
            String companyTag = company.getCompanyTag();
            if (companyTag != null) {
                if (companyTag.contains("双向")) {
                    tags.add("双向");
                }
                if (companyTag.contains("自主")) {
                    tags.add("自主");
                }
                if (companyTag.contains("兜底")) {
                    tags.add("兜底");
                }
            }
            if (tags.isEmpty()) {
                tags.add("自主");
            }
            company.setTags(tags);
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("企业入驻详情");

            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {"企业名称", "入驻时间", "提供岗位数", "已录取学生数", "企业状态", "企业标签"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4500);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            int rowNum = 1;
            for (CompanyDetailDTO company : companies) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(company.getName() != null ? company.getName() : "");
                row.createCell(1).setCellValue(company.getJoinDate() != null ? dateFormat.format(company.getJoinDate()) : "");
                row.createCell(2).setCellValue(company.getPositionCount());
                row.createCell(3).setCellValue(company.getAdmittedCount());
                row.createCell(4).setCellValue(company.getStatus() != null ? company.getStatus() : "");
                row.createCell(5).setCellValue(company.getTags() != null ? String.join(", ", company.getTags()) : "");
            }

            workbook.write(out);
            byte[] excelData = out.toByteArray();

            String fileName = "企业入驻报表_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setContentLength(excelData.length);

            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelData);
                outputStream.flush();
            }
        }

        log.info("企业入驻报表导出成功，共 {} 条记录", companies.size());
    }

    /**
     * 导出学生实习报表
     */
    @GetMapping("/export/students")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public void exportStudentReport(HttpServletResponse response) throws IOException {
        log.info("导出学生实习报表");

        List<StudentInternshipDetailDTO> students = reportMapper.getStudentInternshipDetails(0, 10000);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("学生实习详情");

            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {"学院", "专业", "学生总数", "已实习人数", "实习率(%)", "未实习人数"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            int rowNum = 1;
            for (StudentInternshipDetailDTO student : students) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(student.getCollege() != null ? student.getCollege() : "");
                row.createCell(1).setCellValue(student.getMajor() != null ? student.getMajor() : "");
                row.createCell(2).setCellValue(student.getTotalCount());
                row.createCell(3).setCellValue(student.getInternshipCount());
                row.createCell(4).setCellValue(student.getInternshipRate());
                row.createCell(5).setCellValue(student.getTotalCount() - student.getInternshipCount());
            }

            workbook.write(out);
            byte[] excelData = out.toByteArray();

            String fileName = "学生实习报表_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setContentLength(excelData.length);

            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelData);
                outputStream.flush();
            }
        }

        log.info("学生实习报表导出成功，共 {} 条记录", students.size());
    }

    /**
     * 导出申请审核报表
     */
    @GetMapping("/export/approvals")
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public void exportApprovalReport(HttpServletResponse response) throws IOException {
        log.info("导出申请审核报表");

        List<ApprovalDetailDTO> approvals = reportMapper.getApprovalDetails(0, 10000);

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("申请审核详情");

            CellStyle headerStyle = createHeaderStyle(workbook);

            String[] headers = {"申请类型", "申请数量", "通过数量", "驳回数量", "通过率(%)", "待审核数量"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4000);
            }

            int rowNum = 1;
            for (ApprovalDetailDTO approval : approvals) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(approval.getType() != null ? approval.getType() : "");
                row.createCell(1).setCellValue(approval.getTotalCount());
                row.createCell(2).setCellValue(approval.getApprovedCount());
                row.createCell(3).setCellValue(approval.getRejectedCount());
                row.createCell(4).setCellValue(approval.getApprovalRate());
                row.createCell(5).setCellValue(approval.getTotalCount() - approval.getApprovedCount() - approval.getRejectedCount());
            }

            workbook.write(out);
            byte[] excelData = out.toByteArray();

            String fileName = "申请审核报表_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx";
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"");
            response.setContentLength(excelData.length);

            try (OutputStream outputStream = response.getOutputStream()) {
                outputStream.write(excelData);
                outputStream.flush();
            }
        }

        log.info("申请审核报表导出成功，共 {} 条记录", approvals.size());
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
        headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        headerStyle.setFont(headerFont);
        headerStyle.setAlignment(HorizontalAlignment.CENTER);
        return headerStyle;
    }

    private CellStyle createTitleStyle(Workbook workbook) {
        CellStyle titleStyle = workbook.createCellStyle();
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleStyle.setFont(titleFont);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        return titleStyle;
    }

    private Date[] calculateDateRange(String timeRange, Date startDate, Date endDate) {
        Calendar calendar = Calendar.getInstance();
        Date currentEndDate = calendar.getTime();
        Date currentStartDate;
        Date previousEndDate;
        Date previousStartDate;

        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if ("custom".equals(timeRange) && startDate != null && endDate != null) {
            currentStartDate = startDate;
            currentEndDate = endDate;
            long diff = currentEndDate.getTime() - currentStartDate.getTime();
            previousEndDate = new Date(currentStartDate.getTime() - 1);
            previousStartDate = new Date(previousEndDate.getTime() - diff);
        } else {
            switch (timeRange) {
                case "week":
                    calendar.add(Calendar.WEEK_OF_YEAR, -1);
                    currentStartDate = calendar.getTime();
                    calendar.add(Calendar.WEEK_OF_YEAR, -1);
                    previousEndDate = calendar.getTime();
                    calendar.add(Calendar.WEEK_OF_YEAR, -1);
                    previousStartDate = calendar.getTime();
                    break;
                case "quarter":
                    calendar.add(Calendar.MONTH, -3);
                    currentStartDate = calendar.getTime();
                    calendar.add(Calendar.MONTH, -3);
                    previousEndDate = calendar.getTime();
                    calendar.add(Calendar.MONTH, -3);
                    previousStartDate = calendar.getTime();
                    break;
                case "year":
                    calendar.add(Calendar.YEAR, -1);
                    currentStartDate = calendar.getTime();
                    calendar.add(Calendar.YEAR, -1);
                    previousEndDate = calendar.getTime();
                    calendar.add(Calendar.YEAR, -1);
                    previousStartDate = calendar.getTime();
                    break;
                case "month":
                default:
                    calendar.add(Calendar.MONTH, -1);
                    currentStartDate = calendar.getTime();
                    calendar.add(Calendar.MONTH, -1);
                    previousEndDate = calendar.getTime();
                    calendar.add(Calendar.MONTH, -1);
                    previousStartDate = calendar.getTime();
                    break;
            }
        }

        return new Date[]{currentStartDate, currentEndDate, previousStartDate, previousEndDate};
    }

    private int getApprovalCount(Date startDate, Date endDate) {
        try {
            return studentApplicationMapper.countByTimeRange(startDate, endDate);
        } catch (Exception e) {
            log.error("获取申请审核数量失败: {}", e.getMessage());
            return 0;
        }
    }
}
