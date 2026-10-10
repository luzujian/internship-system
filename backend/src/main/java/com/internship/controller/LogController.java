package com.internship.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.internship.anno.Log;
import com.internship.dto.LogExportRequest;
import com.internship.dto.LogQueryParams;
import com.internship.entity.LoginLog;
import com.internship.entity.OperateLog;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.SecurityEventLog;
import com.internship.entity.SystemConfig;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditLogService;
import com.internship.service.LogOperatorNameResolver;
import com.internship.service.SystemConfigService;
import com.internship.task.LogRetentionTask;
import com.internship.exception.BusinessException;
import com.internship.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/logs")
public class LogController {

    private static final Logger log = LoggerFactory.getLogger(LogController.class);

    /** 导出上限（超过要求缩小时间范围） */
    private static final int EXPORT_MAX_ROWS = 100000;
    /** 时间范围最大跨度（天） */
    private static final int MAX_RANGE_DAYS = 365;
    /** 默认查询最近天数 */
    private static final int DEFAULT_RANGE_DAYS = 7;
    /** 分页大小上限 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 操作日志排序字段白名单 */
    private static final Set<String> OPERATE_SORT_FIELDS = new HashSet<>(Arrays.asList(
            "id", "operate_time", "operator_name", "operation_type", "module", "operation_result", "ip_address", "risk_level"));
    /** 登录日志排序字段白名单 */
    private static final Set<String> LOGIN_SORT_FIELDS = new HashSet<>(Arrays.asList(
            "id", "login_time", "user_name", "login_status", "ip_address"));
    /** 安全事件排序字段白名单 */
    private static final Set<String> SECURITY_SORT_FIELDS = new HashSet<>(Arrays.asList(
            "id", "event_time", "username", "event_type", "risk_level", "result", "ip_address"));

    /** 留存策略配置键 */
    private static final String RETENTION_HIGH_RISK_DAYS_KEY = "audit.retention.highRiskDays";
    private static final String RETENTION_NORMAL_DAYS_KEY = "audit.retention.normalDays";
    private static final String RETENTION_LOGIN_DAYS_KEY = "audit.retention.loginDays";

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private SecurityEventLogMapper securityEventLogMapper;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private LogOperatorNameResolver logOperatorNameResolver;

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private com.internship.task.AuditIntegrityTask auditIntegrityTask;

    @Autowired
    private LogRetentionTask logRetentionTask;

    // ================================================================
    // P1 统一日志中心 API
    // ================================================================

    /**
     * 顶部统计：今日操作、失败操作、登录失败、高风险事件
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('log:view') or hasAuthority('log:login:view') or hasAuthority('log:security:view')")
    public Result getSummary() {
        try {
            Calendar cal = Calendar.getInstance();
            cal.set(Calendar.HOUR_OF_DAY, 0);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            Date todayStart = cal.getTime();
            Date now = new Date();

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("todayOperations", hasAuthority("log:view") ? operateLogMapper.countByTimeRange(todayStart, now, null) : null);
            summary.put("todayFailedOperations", hasAuthority("log:view") ? operateLogMapper.countByTimeRange(todayStart, now, "FAILURE") : null);
            summary.put("todayFailedLogins", hasAuthority("log:login:view") ? loginLogMapper.countByTimeRange(todayStart, now, "FAILURE") : null);
            summary.put("todayHighRiskEvents", hasAuthority("log:security:view") ? securityEventLogMapper.countHighRiskByTimeRange(todayStart, now) : null);
            return Result.success(summary);
        } catch (Exception e) {
            log.error("获取日志统计失败：{}", e.getMessage(), e);
            return Result.error("获取日志统计失败");
        }
    }

    /**
     * 操作日志列表（组合筛选 + 分页）
     */
    @GetMapping("/operations")
    @PreAuthorize("hasAuthority('log:view')")
    public Result queryOperations(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = "operate_time") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            LogQueryParams q = buildQueryParams(startTime, endTime, operatorName, role, module, action,
                    result, riskLevel, ip, targetId, requestId, sortField, sortOrder, OPERATE_SORT_FIELDS, "operate_time");

            startLogPage(page, pageSize);
            List<OperateLog> logs = operateLogMapper.selectByFilter(q);
            PageInfo<OperateLog> pageInfo = new PageInfo<>(logs);

            // 列表默认脱敏：IP 掩码 + 不返回详情JSON（详情接口按权限返回）
            pageInfo.getList().forEach(this::sanitizeOperateLog);
            return Result.success(toPageResult(pageInfo));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询操作日志失败：{}", e.getMessage(), e);
            return Result.error("查询操作日志失败");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 登录日志列表（组合筛选 + 分页）
     */
    @GetMapping("/login-events")
    @PreAuthorize("hasAuthority('log:login:view')")
    public Result queryLoginEvents(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = "login_time") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            LogQueryParams q = buildQueryParams(startTime, endTime, operatorName, role, null, null,
                    result, riskLevel, ip, null, requestId, sortField, sortOrder, LOGIN_SORT_FIELDS, "login_time");

            startLogPage(page, pageSize);
            List<LoginLog> logs = loginLogMapper.selectByFilter(q);
            PageInfo<LoginLog> pageInfo = new PageInfo<>(logs);

            pageInfo.getList().forEach(this::sanitizeLoginLog);
            return Result.success(toPageResult(pageInfo));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询登录日志失败：{}", e.getMessage(), e);
            return Result.error("查询登录日志失败");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 安全事件列表（组合筛选 + 分页）
     */
    @GetMapping("/security-events")
    @PreAuthorize("hasAuthority('log:security:view')")
    public Result querySecurityEvents(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String result,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String ip,
            @RequestParam(required = false) String targetId,
            @RequestParam(required = false) String requestId,
            @RequestParam(defaultValue = "event_time") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            LogQueryParams q = buildQueryParams(startTime, endTime, operatorName, role, null, action,
                    result, riskLevel, ip, targetId, requestId, sortField, sortOrder, SECURITY_SORT_FIELDS, "event_time");

            startLogPage(page, pageSize);
            List<SecurityEventLog> logs = securityEventLogMapper.selectByFilter(q);
            PageInfo<SecurityEventLog> pageInfo = new PageInfo<>(logs);

            pageInfo.getList().forEach(this::sanitizeSecurityEvent);
            return Result.success(toPageResult(pageInfo));
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("查询安全事件失败：{}", e.getMessage(), e);
            return Result.error("查询安全事件失败");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 日志详情（按类别校验权限；敏感字段按 log:sensitive:view 返回）
     */
    @GetMapping("/{category}/{id}")
    @PreAuthorize("hasAuthority('log:view') or hasAuthority('log:login:view') or hasAuthority('log:security:view')")
    public Result getLogDetail(@PathVariable String category, @PathVariable Long id) {
        try {
            switch (category) {
                case "operations" -> {
                    if (!hasAuthority("log:view")) {
                        return Result.error("无权限查看操作日志详情");
                    }
                    OperateLog operateLog = operateLogMapper.selectById(id);
                    if (operateLog == null) {
                        return Result.error("日志不存在");
                    }
                    applySensitiveFields(operateLog.getIpAddress(), operateLog.getDetailJson(), operateLog::setIpAddress, operateLog::setDetailJson);
                    operateLog.setOperatorName(logOperatorNameResolver.resolve(
                            operateLog.getOperatorRole(),
                            operateLog.getOperateAdminId() == null ? null : String.valueOf(operateLog.getOperateAdminId()),
                            operateLog.getOperatorUsername()));
                    String resolvedOperatorUsername = logOperatorNameResolver.resolveUsername(
                            operateLog.getOperatorRole(),
                            operateLog.getOperateAdminId() == null ? null : String.valueOf(operateLog.getOperateAdminId()),
                            operateLog.getOperatorUsername());
                    if (resolvedOperatorUsername != null
                            && (operateLog.getOperatorUsername() == null
                            || !operateLog.getOperatorUsername().contains("(切换角色)"))) {
                        operateLog.setOperatorUsername(resolvedOperatorUsername);
                    }
                    return Result.success(operateLog);
                }
                case "login-events" -> {
                    if (!hasAuthority("log:login:view")) {
                        return Result.error("无权限查看登录日志详情");
                    }
                    LoginLog loginLog = loginLogMapper.selectById(id);
                    if (loginLog == null) {
                        return Result.error("日志不存在");
                    }
                    applySensitiveFields(loginLog.getIpAddress(), loginLog.getDetailJson(), loginLog::setIpAddress, loginLog::setDetailJson);
                    loginLog.setOperatorName(logOperatorNameResolver.resolve(
                            loginLog.getUserType(), loginLog.getUserId(), loginLog.getUserName()));
                    return Result.success(loginLog);
                }
                case "security-events" -> {
                    if (!hasAuthority("log:security:view")) {
                        return Result.error("无权限查看安全事件详情");
                    }
                    SecurityEventLog eventLog = securityEventLogMapper.selectById(id);
                    if (eventLog == null) {
                        return Result.error("日志不存在");
                    }
                    applySensitiveFields(eventLog.getIpAddress(), eventLog.getDetailJson(), eventLog::setIpAddress, eventLog::setDetailJson);
                    eventLog.setOperatorName(logOperatorNameResolver.resolve(
                            eventLog.getUserRole(), eventLog.getUserId(), eventLog.getUsername()));
                    return Result.success(eventLog);
                }
                default -> {
                    return Result.error("不支持的日志类别");
                }
            }
        } catch (Exception e) {
            log.error("查询日志详情失败：{}", e.getMessage(), e);
            return Result.error("查询日志详情失败");
        }
    }

    /**
     * 筛选选项（操作类型/模块/事件类型/角色/风险等级），由后端返回，前端不再硬编码
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('log:view') or hasAuthority('log:login:view') or hasAuthority('log:security:view')")
    public Result getOptions() {
        try {
            Map<String, Object> options = new LinkedHashMap<>();
            // 规范动作 + 数据库现存类型合并去重
            Set<String> actions = new java.util.LinkedHashSet<>(Arrays.asList(
                    "CREATE", "UPDATE", "DELETE", "READ", "IMPORT", "EXPORT", "UPLOAD",
                    "AUDIT", "CLEAN", "LOGIN", "LOGOUT", "SWITCH_ROLE", "BACKUP", "RESTORE",
                    "RESET_PASSWORD", "ASSIGN", "DOWNLOAD", "VERIFY"));
            if (hasAuthority("log:view")) {
                actions.addAll(operateLogMapper.selectDistinctOperationTypes());
            }
            options.put("actions", actions);

            Set<String> modules = new java.util.LinkedHashSet<>(hasAuthority("log:view") ? operateLogMapper.selectDistinctModules() : List.of());
            options.put("modules", modules);

            Set<String> eventTypes = new java.util.LinkedHashSet<>(hasAuthority("log:security:view") ? securityEventLogMapper.selectDistinctEventTypes() : List.of());
            options.put("eventTypes", eventTypes);

            options.put("roles", Arrays.asList("STUDENT", "TEACHER", "ADMIN", "COMPANY", "SYSTEM", "UNKNOWN"));
            options.put("riskLevels", Arrays.asList("LOW", "MEDIUM", "HIGH", "CRITICAL"));
            options.put("results", Arrays.asList("SUCCESS", "FAILURE", "ATTEMPTED"));
            return Result.success(options);
        } catch (Exception e) {
            log.error("获取日志筛选选项失败：{}", e.getMessage(), e);
            return Result.error("获取筛选选项失败");
        }
    }

    /**
     * 导出日志（服务端流式 XLSX，只导出当前筛选范围，上限 100000 条）
     */
    @PostMapping("/export")
    @PreAuthorize("hasAuthority('log:export')")
    public void exportLogs(@RequestBody LogExportRequest exportRequest, HttpServletResponse response) throws IOException {
        String category = exportRequest.getCategory() == null ? "operations" : exportRequest.getCategory();
        String viewPermission = switch (category) {
            case "operations" -> "log:view";
            case "login-events" -> "log:login:view";
            case "security-events" -> "log:security:view";
            default -> null;
        };
        if (viewPermission == null || !hasAuthority(viewPermission)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // 关键操作（批量导出）：先记录 ATTEMPTED，审计不可用则禁止导出
        try {
            auditLogService.recordCriticalAttempt(buildExportAuditLog(category, exportRequest, 0));
        } catch (Exception e) {
            log.error("导出审计不可用，已阻止导出：{}", e.getMessage());
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":503,\"message\":\"审计服务暂不可用，导出已被拒绝，请稍后重试\"}");
            return;
        }

        try {
            Date[] range = parseTimeRange(exportRequest.getStartTime(), exportRequest.getEndTime());
            LogQueryParams q = buildQueryParams(range[0], range[1], exportRequest.getOperatorName(),
                    exportRequest.getRole(), exportRequest.getModule(), exportRequest.getAction(),
                    exportRequest.getResult(), exportRequest.getRiskLevel(), exportRequest.getIp(),
                    exportRequest.getTargetId(), exportRequest.getRequestId(),
                    "operate_time", "desc",
                    "login-events".equals(category) ? LOGIN_SORT_FIELDS : "security-events".equals(category) ? SECURITY_SORT_FIELDS : OPERATE_SORT_FIELDS,
                    "login-events".equals(category) ? "login_time" : "security-events".equals(category) ? "event_time" : "operate_time");

            long total = switch (category) {
                case "operations" -> operateLogMapper.countByFilter(q);
                case "login-events" -> loginLogMapper.countByFilter(q);
                case "security-events" -> securityEventLogMapper.countByFilter(q);
                default -> throw new IllegalArgumentException("不支持的日志类别");
            };
            if (total > EXPORT_MAX_ROWS) {
                throw new IllegalArgumentException("导出数据超过上限" + EXPORT_MAX_ROWS + "条，请缩小时间范围");
            }
            // 先检查数量，再以数据库 LIMIT 读取；额外一条用于检测统计后新增的数据。
            q.setLimit(EXPORT_MAX_ROWS + 1);
            List<?> rows = switch (category) {
                case "operations" -> operateLogMapper.selectByFilter(q);
                case "login-events" -> loginLogMapper.selectByFilter(q);
                case "security-events" -> securityEventLogMapper.selectByFilter(q);
                default -> throw new IllegalArgumentException("不支持的日志类别");
            };
            if (rows.size() > EXPORT_MAX_ROWS) {
                throw new IllegalArgumentException("导出数据超过上限" + EXPORT_MAX_ROWS + "条，请缩小时间范围");
            }

            String fileName = URLEncoder.encode("日志导出_" + category + "_" +
                    new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

            writeXlsx(category, rows, response);

            // 导出行为本身记录审计（筛选摘要、行数、执行人）
            recordExportAudit(category, exportRequest, rows.size(), "SUCCESS");
        } catch (IllegalArgumentException e) {
            recordExportAudit(category, exportRequest, 0, "FAILURE");
            writeExportError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("导出日志失败：{}", e.getMessage(), e);
            recordExportAudit(category, exportRequest, 0, "FAILURE");
            writeExportError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "导出日志失败");
        }
    }

    private void writeExportError(HttpServletResponse response, int status, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.reset();
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new com.fasterxml.jackson.databind.ObjectMapper()
                .writeValueAsString(new Result(status, message, null)));
    }

    // ================================================================
    // P2 完整性校验
    // ================================================================

    /**
     * 手动触发全量完整性校验（哈希链逐条重算 + 签名根验证；失败自动告警）
     */
    @PostMapping("/integrity/verify")
    @PreAuthorize("hasAuthority('log:retention:manage')")
    public Result verifyIntegrity() {
        try {
            Map<String, Object> report = auditIntegrityTask.verifyAll(true);
            boolean passed = Boolean.TRUE.equals(report.get("passed"));
            if (!passed) {
                log.warn("手动完整性校验未完整通过: status={}, totalIssues={}, totalWarnings={}",
                        report.get("status"), report.get("totalIssues"), report.get("totalWarnings"));
            }
            return Result.success(report);
        } catch (Exception e) {
            log.error("完整性校验执行失败：{}", e.getMessage(), e);
            return Result.error("完整性校验执行失败");
        }
    }

    // ================================================================
    // P1 留存策略
    // ================================================================

    /**
     * 获取留存策略
     */
    @GetMapping("/retention/policies")
    @PreAuthorize("hasAuthority('log:retention:manage')")
    public Result getRetentionPolicies() {
        try {
            Map<String, Object> policies = new LinkedHashMap<>();
            policies.put("highRiskDays", getRetentionConfig(RETENTION_HIGH_RISK_DAYS_KEY, 365));
            policies.put("normalDays", getRetentionConfig(RETENTION_NORMAL_DAYS_KEY, 180));
            policies.put("loginDays", getRetentionConfig(RETENTION_LOGIN_DAYS_KEY, 180));
            // Logback 运行日志保留天数由 logback.xml 控制（当前30天），只读展示
            policies.put("logbackDays", 30);
            return Result.success(policies);
        } catch (Exception e) {
            log.error("获取留存策略失败：{}", e.getMessage(), e);
            return Result.error("获取留存策略失败");
        }
    }

    /**
     * 更新留存策略（关键操作：审计不可用则禁止执行）
     */
    @PutMapping("/retention/policies")
    @Log(operationType = "UPDATE", module = "LOG_MANAGEMENT", description = "更新日志留存策略",
            riskLevel = "HIGH", targetType = "RETENTION_POLICY", critical = true)
    @PreAuthorize("hasAuthority('log:retention:manage')")
    @Transactional(rollbackFor = Exception.class)
    public Result updateRetentionPolicies(@RequestBody Map<String, Integer> policies) {
        Integer highRiskDays;
        Integer normalDays;
        Integer loginDays;
        try {
            if (policies == null || policies.isEmpty()) {
                return Result.error("请填写留存策略");
            }
            highRiskDays = policies.get("highRiskDays");
            normalDays = policies.get("normalDays");
            loginDays = policies.get("loginDays");
            if (highRiskDays == null && normalDays == null && loginDays == null) {
                return Result.error("请填写有效的留存策略");
            }
            for (Integer days : new Integer[]{highRiskDays, normalDays, loginDays}) {
                if (days != null) {
                    validateRetentionDays(days);
                }
            }
            int effectiveHighRisk = highRiskDays != null ? highRiskDays : getRetentionConfig(RETENTION_HIGH_RISK_DAYS_KEY, 365);
            int effectiveNormal = normalDays != null ? normalDays : getRetentionConfig(RETENTION_NORMAL_DAYS_KEY, 180);
            if (effectiveHighRisk < effectiveNormal) {
                return Result.error("高风险日志留存时间不能短于普通业务日志");
            }
        } catch (IllegalArgumentException e) {
            return Result.error(e.getMessage());
        }
        try {
            if (highRiskDays != null) {
                saveRetentionConfig(RETENTION_HIGH_RISK_DAYS_KEY, highRiskDays, "高风险事件留存天数");
            }
            if (normalDays != null) {
                saveRetentionConfig(RETENTION_NORMAL_DAYS_KEY, normalDays, "普通业务日志留存天数");
            }
            if (loginDays != null) {
                saveRetentionConfig(RETENTION_LOGIN_DAYS_KEY, loginDays, "登录日志留存天数");
            }
            return getRetentionPolicies();
        } catch (Exception e) {
            log.error("更新留存策略失败：{}", e.getMessage(), e);
            // 让异常穿过事务代理，避免三项配置只保存一部分。
            throw new BusinessException("更新留存策略失败");
        }
    }

    /**
     * 留存清理预览：统计各类别到期数据量
     */
    @PostMapping("/retention/preview")
    @PreAuthorize("hasAuthority('log:retention:manage')")
    public Result previewRetention() {
        try {
            int highRiskDays = getRetentionConfig(RETENTION_HIGH_RISK_DAYS_KEY, 365);
            int normalDays = getRetentionConfig(RETENTION_NORMAL_DAYS_KEY, 180);
            int loginDays = getRetentionConfig(RETENTION_LOGIN_DAYS_KEY, 180);

            Date normalBefore = daysBefore(normalDays);
            Date highRiskBefore = daysBefore(highRiskDays);
            Date loginBefore = daysBefore(loginDays);

            Map<String, Object> preview = new LinkedHashMap<>();
            preview.put("operateLogNormal", operateLogMapper.countBeforeTimeExcludeHighRisk(normalBefore));
            preview.put("operateLogHighRisk", operateLogMapper.countBeforeTimeHighRisk(highRiskBefore));
            preview.put("loginLog", loginLogMapper.countBeforeTime(loginBefore));
            preview.put("securityEventNormal", securityEventLogMapper.countBeforeTimeExcludeHighRisk(normalBefore));
            preview.put("securityEventHighRisk", securityEventLogMapper.countBeforeTimeHighRisk(highRiskBefore));
            return Result.success(preview);
        } catch (Exception e) {
            log.error("留存清理预览失败：{}", e.getMessage(), e);
            return Result.error("留存清理预览失败");
        }
    }

    /**
     * 执行到期清理（关键操作：分批删除，清理前后写安全事件，清理记录本身不会被同批删除）
     */
    @PostMapping("/retention/run")
    @Log(operationType = "CLEAN", module = "LOG_MANAGEMENT", description = "执行日志到期清理",
            riskLevel = "HIGH", targetType = "RETENTION", critical = true)
    @PreAuthorize("hasAuthority('log:retention:manage')")
    public Result runRetention(@RequestBody Map<String, String> request) {
        String reason = request == null ? null : request.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            return Result.error("必须填写清理原因");
        }
        reason = reason.trim();
        if (reason.length() > 500) {
            return Result.error("清理原因不能超过500字");
        }
        String operator = currentUsername();
        try {
            recordRetentionEvent("RETENTION_CLEAN", "ATTEMPTED", operator, reason, 0);
            long totalDeleted = logRetentionTask.cleanupExpiredLogs();

            recordRetentionEvent("RETENTION_CLEAN", "SUCCESS", operator, reason, totalDeleted);

            Map<String, Object> result = new HashMap<>();
            result.put("deletedCount", totalDeleted);
            return Result.success(result);
        } catch (Exception e) {
            log.error("执行留存清理失败：{}", e.getMessage(), e);
            recordRetentionEvent("RETENTION_CLEAN", "FAILURE", operator, reason, 0);
            return Result.error("执行留存清理失败");
        }
    }

    // ================================================================
    // 兼容保留接口（旧版前端/内部调用，逐步停用）
    // ================================================================

    /**
     * 获取最近的操作日志（管理员登录后预加载）
     */
    @GetMapping("/recent")
    @PreAuthorize("hasAuthority('log:view')")
    public Result getRecentLogs() {
        try {
            PageHelper.startPage(1, 10, false);
            List<OperateLog> logs = operateLogMapper.selectAll("operate_time", "desc");
            int limit = Math.min(logs.size(), 10);
            List<OperateLog> recentLogs = logs.subList(0, limit);
            recentLogs.forEach(this::sanitizeOperateLog);
            return Result.success(recentLogs);
        } catch (Exception e) {
            log.error("获取最近的操作日志失败: {}", e.getMessage(), e);
            return Result.error("获取最近的操作日志失败");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 查询操作日志（旧版接口，兼容保留）
     */
    @GetMapping("/operation")
    @PreAuthorize("hasAuthority('log:view')")
    public Result queryOperationLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String operatorName,
            @RequestParam(required = false) String operatorRole,
            @RequestParam(required = false) String operationType,
            @RequestParam(required = false) String module,
            @RequestParam(defaultValue = "id") String sortField,
            @RequestParam(defaultValue = "desc") String sortOrder) {
        try {
            String dbSortField = "operation_time".equals(sortField) ? "operate_time" : sortField;

            Set<String> allowedSortFields = new HashSet<>(Arrays.asList("id", "operate_time", "operator_name", "operation_type", "module", "ip_address", "operation_result"));
            Set<String> allowedSortOrders = new HashSet<>(Arrays.asList("asc", "desc"));

            String safeSortField = allowedSortFields.contains(dbSortField) ? dbSortField : "id";
            String safeSortOrder = allowedSortOrders.contains(sortOrder.toLowerCase()) ? sortOrder.toLowerCase() : "desc";

            startLogPage(page, pageSize);
            List<OperateLog> logs;
            if (operatorName != null || operatorRole != null || operationType != null || module != null) {
                logs = operateLogMapper.selectByConditions(operatorName, operatorRole, operationType, module, safeSortField, safeSortOrder);
            } else {
                logs = operateLogMapper.selectAll(safeSortField, safeSortOrder);
            }

            PageInfo<OperateLog> pageInfo = new PageInfo<>(logs);
            pageInfo.getList().forEach(this::sanitizeOperateLog);
            return Result.success(toPageResult(pageInfo));
        } catch (Exception e) {
            log.error("获取操作日志失败: {}", e.getMessage(), e);
            return Result.error("获取操作日志失败");
        } finally {
            PageHelper.clearPage();
        }
    }

    /**
     * 清理操作日志（手动清理，废弃：请使用留存策略 /retention/*）
     */
    @Deprecated
    @Log(operationType = "CLEAN", module = "LOG_MANAGEMENT", description = "清理操作日志",
            riskLevel = "HIGH", critical = true)
    @DeleteMapping("/operation/clean")
    @PreAuthorize("hasAuthority('log:delete')")
    public Result manualCleanOperationLogs(
            @RequestParam(required = false) String operatorRole,
            @RequestParam(required = false, defaultValue = "false") boolean deleteAll) {
        return Result.error("按数量清理和全量清空已停用，请使用日志留存策略预览并清理到期记录");
    }

    /**
     * 清理操作日志（按角色阈值自动清理，废弃：请使用留存策略 /retention/*）
     */
    @Deprecated
    @Log(operationType = "CLEAN", module = "LOG_MANAGEMENT", description = "按阈值自动清理操作日志",
            riskLevel = "HIGH", critical = true)
    @DeleteMapping("/operation/clean/auto")
    @PreAuthorize("hasAuthority('log:delete')")
    public Result autoCleanOperationLogs() {
        return Result.error("按数量清理已停用，请使用日志留存策略预览并清理到期记录");
    }

    // ================================================================
    // 私有工具方法
    // ================================================================

    private LogQueryParams buildQueryParams(Date startTime, Date endTime, String operatorName, String role,
                                            String module, String action, String result, String riskLevel,
                                            String ip, String targetId, String requestId,
                                            String sortField, String sortOrder,
                                            Set<String> allowedSortFields, String defaultSortField) {
        Date[] range = normalizeTimeRange(startTime, endTime);

        LogQueryParams q = new LogQueryParams();
        q.setStartTime(range[0]);
        q.setEndTime(range[1]);
        q.setOperatorName(trimToNull(operatorName));
        q.setRole(trimToNull(role));
        q.setModule(trimToNull(module));
        q.setActionList(auditLogService.expandActionAliases(trimToNull(action)));
        q.setResult(trimToNull(result));
        q.setRiskLevel(trimToNull(riskLevel));
        q.setIp(trimToNull(ip));
        q.setTargetId(trimToNull(targetId));
        q.setRequestId(trimToNull(requestId));

        // 排序白名单：不合法一律回退默认
        String safeSortField = allowedSortFields.contains(sortField) ? sortField : defaultSortField;
        String safeSortOrder = "asc".equalsIgnoreCase(sortOrder) ? "asc" : "desc";
        q.setSortField(safeSortField);
        q.setSortOrder(safeSortOrder);
        return q;
    }

    private Date[] normalizeTimeRange(Date startTime, Date endTime) {
        Date end = endTime != null ? endTime : new Date();
        Date start = startTime;
        if (start == null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(end);
            cal.add(Calendar.DAY_OF_MONTH, -DEFAULT_RANGE_DAYS);
            start = cal.getTime();
        }
        if (start.after(end)) {
            throw new IllegalArgumentException("开始时间不能晚于结束时间");
        }
        long rangeMillis = end.getTime() - start.getTime();
        if (rangeMillis > MAX_RANGE_DAYS * 24L * 60L * 60L * 1000L) {
            throw new IllegalArgumentException("时间范围不能超过" + MAX_RANGE_DAYS + "天");
        }
        return new Date[]{start, end};
    }

    private Date[] parseTimeRange(String startTime, String endTime) {
        try {
            DateTimeFormatter format = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss")
                    .withResolverStyle(ResolverStyle.STRICT);
            Date start = trimToNull(startTime) == null ? null
                    : Date.from(LocalDateTime.parse(startTime, format).atZone(ZoneId.systemDefault()).toInstant());
            Date end = trimToNull(endTime) == null ? null
                    : Date.from(LocalDateTime.parse(endTime, format).atZone(ZoneId.systemDefault()).toInstant());
            return normalizeTimeRange(start, end);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("时间格式错误，正确格式：yyyy-MM-dd HH:mm:ss");
        }
    }

    private void startLogPage(Integer page, Integer pageSize) {
        PageHelper.startPage(page == null ? 1 : Math.max(page, 1),
                pageSize == null ? 20 : Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE)));
    }

    private <T> PageResult<T> toPageResult(PageInfo<T> pageInfo) {
        PageResult<T> pageResult = new PageResult<>();
        pageResult.setRows(pageInfo.getList());
        pageResult.setTotal(pageInfo.getTotal());
        pageResult.setPages(pageInfo.getPages());
        pageResult.setCurrent(pageInfo.getPageNum());
        pageResult.setPageSize(pageInfo.getPageSize());
        return pageResult;
    }

    private boolean hasAuthority(String authority) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()) {
            return authentication.getName();
        }
        return null;
    }

    /** 列表脱敏：IP 掩码、移除详情JSON */
    private void sanitizeOperateLog(OperateLog operateLog) {
        operateLog.setIpAddress(maskIp(operateLog.getIpAddress()));
        operateLog.setDetailJson(null);
    }

    private void sanitizeLoginLog(LoginLog loginLog) {
        loginLog.setIpAddress(maskIp(loginLog.getIpAddress()));
        loginLog.setDetailJson(null);
    }

    private void sanitizeSecurityEvent(SecurityEventLog eventLog) {
        eventLog.setIpAddress(maskIp(eventLog.getIpAddress()));
        eventLog.setDetailJson(null);
    }

    /** 详情敏感字段：无 log:sensitive:view 权限时 IP 掩码、详情JSON 不返回 */
    private void applySensitiveFields(String rawIp, String rawDetailJson,
                                      java.util.function.Consumer<String> ipSetter,
                                      java.util.function.Consumer<String> detailSetter) {
        if (hasAuthority("log:sensitive:view")) {
            ipSetter.accept(rawIp);
            detailSetter.accept(rawDetailJson);
        } else {
            ipSetter.accept(maskIp(rawIp));
            detailSetter.accept(null);
        }
    }

    /** IP 掩码：192.168.1.100 -> 192.168.*.*；IPv6 截断处理 */
    private String maskIp(String ip) {
        if (ip == null || ip.isEmpty() || "未知".equals(ip)) {
            return ip;
        }
        if (ip.contains(".")) {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
            return parts[0] + ".*";
        }
        if (ip.contains(":")) {
            String[] parts = ip.split(":");
            return parts[0] + ":*:*";
        }
        return "*";
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** 流式写出 XLSX（防公式注入：以 = + - @ 开头的文本前置单引号） */
    private void writeXlsx(String category, List<?> rows, HttpServletResponse response) throws IOException {
        SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(200)) {
            SXSSFSheet sheet = workbook.createSheet("日志");
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            String[] headers;
            if ("login-events".equals(category)) {
                headers = new String[]{"ID", "时间", "用户名", "用户类型", "状态", "风险等级", "IP地址", "设备类别", "错误码", "请求ID"};
            } else if ("security-events".equals(category)) {
                headers = new String[]{"ID", "时间", "用户名", "角色", "事件类型", "结果", "风险等级", "IP地址", "请求路径", "描述", "请求ID"};
            } else {
                headers = new String[]{"ID", "时间", "操作人", "角色", "模块", "动作", "结果", "风险等级", "目标类型", "目标ID", "IP地址", "耗时(ms)", "描述", "请求ID"};
            }

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 18 * 256);
            }

            int rowIndex = 1;
            for (Object row : rows) {
                Row dataRow = sheet.createRow(rowIndex++);
                String[] values;
                if (row instanceof OperateLog ol) {
                    values = new String[]{
                            safeStr(ol.getId()), ol.getOperateTime() != null ? timeFormat.format(ol.getOperateTime()) : "",
                            safeStr(ol.getOperatorName()), safeStr(ol.getOperatorRole()), safeStr(ol.getModule()),
                            safeStr(ol.getOperationType()), safeStr(ol.getOperationResult()), safeStr(ol.getRiskLevel()),
                            safeStr(ol.getTargetType()), safeStr(ol.getTargetId()), safeStr(exportIp(ol.getIpAddress())),
                            ol.getDurationMs() != null ? String.valueOf(ol.getDurationMs()) : "", safeStr(ol.getDescription()), safeStr(ol.getRequestId())
                    };
                } else if (row instanceof LoginLog ll) {
                    values = new String[]{
                            safeStr(ll.getId()), ll.getLoginTime() != null ? timeFormat.format(ll.getLoginTime()) : "",
                            safeStr(ll.getUserName()), safeStr(ll.getUserType()), safeStr(ll.getLoginStatus()),
                            safeStr(ll.getRiskLevel()), safeStr(exportIp(ll.getIpAddress())), safeStr(ll.getDeviceType()), safeStr(ll.getErrorCode()), safeStr(ll.getRequestId())
                    };
                } else if (row instanceof SecurityEventLog se) {
                    values = new String[]{
                            safeStr(se.getId()), se.getEventTime() != null ? timeFormat.format(se.getEventTime()) : "",
                            safeStr(se.getUsername()), safeStr(se.getUserRole()), safeStr(se.getEventType()),
                            safeStr(se.getResult()), safeStr(se.getRiskLevel()), safeStr(exportIp(se.getIpAddress())),
                            safeStr(se.getRequestPath()), safeStr(se.getDescription()), safeStr(se.getRequestId())
                    };
                } else {
                    continue;
                }
                for (int i = 0; i < values.length; i++) {
                    dataRow.createCell(i).setCellValue(preventFormulaInjection(values[i]));
                }
            }

            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        }
    }

    private String safeStr(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private String exportIp(String ip) {
        return hasAuthority("log:sensitive:view") ? ip : maskIp(ip);
    }

    private String preventFormulaInjection(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@') {
            return "'" + value;
        }
        return value;
    }

    /** 构建导出审计日志（服务端构造，含筛选摘要与行数） */
    private OperateLog buildExportAuditLog(String category, LogExportRequest exportRequest, long rowCount) {
        OperateLog auditLog = new OperateLog();
        auditLog.setOperatorName(currentUsername());
        auditLog.setOperatorUsername(currentUsername());
        auditLog.setOperatorRole(CurrentHolder.getUserRole());
        auditLog.setOperateAdminId(CurrentHolder.getUserId());
        auditLog.setOperationType("EXPORT");
        auditLog.setModule("LOG_MANAGEMENT");
        auditLog.setRiskLevel(AuditLogService.RISK_MEDIUM);
        auditLog.setTargetType("LOG_EXPORT");
        auditLog.setAffectedCount((int) rowCount);
        auditLog.setDescription("导出日志：类别=" + category
                + (exportRequest.getStartTime() != null ? "，开始=" + exportRequest.getStartTime() : "")
                + (exportRequest.getEndTime() != null ? "，结束=" + exportRequest.getEndTime() : "")
                + "，共" + rowCount + "条");
        return auditLog;
    }

    /** 记录导出最终结果审计 */
    private void recordExportAudit(String category, LogExportRequest exportRequest, long rowCount, String result) {
        try {
            OperateLog auditLog = buildExportAuditLog(category, exportRequest, rowCount);
            auditLog.setOperationResult(result);
            auditLogService.record(auditLog);
        } catch (Exception e) {
            log.error("记录导出审计失败：{}", e.getMessage());
        }
    }

    private int getRetentionConfig(String key, int defaultValue) {
        String rawValue = systemConfigService.getConfigValue(key);
        if (rawValue == null) {
            return defaultValue;
        }
        try {
            int value = Integer.parseInt(rawValue.trim());
            validateRetentionDays(value);
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("留存策略配置无效，请检查留存天数");
        }
    }

    private void validateRetentionDays(int days) {
        if (days < 30 || days > 3650) {
            throw new IllegalArgumentException("留存天数必须在30-3650之间");
        }
    }

    private void saveRetentionConfig(String key, int days, String name) {
        SystemConfig existing = systemConfigService.findByConfigKey(key);
        if (existing != null) {
            existing.setConfigValue(String.valueOf(days));
            if (systemConfigService.update(existing) != 1) {
                throw new BusinessException("留存策略保存失败");
            }
        } else {
            SystemConfig config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(String.valueOf(days));
            config.setConfigType("AUDIT_RETENTION");
            config.setConfigName(name);
            config.setDescription("审计日志留存策略（天）");
            config.setStatus(1);
            if (systemConfigService.insert(config) != 1) {
                throw new BusinessException("留存策略保存失败");
            }
        }
    }

    private Date daysBefore(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -days);
        return cal.getTime();
    }

    /** 留存清理安全事件（先 ATTEMPTED 后结果，清理记录为新数据不会被同批删除） */
    private void recordRetentionEvent(String eventType, String result, String operator, String reason, long affectedCount) {
        try {
            SecurityEventLog event = new SecurityEventLog();
            event.setEventType(eventType);
            event.setUsername(operator);
            event.setRiskLevel(AuditLogService.RISK_HIGH);
            event.setResult(result);
            event.setAffectedCount((int) affectedCount);
            event.setDescription("日志留存清理：" + reason);
            auditLogService.recordSecurityEvent(event);
        } catch (Exception e) {
            log.error("记录留存清理安全事件失败：{}", e.getMessage());
        }
    }

}
