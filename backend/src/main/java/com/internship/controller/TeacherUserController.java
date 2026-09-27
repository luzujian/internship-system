package com.internship.controller;

import com.internship.anno.Log;
import com.internship.exception.BusinessException;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.Department;
import com.internship.entity.Division;
import com.internship.entity.TeacherUser;
import com.internship.service.DepartmentService;
import com.internship.service.DivisionService;
import com.internship.service.TeacherUserService;
import com.internship.utils.ExcelUtils;
import com.internship.utils.JwtUtils;
import com.internship.utils.PasswordValidator;
import com.internship.utils.UserCredentialDefaults;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private DivisionService divisionService;
    
    @Autowired
    private JwtUtils jwtUtils;

    @Autowired(required = false)
    private com.internship.service.UserCacheService userCacheService;

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
            String initialPassword = UserCredentialDefaults.PASSWORD;
            teacherUser.setPassword(initialPassword);
            // 设置username字段，与teacherUserId相同
            if (teacherUser.getUsername() == null || teacherUser.getUsername().isEmpty()) {
                teacherUser.setUsername(teacherUser.getTeacherUserId());
            }
            teacherUserService.register(teacherUser);
            return Result.success("添加成功", Map.of("initialCredentials",
                    List.of(Map.of("username", teacherUser.getUsername(), "password", initialPassword))));
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
            List<Map<String, String>> initialCredentials = new ArrayList<>();
            for (TeacherUser teacherUser : teacherUsers) {
                try {
                    String initialPassword = UserCredentialDefaults.PASSWORD;
                    teacherUser.setPassword(initialPassword);
                    // 设置username字段，与teacherUserId相同
                    if (teacherUser.getUsername() == null || teacherUser.getUsername().isEmpty()) {
                        teacherUser.setUsername(teacherUser.getTeacherUserId());
                    }
                    teacherUserService.register(teacherUser);
                    successCount++;
                    initialCredentials.add(Map.of("username", teacherUser.getUsername(), "password", initialPassword));
                } catch (Exception e) {
                    log.warn("添加教师用户 {} 失败: {}", teacherUser.getTeacherUserId(), e.getMessage());
                }
            }
            return Result.success("批量添加完成，成功: " + successCount + " 个用户",
                    Map.of("initialCredentials", initialCredentials));
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
        log.info("收到教师用户状态更新请求");
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
    @Log(operationType = "DELETE", module = "TEACHER_MANAGEMENT", description = "批量删除教师用户", riskLevel = "HIGH", critical = true)
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

    @Log(operationType = "RESET_PASSWORD", module = "TEACHER_MANAGEMENT", description = "一键重置全部教师用户密码",
            riskLevel = "HIGH", targetType = "TEACHER", critical = true)
    @PostMapping("/reset-password/all")
    @PreAuthorize("hasAuthority('user:teacher:reset')")
    public Result resetAllTeacherUserPasswords() {
        log.info("一键重置全部教师用户密码");
        try {
            List<TeacherUser> teachers = teacherUserService.findAll(null, null, null);
            int resetCount = teacherUserService.resetAllPasswords();
            for (TeacherUser teacher : teachers) {
                invalidateUserCredentials(teacher.getTeacherUserId(), teacher.getUsername());
            }
            return Result.success("全部教师用户密码已重置为 123456", Map.of("resetCount", resetCount));
        } catch (Exception e) {
            log.error("一键重置全部教师用户密码失败", e);
            return Result.error("一键重置全部教师用户密码失败：" + e.getMessage());
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

            // 管理员端统一重置口令，服务端固定生效，不信任前端传值。
            String newPassword = UserCredentialDefaults.PASSWORD;

            teacherUser.setPassword(newPassword);
            teacherUserService.update(teacherUser);
            // 清除 Redis 用户缓存，避免被重置用户登录时命中旧密码哈希
            if (userCacheService != null) {
                userCacheService.evictUserCache(teacherUser.getTeacherUserId());
            }

            jwtUtils.incrementUserTokenVersion(teacherUser.getTeacherUserId());

            log.info("教师用户ID: {} 密码重置成功", id);
            return Result.success("密码重置成功", newPassword);
        } catch (Exception e) {
            log.error("重置教师用户密码失败: {}", e.getMessage());
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
     * 导出教师数据到Excel文件
     * @param response HTTP响应对象
     * @param teacherUserId 工号筛选条件
     * @param name 姓名筛选条件
     * @param departmentId 院系ID筛选条件
     */
    @Log(operationType = "EXPORT", module = "TEACHER_MANAGEMENT", description = "导出教师数据", riskLevel = "MEDIUM", critical = true)
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

            // 构建系ID -> 系名称映射（一次查询，避免逐行查库）
            Map<String, String> divisionNameMap = new HashMap<>();
            try {
                List<Division> divisionList = divisionService.findAll();
                if (divisionList != null) {
                    for (Division d : divisionList) {
                        if (d.getId() != null && d.getName() != null) {
                            divisionNameMap.put(String.valueOf(d.getId()), d.getName());
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取系列表失败，导出时系名称将为空: {}", e.getMessage());
            }
            
            // 创建Excel工作簿
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("教师数据");
            
            // 创建表头行
            Row headerRow = sheet.createRow(0);
            String[] headers = {"工号", "姓名", "学院", "系", "身份", "手机号", "邮箱"};
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
                if (teacher.getDepartmentId() != null && !teacher.getDepartmentId().isEmpty()) {
                    try {
                        Long deptId = Long.parseLong(teacher.getDepartmentId());
                        Department department = departmentService.findById(deptId);
                        if (department != null) {
                            cell2.setCellValue(department.getName());
                        } else {
                            cell2.setCellValue("未知学院");
                        }
                    } catch (Exception e) {
                        log.error("获取院系名称失败: {}", e.getMessage());
                        cell2.setCellValue(teacher.getDepartmentId());
                    }
                } else {
                    cell2.setCellValue("");
                }
                cell2.setCellStyle(dataStyle);

                Cell cell3 = dataRow.createCell(3);
                String divisionName = "";
                if (teacher.getDivisionId() != null && !teacher.getDivisionId().isEmpty()) {
                    divisionName = divisionNameMap.getOrDefault(String.valueOf(teacher.getDivisionId()).trim(), "");
                }
                cell3.setCellValue(divisionName);
                cell3.setCellStyle(dataStyle);

                Cell cell4 = dataRow.createCell(4);
                String teacherType = teacher.getTeacherType();
                if (teacherType != null && !teacherType.isEmpty()) {
                    switch (teacherType) {
                        case "COLLEGE":
                            cell4.setCellValue("学院教师");
                            break;
                        case "DEPARTMENT":
                            cell4.setCellValue("系室教师");
                            break;
                        case "COUNSELOR":
                            cell4.setCellValue("辅导员");
                            break;
                        default:
                            cell4.setCellValue(teacherType);
                    }
                } else {
                    cell4.setCellValue("");
                }
                cell4.setCellStyle(dataStyle);

                Cell cell5 = dataRow.createCell(5);
                cell5.setCellValue(teacher.getPhone() != null ? teacher.getPhone() : "");
                cell5.setCellStyle(dataStyle);

                Cell cell6 = dataRow.createCell(6);
                cell6.setCellValue(teacher.getEmail() != null ? teacher.getEmail() : "");
                cell6.setCellStyle(dataStyle);
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

            // 创建表头行（第 1 行）：仅必填项，手机号/邮箱可在管理人员编辑页或个人中心后续绑定
            Row headerRow = sheet.createRow(0);
            String[] headers = {"工号", "姓名", "学院", "系", "身份", "说明"};

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
            descCell.setCellValue("说明：工号、姓名、学院、身份为必填项；系室教师还须填写所属系；身份请填写学院教师/系室教师/辅导员");

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
