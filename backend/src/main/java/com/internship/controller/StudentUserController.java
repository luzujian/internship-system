package com.internship.controller;

import com.internship.anno.Log;
import com.internship.exception.BusinessException;
import com.internship.entity.Class;
import com.internship.entity.Major;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.StudentUser;
import com.internship.mapper.ClassMapper;
import com.internship.mapper.MajorMapper;
import com.internship.service.StudentUserService;
import com.internship.utils.JwtUtils;
import com.internship.utils.ExcelUtils;
import com.internship.utils.PasswordValidator;
import com.internship.utils.UserCredentialDefaults;
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
import java.util.ArrayList;
import java.util.List;

import java.util.Map;
import java.util.HashMap;

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

    @Autowired
    private MajorMapper majorMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired(required = false)
    private com.internship.service.UserCacheService userCacheService;

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
        log.info("分页获取学生用户列表：page={}, pageSize={}, hasFilters={}",
                page, pageSize, studentId != null || name != null || grade != null || majorId != null
                        || classId != null || status != null || gender != null || department != null);
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
    @Log(operationType = "EXPORT", module = "STUDENT_MANAGEMENT", description = "导出学生数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('user:student:view')")
    public void exportStudentData(HttpServletResponse response,
                                 @RequestParam(required = false) String studentId,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) Integer grade,
                                 @RequestParam(required = false) String majorId,
                                 @RequestParam(required = false) String classId,
                                 @RequestParam(required = false) Integer status) {
        log.info("收到学生 Excel 数据导出请求");

        try {
            // 获取所有满足条件的学生数据（不使用分页）
            List<StudentUser> students = studentUserService.findAll(studentId, name, grade, majorId, classId, status);

            // 构建专业ID -> 专业名称 的映射
            Map<Long, String> majorNameMap = new HashMap<>();
            List<Major> majorList = majorMapper.findAll();
            if (majorList != null) {
                for (Major m : majorList) {
                    if (m.getId() != null && m.getName() != null) {
                        majorNameMap.put(m.getId(), m.getName());
                    }
                }
            }

            // 构建班级ID -> 班级名称 的映射
            Map<Long, String> classNameMap = new HashMap<>();
            List<Class> classList = classMapper.findAll();
            if (classList != null) {
                for (Class c : classList) {
                    if (c.getId() != null && c.getName() != null) {
                        classNameMap.put(c.getId(), c.getName());
                    }
                }
            }
            
            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("学生数据");
            
            // 创建表头行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"学号", "姓名", "年级", "专业", "班级", "手机号", "邮箱"};
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
                    String majorName = majorNameMap.get(student.getMajorId());
                    dataRow.createCell(3).setCellValue(majorName != null ? majorName : "");
                } else {
                    dataRow.createCell(3).setCellValue("");
                }
                
                if (student.getClassId() != null) {
                    String clsName = classNameMap.get(student.getClassId());
                    dataRow.createCell(4).setCellValue(clsName != null ? clsName : "");
                } else {
                    dataRow.createCell(4).setCellValue("");
                }

                dataRow.createCell(5).setCellValue(student.getPhone() != null ? student.getPhone() : "");
                dataRow.createCell(6).setCellValue(student.getEmail() != null ? student.getEmail() : "");

                // 设置数据行样式
                for (int j = 0; j < headers.length; j++) {
                    Cell cell = dataRow.getCell(j);
                    if (cell == null) {
                        cell = dataRow.createCell(j);
                    }
                    cell.setCellStyle(dataStyle);
                }
            }

            // 自动调整列宽（所有数据填充后执行一次即可）
            for (int j = 0; j < headers.length; j++) {
                sheet.autoSizeColumn(j);
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
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:student:add')")
    public Result addStudentUser(@RequestBody @Validated StudentUser studentUser) {
        log.info("添加学生用户: 学号{}", studentUser.getStudentId());
        try {
            String initialPassword = UserCredentialDefaults.PASSWORD;
            studentUser.setPassword(initialPassword);
            // 设置username字段，与studentUserId相同
            if (studentUser.getUsername() == null || studentUser.getUsername().isEmpty()) {
                studentUser.setUsername(studentUser.getStudentId());
            }
            studentUser.setRole("ROLE_STUDENT"); // 确保角色正确
            studentUserService.register(studentUser);
            return Result.success("添加成功", Map.of("initialCredentials",
                    List.of(Map.of("username", studentUser.getUsername(), "password", initialPassword))));
        } catch (BusinessException e) {
            log.warn("添加学生用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量添加学生用户
    @Log(operationType = "ADD", module = "STUDENT_MANAGEMENT", description = "批量添加学生用户")
    @PostMapping("/batch")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:student:add')")
    public Result batchAddStudentUsers(@RequestBody List<StudentUser> studentUsers) {
        log.info("批量添加学生用户，数量: {}", studentUsers.size());
        try {
            int successCount = 0;
            List<Map<String, String>> initialCredentials = new ArrayList<>();
            for (StudentUser studentUser : studentUsers) {
                try {
                    String initialPassword = UserCredentialDefaults.PASSWORD;
                    studentUser.setPassword(initialPassword);
                    // 设置username字段，与studentUserId相同
                    if (studentUser.getUsername() == null || studentUser.getUsername().isEmpty()) {
                        studentUser.setUsername(studentUser.getStudentId());
                    }
                    studentUser.setRole("ROLE_STUDENT"); // 确保角色正确
                    studentUserService.register(studentUser);
                    successCount++;
                    initialCredentials.add(Map.of("username", studentUser.getUsername(), "password", initialPassword));
                } catch (Exception e) {
                    log.warn("添加学生用户 学号{} 失败: {}", studentUser.getStudentId(), e.getMessage());
                }
            }
            return Result.success("批量添加完成，成功: " + successCount + " 个用户",
                    Map.of("initialCredentials", initialCredentials));
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
        log.info("更新学生用户请求: id={}, status={}", studentUser.getId(), studentUser.getStatus());
        try {
            // 安全处理：不允许通过此接口修改密码
            studentUser.setPassword(null);
            log.debug("更新学生用户数据校验完成: id={}", studentUser.getId());
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
        try {
            Long id = Long.valueOf(params.get("id").toString());
            Integer status = Integer.valueOf(params.get("status").toString());
            log.info("更新学生用户状态: id={}, status={}", id, status);
            
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
    @Log(operationType = "DELETE", module = "STUDENT_MANAGEMENT", description = "批量删除学生用户", riskLevel = "HIGH", critical = true)
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('user:student:delete')")
    public Result batchDeleteStudentUsers(@RequestBody List<Long> ids) {
        log.info("批量删除学生用户：count={}", ids != null ? ids.size() : 0);
        try {
            int deletedCount = studentUserService.batchDeleteStudentUsers(ids);
            return Result.success("批量删除成功", deletedCount);
        } catch (BusinessException e) {
            log.warn("批量删除学生用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @Log(operationType = "RESET_PASSWORD", module = "STUDENT_MANAGEMENT", description = "一键重置全部学生用户密码",
            riskLevel = "HIGH", targetType = "STUDENT", critical = true)
    @PostMapping("/reset-password/all")
    @PreAuthorize("hasAuthority('user:student:reset')")
    public Result resetAllStudentUserPasswords() {
        log.info("一键重置全部学生用户密码");
        try {
            List<StudentUser> students = studentUserService.findAll(null, null, null, null, null);
            int resetCount = studentUserService.resetAllPasswords();
            for (StudentUser student : students) {
                invalidateUserCredentials(student.getStudentId(), student.getUsername());
            }
            return Result.success("全部学生用户密码已重置为 123456", Map.of("resetCount", resetCount));
        } catch (Exception e) {
            log.error("一键重置全部学生用户密码失败", e);
            return Result.error("一键重置全部学生用户密码失败：" + e.getMessage());
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
            // 管理员端统一重置口令，服务端固定生效，不信任前端传值。
            String newPassword = UserCredentialDefaults.PASSWORD;

            studentUser.setPassword(newPassword);
            studentUserService.update(studentUser);
            // 清除 Redis 用户缓存，避免被重置用户登录时命中旧密码哈希
            if (userCacheService != null) {
                userCacheService.evictUserCache(studentUser.getStudentId());
            }

            jwtUtils.incrementUserTokenVersion(studentUser.getStudentId());

            log.info("学生用户ID: {} 密码重置成功", id);
            return Result.success("密码重置成功", newPassword);
        } catch (Exception e) {
            log.error("重置学生用户密码失败: {}", e.getMessage());
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
     * 从Excel导入学生数据
     * @param file 上传的Excel文件
     * @return 导入结果
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('user:student:add')")
    @Log(operationType = "IMPORT", module = "STUDENT_MANAGEMENT", description = "批量导入学生数据", riskLevel = "MEDIUM")
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
            String[] headers = {"学号", "姓名", "年级", "专业名称", "班级名称", "说明"};

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
                if (i < headers.length - 1) {
                    sheet.setColumnWidth(i, 15 * 256);
                } else {
                    // 说明列宽度设置更大
                    sheet.setColumnWidth(i, 40 * 256);
                }
            }

            // 在表头行的最后一列右边添加说明
            Row descRow = sheet.createRow(1);
            Cell descCell = descRow.createCell(5);
            descCell.setCellValue("说明：学号、姓名、年级、专业名称、班级名称为必填项");

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
