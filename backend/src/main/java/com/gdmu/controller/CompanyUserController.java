package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.exception.BusinessException;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.service.CompanyUserService;
import com.gdmu.utils.CurrentHolder;
import com.gdmu.utils.ExcelUtils;
import com.gdmu.utils.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/admin/companies")
public class CompanyUserController {

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.gdmu.service.CompanyTagService companyTagService;

    @GetMapping
    @PreAuthorize("hasAuthority('user:company:view')")
    public Result getAllCompanies(@RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "10") Integer pageSize,
                                 @RequestParam(required = false) String companyName,
                                 @RequestParam(required = false) Integer status,
                                 @RequestParam(required = false) Integer recallStatus,
                                 @RequestParam(required = false) String companyTag) {
        log.info("分页获取企业列表，页码：{}, 每页条数：{}, 企业名称：{}, 状态：{}, 撤回状态：{}, 企业标签：{}",
                page, pageSize, companyName, status, recallStatus, companyTag);
        PageResult<CompanyUser> pageResult = companyUserService.findPage(page, pageSize, companyName, status, recallStatus, companyTag);
        return Result.success(pageResult);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('user:company:view')")
    public void exportCompanyData(HttpServletResponse response,
                                  @RequestParam(required = false) String companyName,
                                  @RequestParam(required = false) Integer status) {
        log.info("导出企业 Excel 数据，筛选条件：企业名称={}, 状态={}", companyName, status);

        try {
            List<CompanyUser> companies = companyUserService.list(companyName, status);
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("企业数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"企业名称", "联系人", "联系电话", "联系邮箱", "企业地址", "企业简介", "标签", "状态", "创建时间", "更新时间"};
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            for (int i = 0; i < companies.size(); i++) {
                CompanyUser company = companies.get(i);
                Row dataRow = sheet.createRow(i + 1);

                dataRow.createCell(0).setCellValue(company.getCompanyName() != null ? company.getCompanyName() : "");
                dataRow.createCell(1).setCellValue(company.getContactPerson() != null ? company.getContactPerson() : "");
                dataRow.createCell(2).setCellValue(company.getContactPhone() != null ? company.getContactPhone() : "");
                dataRow.createCell(3).setCellValue(company.getContactEmail() != null ? company.getContactEmail() : "");
                dataRow.createCell(4).setCellValue(company.getAddress() != null ? company.getAddress() : "");
                dataRow.createCell(5).setCellValue(company.getIntroduction() != null ? company.getIntroduction() : "");
                dataRow.createCell(6).setCellValue(company.getCompanyTag() != null ? company.getCompanyTag() : "");
                dataRow.createCell(7).setCellValue(company.getStatus() != null ? getStatusText(company.getStatus()) : "");
                dataRow.createCell(8).setCellValue(company.getCreateTime() != null ? dateFormat.format(company.getCreateTime()) : "");
                dataRow.createCell(9).setCellValue(company.getUpdateTime() != null ? dateFormat.format(company.getUpdateTime()) : "");

                for (int j = 0; j < 10; j++) {
                    dataRow.getCell(j).setCellStyle(dataStyle);
                    sheet.autoSizeColumn(j);
                }
            }

            String filename = "企业数据_" + new Date().getTime() + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString());
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            workbook.write(response.getOutputStream());
            workbook.close();

            log.info("企业数据 Excel 导出成功，共导出{}条记录", companies.size());
        } catch (IOException e) {
            log.error("Excel 数据导出失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败：" + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        } catch (Exception e) {
            log.error("导出企业数据时发生异常", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败：系统异常\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        }
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待审核";
            case 1: return "审核通过";
            case 2: return "审核拒绝";
            case 3: return "已撤销";
            default: return "未知";
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:company:view')")
    public Result getCompanyById(@PathVariable Long id) {
        log.info("根据 ID 获取企业信息：{}", id);
        CompanyUser company = companyUserService.findById(id);
        if (company == null) {
            return Result.error("企业不存在");
        }
        return Result.success(company);
    }

    @Log(operationType = "ADD", module = "COMPANY_MANAGEMENT", description = "添加企业信息")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result addCompany(@RequestBody @Validated CompanyUser company) {
        log.info("添加企业信息：{}", company.getCompanyName());
        try {
            String username = company.getCompanyName();
            String password = "123456";

            company.setUsername(username);
            company.setPassword(password);
            company.setRole("ROLE_COMPANY");

            companyUserService.insert(company);

            return Result.success("添加成功");
        } catch (BusinessException e) {
            log.warn("添加企业信息失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @Log(module = "COMPANY_MANAGEMENT", operationType = "UPDATE", description = "更新企业信息")
    @PutMapping
    @PreAuthorize("hasAuthority('user:company:edit')")
    public Result updateCompany(@RequestBody CompanyUser company) {
        log.info("更新企业信息，ID: {}", company.getId());
        try {
            companyUserService.update(company);
            return Result.success("更新成功");
        } catch (BusinessException e) {
            log.warn("更新企业信息失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "删除企业信息")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result deleteCompany(@PathVariable Long id) {
        log.info("删除企业信息，ID: {}", id);
        try {
            companyUserService.delete(id);
            return Result.success("删除成功");
        } catch (BusinessException e) {
            log.warn("删除企业信息失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "批量删除企业信息")
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchDeleteCompanies(@RequestBody List<Long> ids) {
        log.info("批量删除企业信息，ID 列表：{}", ids);
        try {
            int deletedCount = companyUserService.batchDelete(ids);
            return Result.success("批量删除成功", deletedCount);
        } catch (BusinessException e) {
            log.warn("批量删除企业信息失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "审核企业注册")
    @PutMapping("/{id}/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Result auditCompany(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核企业注册，ID: {}, 审核参数：{}", id, params);
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
            log.warn("审核企业注册失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核企业注册时发生异常：{}", e.getMessage());
            return Result.error("审核失败：" + e.getMessage());
        }
    }

    @GetMapping("/audit/pending")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getPendingAuditCompanies(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String companyName,
                                           @RequestParam(required = false) String contactPerson,
                                           @RequestParam(required = false) String contactPhone) {
        log.info("获取待审核企业列表，页码：{}, 每页条数：{}, 企业名称：{}, 联系人：{}, 联系电话：{}",
                page, pageSize, companyName, contactPerson, contactPhone);
        try {
            PageResult<CompanyUser> pageResult = companyUserService.findPendingAuditPage(page, pageSize, companyName, contactPerson, contactPhone);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取待审核企业列表失败：{}", e.getMessage(), e);
            return Result.error("获取待审核企业列表失败");
        }
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

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('user:company:view')")
    public Result getStatistics() {
        log.info("获取企业审核统计数据");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("pending", companyUserService.countByAuditStatus(0));
            statistics.put("approved", companyUserService.countByAuditStatus(1));
            statistics.put("rejected", companyUserService.countByAuditStatus(2));
            statistics.put("total", companyUserService.count());
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败：{}", e.getMessage(), e);
            return Result.error("获取统计数据失败");
        }
    }

    @Log(operationType = "IMPORT", module = "COMPANY_MANAGEMENT", description = "批量导入企业信息")
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Result importCompanies(@RequestParam("file") MultipartFile file) {
        log.info("批量导入企业信息，文件名：{}", file.getOriginalFilename());

        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String fileName = file.getOriginalFilename();
            if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
                return Result.error("文件类型错误，请上传 Excel 文件");
            }

            List<Map<String, Object>> companyDataList = ExcelUtils.readExcel(file);

            Map<String, Object> importResult = companyUserService.importFromExcel(companyDataList);

            return Result.success("导入完成", importResult);
        } catch (Exception e) {
            log.error("批量导入企业信息失败：{}", e.getMessage(), e);
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    @Log(operationType = "DOWNLOAD", module = "COMPANY_MANAGEMENT", description = "下载企业导入模板")
    @GetMapping("/import-template")
    @PreAuthorize("hasRole('ADMIN')")
    public void downloadImportTemplate(HttpServletResponse response) {
        log.info("下载企业导入模板");

        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("企业导入模板");

            // 创建表头行（第 1 行）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"企业名称", "联系人", "联系电话", "联系邮箱", "企业地址", "企业简介", "手机号"};

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(headerFont);

            // 设置表头单元格
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 创建数据样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.LEFT);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // 设置列宽
            int[] columnWidths = {30, 15, 15, 25, 50, 60, 15};
            for (int i = 0; i < headers.length; i++) {
                sheet.setColumnWidth(i, columnWidths[i] * 256);
            }

            // 在最后一个字段列的右侧添加填写说明
            int explanationCol = headers.length;
            sheet.setColumnWidth(explanationCol, 45 * 256);

            // 填写说明标题
            Cell explanationTitleCell = headerRow.createCell(explanationCol);
            explanationTitleCell.setCellValue("填写说明：");
            Font boldFont = workbook.createFont();
            boldFont.setBold(true);
            boldFont.setFontHeightInPoints((short) 11);
            CellStyle explanationTitleStyle = workbook.createCellStyle();
            explanationTitleStyle.setFont(boldFont);
            explanationTitleStyle.setAlignment(HorizontalAlignment.LEFT);
            explanationTitleStyle.setVerticalAlignment(VerticalAlignment.TOP);
            explanationTitleCell.setCellStyle(explanationTitleStyle);

            // 填写说明内容
            String[] explanations = {
                "1. 企业名称为必填项，且不能与现有企业重名",
                "2. 联系电话和手机号请填写 11 位手机号码",
                "3. 联系邮箱请填写有效的邮箱地址",
                "4. 默认密码为 123456",
                "5. 手机号为选填项"
            };
            for (int i = 0; i < explanations.length; i++) {
                Row row = sheet.createRow(i + 1);
                Cell cell = row.createCell(explanationCol);
                cell.setCellValue(explanations[i]);
                CellStyle expStyle = workbook.createCellStyle();
                expStyle.setAlignment(HorizontalAlignment.LEFT);
                expStyle.setVerticalAlignment(VerticalAlignment.TOP);
                expStyle.setWrapText(true);
                cell.setCellStyle(expStyle);
            }

            String filename = "企业导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString());
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            workbook.write(response.getOutputStream());
            workbook.close();

            log.info("企业导入模板下载成功");
        } catch (IOException e) {
            log.error("下载模板失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"下载失败：" + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        }
    }

    @GetMapping("/recall/pending")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getPendingRecallAuditCompanies(@RequestParam(defaultValue = "1") Integer page,
                                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                                  @RequestParam(required = false) String companyName,
                                                  @RequestParam(required = false) String contactPerson,
                                                  @RequestParam(required = false) String contactPhone,
                                                  @RequestParam(required = false) Long companyId) {
        log.info("获取待审核撤回申请列表，页码：{}, 每页条数：{}, 企业名称：{}, 联系人：{}, 联系电话：{}, 企业 ID: {}",
                page, pageSize, companyName, contactPerson, contactPhone, companyId);
        try {
            PageResult<CompanyUser> pageResult;
            if (companyId != null && companyId > 0) {
                pageResult = companyUserService.findPendingRecallAuditPageById(page, pageSize, companyId);
            } else {
                pageResult = companyUserService.findPendingRecallAuditPage(page, pageSize, companyName, contactPerson, contactPhone);
            }
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取待审核撤回申请列表失败：{}", e.getMessage(), e);
            return Result.error("获取待审核撤回申请列表失败");
        }
    }

    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "审核企业撤回申请")
    @PutMapping("/{id}/recall-audit")
    @PreAuthorize("hasAuthority('company:recall:audit')")
    public Result auditRecallApplication(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核企业撤回申请，ID: {}, 审核参数：{}", id, params);
        try {
            Integer recallStatus = Integer.valueOf(params.get("recallStatus").toString());
            String recallAuditRemark = params.get("recallAuditRemark") != null ? params.get("recallAuditRemark").toString() : "";

            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }

            int result = companyUserService.auditRecallApplication(id, recallStatus, recallAuditRemark, getCurrentUserId());

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
            log.warn("审核企业撤回申请失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核企业撤回申请时发生异常：{}", e.getMessage(), e);
            return Result.error("审核失败：" + e.getMessage());
        }
    }

    @GetMapping("/recall/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getRecallStatistics() {
        log.info("获取撤回申请统计数据");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("pending", companyUserService.countByRecallStatus(1));
            statistics.put("approved", companyUserService.countByRecallStatus(2));
            statistics.put("rejected", companyUserService.countByRecallStatus(3));
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取撤回申请统计数据失败：{}", e.getMessage(), e);
            return Result.error("获取撤回申请统计数据失败");
        }
    }

    @PostMapping("/tags/update-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "批量更新企业标签")
    public Result updateAllTags() {
        log.info("批量更新企业标签");
        try {
            companyTagService.updateCompanyTags();
            return Result.success("企业标签更新成功");
        } catch (Exception e) {
            log.error("批量更新企业标签失败：{}", e.getMessage(), e);
            return Result.error("批量更新企业标签失败");
        }
    }

    @PostMapping("/{id}/tags/update")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "更新企业标签")
    public Result updateCompanyTag(@PathVariable Long id) {
        log.info("更新企业标签，ID: {}", id);
        try {
            companyTagService.updateCompanyTag(id);
            return Result.success("企业标签更新成功");
        } catch (Exception e) {
            log.error("更新企业标签失败：{}", e.getMessage(), e);
            return Result.error("更新企业标签失败");
        }
    }

    @DeleteMapping("/recall/clear")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "清除撤回申请数据")
    public Result clearRecallData() {
        log.info("清除撤回申请数据");
        try {
            int count = companyUserService.clearRecallData();
            log.info("清除撤回申请数据成功，共清除{}条记录", count);
            return Result.success("清除成功，共清除" + count + "条记录");
        } catch (Exception e) {
            log.error("清除撤回申请数据失败：{}", e.getMessage(), e);
            return Result.error("清除失败");
        }
    }

    @GetMapping("/recall-records")
    @PreAuthorize("hasAuthority('application:withdrawal:view')")
    public Result getRecallRecords(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String contactPerson,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {
        log.info("分页查询企业撤回记录，页码：{}, 每页大小：{}, 企业名称：{}, 联系人：{}, 开始时间：{}, 结束时间：{}",
                page, pageSize, companyName, contactPerson, startTime, endTime);
        try {
            PageResult<CompanyUser> pageResult = companyUserService.findRecallRecordsPage(page, pageSize, companyName, contactPerson, startTime, endTime);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("查询企业撤回记录失败：{}", e.getMessage(), e);
            return Result.error("查询企业撤回记录失败");
        }
    }

    @DeleteMapping("/recall-records/{id}")
    @PreAuthorize("hasAuthority('application:withdrawal:delete')")
    @Log(operationType = "DELETE", module = "WITHDRAWAL_RECORD_MANAGEMENT", description = "删除企业撤回记录")
    public Result deleteRecallRecord(@PathVariable Long id) {
        log.info("删除企业撤回记录，ID: {}", id);
        try {
            int result = companyUserService.deleteRecallRecord(id);
            if (result > 0) {
                return Result.success("删除成功");
            } else {
                return Result.error("删除失败，记录不存在");
            }
        } catch (Exception e) {
            log.error("删除企业撤回记录失败：{}", e.getMessage(), e);
            return Result.error("删除失败");
        }
    }

    @DeleteMapping("/recall-records/batch")
    @PreAuthorize("hasAuthority('application:withdrawal:delete')")
    @Log(operationType = "DELETE", module = "WITHDRAWAL_RECORD_MANAGEMENT", description = "批量删除企业撤回记录")
    public Result batchDeleteRecallRecords(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        log.info("批量删除企业撤回记录，IDs: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("ID 列表不能为空");
            }
            int result = companyUserService.batchDeleteRecallRecords(ids);
            return Result.success("批量删除成功", result);
        } catch (Exception e) {
            log.error("批量删除企业撤回记录失败：{}", e.getMessage(), e);
            return Result.error("批量删除失败");
        }
    }

    /**
     * 重置企业用户密码
     *
     * @param id          企业 ID
     * @param passwordDTO 包含新密码的 DTO 对象
     * @return 操作结果
     */
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "重置企业用户密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:company:reset')")
    public Result resetCompanyUserPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordDTO) {
        log.info("重置企业用户 ID: {} 的密码", id);
        try {
            CompanyUser companyUser = companyUserService.findById(id);
            if (companyUser == null) {
                return Result.error("企业不存在");
            }

            String newPassword = passwordDTO.get("password");
            if (StringUtils.isBlank(newPassword)) {
                newPassword = UUID.randomUUID().toString().substring(0, 8);
            }

            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
            if (!validationResult.isValid()) {
                log.warn("重置企业用户密码失败：新密码不符合复杂度要求 - {}", validationResult.getMessage());
                return Result.error(validationResult.getMessage());
            }

            companyUser.setPassword(newPassword);
            companyUserService.update(companyUser);

            log.info("企业用户 ID: {} 密码重置成功", id);
            return Result.success("密码重置成功", newPassword);
        } catch (Exception e) {
            log.error("重置企业用户密码失败：{}", e.getMessage());
            return Result.error("重置密码失败：" + e.getMessage());
        }
    }

    /**
     * 更新企业用户状态（启用/禁用）
     *
     * @param id 企业 ID
     * @param statusDTO 包含状态的对象 { status: "0" | "1" }
     * @return 操作结果
     */
    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateCompanyUserStatus(@PathVariable Long id, @RequestBody Map<String, String> statusDTO) {
        log.info("更新企业用户 ID: {} 的状态", id);
        try {
            CompanyUser companyUser = companyUserService.findById(id);
            if (companyUser == null) {
                return Result.error("企业不存在");
            }

            String status = statusDTO.get("status");
            if (StringUtils.isBlank(status)) {
                return Result.error("状态不能为空");
            }

            // 设置状态到 companyUser 对象
            companyUser.setStatus(Integer.parseInt(status));
            companyUser.setUpdateTime(new Date());
            companyUserService.update(companyUser);

            log.info("企业用户 ID: {} 状态更新成功，新状态：{}", id, status);
            return Result.success("状态更新成功");
        } catch (BusinessException e) {
            log.warn("更新企业用户状态失败：{}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新企业用户状态失败：{}", e.getMessage());
            return Result.error("更新状态失败");
        }
    }
}
