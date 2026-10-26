package com.internship.controller;

import com.internship.utils.ExcelExports;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.Class;
import com.internship.service.ClassService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/classes")
public class ClassController {

    @Autowired
    private ClassService classService;

    // 获取所有班级
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR') or hasAuthority('class:view')")
    public Result getAllClasses() {
        log.info("获取所有班级列表");
        try {
            List<Class> classes = classService.findAllWithStudentCount();
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取班级列表失败：{}", e.getMessage(), e);
            return Result.error("获取班级列表失败：" + e.getMessage());
        }
    }

    // 根据 ID 获取班级
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('class:view')")
    public Result getClassById(@PathVariable Long id) {
        log.info("根据 ID 获取班级：{}", id);
        try {
            Class classEntity = classService.findById(id);
            if (classEntity == null) {
                return Result.error("班级不存在");
            }
            return Result.success(classEntity);
        } catch (Exception e) {
            log.error("获取班级详情失败：{}", e.getMessage(), e);
            return Result.error("获取班级详情失败：" + e.getMessage());
        }
    }

    // 根据专业 ID 获取班级
    @GetMapping("/major/{majorId}")
    @PreAuthorize("hasAuthority('class:view')")
    public Result getClassesByMajorId(@PathVariable Long majorId) {
        log.info("根据专业 ID 获取班级：{}", majorId);
        try {
            List<Class> classes = classService.findByMajorId(majorId);
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取专业下的班级列表失败：{}", e.getMessage(), e);
            return Result.error("获取专业下的班级列表失败：" + e.getMessage());
        }
    }

    // 新增班级
    @Log(operationType = "ADD", module = "CLASS_MANAGEMENT", description = "新增班级")
    @PostMapping
    @PreAuthorize("hasAuthority('class:add')")
    public Result addClass(@RequestBody Class classEntity) {
        log.info("新增班级：{}", classEntity.getName());
        try {
            int result = classService.save(classEntity);
            return Result.success("添加班级成功", result);
        } catch (Exception e) {
            log.error("添加班级失败：{}", e.getMessage(), e);
            return Result.error("添加班级失败：" + e.getMessage());
        }
    }

    // 更新班级
    @Log(operationType = "UPDATE", module = "CLASS_MANAGEMENT", description = "更新班级")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('class:edit')")
    public Result updateClass(@PathVariable Long id, @RequestBody Class classEntity) {
        log.info("更新班级：ID={}", id);
        try {
            classEntity.setId(id);
            int result = classService.update(classEntity);
            return Result.success("更新班级成功", result);
        } catch (Exception e) {
            log.error("更新班级失败：{}", e.getMessage(), e);
            return Result.error("更新班级失败：" + e.getMessage());
        }
    }

    // 删除班级
    @Log(operationType = "DELETE", module = "CLASS_MANAGEMENT", description = "删除班级")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('class:delete')")
    public Result deleteClass(@PathVariable Long id) {
        log.info("删除班级：ID={}", id);
        try {
            int result = classService.delete(id);
            return Result.success("删除班级成功", result);
        } catch (Exception e) {
            log.error("删除班级失败：{}", e.getMessage(), e);
            return Result.error("删除班级失败：" + e.getMessage());
        }
    }

    // 导入 Excel 班级数据
    @Log(operationType = "IMPORT", module = "CLASS_MANAGEMENT", description = "导入班级数据")
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('class:add')")
    public Result importExcel(@RequestParam("file") MultipartFile file,
                           @RequestParam(value = "validData", required = false) String validDataStr,
                           @RequestParam(value = "hasValidData", required = false) String hasValidData) {
        log.info("导入班级 Excel 数据，文件名：{}, hasValidData: {}", file.getOriginalFilename(), hasValidData);
        log.info("validData 参数是否为空：{}, 长度：{}", validDataStr == null, validDataStr != null ? validDataStr.length() : 0);
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            if ("true".equals(hasValidData) && validDataStr != null && !validDataStr.isEmpty()) {
                log.info("使用前端处理后的数据导入班级");
                log.info("validData 前 100 个字符：{}", validDataStr.length() > 100 ? validDataStr.substring(0, 100) : validDataStr);
                Map<String, Object> importResult = classService.importFromJson(validDataStr);
                return Result.success("导入完成", importResult);
            }

            log.info("使用传统 Excel 导入方式");
            Map<String, Object> importResult = classService.importExcel(file);
            return Result.success("导入完成", importResult);
        } catch (Exception e) {
            log.error("导入班级数据失败：{}", e.getMessage(), e);
            return Result.error("导入班级数据失败：" + e.getMessage());
        }
    }

    // 导出班级数据到 Excel
    @Log(operationType = "EXPORT", module = "CLASS_MANAGEMENT", description = "导出班级数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('class:view')")
    public void exportExcel(HttpServletResponse response,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Long majorId) throws IOException {
        List<Object[]> rows = classService.getAllClassData().stream()
                .filter(c -> ExcelExports.contains(c.getName(),name) && ExcelExports.contains(ExcelExports.text(c.getGrade()),grade)
                        && (majorId == null || majorId.equals(c.getMajorId())))
                .map(c -> new Object[]{c.getName(),c.getGrade(),c.getMajorName(),c.getStudentCount(),c.getTeacherName(),c.getCreateTime(),c.getUpdateTime()}).toList();
        ExcelExports.write(response,"班级信息",new String[]{"班级名称","年级","专业","学生人数","负责教师","创建时间","更新时间"},rows);
    }

    // 批量删除班级
    @Log(operationType = "DELETE", module = "CLASS_MANAGEMENT", description = "批量删除班级", riskLevel = "HIGH", critical = true)
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('class:delete')")
    public Result batchDeleteClass(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        log.info("批量删除班级，IDs: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                throw new IllegalArgumentException("班级 ID 列表不能为空");
            }
            int result = classService.batchDelete(ids);
            return Result.success("批量删除班级成功", result);
        } catch (Exception e) {
            log.error("批量删除班级失败：{}", e.getMessage(), e);
            return Result.error("批量删除班级失败：" + e.getMessage());
        }
    }

    // 下载班级导入模板
    @Log(operationType = "DOWNLOAD", module = "CLASS_MANAGEMENT", description = "下载班级导入模板")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('class:add')")
    public void downloadImportTemplate(HttpServletResponse response) {
        log.info("下载班级导入模板");

        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("班级导入模板");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"班级名称", "年级", "专业名称", "负责教师", "班级人数", "说明"};

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
                if (i < headers.length - 1) {
                    sheet.setColumnWidth(i, 15 * 256);
                } else {
                    // 说明列宽度设置更大
                    sheet.setColumnWidth(i, 30 * 256);
                }
            }

            // 在第一行下方添加说明
            Row descRow = sheet.createRow(1);
            Cell descCell = descRow.createCell(5);
            descCell.setCellValue("说明：班级名称、年级、专业名称为必填项，负责教师和班级人数为可选项");

            CellStyle descStyle = workbook.createCellStyle();
            descStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            descStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            descStyle.setFont(headerFont);
            descCell.setCellStyle(descStyle);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            String filename = "班级导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("班级导入模板下载成功");
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
