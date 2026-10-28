package com.internship.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.config.DynamicChatClientFactory;
import com.internship.entity.AIModel;
import com.internship.entity.CategoryWeight;
import com.internship.entity.InternshipReflection;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.entity.KeywordLibrary;
import com.internship.entity.ScoringRule;
import com.internship.entity.SystemSettings;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.service.AIModelService;
import com.internship.service.CategoryWeightService;
import com.internship.service.InternshipReflectionService;
import com.internship.service.KeywordLibraryService;
import com.internship.service.ScoringRuleService;
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

@Slf4j
@Service
public class InternshipReflectionServiceImpl implements InternshipReflectionService {
    @Autowired
    private com.internship.service.ReflectionFeatureService reflectionFeature;

    @Autowired
    private InternshipReflectionMapper internshipReflectionMapper;

    @Autowired
    private DynamicChatClientFactory chatClientFactory;

    @Autowired
    private ScoringRuleService scoringRuleService;

    @Autowired
    private CategoryWeightService categoryWeightService;

    @Autowired
    private KeywordLibraryService keywordLibraryService;

    @Autowired
    private com.internship.service.SystemConfigService systemConfigService;

    @Autowired
    private com.internship.service.SystemSettingsService systemSettingsService;

    @Autowired
    private com.internship.service.InternshipCalendarService internshipCalendarService;

    @Autowired
    private AIModelService aiModelService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private static final String SYSTEM_PROMPT = "你是一位专业的实习指导教师，请仔细分析学生实习心得，根据内容质量给出客观、公正的评价和评分。";

    @Override
    public int insert(InternshipReflection internshipReflection) {
        if (internshipReflection.getCreateTime() == null) {
            internshipReflection.setCreateTime(new Date());
        }
        if (internshipReflection.getUpdateTime() == null) {
            internshipReflection.setUpdateTime(new Date());
        }
        if (internshipReflection.getDeleted() == null) {
            internshipReflection.setDeleted(0);
        }
        if (internshipReflection.getStatus() == null) {
            internshipReflection.setStatus(0);
        }
        return internshipReflectionMapper.insert(internshipReflection);
    }

    @Override
    public int updateById(InternshipReflection internshipReflection) {
        internshipReflection.setUpdateTime(new Date());
        return internshipReflectionMapper.updateById(internshipReflection);
    }

    @Override
    public int deleteById(Long id) {
        return internshipReflectionMapper.deleteById(id);
    }

    @Override
    public InternshipReflection findById(Long id) {
        return internshipReflectionMapper.findById(id);
    }

    @Override
    public InternshipReflection findByStudentId(Long studentId) {
        return internshipReflectionMapper.findByStudentId(studentId);
    }

    @Override
    public List<InternshipReflection> findAll() {
        return internshipReflectionMapper.findAll();
    }

    @Override
    public List<InternshipReflection> findByStatus(Integer status) {
        return internshipReflectionMapper.findByStatus(status);
    }

    @Override
    public List<InternshipReflection> list(Long studentId, String studentName, String studentUserId, Integer status) {
        return internshipReflectionMapper.list(studentId, studentName, studentUserId, status);
    }

    @Override
    public int countByStatus(Integer status) {
        return internshipReflectionMapper.countByStatus(status);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> analyzeReflection(Long id) {
        // Capture the row and its submission event together, then release the lock before either API call.
        Submission snapshot = transactionTemplate.execute(transaction -> {
            InternshipReflection reflection = internshipReflectionMapper.lockById(id);
            if (!submitted(reflection) || internshipReflectionMapper.hasManualEvaluation(id)) return null;
            return new Submission(reflection,
                    internshipReflectionMapper.latestSubmissionEventId(id, reflection.getStudentId()));
        });
        if (snapshot == null) return staleResult();

        Map<String, Object> result = analyzeReflectionContent(snapshot.reflection().getContent());
        if (!Boolean.TRUE.equals(result.get("success"))) return result;
        try {
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            boolean applied = Boolean.TRUE.equals(transactionTemplate.execute(transaction -> {
                InternshipReflection current = internshipReflectionMapper.lockById(id);
                InternshipReflection original = snapshot.reflection();
                if (!submitted(current) || internshipReflectionMapper.hasManualEvaluation(id)
                        || !Objects.equals(original.getStudentId(), current.getStudentId())
                        || !Objects.equals(original.getContent(), current.getContent())
                        || !Objects.equals(original.getSubmitTime(), current.getSubmitTime())
                        || !Objects.equals(original.getUpdateTime(), current.getUpdateTime())
                        || !Objects.equals(original.getStatus(), current.getStatus())
                        || !Objects.equals(snapshot.version(), internshipReflectionMapper.latestSubmissionEventId(
                                id, original.getStudentId()))) return false;
                current.setAiKeywords(data.get("keywords").toString());
                current.setAiAnalysis((String) data.get("comment"));
                current.setAiImprovements(data.get("improvements").toString());
                // Preserve the legacy admin score contract, distinct from the counselor report's grade.
                current.setAiScore(new BigDecimal(data.get("score").toString()));
                Date now = new Date();
                current.setAiAnalysisTime(now);
                current.setUpdateTime(now);
                current.setStatus(1);
                if (internshipReflectionMapper.updateById(current) != 1) {
                    throw new IllegalStateException("保存AI分析结果失败");
                }
                return true;
            }));
            return applied ? result : staleResult();
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("保存管理员AI分析失败：reflectionId={}, type={}", id, e.getClass().getSimpleName());
            return Map.of("success", false, "message", "保存AI分析结果失败，请稍后重试");
        }
    }

    private record Submission(InternshipReflection reflection, Long version) {}

    private static boolean submitted(InternshipReflection reflection) {
        return reflection != null && !Integer.valueOf(1).equals(reflection.getDeleted())
                && reflection.getStudentId() != null && reflection.getRemark() == null
                && reflection.getSubmitTime() != null && reflection.getContent() != null
                && !reflection.getContent().isBlank()
                && (Integer.valueOf(0).equals(reflection.getStatus()) || Integer.valueOf(1).equals(reflection.getStatus()));
    }

    private static Map<String, Object> staleResult() {
        return Map.of("success", false, "stale", true,
                "message", "心得已撤回、重交或进入批阅，本次分析结果已忽略");
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> analyzeReflectionContent(String content) {
        return analyzeReflectionContent(content, null);
    }

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Map<String, Object> analyzeReflectionContent(String content, String modelCode) {
        if (!reflectionFeature.enabled()) return Map.of("success", false, "retryable", false,
                "message", com.internship.service.ReflectionFeatureService.DISABLED_MESSAGE);
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (content == null || content.isBlank()) throw new IllegalArgumentException("实习心得内容不能为空");
            ChatClient selectedClient = getChatClientByModel(modelCode);
            Map<String, Object> validationResult = validateReflectionContent(content, selectedClient);
            
            log.info("验证结果: isReflection={}, reason={}", 
                validationResult.get("isReflection"), validationResult.get("reason"));
            
            boolean isReflection = Boolean.TRUE.equals(validationResult.get("isReflection"));
            
            log.info("解析后的isReflection值: {}", isReflection);
            
            if (!isReflection) {
                result.put("success", false);
                result.put("isNotReflection", true);
                result.put("message", "该文件内容不是实习心得，请提交与实习心得相关的文件");
                log.info("返回非实习心得错误消息");
                return result;
            }
            
            List<CategoryWeight> weights = categoryWeightService.findActive();
            String prompt = buildAnalysisPrompt(content, weights);
            
            String aiResponse = selectedClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("AI 心得分析响应已返回：model={}, length={}",
                    modelCode != null ? modelCode : "deepseek-chat",
                    aiResponse != null ? aiResponse.length() : 0);

            Map<String, Object> analysisResult = parseAIResponse(aiResponse);
            validateAnalysisResult(analysisResult);
            calculateWeightedScore(analysisResult, weights);
            
            validateAndMatchKeywords(analysisResult);
            
            result.put("success", true);
            result.put("data", analysisResult);
            
            return result;
        } catch (AgentException e) {
            throw e;
        } catch (Exception e) {
            log.warn("管理员AI分析失败：type={}", e.getClass().getSimpleName());
            result.put("success", false);
            result.put("message", "AI分析暂未完成，请稍后重试");
            return result;
        }
    }

    private Map<String, Object> validateReflectionContent(String content, ChatClient selectedClient) {
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

        String aiResponse = selectedClient.prompt()
                .user(validationPrompt)
                .call()
                .content();

        log.debug("AI 内容验证响应已返回：length={}",
                aiResponse != null ? aiResponse.length() : 0);

        Map<String, Object> validationData = parseAIResponse(aiResponse);

        if (!(validationData.get("isReflection") instanceof Boolean)) {
            throw new IllegalArgumentException("AI JSON缺少有效的实习心得判断");
        }
        return validationData;
    }

    private ChatClient getChatClientByModel(String modelCode) {
        String actualModelCode;
        if (modelCode == null || modelCode.trim().isEmpty()) {
            // 使用管理员设置的默认模型
            try {
                AIModel defaultModel = aiModelService.findDefaultModel();
                if (defaultModel != null) {
                    actualModelCode = defaultModel.getModelCode();
                    log.info("使用管理员默认模型: {}", actualModelCode);
                } else {
                    actualModelCode = "deepseek-flash";
                }
            } catch (Exception e) {
                log.warn("获取默认模型失败，使用deepseek-flash: {}", e.getMessage());
                actualModelCode = "deepseek-flash";
            }
        } else if (modelCode.equals("deepseek-reasoner")) {
            actualModelCode = "deepseek-reasoner";
        } else if (modelCode.equals("deepseek-v4-flash") || modelCode.equals("deepseek-flash")) {
            // deepseek-v4-flash 已下线，统一使用 v4.1 (deepseek-flash)，旧配置值作为别名兼容
            actualModelCode = "deepseek-flash";
        } else if (modelCode.equals("deepseek-v4-pro")) {
            actualModelCode = "deepseek-v4-pro";
        } else {
            actualModelCode = "deepseek-flash";
        }
        return chatClientFactory.createChatClient(actualModelCode, SYSTEM_PROMPT);
    }

    private String buildAnalysisPrompt(String content, List<CategoryWeight> categoryWeights) {
        List<ScoringRule> scoringRules = scoringRuleService.findByScenario("实习心得分析");
        
        String scoringCriteria = buildScoringCriteria(scoringRules, categoryWeights);
        String weightInfo = buildWeightInfo(categoryWeights);
        String aspectsExample = buildAspectsExample(categoryWeights);
        
        List<KeywordLibrary> keywords = keywordLibraryService.findByUsageType("internship");
        String keywordHints = buildKeywordHints(keywords);
        
        Integer keywordMatchCount = systemConfigService.getConfigValueAsInt("keyword_match_count");
        if (keywordMatchCount == null || keywordMatchCount < 3) {
            keywordMatchCount = 8;
        }
        
        StringBuilder keywordsExample = new StringBuilder();
        keywordsExample.append("\"关键词1\"");
        for (int i = 2; i <= keywordMatchCount; i++) {
            keywordsExample.append(", \"关键词").append(i).append("\"");
        }
        
        // 构建 aspects JSON 字符串，确保 AI 返回正确维度的评分
        String aspectsJson = buildAspectsJson(categoryWeights);

        // 获取维度数量，用于提示 AI
        int dimensionCount = (categoryWeights != null) ? categoryWeights.size() : 0;

        // 构建提示词，明确指定只返回配置的维度
        String promptTemplate = """
            你是一位专业的实习指导教师，请仔细分析以下学生实习心得，根据内容质量给出客观、公正的评价和评分。

            实习心得内容：
            %1$s

            %2$s

            请按以下JSON格式返回分析结果（不要包含其他文字，只返回JSON）：
            {
                "keywords": [...],
                "summary": "实习心得的简要总结（50-100字）",
                "highlights": ["亮点1", "亮点2", "亮点3"],
                "improvements": ["改进建议1", "改进建议2"],
                "score": 85.5,
                "rating": "优秀",
                "aspects": %3$s,
                "weightedScore": 85.5,
                "comment": "根据实习心得内容生成的详细评语（100-200字），应包含对实习表现、收获、不足等方面的具体评价"
            }

            评分标准（请严格按照以下标准评分，不要给出固定分数）：
            %4$s

            类别权重配置：
            %5$s

            总分计算：
            1. 先计算各项能力维度的平均分（aspects中的各项平均值）
            2. 再根据类别权重计算加权总分（weightedScore）
            3. 加权总分计算公式：Σ(维度分数 × 权重) / 总权重

            评级标准：90分以上为优秀，80-89分为良好，70-79分为中等，60-69分为及格，60分以下为不及格

            重要提示：
            1. aspects 中的维度必须严格按照以上配置，只返回 %6$d 个维度，不要添加其他维度
            2. 请根据实习心得的实际内容进行评分，不要给出固定分数
            3. 仔细阅读实习心得的每个部分，全面评估学生的表现
            4. 对于内容丰富、反思深刻、表现突出的心得，应给予高分（90分以上）
            5. 对于内容简单、反思浅显、表现一般的心得，应给予中等分数（70-80分）
            6. 对于内容空洞、缺乏反思、表现较差的心得，应给予低分（60-70分以下）
            7. 评分要客观公正，体现不同质量实习心得的差异
            8. 必须严格按照权重配置计算加权总分（weightedScore），确保权重高的维度对总分影响更大
            """;

        return String.format(promptTemplate,
            content,
            keywordHints,
            aspectsJson,
            scoringCriteria,
            weightInfo,
            dimensionCount);
    }

    private String buildScoringCriteria(List<ScoringRule> scoringRules, List<CategoryWeight> categoryWeights) {
        if (scoringRules == null || scoringRules.isEmpty()) {
            return "请根据实习心得内容进行客观评分";
        }

        Map<String, List<ScoringRule>> rulesByCategory = new LinkedHashMap<>();

        for (ScoringRule rule : scoringRules) {
            if (rule.getCategory() != null && !rule.getCategory().trim().isEmpty()) {
                rulesByCategory.computeIfAbsent(rule.getCategory(), k -> new ArrayList<>()).add(rule);
            }
        }

        Map<String, String> categoryNames = new HashMap<>();
        if (categoryWeights != null && !categoryWeights.isEmpty()) {
            for (CategoryWeight cw : categoryWeights) {
                if (cw.getCategoryName() != null && !cw.getCategoryName().trim().isEmpty()) {
                    categoryNames.put(cw.getCategoryCode(), cw.getCategoryName());
                }
            }
        }

        StringBuilder criteria = new StringBuilder();

        for (Map.Entry<String, List<ScoringRule>> entry : rulesByCategory.entrySet()) {
            String category = entry.getKey();
            List<ScoringRule> rules = entry.getValue();

            if (rules.isEmpty()) {
                continue;
            }

            String displayName = categoryNames.getOrDefault(category, category);
            criteria.append(displayName).append("（").append(category).append("）：\n");

            for (ScoringRule rule : rules) {
                if (rule.getStatus() != null && rule.getStatus() == 1) {
                    criteria.append("- ")
                            .append(rule.getMinScore()).append("-").append(rule.getMaxScore())
                            .append("分：").append(rule.getDescription()).append("\n");
                }
            }
            criteria.append("\n");
        }

        return criteria.toString();
    }

    private String buildWeightInfo(List<CategoryWeight> categoryWeights) {
        if (categoryWeights == null || categoryWeights.isEmpty()) {
            return "暂无权重配置";
        }

        StringBuilder weightInfo = new StringBuilder();

        int totalWeight = 0;
        for (CategoryWeight cw : categoryWeights) {
            totalWeight += cw.getWeight();
        }

        weightInfo.append("总权重：").append(totalWeight).append("\n\n");
        weightInfo.append("各维度权重：\n");

        for (CategoryWeight cw : categoryWeights) {
            String name = cw.getCategoryName() != null ? cw.getCategoryName() : cw.getCategoryCode();
            weightInfo.append("- ").append(name)
                    .append("（").append(cw.getCategoryCode()).append("）")
                    .append("：").append(cw.getWeight()).append("\n");
        }

        return weightInfo.toString();
    }

    private String buildAspectsExample(List<CategoryWeight> categoryWeights) {
        if (categoryWeights == null || categoryWeights.isEmpty()) {
            return "";
        }

        StringBuilder example = new StringBuilder();
        example.append("\n                    ");

        int index = 0;
        for (CategoryWeight cw : categoryWeights) {
            if (index > 0) {
                example.append(",\n                    ");
            }
            // 返回等级名称（如"良好"），与教师端逻辑一致
            example.append("\"").append(cw.getCategoryCode()).append("\": \"良好\"");
            index++;
        }

        return example.toString();
    }

    /**
     * 构建 aspects JSON 字符串，确保 AI 返回正确维度的评分
     * 修复：之前 aspectsExample 没有正确插入到 "aspects": {...} 占位符中
     */
    private String buildAspectsJson(List<CategoryWeight> categoryWeights) {
        if (categoryWeights == null || categoryWeights.isEmpty()) {
            return "{}";
        }

        StringBuilder json = new StringBuilder();
        json.append("{");

        int index = 0;
        for (CategoryWeight cw : categoryWeights) {
            if (index > 0) {
                json.append(", ");
            }
            // 使用 categoryCode 作为 key，示例值为 85
            json.append("\"").append(cw.getCategoryCode()).append("\": 85");
            index++;
        }

        json.append("}");
        return json.toString();
    }

    private String buildKeywordHints(List<KeywordLibrary> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }

        Integer keywordMatchCount = systemConfigService.getConfigValueAsInt("keyword_match_count");
        if (keywordMatchCount == null || keywordMatchCount < 3) {
            keywordMatchCount = 8;
        }

        StringBuilder hints = new StringBuilder();
        hints.append("参考关键词库（以下关键词可作为参考，但请根据实习心得的实际内容自由提取").append(keywordMatchCount).append("个最相关的关键词，不必局限于关键词库）：\n\n");

        int count = 0;
        for (KeywordLibrary kw : keywords) {
            if (kw.getStatus() != null && kw.getStatus() == 1) {
                hints.append("- ").append(kw.getKeyword());
                hints.append("\n");
                count++;
                
                if (count >= 20) {
                    break;
                }
            }
        }

        hints.append("\n注意：\n");
        hints.append("1. 请根据实习心得的实际内容，自由提取").append(keywordMatchCount).append("个最相关的关键词\n");
        hints.append("2. 关键词库仅供参考，可以提取关键词库中的词，也可以提取内容中出现的其他相关关键词\n");
        hints.append("3. 关键词应该简洁、准确，能够体现实习心得的核心内容\n");

        return hints.toString();
    }

    private void validateAndMatchKeywords(Map<String, Object> analysisResult) {
        try {
            Object keywordsObj = analysisResult.get("keywords");
            if (keywordsObj == null) {
                log.warn("分析结果中缺少keywords字段");
                return;
            }

            List<String> aiKeywords = new ArrayList<>();
            if (keywordsObj instanceof List) {
                for (Object item : (List<?>) keywordsObj) {
                    if (item != null) {
                        aiKeywords.add(item.toString());
                    }
                }
            }

            List<KeywordLibrary> allKeywords = keywordLibraryService.findByUsageType("internship");
            Set<String> keywordSet = new HashSet<>();

            for (KeywordLibrary kw : allKeywords) {
                if (kw.getStatus() != null && kw.getStatus() == 1) {
                    String keyword = kw.getKeyword().trim();
                    keywordSet.add(keyword);
                }
            }

            List<String> matchedKeywords = new ArrayList<>();
            List<String> unmatchedKeywords = new ArrayList<>();

            for (String aiKeyword : aiKeywords) {
                String trimmedKeyword = aiKeyword.trim();
                if (keywordSet.contains(trimmedKeyword)) {
                    matchedKeywords.add(trimmedKeyword);
                } else {
                    unmatchedKeywords.add(trimmedKeyword);
                }
            }

            double matchRate = 0.0;
            if (!aiKeywords.isEmpty()) {
                matchRate = (double) matchedKeywords.size() / aiKeywords.size() * 100;
            }

            Map<String, Object> keywordStats = new HashMap<>();
            keywordStats.put("totalKeywords", aiKeywords.size());
            keywordStats.put("matchedKeywords", matchedKeywords);
            keywordStats.put("matchedCount", matchedKeywords.size());
            keywordStats.put("unmatchedKeywords", unmatchedKeywords);
            keywordStats.put("unmatchedCount", unmatchedKeywords.size());
            keywordStats.put("matchRate", Math.round(matchRate * 10.0) / 10.0);

            analysisResult.put("keywordStats", keywordStats);

            log.info("关键词匹配统计: 总数={}, 匹配数={}, 未匹配数={}, 匹配率={}%", 
                    aiKeywords.size(), matchedKeywords.size(), unmatchedKeywords.size(), matchRate);

        } catch (Exception e) {
            log.error("关键词匹配验证失败: {}", e.getMessage(), e);
        }
    }

    private void validateAnalysisResult(Map<String, Object> data) {
        validateTextList(data.get("keywords"), "keywords", true);
        validateTextList(data.get("improvements"), "improvements", false);
        if (!(data.get("comment") instanceof String comment) || comment.isBlank()
                || !(data.get("aspects") instanceof Map<?, ?> aspects)) {
            throw new IllegalArgumentException("AI JSON缺少有效的分析及评分字段");
        }
        score(data.get("score"));
        if (data.containsKey("weightedScore")) score(data.get("weightedScore"));
        for (Map.Entry<?, ?> aspect : aspects.entrySet()) {
            if (!(aspect.getKey() instanceof String name) || name.isBlank()) {
                throw new IllegalArgumentException("AI JSON评分维度无效");
            }
            score(aspect.getValue());
        }
    }

    private static void validateTextList(Object value, String field, boolean requireNonempty) {
        if (!(value instanceof List<?> list) || (requireNonempty && list.isEmpty())
                || list.stream().anyMatch(item -> !(item instanceof String text) || text.isBlank())) {
            throw new IllegalArgumentException("AI JSON缺少有效的" + field + "字段");
        }
    }

    private static double score(Object value) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
                || number.doubleValue() < 0 || number.doubleValue() > 100) {
            throw new IllegalArgumentException("AI JSON评分必须是0到100之间的数字");
        }
        return number.doubleValue();
    }

    private void calculateWeightedScore(Map<String, Object> analysisResult, List<CategoryWeight> categoryWeights) {
        Map<String, Object> aspects = (Map<String, Object>) analysisResult.get("aspects");
        if (categoryWeights == null || categoryWeights.isEmpty()) {
            return;
        }

        // AI可能使用的别名映射（处理AI自由发挥时返回的近似维度名）
        Map<String, String> aliasMap = new HashMap<>();
        aliasMap.put("innovationAbility", "innovation_ability");
        aliasMap.put("innovation_ability", "innovation_ability");
        aliasMap.put("learning_reflection", "learningAbility");
        aliasMap.put("learningReflection", "learningAbility");
        aliasMap.put("learning", "learningAbility");
        aliasMap.put("communicationCooperation", "communication");
        aliasMap.put("communication_cooperation", "communication");
        aliasMap.put("team_cooperation", "teamwork");
        aliasMap.put("teamworkAbility", "teamwork");
        aliasMap.put("taskExecution", "task_execution");
        aliasMap.put("professionalAwareness", "professional_awareness");
        aliasMap.put("professionalism", "professional_awareness");

        double weightedSum = 0.0;
        int totalWeight = 0;

        for (CategoryWeight cw : categoryWeights) {
            String categoryCode = cw.getCategoryCode();
            Integer weight = cw.getWeight();

            if (weight == null || weight <= 0) {
                continue;
            }

            Object scoreObj = aspects.get(categoryCode);

            // 如果精确匹配失败，尝试别名映射
            if (scoreObj == null && aliasMap.containsKey(categoryCode)) {
                String aliasedCode = aliasMap.get(categoryCode);
                scoreObj = aspects.get(aliasedCode);
                if (scoreObj != null) {
                    log.info("通过别名映射找到评分：{} -> {} = {}", categoryCode, aliasedCode, scoreObj);
                }
            }

            // 如果仍然失败，尝试在别名映射中查找作为key的情况
            if (scoreObj == null) {
                for (Map.Entry<String, String> alias : aliasMap.entrySet()) {
                    if (alias.getValue().equals(categoryCode) && aspects.containsKey(alias.getKey())) {
                        scoreObj = aspects.get(alias.getKey());
                        if (scoreObj != null) {
                            log.info("通过反向别名映射找到评分：{} <- {} = {}", categoryCode, alias.getKey(), scoreObj);
                            break;
                        }
                    }
                }
            }

            if (scoreObj == null) {
                throw new IllegalArgumentException("AI JSON缺少评分维度：" + categoryCode);
            }
            weightedSum += score(scoreObj) * weight;
            totalWeight += weight;
        }

        if (totalWeight > 0) {
            if (totalWeight != 100) {
                log.warn("总权重为{}，不为100，将进行归一化处理", totalWeight);
            }

            double weightedScore = weightedSum / totalWeight;
            BigDecimal roundedScore = new BigDecimal(weightedScore)
                .setScale(1, RoundingMode.HALF_UP);
            analysisResult.put("weightedScore", roundedScore.doubleValue());
            log.info("加权分数计算完成：加权总分 = {}，总权重 = {}", roundedScore, totalWeight);
        } else {
            throw new IllegalArgumentException("系统评分权重无效");
        }
    }

    private Map<String, Object> parseAIResponse(String aiResponse) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String jsonContent = extractJSON(aiResponse);
            
            if (jsonContent == null) {
                throw new RuntimeException("无法从AI响应中提取JSON数据");
            }

            Map<String, Object> parsedData = objectMapper.readValue(jsonContent, Map.class);
            
            result.putAll(parsedData);
            
            return result;
        } catch (JsonProcessingException e) {
            log.error("解析AI响应JSON失败: {}", e.getMessage());
            throw new RuntimeException("解析AI响应失败", e);
        }
    }

    private String extractJSON(String response) {
        if (response == null || response.isBlank()) return null;
        String cleanedResponse = response.trim();
        if (cleanedResponse.startsWith("```")) {
            if (!cleanedResponse.matches("(?is)^```(?:json)?\\s*\\{.*}\\s*```$")) return null;
            cleanedResponse = cleanedResponse.replaceFirst("(?i)^```(?:json)?\\s*", "");
            cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3).trim();
        }
        return cleanedResponse.startsWith("{") && cleanedResponse.endsWith("}") ? cleanedResponse : null;
    }

    @Override
    public Integer calculatePeriodNumber(Date submitTime) {
        if (submitTime == null) {
            return null;
        }

        try {
            InternshipCalendar calendar = internshipCalendarService.getCalendar();
            if (calendar == null || isBlank(calendar.getStartDate())
                    || isBlank(calendar.getEndDate())
                    || calendar.getReportCycle() == null || calendar.getReportCycle() <= 0) {
                return null;
            }

            Date startDate = parseDate(calendar.getStartDate(), false);
            Date endDate = parseDate(calendar.getEndDate(), true);
            if (submitTime.before(startDate) || submitTime.after(endDate)) {
                return null;
            }
            return calculatePeriodFromStart(submitTime, startDate, calendar.getReportCycle(), "全局实习日历");
        } catch (Exception e) {
            log.error("计算全局实习心得期数失败: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public int withdrawSubmitted(Long id, Long studentId, java.util.Date updateTime) {
        return internshipReflectionMapper.withdrawSubmitted(id, studentId, updateTime);
    }

    private Date parseDate(String value, boolean endOfDay) throws java.text.ParseException {
        Date date = new java.text.SimpleDateFormat("yyyy-MM-dd").parse(value);
        if (endOfDay) {
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(date);
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            return calendar.getTime();
        }
        return date;
    }

    private Integer calculatePeriodFromStart(Date submitTime, Date startDate, Integer cycle, String scope) {
        long diffDays = Math.max(0, (submitTime.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24));
        int periodNumber = (int) (diffDays / cycle) + 1;
        log.info("计算{}心得期数: 提交时间={}, 开始时间={}, 周期={}天, 期数={}",
                scope, submitTime, startDate, cycle, periodNumber);
        return periodNumber;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    @Override
    public boolean existsByStudentIdAndPeriodNumber(Long studentId, Integer periodNumber) {
        if (studentId == null || periodNumber == null) {
            return false;
        }
        try {
            return internshipReflectionMapper.existsByStudentIdAndPeriodNumber(studentId, periodNumber);
        } catch (Exception e) {
            log.error("检查阶段提交失败: studentId={}, periodNumber={}, error={}", studentId, periodNumber, e.getMessage());
            return false;
        }
    }
}
