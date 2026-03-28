package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.exception.BusinessException;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentUser;
import com.gdmu.service.StudentUserService;
import com.gdmu.utils.JwtUtils;
import com.gdmu.utils.ExcelUtils;
import com.gdmu.utils.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import java.util.Map;
import java.util.UUID;

/**
 * 学生用户控制器
 */
@Slf4j
@RestController
@RequestMapping({"/api/admin/student-users", "/api/admin/students"})
public class StudentUserController {

    @Autowired
    private StudentUserService studentUserService;
    
    @Autowired
    private JwtUtils jwtUtils;

    // 获取所有学生用户（分页）
    @GetMapping
    @PreAuthorize("hasAuthority('user:student:view')")
    public Result getAllStudentUsers(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                     @RequestParam(required = false) String studentId,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false) Integer grade,
                                     @RequestParam(required = false) String majorId,
                                     @RequestParam(required = false) String classId,
                                     @RequestParam(required = false) Integer status,
                                     @RequestParam(required = false) Integer gender,
                                     @RequestParam(required = false) String department) {
        log.info("分页获取学生用户列表，页码: {}, 每页条数: {}, 学号: {}, 姓名: {}, 年级: {}, 专业ID: {}, 班级ID: {}, 状态: {}, 性别: {}, 院系: {}",
                page, pageSize, studentId, name, grade, majorId, classId, status, gender, department);
        PageResult<StudentUser> pageResult = studentUserService.findPage(page, pageSize, studentId, name, grade, majorId, classId, status, gender, department);
        return Result.success(pageResult);
    }

    /**
     * 导出学生数据到Excel文件
     * @param response HTTP响应对象
     * @param studentId 学号筛选条件
     * @param name 姓名筛选条件
     * @param grade 年级筛选条件
     * @param majorId 专业ID筛选条件
     * @param classId 班级ID筛选条件
     * @param status 状态筛选条件
     */
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('user:student:view')")
    public void exportStudentData(HttpServletResponse response,
                                 @RequestParam(required = false) String studentId,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) Integer grade,
                                 @RequestParam(required = false) String majorId,
                                 @RequestParam(required = false) String classId,
                                 @RequestParam(required = false) Integer status) {
        log.info("导出学生Excel数据，筛选条件: 学号={}, 姓名={}, 年级={}, 专业ID={}, 班级ID={}, 状态={}",
                studentId, name, grade, majorId, classId, status);

        try {
            // 获取所有满足条件的学生数据（不使用分页）
            List<StudentUser> students = studentUserService.findAll(studentId, name, grade, majorId, classId, status);
            
            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("学生数据");
            
            // 创建表头行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"学号", "姓名", "年级", "专业", "班级", "入学时间"};
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
            
            // 填充数据行
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            
            for (int i = 0; i < students.size(); i++) {
                StudentUser student = students.get(i);
                Row dataRow = sheet.createRow(i + 1);
                
                // 填充各个字段数据
                dataRow.createCell(0).setCellValue(student.getStudentId() != null ? student.getStudentId() : "");
                dataRow.createCell(1).setCellValue(student.getName() != null ? student.getName() : "");
                dataRow.createCell(2).setCellValue(student.getGrade() != null ? student.getGrade() : 0);
                if (student.getMajorId() != null) {
                    dataRow.createCell(3).setCellValue(student.getMajorId());
                } else {
                    dataRow.createCell(3).setCellValue("");
                }
                
                if (student.getClassId() != null) {
                    dataRow.createCell(4).setCellValue(student.getClassId());
                } else {
                    dataRow.createCell(4).setCellValue("");
                }
                
                // 设置数据行样式
                for (int j = 0; j < headers.length; j++) {
                    dataRow.getCell(j).setCellStyle(dataStyle);
                    // 自动调整列宽
                    sheet.autoSizeColumn(j);
                }
            }
            
            // 设置响应头
            String filename = "学生数据_" + new Date().getTime() + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString());
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");
            
            // 写入响应输出流
            workbook.write(response.getOutputStream());
            workbook.close();
            
            log.info("学生数据Excel导出成功，共导出{}条记录", students.size());
        } catch (IOException e) {
            log.error("Excel数据导出失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败: " + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        } catch (Exception e) {
            log.error("导出学生数据时发生异常", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败: 系统异常\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        }
    }

    // 根据ID获取学生用户
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:student:view')")
    public Result getStudentUserById(@PathVariable Long id) {
        log.info("根据ID获取学生用户: {}", id);
        StudentUser studentUser = studentUserService.findById(id);
        if (studentUser == null) {
            return Result.error("用户不存在");
        }
        // 安全处理：移除密码信息
        studentUser.setPassword(null);
        return Result.success(studentUser);
    }

    // 添加学生用户
    @Log(operationType = "ADD", module = "STUDENT_MANAGEMENT", description = "添加学生用户")
    @PostMapping
    @PreAuthorize("hasAuthority('user:student:add')")
    public Result addStudentUser(@RequestBody @Validated StudentUser studentUser) {
        log.info("添加学生用户: 学号{}", studentUser.getStudentId());
        try {
            // 设置username字段，与studentUserId相同
            if (studentUser.getUsername() == null || studentUser.getUsername().isEmpty()) {
                studentUser.setUsername(studentUser.getStudentId());
            }
            studentUser.setRole("ROLE_STUDENT"); // 确保角色正确
            studentUserService.register(studentUser);
            return Result.success("添加成功");
        } catch (BusinessException e) {
            log.warn("添加学生用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量添加学生用户
    @Log(operationType = "ADD", module = "STUDENT_MANAGEMENT", description = "批量添加学生用户")
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('user:student:add')")
    public Result batchAddStudentUsers(@RequestBody List<StudentUser> studentUsers) {
        log.info("批量添加学生用户，数量: {}", studentUsers.size());
        try {
            int successCount = 0;
            for (StudentUser studentUser : studentUsers) {
                try {
                    // 设置username字段，与studentUserId相同
                    if (studentUser.getUsername() == null || studentUser.getUsername().isEmpty()) {
                        studentUser.setUsername(studentUser.getStudentId());
                    }
                    studentUser.setRole("ROLE_STUDENT"); // 确保角色正确
                    studentUserService.register(studentUser);
                    successCount++;
                } catch (Exception e) {
                    log.warn("添加学生用户 学号{} 失败: {}", studentUser.getStudentId(), e.getMessage());
                }
            }
            return Result.success("批量添加完成，成功: " + successCount + " 个用户");
        } catch (Exception e) {
            log.error("批量添加学生用户失败: {}", e.getMessage());
            return Result.error("批量添加用户失败");
        }
    }

    // 更新学生用户
    @Log(module = "STUDENT_MANAGEMENT")
    @PutMapping
    @PreAuthorize("hasAuthority('user:student:edit')")
    public Result updateStudentUser(@RequestBody StudentUser studentUser) {
        log.info("更新学生用户请求: 学号={}, ID={}, 状态={}", studentUser.getStudentId(), studentUser.getId(), studentUser.getStatus());
        try {
            // 安全处理：不允许通过此接口修改密码
            studentUser.setPassword(null);
            log.debug("更新学生用户前的数据检查: {}", studentUser);
            studentUserService.update(studentUser);
            return Result.success("更新成功", null);
        } catch (BusinessException e) {
            log.warn("业务异常 - 更新学生用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("系统异常 - 更新学生用户失败: {}", e.getMessage(), e);
            return Result.error("系统内部错误: " + e.getMessage());
        }
    }
    
    // 更新学生用户状态
    @Log(module = "STUDENT_MANAGEMENT", operationType = "UPDATE", description = "更新学生用户状态")
    @PutMapping("/status")
    @PreAuthorize("hasAuthority('user:student:edit')")
    public Result updateStudentUserStatus(@RequestBody Map<String, Object> params) {
        log.info("更新学生用户状态: {}", params);
        try {
            Long id = Long.valueOf(params.get("id").toString());
            Integer status = Integer.valueOf(params.get("status").toString());
            
            StudentUser studentUser = new StudentUser();
            studentUser.setId(id);
            studentUser.setStatus(status);
            studentUser.setUpdateTime(new Date());
            
            studentUserService.update(studentUser);
            return Result.success("状态更新成功");
        } catch (Exception e) {
            log.warn("更新学生用户状态失败: {}", e.getMessage());
            return Result.error("状态更新失败: " + e.getMessage());
        }
    }

    // 删除学生用户
    @Log(operationType = "DELETE", module = "STUDENT_MANAGEMENT", description = "删除学生用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:student:delete')")
    public Result deleteStudentUser(@PathVariable Long id) {
        log.info("删除学生用户，ID: {}", id);
        try {
            studentUserService.delete(id);
            return Result.success("删除成功");
        } catch (BusinessException e) {
            log.warn("删除学生用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量删除学生用户
    @Log(operationType = "DELETE", module = "STUDENT_MANAGEMENT", description = "批量删除学生用户")
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('user:student:delete')")
    public Result batchDeleteStudentUsers(@RequestBody List<Long> ids) {
        log.info("批量删除学生用户，ID列表: {}", ids);
        try {
            int deletedCount = studentUserService.batchDeleteStudentUsers(ids);
            return Result.success("批量删除成功", deletedCount);
        } catch (BusinessException e) {
            log.warn("批量删除学生用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 重置学生用户密码
     *
     * @param id          用户ID
     * @param passwordDTO 包含新密码的DTO对象
     * @return 操作结果
     */
    @Log(operationType = "UPDATE", module = "STUDENT_MANAGEMENT", description = "重置学生用户密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:student:reset')")
    public Result resetStudentUserPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordDTO) {
        log.info("重置学生用户ID: {} 的密码", id);
        try {
            StudentUser studentUser = studentUserService.findById(id);
            if (studentUser == null) {
                return Result.error("用户不存在");
            }

            String newPassword = passwordDTO.get("password");
            if (StringUtils.isBlank(newPassword)) {
                newPassword = UUID.randomUUID().toString().substring(0, 8);
            }

            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
            if (!validationResult.isValid()) {
                log.warn("重置学生用户密码失败：新密码不符合复杂度要求 - {}", validationResult.getMessage());
                return Result.error(validationResult.getMessage());
            }

            studentUser.setPassword(newPassword);
            studentUserService.update(studentUser);

            jwtUtils.incrementUserTokenVersion(studentUser.getStudentId());

            log.info("学生用户ID: {} 密码重置成功", id);
            return Result.success("密码重置成功", newPassword);
        } catch (Exception e) {
            log.error("重置学生用户密码失败: {}", e.getMessage());
            return Result.error("重置密码失败：" + e.getMessage());
        }
    }
    
    /**
     * 从Excel导入学生数据
     * @param file 上传的Excel文件
     * @return 导入结果
     */
    @PostMapping("/import")
    @PreAuthorize("hasAuthority('user:student:add')")
    public Result importStudentData(@RequestParam("file") MultipartFile file) {
        log.info("导入学生Excel数据，文件名: {}", file.getOriginalFilename());
        
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            // 使用ExcelUtils工具类读取Excel文件
            List<Map<String, Object>> studentDataList = ExcelUtils.readExcel(file);
            
            // 调用service层处理导入逻辑
            Map<String, Object> importResult = studentUserService.importFromExcel(studentDataList);
            
            return Result.success(importResult);
        } catch (Exception e) {
            log.error("Excel数据导入失败", e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 下载学生导入模板
     */
    @Log(operationType = "DOWNLOAD", module = "STUDENT_MANAGEMENT", description = "下载学生导入模板")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('user:student:add')")
    public void downloadImportTemplate(HttpServletResponse response) {
        log.info("下载学生导入模板");

        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("学生导入模板");

            // 创建表头行（第 1 行）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"学号", "姓名", "性别", "年级", "专业名称", "班级名称", "说明"};

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
            headerStyle.setFont(headerFont);

            // 设置表头单元格
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                if (i < headers.length - 1) {
                    sheet.setColumnWidth(i, 15 * 256);
                } else {
                    // 说明列宽度设置更大
                    sheet.setColumnWidth(i, 40 * 256);
                }
            }

            // 在表头行的最后一列右边添加说明
            Row descRow = sheet.createRow(1);
            Cell descCell = descRow.createCell(6);
            descCell.setCellValue("说明：学号、姓名、性别、年级、专业名称、班级名称为必填项");

            CellStyle descStyle = workbook.createCellStyle();
            descStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            descStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            descStyle.setFont(headerFont);
            descCell.setCellStyle(descStyle);

            // 创建数据样式
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setAlignment(HorizontalAlignment.CENTER);
            dataStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            String filename = "学生导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("学生导入模板下载成功");
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
