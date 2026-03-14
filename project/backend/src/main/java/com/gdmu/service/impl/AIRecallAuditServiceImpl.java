package com.gdmu.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.config.DynamicChatClientFactory;
import com.gdmu.entity.AiAuditRecord;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.enums.AuditDecision;
import com.gdmu.enums.AuditType;
import com.gdmu.enums.RiskLevel;
import com.gdmu.enums.TargetType;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.AiAuditRecordMapper;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.StudentInternshipStatusMapper;
import com.gdmu.service.AIRecallAuditService;
import com.gdmu.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AIRecallAuditServiceImpl implements AIRecallAuditService {

    private static final String AI_AUDIT_SYSTEM_PROMPT = 
        "你是实习管理系统的AI审核助手，负责审核企业提交的撤回申请。\n\n" +
        "你的任务是判断撤回申请是否合理，并给出审核决策。\n\n" +
        "审核标准：\n" +
        "1. 信息错误类撤回（如：信息填写错误、资料错误）→ 批准\n" +
        "2. 资料补充类撤回（如：需要补充材料、资料不完整）→ 批准\n" +
        "3. 信息更新类撤回（如：信息更新、信息变更）→ 批准\n" +
        "4. 联系方式变更类撤回（如：联系方式错误）→ 批准\n" +
        "5. 时间相关撤回（如：等待时间太长）→ 拒绝，转人工审核\n" +
        "6. 态度相关撤回（如：不想要了、放弃申请）→ 拒绝，转人工审核\n" +
        "7. 系统问题类撤回（如：系统问题、系统错误）→ 拒绝，转人工审核\n" +
        "8. 模糊不清的撤回原因 → 拒绝，转人工审核\n" +
        "9. 高风险撤回（如：重新注册、换个账号）→ 拒绝，转人工审核\n\n" +
        "请以JSON格式返回审核结果，格式如下：\n" +
        "{\n" +
        "  \"approved\": true/false,\n" +
        "  \"reason\": \"审核理由\",\n" +
        "  \"riskLevel\": \"LOW/MEDIUM/HIGH\",\n" +
        "  \"needManualReview\": true/false,\n" +
        "  \"aiRemark\": \"AI审核备注\"\n" +
        "}\n\n" +
        "请保持客观、公正的审核态度，确保审核决策的准确性。";

    private static final String AI_STUDENT_AUDIT_SYSTEM_PROMPT = 
        "你是实习管理系统的AI审核助手，负责审核学生提交的实习确认表撤回申请。\n\n" +
        "你的任务是判断学生的撤回申请是否合理，并给出审核决策。\n\n" +
        "审核标准（相对宽松）：\n" +
        "1. 信息错误类撤回（如：信息填写错误、资料错误、信息不完整）→ 批准\n" +
        "2. 岗位不合适类撤回（如：岗位不符、专业不符、岗位调整）→ 批准\n" +
        "3. 个人原因类撤回（如：个人规划、家庭原因、健康原因）→ 批准\n" +
        "4. 资料补充类撤回（如：需要补充材料、资料不完整）→ 批准\n" +
        "5. 信息更新类撤回（如：信息更新、信息变更）→ 批准\n" +
        "6. 联系方式变更类撤回（如：联系方式错误）→ 批准\n" +
        "7. 时间相关撤回（如：等待时间太长、时间冲突）→ 批准\n" +
        "8. 模糊不清的撤回原因 → 批准（给予学生更多理解）\n" +
        "9. 只有以下情况才拒绝：恶意撤回、重复撤回、明显不合理撤回\n\n" +
        "请以JSON格式返回审核结果，格式如下：\n" +
        "{\n" +
        "  \"approved\": true/false,\n" +
        "  \"reason\": \"审核理由\",\n" +
        "  \"riskLevel\": \"LOW/MEDIUM/HIGH\",\n" +
        "  \"needManualReview\": true/false,\n" +
        "  \"aiRemark\": \"AI审核备注\"\n" +
        "}\n\n" +
        "请保持客观、公正的审核态度，对学生的撤回申请给予更多理解和支持。";

    private final DynamicChatClientFactory chatClientFactory;
    private final CompanyUserMapper companyUserMapper;
    private final StudentInternshipStatusMapper studentInternshipStatusMapper;
    private final AiAuditRecordMapper aiAuditRecordMapper;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;

    @Autowired
    public AIRecallAuditServiceImpl(
            DynamicChatClientFactory chatClientFactory,
            CompanyUserMapper companyUserMapper,
            StudentInternshipStatusMapper studentInternshipStatusMapper,
            AiAuditRecordMapper aiAuditRecordMapper,
            SystemConfigService systemConfigService) {
        this.chatClientFactory = chatClientFactory;
        this.companyUserMapper = companyUserMapper;
        this.studentInternshipStatusMapper = studentInternshipStatusMapper;
        this.aiAuditRecordMapper = aiAuditRecordMapper;
        this.systemConfigService = systemConfigService;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public Map<String, Object> autoAuditRecall(Long companyId, String recallReason) {
        log.info("开始AI自动审核撤回申请，公司ID: {}, 撤回原因: {}", companyId, recallReason);

        try {
            CompanyUser companyUser = companyUserMapper.findById(companyId);
            if (companyUser == null) {
                throw new BusinessException("企业不存在");
            }

            Map<String, Object> result = new HashMap<>();
            long startTime = System.currentTimeMillis();

            try {
                Map<String, Object> auditDecision = performAIAudit(recallReason, companyUser);
                long duration = System.currentTimeMillis() - startTime;

                result.putAll(auditDecision);
                result.put("auditMethod", "AI");
                result.put("modelUsed", "deepseek-reasoner");
                result.put("auditDuration", duration);

                recordAuditResult(result, companyId, "COMPANY", "deepseek-reasoner", duration);

                log.info("AI审核完成，公司ID: {}, 审核结果: {}, 耗时: {}ms", 
                    companyId, auditDecision.get("auditDecision"), duration);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("AI审核失败，转人工审核，公司ID: {}, 错误: {}", companyId, e.getMessage(), e);

                result.put("approved", false);
                result.put("needManualReview", true);
                result.put("auditDecision", "MANUAL");
                result.put("reason", "AI审核失败，转人工审核");
                result.put("riskLevel", "MEDIUM");
                result.put("aiRemark", "AI审核异常: " + e.getMessage());
                result.put("auditMethod", "AI_FAILED");
                result.put("modelUsed", "deepseek-reasoner");
                result.put("auditDuration", duration);

                recordAuditResult(result, companyId, "COMPANY", "deepseek-reasoner", duration);
            }

            return result;

        } catch (Exception e) {
            log.error("AI审核撤回申请失败: {}", e.getMessage(), e);
            throw new BusinessException("AI审核失败: " + e.getMessage());
        }
    }

    @Override
    public Map<String, Object> autoAuditStudentRecall(Long studentInternshipStatusId, String recallReason) {
        log.info("开始AI自动审核学生撤回申请，实习状态ID: {}, 撤回原因: {}", studentInternshipStatusId, recallReason);

        try {
            StudentInternshipStatus status = studentInternshipStatusMapper.findById(studentInternshipStatusId);
            if (status == null) {
                throw new BusinessException("实习状态不存在");
            }

            Map<String, Object> result = new HashMap<>();
            long startTime = System.currentTimeMillis();

            try {
                Map<String, Object> auditDecision = performStudentAIAudit(recallReason, status);
                long duration = System.currentTimeMillis() - startTime;

                result.putAll(auditDecision);
                result.put("auditMethod", "AI");
                result.put("modelUsed", "deepseek-reasoner");
                result.put("auditDuration", duration);

                recordAuditResult(result, studentInternshipStatusId, "STUDENT", "deepseek-reasoner", duration);

                log.info("AI审核完成，实习状态ID: {}, 审核结果: {}, 耗时: {}ms", 
                    studentInternshipStatusId, auditDecision.get("auditDecision"), duration);

            } catch (Exception e) {
                long duration = System.currentTimeMillis() - startTime;
                log.error("AI审核失败，转人工审核，实习状态ID: {}, 错误: {}", studentInternshipStatusId, e.getMessage(), e);

                result.put("approved", false);
                result.put("needManualReview", true);
                result.put("auditDecision", "MANUAL");
                result.put("reason", "AI审核失败，转人工审核");
                result.put("riskLevel", "MEDIUM");
                result.put("aiRemark", "AI审核异常: " + e.getMessage());
                result.put("auditMethod", "AI_FAILED");
                result.put("modelUsed", "deepseek-reasoner");
                result.put("auditDuration", duration);

                recordAuditResult(result, studentInternshipStatusId, "STUDENT", "deepseek-reasoner", duration);
            }

            return result;

        } catch (Exception e) {
            log.error("AI审核学生撤回申请失败: {}", e.getMessage(), e);
            throw new BusinessException("AI审核失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isAIRecallAuditEnabled() {
        try {
            String enabled = systemConfigService.getConfigValue("ai_recall_audit_enabled");
            return "true".equalsIgnoreCase(enabled);
        } catch (Exception e) {
            log.error("获取AI审核配置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean isAIStudentRecallAuditEnabled() {
        try {
            String enabled = systemConfigService.getConfigValue("ai_student_recall_audit_enabled");
            return "true".equalsIgnoreCase(enabled);
        } catch (Exception e) {
            log.error("获取AI学生审核配置失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void recordAuditResult(Map<String, Object> auditResult, Long targetId, String targetType, String modelUsed, long duration) {
        try {
            AiAuditRecord record = new AiAuditRecord();
            record.setAuditTypeEnum(AuditType.RECALL_APPLY);
            record.setTargetId(targetId);
            record.setTargetTypeEnum(TargetType.valueOf(targetType));
            record.setRecallReason((String) auditResult.get("recallReason"));
            record.setAuditDecision((String) auditResult.get("auditDecision"));
            record.setAuditReason((String) auditResult.get("reason"));
            record.setRiskLevel((String) auditResult.get("riskLevel"));
            record.setAiRemark((String) auditResult.get("aiRemark"));
            record.setModelUsed(modelUsed);
            record.setAuditTime(new Date());
            record.setAuditDuration((int) duration);
            record.setStatus(1);
            record.setCreateTime(new Date());

            aiAuditRecordMapper.insert(record);
            log.info("AI审核记录已保存，记录ID: {}", record.getId());
        } catch (Exception e) {
            log.error("保存AI审核记录失败: {}", e.getMessage(), e);
        }
    }

    @Override
    public Map<String, Object> getAuditStatistics(String auditType, String startDate, String endDate) {
        try {
            return aiAuditRecordMapper.getAuditStatistics(auditType, startDate, endDate);
        } catch (Exception e) {
            log.error("获取AI审核统计失败: {}", e.getMessage(), e);
            return new HashMap<>();
        }
    }

    private Map<String, Object> performAIAudit(String recallReason, CompanyUser companyUser) {
        Map<String, Object> result = new HashMap<>();
        result.put("recallReason", recallReason);

        try {
            int timeout = getAuditTimeout();
            int maxRetry = getMaxRetry();

            for (int i = 0; i < maxRetry; i++) {
                try {
                    Map<String, Object> aiResult = callAIWithTimeout(recallReason, companyUser, timeout);
                    result.putAll(aiResult);
                    break;
                } catch (TimeoutException e) {
                    log.warn("AI审核超时（第{}次尝试），转人工审核", i + 1);
                    if (i == maxRetry - 1) {
                        result.put("approved", false);
                        result.put("needManualReview", true);
                        result.put("auditDecision", "MANUAL");
                        result.put("reason", "AI审核超时，转人工审核");
                        result.put("riskLevel", "MEDIUM");
                        result.put("aiRemark", "AI审核超时");
                    }
                } catch (Exception e) {
                    log.warn("AI审核失败（第{}次尝试）: {}", i + 1, e.getMessage());
                    if (i == maxRetry - 1) {
                        throw e;
                    }
                    Thread.sleep(1000);
                }
            }

        } catch (Exception e) {
            throw new BusinessException("AI审核失败: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> performStudentAIAudit(String recallReason, StudentInternshipStatus status) {
        Map<String, Object> result = new HashMap<>();
        result.put("recallReason", recallReason);

        try {
            int timeout = getAuditTimeout();
            int maxRetry = getMaxRetry();

            for (int i = 0; i < maxRetry; i++) {
                try {
                    Map<String, Object> aiResult = callStudentAIWithTimeout(recallReason, status, timeout);
                    result.putAll(aiResult);
                    break;
                } catch (TimeoutException e) {
                    log.warn("AI审核超时（第{}次尝试），转人工审核", i + 1);
                    if (i == maxRetry - 1) {
                        result.put("approved", false);
                        result.put("needManualReview", true);
                        result.put("auditDecision", "MANUAL");
                        result.put("reason", "AI审核超时，转人工审核");
                        result.put("riskLevel", "MEDIUM");
                        result.put("aiRemark", "AI审核超时");
                    }
                } catch (Exception e) {
                    log.warn("AI审核失败（第{}次尝试）: {}", i + 1, e.getMessage());
                    if (i == maxRetry - 1) {
                        throw e;
                    }
                    Thread.sleep(1000);
                }
            }

        } catch (Exception e) {
            throw new BusinessException("AI审核失败: " + e.getMessage());
        }

        return result;
    }

    private Map<String, Object> callStudentAIWithTimeout(String recallReason, StudentInternshipStatus status, int timeout) 
            throws Exception {
        CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
            return callStudentAIModel(recallReason, status);
        });

        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    private Map<String, Object> callStudentAIModel(String recallReason, StudentInternshipStatus status) {
        try {
            ChatClient client = chatClientFactory.createChatClient("deepseek-reasoner", AI_STUDENT_AUDIT_SYSTEM_PROMPT);

            String userPrompt = buildStudentUserPrompt(recallReason, status);

            String aiResponse = client.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("AI学生审核响应: {}", aiResponse);

            return parseAIResponse(aiResponse);

        } catch (Exception e) {
            log.error("调用AI模型失败: {}", e.getMessage(), e);
            throw new BusinessException("调用AI模型失败: " + e.getMessage());
        }
    }

    private String buildStudentUserPrompt(String recallReason, StudentInternshipStatus status) {
        return String.format(
            "请审核以下学生实习确认表撤回申请：\n\n" +
            "学生实习信息：\n" +
            "- 实习状态ID：%s\n" +
            "- 学生ID：%s\n" +
            "- 公司ID：%s\n" +
            "- 岗位ID：%s\n" +
            "- 实习状态：%s\n" +
            "- 确认时间：%s\n\n" +
            "撤回原因：%s\n\n" +
            "请根据撤回原因和学生实习信息，判断是否批准撤回申请。对学生的撤回申请给予更多理解和支持。",
            status.getId(),
            status.getStudentId(),
            status.getCompanyId(),
            status.getPositionId(),
            status.getStatus() == 2 ? "已确认" : "其他",
            status.getUpdateTime(),
            recallReason
        );
    }

    private Map<String, Object> callAIWithTimeout(String recallReason, CompanyUser companyUser, int timeout) 
            throws Exception {
        CompletableFuture<Map<String, Object>> future = CompletableFuture.supplyAsync(() -> {
            return callAIModel(recallReason, companyUser);
        });

        try {
            return future.get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            throw e;
        }
    }

    private Map<String, Object> callAIModel(String recallReason, CompanyUser companyUser) {
        try {
            ChatClient client = chatClientFactory.createChatClient("deepseek-reasoner", AI_AUDIT_SYSTEM_PROMPT);

            String userPrompt = buildUserPrompt(recallReason, companyUser);

            String aiResponse = client.prompt()
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("AI审核响应: {}", aiResponse);

            return parseAIResponse(aiResponse);

        } catch (Exception e) {
            log.error("调用AI模型失败: {}", e.getMessage(), e);
            throw new BusinessException("调用AI模型失败: " + e.getMessage());
        }
    }

    private String buildUserPrompt(String recallReason, CompanyUser companyInfo) {
        return String.format(
            "请审核以下撤回申请：\n\n" +
            "公司信息：\n" +
            "- 公司名称：%s\n" +
            "- 联系人：%s\n" +
            "- 联系电话：%s\n" +
            "- 联系邮箱：%s\n" +
            "- 申请时间：%s\n\n" +
            "撤回原因：%s\n\n" +
            "请根据撤回原因和公司信息，判断是否批准撤回申请。",
            companyInfo.getCompanyName(),
            companyInfo.getContactPerson(),
            companyInfo.getContactPhone(),
            companyInfo.getContactEmail(),
            companyInfo.getApplyTime(),
            recallReason
        );
    }

    private Map<String, Object> parseAIResponse(String aiResponse) {
        try {
            JsonNode rootNode = objectMapper.readTree(aiResponse);

            Map<String, Object> result = new HashMap<>();
            result.put("approved", rootNode.path("approved").asBoolean());
            result.put("reason", rootNode.path("reason").asText());
            result.put("riskLevel", rootNode.path("riskLevel").asText("MEDIUM"));
            result.put("needManualReview", rootNode.path("needManualReview").asBoolean());
            result.put("aiRemark", rootNode.path("aiRemark").asText());

            String auditDecision;
            if (result.get("approved").equals(true)) {
                auditDecision = "APPROVED";
            } else if (result.get("needManualReview").equals(true)) {
                auditDecision = "MANUAL";
            } else {
                auditDecision = "REJECTED";
            }
            result.put("auditDecision", auditDecision);

            return result;

        } catch (Exception e) {
            log.error("解析AI响应失败: {}", e.getMessage(), e);
            Map<String, Object> result = new HashMap<>();
            result.put("approved", false);
            result.put("needManualReview", true);
            result.put("auditDecision", "MANUAL");
            result.put("reason", "AI响应解析失败，转人工审核");
            result.put("riskLevel", "MEDIUM");
            result.put("aiRemark", "AI响应格式异常");
            return result;
        }
    }

    private int getAuditTimeout() {
        try {
            String timeout = systemConfigService.getConfigValue("ai_recall_audit_timeout");
            return timeout != null ? Integer.parseInt(timeout) : 30;
        } catch (Exception e) {
            log.warn("获取AI审核超时配置失败，使用默认值30秒");
            return 30;
        }
    }

    private int getMaxRetry() {
        try {
            String retry = systemConfigService.getConfigValue("ai_recall_audit_max_retry");
            return retry != null ? Integer.parseInt(retry) : 3;
        } catch (Exception e) {
            log.warn("获取AI审核重试配置失败，使用默认值3次");
            return 3;
        }
    }
}
