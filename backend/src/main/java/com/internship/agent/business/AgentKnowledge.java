package com.internship.agent.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.mapper.AgentKnowledgeMapper;
import com.internship.service.SysDictService;
import com.internship.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import static com.internship.agent.business.AgentTypes.*;

@Service
@Slf4j
public class AgentKnowledge {
    private final ObjectMapper json;
    private final List<Map<String,Object>> coreCards;
    private final List<Map<String,Object>> systemCards;
    private final Map<String,Map<String,Object>> accessRules = new HashMap<>();
    private volatile String version;
    private volatile List<Map<String,Object>> cards;

    @Autowired(required = false)
    private AgentKnowledgeMapper dbMapper;
    @Autowired(required = false)
    private SysDictService dictService;
    @Autowired(required = false)
    private SystemConfigService configService;
    private static volatile String statusPattern;

    public AgentKnowledge(ObjectMapper json) throws IOException {
        this.json = json;
        this.coreCards = readCards("agent/knowledge.json");
        this.systemCards = readCards("agent/system-knowledge.json");
        coreCards.forEach(card -> accessRules.put(String.valueOf(card.get("id")), card));
        systemCards.forEach(card -> accessRules.put(String.valueOf(card.get("id")), card));
        this.cards = coreCards;
        refreshVersion();
        log.info("AgentKnowledge 从 classpath 加载：{} 张核心卡片，{} 张系统卡片，版本 {}", coreCards.size(), systemCards.size(), version);
    }

    @PostConstruct
    public void load() {
        if (loadFromDb()) { initStatusPattern(); return; }
        initStatusPattern();
    }

    private void initStatusPattern() {
        try {
            String names = "暂无有效投递|已投递未录用|已录用待确认|已定单位待实习|实习进行中|实习已结束|实习已中断|已延期实习|未投递|无offer|待确认|已确定|实习中|已结束|已中断|延期";
            if (dictService != null) {
                String configured = dictService.codesByType("internship_status").stream()
                        .map(code -> dictService.label("internship_status", code))
                        .collect(Collectors.joining("|"));
                if (!configured.isBlank()) names = names + "|" + configured;
            }
            statusPattern = ".*(" + names + ").*";
        } catch (Exception e) {
            statusPattern = ".*(暂无有效投递|已投递未录用|已录用待确认|已定单位待实习|实习进行中|实习已结束|实习已中断|已延期实习|未投递|无offer|待确认|已确定|实习中|已结束|已中断|延期).*";
        }
    }

    private boolean loadFromDb() {
        if (dbMapper == null) { log.info("AgentKnowledge dbMapper 为空，跳过 DB 加载"); return false; }
        try {
            List<Map<String, Object>> rows = dbMapper.findAllCards();
            if (rows == null || rows.isEmpty()) { log.info("AgentKnowledge DB 返回空，回退 classpath"); return false; }
            Map<String,Map<String,Object>> merged = new LinkedHashMap<>();
            coreCards.forEach(card -> merged.put(String.valueOf(card.get("id")), card));
            for (Map<String, Object> row : rows) {
                Map<String, Object> card = new LinkedHashMap<>(row);
                card.put("roles", parseJsonList(card.get("roles")));
                card.put("keywords", parseJsonList(card.get("keywords")));
                card.put("states", parseJsonList(card.get("states")));
                String id = String.valueOf(card.get("id"));
                Map<String,Object> bundled = merged.get(id);
                // Keep packaged corrections when the database still contains an older guide.
                if (bundled == null || verifiedDate(card).isAfter(verifiedDate(bundled))) merged.put(id, card);
            }
            this.cards = List.copyOf(merged.values());
            refreshVersion();
            log.info("AgentKnowledge 合并 DB 知识：{} 张核心卡片，版本 {}", cards.size(), version);
            return true;
        } catch (Exception e) {
            log.warn("AgentKnowledge DB 加载失败，回退 classpath：{}", e.getMessage());
            return false;
        }
    }

    private List<Map<String,Object>> readCards(String path) throws IOException {
        try (var stream = new ClassPathResource(path).getInputStream()) {
            return List.copyOf(json.readValue(stream, new TypeReference<List<Map<String,Object>>>() {}));
        }
    }

    private java.time.LocalDate verifiedDate(Map<String,Object> card) {
        String date = Objects.toString(card.get("verifiedAt"), "");
        try { return java.time.LocalDate.parse(date.substring(0, Math.min(10, date.length()))); }
        catch (java.time.format.DateTimeParseException e) { return java.time.LocalDate.MIN; }
    }

    private List<Object> parseJsonList(Object value) {
        if (value == null) return List.of();
        if (value instanceof List<?> list) return List.copyOf(list);
        try {
            return json.readValue(String.valueOf(value), new TypeReference<List<Object>>() {});
        } catch (Exception e) { return List.of(); }
    }

    private String computeVersion(byte[] content) {
        try {
            byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(content);
            return "kn-" + java.util.HexFormat.of().formatHex(hash).substring(0, 12);
        } catch (Exception e) { return "kn-unknown"; }
    }
    private void refreshVersion() throws IOException {
        List<Map<String,Object>> effective = new ArrayList<>(cards);
        effective.addAll(systemCards);
        this.version = computeVersion(json.writeValueAsBytes(effective));
    }
    public String getVersion() { return version; }
    public ToolResult lookup(Actor actor,String topic) {
        String actorRole = role(actor);
        boolean admin = "admin".equals(actorRole);
        Map<String,Map<String,Object>> all = new LinkedHashMap<>();
        cards.forEach(card -> all.put(String.valueOf(card.get("id")), card));
        systemCards.forEach(card -> all.put(String.valueOf(card.get("id")), card));
        Set<String> studentMenus = "student".equals(actorRole) ? studentMenuPaths() : Set.of();
        List<Map<String,Object>> visible = all.values().stream()
                .filter(card -> admin || visibleFor(card, actor, actorRole, studentMenus)).toList();
        String fieldKey = AgentAnswerScope.fieldKey(actor, topic);
        if (fieldKey != null) {
            for (Map<String,Object> card : visible) {
                if (!"CODE_TRACED".equals(card.get("verifyLevel")) || !(card.get("fieldAnswers") instanceof Map<?,?> answers)
                        || !(answers.get(fieldKey) instanceof String answer) || answer.isBlank()) continue;
                Map<String,Object> focused = new LinkedHashMap<>();
                for (String key : List.of("id", "title", "routes", "source", "verifiedAt", "verifyLevel"))
                    if (card.containsKey(key)) focused.put(key, card.get(key));
                focused.put("field", fieldKey); focused.put("answer", answer);
                return AgentReadTools.result("knowledge_lookup", "字段填写说明", actor, List.of(focused), 1,
                        Map.of("knowledgeVersion", version, "matched", true, "fieldKey", fieldKey, "conciseAnswer", answer), List.of(), List.of());
            }
        }
        String normalized=topic==null?"":topic.trim().toLowerCase(Locale.ROOT);
        List<Map<String,Object>> matching=normalized.isEmpty()?visible:visible.stream()
                .filter(card->matches(card,normalized))
                .sorted(Comparator.<Map<String,Object>>comparingInt(card -> isStatusQuestion(normalized) && "internship-status-guide".equals(card.get("id")) ? 0 : 1)
                        .thenComparing(Comparator.comparingInt((Map<String,Object> card) -> topicScore(card, normalized)).reversed()))
                .limit(8)
                .toList();
        List<String> warnings=new ArrayList<>();
        warnings.add("规则解释不代表业务已经执行；具体状态必须查询真实记录。");
        if (admin) warnings.add("管理员可了解四端功能规则；知识可见不增加任何业务查询或执行权限，不能据此断言具体用户的状态。");
        if ("student".equals(actorRole)) warnings.add("学生入口说明按本次可核验的菜单范围筛选；配置无法读取时仅保留基础入口，不能据此断言其他业务不存在。");
        if(!normalized.isEmpty()&&matching.isEmpty())warnings.add("当前身份可用的知识资料未匹配该内容，请核对权限或缩小问题，不能编造入口、规则或日期。");
        Map<String,Object> facts = new LinkedHashMap<>(Map.of("knowledgeVersion",version,"matched",!matching.isEmpty()));
        if ("student".equals(actorRole)) facts.put("visibleStudentMenus", studentMenus.stream().sorted().toList());
        return AgentReadTools.result("knowledge_lookup","系统功能与流程依据",actor,matching,matching.size(),facts,List.of(),warnings);
    }
    private boolean visibleFor(Map<String,Object> card, Actor actor, String actorRole, Set<String> studentMenus) {
        if (!((List<?>) card.get("roles")).contains(actorRole)) return false;
        Map<String,Object> policy = accessRules.getOrDefault(String.valueOf(card.get("id")), card);
        if (!((List<?>) policy.get("roles")).contains(actorRole)) return false;
        if ("teacher".equals(actorRole) && policy.get("teacherTypes") instanceof List<?> types
                && !types.contains(actor.teacherType())) return false;
        if (policy.get("permissionAny") instanceof Map<?,?> byRole
                && byRole.get(actorRole) instanceof List<?> codes && !codes.isEmpty()
                && codes.stream().noneMatch(actor.permissions()::contains)) return false;
        if (policy.get("permissionAll") instanceof Map<?,?> byRole
                && byRole.get(actorRole) instanceof List<?> codes
                && !actor.permissions().containsAll(codes)) return false;
        return !"student".equals(actorRole) || !(policy.get("studentMenus") instanceof List<?> paths)
                || paths.stream().anyMatch(studentMenus::contains);
    }

    private Set<String> studentMenuPaths() {
        Set<String> paths = new HashSet<>(Set.of("/student/home", "/student/profile"));
        if (configService == null) return paths;
        try {
            var config = configService.findByConfigKey("STUDENT_MENU_CONFIG");
            String configured = config == null ? null : config.getConfigValue();
            if (configured == null || configured.isBlank()) return paths;
            var menus = json.readTree(configured);
            if (!menus.isArray()) return paths;
            for (var menu : menus) {
                String path = menu.path("path").asText();
                if (path.startsWith("/student/") && (!menu.path("visible").isBoolean()
                        || menu.path("visible").asBoolean())) paths.add(path);
            }
        } catch (Exception e) {
            log.warn("学生知识菜单权限暂时无法读取，仅保留基础功能说明");
        }
        return paths;
    }
    private int topicScore(Map<String,Object> card, String topic) {
        String query = compact(topic);
        if (query.equals(compact(String.valueOf(card.get("id"))))) return 10_000;
        String title = compact(String.valueOf(card.getOrDefault("title", "")));
        int score = 4 * longestCommonSubstring(query, title);
        if (!query.isEmpty() && title.contains(query)) score += 200;
        for (Object keyword : (List<?>) card.getOrDefault("keywords", List.of())) {
            String term = compact(String.valueOf(keyword));
            if (term.length() >= 2 && query.contains(term)) score += 100 + 5 * term.length();
        }
        if ("system-business-overview".equals(card.get("id")) && isSystemOverviewQuestion(topic)) score += 1_000;
        return score;
    }

    static boolean isGeneralKnowledgeQuestion(String question) {
        String q = compact(question);
        return q.matches(".*(怎么|如何|哪里|在哪|入口|流程|步骤|规则|条件|材料清单|含义|区别|功能介绍|主要功能|有哪些功能).*")
                && !q.matches(".*(这个|这份|这条|该学生|该企业|该岗位|当前学生|当前企业|当前岗位|当前记录|选中|多少|几个|几条).*")
                && !q.matches(".*(我的|本人|本企业|我当前|我现在).*(状态|进度|结果|人数|数量|为什么|能否|是否|在哪里|在哪|还缺|还差|缺什么).*");
    }
    // Only standalone overview questions bypass current-record evidence. Mixed or
    // personal status questions continue through the normal scoped query path.
    static boolean isSystemOverviewQuestion(String question) {
        return compact(question).matches("(?:请|请问|帮我|绘制|画出|画一下|介绍|介绍一下|说明|说明一下|概括|概括一下|告诉我)*(?:该|本|这个|你们的|deepintern)?系统(?:的)?(?:核心业务逻辑|核心业务流程|业务流程|业务逻辑|核心流程|整体流程|主要业务|主要功能)(?:图)?(?:是什么|是怎样的|有哪些|如何运作|介绍一下|说明一下)?(?:以文本的形式|以文本形式|用文本表示|用文字表示)?");
    }

    static boolean isCalendarQuestion(String question) {
        String value = compact(question);
        return value.matches("(?:请|请问|告诉我|帮我查一下|帮我查询)*(?:我的|我们|学校|全校|统一|本次|今年)?(?:实习|实习日历|实习教学日历)(?:的)?(?:时间)?(?:什么时候|何时|几号|哪天|几月几号)(?:开始|结束)(?:呢|啊|呀)?")
                || value.matches("(?:请|请问|告诉我|帮我查一下|帮我查询)*(?:我的|我们|学校|全校|统一|本次|今年)?(?:实习|实习日历|实习教学日历|实习鉴定表|鉴定表)(?:的)?(?:开始|结束|起止|截止|提交截止)(?:时间|日期|日)?(?:是什么|是多少|是哪天|是什么时候|是几号|是几月几号|呢|啊|呀)?");
    }

    static boolean isAppraisalQuestion(String question) { String q=compact(question); return q.contains("鉴定表") && (q.contains("哪里")||q.contains("获取")||q.contains("下载")||q.contains("长什么样")||q.contains("怎么填")||q.contains("如何填写")||q.contains("填写")||q.contains("字段")); }

    static boolean isRecentActivityQuestion(String question) { String q=compact(question); return q.contains("最近动态")||q.contains("首页动态")||q.contains("动态卡片")||q.contains("最近进展")||q.contains("最近发生了什么"); }

    static boolean isStatusQuestion(String question) {
        String q=compact(question);
        return q.contains("实习状态") || q.matches(statusPattern != null ? statusPattern : ".*(暂无有效投递|已投递未录用|已录用待确认|已定单位待实习|实习进行中|实习已结束|实习已中断|已延期实习|未投递|无offer|待确认|已确定|实习中|已结束|已中断|延期).*")
                || q.matches(".*(我的|我当前|我现在|学生|实习).*(状态有哪些|什么状态|状态怎么|状态如何|状态变化|状态变更|状态流转).* ".trim());
    }
    private boolean matches(Map<String,Object> card,String topic){
        if ("internship-status-guide".equals(card.get("id")) && isStatusQuestion(topic)) return true;
        if ("appraisal-howto".equals(card.get("id")) && isAppraisalQuestion(topic)) return true;
        if ("recent-activity-guide".equals(card.get("id")) && isRecentActivityQuestion(topic)) return true;
        if ("system-business-overview".equals(card.get("id")) && isSystemOverviewQuestion(topic)) return true;
        String searchable=String.valueOf(card.getOrDefault("id",""))+" "+
                String.valueOf(card.getOrDefault("title",""))+" "+
                String.valueOf(card.getOrDefault("keywords",""))+" "+
                String.valueOf(card.getOrDefault("steps",""))+" "+
                String.valueOf(card.getOrDefault("rules",""))+" "+
                String.valueOf(card.getOrDefault("source",""));
        String searchableLower=searchable.toLowerCase(Locale.ROOT);
        if(searchableLower.contains(topic))return true;
        for(String token:topic.split("\\s+")){
            if(token.length()>=2&&searchableLower.contains(token))return true;
        }
        String compactTopic=compact(topic);
        for (Object keyword : (List<?>) card.getOrDefault("keywords", List.of())) {
            String term = compact(String.valueOf(keyword));
            if (term.length() >= 2 && compactTopic.contains(term)) return true;
        }
        if(compactTopic.length()>=3){
            int required=Math.min(4,Math.max(3,compactTopic.length()/2));
            if(longestCommonSubstring(compactTopic,compact(searchable))>=required)return true;
        }
        return false;
    }

    private static String compact(String value){
        StringBuilder result=new StringBuilder();
        if(value==null)return "";
        for(char ch:value.toLowerCase(Locale.ROOT).toCharArray()){
            if(Character.isLetterOrDigit(ch))result.append(ch);
        }
        return result.toString();
    }

    private static int longestCommonSubstring(String left,String right){
        if(left.isEmpty()||right.isEmpty())return 0;
        int[] previous=new int[right.length()+1];
        int best=0;
        for(int i=1;i<=left.length();i++){
            int[] current=new int[right.length()+1];
            for(int j=1;j<=right.length();j++){
                if(left.charAt(i-1)==right.charAt(j-1)){
                    current[j]=previous[j-1]+1;
                    if(current[j]>best)best=current[j];
                }
            }
            previous=current;
        }
        return best;
    }
    private String role(Actor a){return a.teacherType()!=null?"teacher":a.role().replace("ROLE_","").toLowerCase(Locale.ROOT);}
}
