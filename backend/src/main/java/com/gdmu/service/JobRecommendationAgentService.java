package com.gdmu.service;

import com.gdmu.config.DynamicChatClientFactory;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.Position;
import com.gdmu.entity.StudentJobApplication;
import com.gdmu.entity.StudentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 岗位推荐AI代理服务
 * 负责为学生推荐合适的实习岗位
 */
@Service
public class JobRecommendationAgentService {
    private static final Logger log = LoggerFactory.getLogger(JobRecommendationAgentService.class);

    private final PositionService positionService;
    private final StudentUserService studentUserService;
    private final PositionFavoriteService positionFavoriteService;
    private final StudentJobApplicationService studentJobApplicationService;
    private final CompanyUserService companyUserService;
    private final DynamicChatClientFactory chatClientFactory;

    private static final String SYSTEM_PROMPT = "你是实习管理系统的学生端AI助手，负责为学生推荐合适的实习岗位。";

    @Autowired
    public JobRecommendationAgentService(
            PositionService positionService,
            StudentUserService studentUserService,
            PositionFavoriteService positionFavoriteService,
            StudentJobApplicationService studentJobApplicationService,
            CompanyUserService companyUserService,
            DynamicChatClientFactory chatClientFactory) {
        this.positionService = positionService;
        this.studentUserService = studentUserService;
        this.positionFavoriteService = positionFavoriteService;
        this.studentJobApplicationService = studentJobApplicationService;
        this.companyUserService = companyUserService;
        this.chatClientFactory = chatClientFactory;
    }

    /**
     * 处理岗位推荐请求
     * @param query 用户查询文本
     * @param studentUserId 学生用户名（如s001）
     * @param model 使用的模型名称
     * @return 推荐结果
     */
    public Map<String, Object> processJobRecommendation(String query, String studentUserId, String model) {
        log.info("开始处理岗位推荐: query={}, studentUserId={}", query, studentUserId);

        try {
            // 1. 获取学生信息
            StudentUser student = studentUserService.findByStudentId(studentUserId);
            if (student == null) {
                log.warn("未找到学生信息, studentUserId={}", studentUserId);
                return buildErrorResult("未找到学生信息");
            }
            log.info("学生信息: 专业={}, 年级={}", student.getMajor(), student.getGrade());

            // 2. 获取所有active岗位
            List<Position> allActivePositions = positionService.findAll().stream()
                    .filter(p -> "active".equals(p.getStatus()))
                    .collect(Collectors.toList());
            log.info("系统中共有 {} 个active岗位", allActivePositions.size());

            // 3. 让AI分析哪些岗位适合该学生
            List<Long> matchedPositionIds = analyzeAndMatchPositions(query, student, allActivePositions, model);

            // 4. 根据ID获取匹配的岗位，并保持AI返回的顺序（按匹配度排序）
            List<Position> matchedPositions;
            if (matchedPositionIds.isEmpty()) {
                matchedPositions = Collections.emptyList();
                log.info("AI未找到匹配的岗位");
            } else {
                // 构建ID到岗位的映射
                Map<Long, Position> positionMap = positionService.findByIds(matchedPositionIds).stream()
                        .filter(p -> "active".equals(p.getStatus()))
                        .collect(Collectors.toMap(Position::getId, p -> p));

                // 按照 matchedPositionIds 的顺序构建岗位列表，确保按匹配度排序
                matchedPositions = matchedPositionIds.stream()
                        .map(positionMap::get)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());

                log.info("按AI匹配度排序后的岗位ID顺序: {}", matchedPositions.stream().map(Position::getId).collect(Collectors.toList()));
            }

            // 5. 获取学生的收藏和投递状态（使用学生的内部ID）
            Set<Long> favoritePositionIds = getFavoritePositionIds(student.getId());
            Set<Long> appliedPositionIds = getAppliedPositionIds(student.getId());

            // 6. 构建推荐结果
            List<Map<String, Object>> recommendedJobs = buildRecommendedJobs(
                    matchedPositions, student, favoritePositionIds, appliedPositionIds);

            // 7. 构建最终结果
            Map<String, Object> result = new HashMap<>();
            result.put("type", "job_recommendation");
            result.put("total", recommendedJobs.size());
            result.put("positions", recommendedJobs);
            result.put("message", String.format("为您找到%d个匹配的岗位", recommendedJobs.size()));

            log.info("岗位推荐完成: 找到{}个匹配岗位", recommendedJobs.size());
            return result;

        } catch (Exception e) {
            log.error("处理岗位推荐异常", e);
            return buildErrorResult("推荐服务暂时不可用: " + e.getMessage());
        }
    }

    /**
     * 让AI分析学生的专业特点，并从岗位列表中匹配适合的岗位
     * @param query 用户查询
     * @param student 学生信息
     * @param positions 所有可用岗位
     * @param model 使用的模型
     * @return 匹配的岗位ID列表
     */
    private List<Long> analyzeAndMatchPositions(String query, StudentUser student, List<Position> positions, String model) {
        try {
            // 构建岗位列表信息
            StringBuilder positionsInfo = new StringBuilder();
            for (Position p : positions) {
                positionsInfo.append(String.format("ID:%d|名称:%s|要求:%s|描述:%s\n",
                        p.getId(),
                        p.getPositionName(),
                        p.getRequirements() != null ? p.getRequirements() : "",
                        p.getDescription() != null ? p.getDescription() : ""));
            }

            String systemPrompt = buildPositionMatchingPrompt();
            String userPrompt = buildPositionMatchingUserPrompt(query, student, positionsInfo.toString());

            ChatClient client = getChatClientByModel(model);
            Prompt prompt = new Prompt(List.of(
                    new SystemMessage(systemPrompt),
                    new UserMessage(userPrompt)
            ));

            String aiResponse = client.prompt(prompt).call().content();
            log.info("AI岗位匹配结果:\n{}", aiResponse);

            // 解析AI返回的岗位ID列表
            return parsePositionIdsFromResponse(aiResponse);

        } catch (Exception e) {
            log.error("AI岗位匹配异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 构建岗位匹配的提示词
     */
    private String buildPositionMatchingPrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是实习岗位推荐专家，负责分析学生的专业背景并匹配适合的岗位。\n\n");
        sb.append("你的任务是：\n");
        sb.append("1. 分析学生的专业、年级等背景信息\n");
        sb.append("2. 根据岗位的名称、要求、描述判断是否适合该学生\n");
        sb.append("3. 计算机相关专业的学生适合大多数技术开发类岗位\n");
        sb.append("4. 即使岗位名称不直接包含\"计算机\"三个字，只要是技术开发类岗位都适合\n\n");
        sb.append("推荐原则：\n");
        sb.append("- 优先推荐专业对口的岗位（开发、算法、数据、测试等）\n");
        sb.append("- 考虑学生的学习经历和技能\n");
        sb.append("- 不必严格限制，只要技术相关且学生能胜任即可\n");
        sb.append("- 必须按匹配度从高到低排序，最匹配的排在最前面\n\n");
        sb.append("输出格式：\n");
        sb.append("请返回按匹配度排序的岗位ID列表（最匹配的在前），格式为：position_ids: 1,2,3,4,5\n");
        sb.append("只返回ID列表，用逗号分隔，按匹配度从高到低排序。最多返回10个最匹配的岗位。\n");
        sb.append("如果没有找到任何适合的岗位，返回：position_ids: \"\"");
        return sb.toString();
    }

    /**
     * 构建岗位匹配的用户提示词
     */
    private String buildPositionMatchingUserPrompt(String query, StudentUser student, String positionsInfo) {
        return "学生信息：\n" +
                "- 专业：" + student.getMajor() + "\n" +
                "- 年级：" + student.getGrade() + "\n" +
                "- 姓名：" + student.getName() + "\n" +
                "- 用户需求：" + query + "\n\n" +
                "可用岗位列表：\n" + positionsInfo + "\n\n" +
                "请根据学生的专业背景和用户需求，从上述岗位列表中选出最适合的岗位。\n" +
                "重要：必须按匹配度从高到低排序，最匹配的岗位ID排在前面。\n" +
                "输出格式：position_ids: 1,2,3,4,5（只返回ID列表，用逗号分隔，按匹配度从高到低排序，最多10个）";
    }

    /**
     * 从AI响应中解析岗位ID列表
     */
    private List<Long> parsePositionIdsFromResponse(String aiResponse) {
        if (aiResponse == null || aiResponse.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            // 查找 position_ids: 后面的内容
            Pattern pattern = Pattern.compile("position_ids:\\s*([^\\n]+)");
            Matcher matcher = pattern.matcher(aiResponse);

            if (matcher.find()) {
                String idsStr = matcher.group(1).trim();
                if (idsStr.isEmpty() || "\"\"".equals(idsStr)) {
                    return Collections.emptyList();
                }

                // 解析ID列表
                List<Long> ids = new ArrayList<>();
                for (String idStr : idsStr.split(",")) {
                    try {
                        ids.add(Long.parseLong(idStr.trim()));
                    } catch (NumberFormatException e) {
                        // 忽略无效的ID
                    }
                }
                log.info("解析到 %d 个匹配岗位ID", ids.size());
                return ids;
            }

            log.warn("无法从AI响应中解析岗位ID: {}", aiResponse);
            return Collections.emptyList();

        } catch (Exception e) {
            log.error("解析岗位ID异常: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 获取学生收藏的岗位ID集合
     */
    private Set<Long> getFavoritePositionIds(Long studentId) {
        try {
            List<Long> favoriteIds = positionFavoriteService.getStudentFavoritePositionIds(studentId);
            return new HashSet<>(favoriteIds);
        } catch (Exception e) {
            log.warn("获取收藏状态异常: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 获取学生已投递的岗位ID集合
     */
    private Set<Long> getAppliedPositionIds(Long studentId) {
        try {
            List<StudentJobApplication> applications = studentJobApplicationService.findByStudentId(studentId);
            return applications.stream()
                    .map(StudentJobApplication::getPositionId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            log.warn("获取投递状态异常: {}", e.getMessage());
            return Collections.emptySet();
        }
    }

    /**
     * 构建推荐岗位列表
     */
    private List<Map<String, Object>> buildRecommendedJobs(
            List<Position> positions,
            StudentUser student,
            Set<Long> favoritePositionIds,
            Set<Long> appliedPositionIds) {

        // 获取所有涉及的公司ID
        Set<Long> companyIds = positions.stream()
                .map(Position::getCompanyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 批量查询公司信息
        Map<Long, CompanyUser> companyMap = new HashMap<>();
        if (!companyIds.isEmpty()) {
            List<CompanyUser> companies = companyUserService.findByIds(new ArrayList<>(companyIds));
            for (CompanyUser company : companies) {
                companyMap.put(company.getId(), company);
            }
        }

        List<Map<String, Object>> results = new ArrayList<>();

        for (Position position : positions) {
            Map<String, Object> job = new HashMap<>();
            job.put("id", position.getId());
            job.put("positionName", position.getPositionName());
            job.put("salary", formatSalary(position.getSalaryMin(), position.getSalaryMax()));
            job.put("location", formatLocation(position.getProvince(), position.getCity()));
            job.put("requirements", position.getRequirements());
            job.put("description", position.getDescription());
            job.put("department", position.getDepartment());
            job.put("positionType", position.getPositionType());
            job.put("isFavorited", favoritePositionIds.contains(position.getId()));
            job.put("isApplied", appliedPositionIds.contains(position.getId()));
            // 公司信息
            CompanyUser company = companyMap.get(position.getCompanyId());
            job.put("companyName", company != null ? company.getCompanyName() : "未知公司");
            job.put("contactPerson", company != null ? company.getContactPerson() : "");
            job.put("contactPhone", company != null ? company.getContactPhone() : "");

            results.add(job);

            // 限制返回数量
            if (results.size() >= 10) {
                break;
            }
        }

        return results;
    }

    /**
     * 格式化薪资显示（单位：K/月）
     */
    private String formatSalary(Integer salaryMin, Integer salaryMax) {
        if (salaryMin == null && salaryMax == null) {
            return "薪资面议";
        }
        if (salaryMin != null && salaryMax != null) {
            return salaryMin + "-" + salaryMax + "K/月";
        }
        if (salaryMin != null) {
            return salaryMin + "K/月起";
        }
        return "最高" + salaryMax + "K/月";
    }

    /**
     * 格式化地点显示
     */
    private String formatLocation(String province, String city) {
        if (province == null && city == null) {
            return "地点不限";
        }
        StringBuilder sb = new StringBuilder();
        if (province != null && !province.isEmpty()) {
            sb.append(province);
        }
        if (city != null && !city.isEmpty()) {
            if (sb.length() > 0) {
                sb.append(" ").append(city);
            } else {
                sb.append(city);
            }
        }
        return sb.length() > 0 ? sb.toString() : "地点不限";
    }

    /**
     * 生成推荐理由
     */
    private String generateMatchReason(Position position, StudentUser student) {
        List<String> reasons = new ArrayList<>();

        // 检查专业匹配
        if (student.getMajor() != null && position.getRequirements() != null) {
            String major = student.getMajor().toLowerCase();
            String req = position.getRequirements().toLowerCase();
            if (req.contains(major) || major.contains(req.substring(0, Math.min(3, req.length())))) {
                reasons.add("专业对口");
            }
        }

        // 检查岗位类型匹配
        if (student.getMajor() != null && position.getPositionName() != null) {
            String major = student.getMajor().toLowerCase();
            String name = position.getPositionName().toLowerCase();
            if (name.contains(major) || major.contains(name.substring(0, Math.min(3, name.length())))) {
                if (!reasons.contains("专业对口")) {
                    reasons.add("岗位与专业相关");
                }
            }
        }

        // 检查薪资
        if (position.getSalaryMin() != null && position.getSalaryMin() > 100) {
            reasons.add("薪资待遇较好");
        }

        // 如果没有特定理由
        if (reasons.isEmpty()) {
            reasons.add("符合您的求职意向");
        }

        return String.join("、", reasons);
    }

    /**
     * 构建错误结果
     */
    private Map<String, Object> buildErrorResult(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("type", "job_recommendation");
        result.put("total", 0);
        result.put("positions", Collections.emptyList());
        result.put("message", message);
        result.put("error", true);
        return result;
    }

    /**
     * 根据模型名称获取ChatClient
     */
    private ChatClient getChatClientByModel(String model) {
        String modelCode;
        if (model == null || model.trim().isEmpty()) {
            modelCode = "deepseek-chat";
        } else if ("deepseek-reasoner".equalsIgnoreCase(model)) {
            modelCode = "deepseek-reasoner";
        } else if ("deepseek-v4-flash".equalsIgnoreCase(model)) {
            modelCode = "deepseek-v4-flash";
        } else if ("deepseek-v4-pro".equalsIgnoreCase(model)) {
            modelCode = "deepseek-v4-pro";
        } else {
            modelCode = "deepseek-chat";
        }
        return chatClientFactory.createChatClient(modelCode, SYSTEM_PROMPT);
    }
}
