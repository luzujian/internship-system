package com.internship.controller;

import com.internship.utils.ExcelExports;

import com.internship.anno.Log;
import com.internship.dto.TeacherCompanyDTO;
import com.internship.entity.*;
import com.internship.entity.Class;
import com.internship.entity.*;
import com.internship.exception.BusinessException;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.DepartmentMapper;
import com.internship.mapper.DivisionMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.service.*;
import com.internship.service.MajorService;
import com.internship.service.CompanyUserService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import com.internship.utils.MaterialMetadataRedactor;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 教师控制器
 * 处理教师相关的所有接口请求，包括实习管理、成绩管理、学生管理等功能
 */
@Slf4j
@RestController
@RequestMapping("/api/teacher")
@PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
@Validated
public class TeacherController {

    @Autowired
    private com.internship.service.JobSearchStatusService jobSearchStatusService;

    @Autowired
    private UserService userService;

    @Autowired
    private ClassService classService;

    @Autowired
    private MajorService majorService;

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private CompanyRelationsService companyRelationsService;

    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private StudentInternshipStatusMapper studentInternshipStatusMapper;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private AnnouncementService announcementService;

    @Autowired
    private AnnouncementReadRecordService announcementReadRecordService;

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private InternshipStageAssignmentService internshipStageAssignmentService;

    @Autowired
    private PositionService positionService;

    @Autowired
    private DivisionMapper divisionMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Autowired
    private ClassCounselorRelationMapper classCounselorRelationMapper;

    @GetMapping("/current")
    public Result getCurrentTeacherInfo() {
        log.info("获取当前登录教师信息");
        try {
            Long userId = CurrentHolder.getUserId();
            if (userId == null) {
                log.warn("未获取到当前登录用户ID");
                return Result.error("未登录或登录已过期");
            }
            
            User user = CurrentHolder.getUser();
            if (user == null || user.getUsername() == null) {
                log.warn("未获取到当前登录用户信息");
                return Result.error("未登录或登录已过期");
            }
            
            com.internship.entity.TeacherUser teacher = teacherUserService.findByTeacherUserId(user.getUsername());
            if (teacher == null) {
                log.warn("教师信息不存在，username: {}", user.getUsername());
                return Result.error("教师信息不存在");
            }
            return Result.success(teacher);
        } catch (Exception e) {
            log.error("获取当前教师信息失败: {}", e.getMessage(), e);
            return Result.error("获取教师信息失败");
        }
    }

    @GetMapping("/notifications")
    public Result getNotifications(@RequestParam(defaultValue = "5") Integer limit) {
        log.info("获取教师通知：limit={}", limit);
        try {
            Long teacherId = getCurrentUserId();
            if (teacherId == null) {
                log.warn("未找到教师用户ID");
                return Result.error("未找到教师用户信息");
            }
            log.info("当前教师用户ID: {}", teacherId);

            // 获取教师类型，用于按 targetType 过滤公告
            String teacherType = null;
            try {
                com.internship.entity.TeacherUser teacher = teacherUserService.findById(teacherId);
                if (teacher != null && teacher.getTeacherType() != null) {
                    teacherType = teacher.getTeacherType();
                }
            } catch (Exception e) {
                log.warn("获取教师类型失败: {}", e.getMessage());
            }
            log.info("当前教师类型: {}", teacherType);

            // 按目标群体过滤公告
            List<Announcement> allAnnouncements = announcementService.getAnnouncementsForUser(
                    "TEACHER", teacherType, String.valueOf(teacherId), "TEACHER");

            // 学院教师和系室教师不显示辅导员发布的公告
            String currentUserRole = com.internship.utils.CurrentHolder.getUserRole();
            boolean excludeCounselor = "ROLE_TEACHER_COLLEGE".equals(currentUserRole)
                    || "ROLE_TEACHER_DEPARTMENT".equals(currentUserRole)
                    || "ROLE_TEACHER_COUNSELOR".equals(currentUserRole);
            if (excludeCounselor) {
                allAnnouncements = allAnnouncements.stream()
                    .filter(a -> !"COUNSELOR".equals(a.getPublisherRole()))
                    .toList();
            }

            // 首页公告栏固定展示最近通知，已读和未读均保留，点击只改变阅读状态
            java.util.Map<Long, Boolean> readStatusMap = new java.util.HashMap<>();
            try {
                for (com.internship.entity.AnnouncementReadRecord record :
                        announcementReadRecordService.findByUserId(String.valueOf(teacherId), "TEACHER")) {
                    if (record.getAnnouncementId() != null) {
                        readStatusMap.put(record.getAnnouncementId(), true);
                    }
                }
            } catch (Exception e) {
                log.warn("批量查询已读记录失败: {}", e.getMessage());
            }

            int safeLimit = limit == null || limit < 1 ? 5 : limit;
            List<java.util.Map<String, Object>> result = allAnnouncements.stream()
                    .limit(safeLimit)
                    .map(announcement -> {
                        boolean isRead = Boolean.TRUE.equals(announcement.getIsRead())
                                || readStatusMap.getOrDefault(announcement.getId(), false);
                        announcement.setIsRead(isRead);
                        java.util.Map<String, Object> item = new java.util.HashMap<>();
                        item.put("id", announcement.getId());
                        item.put("type", "system");
                        item.put("title", announcement.getTitle());
                        item.put("content", announcement.getContent());
                        item.put("time", formatTime(announcement.getPublishTime()));
                        item.put("publishTime", announcement.getPublishTime());
                        item.put("publisher", announcement.getPublisher());
                        item.put("publisherRole", announcement.getPublisherRole());
                        item.put("readCount", announcement.getReadCount());
                        item.put("isRead", isRead);
                        return item;
                    })
                    .toList();

            log.info("教师通知查询完成：count={}", result.size());
            return Result.success(result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取教师通知失败：{}", e.getMessage(), e);
            return Result.error("获取教师通知失败：" + e.getMessage());
        }
    }

    private String formatTime(java.util.Date date) {
        if (date == null) return "";
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return sdf.format(date);
    }

    @GetMapping("/majors")
    public Result getMajors() {
        log.info("教师端获取专业列表");
        try {
            List<com.internship.entity.Major> majors = majorService.findAll();
            log.info("查询到的专业数量: {}", majors != null ? majors.size() : 0);
            if (majors != null && !majors.isEmpty()) {
                log.info("第一个专业: id={}, name={}", majors.get(0).getId(), majors.get(0).getName());
            }
            return Result.success(majors);
        } catch (Exception e) {
            log.error("获取专业列表失败：{}", e.getMessage(), e);
            return Result.error("获取专业列表失败");
        }
    }

    @GetMapping("/grades")
    public Result getGrades() {
        log.info("教师端获取年级列表");
        try {
            List<Integer> grades = userService.findDistinctGrades();
            log.info("查询到的年级数量: {}", grades != null ? grades.size() : 0);
            if (grades != null && !grades.isEmpty()) {
                log.info("第一个年级: {}", grades.get(0));
            }
            return Result.success(grades);
        } catch (Exception e) {
            log.error("获取年级列表失败：{}", e.getMessage(), e);
            return Result.error("获取年级列表失败");
        }
    }

    @GetMapping("/all-classes")
    public Result getClasses() {
        log.info("教师端获取所有班级列表");
        try {
            List<com.internship.entity.Class> classes = classService.findAll();
            log.info("查询到的班级数量: {}", classes != null ? classes.size() : 0);
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取班级列表失败：{}", e.getMessage(), e);
            return Result.error("获取班级列表失败");
        }
    }

    @GetMapping("/companies")
    public Result getCompanies(@RequestParam(defaultValue = "1") Integer page,
                               @RequestParam(defaultValue = "1000") Integer pageSize,
                               @RequestParam(required = false) String companyName,
                               @RequestParam(required = false) Integer status,
                               @RequestParam(required = false) Integer auditStatus,
                               @RequestParam(required = false) Integer recallStatus,
                               @RequestParam(required = false) String companyTag,
                               @RequestParam(required = false) String industry,
                               @RequestParam(required = false) String keyword,
                               @RequestParam(required = false) Integer visitedYear,
                               @RequestParam(required = false) Integer visitState,
                               @RequestParam(required = false) Long visitTeacherId,
                               @RequestParam(required = false) Long liaisonTeacherId) {
        log.info("教师端获取企业列表（用于下拉框），页码：{}, 每页条数：{}, 企业名称：{}, 行业：{}", page, pageSize, companyName, industry);
        try {
            PageResult<TeacherCompanyDTO> pageResult = companyRelationsService.findTeacherCompanies(
                    page, pageSize, companyName, status, auditStatus, recallStatus, companyTag, industry,
                    keyword, visitedYear, visitState, visitTeacherId, liaisonTeacherId);
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("获取企业列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业列表失败");
        }
    }

    @GetMapping("/companies/tags")
    public Result getCompanyTags() {
        try {
            List<String> tags = companyUserService.getDistinctTags();
            return Result.success(tags);
        } catch (Exception e) {
            log.error("获取企业标签列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业标签列表失败");
        }
    }

    @GetMapping("/students")
    public Result getStudents(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer pageSize,
                              @RequestParam(required = false) String searchName,
                              @RequestParam(required = false) Long classId) {
        log.info("教师端获取学生列表，页码：{}, 每页条数：{}, 搜索姓名：{}, 班级ID：{}", page, pageSize, searchName, classId);
        try {
            ScopeInfo scope = resolveTeacherScope();
            PageResult<com.internship.entity.StudentUser> pageResult = studentUserService.findPageByTeacherScope(
                    page, pageSize, searchName, classId,
                    scope.departmentIds, scope.divisionIds, scope.classIds);
            return Result.success(pageResult);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取学生列表失败：{}", e.getMessage(), e);
            return Result.error("获取学生列表失败");
        }
    }

    @GetMapping("/companies/{id:\\d+}")
    public Result getCompanyById(@PathVariable Long id) {
        log.info("教师端根据 ID 获取企业信息：{}", id);
        try {
            TeacherCompanyDTO company = companyRelationsService.findCompanyDetail(id);
            if (company == null) {
                return Result.error("企业不存在");
            }
            return Result.success(company);
        } catch (Exception e) {
            log.error("获取企业信息失败: {}", e.getMessage(), e);
            return Result.error("获取企业信息失败");
        }
    }

    @GetMapping("/companies/audit/pending")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getPendingAuditCompanies(@RequestParam(defaultValue = "1") Integer page,
                                           @RequestParam(defaultValue = "10") Integer pageSize,
                                           @RequestParam(required = false) String companyName,
                                           @RequestParam(required = false) String contactPerson,
                                           @RequestParam(required = false) String contactPhone,
                                           @RequestParam(required = false) Integer status) {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        log.info("当前用户: {}, 权限列表: {}", authentication.getName(), authentication.getAuthorities());

        log.info("获取企业注册申请列表，页码: {}, 每页条数: {}, 企业名称: {}, 联系人: {}, 联系电话: {}, 状态: {}",
                page, pageSize, companyName, contactPerson, contactPhone, status);
        try {
            // 未指定状态时默认只看待审核（该接口语义为“待审核企业列表”），传 null 会查询全部状态
            Integer queryStatus = status != null ? status : 0;
            PageResult<CompanyUser> pageResult = companyUserService.findPendingAuditPage(page, pageSize, companyName, contactPerson, contactPhone, queryStatus);
            return Result.success(MaterialMetadataRedactor.companyPage(pageResult));
        } catch (Exception e) {
            log.error("获取企业注册申请列表失败: {}", e.getMessage(), e);
            return Result.error("获取企业注册申请列表失败");
        }
    }

    @GetMapping("/companies/statistics")
    @PreAuthorize("hasAuthority('user:company:audit')")
    public Result getCompanyStatistics() {
        log.info("获取企业审核统计数据");
        try {
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("pending", companyUserService.countByAuditStatus(0));
            statistics.put("approved", companyUserService.countByAuditStatus(1));
            statistics.put("rejected", companyUserService.countByAuditStatus(2));
            statistics.put("total", companyUserService.count());
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取统计数据失败");
        }
    }

    @PutMapping("/companies/{id}/audit")
    @PreAuthorize("hasAuthority('user:company:audit')")
    @Log(operationType = "AUDIT", module = "COMPANY_MANAGEMENT", description = "审核企业注册申请")
    public Result auditCompany(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("审核企业注册：id={}", id);
        try {
            Integer auditStatus = Integer.valueOf(params.get("auditStatus").toString());
            String auditRemark = params.get("auditRemark") != null ? params.get("auditRemark").toString() : "";
            
            CompanyUser company = companyUserService.findById(id);
            if (company == null) {
                return Result.error("企业不存在");
            }

            // 只有待审核状态的申请才能被审核，防止重复审核
            if (company.getAuditStatus() != null && company.getAuditStatus() != 0) {
                return Result.error("该企业已审核，无法重复操作");
            }

            company.setAuditStatus(auditStatus);
            company.setAuditRemark(auditRemark);
            company.setAuditTime(new Date());
            company.setUpdateTime(new Date());
            
            if (auditStatus == 1) {
                company.setStatus(1);
                company.setReviewerId(getCurrentUserId());
                
                String username = company.getCompanyName();
                company.setUsername(username);
                // 保留注册时生成的随机密码，企业使用原登记邮箱验证后自行设置密码。
                company.setPassword(null);
                company.setRole("ROLE_COMPANY");
                
                companyUserService.update(company);
                
                return Result.success("审核通过");
            } else if (auditStatus == 2) {
                company.setStatus(3);
                company.setReviewerId(getCurrentUserId());
                
                companyUserService.update(company);
                
                return Result.success("审核拒绝");
            } else {
                return Result.error("无效的审核状态");
            }
        } catch (BusinessException e) {
            log.warn("审核企业注册失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("审核企业注册时发生异常: {}", e.getMessage(), e);
            return Result.error("审核失败: " + e.getMessage());
        }
    }

    @GetMapping("/internship-status")
    public Result getInternshipStatusList(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String className) {
        log.info("教师端分页获取实习状态列表，页码：{}, 每页条数：{}", page, pageSize);
        try {
            ScopeInfo scope = resolveTeacherScope();
            PageResult<com.internship.entity.StudentInternshipStatus> pageResult =
                    studentInternshipStatusService.findPageByScope(
                            page, pageSize, studentId, name, gender, status, companyId, companyName,
                            grade, major, className, null, scope.departmentIds, scope.divisionIds, scope.classIds);
            Map<String, Object> result = new HashMap<>();
            result.put("list", pageResult.getRows());
            result.put("total", pageResult.getTotal());
            result.put("scopeName", scope.name);
            result.put("scopeType", scope.type);
            return Result.success(result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取实习状态列表失败：{}", e.getMessage(), e);
            return Result.error("获取实习状态列表失败");
        }
    }

    @GetMapping("/internship-status/statistics")
    public Result getInternshipStatusStatistics() {
        log.info("获取学生实习状态统计数据");
        try {
            ScopeInfo scope = resolveTeacherScope();
            PageResult<com.internship.entity.StudentInternshipStatus> pageResult =
                    studentInternshipStatusService.findPageByScope(
                            1, 1, null, null, null, null, null, null, null, null, null, null,
                            scope.departmentIds, scope.divisionIds, scope.classIds);
            Long total = pageResult.getTotal();

            Map<String, Object> statistics = new HashMap<>();
            statistics.put("studentCount", total);
            statistics.put("scopeName", scope.name);
            statistics.put("scopeType", scope.type);
            statistics.put("noOfferCount", countScopedStatus(scope, 0));
            statistics.put("pendingCount", countScopedStatus(scope, 1));
            statistics.put("confirmedCount", countScopedStatus(scope, 2));
            statistics.put("interningCount", countScopedStatus(scope, 3));
            statistics.put("finishedCount", countScopedStatus(scope, 4));
            statistics.put("interruptedCount", countScopedStatus(scope, 5));
            statistics.put("delayedCount", countScopedStatus(scope, 6));

            log.info("统计数据：总数={}", total);
            return Result.success(statistics);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取统计数据失败：{}", e.getMessage(), e);
            return Result.error("获取统计数据失败");
        }
    }

    @GetMapping("/internship-status/company-options")
    public Result getInternshipCompanyOptions() {
        try {
            ScopeInfo scope = resolveTeacherScope();
            List<String> companyNames = studentInternshipStatusMapper.findDistinctCompanyNamesByScope(
                    scope.departmentIds, scope.divisionIds, scope.classIds);
            return Result.success(companyNames);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取实习单位筛选选项失败：{}", e.getMessage(), e);
            return Result.error("获取实习单位筛选选项失败");
        }
    }

    /**
     * 获取学生状态监控详情。这里按学生主键重新查询完整数据，避免复用列表行的精简字段，
     * 同时只返回页面需要的安全字段，防止学生密码等敏感信息被序列化到前端。
     */
    @GetMapping("/students/{studentId:\\d+}/internship-detail")
    public Result getStudentInternshipDetail(@PathVariable Long studentId) {
        try {
            ScopeInfo scope = resolveTeacherScope();
            StudentUser student = studentUserService.findById(studentId);
            if (student == null) {
                return Result.error("学生不存在");
            }

            Major major = student.getMajorId() == null ? null : majorService.findById(student.getMajorId());
            if (!canViewStudentDetail(student, major, scope)) {
                throw new AccessDeniedException("无权查看该学生详情");
            }

            Department department = null;
            if (major != null && major.getDepartmentId() != null) {
                department = departmentMapper.findById(major.getDepartmentId());
            }

            StudentInternshipStatus internshipStatus = studentInternshipStatusService.findByStudentId(studentId);
            List<InternshipStageAssignment> assignments = internshipStageAssignmentService.findByStudentId(studentId);
            if (assignments == null) assignments = Collections.emptyList();

            Date now = new Date();
            InternshipStageAssignment currentAssignment = selectCurrentAssignment(assignments, now);
            boolean hasPendingConfirmation = assignments.stream().anyMatch(item ->
                    "PENDING_CONFIRMATION".equals(item.getStatus()));

            Object displayStatus = internshipStatus != null && internshipStatus.getStatus() != null
                    && internshipStatus.getStatus() >= 2 && internshipStatus.getStatus() <= 6
                    ? internshipStatus.getStatus()
                    : currentAssignment != null ? deriveInternshipStatus(currentAssignment)
                    : internshipStatus != null && internshipStatus.getStatus() != null
                    && internshipStatus.getStatus() <= 6 ? internshipStatus.getStatus() : 0;

            String companyName = null;
            String positionName = null;
            Date startTime = null;
            Date endTime = null;

            if (currentAssignment != null) {
                companyName = currentAssignment.getCompanyName();
                positionName = currentAssignment.getPositionName();
                startTime = currentAssignment.getInternshipStartTime();
                endTime = currentAssignment.getInternshipEndTime();
                if (!StringUtils.hasText(companyName) && currentAssignment.getCompanyId() != null) {
                    CompanyUser company = companyUserService.findById(currentAssignment.getCompanyId());
                    companyName = company == null ? null : company.getCompanyName();
                }
                if (!StringUtils.hasText(positionName) && currentAssignment.getPositionId() != null) {
                    Position position = positionService.findById(currentAssignment.getPositionId());
                    positionName = position == null ? null : position.getPositionName();
                }
            } else if (internshipStatus != null) {
                companyName = StringUtils.hasText(internshipStatus.getCompanyName())
                        ? internshipStatus.getCompanyName()
                        : internshipStatus.getCompany() == null ? null : internshipStatus.getCompany().getCompanyName();
                positionName = StringUtils.hasText(internshipStatus.getPositionName())
                        ? internshipStatus.getPositionName()
                        : internshipStatus.getPosition() == null ? null : internshipStatus.getPosition().getPositionName();
                startTime = internshipStatus.getInternshipStartTime();
                endTime = internshipStatus.getInternshipEndTime();
            }

            String departmentName = department == null ? null : department.getName();
            if (!StringUtils.hasText(departmentName) && StringUtils.hasText(student.getDepartment())) {
                try {
                    Department storedDepartment = departmentMapper.findById(Long.valueOf(student.getDepartment()));
                    departmentName = storedDepartment == null ? null : storedDepartment.getName();
                } catch (NumberFormatException ignored) {
                    departmentName = student.getDepartment();
                }
            }

            Map<String, Object> detail = new LinkedHashMap<>();
            detail.put("id", student.getId());
            detail.put("studentId", student.getStudentId());
            detail.put("name", student.getName());
            detail.put("gender", student.getGender());
            detail.put("grade", student.getGrade());
            detail.put("department", departmentName);
            detail.put("major", major != null ? major.getName() : student.getMajor());
            detail.put("className", student.getClassName());
            detail.put("phone", student.getPhone());
            detail.put("email", student.getEmail());
            detail.put("internshipStatus", displayStatus);
            if (internshipStatus != null) {
                detail.put("jobSearchStatus", internshipStatus.getJobSearchStatus());
                detail.put("jobSearchStatusText", internshipStatus.getJobSearchStatusText());
                detail.put("pendingApplicationTexts", internshipStatus.getPendingApplicationTexts());
            }
            detail.put("company", companyName);
            detail.put("positionName", positionName);
            detail.put("hasPendingConfirmation", hasPendingConfirmation);
            detail.put("internshipStartDate", startTime);
            detail.put("internshipEndDate", endTime);
            return Result.success(detail);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取学生状态监控详情失败，studentId={}：{}", studentId, e.getMessage(), e);
            return Result.error("获取学生详情失败");
        }
    }

    private boolean canViewStudentDetail(StudentUser student, Major major, ScopeInfo scope) {
        if (student == null) return false;
        return (scope.classIds != null && scope.classIds.contains(student.getClassId()))
                || (major != null && scope.departmentIds != null
                    && scope.departmentIds.contains(major.getDepartmentId()))
                || (major != null && scope.divisionIds != null
                    && scope.divisionIds.contains(major.getDivisionId()));
    }

    private Object deriveInternshipStatus(InternshipStageAssignment assignment) {
        if (assignment == null) return 0;
        if ("PENDING_CONFIRMATION".equals(assignment.getStatus())) return 1;
        if ("INTERRUPTED".equals(assignment.getStatus())) return 5;
        if ("REPLACED".equals(assignment.getStatus()) || "ENDED".equals(assignment.getStatus())) return 4;
        Date now = new Date();
        if (assignment.getInternshipEndTime() != null && assignment.getInternshipEndTime().before(now)) return 4;
        if (assignment.getInternshipStartTime() != null && !assignment.getInternshipStartTime().after(now)) return 3;
        return 2;
    }

    private InternshipStageAssignment selectCurrentAssignment(List<InternshipStageAssignment> assignments, Date now) {
        Comparator<InternshipStageAssignment> byStartThenId = Comparator
                .comparing(InternshipStageAssignment::getInternshipStartTime,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(InternshipStageAssignment::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder()));

        InternshipStageAssignment active = assignments.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getCurrentFlag()))
                .filter(item -> !"PENDING_CONFIRMATION".equals(item.getStatus()))
                .filter(item -> !"REPLACED".equals(item.getStatus()))
                .filter(item -> !"ENDED".equals(item.getStatus()))
                .filter(item -> !"INTERRUPTED".equals(item.getStatus()))
                .filter(item -> item.getInternshipStartTime() == null || !item.getInternshipStartTime().after(now))
                .filter(item -> item.getInternshipEndTime() == null || item.getInternshipEndTime().after(now))
                .max(byStartThenId)
                .orElse(null);
        if (active != null) return active;

        return assignments.stream()
                .filter(item -> Integer.valueOf(1).equals(item.getCurrentFlag()))
                .filter(item -> "CONFIRMED".equals(item.getStatus()))
                .filter(item -> item.getInternshipStartTime() != null && item.getInternshipStartTime().after(now))
                .min(byStartThenId)
                .orElse(null);
    }

    private Long countScopedStatus(ScopeInfo scope, Integer status) {
        return studentInternshipStatusService.findPageByScope(
                1, 1, null, null, null, status, null, null, null, null, null, null,
                scope.departmentIds, scope.divisionIds, scope.classIds).getTotal();
    }

    private ScopeInfo resolveTeacherScope() {
        ScopeInfo scope = new ScopeInfo();
        User user = CurrentHolder.getUser();
        if (user == null || user.getId() == null || !StringUtils.hasText(user.getUsername())) {
            throw new AccessDeniedException("教师身份不可用");
        }
        try {
            TeacherUser teacher = teacherUserService.findById(user.getId());
            if (teacher == null || !user.getUsername().equals(teacher.getTeacherUserId())) {
                throw new AccessDeniedException("教师身份不匹配");
            }

            String teacherType = teacher.getTeacherType();
            if ("COUNSELOR".equals(teacherType)) {
                List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(user.getId());
                if (relations != null) {
                    scope.classIds = relations.stream().filter(Objects::nonNull)
                            .map(ClassCounselorRelation::getClassId)
                            .filter(id -> id != null && id > 0).distinct().collect(Collectors.toList());
                }
                scope.name = "负责班级";
                scope.type = "COUNSELOR";
            } else if ("DEPARTMENT".equals(teacherType)) {
                if (StringUtils.hasText(teacher.getDivisionId())) {
                    Long divisionId = Long.valueOf(teacher.getDivisionId());
                    if (divisionId > 0) {
                        scope.divisionIds = Collections.singletonList(divisionId);
                        Division division = divisionMapper.findById(divisionId);
                        scope.name = division != null ? division.getName() : "本系";
                    }
                }
                scope.type = "DEPARTMENT";
            } else if ("COLLEGE".equals(teacherType)) {
                if (StringUtils.hasText(teacher.getDepartmentId())) {
                    Long deptId = Long.valueOf(teacher.getDepartmentId());
                    if (deptId > 0) {
                        scope.departmentIds = Collections.singletonList(deptId);
                        Department dept = departmentMapper.findById(deptId);
                        scope.name = dept != null ? dept.getName() : "本院";
                    }
                }
                scope.type = "COLLEGE";
            }
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.warn("获取教师管辖范围失败: {}", e.getMessage());
            throw new AccessDeniedException("教师管辖范围不可用", e);
        }
        if (!scope.hasScope()) {
            throw new AccessDeniedException("教师管辖范围未配置");
        }
        return scope;
    }

    private static class ScopeInfo {
        List<Long> departmentIds;
        List<Long> divisionIds;
        List<Long> classIds;
        String name = "";
        String type = "";
        boolean hasScope() {
            return (departmentIds != null && !departmentIds.isEmpty())
                    || (divisionIds != null && !divisionIds.isEmpty())
                    || (classIds != null && !classIds.isEmpty());
        }
    }

    @GetMapping("/internship-status/{id}")
    public Result getInternshipStatusById(@PathVariable Long id) {
        log.info("教师端获取实习状态详情，ID: {}", id);
        try {
            ScopeInfo scope = resolveTeacherScope();
            com.internship.entity.StudentInternshipStatus status = studentInternshipStatusService.findById(id);
            if (status == null) {
                return Result.error("实习状态不存在");
            }
            StudentUser student = studentUserService.findById(status.getStudentId());
            Major major = student == null || student.getMajorId() == null
                    ? null : majorService.findById(student.getMajorId());
            if (!canViewStudentDetail(student, major, scope)) {
                throw new AccessDeniedException("无权查看该实习状态");
            }
            return Result.success(status);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取实习状态详情失败：{}", e.getMessage(), e);
            return Result.error("获取实习状态详情失败");
        }
    }

    @Log(operationType = "EXPORT", module = "TEACHER_MANAGEMENT", description = "导出教师实习状态数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/internship-status/export")
    public void exportInternshipStatus(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Integer gender,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) String companyName,
            @RequestParam(required = false) String studentIds,
            HttpServletResponse response) throws IOException {
        List<StudentInternshipStatus> rows;
        if (StringUtils.hasText(studentIds)) {
            Set<Long> ids = new HashSet<>();
            for (String value : studentIds.split(",")) {
                try { ids.add(Long.valueOf(value.trim())); }
                catch (NumberFormatException ignored) { /* Invalid IDs cannot widen the export. */ }
            }
            List<StudentInternshipStatus> scoped = getScopedStudentList(null,null,null,null,null,null,null,null,null);
            Set<Long> allowed = scoped.stream().map(StudentInternshipStatus::getStudentId).collect(Collectors.toSet());
            if (ids.isEmpty() || !allowed.containsAll(ids)) throw new AccessDeniedException("导出包含管辖范围外的学生");
            rows = scoped.stream().filter(r -> ids.contains(r.getStudentId())).toList();
        } else rows = getScopedStudentList(studentId,name,gender,status,companyId,companyName,null,null,null);
        ExcelExports.internships(response, "学生实习状态", rows);
    }

    @Log(operationType = "EXPORT", module = "TEACHER_MANAGEMENT", description = "批量导出教师实习状态数据", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/internship-status/export/batch")
    public void exportBatchInternshipStatus(
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Integer status,
            @RequestParam Map<String, String> filters,
            HttpServletResponse response) throws IOException {
        List<StudentInternshipStatus> rows = getScopedStudentList(null,filters.get("name"),null,status,null,
                filters.get("companyName"),grade,filters.get("major"),className);
        String path = filters.get("internshipPath");
        if (StringUtils.hasText(path)) rows = rows.stream().filter(r -> path.equals(ExcelExports.pathCode(r))).toList();
        ExcelExports.internships(response, "学生实习状态汇总", rows);
    }

    public void exportBatchInternshipStatus(String scope, String className, String grade, Integer status,
                                             HttpServletResponse response) throws IOException {
        exportBatchInternshipStatus(scope,className,grade,status,Map.of(),response);
    }

    private String getGenderText(Integer gender) {
        if (gender == null) return "";
        return gender == 1 ? "男" : (gender == 2 ? "女" : "未知");
    }

    private String getStatusText(Integer status) {
        if (status == null) return "状态待核实";
        return switch (status) {
            case 0 -> "尚未获录用";
            case 1 -> "已录用待确认";
            case 2 -> "已定单位待实习";
            case 3 -> "实习进行中";
            case 4 -> "实习已结束";
            case 5 -> "实习已中断";
            case 6 -> "已延期实习";
            default -> "状态待核实";
        };
    }

    private String exportStatusText(StudentInternshipStatus item) {
        String text = StringUtils.hasText(item.getJobSearchStatusText())
                ? item.getJobSearchStatusText() : getStatusText(item.getStatus());
        return item.getPendingApplicationTexts() == null || item.getPendingApplicationTexts().isEmpty()
                ? text : text + " " + String.join(" ", item.getPendingApplicationTexts());
    }

    /**
     * 根据当前教师的管辖范围获取学生列表
     */
    private List<StudentInternshipStatus> getScopedStudentList(
            Long studentId, String name, Integer gender, Integer status,
            Long companyId, String companyName, String grade, String major, String className) {
        ScopeInfo scope = resolveTeacherScope();
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.listByScope(studentId, name, gender, status,
                companyId, companyName, grade, major, className, null,
                scope.departmentIds, scope.divisionIds, scope.classIds);
        jobSearchStatusService.enrich(statuses);
        return statuses;
    }

    private Long getCurrentUserId() {
        Long userId = CurrentHolder.getUserId();
        if (userId == null || userId <= 0) {
            throw new AccessDeniedException("教师身份不可用");
        }
        log.debug("获取到当前登录用户 ID: {}", userId);
        return userId;
    }

    /**
     * 获取教师可见的资源列表（只显示已发布的资源）
     */
    @GetMapping("/resources")
    public Result getPublishedResources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("教师端获取已发布资源列表，页码：{}, 每页条数：{}", page, pageSize);
        try {
            Object result = resourceService.getPublishedResources(page, pageSize);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取已发布资源列表失败：{}", e.getMessage(), e);
            return Result.error("获取已发布资源列表失败");
        }
    }

    /**
     * 获取教师自己上传的资源列表（包括草稿状态的）
     */
    @GetMapping("/resources/my")
    public Result getMyResources(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("教师端获取自己上传的资源列表，页码：{}, 每页条数：{}", page, pageSize);
        try {
            Long currentUserId = getCurrentUserId();
            List<com.internship.entity.LearningResource> myResources = resourceService.getResourcesByUploaderId(currentUserId);
            if (myResources == null) {
                throw new AccessDeniedException("教师资源列表不可用");
            }
            myResources = myResources.stream()
                    .filter(item -> item != null && Objects.equals(item.getUploaderId(), currentUserId)
                            && "TEACHER".equals(item.getUploaderRole()))
                    .toList();
            
            // 构建分页结果
            int total = myResources.size();
            int startIndex = (page - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, total);
            
            List<com.internship.entity.LearningResource> pageResources = myResources.subList(startIndex, endIndex);
            
            Map<String, Object> result = new HashMap<>();
            result.put("list", pageResources);
            result.put("total", total);
            
            return Result.success(result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("获取自己上传的资源列表失败：{}", e.getMessage(), e);
            return Result.error("获取自己上传的资源列表失败");
        }
    }

    /**
     * 更新资源（只能更新自己上传的）
     */
    @PutMapping("/resources/{id}")
    @Log(operationType = "UPDATE", module = "RESOURCE_MANAGEMENT", description = "教师更新学习资源")
    public Result updateResource(@PathVariable Long id, @RequestBody com.internship.entity.LearningResource resource) {
        log.info("教师端更新资源，ID: {}, 标题: {}", id, resource.getTitle());
        try {
            Long currentUserId = getCurrentUserId();
            com.internship.entity.LearningResource existingResource = resourceService.getResourceById(id);
            
            if (existingResource == null) {
                return Result.error("资源不存在");
            }
            
            // 检查是否是自己上传的资源
            if (!Objects.equals(existingResource.getUploaderId(), currentUserId)
                    || !"TEACHER".equals(existingResource.getUploaderRole())) {
                log.warn("用户 {} 尝试更新非自己上传的资源 {}", currentUserId, id);
                throw new AccessDeniedException("只能编辑自己上传的资源");
            }
            
            // 更新资源信息
            existingResource.setTitle(resource.getTitle());
            existingResource.setDescription(resource.getDescription());
            if (resource.getFileType() != null) {
                existingResource.setFileType(resource.getFileType());
            }
            
            // 保存更新
            resourceService.updateResource(existingResource);
            
            log.info("资源更新成功，ID: {}", id);
            return Result.success("更新成功");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("更新资源失败：{}", e.getMessage(), e);
            return Result.error("更新资源失败");
        }
    }

    /**
     * 删除资源（只能删除自己上传的）
     */
    @DeleteMapping("/resources/{id}")
    @Log(operationType = "DELETE", module = "RESOURCE_MANAGEMENT", description = "教师删除学习资源")
    public Result deleteResource(@PathVariable Long id) {
        log.info("教师端删除资源，ID: {}", id);
        try {
            Long currentUserId = getCurrentUserId();
            com.internship.entity.LearningResource existingResource = resourceService.getResourceById(id);
            
            if (existingResource == null) {
                return Result.error("资源不存在");
            }
            
            // 检查是否是自己上传的资源
            if (!Objects.equals(existingResource.getUploaderId(), currentUserId)
                    || !"TEACHER".equals(existingResource.getUploaderRole())) {
                log.warn("用户 {} 尝试删除非自己上传的资源 {}", currentUserId, id);
                throw new AccessDeniedException("只能删除自己上传的资源");
            }
            
            // 删除资源
            resourceService.deleteResource(id);
            
            log.info("资源删除成功，ID: {}", id);
            return Result.success("删除成功");
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("删除资源失败：{}", e.getMessage(), e);
            return Result.error("删除资源失败");
        }
    }

}
