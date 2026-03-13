package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.Class;
import com.gdmu.service.ClassService;
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
    @PreAuthorize("hasAuthority('class:view')")
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
    @Log(operationType = "EXPORT", module = "CLASS_MANAGEMENT", description = "导出班级数据")
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('class:view')")
    public void exportExcel(HttpServletResponse response) {
        log.info("导出班级 Excel 数据");
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("班级数据" + System.currentTimeMillis(), StandardCharsets.UTF_8) + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);

            List<Class> classes = classService.getAllClassData();

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("班级数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"班级名称", "专业 ID", "年级", "学生数量", "负责教师"};
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
                sheet.autoSizeColumn(i);
            }

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            for (int i = 0; i < classes.size(); i++) {
                Class classEntity = classes.get(i);
                Row dataRow = sheet.createRow(i + 1);

                Cell nameCell = dataRow.createCell(0);
                nameCell.setCellValue(classEntity.getName());
                nameCell.setCellStyle(dataStyle);

                Cell majorIdCell = dataRow.createCell(1);
                if (classEntity.getMajorId() != null) {
                    majorIdCell.setCellValue(classEntity.getMajorId());
                }
                majorIdCell.setCellStyle(dataStyle);

                Cell gradeCell = dataRow.createCell(2);
                if (classEntity.getGrade() != null) {
                    gradeCell.setCellValue(classEntity.getGrade());
                }
                gradeCell.setCellStyle(dataStyle);

                Cell studentCountCell = dataRow.createCell(3);
                if (classEntity.getStudentCount() != null) {
                    studentCountCell.setCellValue(classEntity.getStudentCount());
                }
                studentCountCell.setCellStyle(dataStyle);

                Cell teacherCell = dataRow.createCell(4);
                if (classEntity.getTeacherName() != null) {
                    teacherCell.setCellValue(classEntity.getTeacherName());
                } else {
                    teacherCell.setCellValue("");
                }
                teacherCell.setCellStyle(dataStyle);
            }

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("导出班级 Excel 数据成功，共导出 {} 条数据", classes.size());
            } catch (IOException e) {
                log.error("导出班级 Excel 数据失败（流操作异常）: {}", e.getMessage(), e);
                throw new RuntimeException("导出班级 Excel 数据失败：" + e.getMessage());
            } finally {
                try {
                    if (workbook != null) {
                        workbook.close();
                    }
                } catch (IOException e) {
                    log.error("关闭工作簿失败：{}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("导出班级数据失败：{}", e.getMessage(), e);
            throw new RuntimeException("导出班级数据失败：" + e.getMessage());
        }
    }

    // 批量删除班级
    @Log(operationType = "DELETE", module = "CLASS_MANAGEMENT", description = "批量删除班级")
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
