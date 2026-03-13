package com.gdmu.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.config.DynamicChatClientFactory;
import com.gdmu.entity.CategoryWeight;
import com.gdmu.entity.InternshipReflection;
import com.gdmu.entity.KeywordLibrary;
import com.gdmu.entity.ScoringRule;
import com.gdmu.mapper.InternshipReflectionMapper;
import com.gdmu.service.CategoryWeightService;
import com.gdmu.service.InternshipReflectionService;
import com.gdmu.service.KeywordLibraryService;
import com.gdmu.service.ScoringRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
public class InternshipReflectionServiceImpl implements InternshipReflectionService {

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
    private com.gdmu.service.SystemConfigService systemConfigService;

    private final ObjectMapper objectMapper = new ObjectMapper();

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
    @Transactional
    public Map<String, Object> analyzeReflection(Long id) {
        InternshipReflection reflection = findById(id);
        if (reflection == null) {
            throw new RuntimeException("实习心得不存在");
        }

        return analyzeReflectionContent(reflection.getContent());
    }

    @Override
    public Map<String, Object> analyzeReflectionContent(String content) {
        return analyzeReflectionContent(content, null);
    }

    @Override
    public Map<String, Object> analyzeReflectionContent(String content, String modelCode) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Map<String, Object> validationResult = validateReflectionContent(content, modelCode);
            
            log.info("验证结果: isReflection={}, reason={}", 
                validationResult.get("isReflection"), validationResult.get("reason"));
            
            Object isReflectionObj = validationResult.get("isReflection");
            boolean isReflection = true;
            
            if (isReflectionObj instanceof Boolean) {
                isReflection = (Boolean) isReflectionObj;
            } else if (isReflectionObj instanceof String) {
                isReflection = Boolean.parseBoolean((String) isReflectionObj);
            }
            
            log.info("解析后的isReflection值: {}", isReflection);
            
            if (!isReflection) {
                result.put("success", false);
                result.put("isNotReflection", true);
                result.put("message", "该文件内容不是实习心得，请提交与实习心得相关的文件");
                log.info("返回非实习心得错误消息");
                return result;
            }
            
            String prompt = buildAnalysisPrompt(content);
            
            ChatClient selectedClient = getChatClientByModel(modelCode);
            
            String aiResponse = selectedClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.info("AI分析响应（模型: {}）: {}", modelCode != null ? modelCode : "deepseek-chat", aiResponse);

            Map<String, Object> analysisResult = parseAIResponse(aiResponse);
            
            calculateWeightedScore(analysisResult);
            
            validateAndMatchKeywords(analysisResult);
            
            result.put("success", true);
            result.put("data", analysisResult);
            
            return result;
        } catch (Exception e) {
            log.error("AI分析失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "AI分析失败: " + e.getMessage());
            return result;
        }
    }

    private Map<String, Object> validateReflectionContent(String content, String modelCode) {
        Map<String, Object> result = new HashMap<>();
        
        try {
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
            
            ChatClient selectedClient = getChatClientByModel(modelCode);
            
            String aiResponse = selectedClient.prompt()
                    .user(validationPrompt)
                    .call()
                    .content();

            log.info("AI内容验证响应: {}", aiResponse);

            Map<String, Object> validationData = parseAIResponse(aiResponse);
            
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
            log.error("内容验证失败: {}", e.getMessage(), e);
            result.put("isReflection", true);
            result.put("reason", "验证失败，默认认为是实习心得");
            return result;
        }
    }

    private ChatClient getChatClientByModel(String modelCode) {
        String actualModelCode = (modelCode != null && modelCode.equals("deepseek-reasoner")) ? "deepseek-reasoner" : "deepseek-chat";
        return chatClientFactory.createChatClient(actualModelCode, SYSTEM_PROMPT);
    }

    private String buildAnalysisPrompt(String content) {
        List<ScoringRule> scoringRules = scoringRuleService.findByScenario("实习心得分析");
        
        List<CategoryWeight> categoryWeights = categoryWeightService.findActive();
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
        
        return """
            你是一位专业的实习指导教师，请仔细分析以下学生实习心得，根据内容质量给出客观、公正的评价和评分。
            
            实习心得内容：
            %s
            
            %s
            
            请按以下JSON格式返回分析结果（不要包含其他文字，只返回JSON）：
            {
                "keywords": [%s],
                "summary": "实习心得的简要总结（50-100字）",
                "highlights": ["亮点1", "亮点2", "亮点3"],
                "improvements": ["改进建议1", "改进建议2"],
                "score": 85.5,
                "rating": "优秀",
                "aspects": {%s},
                "weightedScore": 85.5,
                "comment": "综合评语（100-200字）"
            }
            
            评分标准（请严格按照以下标准评分，不要给出固定分数）：
            %s
            
            类别权重配置：
            %s
            
            总分计算：
            1. 先计算各项能力维度的平均分（aspects中的各项平均值）
            2. 再根据类别权重计算加权总分（weightedScore）
            3. 加权总分计算公式：Σ(维度分数 × 权重) / 总权重
            
            评级标准：90分以上为优秀，80-89分为良好，70-79分为中等，60-69分为及格，60分以下为不及格
            
            重要提示：
            1. 请根据实习心得的实际内容进行评分，不要给出固定分数
            2. 仔细阅读实习心得的每个部分，全面评估学生的表现
            3. 对于内容丰富、反思深刻、表现突出的心得，应给予高分（90分以上）
            4. 对于内容简单、反思浅显、表现一般的心得，应给予中等分数（70-80分）
            5. 对于内容空洞、缺乏反思、表现较差的心得，应给予低分（60-70分以下）
            6. 评分要客观公正，体现不同质量实习心得的差异
            7. 必须严格按照权重配置计算加权总分（weightedScore），确保权重高的维度对总分影响更大
            """.formatted(content, keywordHints, keywordsExample.toString(), aspectsExample, scoringCriteria, weightInfo);
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
            String name = cw.getCategoryName() != null ? cw.getCategoryName() : cw.getCategoryCode();
            example.append("\"").append(cw.getCategoryCode()).append("\": 85");
            index++;
        }
        
        return example.toString();
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

    private void calculateWeightedScore(Map<String, Object> analysisResult) {
        try {
            Map<String, Object> aspects = (Map<String, Object>) analysisResult.get("aspects");
            if (aspects == null) {
                log.warn("AI响应中缺少aspects字段，无法计算加权分数");
                return;
            }

            List<CategoryWeight> categoryWeights = categoryWeightService.findActive();
            if (categoryWeights == null || categoryWeights.isEmpty()) {
                log.warn("未找到类别权重配置，使用简单平均分");
                return;
            }

            double weightedSum = 0.0;
            int totalWeight = 0;

            for (CategoryWeight cw : categoryWeights) {
                String categoryCode = cw.getCategoryCode();
                Integer weight = cw.getWeight();
                
                if (weight == null || weight <= 0) {
                    continue;
                }

                Object scoreObj = aspects.get(categoryCode);
                if (scoreObj != null) {
                    double score = 0.0;
                    if (scoreObj instanceof Integer) {
                        score = ((Integer) scoreObj).doubleValue();
                    } else if (scoreObj instanceof Double) {
                        score = (Double) scoreObj;
                    } else if (scoreObj instanceof String) {
                        score = Double.parseDouble((String) scoreObj);
                    }

                    weightedSum += score * weight;
                    totalWeight += weight;
                }
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
                log.warn("总权重为0，无法计算加权分数");
            }
        } catch (Exception e) {
            log.error("计算加权分数失败: {}", e.getMessage(), e);
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
        String cleanedResponse = response.trim();
        
        cleanedResponse = cleanedResponse.replaceAll("```json", "");
        cleanedResponse = cleanedResponse.replaceAll("```JSON", "");
        cleanedResponse = cleanedResponse.replaceAll("```", "");
        
        int start = cleanedResponse.indexOf("{");
        int end = cleanedResponse.lastIndexOf("}");
        
        if (start != -1 && end != -1 && end > start) {
            return cleanedResponse.substring(start, end + 1);
        }
        
        return null;
    }
}
