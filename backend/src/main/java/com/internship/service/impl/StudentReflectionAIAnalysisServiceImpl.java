package com.internship.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.config.DynamicChatClientFactory;
import com.internship.entity.*;
import com.internship.enums.AiGradeEnum;
import com.internship.mapper.StudentReflectionAIAnalysisMapper;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.agent.business.AgentException;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.InternshipReflectionService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.StudentReflectionAIAnalysisService;
import com.internship.util.AiServiceCircuitBreaker;
import com.internship.websocket.AnnouncementWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 学生实习心得 AI 分析服务实现类
 * 【AI 评分工程化方案】
 * 1. 可配置评分规则引擎 - 支持辅导员自定义评分维度和权重
 * 2. 严格验证 AI 响应 - 支持 JSON 代码块，拒绝缺失或错误评分
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

    @Autowired
    private InternshipReflectionMapper reflectionMapper;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    private final Map<Long, Object> runningAnalyses = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

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
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> analyzeReflection(Long reflectionId, Long counselorId) {
        InternshipReflection reflection = internshipReflectionService.findById(reflectionId);
        if (reflection == null) return failure("实习心得不存在", false);
        return analyzeReflectionContent(reflection.getContent(), counselorId, reflection.getStudentId(), reflectionId);
    }

    /** Network work runs outside a database transaction; only the version-checked write is transactional. */
    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> analyzeReflectionContent(String content, Long counselorId, Long studentId, Long reflectionId) {
        if (reflectionId == null || studentId == null || counselorId == null || content == null || content.isBlank()) {
            return failure("实习心得及辅导员信息不完整", false);
        }
        Object claim = new Object();
        if (runningAnalyses.putIfAbsent(reflectionId, claim) != null) {
            return deferred("该心得正在分析，请稍后查看结果");
        }
        try {
            SubmissionSnapshot captured = transactionTemplate.execute(transaction -> {
                InternshipReflection current = reflectionMapper.lockById(reflectionId);
                if (!submitted(current) || !Objects.equals(studentId, current.getStudentId())
                        || !Objects.equals(content, current.getContent())
                        || reflectionMapper.hasManualEvaluation(reflectionId)) return null;
                InternshipReflection copy = new InternshipReflection();
                org.springframework.beans.BeanUtils.copyProperties(current, copy);
                return new SubmissionSnapshot(copy, reflectionMapper.latestSubmissionEventId(reflectionId, studentId));
            });
            if (captured == null) return staleResult();
            InternshipReflection snapshot = captured.reflection();
            Long submissionVersion = captured.version();
            if (!circuitBreaker.allowRequest(counselorId)) {
                Map<String, Object> fallback = buildFallbackResult();
                fallback.put("retryable", true);
                fallback.put("deferred", true);
                return fallback;
            }
            Map<String, Object> validation = validateReflectionContent(content, counselorId);
            if (!Boolean.TRUE.equals(validation.get("isReflection"))) {
                boolean applied = persistResult(snapshot, submissionVersion, null);
                if (!applied) return staleResult();
                circuitBreaker.recordSuccess(counselorId);
                notifyResult(studentId, reflectionId, false, true, "您提交的内容不是实习心得，已退回草稿，请修改后重新提交");
                return Map.of("success", false, "isNotReflection", true, "retryable", false,
                        "message", "您提交的内容不是实习心得，已退回草稿，请修改后重新提交");
            }
            List<CounselorScoringRule> rules = counselorAISettingsService.getScoringRules(counselorId);
            if (!isValidMultiDimensionalRules(rules)) rules = getDefaultScoringRules();
            CounselorAISettings settings = counselorAISettingsService.findByCounselorId(counselorId);
            String requestedModel = settings != null && settings.getAiModelCode() != null
                    ? settings.getAiModelCode() : "deepseek-flash";
            String modelCode = chatClientFactory.resolveModelCode(requestedModel);
            ChatClient client = chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
            String response = client.prompt().user(buildAnalysisPrompt(content, rules)).call().content();
            Map<String, Object> data = parseAIResponse(response, rules, counselorId);
            StudentReflectionAIAnalysis analysis = new StudentReflectionAIAnalysis();
            analysis.setReflectionId(reflectionId);
            analysis.setStudentId(studentId);
            analysis.setCounselorId(counselorId);
            analysis.setOverallAnalysis((String) data.get("overallAnalysis"));
            analysis.setKeywords((List<String>) data.get("keywords"));
            analysis.setSentimentPositive((Integer) data.get("sentimentPositive"));
            analysis.setSentimentNeutral((Integer) data.get("sentimentNeutral"));
            analysis.setSentimentNegative((Integer) data.get("sentimentNegative"));
            analysis.setScoreDetails(convertScoreDetailsForFrontend((Map<String, Object>) data.get("scoreDetails"), rules));
            analysis.setTotalScore((BigDecimal) data.get("totalScore"));
            analysis.setGrade((String) data.get("grade"));
            analysis.setAnalysisReport((String) data.get("analysisReport"));
            analysis.setAiModelCode(modelCode);
            Date now = new Date();
            analysis.setAnalysisTime(now);
            analysis.setCreateTime(now);
            analysis.setUpdateTime(now);
            if (!persistResult(snapshot, submissionVersion, analysis)) return staleResult();
            circuitBreaker.recordSuccess(counselorId);
            notifyResult(studentId, reflectionId, true, false, "AI分析已完成，可查看分析结果");
            return Map.of("success", true, "data", analysis);
        } catch (AgentException e) {
            // Local admission control is not an upstream outage and must not trip the circuit breaker.
            return deferred(e.getMessage());
        } catch (Exception e) {
            log.warn("AI分析失败：reflectionId={}, type={}", reflectionId, e.getClass().getSimpleName());
            if (isAIRelatedException(e)) circuitBreaker.recordFailure(counselorId);
            return failure("心得已提交，AI分析暂未完成，可稍后重试或由教师人工批阅，无需重复提交", true);
        } finally {
            runningAnalyses.remove(reflectionId, claim);
        }
    }

    private boolean persistResult(InternshipReflection snapshot, Long submissionVersion, StudentReflectionAIAnalysis analysis) {
        return Boolean.TRUE.equals(transactionTemplate.execute(transaction -> {
            InternshipReflection current = reflectionMapper.lockById(snapshot.getId());
            if (!submitted(current) || reflectionMapper.hasManualEvaluation(snapshot.getId())
                    || !Objects.equals(snapshot.getStudentId(), current.getStudentId())
                    || !Objects.equals(snapshot.getContent(), current.getContent())
                    || !Objects.equals(snapshot.getSubmitTime(), current.getSubmitTime())
                    || !Objects.equals(snapshot.getUpdateTime(), current.getUpdateTime())
                    || !Objects.equals(snapshot.getStatus(), current.getStatus())
                    || !Objects.equals(submissionVersion,
                            reflectionMapper.latestSubmissionEventId(snapshot.getId(), snapshot.getStudentId()))) return false;
            // The row lock serializes withdrawal/review/resubmission with this short write, never with the API wait.
            analysisMapper.deleteByReflectionId(snapshot.getId());
            if (analysis != null && analysisMapper.insert(analysis) != 1) {
                throw new IllegalStateException("保存AI分析结果失败");
            }
            if (reflectionMapper.completeAiAnalysis(snapshot.getId(), analysis != null, new Date()) != 1) {
                throw new IllegalStateException("保存心得分析状态失败");
            }
            if (analysis == null) {
                // Draft state and submission lifecycle must change together, so the student can resubmit.
                com.internship.service.InternshipProgressEvents.append(progressRecordService,
                        snapshot.getStudentId(), "reflection_withdrawn", "AI已退回实习心得草稿",
                        "提交内容未通过实习心得验证，请修改后重新提交", "draft", snapshot.getId(), new Date());
            }
            return true;
        }));
    }

    private record SubmissionSnapshot(InternshipReflection reflection, Long version) { }

    private static boolean submitted(InternshipReflection reflection) {
        return reflection != null && !Integer.valueOf(1).equals(reflection.getDeleted())
                && reflection.getRemark() == null && reflection.getSubmitTime() != null
                && reflection.getContent() != null && !reflection.getContent().isBlank()
                && (Integer.valueOf(0).equals(reflection.getStatus()) || Integer.valueOf(1).equals(reflection.getStatus()));
    }

    private static Map<String, Object> failure(String message, boolean retryable) {
        return Map.of("success", false, "message", message, "retryable", retryable);
    }

    private static Map<String, Object> deferred(String message) {
        return Map.of("success", false, "message", message, "retryable", true, "deferred", true);
    }

    private static Map<String, Object> staleResult() {
        return Map.of("success", false, "stale", true, "retryable", false,
                "message", "心得已撤回、重交或进入批阅，本次旧分析结果已忽略");
    }

    private void notifyResult(Long studentId, Long reflectionId, boolean success, boolean isNotReflection, String message) {
        try {
            webSocketHandler.sendAIAnalysisResultToUser(studentId, reflectionId, success, isNotReflection, message);
        } catch (Exception e) {
            // A notification outage must never turn a committed grade into a failed/retried analysis.
            log.warn("推送AI分析通知失败：reflectionId={}", reflectionId);
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
        return scoringRules != null && !scoringRules.isEmpty() && scoringRules.stream().allMatch(rule ->
                rule.getCategory() != null && !rule.getCategory().isBlank() && rule.getRuleName() != null
                && rule.getMinScore() != null && rule.getMaxScore() != null
                && rule.getMinScore() >= 0 && rule.getMaxScore() <= 100 && rule.getMinScore() <= rule.getMaxScore());
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
            "1. 【关键】scoreDetails 中每个评分维度只需返回选中的等级名称（如 \"良好\"），必须是标准等级名称，不得添加任何描述性文字\n" +
            "2. totalScore 是根据各维度分数和权重计算的加权总分\n" +
            "3. 加权总分计算公式：Σ(维度分数 × 权重) / 总权重\n" +
            "4. grade 根据总分判定：90 分以上为优秀，80-89 分为良好，70-79 分为中等，60-69 分为及格，60 分以下为不及格\n" +
            "5. analysisReport 是完整的分析报告，使用 Markdown 格式\n" +
            "6. 请根据实习心得的实际内容进行评分，不要给出固定分数\n" +
            "7. 【强制】scoreDetails 的每个值必须是被双引号包裹的纯等级名称（如\"良好\"），禁止是句子或描述",
            content, scoringCriteria.toString(), weightInfo.toString(), aspectsExample.toString()
        );
    }

    /**
     * 严格解析 AI 分析结果，评分由有效规则及权重计算
     */
    private Map<String, Object> parseAIResponse(String aiResponse, List<CounselorScoringRule> scoringRules, Long counselorId) {
        try {
            Map<String, Object> data = readJsonResponse(aiResponse);
            if (!(data.get("keywords") instanceof List<?> keywords)
                    || keywords.stream().anyMatch(word -> !(word instanceof String) || ((String) word).isBlank())
                    || !(data.get("overallAnalysis") instanceof String summary) || summary.isBlank()
                    || !(data.get("analysisReport") instanceof String report) || report.isBlank()
                    || !(data.get("scoreDetails") instanceof Map<?, ?> details) || details.isEmpty()) {
                throw new IllegalArgumentException("AI JSON缺少有效的分析及评分字段");
            }
            int positive = percentage(data.get("sentimentPositive"));
            int neutral = percentage(data.get("sentimentNeutral"));
            int negative = percentage(data.get("sentimentNegative"));
            if (positive + neutral + negative != 100) throw new IllegalArgumentException("AI JSON情绪比例无效");
            Map<String, Object> scores = (Map<String, Object>) details;
            Map<String, Set<String>> accepted = new LinkedHashMap<>();
            for (CounselorScoringRule rule : scoringRules) {
                if (Integer.valueOf(1).equals(rule.getStatus())) {
                    accepted.computeIfAbsent(rule.getCategory(), key -> new HashSet<>()).add(rule.getRuleName());
                }
            }
            if (!scores.keySet().equals(accepted.keySet())) throw new IllegalArgumentException("AI JSON评分维度不完整");
            for (Map.Entry<String, Set<String>> category : accepted.entrySet()) {
                if (!(scores.get(category.getKey()) instanceof String selected)
                        || !category.getValue().contains(selected)) throw new IllegalArgumentException("AI JSON评分等级无效");
            }
            if (data.containsKey("totalScore")) {
                Object value = data.get("totalScore");
                if (!(value instanceof Number)) throw new IllegalArgumentException("AI JSON总分无效");
                BigDecimal supplied = new BigDecimal(value.toString());
                if (supplied.signum() < 0 || supplied.compareTo(BigDecimal.valueOf(100)) > 0) {
                    throw new IllegalArgumentException("AI JSON总分超出范围");
                }
            }
            // Grades use configured category weights, not an unverified model-supplied aggregate.
            BigDecimal totalScore = calculateWeightedScore(scores, scoringRules, counselorId);
            Map<String, Object> result = new HashMap<>(data);
            result.put("sentimentPositive", positive);
            result.put("sentimentNeutral", neutral);
            result.put("sentimentNegative", negative);
            result.put("totalScore", totalScore.setScale(2, RoundingMode.HALF_UP));
            result.put("grade", AiGradeEnum.getNameByScore(totalScore.doubleValue()));
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("AI JSON分析结果无效", e);
        }
    }

    private static int percentage(Object value) {
        if (!(value instanceof Number number)) throw new IllegalArgumentException("AI JSON情绪比例缺失");
        BigDecimal parsed = new BigDecimal(number.toString());
        int integer = parsed.intValueExact();
        if (integer < 0 || integer > 100) throw new IllegalArgumentException("AI JSON情绪比例超出范围");
        return integer;
    }

    private Map<String, Object> readJsonResponse(String response) throws com.fasterxml.jackson.core.JsonProcessingException {
        if (response == null || response.isBlank()) throw new IllegalArgumentException("AI JSON响应为空");
        String json = response.trim();
        if (json.startsWith("```")) {
            int firstLine = json.indexOf('\n');
            if (firstLine < 0 || !json.endsWith("```")) throw new IllegalArgumentException("AI JSON代码块不完整");
            String language = json.substring(3, firstLine).trim();
            if (!language.isEmpty() && !"json".equalsIgnoreCase(language)) throw new IllegalArgumentException("AI JSON代码块无效");
            json = json.substring(firstLine + 1, json.length() - 3).trim();
        }
        return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
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
                    if (Integer.valueOf(1).equals(cw.getStatus()) && cw.getCategoryCode() != null && cw.getWeight() != null && cw.getWeight() > 0) {
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

        if (totalWeight <= 0) {
            throw new IllegalArgumentException("AI评分权重配置无效");
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
                ? settings.getAiModelCode() : "deepseek-flash";

            ChatClient chatClient = chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);

            String aiResponse = chatClient.prompt()
                    .user(validationPrompt)
                    .call()
                    .content();

            log.debug("AI 内容验证响应已返回：length={}",
                    aiResponse != null ? aiResponse.length() : 0);

            Map<String, Object> validationData = readJsonResponse(aiResponse);
            if (!(validationData.get("isReflection") instanceof Boolean)) {
                throw new IllegalArgumentException("AI JSON内容验证缺少布尔判断");
            }
            result.put("isReflection", validationData.get("isReflection"));
            result.put("reason", validationData.get("reason"));
            return result;
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("AI内容验证失败", e);
        }
    }

    /**
     * 默认评分规则（当数据库未配置时使用）
     */
    private List<CounselorScoringRule> getDefaultScoringRules() {
        List<CounselorScoringRule> rules = new ArrayList<>();
        String[] categories = {"attitude", "ability", "report"};
        String[] labels = {"优秀", "良好", "中等", "及格", "不及格"};
        int[][] bounds = {{90, 100}, {80, 89}, {70, 79}, {60, 69}, {0, 59}};
        int[] weights = {30, 40, 30};
        for (int category = 0; category < categories.length; category++) {
            for (int level = 0; level < labels.length; level++) {
                CounselorScoringRule rule = new CounselorScoringRule();
                rule.setCategory(categories[category]);
                rule.setRuleCode(categories[category] + "_" + level);
                rule.setRuleName(labels[level]);
                rule.setWeight(weights[category]);
                rule.setMinScore(bounds[level][0]);
                rule.setMaxScore(bounds[level][1]);
                rule.setStatus(1);
                rules.add(rule);
            }
        }
        return rules;
    }
}
