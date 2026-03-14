package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.exception.BusinessException;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.service.CompanyUserService;
import com.gdmu.utils.ExcelUtils;
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
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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
        log.info("分页获取公司列表，页码: {}, 每页条数: {}, 公司名称: {}, 状态: {}, 撤回状态: {}, 公司标签: {}",
                page, pageSize, companyName, status, recallStatus, companyTag);
        PageResult<CompanyUser> pageResult = companyUserService.findPage(page, pageSize, companyName, status, recallStatus, companyTag);
        return Result.success(pageResult);
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority('user:company:view')")
    public void exportCompanyData(HttpServletResponse response,
                                  @RequestParam(required = false) String companyName,
                                  @RequestParam(required = false) Integer status) {
        log.info("导出企业Excel数据，筛选条件: 公司名称={}, 状态={}", companyName, status);

        try {
            List<CompanyUser> companies = companyUserService.list(companyName, status);
            
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("企业数据");
            
            Row headerRow = sheet.createRow(0);
            String[] headers = {"企业名称", "联系人", "联系电话", "联系邮箱", "公司地址", "公司简介", "状态"};
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
                dataRow.createCell(6).setCellValue(company.getStatus() != null ? getStatusText(company.getStatus()) : "");
                
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
            
            log.info("企业数据Excel导出成功，共导出{}条记录", companies.size());
        } catch (IOException e) {
            log.error("Excel数据导出失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败: " + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        } catch (Exception e) {
            log.error("导出企业数据时发生异常", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败: 系统异常\"}");
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
        log.info("根据ID获取公司信息: {}", id);
        CompanyUser company = companyUserService.findById(id);
        if (company == null) {
            return Result.error("公司不存在");
        }
        return Result.success(company);
    }
    
    @Log(operationType = "ADD", module = "COMPANY_MANAGEMENT", description = "添加公司信息")
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result addCompany(@RequestBody @Validated CompanyUser company) {
        log.info("添加公司信息: {}", company.getCompanyName());
        try {
            String username = company.getCompanyName();
            String password = "123456";
            
            company.setUsername(username);
            company.setPassword(password);
            company.setRole("ROLE_COMPANY");
            
            companyUserService.insert(company);
            
            return Result.success("添加成功");
        } catch (BusinessException e) {
            log.warn("添加公司信息失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
    
    @Log(module = "COMPANY_MANAGEMENT", operationType = "UPDATE", description = "更新公司信息")
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateCompany(@RequestBody CompanyUser company) {
        log.info("更新公司信息，ID: {}", company.getId());
        try {
            companyUserService.update(company);
            return Result.success("更新成功");
        } catch (BusinessException e) {
            log.warn("更新公司信息失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
    
    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "删除公司信息")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result deleteCompany(@PathVariable Long id) {
        log.info("删除公司信息，ID: {}", id);
        try {
            companyUserService.delete(id);
            return Result.success("删除成功");
        } catch (BusinessException e) {
            log.warn("删除公司信息失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
    
    @Log(operationType = "DELETE", module = "COMPANY_MANAGEMENT", description = "批量删除公司信息")
    @DeleteMapping("/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchDeleteCompanies(@RequestBody List<Long> ids) {
        log.info("批量删除公司信息，ID列表: {}", ids);
        try {
            int deletedCount = companyUserService.batchDelete(ids);
            return Result.success("批量删除成功", deletedCount);
        } catch (BusinessException e) {
            log.warn("批量删除公司信息失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
    
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "审核公司注册")
    @PutMapping("/{id}/audit")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result auditCompany(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核公司注册，ID: {}, 审核参数: {}", id, params);
        try {
            Integer auditStatus = Integer.valueOf(params.get("auditStatus").toString());
            String auditRemark = params.get("auditRemark") != null ? params.get("auditRemark").toString() : "";
            
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("公司不存在");
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
            log.warn("审核公司注册失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核公司注册时发生异常: {}", e.getMessage());
            return Result.error("审核失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/audit/pending")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getPendingAuditCompanies(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String companyName,
                                           @RequestParam(required = false) String contactPerson,
                                           @RequestParam(required = false) String contactPhone) {
        log.info("获取待审核公司列表，页码: {}, 每页条数: {}, 公司名称: {}, 联系人: {}, 联系电话: {}", 
                page, pageSize, companyName, contactPerson, contactPhone);
        try {
            PageResult<CompanyUser> pageResult = companyUserService.findPendingAuditPage(page, pageSize, companyName, contactPerson, contactPhone);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取待审核公司列表失败: {}", e.getMessage(), e);
            return Result.error("获取待审核公司列表失败");
        }
    }
    
    private Long getCurrentUserId() {
        return 1L;
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
            log.error("获取统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取统计数据失败");
        }
    }
    
    @Log(operationType = "IMPORT", module = "COMPANY_MANAGEMENT", description = "批量导入公司信息")
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN')")
    public Result importCompanies(@RequestParam("file") MultipartFile file) {
        log.info("批量导入公司信息，文件名: {}", file.getOriginalFilename());
        
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            String fileName = file.getOriginalFilename();
            if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
                return Result.error("文件类型错误，请上传Excel文件");
            }
            
            List<Map<String, Object>> companyDataList = ExcelUtils.readExcel(file);
            
            Map<String, Object> importResult = companyUserService.importFromExcel(companyDataList);
            
            return Result.success("导入完成", importResult);
        } catch (Exception e) {
            log.error("批量导入公司信息失败: {}", e.getMessage(), e);
            return Result.error("导入失败: " + e.getMessage());
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
        log.info("获取待审核撤回申请列表，页码: {}, 每页条数: {}, 公司名称: {}, 联系人: {}, 联系电话: {}, 企业ID: {}", 
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
            log.error("获取待审核撤回申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取待审核撤回申请列表失败");
        }
    }
    
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "审核企业撤回申请")
    @PutMapping("/{id}/recall-audit")
    @PreAuthorize("hasRole('ADMIN')")
    public Result auditRecallApplication(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核企业撤回申请，ID: {}, 审核参数: {}", id, params);
        try {
            Integer recallStatus = Integer.valueOf(params.get("recallStatus").toString());
            String recallAuditRemark = params.get("recallAuditRemark") != null ? params.get("recallAuditRemark").toString() : "";
            
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("公司不存在");
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
            log.warn("审核企业撤回申请失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("审核企业撤回申请时发生异常: {}", e.getMessage(), e);
            return Result.error("审核失败: " + e.getMessage());
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
            log.error("获取撤回申请统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取撤回申请统计数据失败");
        }
    }
    
    @PostMapping("/tags/update-all")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "批量更新公司标签")
    public Result updateAllCompanyTags() {
        log.info("批量更新公司标签");
        try {
            companyTagService.updateCompanyTags();
            return Result.success("公司标签更新成功");
        } catch (Exception e) {
            log.error("批量更新公司标签失败: {}", e.getMessage(), e);
            return Result.error("批量更新公司标签失败");
        }
    }
    
    @PostMapping("/{id}/tags/update")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "UPDATE", module = "COMPANY_MANAGEMENT", description = "更新公司标签")
    public Result updateCompanyTag(@PathVariable Long id) {
        log.info("更新公司标签，ID: {}", id);
        try {
            companyTagService.updateCompanyTag(id);
            return Result.success("公司标签更新成功");
        } catch (Exception e) {
            log.error("更新公司标签失败: {}", e.getMessage(), e);
            return Result.error("更新公司标签失败");
        }
    }
}