package com.internship.audit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1 写接口审计覆盖率检查
 * 计划要求：所有写接口（POST/PUT/DELETE/PATCH）必须有 @Log 审计注解或显式审计调用。
 * 未覆盖的接口必须加入下方白名单并注明原因（显式审计调用 / 无状态变更 / 公开端点）。
 */
class WriteEndpointAuditCoverageTest {

    /** 白名单：无需 @Log 的写接口（类简单名.方法名 → 原因） */
    private static final java.util.Map<String, String> WHITELIST = java.util.Map.ofEntries(
            // ===== 显式审计调用（服务端构造审计，非 @Log 注解） =====
            java.util.Map.entry("AuthController.login", "显式审计：recordLoginAudit 写登录日志+安全事件"),
            java.util.Map.entry("LogController.exportLogs", "显式审计：recordExportAudit 写导出审计"),

            // ===== 无状态变更：仅刷新令牌 =====
            java.util.Map.entry("AuthController.refreshToken", "无状态变更：仅刷新令牌"),

            // ===== AI 查询类：POST 但仅为查询/流式生成，无业务状态变更（禁止记录对话正文） =====
            java.util.Map.entry("AIChatController.chat", "AI查询：无状态变更，禁止记录正文"),
            java.util.Map.entry("AIChatController.streamChat", "AI查询：无状态变更，禁止记录正文"),
            java.util.Map.entry("ResourceController.aiSearchResources", "AI搜索：无状态变更，禁止记录正文"),
            java.util.Map.entry("ResourceQueryAgentController.queryResources", "AI查询：无状态变更，禁止记录正文"),
            java.util.Map.entry("ResourceQueryAgentController.queryResourcesStream", "AI查询：无状态变更，禁止记录正文"),
            java.util.Map.entry("StudentQueryAgentController.processStudentOperation", "AI查询代理：无状态变更，禁止记录正文"),
            java.util.Map.entry("StudentQueryAgentController.processStudentOperationStream", "AI查询代理：无状态变更，禁止记录正文"),
            java.util.Map.entry("JobDescriptionGeneratorController.generateJobDescription", "AI生成：无状态变更，禁止记录正文"),
            java.util.Map.entry("JobPostingParseController.parseJobPosting", "AI解析：无状态变更，禁止记录岗位原文"),

            // ===== 验证码发送：仅 Redis 临时数据，无持久状态变更 =====
            java.util.Map.entry("SmsController.sendVerifyCode", "验证码：仅Redis临时数据"),
            java.util.Map.entry("AccountSettingsController.sendVerifyCode", "验证码：仅Redis临时数据"),
            java.util.Map.entry("CompanyRegisterController.sendVerifyCode", "验证码：仅Redis临时数据"),
            java.util.Map.entry("CompanyRegisterController.sendManagementCode", "申请管理验证码：仅邮件与Redis临时状态，禁止记录邮箱证明材料"),
            java.util.Map.entry("CompanyRegisterController.verifyManagementCode", "申请管理预校验：仅签发短时凭据，禁止记录验证码和令牌"),
            java.util.Map.entry("CompanyRegisterController.applicationDetail", "申请详情只读查询：POST承载短时凭据，禁止记录令牌和登记材料"),
            java.util.Map.entry("AuthController.sendPasswordResetCode", "验证码：仅Redis临时数据，失败路径写安全事件"),
            java.util.Map.entry("AuthController.verifyPasswordResetCode", "验证码预校验：仅Redis临时数据，失败路径写安全事件"),

            // ===== 页面点击类已读/查看标记：按计划"页面点击不逐条入库" =====
            java.util.Map.entry("HomeController.markAnnouncementAsRead", "页面点击类已读标记，按计划不入库"),
            java.util.Map.entry("HomeController.markAnnouncementAsUnread", "页面点击类标记，按计划不入库"),
            java.util.Map.entry("ChatController.markMessageAsRead", "页面点击类已读标记，按计划不入库"),
            java.util.Map.entry("ChatController.batchMarkAsRead", "页面点击类已读标记，按计划不入库"),
            java.util.Map.entry("CompanyController.markApplicationAsViewed", "页面点击类查看标记，按计划不入库"),

            // ===== 预览类：只读计算，无状态变更 =====
            java.util.Map.entry("FreeInternshipReconciliationController.preview", "预览：只读计算，无状态变更"),
            java.util.Map.entry("LogController.previewRetention", "预览：只读统计，无状态变更"),
            java.util.Map.entry("TeacherAutoTaskController.preview", "AI任务解析预览：仅保存确认身份，无业务变更，禁止记录消息正文"),
            java.util.Map.entry("TeacherAutoTaskController.confirmationPreview", "任务确认预览：仅保存动作身份，实际创建另有审计"),
            java.util.Map.entry("TeacherAutoTaskController.enablePreview", "启用确认预览：仅保存动作身份，实际启用另有审计"),
            java.util.Map.entry("TeacherAutomationV2Controller.draftPreview", "仅保存可修改任务草稿及预览，实际确认另有审计，禁止记录消息正文"),
            java.util.Map.entry("TeacherAutomationV2Controller.preview", "仅保存确认身份，实际确认另有审计"),
            java.util.Map.entry("TeacherAutomationV2Controller.matches", "只读查询权限范围内匹配名单"),
            java.util.Map.entry("AutomationVerificationController.preview", "邮件核实预览，实际确认另有审计"),
            java.util.Map.entry("AgentCapabilityAdminController.previewConfig", "权限确认预览：仅保存动作身份，实际权限变更在saveConfig审计"),
            java.util.Map.entry("LogController.verifyIntegrity", "校验：只读重算哈希链，失败时由告警服务记录安全事件"),

            // ===== 已停用旧入口：仅返回错误提示，无状态变更 =====
            java.util.Map.entry("StudentInternshipConfirmationController.submit", "旧确认入口已停用，仅返回错误，无状态变更")
    );

    @Test
    void allWriteEndpointsMustHaveAuditAnnotationOrExplicitCall() throws Exception {
        Set<BeanDefinition> candidates = scanControllers();
        assertTrue(candidates.size() > 20, "应扫描到大量 Controller，实际: " + candidates.size());

        List<String> uncovered = new ArrayList<>();
        int checked = 0;

        for (BeanDefinition candidate : candidates) {
            String className = candidate.getBeanClassName();
            Class<?> clazz = Class.forName(className);
            String simpleName = clazz.getSimpleName();

            for (Method method : clazz.getDeclaredMethods()) {
                if (!isWriteEndpoint(method)) {
                    continue;
                }
                checked++;
                boolean hasLog = method.isAnnotationPresent(com.internship.anno.Log.class);
                String key = simpleName + "." + method.getName();
                boolean whitelisted = WHITELIST.containsKey(key);
                if (!hasLog && !whitelisted) {
                    uncovered.add(key);
                }
            }
        }

        assertTrue(uncovered.isEmpty(),
                "发现 " + uncovered.size() + " 个写接口缺少审计（共检查 " + checked + " 个）。\n" +
                        "缺少 @Log 且不在白名单的接口:\n" + String.join("\n", uncovered) +
                        "\n请为其添加 @Log 注解，或加入白名单并注明显式审计调用/无状态变更原因。");
    }

    /** GET 导出接口（敏感批量导出）必须有 @Log（计划：批量导入导出必须记录） */
    @Test
    void getExportEndpointsMustHaveAuditAnnotation() throws Exception {
        Set<BeanDefinition> candidates = scanControllers();
        List<String> uncovered = new ArrayList<>();

        for (BeanDefinition candidate : candidates) {
            String className = candidate.getBeanClassName();
            Class<?> clazz = Class.forName(className);
            String simpleName = clazz.getSimpleName();

            for (Method method : clazz.getDeclaredMethods()) {
                GetMapping mapping = method.getAnnotation(GetMapping.class);
                if (mapping == null) {
                    continue;
                }
                boolean isExport = false;
                for (String path : mapping.value()) {
                    if (path.toLowerCase().contains("export")) {
                        isExport = true;
                        break;
                    }
                }
                if (!isExport) {
                    continue;
                }
                // LogController.exportLogs 为显式审计（recordExportAudit + ATTEMPTED 关键操作模式）
                if ("LogController".equals(simpleName) && "exportLogs".equals(method.getName())) {
                    continue;
                }
                if (!method.isAnnotationPresent(com.internship.anno.Log.class)) {
                    uncovered.add(simpleName + "." + method.getName() + " -> " + String.join(",", mapping.value()));
                }
            }
        }

        assertTrue(uncovered.isEmpty(),
                "发现 " + uncovered.size() + " 个 GET 导出接口缺少 @Log（批量导出必须记录）:\n" +
                        String.join("\n", uncovered));
    }

    /** 批量删除接口必须标记 critical=true（计划：批量删除为关键操作，审计不可用禁止执行） */
    @Test
    void batchDeleteEndpointsMustBeCritical() throws Exception {
        Set<BeanDefinition> candidates = scanControllers();
        List<String> notCritical = new ArrayList<>();

        for (BeanDefinition candidate : candidates) {
            String className = candidate.getBeanClassName();
            Class<?> clazz = Class.forName(className);
            String simpleName = clazz.getSimpleName();

            for (Method method : clazz.getDeclaredMethods()) {
                com.internship.anno.Log logAnno = method.getAnnotation(com.internship.anno.Log.class);
                if (logAnno == null || !"DELETE".equals(logAnno.operationType())) {
                    continue;
                }
                boolean isBatch = logAnno.description() != null && logAnno.description().contains("批量删除");
                if (!isBatch) {
                    continue;
                }
                if (!logAnno.critical()) {
                    notCritical.add(simpleName + "." + method.getName() + " -> " + logAnno.description());
                }
            }
        }

        assertTrue(notCritical.isEmpty(),
                "发现 " + notCritical.size() + " 个批量删除接口未标记 critical=true:\n" +
                        String.join("\n", notCritical));
    }

    /**
     * 计划规定的关键操作类别必须标记 critical=true（ATTEMPTED 先行、审计不可用禁止执行）：
     * 密码重置/修改、角色切换、备份/恢复/删除备份、权限调整、批量删除、批量导出、日志留存清理。
     */
    @Test
    void criticalOperationCategoriesMustBeMarkedCritical() throws Exception {
        Set<BeanDefinition> candidates = scanControllers();
        List<String> violations = new ArrayList<>();
        int criticalCount = 0;

        for (BeanDefinition candidate : candidates) {
            String className = candidate.getBeanClassName();
            Class<?> clazz = Class.forName(className);
            String simpleName = clazz.getSimpleName();

            for (Method method : clazz.getDeclaredMethods()) {
                com.internship.anno.Log logAnno = method.getAnnotation(com.internship.anno.Log.class);
                if (logAnno == null) {
                    continue;
                }
                if (logAnno.critical()) {
                    criticalCount++;
                    continue;
                }
                if (isCriticalCategory(logAnno)) {
                    violations.add(simpleName + "." + method.getName() + " -> "
                            + logAnno.operationType() + "/" + logAnno.module() + "/" + logAnno.description());
                }
            }
        }

        // 至少应存在计划要求的各类关键操作标记
        assertTrue(criticalCount >= 40, "关键操作标记数量应覆盖计划要求（密码/权限/批量/备份/留存/导出），实际: " + criticalCount);
        assertTrue(violations.isEmpty(),
                "发现 " + violations.size() + " 个关键操作类别未标记 critical=true:\n" +
                        String.join("\n", violations));
    }

    /** 判断注解是否属于计划规定的关键操作类别 */
    private boolean isCriticalCategory(com.internship.anno.Log logAnno) {
        String type = logAnno.operationType();
        String module = logAnno.module() == null ? "" : logAnno.module();
        String desc = logAnno.description() == null ? "" : logAnno.description();

        // 密码重置/修改
        if ("RESET_PASSWORD".equals(type)) {
            return true;
        }
        // 管理员角色切换
        if ("SWITCH_ROLE".equals(type)) {
            return true;
        }
        // 备份/恢复/删除备份
        if ("BACKUP".equals(module) && ("BACKUP".equals(type) || "RESTORE".equals(type) || "DELETE".equals(type))) {
            return true;
        }
        // 权限调整（分配/角色/权限管理模块）
        if ((module.contains("PERMISSION")) && ("ASSIGN".equals(type) || "CREATE".equals(type)
                || "UPDATE".equals(type) || "DELETE".equals(type))) {
            return true;
        }
        // 批量删除
        if ("DELETE".equals(type) && desc.contains("批量删除")) {
            return true;
        }
        // 批量导出
        if ("EXPORT".equals(type)) {
            return true;
        }
        // 日志留存清理
        if ("LOG_MANAGEMENT".equals(module) && ("CLEAN".equals(type) || desc.contains("留存"))) {
            return true;
        }
        return false;
    }

    private Set<BeanDefinition> scanControllers() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        return scanner.findCandidateComponents("com.internship.controller");
    }

    /** 判断方法是否为写接口（POST/PUT/DELETE/PATCH，含 @RequestMapping method 属性形式） */
    private boolean isWriteEndpoint(Method method) {
        if (method.isAnnotationPresent(PostMapping.class)
                || method.isAnnotationPresent(PutMapping.class)
                || method.isAnnotationPresent(DeleteMapping.class)
                || method.isAnnotationPresent(PatchMapping.class)) {
            return true;
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping != null) {
            for (RequestMethod requestMethod : mapping.method()) {
                if (requestMethod == RequestMethod.POST
                        || requestMethod == RequestMethod.PUT
                        || requestMethod == RequestMethod.DELETE
                        || requestMethod == RequestMethod.PATCH) {
                    return true;
                }
            }
        }
        return false;
    }
}
