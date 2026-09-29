package com.internship.agent.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.mapper.AgentKnowledgeMapper;
import com.internship.service.SysDictService;
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
    private volatile String version;
    private volatile List<Map<String,Object>> cards;

    @Autowired(required = false)
    private AgentKnowledgeMapper dbMapper;
    @Autowired(required = false)
    private SysDictService dictService;
    private static volatile String statusPattern;

    public AgentKnowledge(ObjectMapper json) throws IOException {
        this.json = json;
        loadFromClasspath();
    }

    @PostConstruct
    public void load() {
        if (loadFromDb()) { initStatusPattern(); return; }
        initStatusPattern();
    }

    private void initStatusPattern() {
        try {
            String names = "未投递|无offer|待确认|已确定|实习中|已结束|已中断|延期";
            if (dictService != null) {
                String configured = dictService.codesByType("internship_status").stream()
                        .map(code -> dictService.label("internship_status", code))
                        .collect(Collectors.joining("|"));
                if (!configured.isBlank()) names = configured;
            }
            statusPattern = ".*(" + names + ").*";
        } catch (Exception e) {
            statusPattern = ".*(未投递|无offer|待确认|已确定|实习中|已结束|已中断|延期).*";
        }
    }

    private boolean loadFromDb() {
        if (dbMapper == null) { log.info("AgentKnowledge dbMapper 为空，跳过 DB 加载"); return false; }
        try {
            List<Map<String, Object>> rows = dbMapper.findAllCards();
            if (rows == null || rows.isEmpty()) { log.info("AgentKnowledge DB 返回空，回退 classpath"); return false; }
            List<Map<String, Object>> parsed = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Map<String, Object> card = new LinkedHashMap<>(row);
                card.put("roles", parseJsonList(card.get("roles")));
                card.put("keywords", parseJsonList(card.get("keywords")));
                card.put("states", parseJsonList(card.get("states")));
                parsed.add(card);
            }
            this.cards = List.copyOf(parsed);
            this.version = computeVersion(json.writeValueAsBytes(parsed));
            log.info("AgentKnowledge 从 DB 加载：{} 张卡片，版本 {}", parsed.size(), version);
            return true;
        } catch (Exception e) {
            log.warn("AgentKnowledge DB 加载失败，回退 classpath：{}", e.getMessage());
            return false;
        }
    }

    private void loadFromClasspath() {
        try {
            byte[] raw;
            try (var stream = new ClassPathResource("agent/knowledge.json").getInputStream()) {
                raw = stream.readAllBytes();
                this.cards = json.readValue(raw, new TypeReference<>() {});
            }
            this.version = computeVersion(raw);
            log.info("AgentKnowledge 从 classpath 加载：{} 张卡片，版本 {}", cards.size(), version);
        } catch (IOException e) {
            throw new RuntimeException("AgentKnowledge 加载失败", e);
        }
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
    public String getVersion() { return version; }
    public ToolResult lookup(Actor actor,String topic) {
        List<Map<String,Object>> visible=cards.stream().filter(c->((List<?>)c.get("roles")).contains(role(actor))).toList();
        String normalized=topic==null?"":topic.trim().toLowerCase(Locale.ROOT);
        List<Map<String,Object>> matching=normalized.isEmpty()?visible:visible.stream()
                .filter(card->matches(card,normalized))
                .sorted(Comparator.comparingInt(card -> isStatusQuestion(normalized) && "internship-status-guide".equals(card.get("id")) ? 0 : 1))
                .limit(8)
                .toList();
        List<String> warnings=new ArrayList<>();
        warnings.add("规则解释不代表业务已经执行；具体状态必须查询真实记录。");
        if(!normalized.isEmpty()&&matching.isEmpty())warnings.add("系统知识资料未登记该内容，不能据现有卡片编造入口、规则或日期。");
        return AgentReadTools.result("knowledge_lookup","系统功能与流程依据",actor,matching,matching.size(),Map.of("knowledgeVersion",version,"matched",!matching.isEmpty()),List.of(),warnings);
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
        return q.contains("实习状态") || q.matches(statusPattern != null ? statusPattern : ".*(未投递|无offer|待确认|已确定|实习中|已结束|已中断|延期).*")
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
