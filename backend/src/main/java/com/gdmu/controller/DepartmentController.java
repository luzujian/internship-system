package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.Department;
import com.gdmu.service.DepartmentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 院系控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/departments")
public class DepartmentController {

    @Autowired
    private DepartmentService departmentService;

    // 获取所有院系
    @GetMapping
    @PreAuthorize("hasAuthority('department:view')")
    public Result getAllDepartments() {
        log.info("获取所有院系列表");
        List<Department> departments = departmentService.findAllWithUserCount();
        return Result.success(departments);
    }
    
    // 按名称模糊查询院系
    @GetMapping("/search")
    @PreAuthorize("hasAuthority('department:view')")
    public Result searchDepartmentsByName(@RequestParam String name) {
        log.info("按名称模糊查询院系: {}", name);
        List<Department> departments = departmentService.findByName(name);
        return Result.success(departments);
    }

    // 根据ID获取院系
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('department:view')")
    public Result getDepartmentById(@PathVariable Long id) {
        log.info("根据ID获取院系: {}", id);
        Department department = departmentService.findById(id);
        if (department == null) {
            return Result.error("院系不存在");
        }
        return Result.success(department);
    }

    // 添加院系
    @Log(operationType = "ADD", module = "DEPARTMENT_MANAGEMENT", description = "添加院系")
    @PostMapping
    @PreAuthorize("hasAuthority('department:add')")
    public Result addDepartment(@RequestBody Department department) {
        log.info("添加院系: {}", department.getName());
        try {
            departmentService.addDepartment(department);
            return Result.success("添加成功", department);
        } catch (Exception e) {
            log.warn("添加院系失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 更新院系
    @Log(operationType = "UPDATE", module = "DEPARTMENT_MANAGEMENT", description = "更新院系")
    @PutMapping
    @PreAuthorize("hasAuthority('department:edit')")
    public Result updateDepartment(@RequestBody Department department) {
        log.info("更新院系: {}", department.getName());
        try {
            departmentService.updateDepartment(department);
            return Result.success("更新成功");
        } catch (Exception e) {
            log.warn("更新院系失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 删除院系
    @Log(operationType = "DELETE", module = "DEPARTMENT_MANAGEMENT", description = "删除院系")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('department:delete')")
    public Result deleteDepartment(@PathVariable Long id) {
        log.info("删除院系，ID: {}", id);
        try {
            departmentService.deleteDepartment(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.warn("删除院系失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 下载院系专业导入模板
     */
    @Log(operationType = "DOWNLOAD", module = "DEPARTMENT_MAJOR_MANAGEMENT", description = "下载院系专业导入模板")
    @GetMapping("/majors/import-template")
    @PreAuthorize("hasAuthority('department:view')")
    public void downloadImportTemplate(HttpServletResponse response) {
        log.info("下载院系专业导入模板");

        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("院系专业导入模板");

            // 创建表头行（第 1 行）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"院系名称", "专业名称", "说明"};

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
                if (i < headers.length - 1) {
                    sheet.setColumnWidth(i, 20 * 256);
                } else {
                    // 说明列宽度设置更大
                    sheet.setColumnWidth(i, 40 * 256);
                }
            }

            // 在表头行的最后一列右边添加说明
            Row descRow = sheet.createRow(1);
            Cell descCell = descRow.createCell(2);
            descCell.setCellValue("说明：院系名称、专业名称为必填项，同一院系的专业可以在多行中填写");

            CellStyle descStyle = workbook.createCellStyle();
            descStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            descStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            descStyle.setFont(headerFont);
            descCell.setCellStyle(descStyle);

            String filename = "院系专业导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("院系专业导入模板下载成功");
            } finally {
                try {
                    workbook.close();
                } catch (IOException e) {
                    log.error("关闭工作簿失败：{}", e.getMessage(), e);
                }
            }
        } catch (IOException e) {
            log.error("下载模板失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"下载失败：" + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }
}