package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Position;
import com.gdmu.entity.PositionCategory;
import com.gdmu.entity.Result;
import com.gdmu.service.PositionCategoryService;
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
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/position-categories")
public class PositionCategoryController {

    @Autowired
    private PositionCategoryService positionCategoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getAllCategories(@RequestParam(required = false) String name) {
        log.info("获取岗位类别列表，名称：{}", name);
        try {
            List<PositionCategory> categories;
            if (name != null && !name.trim().isEmpty()) {
                categories = positionCategoryService.list(name);
            } else {
                categories = positionCategoryService.findAll();
            }
            return Result.success(categories);
        } catch (Exception e) {
            log.error("获取岗位类别列表失败：{}", e.getMessage(), e);
            return Result.error("获取岗位类别列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getCategoryById(@PathVariable Long id) {
        log.info("根据 ID 获取岗位类别：{}", id);
        try {
            PositionCategory category = positionCategoryService.findById(id);
            if (category == null) {
                return Result.error("岗位类别不存在");
            }
            return Result.success(category);
        } catch (Exception e) {
            log.error("获取岗位类别详情失败：{}", e.getMessage(), e);
            return Result.error("获取岗位类别详情失败：" + e.getMessage());
        }
    }

    @GetMapping("/{id}/positions")
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getPositionsByCategoryId(@PathVariable Long id) {
        log.info("根据类别 ID 获取岗位列表：{}", id);
        try {
            List<Position> positions = positionCategoryService.getPositionsByCategoryId(id);
            return Result.success(positions);
        } catch (Exception e) {
            log.error("获取岗位列表失败：{}", e.getMessage(), e);
            return Result.error("获取岗位列表失败：" + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasAuthority('position-category:view')")
    public Result getStatistics() {
        log.info("获取岗位类别统计数据");
        try {
            Map<String, Object> statistics = positionCategoryService.getCategoryStatistics();
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败：{}", e.getMessage(), e);
            return Result.error("获取统计数据失败：" + e.getMessage());
        }
    }

    @Log(operationType = "ADD", module = "POSITION_CATEGORY_MANAGEMENT", description = "新增岗位类别")
    @PostMapping
    @PreAuthorize("hasAuthority('position-category:add')")
    public Result addCategory(@RequestBody PositionCategory category) {
        log.info("新增岗位类别：{}", category.getName());
        try {
            positionCategoryService.insert(category);
            return Result.success("添加岗位类别成功", category);
        } catch (Exception e) {
            log.error("添加岗位类别失败：{}", e.getMessage(), e);
            return Result.error("添加岗位类别失败：" + e.getMessage());
        }
    }

    @Log(operationType = "UPDATE", module = "POSITION_CATEGORY_MANAGEMENT", description = "更新岗位类别")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('position-category:edit')")
    public Result updateCategory(@PathVariable Long id, @RequestBody PositionCategory category) {
        log.info("更新岗位类别：ID={}", id);
        try {
            category.setId(id);
            int result = positionCategoryService.update(category);
            return Result.success("更新岗位类别成功", result);
        } catch (Exception e) {
            log.error("更新岗位类别失败：{}", e.getMessage(), e);
            return Result.error("更新岗位类别失败：" + e.getMessage());
        }
    }

    @Log(operationType = "DELETE", module = "POSITION_CATEGORY_MANAGEMENT", description = "删除岗位类别")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('position-category:delete')")
    public Result deleteCategory(@PathVariable Long id) {
        log.info("删除岗位类别：ID={}", id);
        try {
            int result = positionCategoryService.delete(id);
            return Result.success("删除岗位类别成功", result);
        } catch (Exception e) {
            log.error("删除岗位类别失败：{}", e.getMessage(), e);
            return Result.error("删除岗位类别失败：" + e.getMessage());
        }
    }

    /**
     * 下载岗位类别导入模板
     */
    @Log(operationType = "DOWNLOAD", module = "POSITION_CATEGORY_MANAGEMENT", description = "下载岗位类别导入模板")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('position-category:add')")
    public void downloadImportTemplate(HttpServletResponse response) {
        log.info("下载岗位类别导入模板");

        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("岗位类别导入模板");

            // 创建表头行（第 1 行）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"类别名称", "岗位名称", "岗位要求", "说明"};

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
            Cell descCell = descRow.createCell(3);
            descCell.setCellValue("说明：类别名称、岗位名称为必填项，岗位要求为可选项");

            CellStyle descStyle = workbook.createCellStyle();
            descStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            descStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            descStyle.setFont(headerFont);
            descCell.setCellStyle(descStyle);

            String filename = "岗位类别导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("岗位类别导入模板下载成功");
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
