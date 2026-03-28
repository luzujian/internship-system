package com.gdmu.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.config.DynamicChatClientFactory;
import com.gdmu.entity.*;
import com.gdmu.enums.AiGradeEnum;
import com.gdmu.mapper.StudentReflectionAIAnalysisMapper;
import com.gdmu.service.CounselorAISettingsService;
import com.gdmu.service.InternshipReflectionService;
import com.gdmu.service.StudentReflectionAIAnalysisService;
import com.gdmu.util.AiServiceCircuitBreaker;
import com.gdmu.websocket.AnnouncementWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 学生实习心得 AI 分析服务实现类
 * 【AI 评分工程化方案】
 * 1. 可配置评分规则引擎 - 支持辅导员自定义评分维度和权重
 * 2. AI 响应多层容错解析 - 处理 markdown、格式错误、自动降级计算
 * 3. 熔断器 + 降级方案 - 连续失败 5 次后熔断 5 分钟，不可用时友好提示
 */
@Slf4j
@Service
public class StudentReflectionAIAnalysisServiceImpl implements StudentReflectionAIAnalysisService {

    @Autowired
    private StudentReflectionAIAnalysisMapper analysisMapper;

    @Autowired
    private CounselorAISettingsService counselorAISettingsService;

    @Autowired
    private DynamicChatClientFactory chatClientFactory;

    /**
     * 【熔断器】AI 服务不可用时的降级处理
     */
    @Autowired
    private AiServiceCircuitBreaker circuitBreaker;

    @Autowired
    private InternshipReflectionService internshipReflectionService;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = "你是一位专业的实习指导教师，请仔细分析学生实习心得，根据内容质量给出客观、公正的评价和评分。";

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(StudentReflectionAIAnalysis analysis) {
        if (analysis.getCreateTime() == null) {
            analysis.setCreateTime(new Date());
        }
        if (analysis.getUpdateTime() == null) {
            analysis.setUpdateTime(new Date());
        }
        return analysisMapper.insert(analysis);
    }

    @Override
    public StudentReflectionAIAnalysis findById(Long id) {
        return analysisMapper.findById(id);
    }

    @Override
    public StudentReflectionAIAnalysis findByReflectionId(Long reflectionId) {
        return analysisMapper.findByReflectionId(reflectionId);
    }

    @Override
    public StudentReflectionAIAnalysis findByStudentId(Long studentId) {
        return analysisMapper.findByStudentId(studentId);
    }

    @Override
    public List<StudentReflectionAIAnalysis> findByCounselorId(Long counselorId) {
        return analysisMapper.findByCounselorId(counselorId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(StudentReflectionAIAnalysis analysis) {
        analysis.setUpdateTime(new Date());
        return analysisMapper.update(analysis);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        return analysisMapper.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByReflectionId(Long reflectionId) {
        return analysisMapper.deleteByReflectionId(reflectionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyzeReflection(Long reflectionId, Long counselorId) {
        return Map.of();
    }

    /**
     * 【核心方法】AI 分析实习心得内容
     * 集成熔断器降级处理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyzeReflectionContent(String content, Long counselorId, Long studentId, Long reflectionId) {
        Map<String, Object> result = new HashMap<>();

        // 【空内容检查】
        if (content == null || content.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "实习心得内容不能为空");
            return result;
        }

        // 【熔断器检查】AI 服务不可用时直接返回降级结果
        if (!circuitBreaker.allowRequest(counselorId)) {
            log.warn("AI 服务当前不可用（辅导员ID: {}），返回降级结果", counselorId);
            return buildFallbackResult();
        }

        try {
            // 【内容验证】先检查内容是否是实习心得
            Map<String, Object> validationResult = validateReflectionContent(content, counselorId);
            boolean isReflection = (boolean) validationResult.getOrDefault("isReflection", true);
            if (!isReflection) {
                // 内容不是实习心得，更新状态为草稿
                log.info("AI分析判定内容不是实习心得，反射ID: {}，原因: {}", reflectionId, validationResult.get("reason"));

                // 使用编程式事务确保数据库更新和WebSocket发送在事务提交后执行
                final Long finalStudentId = studentId;
                Boolean updateSuccess = transactionTemplate.execute(status -> {
                    try {
                        InternshipReflection reflection = internshipReflectionService.findById(reflectionId);
                        if (reflection != null) {
                            reflection.setStatus(0);
                            reflection.setRemark("0"); // 设置为草稿状态
                            internshipReflectionService.updateById(reflection);
                        }
                        return true;
                    } catch (Exception e) {
                        log.warn("更新实习心得状态为草稿失败: {}", e.getMessage());
                        return false;
                    }
                });

                result.put("success", false);
                result.put("isNotReflection", true);
                result.put("message", "您提交的内容不是实习心得，已被退回草稿，请修改后重新提交");

                // 【WebSocket推送】通知学生内容被退回（只在更新成功时发送）
                if (Boolean.TRUE.equals(updateSuccess)) {
                    webSocketHandler.sendAIAnalysisResultToUser(finalStudentId, reflectionId, false, true,
                        "您提交的内容不是实习心得，已被退回草稿，请修改后重新提交");
                }

                return result;
            }

            // 【可配置评分规则】从数据库获取辅导员自定义评分规则
            List<CounselorScoringRule> scoringRules = counselorAISettingsService.getScoringRules(counselorId);

            // 【规则有效性验证】检查规则是否为有效的多维度配置
            if (scoringRules == null || scoringRules.isEmpty() || !isValidMultiDimensionalRules(scoringRules)) {
                log.warn("辅导员ID: {} 的评分规则配置无效（共{}条规则），使用默认规则", counselorId,
                    scoringRules == null ? 0 : scoringRules.size());
                scoringRules = getDefaultScoringRules();
            }

            String prompt = buildAnalysisPrompt(content, scoringRules);

            CounselorAISettings settings = counselorAISettingsService.findByCounselorId(counselorId);
            String modelCode = (settings != null && settings.getAiModelCode() != null)
                ? settings.getAiModelCode() : "deepseek-chat";

            ChatClient chatClient = chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);

            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("AI 分析响应（辅导员 ID: {}, 模型：{}）: {}", counselorId, modelCode, aiResponse);

            // 【多层容错解析】解析 AI 响应，支持降级计算
            Map<String, Object> analysisData = parseAIResponse(aiResponse, scoringRules, counselorId);

            StudentReflectionAIAnalysis analysis = new StudentReflectionAIAnalysis();
            analysis.setReflectionId(reflectionId);
            analysis.setStudentId(studentId);
            analysis.setCounselorId(counselorId);
            analysis.setOverallAnalysis((String) analysisData.get("overallAnalysis"));
            analysis.setKeywords((List<String>) analysisData.get("keywords"));
            analysis.setSentimentPositive((Integer) analysisData.get("sentimentPositive"));
            analysis.setSentimentNeutral((Integer) analysisData.get("sentimentNeutral"));
            analysis.setSentimentNegative((Integer) analysisData.get("sentimentNegative"));
            // 【修复】将 scoreDetails 从 Map{ruleCode: score} 转换为 List{ruleName, ruleCode, score} 格式
            analysis.setScoreDetails(convertScoreDetailsForFrontend((Map<String, Object>) analysisData.get("scoreDetails"), scoringRules));
            analysis.setTotalScore((BigDecimal) analysisData.get("totalScore"));
            analysis.setGrade((String) analysisData.get("grade"));
            analysis.setAnalysisReport((String) analysisData.get("analysisReport"));
            analysis.setAiModelCode(modelCode);
            analysis.setAnalysisTime(new Date());
            analysis.setCreateTime(new Date());
            analysis.setUpdateTime(new Date());

            // 删除旧的分析记录（如果存在），避免重复插入
            deleteByReflectionId(reflectionId);
            insert(analysis);

            // 更新实习心得状态为已分析
            try {
                InternshipReflection reflection = internshipReflectionService.findById(reflectionId);
                if (reflection != null) {
                    reflection.setStatus(1);
                    reflection.setRemark(null); // 清除草稿状态
                    internshipReflectionService.updateById(reflection);
                }
            } catch (Exception e) {
                log.warn("更新实习心得状态失败: {}", e.getMessage());
            }

            // 【熔断器】调用成功，重置失败计数
            circuitBreaker.recordSuccess(counselorId);

            result.put("success", true);
            result.put("data", analysis);

            // 【WebSocket推送】通知学生AI分析完成
            webSocketHandler.sendAIAnalysisResultToUser(studentId, reflectionId, true, false,
                "AI分析完成，您的实习心得已通过分析");

            return result;
        } catch (Exception e) {
            log.error("AI 分析失败（辅导员ID: {}）：{}", counselorId, e.getMessage(), e);

            // 【熔断器】仅对 AI 相关异常（网络、超时、解析等）记录失败
            // 业务异常（如数据库错误）不触发熔断
            if (isAIRelatedException(e)) {
                circuitBreaker.recordFailure(counselorId);
            }

            result.put("success", false);
            result.put("message", "AI 分析失败：" + e.getMessage());

            // 【WebSocket推送】通知学生AI分析失败
            webSocketHandler.sendAIAnalysisResultToUser(studentId, reflectionId, false, false,
                "AI分析失败，请稍后重试或联系教师");

            return buildFallbackResult();
        }
    }

    /**
     * 判断是否为 AI 相关异常（网络超时、解析错误等应该触发熔断）
     * 业务异常（如数据库约束违反）不应触发熔断
     */
    private boolean isAIRelatedException(Exception e) {
        if (e == null) return false;
        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        // AI 相关异常特征：网络、超时、解析、连接、AI服务等
        return msg.contains("timeout") || msg.contains("connection")
            || msg.contains("network") || msg.contains("socket")
            || msg.contains("ai") || msg.contains("chat")
            || msg.contains("json") || msg.contains("parse")
            || msg.contains("spring.ai") || msg.contains("chatclient");
    }

    /**
     * 【降级结果】AI 服务不可用时的友好提示
     */
    private Map<String, Object> buildFallbackResult() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("success", false);
        fallback.put("fallback", true);
        fallback.put("message", "AI 服务暂时不可用，请使用人工评分功能");
        return fallback;
    }

    /**
     * 【规则有效性验证】检查规则是否为有效的配置
     * 有效规则：规则数量>=2条即可
     * 注意：辅导员的配置可能只有1个category但有多条等级规则（如innovation_能力分5个等级）
     * 这是有效的配置，应该被使用
     */
    private boolean isValidMultiDimensionalRules(List<CounselorScoringRule> scoringRules) {
        if (scoringRules == null || scoringRules.size() < 2) {
            return false;
        }
        return true;
    }

    /**
     * 【动态 Prompt 构建】根据配置的评分规则生成 AI 提示词
     * 每个category多条等级规则，AI只需选择每个category对应的等级
     */
    private String buildAnalysisPrompt(String content, List<CounselorScoringRule> scoringRules) {
        StringBuilder scoringCriteria = new StringBuilder();
        StringBuilder weightInfo = new StringBuilder();

        // 按category分组显示等级规则
        Map<String, List<CounselorScoringRule>> rulesByCategory = new LinkedHashMap<>();
        for (CounselorScoringRule rule : scoringRules) {
            if (rule.getStatus() != null && rule.getStatus() == 1 && rule.getCategory() != null) {
                rulesByCategory.computeIfAbsent(rule.getCategory(), k -> new ArrayList<>()).add(rule);
            }
        }

        int totalWeight = 0;
        for (Map.Entry<String, List<CounselorScoringRule>> entry : rulesByCategory.entrySet()) {
            String category = entry.getKey();
            List<CounselorScoringRule> rules = entry.getValue();
            if (rules.isEmpty()) continue;

            // 第一个规则的权重作为该category的权重
            int weight = rules.get(0).getWeight() != null ? rules.get(0).getWeight() : 1;
            totalWeight += weight;

            scoringCriteria.append(category).append("：\n");
            for (CounselorScoringRule rule : rules) {
                scoringCriteria.append("- ").append(rule.getRuleName()).append("：")
                    .append(rule.getMinScore()).append("-").append(rule.getMaxScore())
                    .append("分：").append(rule.getDescription() != null ? rule.getDescription() : "").append("\n");
            }
            scoringCriteria.append("\n");
        }

        weightInfo.append("总权重：").append(totalWeight).append("\n\n");
        weightInfo.append("各维度权重（每category权重相同）：\n");
        for (Map.Entry<String, List<CounselorScoringRule>> entry : rulesByCategory.entrySet()) {
            String category = entry.getKey();
            int weight = entry.getValue().get(0).getWeight() != null ? entry.getValue().get(0).getWeight() : 1;
            weightInfo.append("- ").append(category).append("：").append(weight).append("\n");
        }

        // 构建示例：每个category只选一个等级
        StringBuilder aspectsExample = new StringBuilder();
        boolean first = true;
        for (String category : rulesByCategory.keySet()) {
            if (!first) {
                aspectsExample.append(",\n                    ");
            }
            aspectsExample.append("\"").append(category).append("\": \"中等\"");
            first = false;
        }

        return String.format(
            "你是一位专业的实习指导教师，请仔细分析以下学生实习心得，根据内容质量给出客观、公正的评价和评分。\n\n" +
            "实习心得内容：\n%s\n\n" +
            "评分标准（每个维度请选择最合适的等级）：\n%s\n\n" +
            "类别权重配置：\n%s\n\n" +
            "请按以下 JSON 格式返回分析结果（不要包含其他文字，只返回 JSON）：\n" +
            "{\n" +
            "    \"keywords\": [\"关键词 1\", \"关键词 2\", \"关键词 3\", \"关键词 4\", \"关键词 5\", \"关键词 6\", \"关键词 7\", \"关键词 8\"],\n" +
            "    \"overallAnalysis\": \"实习心得的整体分析（150-180字）\",\n" +
            "    \"sentimentPositive\": 70,\n" +
            "    \"sentimentNeutral\": 20,\n" +
            "    \"sentimentNegative\": 10,\n" +
            "    \"scoreDetails\": {\n" +
            "        %s\n" +
            "    },\n" +
            "    \"totalScore\": 85.5,\n" +
            "    \"grade\": \"良好\",\n" +
            "    \"analysisReport\": \"# 实习心得分析报告\\n\\n## 一、内容概述\\n...\\n\\n## 二、亮点分析\\n...\\n\\n## 三、改进建议\\n...\\n\\n## 四、综合评价\\n...\"\n" +
            "}\n\n" +
            "重要说明：\n" +
            "1. scoreDetails 中每个评分维度只需返回选中的等级名称（如 \"良好\"），系统会自动根据等级匹配对应分数\n" +
            "2. totalScore 是根据各维度分数和权重计算的加权总分\n" +
            "3. 加权总分计算公式：Σ(维度分数 × 权重) / 总权重\n" +
            "4. grade 根据总分判定：90 分以上为优秀，80-89 分为良好，70-79 分为中等，60-69 分为及格，60 分以下为不及格\n" +
            "5. analysisReport 是完整的分析报告，使用 Markdown 格式\n" +
            "6. 请根据实习心得的实际内容进行评分，不要给出固定分数",
            content, scoringCriteria.toString(), weightInfo.toString(), aspectsExample.toString()
        );
    }

    /**
     * 【多层容错解析】AI 响应解析，支持多种格式和降级计算
     */
    private Map<String, Object> parseAIResponse(String aiResponse, List<CounselorScoringRule> scoringRules, Long counselorId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 第一层：提取 JSON（处理 markdown 代码块）
            String jsonStr = aiResponse.trim();

            // 查找 JSON 代码块的起始和结束位置
            int jsonBlockStart = -1;
            int jsonBlockEnd = -1;

            if (jsonStr.contains("```json")) {
                // 格式：```json ... ```
                jsonBlockStart = jsonStr.indexOf("```json") + 7;
                // 从起始位置之后查找结束标记
                int closingMarker = jsonStr.indexOf("```", jsonBlockStart);
                if (closingMarker > jsonBlockStart) {
                    jsonBlockEnd = closingMarker;
                }
            } else if (jsonStr.contains("```")) {
                // 格式：``` ... ```
                jsonBlockStart = jsonStr.indexOf("```") + 3;
                int closingMarker = jsonStr.indexOf("```", jsonBlockStart);
                if (closingMarker > jsonBlockStart) {
                    jsonBlockEnd = closingMarker;
                }
            }

            // 如果找到有效的代码块，则提取内容；否则使用原文本
            if (jsonBlockStart > 0 && jsonBlockEnd > jsonBlockStart) {
                jsonStr = jsonStr.substring(jsonBlockStart, jsonBlockEnd);
            }

            jsonStr = jsonStr.trim();

            // 第二层：JSON 解析
            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);

            result.put("keywords", data.get("keywords"));
            result.put("overallAnalysis", data.get("overallAnalysis"));
            result.put("sentimentPositive", data.get("sentimentPositive"));
            result.put("sentimentNeutral", data.get("sentimentNeutral"));
            result.put("sentimentNegative", data.get("sentimentNegative"));
            result.put("scoreDetails", data.get("scoreDetails"));
            result.put("analysisReport", data.get("analysisReport"));

            // 第三层：分数验证，AI 未返回总分时自动降级计算
            Object totalScoreObj = data.get("totalScore");
            BigDecimal totalScore;
            if (totalScoreObj instanceof Number) {
                totalScore = new BigDecimal(totalScoreObj.toString());
            } else {
                totalScore = calculateWeightedScore((Map<String, Object>) data.get("scoreDetails"), scoringRules, counselorId);
            }
            result.put("totalScore", totalScore.setScale(2, RoundingMode.HALF_UP));

            // 第四层：等级判定，AI 未返回时使用枚举计算
            String grade = (String) data.get("grade");
            if (grade == null || grade.isEmpty()) {
                grade = AiGradeEnum.getNameByScore(totalScore.doubleValue());
            }
            result.put("grade", grade);

        } catch (Exception e) {
            log.error("解析 AI 响应失败：{}", e.getMessage(), e);
            // 降级方案：返回默认结构，标记为解析失败
            result.put("keywords", new ArrayList<>());
            result.put("overallAnalysis", "AI 分析结果解析失败");
            result.put("sentimentPositive", 50);
            result.put("sentimentNeutral", 30);
            result.put("sentimentNegative", 20);
            result.put("scoreDetails", new HashMap<>());
            result.put("totalScore", BigDecimal.ZERO);
            result.put("grade", "未评级");
            result.put("analysisReport", "AI 分析结果解析失败，请重新分析");
        }

        return result;
    }

    /**
     * 加权分数计算
     * AI返回的是等级名称（如"中等"），需要根据规则找到对应分数
     * 【修复】使用 CounselorCategoryWeight 的权重，与前端成绩评定保持一致
     */
    private BigDecimal calculateWeightedScore(Map<String, Object> scoreDetails, List<CounselorScoringRule> scoringRules, Long counselorId) {
        if (scoreDetails == null || scoreDetails.isEmpty() || scoringRules == null || scoringRules.isEmpty()) {
            return BigDecimal.ZERO;
        }

        // 构建 category -> {gradeName -> rule} 的映射，用于快速查找等级对应分数
        Map<String, Map<String, CounselorScoringRule>> categoryGradeRules = new HashMap<>();

        for (CounselorScoringRule rule : scoringRules) {
            if (rule.getStatus() != null && rule.getStatus() == 1 && rule.getCategory() != null) {
                String category = rule.getCategory();
                String gradeName = rule.getRuleName(); // 如"优秀"、"良好"等

                categoryGradeRules.computeIfAbsent(category, k -> new HashMap<>()).put(gradeName, rule);
            }
        }

        // 【修复】从 CounselorCategoryWeight 获取权重，与前端保持一致
        Map<String, Integer> categoryWeight = new HashMap<>();
        if (counselorId != null) {
            List<CounselorCategoryWeight> weights = counselorAISettingsService.getCategoryWeights(counselorId);
            if (weights != null) {
                for (CounselorCategoryWeight cw : weights) {
                    if (cw.getCategoryCode() != null && cw.getWeight() != null) {
                        categoryWeight.put(cw.getCategoryCode(), cw.getWeight());
                    }
                }
            }
        }

        // 如果没有配置 categoryWeight，回退到使用旧的计算方式（使用 scoringRules 的第一个规则的 weight）
        if (categoryWeight.isEmpty()) {
            for (CounselorScoringRule rule : scoringRules) {
                if (rule.getStatus() != null && rule.getStatus() == 1 && rule.getCategory() != null) {
                    String category = rule.getCategory();
                    if (!categoryWeight.containsKey(category)) {
                        categoryWeight.put(category, rule.getWeight() != null ? rule.getWeight() : 1);
                    }
                }
            }
        }

        BigDecimal totalWeightedScore = BigDecimal.ZERO;
        int totalWeight = 0;

        for (Map.Entry<String, Integer> entry : categoryWeight.entrySet()) {
            String category = entry.getKey();
            Object gradeObj = scoreDetails.get(category);

            if (gradeObj != null) {
                String gradeName = gradeObj.toString();
                Map<String, CounselorScoringRule> gradeRules = categoryGradeRules.get(category);

                if (gradeRules != null && gradeRules.containsKey(gradeName)) {
                    CounselorScoringRule rule = gradeRules.get(gradeName);
                    // 使用该等级的中间分数：(minScore + maxScore) / 2
                    int midScore = (rule.getMinScore() + rule.getMaxScore()) / 2;
                    BigDecimal score = new BigDecimal(midScore);
                    int weight = entry.getValue();
                    totalWeightedScore = totalWeightedScore.add(score.multiply(new BigDecimal(weight)));
                    totalWeight += weight;
                    log.debug("Category: {}, Grade: {}, MidScore: {}, Weight: {}", category, gradeName, midScore, weight);
                }
            }
        }

        if (totalWeight == 0) {
            return BigDecimal.ZERO;
        }

        return totalWeightedScore.divide(new BigDecimal(totalWeight), 2, RoundingMode.HALF_UP);
    }

    /**
     * 将 scoreDetails 从 Map{category: gradeName} 转换为前端期望的格式
     * AI返回的scoreDetails是等级名称（如 {"innovation_ability": "中等"}）
     * 转换为 {category: category, name: categoryName, grade: gradeName, score: midScore} 格式
     */
    private Map<String, Object> convertScoreDetailsForFrontend(Map<String, Object> scoreDetails, List<CounselorScoringRule> scoringRules) {
        if (scoreDetails == null) {
            return new HashMap<>();
        }
        if (scoringRules == null || scoringRules.isEmpty()) {
            return scoreDetails;
        }

        // 构建 category -> {gradeName -> rule} 的映射
        Map<String, Map<String, CounselorScoringRule>> categoryGradeRules = new HashMap<>();
        // 按category聚合权重
        Map<String, String> categoryToName = new HashMap<>();

        for (CounselorScoringRule rule : scoringRules) {
            if (rule.getStatus() != null && rule.getStatus() == 1 && rule.getCategory() != null) {
                String category = rule.getCategory();
                String gradeName = rule.getRuleName();

                categoryGradeRules.computeIfAbsent(category, k -> new HashMap<>()).put(gradeName, rule);

                if (!categoryToName.containsKey(category)) {
                    // 使用category作为显示名称（已足够清晰）
                    categoryToName.put(category, category);
                }
            }
        }

        // 转换为前端期望的格式
        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : scoreDetails.entrySet()) {
            String category = entry.getKey();
            String categoryName = categoryToName.getOrDefault(category, category);
            String gradeName = entry.getValue().toString();

            Map<String, CounselorScoringRule> gradeRules = categoryGradeRules.get(category);
            int midScore = 0;
            if (gradeRules != null && gradeRules.containsKey(gradeName)) {
                CounselorScoringRule rule = gradeRules.get(gradeName);
                midScore = (rule.getMinScore() + rule.getMaxScore()) / 2;
            }

            Map<String, Object> detail = new HashMap<>();
            detail.put("category", category);
            detail.put("name", categoryName);
            detail.put("grade", gradeName);
            detail.put("score", midScore);
            result.put(category, detail);
        }
        return result;
    }

    /**
     * 【使用枚举】根据分数获取等级
     */
    private String calculateGrade(BigDecimal score) {
        return AiGradeEnum.getNameByScore(score.doubleValue());
    }

    /**
     * 【内容验证】检查提交的内容是否是实习心得
     */
    private Map<String, Object> validateReflectionContent(String content, Long counselorId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 构建验证 prompt
            String validationPrompt = """
                请判断以下内容是否为学生的实习心得。

                实习心得的特征：
                1. 描述实习期间的工作内容、经历和感受
                2. 反思实习过程中的收获和不足
                3. 体现对专业知识的应用和实践
                4. 包含对实习单位、岗位、工作环境的描述
                5. 表达对实习的感悟和体会

                非实习心得的特征：
                1. 纯粹的技术文档、代码说明
                2. 课程作业、论文、研究报告
                3. 个人日记、生活随笔
                4. 与实习工作无关的文档

                待判断的内容：
                %s

                请严格按照以下JSON格式返回判断结果（不要包含其他文字，只返回JSON）：
                {
                    "isReflection": true/false,
                    "reason": "判断理由（简短说明）"
                }
                """.formatted(content);

            CounselorAISettings settings = counselorAISettingsService.findByCounselorId(counselorId);
            String modelCode = (settings != null && settings.getAiModelCode() != null)
                ? settings.getAiModelCode() : "deepseek-chat";

            ChatClient chatClient = chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);

            String aiResponse = chatClient.prompt()
                    .user(validationPrompt)
                    .call()
                    .content();

            log.info("AI内容验证响应: {}", aiResponse);

            // 检查 AI 响应是否为空
            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                log.warn("AI 内容验证响应为空，使用默认结果");
                result.put("isReflection", true);
                result.put("reason", "AI 响应为空，默认通过");
                return result;
            }

            Map<String, Object> validationData = parseSimpleAIResponse(aiResponse);

            Object isReflectionObj = validationData.get("isReflection");
            boolean isReflection = true;

            if (isReflectionObj instanceof Boolean) {
                isReflection = (Boolean) isReflectionObj;
            } else if (isReflectionObj instanceof String) {
                isReflection = Boolean.parseBoolean((String) isReflectionObj);
            }

            result.put("isReflection", isReflection);
            result.put("reason", validationData.get("reason"));

            return result;
        } catch (Exception e) {
            log.error("内容验证失败: content长度={}, counselorId={}, 错误: {}", content != null ? content.length() : 0, counselorId, e.getMessage(), e);
            result.put("isReflection", true);
            result.put("reason", "验证失败，默认认为是实习心得");
            return result;
        }
    }

    /**
     * 【简单响应解析】用于内容验证的JSON解析
     */
    private Map<String, Object> parseSimpleAIResponse(String aiResponse) {
        Map<String, Object> result = new HashMap<>();

        try {
            String jsonStr = aiResponse.trim();

            // 查找 JSON 代码块的起始和结束位置
            int jsonBlockStart = -1;
            int jsonBlockEnd = -1;

            if (jsonStr.contains("```json")) {
                jsonBlockStart = jsonStr.indexOf("```json") + 7;
                int closingMarker = jsonStr.indexOf("```", jsonBlockStart);
                if (closingMarker > jsonBlockStart) {
                    jsonBlockEnd = closingMarker;
                }
            } else if (jsonStr.contains("```")) {
                jsonBlockStart = jsonStr.indexOf("```") + 3;
                int closingMarker = jsonStr.indexOf("```", jsonBlockStart);
                if (closingMarker > jsonBlockStart) {
                    jsonBlockEnd = closingMarker;
                }
            }

            if (jsonBlockStart > 0 && jsonBlockEnd > jsonBlockStart) {
                jsonStr = jsonStr.substring(jsonBlockStart, jsonBlockEnd);
            }

            jsonStr = jsonStr.trim();

            @SuppressWarnings("unchecked")
            Map<String, Object> data = objectMapper.readValue(jsonStr, Map.class);

            result.put("isReflection", data.get("isReflection"));
            result.put("reason", data.get("reason"));

        } catch (Exception e) {
            log.error("解析验证响应失败：{}", e.getMessage(), e);
            result.put("isReflection", true);
            result.put("reason", "解析失败，默认通过");
        }

        return result;
    }

    /**
     * 默认评分规则（当数据库未配置时使用）
     */
    private List<CounselorScoringRule> getDefaultScoringRules() {
        List<CounselorScoringRule> rules = new ArrayList<>();

        CounselorScoringRule rule1 = new CounselorScoringRule();
        rule1.setRuleName("实习态度");
        rule1.setRuleCode("attitude");
        rule1.setWeight(30);
        rule1.setMinScore(0);
        rule1.setMaxScore(100);
        rule1.setDescription("实习态度是否端正，是否积极主动");
        rule1.setStatus(1);
        rules.add(rule1);

        CounselorScoringRule rule2 = new CounselorScoringRule();
        rule2.setRuleName("专业能力");
        rule2.setRuleCode("professional");
        rule2.setWeight(40);
        rule2.setMinScore(0);
        rule2.setMaxScore(100);
        rule2.setDescription("专业知识和技能的应用能力");
        rule2.setStatus(1);
        rules.add(rule2);

        CounselorScoringRule rule3 = new CounselorScoringRule();
        rule3.setRuleName("心得质量");
        rule3.setRuleCode("report");
        rule3.setWeight(30);
        rule3.setMinScore(0);
        rule3.setMaxScore(100);
        rule3.setDescription("实习心得的内容质量和反思深度");
        rule3.setStatus(1);
        rules.add(rule3);

        return rules;
    }
}
