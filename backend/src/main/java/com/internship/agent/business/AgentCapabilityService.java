package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import static com.internship.agent.business.AgentTypes.*;

/**
 * AI 能力授权：叠加在原业务权限之上，只收窄不放大。
 * 配置存 system_config.AGENT_CAPABILITIES_CONFIG（JSON：roleDefaults + userOverrides）；
 * 无配置时代码内默认集生效。能力开关只是闸门——具体工具仍受
 * AgentReadTools 角色白名单、validateObject 对象鉴权与 scopeFingerprint 数据范围三层约束。
 */
@Service
@Slf4j
public class AgentCapabilityService {
    public static final String CONFIG_KEY = "AGENT_CAPABILITIES_CONFIG";
    public static final String USER_OVERRIDE_CONFIG_PREFIX = "AGENT_CAP_USER_OVERRIDE:";

    public static String userOverrideConfigKey(String overrideKey) {
        return USER_OVERRIDE_CONFIG_PREFIX + overrideKey;
    }
    /** 全部已知能力；job_apply 为学生固定能力，其余可配置。 */
    public static final Set<String> KNOWN = Set.of(
            "agent_chat", "voice_input", "form_fill", "internship_plan", "job_apply",
            "auto_task", "auto_task_notify", "teacher_internship_summary");

    /** 代码内默认：未配置时按角色返回最小可用集（与阶段二设计文档 5.1 一致）。
     * 实习计划默认仅学生；投递与撤回为学生固定能力，执行仍需确认和业务校验。
     * 阶段四全系统推广：教师、管理员默认开通 form_fill（只写本地表单 model，
     * 提交仍走原按钮原权限），可在配置中单独关闭。
     * 教师实习情况智能简报默认教师可用；自动化任务仍仅由辅导员业务能力开放。 */
    private static final Map<String, Set<String>> DEFAULTS = Map.of(
            "student", Set.of("agent_chat", "voice_input", "form_fill", "internship_plan"),
            "teacher", Set.of("agent_chat", "voice_input", "form_fill", "teacher_internship_summary"),
            "company", Set.of("agent_chat", "voice_input", "form_fill"),
            "admin", Set.of("agent_chat", "voice_input", "form_fill"));

    public static final Set<String> AUTO_TASK_ACTIONS = Set.of("SEND_REMINDER");
    public static final Set<String> AUTO_TASK_CHANNELS = Set.of("STATION_REMINDER", "EMAIL");
    public static final Set<String> AUTO_TASK_SCOPES = Set.of("OWN_CLASSES", "OWN_DIVISION", "OWN_COLLEGE");
    public static final int DEFAULT_MAX_TARGETS_PER_RUN = 200;
    public static final int MAX_TARGETS_PER_RUN_LIMIT = 1000000;

    private final SystemConfigService config;
    private final ObjectMapper json;

    public AgentCapabilityService(SystemConfigService config, ObjectMapper json) {
        this.config = config;
        this.json = json;
    }

    /** 返回全部已知能力的开闭状态；只读身份（管理员 impersonate）永远不含可操作能力。 */
    public Map<String, Boolean> capabilitiesFor(Actor actor) {
        Set<String> granted = new HashSet<>(configuredRoleDefaults(actor));
        applyUserOverrides(actor, granted);
        // 兼容旧配置：历史角色配置或用户覆盖不再控制投递能力。
        if ("ROLE_STUDENT".equals(actor.role())) granted.add("job_apply");
        else granted.remove("job_apply");
        if (actor.readOnly()) granted.removeIf(cap -> !"agent_chat".equals(cap));
        Map<String, Boolean> result = new LinkedHashMap<>();
        for (String capability : KNOWN) result.put(capability, granted.contains(capability));
        return result;
    }

    public boolean has(Actor actor, String capability) {
        if (capability == null || !KNOWN.contains(capability)) return false;
        return capabilitiesFor(actor).getOrDefault(capability, Boolean.FALSE);
    }

    /** 返回默认角色矩阵，供管理端在未保存配置时展示当前生效值。 */
    public Map<String, List<String>> defaultRoleDefaults() {
        Map<String, List<String>> result = new LinkedHashMap<>();
        for (String role : Set.of("student", "teacher", "company", "admin")) {
            result.put(role, List.copyOf(DEFAULTS.getOrDefault(role, Set.of("agent_chat"))));
        }
        return result;
    }

    /** 自动化策略配置无效或缺失时返回关闭策略，避免授权旁路。 */
    public AutomationPolicy effectiveAutomationPolicy(Actor actor) {
        return automationPolicyFor(actor, readConfig(), capabilitiesFor(actor));
    }

    public Map<String, Object> automationPolicyFor(Actor actor) {
        return effectiveAutomationPolicy(actor).toMap();
    }

    public AutomationPolicy automationPolicyFor(Actor actor, JsonNode config) {
        Set<String> granted = new HashSet<>(DEFAULTS.getOrDefault(roleKey(actor), Set.of("agent_chat")));
        if (config != null && config.path("roleDefaults").path(roleKey(actor)).isArray()) {
            granted.clear();
            config.path("roleDefaults").path(roleKey(actor)).forEach(cap -> { if (KNOWN.contains(cap.asText())) granted.add(cap.asText()); });
        }
        if (config != null) applyOverrideNode(granted, config.path("userOverrides").path(overrideKey(actor)));
        if (actor.readOnly()) granted.removeIf(cap -> !"agent_chat".equals(cap));
        Map<String, Boolean> capabilities = new HashMap<>();
        KNOWN.forEach(cap -> capabilities.put(cap, granted.contains(cap)));
        return automationPolicyFor(actor, config, capabilities);
    }

    private AutomationPolicy automationPolicyFor(Actor actor, JsonNode config, Map<String, Boolean> capabilities) {
        boolean canManage = Boolean.TRUE.equals(capabilities.get("auto_task"));
        boolean canNotify = Boolean.TRUE.equals(capabilities.get("auto_task_notify"));
        if (!canManage || config == null || !config.path("autoTaskPolicy").isObject()) {
            return AutomationPolicy.closed(canManage);
        }

        JsonNode roleNode = config.path("autoTaskPolicy").path("roleDefaults").path(roleKey(actor));
        JsonNode overrideNode = config.path("autoTaskPolicy").path("userOverrides").path(overrideKey(actor));
        return readPolicy(actor, canManage, canNotify, roleNode, overrideNode, config.path("autoTaskPolicy"));
    }

    private String roleKey(Actor actor) {
        String role = actor.role() == null ? "" : actor.role();
        if (role.startsWith("ROLE_TEACHER")) return "teacher";
        return role.replace("ROLE_", "").toLowerCase(Locale.ROOT);
    }

    /** 用户级覆盖键：角色 + 用户ID（同一用户不同登录形态保持一致）。 */
    private String overrideKey(Actor actor) {
        return actor.role() + ":" + actor.id();
    }

    private Set<String> configuredRoleDefaults(Actor actor) {
        JsonNode root = readConfig();
        if (root != null && root.path("roleDefaults").path(roleKey(actor)).isArray()) {
            Set<String> values = new HashSet<>();
            root.path("roleDefaults").path(roleKey(actor)).forEach(item -> {
                String name = item.asText("");
                if (KNOWN.contains(name)) values.add(name);
            });
            return values;
        }
        return DEFAULTS.getOrDefault(roleKey(actor), Set.of("agent_chat"));
    }

    private void applyUserOverrides(Actor actor, Set<String> granted) {
        try {
            var row = config.findByConfigKey(userOverrideConfigKey(overrideKey(actor)));
            if (row != null && row.getConfigValue() != null && !row.getConfigValue().isBlank()) {
                JsonNode overrides = json.readTree(row.getConfigValue());
                if (overrides.isObject()) {
                    applyOverrideNode(granted, overrides);
                    return;
                }
            }
        } catch (Exception e) {
            log.warn("用户级AI能力覆盖读取失败，按角色默认继续：{}", e.getMessage());
        }
        JsonNode root = readConfig();
        if (root != null) {
            applyOverrideNode(granted, root.path("userOverrides").path(overrideKey(actor)));
        }
    }

    private void applyOverrideNode(Set<String> granted, JsonNode overrides) {
        if (overrides == null || !overrides.isObject()) return;
        Iterator<Map.Entry<String, JsonNode>> fields = overrides.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!KNOWN.contains(entry.getKey())) continue;
            if (entry.getValue().asBoolean(false)) granted.add(entry.getKey());
            else granted.remove(entry.getKey());
        }
    }

    private AutomationPolicy readPolicy(Actor actor, boolean canManage, boolean canNotify,
                                        JsonNode roleNode, JsonNode overrideNode, JsonNode policyRoot) {
        if (!roleNode.isObject() || (overrideNode.isObject() == false && !overrideNode.isMissingNode() && !overrideNode.isNull())) {
            return AutomationPolicy.closed(canManage);
        }
        JsonNode override = overrideNode.isObject() ? overrideNode : json.createObjectNode();

        Set<String> actions = readEnums(override.has("actions") ? override.path("actions") : roleNode.path("actions"),
                AUTO_TASK_ACTIONS);
        Set<String> channels = readEnums(override.has("channels") ? override.path("channels") : roleNode.path("channels"),
                AUTO_TASK_CHANNELS);
        Set<String> scopes = readEnums(override.has("scopes") ? override.path("scopes") : roleNode.path("scopes"),
                AUTO_TASK_SCOPES);
        Integer maxTargets = readMaxTargets(override.has("maxTargetsPerRun")
                ? override.path("maxTargetsPerRun") : roleNode.path("maxTargetsPerRun"));
        // Explicit user limits always take precedence over the role's unlimited audience.
        if (!override.has("maxTargetsPerRun") && "ALL_AUTHORIZED".equals(roleNode.path("audienceLimitMode").asText()))
            maxTargets = Integer.MAX_VALUE;
        if(override.has("maxTargetsPerRun") && !"ALL_AUTHORIZED".equals(roleNode.path("audienceLimitMode").asText())) {
            Integer roleLimit=readMaxTargets(roleNode.path("maxTargetsPerRun"));
            if(roleLimit!=null&&maxTargets!=null)maxTargets=Math.min(roleLimit,maxTargets);
        }
        if (actions == null || channels == null || scopes == null || maxTargets == null) {
            return AutomationPolicy.closed(canManage);
        }

        String effectiveScope = com.internship.service.TeacherAutomationScopeService.scopeOf(actor.teacherType());
        boolean manageReady = canManage && actions.contains("SEND_REMINDER") && scopes.contains(effectiveScope);
        boolean notifyReady = canNotify && !channels.isEmpty();
        boolean executeReady = manageReady && notifyReady;
        int cooldown = policyRoot.path("globalCooldownMinutes").asInt(1440);
        if (cooldown < 1 || cooldown > 10080) return AutomationPolicy.closed(canManage);
        return new AutomationPolicy(manageReady, notifyReady, executeReady,
                actions, channels, scopes, maxTargets, cooldown);
    }

    private Set<String> readEnums(JsonNode node, Set<String> allowed) {
        if (!node.isArray()) return null;
        Set<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String value = item.asText("").trim();
            if (!allowed.contains(value)) return null;
            values.add(value);
        }
        return values;
    }

    private Integer readMaxTargets(JsonNode node) {
        if (!node.canConvertToInt()) return null;
        int value = node.asInt(-1);
        return value >= 1 ? value : null;
    }

    /** 读取配置；解析失败或未配置返回 null（保持代码内默认，fail-open 到最小集而非全开）。 */
    private JsonNode readConfig() {
        try {
            var row = config.findByConfigKey(CONFIG_KEY);
            if (row == null || row.getConfigValue() == null || row.getConfigValue().isBlank()) return null;
            return json.readTree(row.getConfigValue());
        } catch (Exception e) {
            log.warn("AI 能力授权配置解析失败，使用默认能力集：{}", e.getMessage());
            return null;
        }
    }

    public record AutomationPolicy(boolean canManage, boolean canNotify, boolean canExecute,
                                   Set<String> actions, Set<String> channels, Set<String> scopes,
                                   int maxTargetsPerRun, int globalCooldownMinutes) {
        public AutomationPolicy(boolean canManage, boolean canNotify, boolean canExecute,
                                Set<String> actions, Set<String> channels, Set<String> scopes, int maxTargetsPerRun) {
            this(canManage, canNotify, canExecute, actions, channels, scopes, maxTargetsPerRun, 1440);
        }
        public static AutomationPolicy closed(boolean canManage) {
            return new AutomationPolicy(canManage, false, false,
                    Set.of(), Set.of(), Set.of(), 0);
        }

        public boolean allowsChannel(String channel) {
            return canExecute && channel != null && channels.contains(channel);
        }

        public Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("canManage", canManage);
            result.put("canNotify", canNotify);
            result.put("canExecute", canExecute);
            result.put("actions", List.copyOf(actions));
            result.put("channels", List.copyOf(channels));
            result.put("scopes", List.copyOf(scopes));
            result.put("maxTargetsPerRun", maxTargetsPerRun);
            result.put("audienceLimitMode", maxTargetsPerRun == Integer.MAX_VALUE ? "ALL_AUTHORIZED" : "LIMITED");
            result.put("globalCooldownMinutes", globalCooldownMinutes);
            return result;
        }
    }
}
