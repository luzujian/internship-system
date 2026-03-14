package com.gdmu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.Announcement;
import com.gdmu.service.AnnouncementService;
import com.gdmu.service.UserService;
import com.gdmu.utils.ExcelUtils;
import com.gdmu.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    
    @Autowired
    private AnnouncementService announcementService;
    
    @Autowired
    private UserService userService;
    
    @Value("${file.upload.path:/uploads}")
    private String uploadPath;
    
    @Value("${file.upload.url-prefix:/uploads}")
    private String urlPrefix;
    
    // 获取所有公告
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getAllAnnouncements() {
        log.info("获取所有公告列表");
        try {
            List<Announcement> announcements = announcementService.findAll();
            return Result.success(announcements);
        } catch (Exception e) {
            log.error("获取公告列表失败: {}", e.getMessage(), e);
            return Result.error("获取公告列表失败: " + e.getMessage());
        }
    }
    
    // 根据ID获取公告
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getAnnouncementById(@PathVariable Long id) {
        log.info("根据ID获取公告: {}", id);
        try {
            Announcement announcement = announcementService.findById(id);
            if (announcement == null) {
                return Result.error("公告不存在");
            }
            return Result.success(announcement);
        } catch (Exception e) {
            log.error("获取公告详情失败: {}", e.getMessage(), e);
            return Result.error("获取公告详情失败: " + e.getMessage());
        }
    }
    
    // 分页查询公告
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('announcement:view')")
    public Result getAnnouncementsByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        log.info("分页查询公告，页码: {}, 每页大小: {}, 标题: {}, 状态: {}", page, pageSize, title, status);
        try {
            return Result.success(announcementService.findPage(page, pageSize, title, status));
        } catch (Exception e) {
            log.error("分页查询公告失败: {}", e.getMessage(), e);
            return Result.error("分页查询公告失败: " + e.getMessage());
        }
    }
    
    // 新增公告
    @Log(operationType = "ADD", module = "ANNOUNCEMENT_MANAGEMENT", description = "新增公告")
    @PostMapping
    @PreAuthorize("hasAuthority('announcement:add')")
    public Result addAnnouncement(@RequestBody Announcement announcement) {
        log.info("新增公告: {}", announcement.getTitle());
        try {
            int result = announcementService.insert(announcement);
            return Result.success("添加公告成功", result);
        } catch (Exception e) {
            log.error("添加公告失败: {}", e.getMessage(), e);
            return Result.error("添加公告失败: " + e.getMessage());
        }
    }
    
    // 新增公告（支持附件）
    @Log(operationType = "ADD", module = "ANNOUNCEMENT_MANAGEMENT", description = "新增公告（带附件）")
    @PostMapping("/with-attachments")
    @PreAuthorize("hasAuthority('announcement:add')")
    public Result addAnnouncementWithAttachments(@RequestBody Map<String, Object> request) {
        log.info("新增公告（带附件）");
        try {
            Announcement announcement = new Announcement();
            announcement.setTitle((String) request.get("title"));
            announcement.setContent((String) request.get("content"));
            announcement.setPublisher((String) request.get("publisher"));
            announcement.setPublisherRole((String) request.get("publisherRole"));
            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority((String) request.getOrDefault("priority", "normal"));
            announcement.setTargetType((String) request.getOrDefault("targetType", "ALL"));
            announcement.setTargetValue((String) request.get("targetValue"));
            
            Object validFrom = request.get("validFrom");
            if (validFrom != null) {
                if (validFrom instanceof String && !((String) validFrom).isEmpty()) {
                    try {
                        announcement.setValidFrom(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse((String) validFrom));
                    } catch (Exception e) {
                        log.warn("解析validFrom日期失败: {}", validFrom);
                    }
                } else if (validFrom instanceof Date) {
                    announcement.setValidFrom((Date) validFrom);
                }
            }
            
            Object validTo = request.get("validTo");
            if (validTo != null) {
                if (validTo instanceof String && !((String) validTo).isEmpty()) {
                    try {
                        announcement.setValidTo(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse((String) validTo));
                    } catch (Exception e) {
                        log.warn("解析validTo日期失败: {}", validTo);
                    }
                } else if (validTo instanceof Date) {
                    announcement.setValidTo((Date) validTo);
                }
            }
            
            Object attachments = request.get("attachments");
            if (attachments != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                    announcement.setAttachments(mapper.writeValueAsString(attachments));
                } catch (Exception e) {
                    log.error("序列化附件数据失败: {}", e.getMessage(), e);
                }
            }
            
            int result = announcementService.insert(announcement);
            return Result.success("添加公告成功", result);
        } catch (Exception e) {
            log.error("添加公告失败: {}", e.getMessage(), e);
            return Result.error("添加公告失败: " + e.getMessage());
        }
    }
    
    // 更新公告
    @Log(operationType = "UPDATE", module = "ANNOUNCEMENT_MANAGEMENT", description = "更新公告")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('announcement:edit')")
    public Result updateAnnouncement(@PathVariable Long id, @RequestBody Announcement announcement) {
        log.info("更新公告: ID={}", id);
        try {
            announcement.setId(id);
            int result = announcementService.update(announcement);
            return Result.success("更新公告成功", result);
        } catch (Exception e) {
            log.error("更新公告失败: {}", e.getMessage(), e);
            return Result.error("更新公告失败: " + e.getMessage());
        }
    }
    
    // 更新公告（支持附件）
    @Log(operationType = "UPDATE", module = "ANNOUNCEMENT_MANAGEMENT", description = "更新公告（带附件）")
    @PutMapping("/{id}/with-attachments")
    @PreAuthorize("hasAuthority('announcement:edit')")
    public Result updateAnnouncementWithAttachments(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        log.info("更新公告（带附件）: ID={}", id);
        try {
            Announcement announcement = new Announcement();
            announcement.setId(id);
            announcement.setTitle((String) request.get("title"));
            announcement.setContent((String) request.get("content"));
            announcement.setPublisher((String) request.get("publisher"));
            announcement.setPublisherRole((String) request.get("publisherRole"));
            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority((String) request.getOrDefault("priority", "normal"));
            announcement.setTargetType((String) request.getOrDefault("targetType", "ALL"));
            announcement.setTargetValue((String) request.get("targetValue"));
            
            Object validFrom = request.get("validFrom");
            if (validFrom != null) {
                if (validFrom instanceof String && !((String) validFrom).isEmpty()) {
                    try {
                        announcement.setValidFrom(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse((String) validFrom));
                    } catch (Exception e) {
                        log.warn("解析validFrom日期失败: {}", validFrom);
                    }
                } else if (validFrom instanceof Date) {
                    announcement.setValidFrom((Date) validFrom);
                }
            }
            
            Object validTo = request.get("validTo");
            if (validTo != null) {
                if (validTo instanceof String && !((String) validTo).isEmpty()) {
                    try {
                        announcement.setValidTo(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse((String) validTo));
                    } catch (Exception e) {
                        log.warn("解析validTo日期失败: {}", validTo);
                    }
                } else if (validTo instanceof Date) {
                    announcement.setValidTo((Date) validTo);
                }
            }
            
            Object attachments = request.get("attachments");
            if (attachments != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                    announcement.setAttachments(mapper.writeValueAsString(attachments));
                } catch (Exception e) {
                    log.error("序列化附件数据失败: {}", e.getMessage(), e);
                }
            }
            
            int result = announcementService.update(announcement);
            return Result.success("更新公告成功", result);
        } catch (Exception e) {
            log.error("更新公告失败: {}", e.getMessage(), e);
            return Result.error("更新公告失败: " + e.getMessage());
        }
    }
    
    // 删除公告
    @Log(operationType = "DELETE", module = "ANNOUNCEMENT_MANAGEMENT", description = "删除公告")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('announcement:delete')")
    public Result deleteAnnouncement(@PathVariable Long id) {
        log.info("删除公告: ID={}", id);
        try {
            int result = announcementService.delete(id);
            return Result.success("删除公告成功", result);
        } catch (Exception e) {
            log.error("删除公告失败: {}", e.getMessage(), e);
            return Result.error("删除公告失败: " + e.getMessage());
        }
    }
    
    // 批量删除公告
    @Log(operationType = "DELETE", module = "ANNOUNCEMENT_MANAGEMENT", description = "批量删除公告")
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('announcement:delete')")
    public Result batchDeleteAnnouncement(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        log.info("批量删除公告，IDs: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("公告ID列表不能为空");
            }
            int result = announcementService.batchDelete(ids);
            return Result.success("批量删除公告成功", result);
        } catch (Exception e) {
            log.error("批量删除公告失败: {}", e.getMessage(), e);
            return Result.error("批量删除公告失败: " + e.getMessage());
        }
    }
    
    // 根据标题和状态搜索公告
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result searchAnnouncements(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        log.info("搜索公告，标题: {}, 状态: {}", title, status);
        try {
            List<Announcement> announcements = announcementService.list(title, status);
            return Result.success(announcements);
        } catch (Exception e) {
            log.error("搜索公告失败: {}", e.getMessage(), e);
            return Result.error("搜索公告失败: " + e.getMessage());
        }
    }
    
    // 根据用户类型和用户信息获取公告列表
    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getAnnouncementsForUser(
            @RequestParam String userType,
            @RequestParam(required = false) String userInfo) {
        log.info("根据用户类型获取公告列表，用户类型: {}, 用户信息: {}", userType, userInfo);
        try {
            List<Announcement> announcements = announcementService.getAnnouncementsForUser(userType, userInfo);
            return Result.success(announcements);
        } catch (Exception e) {
            log.error("获取用户公告列表失败: {}", e.getMessage(), e);
            return Result.error("获取用户公告列表失败: " + e.getMessage());
        }
    }
    
    // 根据发布人身份获取用户列表
    @GetMapping("/users")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getUsersByPublisherRole(@RequestParam String publisherRole) {
        log.info("根据发布人身份获取用户列表，身份: {}", publisherRole);
        try {
            List<UserVO> users = new ArrayList<>();
            
            if ("ADMIN".equals(publisherRole)) {
                List<com.gdmu.entity.AdminUser> adminUsers = userService.getAllAdminUsers();
                for (com.gdmu.entity.AdminUser admin : adminUsers) {
                    UserVO vo = new UserVO();
                    vo.setId(admin.getId());
                    vo.setName(admin.getRealName());
                    vo.setRole(admin.getRole());
                    users.add(vo);
                }
            } else if ("COLLEGE".equals(publisherRole) || "DEPARTMENT".equals(publisherRole) || "COUNSELOR".equals(publisherRole)) {
                List<com.gdmu.entity.TeacherUser> teacherUsers = userService.getTeachersByType(publisherRole);
                for (com.gdmu.entity.TeacherUser teacher : teacherUsers) {
                    UserVO vo = new UserVO();
                    vo.setId(teacher.getId());
                    vo.setName(teacher.getName());
                    vo.setRole(teacher.getRole());
                    vo.setTeacherType(teacher.getTeacherType());
                    users.add(vo);
                }
            }
            
            return Result.success(users);
        } catch (Exception e) {
            log.error("获取用户列表失败: {}", e.getMessage(), e);
            return Result.error("获取用户列表失败: " + e.getMessage());
        }
    }
    
    /**
     * 导出公告数据到Excel文件
     * @param response HTTP响应对象
     * @param title 标题筛选条件
     * @param status 状态筛选条件
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void exportAnnouncementData(HttpServletResponse response,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(required = false) String status) {
        log.info("导出公告Excel数据，筛选条件: 标题={}, 状态={}", title, status);

        try {
            List<Announcement> announcements = announcementService.findAll(title, status);
            
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("公告数据");
            
            Row headerRow = sheet.createRow(0);
            String[] headers = {"标题", "内容", "发布人", "发布人身份", "状态", "优先级", "目标群体", "发布日期", "过期日期", "阅读量"};
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
            dataStyle.setAlignment(HorizontalAlignment.LEFT);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);
            
            for (int i = 0; i < announcements.size(); i++) {
                Announcement announcement = announcements.get(i);
                Row dataRow = sheet.createRow(i + 1);
                
                dataRow.createCell(0).setCellValue(announcement.getTitle() != null ? announcement.getTitle() : "");
                dataRow.createCell(1).setCellValue(announcement.getContent() != null ? announcement.getContent() : "");
                dataRow.createCell(2).setCellValue(announcement.getPublisher() != null ? announcement.getPublisher() : "");
                dataRow.createCell(3).setCellValue(convertPublisherRoleToChinese(announcement.getPublisherRole()));
                dataRow.createCell(4).setCellValue(convertStatusToChinese(announcement.getStatus()));
                dataRow.createCell(5).setCellValue(convertPriorityToChinese(announcement.getPriority()));
                dataRow.createCell(6).setCellValue(convertTargetTypeToChinese(announcement.getTargetType()));
                
                if (announcement.getPublishTime() != null) {
                    dataRow.createCell(7).setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(announcement.getPublishTime()));
                } else {
                    dataRow.createCell(7).setCellValue("");
                }
                
                if (announcement.getValidTo() != null) {
                    dataRow.createCell(8).setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(announcement.getValidTo()));
                } else {
                    dataRow.createCell(8).setCellValue("");
                }
                
                dataRow.createCell(9).setCellValue(announcement.getReadCount() != null ? announcement.getReadCount() : 0);
                
                for (int j = 0; j < headers.length; j++) {
                    dataRow.getCell(j).setCellStyle(dataStyle);
                    sheet.autoSizeColumn(j);
                }
            }
            
            String filename = "公告数据_" + new Date().getTime() + ".xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8.toString());
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");
            
            workbook.write(response.getOutputStream());
            workbook.close();
            
            log.info("公告数据Excel导出成功，共导出{}条记录", announcements.size());
        } catch (IOException e) {
            log.error("Excel数据导出失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败: " + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        } catch (Exception e) {
            log.error("导出公告数据时发生异常", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败: 系统异常\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        }
    }
    
    /**
     * 从Excel导入公告数据
     * @param file 上传的Excel文件
     * @return 导入结果
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result importAnnouncementData(@RequestParam("file") MultipartFile file) {
        log.info("导入公告Excel数据，文件名: {}", file.getOriginalFilename());
        
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }
            
            List<Map<String, Object>> announcementDataList = ExcelUtils.readExcel(file);
            
            Map<String, Object> importResult = announcementService.importFromExcel(announcementDataList);
            
            return Result.success(importResult);
        } catch (Exception e) {
            log.error("Excel数据导入失败", e);
            return Result.error("导入失败: " + e.getMessage());
        }
    }
    
    private String convertPublisherRoleToChinese(String publisherRole) {
        if (publisherRole == null) {
            return "";
        }
        switch (publisherRole) {
            case "ADMIN":
                return "管理员";
            case "COLLEGE":
                return "学院教师";
            case "DEPARTMENT":
                return "系室教师";
            case "COUNSELOR":
                return "辅导员";
            default:
                return publisherRole;
        }
    }
    
    private String convertStatusToChinese(String status) {
        if (status == null) {
            return "";
        }
        switch (status) {
            case "DRAFT":
                return "草稿";
            case "PUBLISHED":
                return "已发布";
            case "EXPIRED":
                return "已过期";
            default:
                return status;
        }
    }
    
    private String convertPriorityToChinese(String priority) {
        if (priority == null) {
            return "";
        }
        switch (priority) {
            case "normal":
                return "普通";
            case "important":
                return "重要";
            default:
                return priority;
        }
    }
    
    private String convertTargetTypeToChinese(String targetType) {
        if (targetType == null) {
            return "";
        }
        switch (targetType) {
            case "ALL":
                return "全体师生";
            case "STUDENT":
                return "全体学生";
            case "TEACHER":
                return "全体教师";
            case "TEACHER_TYPE":
                return "特定教师类别";
            case "MAJOR":
                return "特定专业学生";
            default:
                return targetType;
        }
    }
    
    @GetMapping("/download-attachment")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public ResponseEntity<Resource> downloadAttachment(
            @RequestParam String announcementId,
            @RequestParam String fileName) {
        log.info("下载公告附件，公告ID: {}, 文件名: {}", announcementId, fileName);
        try {
            Announcement announcement = announcementService.findById(Long.parseLong(announcementId));
            if (announcement == null) {
                return ResponseEntity.notFound().build();
            }
            
            if (announcement.getAttachments() == null || announcement.getAttachments().isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            ObjectMapper objectMapper = new ObjectMapper();
            List<Map<String, Object>> attachments = objectMapper.readValue(
                announcement.getAttachments(), 
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class)
            );
            
            Map<String, Object> targetAttachment = null;
            for (Map<String, Object> attachment : attachments) {
                if (fileName.equals(attachment.get("name"))) {
                    targetAttachment = attachment;
                    break;
                }
            }
            
            if (targetAttachment == null) {
                return ResponseEntity.notFound().build();
            }
            
            String fileUrl = (String) targetAttachment.get("url");
            if (fileUrl == null || fileUrl.isEmpty()) {
                return ResponseEntity.notFound().build();
            }
            
            String relativePath = fileUrl;
            if (relativePath.startsWith("/api")) {
                relativePath = relativePath.substring(4);
            }
            if (relativePath.startsWith(urlPrefix)) {
                relativePath = relativePath.substring(urlPrefix.length());
            }
            if (relativePath.startsWith("/")) {
                relativePath = relativePath.substring(1);
            }
            
            String fullPath = uploadPath + "/" + relativePath;
            
            Path path = Paths.get(fullPath);
            Resource resource = new UrlResource(path.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                log.error("文件不存在或不可读: {}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
            
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + encodedFilename + "\"")
                    .body(resource);
        } catch (Exception e) {
            log.error("下载公告附件失败: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }
}