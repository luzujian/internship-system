package com.internship.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.agent.business.AgentCapabilityService;
import com.internship.agent.business.AgentCapabilityPreviewService;
import com.internship.agent.business.AgentFeatureGate;
import com.internship.agent.business.AgentActionService;
import com.internship.agent.business.AgentIdentityService;
import com.internship.agent.business.AgentException;
import com.internship.mapper.AgentCapabilityUserMapper;
import com.internship.mapper.SystemConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.SystemConfig;
import com.internship.entity.TeacherAutoTask;
import com.internship.exception.BusinessException;
import com.internship.mapper.AdminUserMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherAutoTaskMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.AgentCapabilityParseService;
import com.internship.service.SystemConfigService;
import com.internship.service.TeacherAutoTaskService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理员 AI 能力治理：自然语言生成角色矩阵变更预览（不落库）与用户级覆盖（原子写回）。
 * 模型只产出草稿；所有能力、角色与用户存在性由后端校验，实际写配置仍走系统配置。
 */
@RestController
@RequestMapping("/api/admin/agent-capabilities")
@PreAuthorize("hasRole('ADMIN')")
public class AgentCapabilityAdminController {

    private static final String CONFIG_KEY = "AGENT_CAPABILITIES_CONFIG";
    private final AgentCapabilityParseService parser;
    private final AgentCapabilityService capabilityService;
    private final AgentFeatureGate featureGate;
    private final TeacherAutoTaskService autoTaskService;
    private final SystemConfigService configs;
    private final TeacherAutoTaskMapper autoTasks;
    private final StudentUserMapper students;
    private final TeacherUserMapper teachers;
    private final CompanyUserMapper companies;
    private final AdminUserMapper admins;
    private final ObjectMapper json;
    private AgentActionService actions;
    private AgentIdentityService identities;
    private SystemConfigMapper configRows;
    private AgentCapabilityUserMapper accountLookup;
    private AgentCapabilityPreviewService previews;

    @Autowired
    public void configurePreviewService(AgentCapabilityPreviewService previews) { this.previews=previews; }

    private AgentCapabilityPreviewService previewService() {
        if(previews!=null)return previews;
        // Compatibility for existing isolated controller tests constructed without a Spring container.
        return new AgentCapabilityPreviewService(parser, capabilityService, featureGate, configs, autoTasks,
                students, teachers, companies, admins, json, actions, accountLookup);
    }

    @Autowired
    public void configureConfirmation(AgentActionService actions, AgentIdentityService identities,
                                      SystemConfigMapper configRows, AgentCapabilityUserMapper accountLookup) {
        this.actions=actions; this.identities=identities; this.configRows=configRows; this.accountLookup=accountLookup;
    }

    public AgentCapabilityAdminController(AgentCapabilityParseService parser,
                                          AgentCapabilityService capabilityService,
                                          AgentFeatureGate featureGate,
                                          TeacherAutoTaskService autoTaskService,
                                          SystemConfigService configs,
                                          TeacherAutoTaskMapper autoTasks,
                                          StudentUserMapper students,
                                          TeacherUserMapper teachers,
                                          CompanyUserMapper companies,
                                          AdminUserMapper admins,
                                          ObjectMapper json) {
        this.parser = parser;
        this.capabilityService = capabilityService;
        this.featureGate = featureGate;
        this.autoTaskService = autoTaskService;
        this.configs = configs;
        this.autoTasks = autoTasks;
        this.students = students;
        this.teachers = teachers;
        this.companies = companies;
        this.admins = admins;
        this.json = json;
    }

    @GetMapping("/users")
    public Result searchUsers(@RequestHeader("Authorization") String authorization,
                              @RequestParam String role, @RequestParam(defaultValue = "") String keyword) {
        admin(authorization);
        if (!AgentCapabilityParseService.ROLES.contains(role)) throw new BusinessException("用户角色不正确。");
        String query = keyword.trim();
        if (query.length() > 100) throw new BusinessException("搜索内容不能超过100个字符。");
        return Result.success(accountLookup.search(role, query));
    }

    /** 自然语言生成角色级变更预览；不修改配置，返回差异与影响。 */
    @PostMapping("/parse")
    @Log(module = "SYSTEM_MANAGEMENT", operationType = "SELECT", description = "AI解析能力权限变更")
    public Result parse(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, String> body) throws Exception {
        return Result.success(previewService().parse(admin(authorization), body == null ? null : body.get("message")));
    }

    @PostMapping("/config/preview")
    public Result previewConfig(@RequestHeader("Authorization") String authorization, @RequestBody Map<String,Object> body) throws Exception {
        return Result.success(previewService().preview(admin(authorization), body));
    }

    /** 读取角色矩阵与自动化策略；角色能力未配置时返回代码内默认值。 */
    @GetMapping("/config")
    public Result getConfig() throws Exception {
        JsonNode root = readRootWithUserOverrides();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("roleDefaults", root.has("roleDefaults")
                ? readRoleDefaults(root) : capabilityService.defaultRoleDefaults());
        result.put("autoTaskPolicy", readAutoTaskPolicy(root));
        result.put("userOverrides", root.path("userOverrides"));
        result.put("configured", root.has("roleDefaults"));
        result.put("configurationVersion", configurationVersion(root));
        return Result.success(result);
    }

    /** 专用保存入口：角色矩阵与自动化策略白名单校验，防止通用配置接口绕过。 */
    @PutMapping("/config")
    @Log(module = "SYSTEM_MANAGEMENT", operationType = "UPDATE", description = "更新AI能力授权与自动化策略")
    public Result saveConfig(@RequestHeader("Authorization") String authorization, @RequestBody Map<String, Object> body) throws Exception {
        Actor actor = admin(authorization);
        if (body == null || !Boolean.TRUE.equals(body.get("confirmed"))) throw new BusinessException("请先核对具体变更，再确认保存。");
        String actionId = String.valueOf(body.get("actionId"));
        Map<String,Object> parameters = confirmationParameters(body);
        Map<String,Object> receipt = actions.execute(actor, actionId, "CAPABILITY_SAVE", parameters, () -> {
            admin(authorization); // Account and permission are checked again inside the business transaction.
            lockConfiguration();
            try {
                JsonNode current = readRootWithUserOverrides();
                if (!configurationVersion(current).equals(parameters.get("configurationVersion")))
                    throw new AgentException("CONFIG_CHANGED", "配置已被其他管理员修改，请重新预览。", 409);
                JsonNode input = json.valueToTree(parameters);
                ObjectNode updated = previewService().validateConfiguration(actor, current, input);
                writeConfig(updated);
                List<TeacherAutoTask> paused = autoTaskService.pauseTasksWithoutEffectivePolicy();
                return Map.of("saved", true, "pausedTasks", paused == null ? List.of() : paused,
                        "configurationVersion", configurationVersion(updated), "link", "/admin/permissions");
            } catch (RuntimeException e) { throw e; }
            catch (Exception e) { throw new IllegalStateException("配置未保存。", e); }
        });
        return Result.success(receipt);
    }

    private Actor admin(String authorization) {
        Actor actor = identities.resolve(authorization);
        AgentIdentityService.requireAdmin(actor);
        if (actor.readOnly()) throw AgentIdentityService.forbidden();
        return actor;
    }

    private void lockConfiguration() {
        if (configRows.lockByConfigKey(CONFIG_KEY) == null) throw new BusinessException("AI能力配置不存在，请先完成本地初始化。");
    }

    private Map<String,Object> confirmationParameters(Map<String,Object> body) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("roleDefaults",body.getOrDefault("proposedRoleDefaults",body.get("roleDefaults")));
        result.put("autoTaskPolicy",body.getOrDefault("proposedAutoTaskPolicy",body.get("autoTaskPolicy")));
        result.put("userChanges",body.getOrDefault("userChanges",List.of()));
        result.put("configurationVersion",body.get("configurationVersion"));
        return result;
    }

    private String configurationVersion(JsonNode root) {
        try {
            String canonical = json.writeValueAsString(sortJson(root));
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException("配置版本不可用。",e); }
    }
    private JsonNode sortJson(JsonNode node) {
        if (node.isObject()) { ObjectNode sorted=json.createObjectNode(); List<String> names=new ArrayList<>(); node.fieldNames().forEachRemaining(names::add); java.util.Collections.sort(names); names.forEach(name -> sorted.set(name,sortJson(node.path(name)))); return sorted; }
        if (node.isArray()) { var sorted=json.createArrayNode(); node.forEach(value -> sorted.add(sortJson(value))); return sorted; }
        return node;
    }

    /** 用户级覆盖：独立配置行保存，避免主配置 JSON 超过 system_config.config_value 容量。 */
    @PutMapping("/user-overrides")
    @Log(module = "SYSTEM_MANAGEMENT", operationType = "UPDATE", description = "更新用户级AI能力授权")
    @Transactional(rollbackFor = Exception.class)
    public Result updateUserOverrides(@RequestBody Map<String, Object> body) throws Exception {
        lockConfiguration();
        String role = str(body, "role");
        Object userIdRaw = body == null ? null : body.get("userId");
        if (role == null || !AgentCapabilityParseService.ROLES.contains(role)) {
            throw new BusinessException("角色不正确。");
        }
        if (!(userIdRaw instanceof Number number) || number.longValue() <= 0) {
            throw new BusinessException("用户ID不正确。");
        }
        long userId = number.longValue();
        Object user = requireExistingUser(role, userId);
        String roleKey = roleKeyOf(role, user);
        if (!(body.get("overrides") instanceof Map<?, ?> rawOverrides)) {
            throw new BusinessException("能力覆盖不能为空。");
        }
        ObjectNode overrides = json.createObjectNode();
        for (Map.Entry<?, ?> entry : rawOverrides.entrySet()) {
            String cap = String.valueOf(entry.getKey()).trim();
            if (!AgentCapabilityParseService.CAPABILITIES.contains(cap)) continue;
            overrides.put(cap, Boolean.parseBoolean(String.valueOf(entry.getValue())));
        }
        if (overrides.isEmpty()) {
            throw new BusinessException("没有可识别的AI能力。");
        }

        ObjectNode existing = readUserOverride(roleKey).deepCopy();
        existing.setAll(overrides);
        writeUserOverride(roleKey, existing);
        List<TeacherAutoTask> paused = autoTaskService.pauseTasksWithoutEffectivePolicy();
        return Result.success(Map.of("role", role, "userId", userId, "roleKey", roleKey,
                "overrides", existing, "pausedTasks", paused));
    }

    /** 清除用户级覆盖，恢复该用户角色默认能力。 */
    @DeleteMapping("/user-overrides")
    @Log(module = "SYSTEM_MANAGEMENT", operationType = "DELETE", description = "清除用户级AI能力覆盖")
    @Transactional(rollbackFor = Exception.class)
    public Result deleteUserOverrides(@RequestParam String role, @RequestParam Long userId) throws Exception {
        lockConfiguration();
        if (role == null || !AgentCapabilityParseService.ROLES.contains(role.toLowerCase())) {
            throw new BusinessException("角色不正确。");
        }
        if (userId == null || userId <= 0) throw new BusinessException("用户ID不正确。");
        Object user = requireExistingUser(role.toLowerCase(), userId);
        String roleKey = roleKeyOf(role.toLowerCase(), user);
        clearUserOverride(roleKey);
        List<TeacherAutoTask> paused = autoTaskService.pauseTasksWithoutEffectivePolicy();
        return Result.success(Map.of("role", role, "userId", userId, "roleKey", roleKey,
                "overrides", json.createObjectNode(), "pausedTasks", paused));
    }

    /** 读取某用户当前覆盖：优先独立配置行，其次兼容旧版主配置内嵌覆盖。 */
    @GetMapping("/user-overrides")
    public Result getUserOverrides(@RequestParam String role, @RequestParam Long userId) {
        if (role == null || !AgentCapabilityParseService.ROLES.contains(role.toLowerCase())) {
            throw new BusinessException("角色不正确。");
        }
        if (userId == null || userId <= 0) throw new BusinessException("用户ID不正确。");
        Object user = requireExistingUser(role.toLowerCase(), userId);
        String roleKey = roleKeyOf(role.toLowerCase(), user);
        return Result.success(Map.of("roleKey", roleKey, "overrides", readUserOverride(roleKey)));
    }

    private JsonNode readConfig() throws Exception {
        SystemConfig row = configs.findByConfigKey(CONFIG_KEY);
        if (row == null || row.getConfigValue() == null || row.getConfigValue().isBlank()) {
            return json.createObjectNode();
        }
        try {
            return json.readTree(row.getConfigValue());
        } catch (Exception e) {
            throw new BusinessException("当前AI能力配置无效，请先在权限管理页修复。");
        }
    }

    /** 合并主配置、旧版内嵌覆盖和每用户独立配置行。 */
    private JsonNode readRootWithUserOverrides() throws Exception {
        JsonNode base = readConfig();
        ObjectNode root = base != null && base.isObject()
                ? ((ObjectNode) base).deepCopy() : json.createObjectNode();
        ObjectNode overrides = root.path("userOverrides").isObject()
                ? ((ObjectNode) root.path("userOverrides")).deepCopy() : json.createObjectNode();
        for (SystemConfig row : configs.findAll()) {
            if (row == null || row.getConfigKey() == null
                    || !row.getConfigKey().startsWith(AgentCapabilityService.USER_OVERRIDE_CONFIG_PREFIX)
                    || row.getConfigValue() == null || row.getConfigValue().isBlank()) continue;
            String roleKey = row.getConfigKey().substring(AgentCapabilityService.USER_OVERRIDE_CONFIG_PREFIX.length());
            try {
                JsonNode value = json.readTree(row.getConfigValue());
                if (value.isObject()) overrides.set(roleKey, value);
            } catch (Exception ignored) {
                // 单条损坏不阻断其他用户覆盖，管理员可在用户级配置中覆盖修复。
            }
        }
        root.set("userOverrides", overrides);
        return root;
    }

    private ObjectNode readUserOverride(String roleKey) {
        try {
            SystemConfig row = configs.findByConfigKey(AgentCapabilityService.userOverrideConfigKey(roleKey));
            if (row != null && row.getConfigValue() != null && !row.getConfigValue().isBlank()) {
                JsonNode value = json.readTree(row.getConfigValue());
                if (value.isObject()) return ((ObjectNode) value).deepCopy();
            }
            JsonNode legacy = readConfig().path("userOverrides").path(roleKey);
            if (legacy.isObject()) return ((ObjectNode) legacy).deepCopy();
        } catch (Exception ignored) {
        }
        return json.createObjectNode();
    }

    private synchronized void writeUserOverride(String roleKey, JsonNode overrides) throws Exception {
        String key = AgentCapabilityService.userOverrideConfigKey(roleKey);
        SystemConfig row = configs.findByConfigKey(key);
        if (row == null) {
            row = new SystemConfig();
            row.setConfigKey(key);
            row.setConfigType("json");
            row.setConfigName("AI用户级能力覆盖");
            row.setDescription("用户级AI能力授权覆盖；优先于角色默认能力");
            row.setStatus(1);
            row.setConfigValue(json.writeValueAsString(overrides));
            configs.insert(row);
            return;
        }
        row.setConfigValue(json.writeValueAsString(overrides));
        row.setUpdateTime(new java.util.Date());
        configs.update(row);
    }

    private synchronized void clearUserOverride(String roleKey) throws Exception {
        writeUserOverride(roleKey, json.createObjectNode());
    }

    private synchronized void writeConfig(JsonNode root) throws Exception {
        ObjectNode main = json.createObjectNode();
        if (root.path("roleDefaults").isObject()) main.set("roleDefaults", root.path("roleDefaults"));
        if (root.path("autoTaskPolicy").isObject()) main.set("autoTaskPolicy", root.path("autoTaskPolicy"));
        JsonNode overrides = root.path("userOverrides");
        if (overrides.isObject()) {
            overrides.fields().forEachRemaining(entry -> {
                if (!entry.getValue().isObject()) return;
                try {
                    writeUserOverride(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    throw new IllegalStateException("保存用户级AI能力覆盖失败：" + entry.getKey(), e);
                }
            });
        }
        SystemConfig row = configs.findByConfigKey(CONFIG_KEY);
        if (row == null) throw new BusinessException("AI能力配置不存在。");
        row.setConfigValue(json.writeValueAsString(main));
        row.setUpdateTime(new java.util.Date());
        configs.update(row);
    }
    private Map<String, List<String>> readRoleDefaults(JsonNode root) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Map<String, List<String>> codeDefaults = capabilityService.defaultRoleDefaults();
        JsonNode defaults = root.path("roleDefaults");
        for (String role : AgentCapabilityParseService.ROLES) {
            List<String> caps = new ArrayList<>();
            JsonNode list = defaults.path(role);
            if (!list.isArray()) caps.addAll(codeDefaults.getOrDefault(role, List.of()));
            else list.forEach(item -> {
                String cap = item.asText("").trim();
                if (AgentCapabilityParseService.CAPABILITIES.contains(cap) && !caps.contains(cap)) {
                    caps.add(cap);
                }
            });
            result.put(role, caps);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readAutoTaskPolicy(JsonNode root) {
        JsonNode policy = root.path("autoTaskPolicy");
        return policy.isObject() ? json.convertValue(policy, Map.class) : Map.of("version",1,"globalCooldownMinutes",1440,
                "roleDefaults",Map.of("teacher",Map.of("actions",List.of(),"channels",List.of(),"scopes",List.of(),"maxTargetsPerRun",200)),"userOverrides",Map.of());
    }

    private Object requireExistingUser(String role, long userId) {
        Object user = switch (role) {
            case "student" -> students.findById(userId);
            case "teacher" -> teachers.findById(userId);
            case "company" -> companies.findById(userId);
            default -> admins.findById(userId);
        };
        if (user == null) throw new BusinessException("用户不存在。");
        return user;
    }

    /** 与 AgentCapabilityService 的 overrideKey 保持一致：教师按子角色展开，其余按基础角色。 */
    private String roleKeyOf(String role, Object user) {
        if (user instanceof com.internship.entity.TeacherUser teacher) {
            String type = teacher.getTeacherType() == null ? "" : teacher.getTeacherType();
            return "ROLE_TEACHER_" + type + ":" + teacher.getId();
        }
        long id;
        if (user instanceof com.internship.entity.StudentUser student) id = student.getId();
        else if (user instanceof com.internship.entity.CompanyUser company) id = company.getId();
        else if (user instanceof com.internship.entity.AdminUser admin) id = admin.getId();
        else throw new BusinessException("用户类型不正确。");
        return "ROLE_" + role.toUpperCase() + ":" + id;
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return null;
        String value = String.valueOf(body.get(key)).trim().toLowerCase();
        return value.isEmpty() ? null : value;
    }
}
