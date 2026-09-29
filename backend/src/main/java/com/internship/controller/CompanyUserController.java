package com.internship.controller;

import com.internship.anno.Log;
import com.internship.exception.BusinessException;
import com.internship.entity.CompanyUser;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.service.CompanyUserService;
import com.internship.utils.CurrentHolder;
import com.internship.utils.ExcelUtils;
import com.internship.utils.JwtUtils;
import com.internship.utils.PasswordValidator;
import com.internship.utils.UserCredentialDefaults;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.CompanyQualificationMaterial;
import com.internship.utils.RecordAttachmentResponse;
import com.internship.utils.MaterialMetadataRedactor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
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

@Slf4j
@RestController
@RequestMapping("/api/admin/companies")
public class CompanyUserController {

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @GetMapping("/{id}/materials/{kind}/{index}")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:company:audit')")
    @Log(operationType = "DOWNLOAD", module = "COMPANY_MANAGEMENT", description = "下载企业资质材料", riskLevel = "MEDIUM")
    public ResponseEntity<byte[]> downloadCompanyQualificationMaterial(@PathVariable Long id,
                                                                         @PathVariable String kind,
                                                                         @PathVariable int index) {
        CompanyUser company = companyUserService.findById(id);
        if (company == null) return ResponseEntity.notFound().build();
        String storedUrl = CompanyQualificationMaterial.storedUrl(company, kind, index);
        if (storedUrl == null || storedUrl.isBlank()) return ResponseEntity.notFound().build();
        try {
            return RecordAttachmentResponse.ok(aliyunOSSOperator.downloadRecordAttachment(storedUrl, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(403).build();
        } catch (Exception e) {
            log.error("读取企业资质材料失败: companyId={}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.internship.service.CompanyTagService companyTagService;

    @Autowired
    private com.internship.service.PositionCacheService positionCacheService;

    @Autowired
    private com.internship.service.CompanyRelationsService companyRelationsService;

    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired(required = false)
    private com.internship.service.UserCacheService userCacheService;

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
        return Result.success(MaterialMetadataRedactor.companyPage(pageResult));
    }

    @GetMapping("/tags")
    @PreAuthorize("hasAuthority('user:company:view')")
    public Result getCompanyTags() {
        try {
            List<String> tags = companyUserService.getDistinctTags();
            return Result.success(tags);
        } catch (Exception e) {
            log.error("获取企业标签列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业标签列表失败");
        }
    }

    @Log(operationType = "EXPORT", module = "COMPANY_MANAGEMENT", description = "导出企业数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('user:company:view')")
    public void exportCompanyData(HttpServletResponse response,
                                  @RequestParam(required = false) String companyName,
                                  @RequestParam(required = false) Integer status) {
        log.info("导出企业 Excel 数据，筛选条件：企业名称={}, 状态={}", companyName, status);

        try {
            List<CompanyUser> companies = companyUserService.list(companyName, status);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("企业数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"企业名称", "联系人", "联系电话", "联系邮箱", "个人邮箱", "企业地址", "标签"};
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
                dataRow.createCell(4).setCellValue(company.getEmail() != null ? company.getEmail() : "");
                dataRow.createCell(5).setCellValue(company.getAddress() != null ? company.getAddress() : "");
                dataRow.createCell(6).setCellValue(company.getCompanyTag() != null ? company.getCompanyTag() : "");

                for (int j = 0; j < headers.length; j++) {
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

    @GetMapping("/{id:\\d+}")
    @PreAuthorize("hasAuthority('user:company:view')")
    public Result getCompanyById(@PathVariable Long id) {
        log.info("根据 ID 获取企业信息：{}", id);
        CompanyUser company = companyUserService.findById(id);
        if (company == null) {
            return Result.error("企业不存在");
        }
        // 与教师端详情一致：补充校友信息、校内对接人、走访情况
        com.internship.dto.TeacherCompanyDTO decorated = companyRelationsService.findCompanyDetail(id);
        Map<String, Object> data = objectMapper
                .convertValue(MaterialMetadataRedactor.company(company), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        if (decorated != null) {
            data.put("alumniMembers", decorated.getAlumniMembers());
            data.put("schoolLiaisons", decorated.getSchoolLiaisons());
            data.put("visitSummary", decorated.getVisitSummary());
        }
        return Result.success(data);
    }

    @Log(operationType = "ADD", module = "COMPANY_MANAGEMENT", description = "添加企业信息")
    @PostMapping
    @PreAuthorize("hasAuthority('user:company:add')")
    public Result addCompany(@RequestBody @Validated CompanyUser company) {
        log.info("添加企业信息：{}", company.getCompanyName());
        try {
            // 登录账号自动等于企业名称（与导入、审核逻辑保持一致）
            String username = company.getCompanyName();
            if (companyUserService.findByUsername(username) != null) {
                return Result.error("企业名称已存在");
            }
            String initialPassword = UserCredentialDefaults.PASSWORD;
            company.setPassword(initialPassword);

            company.setUsername(username);
            company.setRole("ROLE_COMPANY");
            // 管理员直接新增的企业默认正常可用，无需再审核
            company.setStatus(1);
            company.setAuditStatus(1);
            company.setAuditTime(new Date());

            companyUserService.insert(company);

            return Result.success("添加成功", Map.of("initialCredentials",
                    List.of(Map.of("username", company.getUsername(), "password", initialPassword))));
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
            // 管理员更新企业信息后同步重建自动标签（挂牌实习基地/接受兜底/合作方式），保留手动标签
            companyTagService.updateCompanyTag(company.getId());
            // 企业信息影响岗位展示（实习基地标签等），清除岗位列表缓存
            positionCacheService.clearPositionsCache();
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

    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "批量删除企业信息", riskLevel = "HIGH", critical = true)
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
        log.info("审核企业注册：id={}", id);
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
                company.setUsername(username);
                // 注册时已经保存不可预测的密码；企业通过原登记邮箱验证后自行设置密码。
                company.setPassword(null);
                company.setRole("ROLE_COMPANY");

                companyUserService.update(company);
                companyTagService.updateCompanyTag(id);
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
            PageResult<CompanyUser> pageResult = companyUserService.findPendingAuditPage(page, pageSize, companyName, contactPerson, contactPhone, 0);
            return Result.success(MaterialMetadataRedactor.companyPage(pageResult));
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
            String[] headers = {"企业名称", "联系人", "联系电话", "企业地址"};

            // 创建表头样式
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
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
            int[] columnWidths = {30, 15, 15, 50};
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
                "1. 企业名称、联系人、联系电话、企业地址为必填项",
                "2. 企业名称不能与现有企业重名",
                "3. 联系电话请填写 11 位手机号码",
                "4. 导入后请安全交付仅在导入结果中显示一次的随机初始密码"
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
            return Result.success(MaterialMetadataRedactor.companyPage(pageResult));
        } catch (Exception e) {
            log.error("获取待审核撤回申请列表失败：{}", e.getMessage(), e);
            return Result.error("获取待审核撤回申请列表失败");
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
            return Result.success(MaterialMetadataRedactor.companyPage(pageResult));
        } catch (Exception e) {
            log.error("查询企业撤回记录失败：{}", e.getMessage(), e);
            return Result.error("查询企业撤回记录失败");
        }
    }

    /**
     * 导出企业撤回记录到 Excel
     */
    @Log(operationType = "EXPORT", module = "COMPANY_MANAGEMENT", description = "导出企业撤回记录", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/recall-records/export")
    @PreAuthorize("hasAuthority('application:withdrawal:view')")
    public void exportRecallRecords(HttpServletResponse response,
                                    @RequestParam(required = false) String companyName,
                                    @RequestParam(required = false) String contactPerson,
                                    @RequestParam(required = false) String startTime,
                                    @RequestParam(required = false) String endTime) {
        log.info("导出企业撤回记录，企业名称：{}, 联系人：{}, 开始时间：{}, 结束时间：{}",
                companyName, contactPerson, startTime, endTime);
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("企业撤回申请记录_" + System.currentTimeMillis(), StandardCharsets.UTF_8) + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);

            List<CompanyUser> records = companyUserService.findRecallRecords(companyName, contactPerson, startTime, endTime);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("企业撤回申请记录");

            // 表头样式
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

            String[] headers = {"记录 ID", "企业名称", "联系人", "联系电话", "邮箱", "撤回原因", "申请时间", "审核状态", "审核时间", "审核备注"};
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 4500);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            int rowNum = 1;
            for (CompanyUser record : records) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(record.getId() != null ? record.getId().toString() : "");
                row.createCell(1).setCellValue(record.getCompanyName() != null ? record.getCompanyName() : "");
                row.createCell(2).setCellValue(record.getContactPerson() != null ? record.getContactPerson() : "");
                row.createCell(3).setCellValue(record.getContactPhone() != null ? record.getContactPhone() : "");
                row.createCell(4).setCellValue(record.getContactEmail() != null ? record.getContactEmail() : "");
                row.createCell(5).setCellValue(record.getRecallReason() != null ? record.getRecallReason() : "");
                row.createCell(6).setCellValue(record.getRecallApplyTime() != null ? dateFormat.format(record.getRecallApplyTime()) : "");
                row.createCell(7).setCellValue(getAuditStatusText(record.getRecallStatus()));
                row.createCell(8).setCellValue(record.getRecallAuditTime() != null ? dateFormat.format(record.getRecallAuditTime()) : "");
                row.createCell(9).setCellValue(record.getRecallAuditRemark() != null ? record.getRecallAuditRemark() : "");
            }

            workbook.write(response.getOutputStream());
            workbook.close();
            log.info("企业撤回申请记录导出成功，共 {} 条记录", records.size());
        } catch (Exception e) {
            log.error("导出企业撤回申请记录失败：{}", e.getMessage(), e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败：" + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        }
    }

    private String getAuditStatusText(Integer recallStatus) {
        if (recallStatus == null) return "";
        switch (recallStatus) {
            case 1: return "待审核";
            case 2: return "已撤回";
            case 3: return "已拒绝";
            default: return "未知";
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
    @Log(operationType = "DELETE", module = "WITHDRAWAL_RECORD_MANAGEMENT", description = "批量删除企业撤回记录", riskLevel = "HIGH", critical = true)
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

    @Log(operationType = "RESET_PASSWORD", module = "COMPANY_MANAGEMENT", description = "一键重置全部企业用户密码",
            riskLevel = "HIGH", targetType = "COMPANY", critical = true)
    @PostMapping("/reset-password/all")
    @PreAuthorize("hasAuthority('user:company:reset')")
    public Result resetAllCompanyUserPasswords() {
        log.info("一键重置全部企业用户密码");
        try {
            List<CompanyUser> companies = companyUserService.findAll();
            int resetCount = companyUserService.resetAllPasswords();
            for (CompanyUser company : companies) {
                invalidateUserCredentials(company.getUsername(), company.getCompanyName());
            }
            return Result.success("全部企业用户密码已重置为 123456", Map.of("resetCount", resetCount));
        } catch (Exception e) {
            log.error("一键重置全部企业用户密码失败", e);
            return Result.error("一键重置全部企业用户密码失败：" + e.getMessage());
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
            // 管理员端统一重置口令，服务端固定生效，不信任前端传值。
            String newPassword = UserCredentialDefaults.PASSWORD;

            companyUser.setPassword(newPassword);
            companyUserService.update(companyUser);
            // 清除 Redis 用户缓存，避免被重置用户登录时命中旧密码哈希
            if (userCacheService != null) {
                userCacheService.evictUserCache(companyUser.getUsername());
            }

            jwtUtils.incrementUserTokenVersion(companyUser.getUsername());

            log.info("企业用户 ID: {} 密码重置成功", id);
            return Result.success("密码重置成功，请通知企业用户使用新密码重新登录", newPassword);
        } catch (Exception e) {
            log.error("重置企业用户密码失败：{}", e.getMessage());
            return Result.error("重置密码失败：" + e.getMessage());
        }
    }

    private void invalidateUserCredentials(String... usernames) {
        for (String username : usernames) {
            if (StringUtils.isNotBlank(username)) {
                if (userCacheService != null) {
                    userCacheService.evictUserCache(username);
                }
                jwtUtils.incrementUserTokenVersion(username);
            }
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
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "更新企业用户状态")
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
