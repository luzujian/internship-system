package com.gdmu.service;

import com.gdmu.config.DynamicChatClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 资源查询AI代理服务
 * 负责解析自然语言查询，提取搜索参数，并执行资源查询
 */
@Service
public class ResourceQueryAgentService {
    private static final Logger log = LoggerFactory.getLogger(ResourceQueryAgentService.class);
    
    private final ResourceService resourceService;
    private final DynamicChatClientFactory chatClientFactory;
    
    // 支持的文件类型映射
    private static final Map<String, String> FILE_TYPE_KEYWORDS = new HashMap<String, String>();
    
    static {
        // 文件类型关键词映射
        FILE_TYPE_KEYWORDS.put("文档", "pdf,doc,docx,txt,ppt,pptx");
        FILE_TYPE_KEYWORDS.put("pdf", "pdf");
        FILE_TYPE_KEYWORDS.put("word", "doc,docx");
        FILE_TYPE_KEYWORDS.put("视频", "mp4,avi,mov,wmv");
        FILE_TYPE_KEYWORDS.put("音频", "mp3,wav,aac,flac");
        FILE_TYPE_KEYWORDS.put("代码", "java,py,js,html,css,xml,json");
        FILE_TYPE_KEYWORDS.put("图片", "jpg,jpeg,png,gif,bmp");
        FILE_TYPE_KEYWORDS.put("压缩包", "zip,rar,tar,gz");
        FILE_TYPE_KEYWORDS.put("excel", "xls,xlsx,csv");
    }
    
    private static final String SYSTEM_PROMPT = "你是实习管理系统的资源查询助手，帮助用户搜索和查找学习资源。";
    
    @Autowired
    public ResourceQueryAgentService(
            ResourceService resourceService,
            DynamicChatClientFactory chatClientFactory) {
        this.resourceService = resourceService;
        this.chatClientFactory = chatClientFactory;
    }
    
    /**
     * 处理资源查询请求
     * @param query 用户查询文本
     * @param context 对话上下文
     * @param model 使用的模型名称
     * @return 查询结果
     */
    public Map<String, Object> processResourceQuery(String query, String context, String model) {
        log.info("开始处理资源查询: {}", query);
        
        try {
            // 构建系统提示词
            String systemPrompt = buildSystemPrompt();
            
            // 处理对话上下文
            String processedContext = processContext(context);
            
            // 构建用户提示词
            String userPrompt = String.format("查询内容: %s\n\n历史上下文:\n%s", query, processedContext);
            
            // 获取适当的ChatClient
            ChatClient client = getChatClientByModel(model);
            
            // 执行AI调用
            // 直接使用字符串作为系统提示，避免模板解析错误
            Prompt prompt = new Prompt(
                    List.of(
                            new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
                            new org.springframework.ai.chat.messages.UserMessage(userPrompt)
                    )
            );
            
            // 适配不同版本的Spring AI API
            String aiResponse = client.prompt(prompt).call().content();
            log.debug("AI解析结果: {}", aiResponse);
            
            // 解析AI响应，提取查询参数
            ResourceSearchParams searchParams = parseResourceSearchParams(aiResponse, query);
            
            // 执行实际搜索
            Object searchResult = executeSearch(searchParams);
            
            // 构建最终结果
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("originalQuery", query);
            result.put("extractedParams", searchParams.toMap());
            result.put("searchResult", searchResult);
            result.put("searchTime", System.currentTimeMillis());
            
            log.info("资源查询完成，提取参数: {}, 查询结果数量: {}", 
                     searchParams.toMap(), 
                     searchResult instanceof Map ? ((Map<?, ?>) searchResult).get("total") : "未知");
            
            return result;
        } catch (Exception e) {
            log.error("处理资源查询异常", e);
            // 降级策略：直接使用原始查询进行简单搜索
            Object fallbackResult = resourceService.aiSmartSearch(query, null, 1, 10);
            Map<String, Object> result = new HashMap<String, Object>();
            result.put("originalQuery", query);
            result.put("searchResult", fallbackResult);
            result.put("warning", "使用了降级搜索策略: " + e.getMessage());
            return result;
        }
    }
    
    /**
     * 构建系统提示词
     */
    private String buildSystemPrompt() {
        return "你是一个资源查询参数提取助手。请分析用户的自然语言查询，提取以下可能的参数：\n" +
               "1. query - 核心查询关键词\n" +
               "2. fileType - 资源类型（如文档、PDF、视频、代码等）\n" +
               "3. uploadTime - 上传时间范围（如最近一周、昨天等）\n" +
               "4. uploaderRole - 上传者角色（STUDENT、TEACHER、ADMIN）\n" +
               "5. uploaderId - 上传者ID（如果可以识别）\n\n" +
               "请以JSON格式返回提取的参数，没有提取到的参数可以省略。\n" +
               "示例：\n" +
               "用户查询: '查找最近一周上传的Java相关PDF文档'\n" +
               "输出: {\"query\": \"Java\", \"fileType\": \"pdf\", \"uploadTime\": \"recent_week\"}\n" +
               "\n用户查询: '有哪些教师上传的数据库优化资料'\n" +
               "输出: {\"query\": \"数据库优化\", \"uploaderRole\": \"TEACHER\"}\n" +
               "\n请确保输出格式严格为JSON，不包含任何其他文本！";
    }
    
    /**
     * 处理对话上下文
     */
    private String processContext(String context) {
        if (context == null || context.trim().isEmpty()) {
            return "无历史上下文";
        }
        
        // 保留最近的相关消息，避免上下文过长
        String[] messages = context.split("\\n");
        List<String> recentMessages = new ArrayList<>();
        
        for (int i = Math.max(0, messages.length - 15); i < messages.length; i++) {
            String message = messages[i].trim();
            if (!message.isEmpty() && (message.contains("资源") || message.contains("文件") || message.contains("查询"))) {
                recentMessages.add(message);
            }
        }
        
        return recentMessages.isEmpty() ? "无历史上下文" : String.join("\n", recentMessages);
    }
    
    /**
     * 根据模型名称获取对应的ChatClient
     */
    private ChatClient getChatClientByModel(String model) {
        String modelCode;
        
        if (model == null || model.trim().isEmpty()) {
            modelCode = "deepseek-chat";
        } else {
            switch (model.toLowerCase()) {
                case "deepseek":
                    modelCode = "deepseek-chat";
                    break;
                case "reasoner":
                case "deepseek-reasoner":
                    modelCode = "deepseek-reasoner";
                    break;
                default:
                    modelCode = "deepseek-chat";
            }
        }
        
        return chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
    }
    
    /**
     * 解析AI响应，提取资源搜索参数
     */
    private ResourceSearchParams parseResourceSearchParams(String aiResponse, String originalQuery) {
        ResourceSearchParams params = new ResourceSearchParams();
        
        try {
            // 尝试解析JSON格式
            if (aiResponse.startsWith("{") && aiResponse.contains("query")) {
                // 简单的JSON解析（避免引入额外依赖）
                extractFromJsonResponse(aiResponse, params);
            }
            
            // 如果AI返回的不是标准JSON，则使用正则表达式进行补充提取
            if (params.getQuery() == null || params.getQuery().trim().isEmpty()) {
                params.setQuery(extractCoreQuery(originalQuery));
            }
            
            // 补充提取文件类型
            if (params.getFileType() == null) {
                params.setFileType(extractFileType(originalQuery));
            }
            
            // 补充提取上传时间
            if (params.getUploadTime() == null) {
                params.setUploadTime(extractUploadTime(originalQuery));
            }
            
            // 补充提取上传者角色
            if (params.getUploaderRole() == null) {
                params.setUploaderRole(extractUploaderRole(originalQuery));
            }
            
        } catch (Exception e) {
            log.warn("解析AI响应异常，使用默认参数: {}", e.getMessage());
            // 如果解析失败，使用原始查询作为关键词
            params.setQuery(originalQuery);
        }
        
        return params;
    }
    
    /**
     * 从JSON响应中提取参数
     */
    private void extractFromJsonResponse(String response, ResourceSearchParams params) {
        // 提取query
        Pattern queryPattern = Pattern.compile("\\\"query\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher queryMatcher = queryPattern.matcher(response);
        if (queryMatcher.find()) {
            params.setQuery(queryMatcher.group(1).trim());
        }
        
        // 提取fileType
        Pattern fileTypePattern = Pattern.compile("\\\"fileType\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher fileTypeMatcher = fileTypePattern.matcher(response);
        if (fileTypeMatcher.find()) {
            params.setFileType(fileTypeMatcher.group(1).trim());
        }
        
        // 提取uploadTime
        Pattern timePattern = Pattern.compile("\\\"uploadTime\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher timeMatcher = timePattern.matcher(response);
        if (timeMatcher.find()) {
            params.setUploadTime(timeMatcher.group(1).trim());
        }
        
        // 提取uploaderRole
        Pattern rolePattern = Pattern.compile("\\\"uploaderRole\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"");
        Matcher roleMatcher = rolePattern.matcher(response);
        if (roleMatcher.find()) {
            params.setUploaderRole(roleMatcher.group(1).trim());
        }
        
        // 提取uploaderId
        Pattern idPattern = Pattern.compile("\\\"uploaderId\\\"\\s*:\\s*(\\d+)");
        Matcher idMatcher = idPattern.matcher(response);
        if (idMatcher.find()) {
            try {
                params.setUploaderId(Long.parseLong(idMatcher.group(1)));
            } catch (NumberFormatException e) {
                log.warn("解析uploaderId失败: {}", e.getMessage());
            }
        }
    }
    
    /**
     * 提取核心查询关键词
     */
    private String extractCoreQuery(String query) {
        // 移除常见的查询前缀和后缀
        String[] commonPrefixes = {"查找", "搜索", "有没有", "有哪些", "找", "我想找", "请问", "帮我找"};
        String[] commonSuffixes = {"资料", "文档", "文件", "资源", "吗", "？", "?", "。"};
        
        String coreQuery = query.toLowerCase();
        
        for (String prefix : commonPrefixes) {
            if (coreQuery.startsWith(prefix)) {
                coreQuery = coreQuery.substring(prefix.length()).trim();
                break;
            }
        }
        
        for (String suffix : commonSuffixes) {
            if (coreQuery.endsWith(suffix)) {
                coreQuery = coreQuery.substring(0, coreQuery.length() - suffix.length()).trim();
                break;
            }
        }
        
        // 移除时间和类型相关词汇
        String[] timeKeywords = {"最近", "昨天", "今天", "前天", "上周", "本周", "上月", "本月", "今年"};
        for (String keyword : timeKeywords) {
            coreQuery = coreQuery.replace(keyword, "").trim();
        }
        
        for (String typeKeyword : FILE_TYPE_KEYWORDS.keySet()) {
            coreQuery = coreQuery.replace(typeKeyword, "").trim();
        }
        
        return coreQuery.isEmpty() ? query : coreQuery;
    }
    
    /**
     * 提取文件类型
     */
    private String extractFileType(String query) {
        String lowerQuery = query.toLowerCase();
        
        for (Map.Entry<String, String> entry : FILE_TYPE_KEYWORDS.entrySet()) {
            if (lowerQuery.contains(entry.getKey())) {
                return entry.getValue().split(",")[0]; // 返回第一个匹配的文件类型
            }
        }
        
        // 直接匹配文件扩展名
        Pattern extPattern = Pattern.compile("\\.(pdf|doc|docx|txt|ppt|pptx|mp4|avi|mov|mp3|java|py|js|html|css|jpg|jpeg|png|zip|rar|xls|xlsx)");
        Matcher extMatcher = extPattern.matcher(query);
        if (extMatcher.find()) {
            return extMatcher.group(1);
        }
        
        return null;
    }
    
    /**
     * 提取上传时间范围
     */
    private String extractUploadTime(String query) {
        String lowerQuery = query.toLowerCase();
        
        if (lowerQuery.contains("今天") || lowerQuery.contains("24小时内")) {
            return "today";
        }
        if (lowerQuery.contains("昨天")) {
            return "yesterday";
        }
        if (lowerQuery.contains("近一周") || lowerQuery.contains("最近一周") || lowerQuery.contains("过去一周")) {
            return "recent_week";
        }
        if (lowerQuery.contains("近一个月") || lowerQuery.contains("最近一个月") || lowerQuery.contains("过去一个月")) {
            return "recent_month";
        }
        if (lowerQuery.contains("本学期") || lowerQuery.contains("这学期")) {
            return "this_semester";
        }
        if (lowerQuery.contains("去年")) {
            return "last_year";
        }
        
        return null;
    }
    
    /**
     * 提取上传者角色
     */
    private String extractUploaderRole(String query) {
        String lowerQuery = query.toLowerCase();
        
        if (lowerQuery.contains("学生")) {
            return "STUDENT";
        }
        if (lowerQuery.contains("教师") || lowerQuery.contains("老师")) {
            return "TEACHER";
        }
        if (lowerQuery.contains("管理员")) {
            return "ADMIN";
        }
        
        return null;
    }
    
    /**
     * 执行实际搜索
     */
    private Object executeSearch(ResourceSearchParams params) {
        // 调用现有资源服务进行搜索
        // 注意：当前ResourceService不直接支持上传时间和角色过滤，后续可以扩展
        Object result = resourceService.aiSmartSearch(
                params.getQuery(), 
                params.getFileType(), 
                1, // 目前固定第一页
                10  // 每页10条记录
        );
        
        // 对搜索结果进行后处理
        return postProcessResults(result, params);
    }
    
    /**
     * 后处理搜索结果
     */
    @SuppressWarnings("unchecked")
    private Object postProcessResults(Object rawResult, ResourceSearchParams params) {
        if (!(rawResult instanceof Map)) {
            return rawResult;
        }
        
        Map<String, Object> result = new HashMap<String, Object>((Map<String, Object>) rawResult);
        
        // 如果有上传时间过滤，进行内存过滤
        if (params.getUploadTime() != null && result.containsKey("list")) {
            List<?> resourceList = (List<?>) result.get("list");
            List<?> filteredList = filterByTime(resourceList, params.getUploadTime());
            result.put("list", filteredList);
            // 保留原始total值，添加新字段表示过滤后的结果数
            result.put("filteredCount", filteredList.size());
            result.put("timeFilterApplied", true);
        }
        
        // 添加查询摘要
        result.put("querySummary", generateQuerySummary(params));
        
        return result;
    }
    
    /**
     * 根据时间过滤资源
     */
    private List<?> filterByTime(List<?> resources, String timeRange) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoffTime = null;
        
        switch (timeRange) {
            case "today":
                cutoffTime = now.minusDays(1);
                break;
            case "yesterday":
                final LocalDateTime yesterdayCutoff = now.minusDays(2);
                final LocalDateTime yesterdayStart = now.minusDays(1).withHour(0).withMinute(0).withSecond(0);
                return resources.stream()
                        .filter(r -> r instanceof Map)
                        .map(r -> (Map<?, ?>) r)
                        .filter(r -> {
                            Object uploadTimeObj = r.get("uploadTime");
                            if (uploadTimeObj instanceof LocalDateTime) {
                                LocalDateTime uploadTime = (LocalDateTime) uploadTimeObj;
                                return uploadTime.isAfter(yesterdayCutoff) && uploadTime.isBefore(yesterdayStart);
                            }
                            return false;
                        })
                        .collect(Collectors.toList());
            case "recent_week":
                cutoffTime = now.minusWeeks(1);
                break;
            case "recent_month":
                cutoffTime = now.minusMonths(1);
                break;
            case "this_semester":
                // 简化处理：假设本学期是最近3个月
                cutoffTime = now.minusMonths(3);
                break;
            case "last_year":
                final LocalDateTime lastYearCutoff = now.minusYears(1);
                final LocalDateTime lastYearEnd = now.minusDays(365);
                return resources.stream()
                        .filter(r -> r instanceof Map)
                        .map(r -> (Map<?, ?>) r)
                        .filter(r -> {
                            Object uploadTimeObj = r.get("uploadTime");
                            if (uploadTimeObj instanceof LocalDateTime) {
                                LocalDateTime uploadTime = (LocalDateTime) uploadTimeObj;
                                return uploadTime.isAfter(lastYearCutoff) && uploadTime.isBefore(lastYearEnd);
                            }
                            return false;
                        })
                        .collect(Collectors.toList());
            default:
                return resources;
        }
        
        if (cutoffTime != null) {
            final LocalDateTime finalCutoffTime = cutoffTime;
            return resources.stream()
                    .filter(r -> r instanceof Map)
                    .map(r -> (Map<?, ?>) r)
                    .filter(r -> {
                        Object uploadTimeObj = r.get("uploadTime");
                        if (uploadTimeObj instanceof LocalDateTime) {
                            return ((LocalDateTime) uploadTimeObj).isAfter(finalCutoffTime);
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }
        
        return resources;
    }
    
    /**
     * 生成查询摘要
     */
    private String generateQuerySummary(ResourceSearchParams params) {
        StringBuilder summary = new StringBuilder("查询摘要：");
        
        if (params.getQuery() != null) {
            summary.append("关于'").append(params.getQuery()).append("'的资源");
        } else {
            summary.append("所有资源");
        }
        
        if (params.getFileType() != null) {
            summary.append("，文件类型：").append(params.getFileType());
        }
        
        if (params.getUploadTime() != null) {
            Map<String, String> timeMap = new HashMap<String, String>();
            timeMap.put("today", "今天");
            timeMap.put("yesterday", "昨天");
            timeMap.put("recent_week", "最近一周");
            timeMap.put("recent_month", "最近一个月");
            timeMap.put("this_semester", "本学期");
            timeMap.put("last_year", "去年");
            
            String timeDesc = timeMap.getOrDefault(params.getUploadTime(), params.getUploadTime());
            summary.append("，上传时间：").append(timeDesc);
        }
        
        if (params.getUploaderRole() != null) {
            Map<String, String> roleMap = new HashMap<String, String>();
            roleMap.put("STUDENT", "学生");
            roleMap.put("TEACHER", "教师");
            roleMap.put("ADMIN", "管理员");
            
            String roleDesc = roleMap.getOrDefault(params.getUploaderRole(), params.getUploaderRole());
            summary.append("，上传者：").append(roleDesc);
        }
        
        return summary.toString();
    }
    
    /**
     * 资源搜索参数封装类
     */
    private static class ResourceSearchParams {
        private String query;
        private String fileType;
        private String uploadTime;
        private String uploaderRole;
        private Long uploaderId;
        
        // Getters and setters
        public String getQuery() { return query; }
        public void setQuery(String query) { this.query = query; }
        
        public String getFileType() { return fileType; }
        public void setFileType(String fileType) { this.fileType = fileType; }
        
        public String getUploadTime() { return uploadTime; }
        public void setUploadTime(String uploadTime) { this.uploadTime = uploadTime; }
        
        public String getUploaderRole() { return uploaderRole; }
        public void setUploaderRole(String uploaderRole) { this.uploaderRole = uploaderRole; }
        
        public Long getUploaderId() { return uploaderId; }
        public void setUploaderId(Long uploaderId) { this.uploaderId = uploaderId; }
        
        // 转换为Map
        public Map<String, Object> toMap() {
            Map<String, Object> map = new HashMap<String, Object>();
            if (query != null) map.put("query", query);
            if (fileType != null) map.put("fileType", fileType);
            if (uploadTime != null) map.put("uploadTime", uploadTime);
            if (uploaderRole != null) map.put("uploaderRole", uploaderRole);
            if (uploaderId != null) map.put("uploaderId", uploaderId);
            return map;
        }
    }
}