package com.internship.controller;

import com.internship.utils.ExcelExports;

import com.internship.anno.Log;
import com.internship.entity.Position;
import com.internship.entity.PositionCategory;
import com.internship.entity.Result;
import com.internship.service.PositionCategoryService;
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

    /**
     * 公开接口：获取所有岗位类别（企业端发布岗位时使用）
     * 不需要权限，任何登录用户都可以访问
     */
    @GetMapping("/public")
    public Result getAllCategoriesPublic() {
        log.info("公开获取所有岗位类别");
        try {
            List<PositionCategory> categories = positionCategoryService.findAll();
            return Result.success(categories);
        } catch (Exception e) {
            log.error("获取岗位类别列表失败：{}", e.getMessage(), e);
            return Result.error("获取岗位类别列表失败：" + e.getMessage());
        }
    }

    /**
     * 公开接口：根据类别ID获取岗位列表（企业端发布岗位时使用）
     * 不需要权限，任何登录用户都可以访问
     */
    @GetMapping("/public/{id}/positions")
    public Result getPositionsByCategoryIdPublic(@PathVariable Long id) {
        log.info("公开获取类别 ID {} 下的岗位列表", id);
        try {
            List<Position> positions = positionCategoryService.getPositionsByCategoryId(id);
            return Result.success(positions);
        } catch (Exception e) {
            log.error("获取岗位列表失败：{}", e.getMessage(), e);
            return Result.error("获取岗位列表失败：" + e.getMessage());
        }
    }

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
     * 导出岗位类别数据到 Excel
     */
    @Log(operationType = "EXPORT", module = "POSITION_CATEGORY_MANAGEMENT", description = "导出岗位类别数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('position-category:view')")
    public void exportCategoryData(HttpServletResponse response,
            @RequestParam(required = false) String categoryName,
            @RequestParam(required = false) String positionName,
            @RequestParam(required = false) Long categoryId) throws IOException {
        List<Position> positions = positionCategoryService.getAllPositions();
        List<Object[]> rows = new java.util.ArrayList<>();
        for (PositionCategory category : positionCategoryService.findAll()) {
            if (categoryId != null && !categoryId.equals(category.getId())) continue;
            if (!ExcelExports.contains(category.getName(),categoryName)) continue;
            List<Position> all = positions.stream().filter(p -> category.getId().equals(p.getCategoryId())).toList();
            List<Position> matched = all.stream().filter(p -> ExcelExports.contains(p.getPositionName(),positionName)).toList();
            for (Position p : matched) rows.add(new Object[]{category.getName(),all.size(),category.getCreateTime(),
                    p.getPositionName(),p.getRequirements(),p.getCreateTime(),p.getUpdateTime()});
            if (matched.isEmpty() && (positionName == null || positionName.isBlank()))
                rows.add(new Object[]{category.getName(),all.size(),category.getCreateTime(),"","",null,null});
        }
        ExcelExports.write(response,"岗位类别数据",new String[]{"类别名称","岗位数量","类别创建时间","岗位名称","岗位要求","发布时间","更新时间"},rows);
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
            headerFont.setFontHeightInPoints((short) 11);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
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
