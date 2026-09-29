package com.internship.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.Announcement;
import com.internship.entity.AnnouncementReadRecord;
import com.internship.entity.PageResult;
import com.internship.entity.User;
import com.internship.service.AnnouncementService;
import com.internship.service.UserService;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.ExcelUtils;
import com.internship.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
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
import java.util.Objects;

@Slf4j
@RestController
@RequestMapping("/api/announcements")
public class AnnouncementController {
    @Autowired
    private com.internship.service.AnnouncementAttachmentPolicy attachmentPolicy;

    @GetMapping("/capabilities")
    @PreAuthorize("isAuthenticated()")
    public Result capabilities(org.springframework.security.core.Authentication authentication) {
        var authorities = authentication.getAuthorities().stream().map(a -> a.getAuthority()).collect(java.util.stream.Collectors.toSet());
        boolean teacher = authorities.stream().anyMatch(java.util.Set.of("ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR")::contains);
        return Result.success(Map.of("add", authorities.contains("announcement:add"),
                "edit", authorities.contains("announcement:edit"), "delete", teacher || authorities.contains("announcement:delete")));
    }

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private UserService userService;

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Autowired
    private com.internship.service.AnnouncementReadRecordService announcementReadRecordService;

    @Autowired
    private com.internship.websocket.AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private com.internship.mapper.StudentUserMapper studentUserMapper;

    // 获取所有公告
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_STUDENT')")
    public Result getAllAnnouncements() {
        log.info("获取所有公告列表");
        try {
            User viewer = requireAnnouncementViewer();
            List<Announcement> announcements = visibleAnnouncements(announcementService.findAll(), viewer);
            return Result.success(announcements);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取公告列表失败：{}", e.getMessage(), e);
            return Result.error("获取公告列表失败：" + e.getMessage());
        }
    }

    // 根据 ID 获取公告
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public Result getAnnouncementById(@PathVariable Long id) {
        log.info("根据 ID 获取公告：{}", id);
        try {
            Announcement announcement = announcementService.findById(id);
            if (announcement == null) {
                return Result.error("公告不存在");
            }

            User viewer = requireAnnouncementViewer();
            if (!canViewAnnouncement(announcement, viewer)) {
                throw new AccessDeniedException("无权查看此公告");
            }
            markAnnouncementRead(announcement, viewer);
            return Result.success(announcement);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取公告详情失败：{}", e.getMessage(), e);
            return Result.error("获取公告详情失败：" + e.getMessage());
        }
    }

    // 显式标记公告已读，首页卡片与消息中心共用同一阅读记录
    @PostMapping("/{id}/read")
    @Log(operationType = "UPDATE", module = "ANNOUNCEMENT_MANAGEMENT", description = "标记公告已读")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public Result markAnnouncementReadApi(@PathVariable Long id) {
        try {
            Announcement announcement = announcementService.findById(id);
            if (announcement == null) {
                return Result.error("公告不存在");
            }
            User viewer = requireAnnouncementViewer();
            if (!canViewAnnouncement(announcement, viewer)) {
                throw new AccessDeniedException("无权查看此公告");
            }
            markAnnouncementRead(announcement, viewer);
            return Result.success(Map.of(
                    "isRead", Boolean.TRUE,
                    "readCount", announcement.getReadCount() == null ? 0 : announcement.getReadCount()
            ));
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("标记公告已读失败：{}", e.getMessage(), e);
            return Result.error("标记公告已读失败：" + e.getMessage());
        }
    }

    // 分页查询公告
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('announcement:view')")
    public Result getAnnouncementsByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String publisher,
            @RequestParam(required = false) String excludePublisherRole,
            @RequestParam(defaultValue = "false") boolean mine) {
        log.info("分页查询公告，页码：{}, 每页大小：{}, 标题：{}, 状态：{}, 发布人：{}, 排除角色：{}", page, pageSize, title, status, publisher, excludePublisherRole);
        try {
            User viewer = requireAnnouncementViewer();
            // 根据当前用户角色设置过滤规则
            String currentUserRole = com.internship.utils.CurrentHolder.getUserRole();
            String actualExcludeRole = excludePublisherRole;

            // 如果当前用户是辅导员，排除其他辅导员的消息
            // 只有在不指定发布人时才排除（查询"我的公告"时需要显示自己的公告）
            if (!mine && "ROLE_TEACHER_COUNSELOR".equals(currentUserRole) && actualExcludeRole == null && publisher == null) {
                actualExcludeRole = "COUNSELOR";
            }
            // 如果当前用户是系室教师或学院教师，也排除辅导员的消息
            // 只有在不指定发布人时才排除
            if (("ROLE_TEACHER_DEPARTMENT".equals(currentUserRole) || "ROLE_TEACHER_COLLEGE".equals(currentUserRole))
                    && !mine && actualExcludeRole == null && publisher == null) {
                actualExcludeRole = "COUNSELOR";
            }

            Long currentUserId = com.internship.utils.CurrentHolder.getUserId();
            String userId = currentUserId != null ? String.valueOf(currentUserId) : null;
            String userType = convertRoleToType(viewer.getRole());
            if (isAnnouncementAdmin(viewer) && !mine) {
                return Result.success(announcementService.findPage(page, pageSize, title, status,
                        publisher, actualExcludeRole, userId, userType));
            }
            List<Announcement> visible = announcementService.listForViewer(
                            title, status, publisher, actualExcludeRole, userId, userType)
                    .stream()
                    .filter(item -> canViewAnnouncement(item, viewer) || ownsAnnouncement(item, viewer))
                    .filter(item -> !mine || com.internship.service.AnnouncementOwnership.owns(item, viewer))
                    .toList();
            int safePage = page == null || page < 1 ? 1 : page;
            int safeSize = pageSize == null || pageSize < 1 ? 10 : pageSize;
            int start = (int) Math.min((long) (safePage - 1) * safeSize, visible.size());
            int end = (int) Math.min((long) start + safeSize, visible.size());
            int pages = (int) Math.ceil((double) visible.size() / safeSize);
            return Result.success(PageResult.build((long) visible.size(), visible.subList(start, end),
                    pages, safePage, safeSize));
        } catch (AccessDeniedException e) {
            throw e;
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
        log.info("收到新增公告请求");
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
            applyPublisherIdentityForCreate(announcement);

            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority("normal");

            // 教师用户只能设置目标群体为 STUDENT 或 MAJOR
            String currentUserRole = com.internship.utils.CurrentHolder.getUserRole();
            boolean isTeacher = currentUserRole != null && (
                currentUserRole.equals("ROLE_TEACHER") ||
                currentUserRole.equals("ROLE_TEACHER_COLLEGE") ||
                currentUserRole.equals("ROLE_TEACHER_DEPARTMENT") ||
                currentUserRole.equals("ROLE_TEACHER_COUNSELOR")
            );

            // 处理 targetType：如果是数组则转为 JSON 字符串，否则直接转换
            Object targetTypeObj = request.get("targetType");
            if (targetTypeObj != null) {
                if (targetTypeObj instanceof java.util.List) {
                    // 前端发送的是数组，转换为 JSON 字符串
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<?> targetTypeList = (List<?>) targetTypeObj;

                        // 教师用户验证目标群体
                        if (isTeacher) {
                            for (Object type : targetTypeList) {
                                if (!"STUDENT".equals(type) && !"MAJOR".equals(type)) {
                                    return Result.error("教师用户只能设置目标群体为\"全体学生\"或\"特定专业学生\"");
                                }
                            }
                        }

                        announcement.setTargetType(mapper.writeValueAsString(targetTypeObj));
                    } catch (Exception e) {
                        log.error("序列化 targetType 失败：{}", e.getMessage(), e);
                        announcement.setTargetType(targetTypeObj.toString());
                    }
                } else {
                    String targetTypeStr = (String) targetTypeObj;

                    // 教师用户验证目标群体（单个值的情况）
                    if (isTeacher && !"STUDENT".equals(targetTypeStr) && !"MAJOR".equals(targetTypeStr)) {
                        return Result.error("教师用户只能设置目标群体为\"全体学生\"或\"特定专业学生\"");
                    }

                    announcement.setTargetType((String) targetTypeObj);
                }
            } else {
                announcement.setTargetType("ALL");
            }

            announcement.setTargetValue((String) request.get("targetValue"));

            // 处理日期字段 - 优先使用 publishDate 和 expireDate（前端字段名），兼容 validFrom 和 validTo
            Object publishDate = request.get("publishDate");
            Object validFrom = request.get("validFrom");
            Object dateToUse = publishDate != null ? publishDate : validFrom;

            if (dateToUse != null) {
                if (dateToUse instanceof String && !((String) dateToUse).isEmpty()) {
                    try {
                        String dateStr = (String) dateToUse;
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
                        log.warn("解析发布日期失败：{}", dateToUse, e);
                    }
                } else if (dateToUse instanceof Date) {
                    announcement.setValidFrom((Date) dateToUse);
                }
            }

            // 处理过期日期 - 优先使用 expireDate（前端字段名），兼容 validTo
            Object expireDate = request.get("expireDate");
            Object validTo = request.get("validTo");
            Object toDateToUse = expireDate != null ? expireDate : validTo;

            if (toDateToUse != null) {
                if (toDateToUse instanceof String && !((String) toDateToUse).isEmpty()) {
                    try {
                        String dateStr = (String) toDateToUse;
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
                        log.warn("解析过期日期失败：{}", toDateToUse, e);
                    }
                } else if (toDateToUse instanceof Date) {
                    announcement.setValidTo((Date) toDateToUse);
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

            validateTeacherAudience(announcement);
            int result = announcementService.insert(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("添加公告成功", result);
        } catch (AccessDeniedException e) {
            throw e;
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
        log.info("收到带附件的新增公告请求");
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
            applyPublisherIdentityForCreate(announcement);

            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority("normal");

            // 教师用户只能设置目标群体为 STUDENT 或 MAJOR
            String currentUserRole = com.internship.utils.CurrentHolder.getUserRole();
            boolean isTeacher = currentUserRole != null && (
                currentUserRole.equals("ROLE_TEACHER") ||
                currentUserRole.equals("ROLE_TEACHER_COLLEGE") ||
                currentUserRole.equals("ROLE_TEACHER_DEPARTMENT") ||
                currentUserRole.equals("ROLE_TEACHER_COUNSELOR")
            );

            // 处理 targetType：如果是数组则转为 JSON 字符串，否则直接转换
            Object targetTypeObj = request.get("targetType");
            if (targetTypeObj != null) {
                if (targetTypeObj instanceof java.util.List) {
                    // 前端发送的是数组，转换为 JSON 字符串
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<?> targetTypeList = (List<?>) targetTypeObj;

                        // 教师用户验证目标群体
                        if (isTeacher) {
                            for (Object type : targetTypeList) {
                                if (!"STUDENT".equals(type) && !"MAJOR".equals(type)) {
                                    return Result.error("教师用户只能设置目标群体为\"全体学生\"或\"特定专业学生\"");
                                }
                            }
                        }

                        announcement.setTargetType(mapper.writeValueAsString(targetTypeObj));
                    } catch (Exception e) {
                        log.error("序列化 targetType 失败：{}", e.getMessage(), e);
                        announcement.setTargetType(targetTypeObj.toString());
                    }
                } else {
                    String targetTypeStr = (String) targetTypeObj;

                    // 教师用户验证目标群体（单个值的情况）
                    if (isTeacher && !"STUDENT".equals(targetTypeStr) && !"MAJOR".equals(targetTypeStr)) {
                        return Result.error("教师用户只能设置目标群体为\"全体学生\"或\"特定专业学生\"");
                    }

                    announcement.setTargetType((String) targetTypeObj);
                }
            } else {
                announcement.setTargetType("ALL");
            }

            announcement.setTargetValue((String) request.get("targetValue"));

            // 处理日期字段 - 优先使用 publishDate 和 expireDate（前端字段名），兼容 validFrom 和 validTo
            Object publishDate = request.get("publishDate");
            Object validFrom = request.get("validFrom");
            Object dateToUse = publishDate != null ? publishDate : validFrom;

            if (dateToUse != null) {
                if (dateToUse instanceof String && !((String) dateToUse).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) dateToUse;
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
                            log.warn("所有日期格式解析失败：{}", dateToUse);
                        }
                    } catch (Exception e) {
                        log.warn("解析发布日期失败：{}", dateToUse, e);
                    }
                } else if (dateToUse instanceof Date) {
                    announcement.setValidFrom((Date) dateToUse);
                }
            }

            // 处理过期日期 - 优先使用 expireDate（前端字段名），兼容 validTo
            Object expireDate = request.get("expireDate");
            Object validTo = request.get("validTo");
            Object toDateToUse = expireDate != null ? expireDate : validTo;

            if (toDateToUse != null) {
                if (toDateToUse instanceof String && !((String) toDateToUse).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) toDateToUse;
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
                            log.warn("所有日期格式解析失败：{}", toDateToUse);
                        }
                    } catch (Exception e) {
                        log.warn("解析过期日期失败：{}", toDateToUse, e);
                    }
                } else if (toDateToUse instanceof Date) {
                    announcement.setValidTo((Date) toDateToUse);
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

            log.info("准备插入公告数据：status={}", announcement.getStatus());

            validateTeacherAudience(announcement);
            int result = announcementService.insert(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("添加公告成功", result);
        } catch (AccessDeniedException e) {
            throw e;
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
            Announcement existing = announcementService.findById(id);
            if (existing == null) {
                return Result.error("公告不存在");
            }
            User actor = requireManagePermission(existing);
            if (!isAnnouncementAdmin(actor)) {
                announcement.setPublisher(existing.getPublisher());
                announcement.setPublisherRole(existing.getPublisherRole());
            }
            announcement.setId(id);
            validateTeacherAudience(announcement);
            int result = announcementService.update(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("更新公告成功", result);
        } catch (AccessDeniedException e) {
            throw e;
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
        log.info("收到带附件的公告更新请求：id={}", id);
        try {
            Announcement existing = announcementService.findById(id);
            if (existing == null) {
                return Result.error("公告不存在");
            }
            User actor = requireManagePermission(existing);

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
            if (!isAnnouncementAdmin(actor)) {
                announcement.setPublisher(existing.getPublisher());
                announcement.setPublisherRole(existing.getPublisherRole());
            }

            announcement.setStatus((String) request.getOrDefault("status", "DRAFT"));
            announcement.setPriority(null);

            // 教师用户只能设置目标群体为 STUDENT 或 MAJOR
            String currentUserRole = com.internship.utils.CurrentHolder.getUserRole();
            boolean isTeacher = currentUserRole != null && (
                currentUserRole.equals("ROLE_TEACHER") ||
                currentUserRole.equals("ROLE_TEACHER_COLLEGE") ||
                currentUserRole.equals("ROLE_TEACHER_DEPARTMENT") ||
                currentUserRole.equals("ROLE_TEACHER_COUNSELOR")
            );

            // 处理 targetType：如果是数组则转为 JSON 字符串，否则直接转换
            Object targetTypeObj = request.get("targetType");
            if (targetTypeObj != null) {
                if (targetTypeObj instanceof java.util.List) {
                    // 前端发送的是数组，转换为 JSON 字符串
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        List<?> targetTypeList = (List<?>) targetTypeObj;

                        // 教师用户验证目标群体
                        if (isTeacher) {
                            for (Object type : targetTypeList) {
                                if (!"STUDENT".equals(type) && !"MAJOR".equals(type)) {
                                    return Result.error("教师用户只能设置目标群体为\"全体学生\"或\"特定专业学生\"");
                                }
                            }
                        }

                        announcement.setTargetType(mapper.writeValueAsString(targetTypeObj));
                    } catch (Exception e) {
                        log.error("序列化 targetType 失败：{}", e.getMessage(), e);
                        announcement.setTargetType(targetTypeObj.toString());
                    }
                } else {
                    String targetTypeStr = (String) targetTypeObj;

                    // 教师用户验证目标群体（单个值的情况）
                    if (isTeacher && !"STUDENT".equals(targetTypeStr) && !"MAJOR".equals(targetTypeStr)) {
                        return Result.error("教师用户只能设置目标群体为\"全体学生\"或\"特定专业学生\"");
                    }

                    announcement.setTargetType((String) targetTypeObj);
                }
            } else {
                announcement.setTargetType("ALL");
            }

            announcement.setTargetValue((String) request.get("targetValue"));

            // 处理日期字段 - 优先使用 publishDate 和 expireDate（前端字段名），兼容 validFrom 和 validTo
            Object publishDate = request.get("publishDate");
            Object validFrom = request.get("validFrom");
            Object dateToUse = publishDate != null ? publishDate : validFrom;

            if (dateToUse != null) {
                if (dateToUse instanceof String && !((String) dateToUse).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) dateToUse;
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
                            log.warn("所有日期格式解析失败：{}", dateToUse);
                        }
                    } catch (Exception e) {
                        log.warn("解析发布日期失败：{}", dateToUse, e);
                    }
                } else if (dateToUse instanceof Date) {
                    announcement.setValidFrom((Date) dateToUse);
                }
            }

            // 处理过期日期 - 优先使用 expireDate（前端字段名），兼容 validTo
            Object expireDate = request.get("expireDate");
            Object validTo = request.get("validTo");
            Object toDateToUse = expireDate != null ? expireDate : validTo;

            if (toDateToUse != null) {
                if (toDateToUse instanceof String && !((String) toDateToUse).isEmpty()) {
                    try {
                        // 尝试多种日期格式解析
                        String dateStr = (String) toDateToUse;
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
                            log.warn("所有日期格式解析失败：{}", toDateToUse);
                        }
                    } catch (Exception e) {
                        log.warn("解析过期日期失败：{}", toDateToUse, e);
                    }
                } else if (toDateToUse instanceof Date) {
                    announcement.setValidTo((Date) toDateToUse);
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

            log.info("准备更新公告数据：id={}, status={}", id, announcement.getStatus());

            validateTeacherAudience(announcement);
            int result = announcementService.update(announcement);
            
            if ("PUBLISHED".equals(announcement.getStatus())) {
                pushAnnouncementNotification(announcement);
            }
            
            return Result.success("更新公告成功", result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新公告失败：{}", e.getMessage(), e);
            return Result.error("更新公告失败：" + e.getMessage());
        }
    }

    // 删除公告
    @Log(operationType = "DELETE", module = "ANNOUNCEMENT_MANAGEMENT", description = "删除公告")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('announcement:delete') or hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result deleteAnnouncement(@PathVariable Long id) {
        log.info("删除公告：ID={}", id);
        try {
            // 检查公告是否存在
            Announcement announcement = announcementService.findById(id);
            if (announcement == null) {
                return Result.error("公告不存在");
            }

            requireManagePermission(announcement);

            int result = announcementService.delete(id);
            return Result.success("删除公告成功", result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除公告失败：{}", e.getMessage(), e);
            return Result.error("删除公告失败：" + e.getMessage());
        }
    }

    // 批量删除公告
    @Log(operationType = "DELETE", module = "ANNOUNCEMENT_MANAGEMENT", description = "批量删除公告", riskLevel = "HIGH", critical = true)
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('announcement:delete') or hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
    public Result batchDeleteAnnouncement(@RequestBody Map<String, List<Long>> request) {
        List<Long> ids = request.get("ids");
        log.info("批量删除公告，IDs: {}", ids);
        try {
            if (ids == null || ids.isEmpty()) {
                return Result.error("公告 ID 列表不能为空");
            }

            for (Long id : ids) {
                Announcement announcement = announcementService.findById(id);
                if (announcement == null) {
                    return Result.error("公告不存在：ID=" + id);
                }
                requireManagePermission(announcement);
            }

            int result = announcementService.batchDelete(ids);
            return Result.success("批量删除公告成功", result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量删除公告失败：{}", e.getMessage(), e);
            return Result.error("批量删除公告失败：" + e.getMessage());
        }
    }

    // 根据标题和状态搜索公告
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public Result searchAnnouncements(
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String status) {
        log.info("搜索公告，标题：{}, 状态：{}", title, status);
        try {
            User viewer = requireAnnouncementViewer();
            List<Announcement> announcements;
            if (isAnnouncementAdmin(viewer)) {
                announcements = announcementService.list(title, status);
            } else if (status != null && !status.isBlank() && !"PUBLISHED".equals(status)) {
                announcements = List.of();
            } else {
                announcements = visibleAnnouncements(announcementService.list(title, "PUBLISHED"), viewer);
            }
            return Result.success(announcements);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("搜索公告失败：{}", e.getMessage(), e);
            return Result.error("搜索公告失败：" + e.getMessage());
        }
    }

    // 根据用户类型和用户信息获取公告列表
    @GetMapping("/user")
    @PreAuthorize("hasAnyRole('ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_STUDENT')")
    public Result getAnnouncementsForUser(
            @RequestParam String userType,
            @RequestParam(required = false) String userInfo,
            @RequestParam(required = false) String userId) {
        log.info("根据用户类型获取公告列表：userType={}, hasUserId={}",
                userType, userId != null && !userId.isBlank());
        try {
            User viewer = requireAnnouncementViewer();
            String trustedType = convertRoleToType(viewer.getRole());
            String trustedInfo = switch (trustedType) {
                case "STUDENT" -> getStudentMajorId(viewer.getId());
                case "TEACHER" -> getTeacherTypeFromRole(viewer.getRole());
                default -> null;
            };
            if ("UNKNOWN".equals(trustedType) || ("TEACHER".equals(trustedType) && trustedInfo == null)) {
                throw new AccessDeniedException("公告阅读身份不可用");
            }
            String queryType = "ENTERPRISE".equals(trustedType) ? "COMPANY" : trustedType;
            List<Announcement> announcements = visibleAnnouncements(
                    announcementService.getAnnouncementsForUser(queryType, trustedInfo,
                            String.valueOf(viewer.getId()), trustedType), viewer);
            return Result.success(announcements);
        } catch (AccessDeniedException e) {
            throw e;
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
                List<com.internship.entity.AdminUser> adminUsers = userService.getAllAdminUsers();
                for (com.internship.entity.AdminUser admin : adminUsers) {
                    UserVO vo = new UserVO();
                    vo.setId(admin.getId());
                    vo.setName(admin.getName());
                    vo.setRole(admin.getRole());
                    users.add(vo);
                }
            } else if ("COLLEGE".equals(publisherRole) || "DEPARTMENT".equals(publisherRole) || "COUNSELOR".equals(publisherRole)) {
                List<com.internship.entity.TeacherUser> teacherUsers = userService.getTeachersByType(publisherRole);
                for (com.internship.entity.TeacherUser teacher : teacherUsers) {
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
    @Log(operationType = "EXPORT", module = "ANNOUNCEMENT_MANAGEMENT", description = "导出公告数据", riskLevel = "MEDIUM", critical = true)
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
            String[] headers = {"标题", "内容", "发布人", "发布人身份", "状态", "目标群体", "发布日期", "阅读量", "创建时间", "更新时间"};
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
                dataRow.createCell(5).setCellValue(convertTargetTypeToChinese(announcement.getTargetType()));

                if (announcement.getPublishTime() != null) {
                    dataRow.createCell(6).setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(announcement.getPublishTime()));
                } else {
                    dataRow.createCell(6).setCellValue("");
                }

                dataRow.createCell(7).setCellValue(announcement.getReadCount() != null ? announcement.getReadCount() : 0);

                if (announcement.getCreateTime() != null) {
                    dataRow.createCell(8).setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(announcement.getCreateTime()));
                } else {
                    dataRow.createCell(8).setCellValue("");
                }

                if (announcement.getUpdateTime() != null) {
                    dataRow.createCell(9).setCellValue(new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(announcement.getUpdateTime()));
                } else {
                    dataRow.createCell(9).setCellValue("");
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
    @Log(operationType = "IMPORT", module = "ANNOUNCEMENT_MANAGEMENT", description = "批量导入公告数据", riskLevel = "MEDIUM")
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

    @Log(operationType = "DOWNLOAD", module = "ANNOUNCEMENT_MANAGEMENT", description = "下载公告附件", riskLevel = "MEDIUM")
    @GetMapping("/download-attachment")
    @PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_TEACHER', 'ROLE_TEACHER_COLLEGE', 'ROLE_TEACHER_DEPARTMENT', 'ROLE_TEACHER_COUNSELOR', 'ROLE_STUDENT', 'ROLE_COMPANY')")
    public ResponseEntity<InputStreamResource> downloadAttachment(
            @RequestParam String announcementId,
            @RequestParam String fileName) {
        log.info("下载公告附件，公告 ID: {}, 文件名：{}", announcementId, fileName);
        try {
            Announcement announcement = announcementService.findById(Long.parseLong(announcementId));
            if (announcement == null) {
                return ResponseEntity.notFound().build();
            }
            if (!canViewAnnouncement(announcement, requireAnnouncementViewer())) {
                throw new AccessDeniedException("无权下载此公告附件");
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

            attachmentPolicy.verifyDownload(announcement.getId(), targetAttachment);
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
        } catch (AccessDeniedException e) {
            throw e;
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
            String[] headers = {"标题", "内容", "发布人", "发布人身份", "状态", "目标群体", "目标值", "说明"};

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
            descCell.setCellValue("说明：标题、内容为必填项；发布人身份：ADMIN/COLLEGE/DEPARTMENT/COUNSELOR；状态：DRAFT/PUBLISHED；目标群体：ALL/STUDENT/TEACHER/MAJOR/COMPANY");

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
            announcementData.put("targetType", announcement.getTargetType());
            announcementData.put("publishTime", announcement.getPublishTime());
            
            // 解析 targetType JSON 数组，精确匹配避免子串歧义
            java.util.List<String> targetTypes = parseTargetTypeList(announcement.getTargetType());

            if (targetTypes.contains("ALL")) {
                log.info("广播公告到所有用户：{}", announcement.getTitle());
                webSocketHandler.broadcastAnnouncement(announcementData);
            } else {
                if (targetTypes.contains("COMPANY")) {
                    log.info("推送公告到企业端：{}", announcement.getTitle());
                    webSocketHandler.sendAnnouncementToRole("COMPANY", announcementData);
                }
                if (targetTypes.contains("STUDENT")) {
                    log.info("推送公告到学生端：{}", announcement.getTitle());
                    webSocketHandler.sendAnnouncementToRole("STUDENT", announcementData);
                }
                if (targetTypes.contains("TEACHER")) {
                    log.info("推送公告到教师端：{}", announcement.getTitle());
                    webSocketHandler.sendAnnouncementToRole("TEACHER", announcementData);
                } else if (targetTypes.contains("TEACHER_TYPE")) {
                    for (String type : java.util.List.of("COLLEGE", "DEPARTMENT", "COUNSELOR")) {
                        if (isUserInfoInJsonArray(announcement.getTargetValue(), "teacherTypes", type)) {
                            webSocketHandler.sendAnnouncementToRole("TEACHER_" + type, announcementData);
                        }
                    }
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

    private User requireAnnouncementViewer() {
        User viewer = com.internship.utils.CurrentHolder.getUser();
        if (viewer == null || viewer.getId() == null || viewer.getId() <= 0
                || viewer.getRole() == null
                || (viewer.getRole().startsWith("ROLE_TEACHER")
                    && getTeacherTypeFromRole(viewer.getRole()) == null)) {
            throw new AccessDeniedException("公告阅读身份不可用");
        }
        return viewer;
    }

    private boolean isAnnouncementAdmin(User viewer) {
        return "ROLE_ADMIN".equals(viewer.getRole());
    }

    private boolean isTeacherUser(User user) {
        return user != null && user.getRole() != null && user.getRole().startsWith("ROLE_TEACHER");
    }

    private String normalizePublisherRole(String publisherRole) {
        if (publisherRole == null || publisherRole.isBlank()) return null;
        return switch (publisherRole) {
            case "ROLE_TEACHER_COLLEGE", "COLLEGE" -> "COLLEGE";
            case "ROLE_TEACHER_DEPARTMENT", "DEPARTMENT" -> "DEPARTMENT";
            case "ROLE_TEACHER_COUNSELOR", "COUNSELOR" -> "COUNSELOR";
            case "ROLE_ADMIN", "ADMIN" -> "ADMIN";
            default -> publisherRole;
        };
    }

    private boolean ownsAnnouncement(Announcement announcement, User user) {
        return isTeacherUser(user) && com.internship.service.AnnouncementOwnership.owns(announcement, user);
    }

    private User requireManagePermission(Announcement announcement) {
        User actor = requireAnnouncementViewer();
        if (!isAnnouncementAdmin(actor) && !ownsAnnouncement(announcement, actor)) {
            throw new AccessDeniedException("只能编辑或删除自己发布的公告");
        }
        return actor;
    }

    private void applyPublisherIdentityForCreate(Announcement announcement) {
        User actor = requireAnnouncementViewer();
        com.internship.service.AnnouncementOwnership.bindCreator(announcement);
        if (isAnnouncementAdmin(actor)) return;
        if (!isTeacherUser(actor)) {
            throw new AccessDeniedException("当前用户不能发布公告");
        }
        String teacherType = getTeacherTypeFromRole(actor.getRole());
        if (teacherType == null) {
            throw new AccessDeniedException("教师发布身份不可用");
        }
        announcement.setPublisher(actor.getName());
        announcement.setPublisherRole(teacherType);
    }

    private void markAnnouncementRead(Announcement announcement, User viewer) {
        Long userId = viewer.getId();
        String userType = convertRoleToType(viewer.getRole());
        AnnouncementReadRecord existing = announcementReadRecordService
                .findByAnnouncementAndUser(announcement.getId(), String.valueOf(userId), userType);
        if (existing == null) {
            AnnouncementReadRecord record = new AnnouncementReadRecord();
            record.setAnnouncementId(announcement.getId());
            record.setUserId(String.valueOf(userId));
            record.setUserType(userType);
            record.setReadTime(new Date());
            announcementReadRecordService.insert(record);
        }
        Long count = announcementReadRecordService.countReadByAnnouncementId(announcement.getId());
        announcement.setReadCount(count == null ? 0 : count.intValue());
        announcement.setIsRead(Boolean.TRUE);
    }

    private void validateTeacherAudience(Announcement announcement) {
        User actor = requireAnnouncementViewer();
        if (isAnnouncementAdmin(actor)) return;
        String target = announcement.getTargetType();
        if (target == null && announcement.getId() != null) {
            Announcement previous = announcementService.findById(announcement.getId());
            if (previous != null) target = previous.getTargetType();
        }
        List<String> types = com.internship.service.AnnouncementAudiencePolicy.targetTypes(target);
        if (types.isEmpty() || types.stream().anyMatch(type -> !"STUDENT".equals(type) && !"MAJOR".equals(type))) {
            throw new AccessDeniedException("教师仅能向学生或指定专业发布公告");
        }
        announcement.setTargetType(target);
    }

    private List<Announcement> visibleAnnouncements(List<Announcement> candidates, User viewer) {
        if (candidates == null) return List.of();
        return candidates.stream().filter(item -> canViewAnnouncement(item, viewer)).toList();
    }

    private boolean canViewAnnouncement(Announcement announcement, User viewer) {
        if (announcement == null || viewer == null) return false;
        if (isAnnouncementAdmin(viewer) || ownsAnnouncement(announcement, viewer)) return true;
        Date now = new Date();
        if (!"PUBLISHED".equals(announcement.getStatus())
                || (announcement.getPublishTime() != null && announcement.getPublishTime().after(now))
                || (announcement.getValidFrom() != null && announcement.getValidFrom().after(now))) {
            return false;
        }
        String role = viewer.getRole();
        String type = convertRoleToType(role);
        if ("UNKNOWN".equals(type)) return false;
        return isUserInTargetAudience(parseTargetTypeList(announcement.getTargetType()), type,
                announcement.getTargetValue(), viewer.getId(), role);
    }

    /**
     * 解析 targetType 字段为字符串列表
     * 兼容 JSON 数组格式（如 ["STUDENT","TEACHER"]）和旧纯字符串格式（如 STUDENT）
     */
    private java.util.List<String> parseTargetTypeList(String targetType) {
        if (targetType == null || targetType.isBlank()) {
            return java.util.Collections.emptyList();
        }
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(targetType, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
        } catch (Exception e) {
            // 兼容旧格式：纯字符串（非 JSON 数组）
            String[] parts = targetType.split(",");
            java.util.List<String> result = new java.util.ArrayList<>();
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            return result;
        }
    }

    /**
     * 判断用户是否在公告的目标群体中
     * 使用 JSON 解析进行精确匹配，避免子串攻击
     */
    private boolean isUserInTargetAudience(java.util.List<String> targetTypes, String userType,
                                           String targetValue, Long userId, String role) {
        if (targetTypes == null || targetTypes.isEmpty()) {
            return false;
        }

        // ALL 对所有人可见
        if (targetTypes.contains("ALL")) {
            return true;
        }

        if ("STUDENT".equals(userType)) {
            if (targetTypes.contains("STUDENT")) return true;
            if (targetTypes.contains("MAJOR")) {
                if (targetValue == null || targetValue.isBlank()) return false;
                try {
                    String userInfo = getStudentMajorId(userId);
                    if (userInfo == null) return false;
                    return isUserInfoInJsonArray(targetValue, "majorIds", userInfo);
                } catch (Exception ignored) {}
            }
        } else if ("TEACHER".equals(userType)) {
            if (targetTypes.contains("TEACHER")) return true;
            if (targetTypes.contains("TEACHER_TYPE")) {
                if (targetValue == null || targetValue.isBlank()) return false;
                try {
                    String teacherType = getTeacherTypeFromRole(role);
                    if (teacherType == null) return false;
                    return isUserInfoInJsonArray(targetValue, "teacherTypes", teacherType);
                } catch (Exception ignored) {}
            }
        } else if ("ENTERPRISE".equals(userType)) {
            if (targetTypes.contains("COMPANY")) return true;
        }

        return false;
    }

    /**
     * 解析 targetValue JSON，检查 userInfo 是否在指定数组中
     * 使用 JSON 精确解析而非字符串包含，避免子串碰撞
     */
    private boolean isUserInfoInJsonArray(String targetValue, String arrayKey, String userInfo) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(targetValue);
            com.fasterxml.jackson.databind.JsonNode array = root.get(arrayKey);
            if (array != null && array.isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode node : array) {
                    if (userInfo.equals(node.asText())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * 获取学生的专业ID（字符串）
     */
    private String getStudentMajorId(Long userId) {
        if (userId == null) return null;
        try {
            com.internship.entity.StudentUser studentUser = studentUserMapper.findById(userId);
            if (studentUser != null && studentUser.getMajorId() != null) {
                return String.valueOf(studentUser.getMajorId());
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * 从角色中提取教师类别（COLLEGE/DEPARTMENT/COUNSELOR）
     */
    private String getTeacherTypeFromRole(String role) {
        return switch (role == null ? "" : role) {
            case "ROLE_TEACHER_COLLEGE" -> "COLLEGE";
            case "ROLE_TEACHER_DEPARTMENT" -> "DEPARTMENT";
            case "ROLE_TEACHER_COUNSELOR" -> "COUNSELOR";
            default -> null;
        };
    }
}
