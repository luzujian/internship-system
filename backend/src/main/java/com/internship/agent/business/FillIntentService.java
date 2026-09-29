package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import static com.internship.agent.business.AgentTypes.*;

/**
 * 语音/文字智能填表的字段赋值意图服务。
 * 职责划分与 Spring AI 工具调用一致：模型只理解和产出赋值意图（JSON），
 * 真正写入表单由前端完成（写 Vue 响应式数据，不触发提交）。
 * 隐私红线：上行只含表单结构（prop/label/type/选项），不含用户已填字段值；
 * upload 字段不参与模型分配，由响应统一说明需手动上传。
 */
@Service
@Slf4j
public class FillIntentService {
    public record FieldDescriptor(String prop, String label, String type,
                                  List<Map<String, Object>> options, Boolean required, String patternHint,
                                  String valueType, Boolean allowCustom) {
        public FieldDescriptor(String prop, String label, String type, List<Map<String, Object>> options,
                               Boolean required, String patternHint, String valueType) {
            this(prop, label, type, options, required, patternHint, valueType, false);
        }
    }
    public record FillRequest(String formId, String formTitle, List<FieldDescriptor> fields, String message) {}

    /** 参与模型分配的字段类型；upload 只解释不代填。 */
    private static final Set<String> FILLABLE_TYPES = Set.of("input", "textarea", "select", "date", "datetime", "radio", "number");
    private static final int MAX_FIELDS = 40;
    /** 与侧栏输入框的 maxlength 保持一致，允许整段岗位描述直接填入侧栏测试。 */
    private static final int MAX_MESSAGE_LENGTH = 4000;

    private static final String SYSTEM_PROMPT = """
            你是表单字段分配助手。根据用户话语为当前表单生成字段赋值，只输出一个 JSON 对象，不输出任何解释、代码块标记或其他文字。
            格式：{"intent":"form_fill 或 chat","assignments":[{"prop":"字段prop","value":"字符串值","operation":"SET 或 CLEAR","confidence":0.95}],"questions":["需要用户澄清的问题"],"skippedFields":[{"prop":"字段prop","reason":"原因"}]}
            规则：
            1. 只填写用户话语中明确提到的字段；用户说"不变""不要动""其他不用改"的字段绝不填写。
            2. assignments 中的 prop 必须来自给定字段列表，value 一律为字符串。
            3. select/radio 字段：value 从选项的 label 或 value 中选择；仅 allowCustom=true 时可以使用用户明确说出的自定义值。无法唯一匹配时不要猜，把澄清问题放进 questions。
            4. date 类型输出 yyyy-MM-dd；datetime 类型输出 yyyy-MM-dd HH:mm:ss，缺少时间则先澄清；number 类型输出数字字符串；口语音译可合理归正（如"五个"输出"5"）。
            5. 如果话语没有字段赋值意图（提问、闲聊、解释请求），intent 返回 chat，assignments 为空。
            6. confidence 为 0 到 1 的小数，表示该值归属该字段的把握。
            7. 不要编造用户没有说到的内容。同一字段只能返回一次；没有当前字段值时，不猜测“再加一点”等相对修改，先询问用户目标值。
            8. 只有用户明确要求清空具体字段才输出operation=CLEAR和空字符串value；“不变”“不要清空”绝不是清空要求。普通赋值使用SET。""";

    private final AgentTaskService tasks;
    private final AgentCapabilityService capabilities;
    private final AgentModel model;
    private final ObjectMapper json;

    public FillIntentService(AgentTaskService tasks, AgentCapabilityService capabilities,
                             AgentModel model, ObjectMapper json) {
        this.tasks = tasks; this.capabilities = capabilities; this.model = model; this.json = json;
    }

    public Map<String, Object> fill(Actor actor, FillRequest request) {
        requireAllowed(actor);
        validate(request);

        List<FieldDescriptor> fillable = new ArrayList<>();
        List<Map<String, Object>> manualOnly = new ArrayList<>();
        for (FieldDescriptor field : request.fields()) {
            if (field != null && FILLABLE_TYPES.contains(field.type() == null ? "input" : field.type())) fillable.add(field);
            else manualOnly.add(Map.of("prop", field == null ? "" : String.valueOf(field.prop()),
                    "reason", "文件或该类型字段需手动操作，AI 不代填"));
        }
        if (fillable.isEmpty()) throw new AgentException("NO_FILLABLE_FIELD", "当前表单没有可由助手填写的字段。", 400);

        long started = System.currentTimeMillis();
        ChatResponse response;
        try {
            response = model.call(
                    List.<Message>of(new SystemMessage(AgentClock.nowLine() + "\n" + SYSTEM_PROMPT), new UserMessage(userPrompt(request, fillable))),
                    List.of());
        } catch (Exception e) {
            throw new AgentException("MODEL_UNAVAILABLE", "模型服务暂时不可用，请稍后重试。", 502);
        }
        AgentUsage usage = AgentUsage.from(response, System.currentTimeMillis() - started);
        // A model call may outlive a permission change; never return writable assignments after revocation.
        requireAllowed(actor);
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null
                || response.getResult().getOutput().getText() == null || response.getResult().getOutput().getText().isBlank())
            throw new AgentException("MODEL_UNAVAILABLE", "模型未返回有效结果，请稍后重试。", 502);
        Map<String, Object> result = assemble(request, fillable, manualOnly,
                parseJson(response.getResult().getOutput().getText()));
        result.put("usage", usage.toMap());
        return result;
    }

    private void requireAllowed(Actor actor) {
        if (!tasks.enabled()) throw new AgentException("AI_DISABLED", "系统当前未启用AI助手。", 503);
        if (actor.readOnly()) throw new AgentException("READ_ONLY_FORBIDDEN", "管理员只读身份不可使用智能填表。", 403);
        if (!capabilities.has(actor, "form_fill")) throw new AgentException("CAPABILITY_DISABLED", "当前身份未开通智能填表能力。", 403);
    }

    private void validate(FillRequest request) {
        if (request == null) throw invalid();
        if (request.message() == null || request.message().isBlank() || request.message().length() > MAX_MESSAGE_LENGTH)
            throw new AgentException("INVALID_INPUT", "请输入1至4000字的填表内容。", 400);
        if (request.formId() == null || request.formId().length() > 100
                || request.formTitle() == null || request.formTitle().length() > 60)
            throw new AgentException("INVALID_INPUT", "表单标识不正确。", 400);
        if (request.fields() == null || request.fields().isEmpty() || request.fields().size() > MAX_FIELDS)
            throw new AgentException("INVALID_INPUT", "表单字段数量超出支持范围（1至40个）。", 400);
        Set<String> seen = new HashSet<>();
        for (FieldDescriptor field : request.fields()) {
            if (field == null || field.prop() == null || field.prop().isBlank() || field.prop().length() > 60
                    || field.label() == null || field.label().length() > 60) throw invalid();
            if (!seen.add(field.prop())) throw invalid();
        }
    }

    private String userPrompt(FillRequest request, List<FieldDescriptor> fillable) {
        StringBuilder fields = new StringBuilder();
        for (FieldDescriptor field : fillable) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("prop", field.prop());
            item.put("label", field.label());
            item.put("type", field.type() == null ? "input" : field.type());
            if (Boolean.TRUE.equals(field.required())) item.put("required", true);
            if (field.patternHint() != null && !field.patternHint().isBlank()) item.put("hint", field.patternHint());
            if (field.valueType() != null && !field.valueType().isBlank()) item.put("valueType", field.valueType());
            if (Boolean.TRUE.equals(field.allowCustom())) item.put("allowCustom", true);
            if (field.options() != null && !field.options().isEmpty()) item.put("options", field.options());
            fields.append(fields.length() == 0 ? "" : ",").append(json.valueToTree(item).toString());
        }
        return "当前表单：" + request.formTitle() + "\n可填字段列表（JSON 数组）：[" + fields + "]\n用户话语：\"\"\"" + request.message() + "\"\"\"";
    }

    /** 容错解析模型输出：剥离 Markdown 围栏后按 JSON 解析，失败即报模型错误，不做猜测。 */
    private JsonNode parseJson(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end > start) cleaned = cleaned.substring(start, end + 1);
        try {
            JsonNode root = json.readTree(cleaned);
            if (root.isObject()) return root;
        } catch (Exception ignored) {}
        throw new AgentException("MODEL_UNAVAILABLE", "模型返回格式异常，请重试。", 502);
    }

    private Map<String, Object> assemble(FillRequest request, List<FieldDescriptor> fillable,
                                         List<Map<String, Object>> manualOnly, JsonNode parsed) {
        String intent = parsed.path("intent").asText("form_fill");
        List<Map<String, Object>> assignments = new ArrayList<>();
        List<String> questions = new ArrayList<>();
        Map<String, FieldDescriptor> byProp = new HashMap<>();
        for (FieldDescriptor field : fillable) byProp.put(field.prop(), field);

        if ("form_fill".equals(intent)) {
            Map<String, Integer> occurrences = new HashMap<>();
            for (JsonNode item : parsed.path("assignments")) occurrences.merge(item.path("prop").asText(""), 1, Integer::sum);
            for (JsonNode item : parsed.path("assignments")) {
                String prop = item.path("prop").asText("");
                FieldDescriptor field = byProp.get(prop);
                if (field == null) continue; // 模型编造的字段直接丢弃
                if (explicitlyPreserved(request.message(),field)) {
                    manualOnly.add(Map.of("prop",prop,"reason","用户要求保持此字段，已保留当前值。"));
                    continue;
                }
                if (occurrences.getOrDefault(prop, 0) > 1) {
                    String question = field.label() + "返回了多个值，请明确目标值后重试。";
                    if (!questions.contains(question)) questions.add(question);
                    continue;
                }
                boolean clear="CLEAR".equalsIgnoreCase(item.path("operation").asText());
                if(clear&&!explicitlyCleared(request.message(),field)){
                    questions.add("请明确说明是否清空“"+field.label()+"”，本次已保留当前值。");continue;
                }
                String value = clear?"":normalizeValue(field, item.path("value"), questions);
                if (value == null) continue; // 选项无法唯一匹配，已转入 questions
                double confidence = item.path("confidence").asDouble(0.8);
                if (confidence < 0) confidence = 0; if (confidence > 1) confidence = 1;
                if (!Double.isFinite(confidence) || confidence < 0.65) {
                    questions.add("请确认" + field.label() + "是否填写为“" + value + "”。");
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("prop", prop);
                row.put("label", field.label());
                row.put("value", value);
                row.put("confidence", Math.round(confidence * 100) / 100.0);
                row.put("operation",clear?"CLEAR":"SET");
                row.put("overwriteAllowed",clear||explicitlyAssigned(request.message(),field));
                assignments.add(row);
            }
        }
        for (JsonNode question : parsed.path("questions")) {
            String text = question.asText("").trim();
            if (!text.isEmpty() && text.length() <= 200 && questions.size() < 5) questions.add(text);
        }
        List<Map<String, Object>> skipped = new ArrayList<>(manualOnly);
        for (JsonNode item : parsed.path("skippedFields")) {
            String prop = item.path("prop").asText("");
            String reason = item.path("reason").asText("").trim();
            if (byProp.containsKey(prop) && !reason.isEmpty() && reason.length() <= 120
                    && assignments.stream().noneMatch(row -> row.get("prop").equals(prop))) {
                skipped.add(Map.of("prop", prop, "reason", reason));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("intent", assignments.isEmpty() && questions.isEmpty() ? intent : "form_fill");
        result.put("formId", request.formId());
        result.put("formTitle", request.formTitle());
        result.put("assignments", assignments);
        result.put("questions", questions);
        result.put("skippedFields", skipped);
        return result;
    }

    /** Conservative text checks supplement model intent; they never infer a value or widen fields. */
    private List<String> fieldNames(FieldDescriptor field){
        List<String> names=new ArrayList<>(List.of(field.label(),field.prop()));
        if(field.label().contains("人数")||field.prop().toLowerCase(Locale.ROOT).contains("recruit"))names.add("人数");
        if(field.label().contains("薪资")||field.prop().toLowerCase(Locale.ROOT).startsWith("salary"))names.addAll(List.of("薪资","工资"));
        return names.stream().filter(name->name!=null&&!name.isBlank()).distinct().toList();
    }
    private boolean explicitlyPreserved(String message,FieldDescriptor field){
        for(String name:fieldNames(field)){
            String quoted=java.util.regex.Pattern.quote(name);
            String negative="(?:不要|不用|别|不需要|不能|不可以|禁止)";
            String mutation="(?:修改|更改|改变|调整|改|动|清空|清除|清掉|删除|重写|设置)";
            if(java.util.regex.Pattern.compile(negative+"(?:再)?"+mutation+"\\s*"+quoted+"|"+negative+"(?:再)?(?:把|将)?\\s*"+quoted+"\\s*"+mutation+"|(?:保持|保留)\\s*"+quoted+"|"+quoted+"\\s*(?:也|都|先|暂时)?\\s*(?:不变|不动|不改|保持原|保持不变|"+negative+"(?:再)?"+mutation+")").matcher(message).find())return true;
        }
        return false;
    }
    private boolean explicitlyCleared(String message,FieldDescriptor field){
        if(explicitlyPreserved(message,field))return false;
        for(String name:fieldNames(field)){
            String quoted=java.util.regex.Pattern.quote(name);
            if(java.util.regex.Pattern.compile("(?:清空|清除|清掉)\\s*"+quoted+"|"+quoted+"(?:字段|内容)?\\s*(?:清空|清除|清掉|留空|改为空|设为空)").matcher(message).find())return true;
        }
        return false;
    }
    private boolean explicitlyAssigned(String message,FieldDescriptor field){
        if(explicitlyPreserved(message,field))return false;
        for(String clause:message.split("[，,。；;\\n]")){
            if(fieldNames(field).stream().noneMatch(clause::contains))continue;
            return true;
        }
        return false;
    }

    /** 选项字段归一：优先匹配选项 value，其次 label 文本；无法唯一匹配转入 questions。 */
    private String normalizeValue(FieldDescriptor field, JsonNode raw, List<String> questions) {
        if (raw == null || raw.isNull()) return null;
        String value = raw.isTextual() ? raw.asText().trim() : raw.toString();
        if (value.isEmpty() || value.length() > ("textarea".equals(field.type()) ? MAX_MESSAGE_LENGTH : 500)) return null;
        List<Map<String, Object>> options = field.options();
        if ("array".equals(field.valueType())) {
            List<String> parts = Arrays.stream(value.split("[、,，;；]"))
                    .map(String::trim).filter(item -> !item.isEmpty()).toList();
            if (parts.isEmpty()) return null;
            if (options == null || options.isEmpty()) return String.join("、", parts);
            List<String> matchedValues = new ArrayList<>();
            for (String part : parts) {
                String matched = matchOption(field, part, options, questions);
                if (matched == null) return null;
                if (!matchedValues.contains(matched)) matchedValues.add(matched);
            }
            return String.join("、", matchedValues);
        }
        if (!("select".equals(field.type()) || "radio".equals(field.type()))) return value;
        if (options == null || options.isEmpty()) {
            if (Boolean.TRUE.equals(field.allowCustom())) return value;
            questions.add(field.label() + "的选项尚未加载，请加载选项后再填写。");
            return null;
        }
        return matchOption(field, value, options, questions);
    }

    private String matchOption(FieldDescriptor field, String value,
                               List<Map<String, Object>> options, List<String> questions) {
        String matched = null;
        for (Map<String, Object> option : options) {
            String optionValue = String.valueOf(option.getOrDefault("value", ""));
            String optionLabel = String.valueOf(option.getOrDefault("label", ""));
            if (value.equals(optionValue) || value.equals(optionLabel)) {
                if (matched != null && !matched.equals(optionValue)) {
                    questions.add("“" + value + "”在" + field.label() + "的选项中对应多个值，请手动选择。");
                    return null;
                }
                matched = optionValue;
            }
        }
        if (matched == null) {
            if (Boolean.TRUE.equals(field.allowCustom()) && "select".equals(field.type())) return value;
            questions.add(field.label() + "没有与“" + value + "”唯一匹配的选项，请从选项中选择。");
            return null;
        }
        return matched;
    }

    private static AgentException invalid() {
        return new AgentException("INVALID_INPUT", "填表请求格式不正确。", 400);
    }
}
