package com.gdmu.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.Announcement;
import com.gdmu.service.AnnouncementService;
import com.gdmu.service.UserService;
import com.gdmu.utils.AliyunOSSOperator;
import com.gdmu.utils.ExcelUtils;
import com.gdmu.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Autowired
    private com.gdmu.service.AnnouncementReadRecordService announcementReadRecordService;

    @Autowired
    private com.gdmu.websocket.AnnouncementWebSocketHandler webSocketHandler;

    // 获取所有公告
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getAllAnnouncements() {
        log.info("获取所有公告列表");
        try {
            List<Announcement> announcements = announcementService.findAll();
            return Result.success(announcements);
        } catch (Exception e) {
            log.error("获取公告列表失败：{}", e.getMessage(), e);
            return Result.error("获取公告列表失败：" + e.getMessage());
        }
    }

    // 根据 ID 获取公告
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public Result getAnnouncementById(@PathVariable Long id) {
        log.info("根据 ID 获取公告：{}", id);
        try {
            Announcement announcement = announcementService.findById(id);
            if (announcement == null) {
                return Result.error("公告不存在");
            }
            
            // 记录阅读状态（非管理员用户）
            try {
                Long userId = com.gdmu.utils.CurrentHolder.getUserId();
                if (userId != null) {
                    String userRole = com.gdmu.utils.CurrentHolder.getUserRole();
                    String userType = convertRoleToType(userRole);
                    log.info("记录阅读状态 - 用户ID: {}, 用户角色: {}, 用户类型: {}", userId, userRole, userType);
                    if (!"ADMIN".equals(userType)) {
                        // 创建阅读记录
                        com.gdmu.entity.AnnouncementReadRecord readRecord = new com.gdmu.entity.AnnouncementReadRecord();
                        readRecord.setAnnouncementId(id);
                        readRecord.setUserId(String.valueOf(userId));
                        readRecord.setUserType(userType);
                        readRecord.setReadTime(new java.util.Date());
                        announcementReadRecordService.insert(readRecord);
                        log.info("阅读记录插入成功 - 公告ID: {}, 用户ID: {}, 用户类型: {}", id, userId, userType);
                        
                        // 更新公告阅读次数
                        announcementService.incrementReadCount(id);
                    }
                }
            } catch (Exception e) {
                log.warn("记录阅读状态失败：{}", e.getMessage(), e);
            }
            
            return Result.success(announcement);
        } catch (Exception e) {
            log.error("获取公告详情失败：{}", e.getMessage(), e);
            return Result.error("获取公告详情失败：" + e.getMessage());
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
        log.info("分页查询公告，页码：{}, 每页大小：{}, 标题：{}, 状态：{}", page, pageSize, title, status);
        try {
            return Result.success(announcementService.findPage(page, pageSize, title, status));
        } catch (Exception e) {
            log.error("分页查询公告失败：{}", e.getMessage(), e);
            return Result.error("分页查询公告失败：" + e.getMessage());
        }
    }

    // 新增公告
    @Log(operationType = "ADD", module = "ANNOUNCEMENT_MANAGEMENT", description = "新增公告")
    @PostMapping
    @PreAuthorize("hasAuthority('announcement:add')")
    public Result addAnnouncement(@RequestBody Map<String, Object> request) {
        log.info("新增公告，请求参数：{}", request);
        try {
            Announcement announcement = new Announcement();
            announcement.setTitle((String) request.get("title"));
            announcement.setContent((String) request.get("content"));

            // 处理 publisher：如果为空，使用默认值
            String publisher = (String) request.get("publisher");
            if (publisher == null || publisher.trim().isEmpty()) {
                publisher = "系统管理员";
                log.warn("publisher 为空，使用默认值：{}", publisher);
            }
            announcement.setPublisher(publisher);

            // 处理 publisherRole：如果为空，使用默认值
            String publisherRole = (String) request.get("publisherRole");
            if (publisherRole == null || publisherRole.trim().isEmpty()) {
                publisherRole = "ADMIN";
                log.warn("publisherRole 为空，使用默认值：{}", publisherRole);
            }
            announcement.setPublisherRole(publisherRole);

            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority((String) request.getOrDefault("priority", "normal"));

            // 处理 targetType：如果是数组则转为 JSON 字符串，否则直接转换
            Object targetTypeObj = request.get("targetType");
            if (targetTypeObj != null) {
                if (targetTypeObj instanceof java.util.List) {
                    // 前端发送的是数组，转换为 JSON 字符串
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        announcement.setTargetType(mapper.writeValueAsString(targetTypeObj));
                    } catch (Exception e) {
                        log.error("序列化 targetType 失败：{}", e.getMessage(), e);
                        announcement.setTargetType(targetTypeObj.toString());
                    }
                } else {
                    announcement.setTargetType((String) targetTypeObj);
                }
            } else {
                announcement.setTargetType("ALL");
            }

            announcement.setTargetValue((String) request.get("targetValue"));

            // 处理日期字段
            Object validFrom = request.get("validFrom");
            if (validFrom != null) {
                if (validFrom instanceof String && !((String) validFrom).isEmpty()) {
                    try {
                        String dateStr = (String) validFrom;
                        Date parsedDate = null;
                        if (dateStr.contains("Z") || dateStr.contains("+") || dateStr.contains("-")) {
                            try {
                                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateStr);
                                parsedDate = Date.from(offsetDateTime.toInstant());
                            } catch (Exception e) {
                                log.debug("ISO 8601 格式解析失败，尝试其他格式：{}", dateStr);
                            }
                        }
                        if (parsedDate == null) {
                            try {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateStr);
                            } catch (Exception e) {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateStr);
                            }
                        }
                        if (parsedDate == null) {
                            parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
                        }
                        if (parsedDate != null) {
                            announcement.setValidFrom(parsedDate);
                        }
                    } catch (Exception e) {
                        log.warn("解析 validFrom 日期失败：{}", validFrom, e);
                    }
                } else if (validFrom instanceof Date) {
                    announcement.setValidFrom((Date) validFrom);
                }
            }

            Object validTo = request.get("validTo");
            if (validTo != null) {
                if (validTo instanceof String && !((String) validTo).isEmpty()) {
                    try {
                        String dateStr = (String) validTo;
                        Date parsedDate = null;
                        if (dateStr.contains("Z") || dateStr.contains("+") || dateStr.contains("-")) {
                            try {
                                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateStr);
                                parsedDate = Date.from(offsetDateTime.toInstant());
                            } catch (Exception e) {
                                log.debug("ISO 8601 格式解析失败，尝试其他格式：{}", dateStr);
                            }
                        }
                        if (parsedDate == null) {
                            try {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateStr);
                            } catch (Exception e) {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateStr);
                            }
                        }
                        if (parsedDate == null) {
                            parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
                        }
                        if (parsedDate != null) {
                            announcement.setValidTo(parsedDate);
                        }
                    } catch (Exception e) {
                        log.warn("解析 validTo 日期失败：{}", validTo, e);
                    }
                } else if (validTo instanceof Date) {
                    announcement.setValidTo((Date) validTo);
                }
            }

            // 处理附件
            Object attachments = request.get("attachments");
            if (attachments != null) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
                    announcement.setAttachments(mapper.writeValueAsString(attachments));
                } catch (Exception e) {
                    log.error("序列化附件数据失败：{}", e.getMessage(), e);
                }
            } else {
                announcement.setAttachments("[]");
            }

            int result = announcementService.insert(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("添加公告成功", result);
        } catch (Exception e) {
            log.error("添加公告失败：{}", e.getMessage(), e);
            return Result.error("添加公告失败：" + e.getMessage());
        }
    }

    // 新增公告（支持附件）
    @Log(operationType = "ADD", module = "ANNOUNCEMENT_MANAGEMENT", description = "新增公告（带附件）")
    @PostMapping("/with-attachments")
    @PreAuthorize("hasAuthority('announcement:add')")
    public Result addAnnouncementWithAttachments(@RequestBody Map<String, Object> request) {
        log.info("新增公告（带附件），请求参数：{}", request);
        try {
            Announcement announcement = new Announcement();
            announcement.setTitle((String) request.get("title"));
            announcement.setContent((String) request.get("content"));

            // 处理 publisher：如果为空，使用默认值
            String publisher = (String) request.get("publisher");
            if (publisher == null || publisher.trim().isEmpty()) {
                publisher = "系统管理员";
                log.warn("publisher为空，使用默认值: {}", publisher);
            }
            announcement.setPublisher(publisher);

            // 处理 publisherRole：如果为空，使用默认值
            String publisherRole = (String) request.get("publisherRole");
            if (publisherRole == null || publisherRole.trim().isEmpty()) {
                publisherRole = "ADMIN";
                log.warn("publisherRole为空，使用默认值: {}", publisherRole);
            }
            announcement.setPublisherRole(publisherRole);

            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority((String) request.getOrDefault("priority", "normal"));

            // 处理 targetType：如果是数组则转为 JSON 字符串，否则直接转换
            Object targetTypeObj = request.get("targetType");
            if (targetTypeObj != null) {
                if (targetTypeObj instanceof java.util.List) {
                    // 前端发送的是数组，转换为 JSON 字符串
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        announcement.setTargetType(mapper.writeValueAsString(targetTypeObj));
                    } catch (Exception e) {
                        log.error("序列化 targetType 失败：{}", e.getMessage(), e);
                        announcement.setTargetType(targetTypeObj.toString());
                    }
                } else {
                    announcement.setTargetType((String) targetTypeObj);
                }
            } else {
                announcement.setTargetType("ALL");
            }

            announcement.setTargetValue((String) request.get("targetValue"));

            Object validFrom = request.get("validFrom");
            if (validFrom != null) {
                if (validFrom instanceof String && !((String) validFrom).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) validFrom;
                        Date parsedDate = null;

                        // ISO 8601 格式（带毫秒和时区）：2024-01-01T00:00:00.000Z 或 2024-01-01T00:00:00.000+08:00
                        if (dateStr.contains("Z") || dateStr.contains("+") || dateStr.contains("-")) {
                            try {
                                // 使用 ISO 8601 格式解析
                                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateStr);
                                parsedDate = Date.from(offsetDateTime.toInstant());
                            } catch (Exception e) {
                                log.debug("ISO 8601 格式解析失败，尝试其他格式：{}", dateStr);
                            }
                        }

                        // 如果 ISO 8601 解析失败，尝试简单格式
                        if (parsedDate == null) {
                            try {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateStr);
                            } catch (Exception e) {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateStr);
                            }
                        }

                        // 如果还是失败，尝试纯日期格式
                        if (parsedDate == null) {
                            parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
                        }

                        if (parsedDate != null) {
                            announcement.setValidFrom(parsedDate);
                        } else {
                            log.warn("所有日期格式解析失败：{}", validFrom);
                        }
                    } catch (Exception e) {
                        log.warn("解析 validFrom 日期失败：{}", validFrom, e);
                    }
                } else if (validFrom instanceof Date) {
                    announcement.setValidFrom((Date) validFrom);
                }
            }

            Object validTo = request.get("validTo");
            if (validTo != null) {
                if (validTo instanceof String && !((String) validTo).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) validTo;
                        Date parsedDate = null;

                        // ISO 8601 格式（带毫秒和时区）：2024-01-01T00:00:00.000Z 或 2024-01-01T00:00:00.000+08:00
                        if (dateStr.contains("Z") || dateStr.contains("+") || dateStr.contains("-")) {
                            try {
                                // 使用 ISO 8601 格式解析
                                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateStr);
                                parsedDate = Date.from(offsetDateTime.toInstant());
                            } catch (Exception e) {
                                log.debug("ISO 8601 格式解析失败，尝试其他格式：{}", dateStr);
                            }
                        }

                        // 如果 ISO 8601 解析失败，尝试简单格式
                        if (parsedDate == null) {
                            try {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateStr);
                            } catch (Exception e) {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateStr);
                            }
                        }

                        // 如果还是失败，尝试纯日期格式
                        if (parsedDate == null) {
                            parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
                        }

                        if (parsedDate != null) {
                            announcement.setValidTo(parsedDate);
                        } else {
                            log.warn("所有日期格式解析失败：{}", validTo);
                        }
                    } catch (Exception e) {
                        log.warn("解析 validTo 日期失败：{}", validTo, e);
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
                    log.error("序列化附件数据失败：{}", e.getMessage(), e);
                }
            } else {
                // 如果没有附件，设置为空数组
                announcement.setAttachments("[]");
            }

            log.info("准备插入公告数据：title={}, content={}, publisher={}, publisherRole={}, status={}, priority={}, targetType={}, validFrom={}, validTo={}, attachments={}",
                    announcement.getTitle(), announcement.getContent(), announcement.getPublisher(),
                    announcement.getPublisherRole(), announcement.getStatus(), announcement.getPriority(),
                    announcement.getTargetType(), announcement.getValidFrom(), announcement.getValidTo(),
                    announcement.getAttachments());

            int result = announcementService.insert(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("添加公告成功", result);
        } catch (Exception e) {
            log.error("添加公告失败：{}", e.getMessage(), e);
            return Result.error("添加公告失败：" + e.getMessage());
        }
    }

    // 更新公告
    @Log(operationType = "UPDATE", module = "ANNOUNCEMENT_MANAGEMENT", description = "更新公告")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('announcement:edit')")
    public Result updateAnnouncement(@PathVariable Long id, @RequestBody Announcement announcement) {
        log.info("更新公告：ID={}", id);
        try {
            announcement.setId(id);
            int result = announcementService.update(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("更新公告成功", result);
        } catch (Exception e) {
            log.error("更新公告失败：{}", e.getMessage(), e);
            return Result.error("更新公告失败：" + e.getMessage());
        }
    }

    // 更新公告（支持附件）
    @Log(operationType = "UPDATE", module = "ANNOUNCEMENT_MANAGEMENT", description = "更新公告（带附件）")
    @PutMapping("/{id}/with-attachments")
    @PreAuthorize("hasAuthority('announcement:edit')")
    public Result updateAnnouncementWithAttachments(@PathVariable Long id, @RequestBody Map<String, Object> request) {
        log.info("更新公告（带附件）: ID={}, 请求参数：{}", id, request);
        try {
            Announcement announcement = new Announcement();
            announcement.setId(id);
            announcement.setTitle((String) request.get("title"));
            announcement.setContent((String) request.get("content"));

            // 处理 publisher：如果为空，使用默认值
            String publisher = (String) request.get("publisher");
            if (publisher == null || publisher.trim().isEmpty()) {
                publisher = "系统管理员";
                log.warn("更新时publisher为空，使用默认值: {}", publisher);
            }
            announcement.setPublisher(publisher);

            // 处理 publisherRole：如果为空，使用默认值
            String publisherRole = (String) request.get("publisherRole");
            if (publisherRole == null || publisherRole.trim().isEmpty()) {
                publisherRole = "ADMIN";
                log.warn("更新时publisherRole为空，使用默认值: {}", publisherRole);
            }
            announcement.setPublisherRole(publisherRole);

            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority((String) request.getOrDefault("priority", "normal"));

            // 处理 targetType：如果是数组则转为 JSON 字符串，否则直接转换
            Object targetTypeObj = request.get("targetType");
            if (targetTypeObj != null) {
                if (targetTypeObj instanceof java.util.List) {
                    // 前端发送的是数组，转换为 JSON 字符串
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        announcement.setTargetType(mapper.writeValueAsString(targetTypeObj));
                    } catch (Exception e) {
                        log.error("序列化 targetType 失败：{}", e.getMessage(), e);
                        announcement.setTargetType(targetTypeObj.toString());
                    }
                } else {
                    announcement.setTargetType((String) targetTypeObj);
                }
            } else {
                announcement.setTargetType("ALL");
            }

            announcement.setTargetValue((String) request.get("targetValue"));

            Object validFrom = request.get("validFrom");
            if (validFrom != null) {
                if (validFrom instanceof String && !((String) validFrom).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) validFrom;
                        Date parsedDate = null;

                        // ISO 8601 格式（带毫秒和时区）：2024-01-01T00:00:00.000Z 或 2024-01-01T00:00:00.000+08:00
                        if (dateStr.contains("Z") || dateStr.contains("+") || dateStr.contains("-")) {
                            try {
                                // 使用 ISO 8601 格式解析
                                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateStr);
                                parsedDate = Date.from(offsetDateTime.toInstant());
                            } catch (Exception e) {
                                log.debug("ISO 8601 格式解析失败，尝试其他格式：{}", dateStr);
                            }
                        }

                        // 如果 ISO 8601 解析失败，尝试简单格式
                        if (parsedDate == null) {
                            try {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateStr);
                            } catch (Exception e) {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateStr);
                            }
                        }

                        // 如果还是失败，尝试纯日期格式
                        if (parsedDate == null) {
                            parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
                        }

                        if (parsedDate != null) {
                            announcement.setValidFrom(parsedDate);
                        } else {
                            log.warn("所有日期格式解析失败：{}", validFrom);
                        }
                    } catch (Exception e) {
                        log.warn("解析 validFrom 日期失败：{}", validFrom, e);
                    }
                } else if (validFrom instanceof Date) {
                    announcement.setValidFrom((Date) validFrom);
                }
            }

            Object validTo = request.get("validTo");
            if (validTo != null) {
                if (validTo instanceof String && !((String) validTo).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) validTo;
                        Date parsedDate = null;

                        // ISO 8601 格式（带毫秒和时区）：2024-01-01T00:00:00.000Z 或 2024-01-01T00:00:00.000+08:00
                        if (dateStr.contains("Z") || dateStr.contains("+") || dateStr.contains("-")) {
                            try {
                                // 使用 ISO 8601 格式解析
                                java.time.OffsetDateTime offsetDateTime = java.time.OffsetDateTime.parse(dateStr);
                                parsedDate = Date.from(offsetDateTime.toInstant());
                            } catch (Exception e) {
                                log.debug("ISO 8601 格式解析失败，尝试其他格式：{}", dateStr);
                            }
                        }

                        // 如果 ISO 8601 解析失败，尝试简单格式
                        if (parsedDate == null) {
                            try {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS").parse(dateStr);
                            } catch (Exception e) {
                                parsedDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss").parse(dateStr);
                            }
                        }

                        // 如果还是失败，尝试纯日期格式
                        if (parsedDate == null) {
                            parsedDate = new SimpleDateFormat("yyyy-MM-dd").parse(dateStr);
                        }

                        if (parsedDate != null) {
                            announcement.setValidTo(parsedDate);
                        } else {
                            log.warn("所有日期格式解析失败：{}", validTo);
                        }
                    } catch (Exception e) {
                        log.warn("解析 validTo 日期失败：{}", validTo, e);
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
                    log.error("序列化附件数据失败：{}", e.getMessage(), e);
                }
            } else {
                // 如果没有附件，设置为空数组
                announcement.setAttachments("[]");
            }

            log.info("准备更新公告数据：id={}, title={}, content={}, publisher={}, publisherRole={}, status={}, priority={}, targetType={}, validFrom={}, validTo={}, attachments={}",
                    id, announcement.getTitle(), announcement.getContent(), announcement.getPublisher(),
                    announcement.getPublisherRole(), announcement.getStatus(), announcement.getPriority(),
                    announcement.getTargetType(), announcement.getValidFrom(), announcement.getValidTo(),
                    announcement.getAttachments());

            int result = announcementService.update(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("更新公告成功", result);
        } catch (Exception e) {
            log.error("更新公告失败：{}", e.getMessage(), e);
            return Result.error("更新公告失败：" + e.getMessage());
        }
    }

    // 删除公告
    @Log(operationType = "DELETE", module = "ANNOUNCEMENT_MANAGEMENT", description = "删除公告")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('announcement:delete')")
    public Result deleteAnnouncement(@PathVariable Long id) {
        log.info("删除公告：ID={}", id);
        try {
            int result = announcementService.delete(id);
            return Result.success("删除公告成功", result);
        } catch (Exception e) {
            log.error("删除公告失败：{}", e.getMessage(), e);
            return Result.error("删除公告失败：" + e.getMessage());
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
                return Result.error("公告 ID 列表不能为空");
            }
            int result = announcementService.batchDelete(ids);
            return Result.success("批量删除公告成功", result);
        } catch (Exception e) {
            log.error("批量删除公告失败：{}", e.getMessage(), e);
            return Result.error("批量删除公告失败：" + e.getMessage());
        }
    }

    // 根据标题和状态搜索公告
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public Result searchAnnouncements(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        log.info("搜索公告，标题：{}, 状态：{}", title, status);
        try {
            List<Announcement> announcements = announcementService.list(title, status);
            return Result.success(announcements);
        } catch (Exception e) {
            log.error("搜索公告失败：{}", e.getMessage(), e);
            return Result.error("搜索公告失败：" + e.getMessage());
        }
    }

    // 根据用户类型和用户信息获取公告列表
    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('ROLE_TEACHER', 'ROLE_STUDENT')")
    public Result getAnnouncementsForUser(
            @RequestParam String userType,
            @RequestParam(required = false) String userInfo) {
        log.info("根据用户类型获取公告列表，用户类型：{}, 用户信息：{}", userType, userInfo);
        try {
            List<Announcement> announcements = announcementService.getAnnouncementsForUser(userType, userInfo);
            return Result.success(announcements);
        } catch (Exception e) {
            log.error("获取用户公告列表失败：{}", e.getMessage(), e);
            return Result.error("获取用户公告列表失败：" + e.getMessage());
        }
    }

    // 根据发布人身份获取用户列表
    @GetMapping("/users")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result getUsersByPublisherRole(@RequestParam String publisherRole) {
        log.info("根据发布人身份获取用户列表，身份：{}", publisherRole);
        try {
            List<UserVO> users = new ArrayList<>();

            if ("ADMIN".equals(publisherRole)) {
                List<com.gdmu.entity.AdminUser> adminUsers = userService.getAllAdminUsers();
                for (com.gdmu.entity.AdminUser admin : adminUsers) {
                    UserVO vo = new UserVO();
                    vo.setId(admin.getId());
                    vo.setName(admin.getName());
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
            log.error("获取用户列表失败：{}", e.getMessage(), e);
            return Result.error("获取用户列表失败：" + e.getMessage());
        }
    }

    /**
     * 导出公告数据到 Excel 文件
     * @param response HTTP 响应对象
     * @param title 标题筛选条件
     * @param status 状态筛选条件
     */
    @GetMapping("/export")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void exportAnnouncementData(HttpServletResponse response,
                                      @RequestParam(required = false) String title,
                                      @RequestParam(required = false) String status) {
        log.info("导出公告 Excel 数据，筛选条件：标题={}, 状态={}", title, status);

        try {
            List<Announcement> announcements = announcementService.findAll(title, status);

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("公告数据");

            Row headerRow = sheet.createRow(0);
            String[] headers = {"标题", "内容", "发布人", "发布人身份", "状态", "优先级", "目标群体", "发布日期", "过期日期", "阅读量", "创建时间", "更新时间"};
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

                if (announcement.getCreateTime() != null) {
                    dataRow.createCell(10).setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(announcement.getCreateTime()));
                } else {
                    dataRow.createCell(10).setCellValue("");
                }

                if (announcement.getUpdateTime() != null) {
                    dataRow.createCell(11).setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(announcement.getUpdateTime()));
                } else {
                    dataRow.createCell(11).setCellValue("");
                }

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

            log.info("公告数据 Excel 导出成功，共导出{}条记录", announcements.size());
        } catch (IOException e) {
            log.error("Excel 数据导出失败", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败：" + e.getMessage() + "\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        } catch (Exception e) {
            log.error("导出公告数据时发生异常", e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"导出失败：系统异常\"}");
            } catch (IOException ex) {
                log.error("响应错误信息失败", ex);
            }
        }
    }

    /**
     * 从 Excel 导入公告数据
     * @param file 上传的 Excel 文件
     * @return 导入结果
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Result importAnnouncementData(@RequestParam("file") MultipartFile file) {
        log.info("导入公告 Excel 数据，文件名：{}", file.getOriginalFilename());

        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            List<Map<String, Object>> announcementDataList = ExcelUtils.readExcel(file);

            Map<String, Object> importResult = announcementService.importFromExcel(announcementDataList);

            return Result.success(importResult);
        } catch (Exception e) {
            log.error("Excel 数据导入失败", e);
            return Result.error("导入失败：" + e.getMessage());
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
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public ResponseEntity<InputStreamResource> downloadAttachment(
            @RequestParam String announcementId,
            @RequestParam String fileName) {
        log.info("下载公告附件，公告 ID: {}, 文件名：{}", announcementId, fileName);
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

            // 从 OSS 下载文件
            byte[] fileContent = aliyunOSSOperator.downloadFile(fileUrl);

            String encodedFilename = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString())
                    .replaceAll("\\+", "%20");

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + encodedFilename + "\"");
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(new InputStreamResource(new ByteArrayInputStream(fileContent)));
        } catch (Exception e) {
            log.error("下载公告附件失败：{}", e.getMessage(), e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 下载公告导入模板
     */
    @Log(operationType = "DOWNLOAD", module = "ANNOUNCEMENT_MANAGEMENT", description = "下载公告导入模板")
    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('announcement:add')")
    public void downloadImportTemplate(HttpServletResponse response) {
        log.info("下载公告导入模板");

        try {
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("公告导入模板");

            // 创建表头行（第 1 行）
            Row headerRow = sheet.createRow(0);
            String[] headers = {"标题", "内容", "发布人", "发布人身份", "状态", "优先级", "目标群体", "目标值", "说明"};

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
                    sheet.setColumnWidth(i, 15 * 256);
                } else {
                    // 说明列宽度设置更大
                    sheet.setColumnWidth(i, 40 * 256);
                }
            }

            // 在表头行的最后一列右边添加说明
            Row descRow = sheet.createRow(1);
            Cell descCell = descRow.createCell(8);
            descCell.setCellValue("说明：标题、内容为必填项；发布人身份：ADMIN/TEACHER/STUDENT；状态：DRAFT/PUBLISHED；优先级：LOW/MEDIUM/HIGH；目标群体：ALL/STUDENT/TEACHER");

            CellStyle descStyle = workbook.createCellStyle();
            descStyle.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
            descStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            descStyle.setFont(headerFont);
            descCell.setCellStyle(descStyle);

            String filename = "公告导入模板.xlsx";
            String encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8).replaceAll("\\\\+", "%20");
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFilename + "\"");

            try (OutputStream outputStream = response.getOutputStream()) {
                workbook.write(outputStream);
                log.info("公告导入模板下载成功");
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

    /**
     * 推送公告通知到WebSocket客户端
     */
    private void pushAnnouncementNotification(Announcement announcement) {
        try {
            Map<String, Object> announcementData = new java.util.HashMap<>();
            announcementData.put("id", announcement.getId());
            announcementData.put("title", announcement.getTitle());
            announcementData.put("content", announcement.getContent());
            announcementData.put("publisher", announcement.getPublisher());
            announcementData.put("publisherRole", announcement.getPublisherRole());
            announcementData.put("priority", announcement.getPriority());
            announcementData.put("targetType", announcement.getTargetType());
            announcementData.put("publishTime", announcement.getPublishTime());
            announcementData.put("validFrom", announcement.getValidFrom());
            announcementData.put("validTo", announcement.getValidTo());
            
            String targetType = announcement.getTargetType();
            if (targetType != null) {
                if (targetType.contains("COMPANY")) {
                    log.info("推送公告到企业端：{}", announcement.getTitle());
                    webSocketHandler.sendAnnouncementToRole("COMPANY", announcementData);
                }
                if (targetType.contains("STUDENT")) {
                    log.info("推送公告到学生端：{}", announcement.getTitle());
                    webSocketHandler.sendAnnouncementToRole("STUDENT", announcementData);
                }
                if (targetType.contains("TEACHER")) {
                    log.info("推送公告到教师端：{}", announcement.getTitle());
                    webSocketHandler.sendAnnouncementToRole("TEACHER", announcementData);
                }
                if (targetType.contains("ALL")) {
                    log.info("广播公告到所有用户：{}", announcement.getTitle());
                    webSocketHandler.broadcastAnnouncement(announcementData);
                }
            }
            
            log.info("推送公告到管理员端：{}", announcement.getTitle());
            webSocketHandler.sendAnnouncementToRole("ADMIN", announcementData);
        } catch (Exception e) {
            log.error("推送公告通知失败：{}", e.getMessage(), e);
        }
    }

    /**
     * 将角色转换为用户类型
     */
    private String convertRoleToType(String role) {
        if (role == null) {
            return "UNKNOWN";
        }
        if (role.startsWith("ROLE_ADMIN")) {
            return "ADMIN";
        } else if (role.startsWith("ROLE_TEACHER")) {
            return "TEACHER";
        } else if (role.startsWith("ROLE_STUDENT")) {
            return "STUDENT";
        } else if (role.startsWith("ROLE_COMPANY")) {
            return "ENTERPRISE";
        }
        return "UNKNOWN";
    }
}
