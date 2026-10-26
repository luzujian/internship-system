package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.agent.business.AgentCapabilityService.AutomationPolicy;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.entity.SystemConfig;
import com.internship.entity.TeacherAutoTask;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.*;
import com.internship.service.AgentCapabilityParseService;
import com.internship.service.SystemConfigService;
import org.springframework.stereotype.Service;
import java.util.*;

/** Read-only capability proposals, authorized with the server-resolved actor rather than a web-thread SecurityContext.
 * The only persisted object is an owner-bound confirmation action; this service never changes capability configuration. */
@Service
public class AgentCapabilityPreviewService {
    private static final String CONFIG_KEY = "AGENT_CAPABILITIES_CONFIG";
    private final AgentCapabilityParseService parser;
    private final AgentCapabilityService capabilityService;
    private final AgentFeatureGate featureGate;
    private final SystemConfigService configs;
    private final TeacherAutoTaskMapper autoTasks;
    private final StudentUserMapper students;
    private final TeacherUserMapper teachers;
    private final CompanyUserMapper companies;
    private final AdminUserMapper admins;
    private final ObjectMapper json;
    private final AgentActionService actions;
    private final AgentCapabilityUserMapper accountLookup;

    public AgentCapabilityPreviewService(AgentCapabilityParseService parser, AgentCapabilityService capabilityService,
            AgentFeatureGate featureGate, SystemConfigService configs, TeacherAutoTaskMapper autoTasks,
            StudentUserMapper students, TeacherUserMapper teachers, CompanyUserMapper companies,
            AdminUserMapper admins, ObjectMapper json, AgentActionService actions, AgentCapabilityUserMapper accountLookup) {
        this.parser=parser; this.capabilityService=capabilityService; this.featureGate=featureGate;
        this.configs=configs; this.autoTasks=autoTasks; this.students=students; this.teachers=teachers;
        this.companies=companies; this.admins=admins; this.json=json; this.actions=actions; this.accountLookup=accountLookup;
    }

    public Map<String,Object> preview(Actor actor, Map<String,Object> body) throws Exception {
        requireWritableAdmin(actor);
        return preview(actor, readRootWithUserOverrides(), body);
    }

    /** The existing confirmation endpoint applies the same whitelist after locking the current configuration. */
    public ObjectNode validateConfiguration(Actor actor, JsonNode current, JsonNode input) {
        requireWritableAdmin(actor);
        return normalizedProposal(current,input);
    }

    private static void requireWritableAdmin(Actor actor) {
        if(actor==null)throw AgentIdentityService.forbidden();
        AgentIdentityService.requireAdmin(actor);
        if(actor.readOnly())throw AgentIdentityService.forbidden();
    }

    @SuppressWarnings("unchecked")
    public Map<String,Object> parse(Actor actor, String message) throws Exception {
        requireWritableAdmin(actor);
        featureGate.requireEnabled();
        if (message != null) {
            for (String clause : message.split("[，,。；;\\n]")) {
                if (clause.matches(".*(?:学生|企业|管理员).*" )
                        && !clause.matches(".*(?:教师|老师|辅导员).*" )
                        && clause.matches(".*(?:自动化任务|自动任务|自动跟进|自动提醒).*" )
                        && clause.matches(".*(?:赋予|授予|开通|开启|启用|允许|授权|开放|赋权).*" )
                        && !clause.matches(".*(?:取消|关闭|禁止|不允许|撤销|禁用|不要).*" ))
                    throw new BusinessException("自动化任务目前仅支持教师，不能为学生、企业或管理员开通。尚未保存任何权限变更。");
            }
        }
        JsonNode root = readRootWithUserOverrides();
        Map<String, Object> parsed = parser.parse(message, readRoleDefaults(root), readAutoTaskPolicy(root));
        List<String> questions = new ArrayList<>((List<String>) parsed.getOrDefault("questions", List.of()));
        List<Map<String,Object>> selected = new ArrayList<>();
        for (Map<String,Object> request : (List<Map<String,Object>>) parsed.getOrDefault("userRequests", List.of())) {
            String role = String.valueOf(request.get("role"));
            String query = String.valueOf(request.get("query"));
            List<Map<String,Object>> matches = accountLookup.findExact(role, query);
            if (matches == null || matches.isEmpty()) { questions.add("未找到"+query+"，请提供该用户的角色和完整账号。"); continue; }
            if (matches.size() != 1) {
                questions.add(query+"存在重名，请明确完整账号："+matches.stream().map(row -> String.valueOf(row.get("account"))).toList());
                continue;
            }
            Map<String,Object> item = new LinkedHashMap<>(request);
            item.put("userId",matches.get(0).get("id")); item.remove("query"); selected.add(item);
        }
        parsed.put("questions", questions); parsed.remove("userRequests");
        if (!questions.isEmpty()) { parsed.put("userChanges", List.of()); return parsed; }
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("roleDefaults",parsed.get("proposedRoleDefaults")); request.put("autoTaskPolicy",parsed.get("proposedAutoTaskPolicy"));
        request.put("userChanges",selected);
        parsed.putAll(preview(actor, root, request));
        return parsed;
    }

    private Map<String,Object> confirmationParameters(Map<String,Object> body) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("roleDefaults",body.getOrDefault("proposedRoleDefaults",body.get("roleDefaults")));
        result.put("autoTaskPolicy",body.getOrDefault("proposedAutoTaskPolicy",body.get("autoTaskPolicy")));
        result.put("userChanges",body.getOrDefault("userChanges",List.of()));
        result.put("configurationVersion",body.get("configurationVersion"));
        return result;
    }

    private Map<String,Object> preview(Actor actor, JsonNode root, Map<String,Object> body) throws Exception {
        Map<String,Object> input = confirmationParameters(body);
        if (input.get("roleDefaults") == null) input.put("roleDefaults", readRoleDefaults(root));
        if (input.get("autoTaskPolicy") == null) input.put("autoTaskPolicy", readAutoTaskPolicy(root));
        List<Map<String,Object>> changes = normalizeUserChanges(root, json.valueToTree(input.get("userChanges")));
        input.put("userChanges",changes);
        input.put("configurationVersion",configurationVersion(root));
        ObjectNode normalized = normalizedProposal(root, json.valueToTree(input));
        input.put("roleDefaults",json.convertValue(normalized.path("roleDefaults"),Map.class));
        input.put("autoTaskPolicy",json.convertValue(normalized.path("autoTaskPolicy"),Map.class));
        var action = actions.preview(actor, "CAPABILITY_SAVE", CONFIG_KEY, input, String.valueOf(input.get("configurationVersion")));
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("actionId",action.id()); result.put("configurationVersion",input.get("configurationVersion"));
        result.put("proposedRoleDefaults", input.get("roleDefaults")); result.put("proposedAutoTaskPolicy", input.get("autoTaskPolicy"));
        result.put("userChanges",changes); result.put("questions",List.of());
        result.put("impact",buildImpact(normalized, (Map<String,List<String>>)input.get("roleDefaults"), (Map<String,Object>)input.get("autoTaskPolicy")));
        return result;
    }

    private ObjectNode normalizedProposal(JsonNode current, JsonNode input) {
        ObjectNode updated = json.createObjectNode();
        updated.set("roleDefaults",normalizeRoleDefaults(input.path("roleDefaults")));
        for (String role : List.of("student", "company", "admin")) {
            for (String capability : List.of("auto_task", "auto_task_notify")) {
                boolean before = false;
                for (JsonNode value : current.path("roleDefaults").path(role)) if (capability.equals(value.asText())) before = true;
                for (JsonNode value : updated.path("roleDefaults").path(role)) {
                    if (!before && capability.equals(value.asText())) throw new BusinessException("自动化任务目前仅支持教师，不能为学生、企业或管理员开通。尚未保存任何权限变更。");
                }
            }
        }
        updated.set("autoTaskPolicy",normalizeAutoTaskPolicy(input.path("autoTaskPolicy")));
        ObjectNode overrides = current.path("userOverrides").isObject() ? ((ObjectNode)current.path("userOverrides")).deepCopy() : json.createObjectNode();
        for (JsonNode row : input.path("userChanges")) {
            String role = row.path("role").asText(); long id = row.path("userId").asLong();
            String key = roleKeyOf(role, requireExistingUser(role,id));
            if (!"teacher".equals(role)) {
                for (String capability : List.of("auto_task", "auto_task_notify"))
                    if (row.path("overrides").path(capability).asBoolean(false)
                            && !current.path("userOverrides").path(key).path(capability).asBoolean(false))
                        throw new BusinessException("自动化任务目前仅支持教师，不能向该用户开放。尚未保存任何权限变更。");
            }
            if (!key.equals(row.path("roleKey").asText())) throw new BusinessException("用户身份已变化，请重新预览。");
            overrides.set(key,row.path("overrides"));
            if (row.path("automationPolicy").isObject()) ((ObjectNode)updated.path("autoTaskPolicy").path("userOverrides")).set(key,row.path("automationPolicy"));
        }
        updated.set("autoTaskPolicy",normalizeAutoTaskPolicy(updated.path("autoTaskPolicy")));
        updated.set("userOverrides",overrides);
        return updated;
    }

    private List<Map<String,Object>> normalizeUserChanges(JsonNode current, JsonNode input) {
        if (!input.isArray() || input.size() > 10) throw new BusinessException("每次最多配置10位用户。");
        List<Map<String,Object>> result = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (JsonNode row : input) {
            String role = row.path("role").asText(); long id = row.path("userId").asLong();
            if (!AgentCapabilityParseService.ROLES.contains(role) || id <= 0) throw new BusinessException("请明确用户的角色和账号。");
            Object user = requireExistingUser(role,id); String key = roleKeyOf(role,user);
            if (!seen.add(key)) throw new BusinessException("同一用户存在重复变更，请合并后重新预览。");
            JsonNode before = current.path("userOverrides").path(key);
            ObjectNode next = row.path("replace").asBoolean(false) || !before.isObject() ? json.createObjectNode() : ((ObjectNode)before).deepCopy();
            if (!row.path("overrides").isObject()) throw new BusinessException("用户能力覆盖不能为空。");
            row.path("overrides").fields().forEachRemaining(entry -> {
                if (!AgentCapabilityParseService.CAPABILITIES.contains(entry.getKey()) || !entry.getValue().isBoolean()) throw new BusinessException("用户能力覆盖不正确。");
                next.set(entry.getKey(),entry.getValue());
            });
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("role",role); item.put("userId",id); item.put("roleKey",key); item.put("replace",true);
            item.put("fromOverrides",before.isObject() ? before : json.createObjectNode()); item.put("overrides",next);
            JsonNode userNode = json.valueToTree(user);
            item.put("name",userNode.path("name").asText(userNode.path("companyName").asText("")));
            item.put("account",userNode.path("username").asText(""));
            if (row.has("automationPolicy")) {
                if (!"teacher".equals(role) || !row.path("automationPolicy").isObject()) throw new BusinessException("只有教师可以配置自动跟进策略。");
                ObjectNode policy = current.path("autoTaskPolicy").path("userOverrides").path(key).isObject()
                        ? ((ObjectNode)current.path("autoTaskPolicy").path("userOverrides").path(key)).deepCopy() : json.createObjectNode();
                item.put("fromAutomationPolicy",policy.deepCopy());
                policy.setAll((ObjectNode)row.path("automationPolicy")); item.put("automationPolicy",policy);
            }
            result.add(item);
        }
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

    private ObjectNode normalizeRoleDefaults(JsonNode input) {
        ObjectNode result = json.createObjectNode();
        for (String role : AgentCapabilityParseService.ROLES) {
            JsonNode list = input.path(role);
            if (!list.isArray()) throw new BusinessException("角色能力矩阵缺少" + role + "。");
            List<String> caps = new ArrayList<>();
            for (JsonNode item : list) {
                String cap = item.asText("").trim();
                if (!AgentCapabilityParseService.CAPABILITIES.contains(cap)) {
                    throw new BusinessException("不支持的AI能力：" + cap);
                }
                if (!caps.contains(cap)) caps.add(cap);
            }
            result.set(role, json.valueToTree(caps));
        }
        return result;
    }

    private ObjectNode normalizeAutoTaskPolicy(JsonNode input) {
        if (input.path("version").asInt(-1) != 1) {
            throw new BusinessException("自动化策略版本不正确。");
        }
        ObjectNode result = json.createObjectNode();
        result.put("version", 1);
        int cooldown = input.path("globalCooldownMinutes").asInt(1440);
        if (cooldown < 1 || cooldown > 10080) throw new BusinessException("统一提醒间隔需为1至10080分钟。");
        result.put("globalCooldownMinutes", cooldown);
        ObjectNode roleDefaults = json.createObjectNode();
        JsonNode teacher = input.path("roleDefaults").path("teacher");
        if (!teacher.isObject()) throw new BusinessException("自动化策略缺少教师角色。");

        ObjectNode normalizedTeacher = json.createObjectNode();
        normalizedTeacher.set("actions", normalizeEnums(teacher.path("actions"),
                AgentCapabilityService.AUTO_TASK_ACTIONS, "自动化动作"));
        normalizedTeacher.set("channels", normalizeEnums(teacher.path("channels"),
                AgentCapabilityService.AUTO_TASK_CHANNELS, "自动化渠道"));
        normalizedTeacher.set("scopes", normalizeEnums(teacher.path("scopes"),
                AgentCapabilityService.AUTO_TASK_SCOPES, "数据范围"));
        JsonNode maxTargets = teacher.path("maxTargetsPerRun");
        if (!maxTargets.canConvertToInt() || maxTargets.asInt(-1) < 1
                || maxTargets.asInt(-1) > AgentCapabilityService.MAX_TARGETS_PER_RUN_LIMIT) {
            throw new BusinessException("单次触达人数需在1至1000000之间。");
        }
        normalizedTeacher.put("maxTargetsPerRun", maxTargets.asInt());
        String limitMode=teacher.path("audienceLimitMode").asText("LIMITED");
        if(!Set.of("LIMITED","ALL_AUTHORIZED").contains(limitMode))throw new BusinessException("自动化人数限制方式不正确。");
        normalizedTeacher.put("audienceLimitMode",limitMode);
        roleDefaults.set("teacher", normalizedTeacher);
        result.set("roleDefaults", roleDefaults);

        ObjectNode userOverrides = json.createObjectNode();
        JsonNode inputOverrides = input.path("userOverrides");
        if (inputOverrides.isObject()) {
            inputOverrides.fields().forEachRemaining(entry -> {
                String key = entry.getKey();
                if (!key.matches("ROLE_TEACHER_(COLLEGE|DEPARTMENT|COUNSELOR):\\d+")
                        || !entry.getValue().isObject()) {
                    throw new BusinessException("自动化用户覆盖键不正确：" + key);
                }
                ObjectNode override = json.createObjectNode();
                entry.getValue().fields().forEachRemaining(cap -> {
                    if (!Set.of("actions", "channels", "scopes", "maxTargetsPerRun").contains(cap.getKey())) {
                        throw new BusinessException("自动化用户覆盖字段不正确：" + cap.getKey());
                    }
                    if ("maxTargetsPerRun".equals(cap.getKey())) {
                        if (!cap.getValue().canConvertToInt() || cap.getValue().asInt() < 1 || cap.getValue().asInt() > 1000000)
                            throw new BusinessException("用户单次触达人数需在1至1000000之间。");
                        override.put(cap.getKey(), cap.getValue().asInt());
                    } else {
                        Set<String> allowed = switch(cap.getKey()) {
                            case "actions" -> AgentCapabilityService.AUTO_TASK_ACTIONS;
                            case "channels" -> AgentCapabilityService.AUTO_TASK_CHANNELS;
                            default -> AgentCapabilityService.AUTO_TASK_SCOPES;
                        };
                        override.set(cap.getKey(), normalizeEnums(cap.getValue(), allowed, "用户自动化策略"));
                    }
                });
                userOverrides.set(key, override);
            });
        }
        result.set("userOverrides", userOverrides);
        return result;
    }

    private com.fasterxml.jackson.databind.node.ArrayNode normalizeEnums(JsonNode input,
                                                                         Set<String> allowed,
                                                                         String label) {
        if (!input.isArray()) throw new BusinessException(label + "必须是数组。");
        List<String> values = new ArrayList<>();
        for (JsonNode item : input) {
            String value = item.asText("").trim();
            if (!allowed.contains(value)) throw new BusinessException("不支持的" + label + "：" + value);
            if (!values.contains(value)) values.add(value);
        }
        return json.valueToTree(values);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readAutoTaskPolicy(JsonNode root) {
        JsonNode policy = root.path("autoTaskPolicy");
        return policy.isObject() ? json.convertValue(policy, Map.class) : Map.of("version",1,"globalCooldownMinutes",1440,
                "roleDefaults",Map.of("teacher",Map.of("actions",List.of(),"channels",List.of(),"scopes",List.of(),"maxTargetsPerRun",200)),"userOverrides",Map.of());
    }

    /** 影响面：列出将被暂停的任务和用户覆盖键，避免管理员只看到抽象数量。 */
    private Map<String, Object> buildImpact(JsonNode currentRoot, Map<String, List<String>> proposed,
                                            Map<String, Object> proposedPolicy) {
        ObjectNode proposedRoot = json.createObjectNode();
        proposedRoot.set("roleDefaults", json.valueToTree(proposed));
        proposedRoot.set("autoTaskPolicy", json.valueToTree(proposedPolicy));
        proposedRoot.set("userOverrides", currentRoot.path("userOverrides"));

        List<Map<String, Object>> impactedTasks = new ArrayList<>();
        List<TeacherAutoTask> active = autoTasks.findActiveIncludingExpired();
        if (active != null) {
            for (TeacherAutoTask task : active) {
                TeacherUser teacher = teachers.findById(task.getTeacherId());
                if (teacher == null) continue;
                AutomationPolicy policy = capabilityService.automationPolicyFor(actorOf(teacher), proposedRoot);
                String reason = taskImpactReason(policy, task.getAction());
                if (reason == null) continue;
                impactedTasks.add(Map.of(
                        "id", task.getId(),
                        "name", task.getName() == null ? "" : task.getName(),
                        "teacherId", task.getTeacherId(),
                        "teacherName", teacher.getName() == null ? "" : teacher.getName(),
                        "reason", reason
                ));
            }
        }

        List<String> userOverrideKeys = new ArrayList<>();
        JsonNode overrides = currentRoot.path("userOverrides");
        if (overrides.isObject()) overrides.fieldNames().forEachRemaining(userOverrideKeys::add);
        return Map.of(
                "tasks", impactedTasks,
                "tasksAtRisk", impactedTasks.size(),
                "userOverrideKeys", userOverrideKeys,
                "usersWithOverrides", userOverrideKeys.size()
        );
    }

    private String taskImpactReason(AutomationPolicy policy, String action) {
        if (!policy.canManage()) return "auto_task 能力将被关闭";
        if (!policy.canNotify()) return "auto_task_notify 能力将被关闭";
        if (!policy.allowsChannel(action)) return "当前策略不允许该提醒渠道";
        if (policy.scopes().isEmpty()) return "当前策略不允许该教师负责范围";
        return null;
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

    private Actor actorOf(TeacherUser teacher) {
        String role = "ROLE_TEACHER_" + teacher.getTeacherType();
        return new Actor(role + ":" + teacher.getId() + ":false:", teacher.getId(), role,
                teacher.getName(), teacher.getTeacherType(), null, null, false, Set.of());
    }
}
