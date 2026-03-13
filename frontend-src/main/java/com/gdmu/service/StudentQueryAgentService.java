package com.gdmu.service;

import com.gdmu.config.DynamicChatClientFactory;
import com.gdmu.exception.BusinessException;
import com.gdmu.vo.StudentUserVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class StudentQueryAgentService {

    private static final Logger log = LoggerFactory.getLogger(StudentQueryAgentService.class);

    @Autowired
    private StudentUserService studentUserService;
    
    @Autowired
    private DynamicChatClientFactory chatClientFactory;
    
    private static final String SYSTEM_PROMPT = "你是实习管理系统的学生查询助手，帮助学生查询个人信息、实习信息等。";
    
    // 根据模型名称获取对应的ChatClient
    private ChatClient getChatClientByModel(String model) {
        String modelCode = (model != null && model.equals("deepseek-reasoner")) ? "deepseek-reasoner" : "deepseek-chat";
        return chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
    }

    /**
     * 处理学生用户查询的自然语言请求
     */
    public Map<String, Object> processStudentQuery(String userQuery, List<Map<String, Object>> context) {
        return processStudentQuery(userQuery, context, null);
    }
    
    /**
     * 处理学生用户查询或删除的自然语言请求，支持选择不同的模型
     */
    public Map<String, Object> processStudentQuery(String userQuery, List<Map<String, Object>> context, String model) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 首先检查用户是否有明确的删除意图
            boolean userHasDeleteIntent = isDeleteIntent(userQuery);
            
            // 根据用户意图构建合适的系统提示词
            String systemPrompt = buildSystemPrompt(userHasDeleteIntent);

            // 构建消息列表
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(systemPrompt));
            
            // 添加对话上下文（如果有）
            if (context != null && !context.isEmpty()) {
                // 设置要保留的历史消息最大条数为15条
                int maxHistorySize = 15;
                
                // 计算从哪条消息开始处理（保留最近的maxHistorySize条）
                int startIndex = Math.max(0, context.size() - maxHistorySize);
                
                // 只处理从startIndex开始的消息
                for (int i = startIndex; i < context.size(); i++) {
                    Map<String, Object> message = context.get(i);
                    String role = (String) message.get("role");
                    String content = (String) message.get("content");
                    if (role != null && content != null) {
                        // 只添加相关的学生查询对话，避免无关内容影响
                        if (content.toLowerCase().contains("学生") || 
                            content.toLowerCase().contains("学号") || 
                            content.toLowerCase().contains("姓名") ||
                            content.toLowerCase().contains("专业") ||
                            content.toLowerCase().contains("学院") ||
                            content.toLowerCase().contains("班级") ||
                            content.toLowerCase().contains("student") ||
                            content.toLowerCase().contains("query")) {
                            if ("user".equals(role)) {
                                messages.add(new UserMessage(content));
                            } else if ("assistant".equals(role)) {
                                messages.add(new SystemMessage(content));
                            }
                        }
                    }
                }
            }
            
            // 添加当前用户查询
            messages.add(new UserMessage(userQuery));

            // 根据模型参数选择对应的ChatClient
            ChatClient selectedClient = getChatClientByModel(model);
            
            // 调用AI分析查询意图
            String aiResponse = selectedClient.prompt()
                    .messages(messages)
                    .call()
                    .content();
                    
            log.info("AI响应原始内容: {}", aiResponse);

            // 解析AI返回的参数
            Map<String, Object> params = parseQueryParams(aiResponse);
            
            // 判断是查询还是删除操作
            boolean isDeleteAction = "delete".equals(params.get("action"));
            
            // 优先执行删除操作：如果用户明确要求删除，或者AI返回了删除action
            if (userHasDeleteIntent || isDeleteAction) {
                Map<String, Object> deleteResult;
                
                // 只有当AI返回的删除参数无效时，才需要从用户查询中重建参数
                if (isDeleteAction && hasValidDeleteStructure(params)) {
                    // AI返回了有效的删除参数，直接使用
                    log.info("使用AI返回的有效删除参数: {}", params);
                    deleteResult = handleDeleteOperation(params);
                } else {
                    // AI没有返回有效的删除参数，从用户查询中重建
                    Map<String, Object> deleteParams = createDeleteParamsFromUserQuery(userQuery);
                    log.info("从用户查询重构删除参数: {}", deleteParams);
                    deleteResult = handleDeleteOperation(deleteParams);
                }
                
                // 检查删除结果，如果成功删除但返回了"未找到"消息，修正消息内容
                if (deleteResult.get("success") != null && (Boolean) deleteResult.get("success") && 
                    deleteResult.get("message") != null && 
                    deleteResult.get("message").toString().contains("未找到符合条件的学生用户")) {
                    
                    // 检查是否实际删除了学生
                    Integer deletedCount = (Integer) deleteResult.get("deletedCount");
                    if (deletedCount != null && deletedCount > 0) {
                        // 实际有删除操作，修正消息
                        deleteResult.put("message", "成功删除" + deletedCount + "名学生用户");
                    }
                }
                
                result.putAll(deleteResult);
            } else {
                // 检查是否是错误响应
                if (Boolean.TRUE.equals(params.get("error"))) {
                    result.put("success", false);
                    result.put("message", params.get("errorMessage") != null ? 
                               params.get("errorMessage").toString() : "无法处理的查询请求");
                } else {
                    // 执行数据库查询
                    List<StudentUserVO> students = studentUserService.queryStudentsByParams(params);
                    
                    result.put("success", true);
                    result.put("students", students);
                    result.put("queryParams", params);
                    result.put("total", students.size());
                    
                    // 添加对空结果的处理，提供友好的提示消息
                    if (students.isEmpty()) {
                        // 检查是否包含active=false的条件（查询被禁用学生）
                        if (Boolean.FALSE.equals(params.get("active"))) {
                            result.put("message", "当前系统中没有被禁用的学生用户");
                        } else {
                            result.put("message", "未找到符合条件的学生用户");
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "查询处理失败：" + e.getMessage());
        }
        
        return result;
    }
    
    /**
     * 根据用户意图构建系统提示词
     */
    private String buildSystemPrompt(boolean isDeleteIntent) {
        String basePrompt = """
        你是实习管理系统的学生管理AI助手，专门帮助管理员查询和管理学生用户信息。
        
        你的主要功能：
        1. 理解管理员对学生用户的查询需求
        2. 将自然语言查询转换为系统可执行的查询条件
        3. 返回结构化的学生信息
        4. 处理学生用户的删除请求，支持单个删除和批量删除
        
        可查询的学生信息包括：
        - 学生基本信息：学号、姓名、班级、专业、学院
        - 学生状态：是否激活、创建时间
        - 学生实习信息：实习状态、实习企业、实习岗位等
        
        【重要提示】判断学生是否被禁用时，你只能根据系统数据库中的status字段值来判断，当status=0时表示已禁用。
        你绝对不能自行推断或编造任何禁用原因，也不能根据其他因素判断学生是否被禁用。
        只能呈现数据库中实际存在的信息，不添加任何主观判断或猜测。
        """;
        
        String formatPrompt = """
        
        请严格按照以下格式输出：
        1. 如果是查询请求，输出格式：{"字段名1":"值1","字段名2":"值2",...}
        2. 如果是删除请求，输出格式：{"action":"delete","condition":{"字段名":"值"}} 或 {"action":"delete","id":123} 或 {"action":"delete","ids":[1,2,3]}
        3. 如果查询内容与学生用户信息无关（如资料查询、课程查询等），请返回错误格式：{"status":"error","message":"错误消息内容"}
        """;
        
        // 如果检测到用户有删除意图，强调必须返回删除action
        if (isDeleteIntent) {
            String deleteEmphasis = """
            
            【重要提示】用户的请求包含删除意图，请务必生成包含action字段为"delete"的JSON响应。
            例如，对于"删除学号为2023001的学生"，你应该生成 {"action":"delete","condition":{"studentId":"2023001"}}。
            对于"删除张三"，你应该生成 {"action":"delete","condition":{"name":"张三"}}。
            对于"删除学号为2023001的张三学生"，你应该生成 {"action":"delete","condition":{"studentId":"2023001","name":"张三"}}。
            
            请从用户请求中准确提取所有可能的删除条件（学号、姓名等）并放入condition对象中。
            """;
            return basePrompt + deleteEmphasis + formatPrompt;
        }
        
        return basePrompt + formatPrompt;
    }
    
    /**
     * 检查删除操作的参数结构是否有效
     */
    private boolean hasValidDeleteStructure(Map<String, Object> params) {
        // 检查是否有有效的删除结构
        return params != null && 
               "delete".equals(params.get("action")) &&
               (params.containsKey("condition") || 
                params.containsKey("id") || 
                params.containsKey("ids"));
    }
    
    /**
     * 从用户查询直接创建删除参数
     */
    private Map<String, Object> createDeleteParamsFromUserQuery(String userQuery) {
        Map<String, Object> deleteParams = new HashMap<>();
        deleteParams.put("action", "delete");
        
        Map<String, Object> condition = new HashMap<>();
        
        // 尝试提取学号（假设是6-12位数字）
        java.util.regex.Pattern idPattern = java.util.regex.Pattern.compile("(\\d{6,12})");
        java.util.regex.Matcher idMatcher = idPattern.matcher(userQuery);
        if (idMatcher.find()) {
            condition.put("studentId", idMatcher.group(1));
        }
        
        // 尝试提取姓名（假设是中文姓名，2-4个汉字）
        java.util.regex.Pattern namePattern = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]{2,4})");
        java.util.regex.Matcher nameMatcher = namePattern.matcher(userQuery);
        List<String> possibleNames = new ArrayList<>();
        while (nameMatcher.find()) {
            String name = nameMatcher.group(1);
            // 排除常见的非姓名词汇
            if (!name.equals("学生") && !name.equals("删除") && !name.equals("学号")) {
                possibleNames.add(name);
            }
        }
        // 如果找到可能的姓名，使用第一个
        if (!possibleNames.isEmpty()) {
            condition.put("name", possibleNames.get(0));
        }
        
        // 尝试提取专业信息
        if (userQuery.contains("计算机")) {
            condition.put("major", "计算机");
        } else if (userQuery.contains("软件工程")) {
            condition.put("major", "软件工程");
        } else if (userQuery.contains("网络工程")) {
            condition.put("major", "网络工程");
        }
        
        // 尝试提取年级信息（年份）
        java.util.regex.Pattern gradePattern = java.util.regex.Pattern.compile("(20\\d{2})");
        java.util.regex.Matcher gradeMatcher = gradePattern.matcher(userQuery);
        if (gradeMatcher.find()) {
            try {
                condition.put("grade", Integer.parseInt(gradeMatcher.group(1)));
            } catch (NumberFormatException e) {
                log.warn("年级转换失败: {}", gradeMatcher.group(1));
            }
        }
        
        deleteParams.put("condition", condition);
        return deleteParams;
    }

    /**
     * 解析AI返回的查询参数
     */
    /**
     * 检查查询文本是否包含删除相关关键词
     */
    private boolean containsDeleteKeywords(String query) {
        String lowerQuery = query.toLowerCase();
        String[] deleteKeywords = {
            "删除", "remove", "delete", "删掉", "清除", "清除掉", "移除",
            "erase", "clear", "purge", "eliminate"
        };
        for (String keyword : deleteKeywords) {
            if (lowerQuery.contains(keyword)) {
                // 排除一些可能不是真正删除意图的情况
                if (!lowerQuery.contains("是否删除") && 
                    !lowerQuery.contains("能否删除") && 
                    !lowerQuery.contains("如何删除") &&
                    !lowerQuery.contains("可以删除吗") &&
                    !lowerQuery.contains("delete?")) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 更强健的删除意图检测
     */
    private boolean isDeleteIntent(String userQuery) {
        // 更强健的删除意图检测
        String lowerQuery = userQuery.toLowerCase();
        // 包含删除关键词
        boolean containsDelete = containsDeleteKeywords(lowerQuery);
        
        // 特殊处理：即使包含一些查询类词汇，但主要意图是删除的情况
        // 例如"删除学号为xxx的学生信息"中包含"信息"但实际是删除操作
        if (containsDelete) {
            // 如果有明确的删除目标（学号、姓名等），则认为是删除意图
            if (containsTargetIdentifier(lowerQuery)) {
                return true;
            }
            
            // 排除纯查询意图
            boolean pureQueryIntent = (lowerQuery.contains("查询") && !lowerQuery.contains("然后删除")) ||
                                     (lowerQuery.contains("查找") && !lowerQuery.contains("然后删除")) ||
                                     (lowerQuery.contains("search") && !lowerQuery.contains("and delete"));
            return !pureQueryIntent;
        }
        
        return false;
    }
    
    /**
     * 检查是否包含明确的目标标识符
     */
    private boolean containsTargetIdentifier(String query) {
        // 检查是否包含明确的目标标识符，如学号、姓名等
        String[] identifiers = {
            "学号", "学生号", "id", "studentid", "student id",
            "姓名", "name", "同学", "学生"
        };
        for (String identifier : identifiers) {
            if (query.contains(identifier)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 处理删除学生用户的操作
     */
    private Map<String, Object> handleDeleteOperation(Map<String, Object> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.info("开始处理删除操作，参数: {}", params);
            
            // 检查是否有单个ID删除
            if (params.containsKey("id")) {
                Long id = Long.valueOf(params.get("id").toString());
                log.info("执行单个ID删除: {}", id);
                try {
                    int deletedCount = studentUserService.delete(id);
                    result.put("success", deletedCount > 0);
                    result.put("message", deletedCount > 0 ? "成功删除1名学生用户" : "未找到指定ID的学生用户");
                    result.put("deletedCount", deletedCount);
                } catch (BusinessException e) {
                    result.put("success", false);
                    result.put("message", "删除失败: " + e.getMessage());
                    result.put("deletedCount", 0);
                    log.warn("单个ID删除失败: {}, 错误信息: {}", id, e.getMessage());
                }
                return result;
            }
            
            // 检查是否有批量ID删除
            if (params.containsKey("ids")) {
                List<Long> ids = null;
                Object idsObj = params.get("ids");
                if (idsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> rawIds = (List<Object>) idsObj;
                    ids = rawIds.stream()
                            .map(id -> Long.valueOf(id.toString()))
                            .collect(java.util.stream.Collectors.toList());
                }
                
                if (ids != null && !ids.isEmpty()) {
                    log.info("执行批量ID删除，数量: {}", ids.size());
                    try {
                        int deletedCount = studentUserService.batchDeleteStudentUsers(ids);
                        // 即使部分删除失败，只要有成功删除的记录，就标记为成功
                        result.put("success", deletedCount > 0);
                        result.put("message", deletedCount > 0 ? 
                                "成功删除" + deletedCount + "名学生用户" : 
                                "未找到符合条件的学生用户");
                        result.put("deletedCount", deletedCount);
                        // 如果实际删除数量小于请求数量，可能有部分删除失败
                        if (deletedCount > 0 && deletedCount < ids.size()) {
                            result.put("partialSuccess", true);
                            result.put("message", "部分学生删除成功，共删除" + deletedCount + "名学生用户");
                        }
                    } catch (BusinessException e) {
                        result.put("success", false);
                        // 直接使用BusinessException中提供的详细错误信息
                        result.put("message", e.getMessage());
                        result.put("deletedCount", 0);
                        log.warn("批量ID删除失败: {}, 错误信息: {}", ids, e.getMessage());
                    }
                } else {
                    result.put("success", false);
                    result.put("message", "未提供有效的学生ID列表");
                    result.put("deletedCount", 0);
                }
                return result;
            }
            
            // 处理基于条件的删除
            if (params.containsKey("condition")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> condition = (Map<String, Object>) params.get("condition");
                log.info("执行条件删除，条件: {}", condition);
                
                // 构建查询参数以查找要删除的学生
                Map<String, Object> queryParams = new HashMap<>();
                
                // 处理常见的字段映射
                if (condition.containsKey("studentId")) {
                    queryParams.put("studentId", condition.get("studentId"));
                }
                if (condition.containsKey("name")) {
                    queryParams.put("name", condition.get("name"));
                }
                if (condition.containsKey("major")) {
                    queryParams.put("major", condition.get("major"));
                }
                if (condition.containsKey("department")) {
                    queryParams.put("department", condition.get("department")); // 注意字段映射
                }
                if (condition.containsKey("className")) {
                    queryParams.put("className", condition.get("className")); // 注意字段映射
                }
                if (condition.containsKey("grade")) {
                    queryParams.put("grade", condition.get("grade"));
                }
                
                // 如果没有明确映射的字段，使用原始条件（保留原有行为的兼容性）
                if (queryParams.isEmpty()) {
                    queryParams.putAll(condition);
                }
                
                // 执行查询获取匹配的学生
                log.info("使用查询参数查找学生: {}", queryParams);
                List<StudentUserVO> students = studentUserService.queryStudentsByParams(queryParams);
                
                if (!students.isEmpty()) {
                    log.info("找到匹配的学生数量: {}", students.size());
                    List<Long> idsToDelete = students.stream()
                            .map(StudentUserVO::getId)
                            .collect(java.util.stream.Collectors.toList());
                    
                    try {
                        // 执行批量删除
                        int deletedCount = studentUserService.batchDeleteStudentUsers(idsToDelete);
                        // 即使部分删除失败，只要有成功删除的记录，就标记为成功
                        result.put("success", deletedCount > 0);
                        result.put("message", deletedCount > 0 ? 
                                "成功删除" + deletedCount + "名学生用户" : 
                                "未找到符合条件的学生用户");
                        result.put("deletedCount", deletedCount);
                        // 如果实际删除数量小于匹配数量，可能有部分删除失败
                        if (deletedCount > 0 && deletedCount < students.size()) {
                            result.put("partialSuccess", true);
                            result.put("message", "部分学生删除成功，共删除" + deletedCount + "名学生用户");
                        }
                        // 添加已删除学生的信息以便确认
                        result.put("deletedStudents", students.stream()
                                .limit(deletedCount) // 只显示成功删除的学生
                                .map(s -> s.getStudentId() + ": " + s.getName())
                                .collect(java.util.stream.Collectors.toList()));
                    } catch (BusinessException e) {
                        result.put("success", false);
                        // 直接使用BusinessException中提供的详细错误信息
                        result.put("message", e.getMessage());
                        result.put("deletedCount", 0);
                        log.warn("条件删除失败: {}, 错误信息: {}", condition, e.getMessage());
                    }
                } else {
                    result.put("success", true);
                    result.put("message", "未找到符合条件的学生用户");
                    result.put("deletedCount", 0);
                }
                return result;
            }
            
            // 如果没有有效的删除参数
            throw new IllegalArgumentException("删除操作缺少必要的删除参数");
            
        } catch (IllegalArgumentException e) {
            // 处理参数错误，包括NumberFormatException（IllegalArgumentException的子类）
            log.error("删除学生操作参数错误: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "参数错误: " + e.getMessage());
            result.put("deletedCount", 0);
            return result;
        } catch (Exception e) {
            // 处理其他未知错误
            log.error("删除学生操作失败: {}", e.getMessage(), e);
            result.put("success", false);
            // 检查是否是我们自定义的错误信息
            String errorMessage = e.getMessage();
            if (errorMessage == null || errorMessage.isEmpty() || errorMessage.contains("null")) {
                errorMessage = "删除操作失败，请检查学生是否与其他数据存在关联关系";
            }
            result.put("message", errorMessage);
            result.put("deletedCount", 0);
            return result;
        }
    }
    
    private Map<String, Object> parseQueryParams(String aiResponse) {
        Map<String, Object> params = new HashMap<>();
        
        try {
            log.debug("开始解析AI响应: {}", aiResponse);
            
            // 首先尝试解析JSON格式响应
            if (aiResponse.trim().startsWith("{") && aiResponse.trim().endsWith("}")) {
                try {
                    // 检查是否是错误响应
                    if (aiResponse.contains("\"status\":\"error\"")) {
                        log.debug("检测到错误响应，不执行查询操作");
                        params.put("error", true);
                        // 提取错误消息
                        int messageStart = aiResponse.indexOf("\"message\":\"");
                        if (messageStart != -1) {
                            messageStart += 11; // "message":" 的长度
                            int messageEnd = aiResponse.indexOf("\"", messageStart);
                            if (messageEnd != -1) {
                                params.put("errorMessage", aiResponse.substring(messageStart, messageEnd));
                            }
                        }
                        return params;
                    }
                    
                    // 使用简单的正则表达式提取JSON字段值
                    extractJSONFields(aiResponse, params);
                    if (!params.isEmpty()) {
                        log.debug("从JSON响应中提取的参数: {}", params);
                        return params;
                    }
                } catch (Exception e) {
                    log.warn("JSON解析失败，尝试关键词提取: {}", e.getMessage());
                }
            }
            
            // 关键词提取方法
            String lowerCaseResponse = aiResponse.toLowerCase();
            
            // 提取学号
            extractStudentId(aiResponse, lowerCaseResponse, params);
            
            // 提取姓名
            extractName(aiResponse, lowerCaseResponse, params);
            
            // 提取专业
            extractMajor(aiResponse, lowerCaseResponse, params);
            
            // 提取学院
            extractDepartment(aiResponse, lowerCaseResponse, params);
            
            // 提取班级
            extractClass(aiResponse, lowerCaseResponse, params);
            
            // 提取年级（单独处理，可能在班级信息中）
            extractGrade(aiResponse, lowerCaseResponse, params);
            
            // 提取激活状态
            extractActiveStatus(aiResponse, lowerCaseResponse, params);
            
            log.debug("最终提取的查询参数: {}", params);
        } catch (Exception e) {
            log.error("解析AI响应失败: {}", e.getMessage(), e);
        }
        
        return params;
    }
    
    /**
     * 从JSON格式响应中提取字段值
     */
    private void extractJSONFields(String aiResponse, Map<String, Object> params) {
        // 首先检查是否是删除操作的JSON结构
        if (aiResponse.contains("\"action\":\"delete\"")) {
            // 提取action字段
            String actionMatch = extractFieldValue(aiResponse, "action", "\\\"([^\\\"]*)\\\"");
            if (actionMatch != null) {
                params.put("action", actionMatch);
                log.debug("成功提取action字段: {}", actionMatch);
            }
            
            // 提取condition字段（包含所有删除条件）
            // 使用更精确的正则表达式提取condition对象
            int conditionStart = aiResponse.indexOf("\"condition\":{");
            if (conditionStart != -1) {
                // 找到condition开始位置并跳过"condition":
                conditionStart += "\"condition\":".length();
                
                // 解析condition对象
                StringBuilder conditionBuilder = new StringBuilder();
                int braceCount = 0;
                boolean inString = false;
                char[] chars = aiResponse.substring(conditionStart).toCharArray();
                
                for (char c : chars) {
                    conditionBuilder.append(c);
                    if (c == '"') {
                        inString = !inString;
                    } else if (c == '{' && !inString) {
                        braceCount++;
                    } else if (c == '}' && !inString) {
                        braceCount--;
                        if (braceCount < 0) {
                            // 找到了condition对象的结束位置
                            break;
                        }
                    }
                }
                
                String conditionStr = conditionBuilder.toString();
                // 创建condition对象并填充参数
                Map<String, Object> condition = new HashMap<>();
                
                // 提取condition中的各字段
                String condStudentId = extractFieldValue(conditionStr, "studentId|学号", "\\\"(\\d+)\\\"");
                if (condStudentId != null) {
                    condition.put("studentId", condStudentId);
                    log.debug("成功提取condition.studentId: {}", condStudentId);
                }
                
                String condName = extractFieldValue(conditionStr, "name|姓名", "\\\"([\\u4e00-\\u9fa5a-zA-Z]*)\\\"");
                if (condName != null) {
                    condition.put("name", condName);
                    log.debug("成功提取condition.name: {}", condName);
                }
                
                String condMajor = extractFieldValue(conditionStr, "major|专业", "\\\"([\\u4e00-\\u9fa5a-zA-Z]*)\\\"");
                if (condMajor != null) {
                    condition.put("major", condMajor);
                }
                
                String condGrade = extractFieldValue(conditionStr, "grade|年级", "\\\"(\\d+)\\\"");
                if (condGrade != null) {
                    try {
                        condition.put("grade", Integer.parseInt(condGrade));
                    } catch (NumberFormatException e) {
                        log.warn("grade转换失败: {}", condGrade);
                    }
                }
                
                // 将提取的condition对象放入params
                if (!condition.isEmpty()) {
                    params.put("condition", condition);
                    log.debug("成功提取condition对象: {}", condition);
                }
            }
            
            // 提取单个ID删除
            String idMatch = extractFieldValue(aiResponse, "id", "([0-9]+)");
            if (idMatch != null) {
                try {
                    params.put("id", Long.parseLong(idMatch));
                    log.debug("成功提取id字段: {}", idMatch);
                } catch (NumberFormatException e) {
                    log.warn("id转换失败: {}", idMatch);
                }
            }
            
            // 提取批量ID删除
            if (aiResponse.contains("\"ids\":[")) {
                // 这里可以添加更复杂的ids数组解析逻辑
                log.debug("检测到ids数组，暂不解析具体值");
            }
        } else {
            // 普通查询请求，使用原有的字段提取逻辑
            // 提取学生号
            extractFieldFromJSON(aiResponse, "studentId|学号", params, "studentId", "\\d+");
            
            // 提取姓名
            extractFieldFromJSON(aiResponse, "name|姓名", params, "name", "[\\u4e00-\\u9fa5a-zA-Z]+");
            
            // 提取专业
            extractFieldFromJSON(aiResponse, "major|专业", params, "major", "[\\u4e00-\\u9fa5a-zA-Z]+");
            
            // 提取学院
            extractFieldFromJSON(aiResponse, "department|学院", params, "department", "[\\u4e00-\\u9fa5a-zA-Z]+");
            
            // 提取班级
            extractFieldFromJSON(aiResponse, "class|班级", params, "className", "[\u4e00-\u9fa5a-zA-Z0-9]+");
            
            // 提取年级
            String gradeMatch = extractFieldValue(aiResponse, "grade|年级", "\\\"(\\d+)\\\"");
            if (gradeMatch != null) {
                try {
                    int grade = Integer.parseInt(gradeMatch);
                    params.put("grade", grade);
                } catch (NumberFormatException e) {
                    log.warn("JSON中年级转换失败: {}", gradeMatch);
                }
            }
            
            // 提取状态字段（支持status和active两种字段名）
            String statusMatch = extractFieldValue(aiResponse, "status|状态", "\\\"([^\\\"]*)\\\"");
            if (statusMatch != null) {
                // 支持多种状态表示方式
                if ("enabled".equalsIgnoreCase(statusMatch) || "active".equalsIgnoreCase(statusMatch) || 
                    "启用".equals(statusMatch) || "已启用".equals(statusMatch) || "1".equals(statusMatch)) {
                    params.put("active", true);
                } else if ("disabled".equalsIgnoreCase(statusMatch) || "inactive".equalsIgnoreCase(statusMatch) || 
                         "禁用".equals(statusMatch) || "已禁用".equals(statusMatch) || "0".equals(statusMatch)) {
                    params.put("active", false);
                }
            }
            
            // 提取激活状态（兼容旧版本）
            String activeMatch = extractFieldValue(aiResponse, "active|激活状态", ".*?([true|false|1|0])");
            if (activeMatch != null) {
                if ("true".equals(activeMatch) || "1".equals(activeMatch)) {
                    params.put("active", true);
                } else if ("false".equals(activeMatch) || "0".equals(activeMatch)) {
                    params.put("active", false);
                }
            }
        }
    }
    
    /**
     * 从JSON格式中提取特定字段值
     */
    private void extractFieldFromJSON(String aiResponse, String fieldNamePattern, 
                                     Map<String, Object> params, String paramKey, 
                                     String valuePattern) {
        try {
            // 简化正则表达式，使用正确的转义
            String value = extractFieldValue(aiResponse, fieldNamePattern, "\"(" + valuePattern + ")\"");
            
            // 额外的日志记录，帮助调试
            if (value != null && !value.isEmpty()) {
                params.put(paramKey, value);
                log.debug("成功提取字段[{}]: {}", paramKey, value);
            } else {
                // 尝试简单的字符串解析作为备选方案
                String[] fieldNames = fieldNamePattern.split("\\|");
                for (String fieldName : fieldNames) {
                    String searchPattern = "\\\"" + fieldName + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"";
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(searchPattern);
                    java.util.regex.Matcher matcher = pattern.matcher(aiResponse);
                    
                    if (matcher.find()) {
                        String directValue = matcher.group(1);
                        if (directValue != null && !directValue.trim().isEmpty()) {
                            params.put(paramKey, directValue.trim());
                            log.debug("通过备选方案提取字段[{}]: {}", paramKey, directValue);
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("提取字段[{}]时出错: {}", paramKey, e.getMessage());
        }
    }
    
    /**
     * 提取字段值
     */
    private String extractFieldValue(String text, String fieldNamePattern, String regexPattern) {
        try {
            // 简化正则表达式，专门处理JSON格式，确保引号正确匹配
            String pattern = "\\\"(" + fieldNamePattern + ")\\\"\\s*:\\s*" + regexPattern;
            java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher m = p.matcher(text);
            
            if (m.find() && m.groupCount() > 1) {
                // 获取最后一个捕获组，即值部分
                String value = m.group(m.groupCount());
                return value != null ? value.trim() : null;
            }
            
            // 如果标准JSON格式匹配失败，尝试更宽松的匹配方式
            String loosePattern = "(?:" + fieldNamePattern + ")\\s*[:：]\\s*" + regexPattern;
            p = java.util.regex.Pattern.compile(loosePattern, java.util.regex.Pattern.CASE_INSENSITIVE);
            m = p.matcher(text);
            
            if (m.find() && m.groupCount() > 0) {
                String value = m.group(m.groupCount());
                return value != null ? value.trim() : null;
            }
        } catch (Exception e) {
            log.warn("提取字段值时出错: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * 提取学号信息
     */
    private void extractStudentId(String aiResponse, String lowerCaseResponse, Map<String, Object> params) {
        if (lowerCaseResponse.contains("学号") || lowerCaseResponse.contains("studentid") || lowerCaseResponse.contains("student id")) {
            // 提取数字序列作为学号
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\\\d{6,12})");
            java.util.regex.Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String studentId = matcher.group(1);
                if (studentId != null) {
                    params.put("studentId", studentId);
                }
            } else {
                // 尝试其他方式提取
                String[] parts = aiResponse.split("学号|");
                if (parts.length > 1) {
                    String studentId = parts[1].replaceAll("[^0-9]", "").trim();
                    if (!studentId.isEmpty()) {
                        params.put("studentId", studentId);
                    }
                }
            }
        }
    }
    
    /**
     * 提取姓名信息
     */
    private void extractName(String aiResponse, String lowerCaseResponse, Map<String, Object> params) {
        if (lowerCaseResponse.contains("姓名") || lowerCaseResponse.contains("name")) {
            // 提取中文字符作为姓名
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([\\\\u4e00-\\\\u9fa5]{2,4})");
            java.util.regex.Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String name = matcher.group(1);
                if (name != null) {
                    params.put("name", name);
                }
            } else {
                // 尝试其他方式提取
                String[] parts = aiResponse.split("姓名|");
                if (parts.length > 1) {
                    String name = parts[1].replaceAll("[^\\\\u4e00-\\\\u9fa5a-zA-Z]", "").trim();
                    if (!name.isEmpty()) {
                        params.put("name", name);
                    }
                }
            }
        }
    }
    
    /**
     * 提取专业信息
     */
    private void extractMajor(String aiResponse, String lowerCaseResponse, Map<String, Object> params) {
        if (lowerCaseResponse.contains("专业") || lowerCaseResponse.contains("major")) {
            // 提取专业名称
            String[] keywords = {"计算机", "软件工程", "网络工程", "电子信息", "通信工程", "人工智能", 
                                "计算机科学", "软件工程", "网络工程", "信息安全"};
            
            for (String keyword : keywords) {
                if (aiResponse.contains(keyword)) {
                    params.put("major", keyword);
                    return;
                }
            }
            
            // 如果没有找到预定义的专业名称，尝试通用提取
            String[] parts = aiResponse.split("专业|");
            if (parts.length > 1) {
                String major = parts[1].replaceAll("[^\\\\u4e00-\\\\u9fa5a-zA-Z]", "").trim();
                if (!major.isEmpty()) {
                    params.put("major", major);
                }
            }
        }
    }
    
    /**
     * 提取学院信息
     */
    private void extractDepartment(String aiResponse, String lowerCaseResponse, Map<String, Object> params) {
        if (lowerCaseResponse.contains("学院") || lowerCaseResponse.contains("department")) {
            String[] parts = aiResponse.split("学院|");
            if (parts.length > 1) {
                String department = parts[1].replaceAll("[^\\\\u4e00-\\\\u9fa5a-zA-Z]", "").trim();
                if (!department.isEmpty()) {
                    params.put("department", department);
                }
            }
        }
    }
    
    /**
     * 提取班级信息
     */
    private void extractClass(String aiResponse, String lowerCaseResponse, Map<String, Object> params) {
        if (lowerCaseResponse.contains("班级") || lowerCaseResponse.contains("class")) {
            // 提取班级信息，如"计算机2023级1班"
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("([\\u4e00-\\u9fa5]+\\d{4}级\\d+班|[\\u4e00-\\u9fa5]+\\d+班)");
            java.util.regex.Matcher matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String className = matcher.group(1);
                if (className != null) {
                    params.put("className", className);
                }
            } else {
                // 通用提取
                String[] parts = aiResponse.split("班级|");
                if (parts.length > 1) {
                    String className = parts[1].replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]", "").trim();
                    if (!className.isEmpty()) {
                        params.put("className", className);
                    }
                }
            }
        }
    }
    
    /**
     * 提取年级信息
     */
    private void extractGrade(String aiResponse, String lowerCaseResponse, Map<String, Object> params) {
        // 多种方式提取年级信息
        
        // 1. 从JSON格式中提取
        if (aiResponse.trim().startsWith("{") && aiResponse.trim().endsWith("}")) {
            String gradeValue = extractFieldValue(aiResponse, "grade|年级", "\\\"(\\d+)\\\"");
            if (gradeValue != null) {
                try {
                    int grade = Integer.parseInt(gradeValue);
                    params.put("grade", grade);
                    return;
                } catch (NumberFormatException e) {
                    log.warn("JSON中年级转换失败: {}", gradeValue);
                }
            }
        }
        
        // 2. 从文本中提取年份格式（如2023、2024等）
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(20\\d{2})");
        java.util.regex.Matcher matcher = pattern.matcher(aiResponse);
        if (matcher.find()) {
            String gradeStr = matcher.group(1);
            if (gradeStr != null) {
                try {
                    int grade = Integer.parseInt(gradeStr);
                    params.put("grade", grade);
                    return;
                } catch (NumberFormatException e) {
                    log.warn("年级转换失败: {}", gradeStr);
                }
            }
        }
        
        // 3. 从包含"年级"关键词的文本中提取
        if (lowerCaseResponse.contains("年级") || lowerCaseResponse.contains("grade")) {
            // 尝试提取数字
            pattern = java.util.regex.Pattern.compile("(\\d+)");
            matcher = pattern.matcher(aiResponse);
            if (matcher.find()) {
                String gradeStr = matcher.group(1);
                if (gradeStr != null && gradeStr.length() == 4) {
                    try {
                        int grade = Integer.parseInt(gradeStr);
                        params.put("grade", grade);
                        return;
                    } catch (NumberFormatException e) {
                        log.warn("年级关键词提取转换失败: {}", gradeStr);
                    }
                }
            }
        }
        
        // 4. 从班级信息中提取年级（如"计算机2023级1班"）
        pattern = java.util.regex.Pattern.compile("(20\\d{2})级");
        matcher = pattern.matcher(aiResponse);
        if (matcher.find()) {
            String gradeStr = matcher.group(1);
            if (gradeStr != null) {
                try {
                    int grade = Integer.parseInt(gradeStr);
                    params.put("grade", grade);
                } catch (NumberFormatException e) {
                    log.warn("班级中年级转换失败: {}", gradeStr);
                }
            }
        }
    }
    
    /**
     * 提取激活状态
     */
    private void extractActiveStatus(String aiResponse, String lowerCaseResponse, Map<String, Object> params) {
        // 只在params中尚未包含active字段时才执行关键词提取
        // 优先使用从JSON中解析出的明确值，而不是基于文本关键词的推断
        if (!params.containsKey("active")) {
            // 严格检查是否明确提到查询禁用/未激活的学生
            if (lowerCaseResponse.contains("未激活") && lowerCaseResponse.contains("查询") || 
                lowerCaseResponse.contains("禁用") && lowerCaseResponse.contains("查询") ||
                lowerCaseResponse.contains("查询") && lowerCaseResponse.contains("所有禁用") ||
                lowerCaseResponse.contains("查询") && lowerCaseResponse.contains("所有未激活")) {
                params.put("active", false);
            } else if (lowerCaseResponse.contains("已激活") && lowerCaseResponse.contains("查询") || 
                      lowerCaseResponse.contains("查询") && lowerCaseResponse.contains("所有激活")) {
                params.put("active", true);
            }
        }
    }
}