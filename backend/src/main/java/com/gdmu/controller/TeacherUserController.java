package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.exception.BusinessException;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.Department;
import com.gdmu.entity.TeacherUser;
import com.gdmu.service.DepartmentService;
import com.gdmu.service.TeacherUserService;
import com.gdmu.utils.ExcelUtils;
import com.gdmu.utils.JwtUtils;
import com.gdmu.utils.PasswordValidator;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 教师用户控制器
 */
@Slf4j
@RestController
@RequestMapping({"/api/admin/teacher-users", "/api/admin/teachers"})
public class TeacherUserController {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private DepartmentService departmentService;
    
    @Autowired
    private JwtUtils jwtUtils;

    // 获取所有教师用户（分页，支持多条件查询）
    @GetMapping
    @PreAuthorize("hasAuthority('user:teacher:view')")
    public Result getAllTeacherUsers(@RequestParam(defaultValue = "1") Integer page,
                                     @RequestParam(defaultValue = "10") Integer pageSize,
                                     @RequestParam(required = false) String teacherUserId,
                                     @RequestParam(required = false) String name,
                                     @RequestParam(required = false) String departmentId,
                                     @RequestParam(required = false) Integer status,
                                     @RequestParam(required = false) Integer gender,
                                     @RequestParam(required = false) String teacherType) {
        log.info("分页获取教师用户列表，页码: {}, 每页条数: {}, 工号: {}, 姓名: {}, 院系ID: {}, 状态: {}, 性别: {}, 教师类型: {}", 
                page, pageSize, teacherUserId, name, departmentId, status, gender, teacherType);
        
        Map<String, Object> params = new HashMap<>();
        if (StringUtils.isNotBlank(teacherUserId)) {
            params.put("teacherUserId", teacherUserId);
        }
        if (StringUtils.isNotBlank(name)) {
            params.put("name", name);
        }
        if (StringUtils.isNotBlank(departmentId)) {
            params.put("departmentId", departmentId);
        }
        if (status != null) {
            params.put("status", status);
        }
        if (gender != null) {
            params.put("gender", gender);
        }
        if (StringUtils.isNotBlank(teacherType)) {
            params.put("teacherType", teacherType);
        }
        
        // 如果没有查询条件，使用普通分页查询；否则使用条件分页查询
        PageResult<TeacherUser> pageResult;
        if (params.isEmpty()) {
            pageResult = teacherUserService.findPage(page, pageSize);
        } else {
            pageResult = teacherUserService.findPageByCondition(page, pageSize, params);
        }
        
        return Result.success(pageResult);
    }

    // 根据ID获取教师用户
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:teacher:view')")
    public Result getTeacherUserById(@PathVariable Long id) {
        log.info("根据ID获取教师用户: {}", id);
        TeacherUser teacherUser = teacherUserService.findById(id);
        if (teacherUser == null) {
            return Result.error("用户不存在");
        }
        // 安全处理：移除密码信息
        teacherUser.setPassword(null);
        return Result.success(teacherUser);
    }

    // 添加教师用户
    @Log(operationType = "ADD", module = "TEACHER_MANAGEMENT", description = "添加教师用户")
    @PostMapping
    @PreAuthorize("hasAuthority('user:teacher:add')")
    public Result addTeacherUser(@RequestBody @Validated TeacherUser teacherUser) {
        log.info("添加教师用户: {}", teacherUser.getTeacherUserId());
        try {
            // 设置username字段，与teacherUserId相同
            if (teacherUser.getUsername() == null || teacherUser.getUsername().isEmpty()) {
                teacherUser.setUsername(teacherUser.getTeacherUserId());
            }
            teacherUser.setRole("ROLE_TEACHER"); // 确保角色正确
            teacherUserService.register(teacherUser);
            return Result.success("添加成功");
        } catch (BusinessException e) {
            log.warn("添加教师用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量添加教师用户
    @Log(operationType = "ADD", module = "TEACHER_MANAGEMENT", description = "批量添加教师用户")
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('user:teacher:add')")
    public Result batchAddTeacherUsers(@RequestBody List<TeacherUser> teacherUsers) {
        log.info("批量添加教师用户，数量: {}", teacherUsers.size());
        try {
            int successCount = 0;
            for (TeacherUser teacherUser : teacherUsers) {
                try {
                    // 设置username字段，与teacherUserId相同
                    if (teacherUser.getUsername() == null || teacherUser.getUsername().isEmpty()) {
                        teacherUser.setUsername(teacherUser.getTeacherUserId());
                    }
                    teacherUser.setRole("ROLE_TEACHER"); // 确保角色正确
                    teacherUserService.register(teacherUser);
                    successCount++;
                } catch (Exception e) {
                    log.warn("添加教师用户 {} 失败: {}", teacherUser.getTeacherUserId(), e.getMessage());
                }
            }
            return Result.success("批量添加完成，成功: " + successCount + " 个用户");
        } catch (Exception e) {
            log.error("批量添加教师用户失败: {}", e.getMessage());
            return Result.error("批量添加用户失败");
        }
    }

    // 更新教师用户
    @Log(module = "TEACHER_MANAGEMENT")
    @PutMapping
    @PreAuthorize("hasAuthority('user:teacher:edit')")
    public Result updateTeacherUser(@RequestBody TeacherUser teacherUser) {
        log.info("更新教师用户: ID={}, 教师编号={}", teacherUser.getId(), teacherUser.getTeacherUserId());
        try {
            // 安全处理：不允许通过此接口修改密码
            teacherUser.setPassword(null);
            teacherUserService.update(teacherUser);
            return Result.success("更新成功");
        } catch (BusinessException e) {
            log.warn("更新教师用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }
    
    // 更新教师用户状态
    @Log(module = "TEACHER_MANAGEMENT", operationType = "UPDATE", description = "更新教师用户状态")
    @PutMapping("/status")
    @PreAuthorize("hasAuthority('user:teacher:edit')")
    public Result updateTeacherUserStatus(@RequestBody Map<String, Object> params) {
        log.info("更新教师用户状态: {}", params);
        try {
            Long id = Long.valueOf(params.get("id").toString());
            Integer status = Integer.valueOf(params.get("status").toString());
            
            TeacherUser teacherUser = new TeacherUser();
            teacherUser.setId(id);
            teacherUser.setStatus(status); // 正确更新status字段
            teacherUser.setUpdateTime(new Date());
            
            teacherUserService.update(teacherUser);
            return Result.success("状态更新成功");
        } catch (Exception e) {
            log.warn("更新教师用户状态失败: {}", e.getMessage());
            return Result.error("状态更新失败: " + e.getMessage());
        }
    }

    // 删除教师用户
    @Log(operationType = "DELETE", module = "TEACHER_MANAGEMENT", description = "删除教师用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:teacher:delete')")
    public Result deleteTeacherUser(@PathVariable Long id) {
        log.info("删除教师用户，ID: {}", id);
        try {
            teacherUserService.delete(id);
            return Result.success("删除成功");
        } catch (BusinessException e) {
            log.warn("删除教师用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量删除教师用户
    @Log(operationType = "DELETE", module = "TEACHER_MANAGEMENT", description = "批量删除教师用户")
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('user:teacher:delete')")
    public Result batchDeleteTeacherUsers(@RequestBody List<Long> ids) {
        log.info("批量删除教师用户，ID列表: {}", ids);
        try {
            int deletedCount = teacherUserService.batchDelete(ids);
            return Result.success("批量删除成功，共删除" + deletedCount + "条记录");
        } catch (BusinessException e) {
            log.warn("批量删除教师用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 重置教师用户密码
     *
     * @param id          用户ID
     * @param passwordDTO 包含新密码的DTO对象
     * @return 操作结果
     */
    @Log(operationType = "UPDATE", module = "TEACHER_MANAGEMENT", description = "重置教师用户密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:teacher:reset')")
    public Result resetTeacherUserPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordDTO) {
        log.info("重置教师用户ID: {} 的密码", id);
        try {
            TeacherUser teacherUser = teacherUserService.findById(id);
            if (teacherUser == null) {
                return Result.error("用户不存在");
            }

            String newPassword = passwordDTO.get("password");
            if (StringUtils.isBlank(newPassword)) {
                newPassword = UUID.randomUUID().toString().substring(0, 8);
            }

            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
            if (!validationResult.isValid()) {
                log.warn("重置教师用户密码失败：新密码不符合复杂度要求 - {}", validationResult.getMessage());
                return Result.error(validationResult.getMessage());
            }

            teacherUser.setPassword(newPassword);
            teacherUserService.update(teacherUser);

            jwtUtils.incrementUserTokenVersion(teacherUser.getTeacherUserId());

            log.info("教师用户ID: {} 密码重置成功", id);
            return Result.success("密码重置成功", newPassword);
        } catch (Exception e) {
            log.error("重置教师用户密码失败: {}", e.getMessage());
            return Result.error("重置密码失败：" + e.getMessage());
        }
    }

    /**
     * 导出教师数据到Excel文件
     * @param response HTTP响应对象
     * @param teacherUserId 工号筛选条件
     * @param name 姓名筛选条件
     * @param departmentId 院系ID筛选条件
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void exportTeacherData(HttpServletResponse response,
                                 @RequestParam(required = false) String teacherUserId,
                                 @RequestParam(required = false) String name,
                                 @RequestParam(required = false) String departmentId) {
        log.info("导出教师Excel数据，筛选条件: 工号={}, 姓名={}, 院系ID={}",
                teacherUserId, name, departmentId);

        try {
            // 设置响应头
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("UTF-8");
            String fileName = URLEncoder.encode("教师数据" + System.currentTimeMillis(), "UTF-8") + ".xlsx";
            response.setHeader("Content-disposition", "attachment;filename=" + fileName);
            
            // 获取所有满足条件的教师数据（不使用分页）
            List<TeacherUser> teachers = teacherUserService.findAll(teacherUserId, name, departmentId);
            
            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("教师数据");
            
            // 创建表头行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"工号", "姓名", "性别", "身份", "院系", "手机号"};
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
            
            for (int i = 0; i < teachers.size(); i++) {
                TeacherUser teacher = teachers.get(i);
                Row dataRow = sheet.createRow(i + 1);
                
                Cell cell0 = dataRow.createCell(0);
                cell0.setCellValue(teacher.getTeacherUserId());
                cell0.setCellStyle(dataStyle);
                
                Cell cell1 = dataRow.createCell(1);
                cell1.setCellValue(teacher.getName());
                cell1.setCellStyle(dataStyle);
                
                Cell cell2 = dataRow.createCell(2);
                cell2.setCellValue(teacher.getGender() != null ? (teacher.getGender() == 1 ? "男" : "女") : "");
                cell2.setCellStyle(dataStyle);
                
                Cell cell3 = dataRow.createCell(3);
                String teacherType = teacher.getTeacherType();
                if (teacherType != null && !teacherType.isEmpty()) {
                    switch (teacherType) {
                        case "COLLEGE":
                            cell3.setCellValue("学院教师");
                            break;
                        case "DEPARTMENT":
                            cell3.setCellValue("系室教师");
                            break;
                        case "COUNSELOR":
                            cell3.setCellValue("辅导员");
                            break;
                        default:
                            cell3.setCellValue(teacherType);
                    }
                } else {
                    cell3.setCellValue("");
                }
                cell3.setCellStyle(dataStyle);
                
                Cell cell4 = dataRow.createCell(4);
                if (teacher.getDepartmentId() != null && !teacher.getDepartmentId().isEmpty()) {
                    try {
                        Long deptId = Long.parseLong(teacher.getDepartmentId());
                        Department department = departmentService.findById(deptId);
                        if (department != null) {
                            cell4.setCellValue(department.getName());
                        } else {
                            cell4.setCellValue("未知院系");
                        }
                    } catch (Exception e) {
                        log.error("获取院系名称失败: {}", e.getMessage());
                        cell4.setCellValue(teacher.getDepartmentId());
                    }
                } else {
                    cell4.setCellValue("");
                }
                cell4.setCellStyle(dataStyle);

                Cell cell5 = dataRow.createCell(5);
                cell5.setCellValue(teacher.getPhone() != null ? teacher.getPhone() : "");
                cell5.setCellStyle(dataStyle);
            }
            
            // 调整列宽
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // 自动调整后可能还是不够宽，手动增加一些宽度
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) * 11 / 10);
            }
            
            // 输出到响应流
            workbook.write(response.getOutputStream());
            workbook.close();
        } catch (Exception e) {
            log.error("导出教师Excel数据失败", e);
            try {
                response.getWriter().write("导出失败：" + e.getMessage());
            } catch (IOException ioException) {
                log.error("输出错误信息失败", ioException);
            }
        }
    }
    
    /**
     * 从Excel导入教师数据
     * @param file Excel文件
     * @return 导入结果
     */
    @Log(operationType = "IMPORT", module = "TEACHER_MANAGEMENT", description = "批量导入教师数据")
    @PostMapping("/import")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result importTeacherData(@RequestParam("file") MultipartFile file) {
        log.info("导入教师Excel数据，文件名: {}", file.getOriginalFilename());
        
        try {
            // 检查文件是否为空
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            // 检查文件类型
            String fileName = file.getOriginalFilename();
            if (!fileName.endsWith(".xlsx") && !fileName.endsWith(".xls")) {
                return Result.error("文件类型错误，请上传Excel文件");
            }
            
            // 读取Excel文件
            List<Map<String, Object>> teacherDataList = ExcelUtils.readExcel(file);
            
            // 调用服务层导入数据
            Map<String, Object> importResult = teacherUserService.importFromExcel(teacherDataList);
            
            // 构建返回结果
            return Result.success("导入完成", importResult);
        } catch (Exception e) {
            log.error("导入教师Excel数据失败", e);
            return Result.error("导入失败：" + e.getMessage());
        }
    }

    /**
     * 下载教师导入模板
     */
    @Log(operationType = "DOWNLOAD", module = "TEACHER_MANAGEMENT", description = "下载教师导入模板")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('user:teacher:add')")
    public void downloadImportTemplate(HttpServletResponse response) {
        log.info("下载教师导入模板");

        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("教师导入模板");

            // 创建表头行（第 1 行）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"工号", "姓名", "性别", "手机号", "邮箱", "院系 ID", "教师类型", "说明"};

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
            Cell descCell = descRow.createCell(7);
            descCell.setCellValue("说明：工号、姓名、性别、院系 ID、教师类型为必填项，教师类型：COLLEGE(学院教师)/DEPARTMENT(系室教师)/COUNSELOR(辅导员)");

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

            String filename = "教师导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("教师导入模板下载成功");
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
