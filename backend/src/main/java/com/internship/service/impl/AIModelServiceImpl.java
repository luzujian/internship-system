package com.internship.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.AIModel;
import com.internship.exception.BusinessException;
import com.internship.mapper.AIModelMapper;
import com.internship.mapper.CounselorAISettingsMapper;
import com.internship.service.AIConfigRefreshService;
import com.internship.service.AIModelService;
import com.internship.util.AiEndpointSupport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AIModelServiceImpl implements AIModelService {

    /** 管理端可选的提供商；openai/custom 用于任意 OpenAI 兼容网关。 */
    private static final List<String> ALLOWED_PROVIDERS =
            List.of("deepseek", "openai", "custom", "qwen", "zhipu");

    /** 实际请求模型：字母数字打头，可含字母/数字/点/横线/下划线（与 DB varchar(50) 对齐）。 */
    private static final Pattern MODEL_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,49}$");

    /** 一键测连超时：连接5秒 + 读取30秒，单次不重试；只用于管理端手动测试。 */
    private static final Duration TEST_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TEST_READ_TIMEOUT = Duration.ofSeconds(30);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AIModelMapper aiModelMapper;

    @Autowired
    private AIConfigRefreshService aiConfigRefreshService;

    @Autowired
    private CounselorAISettingsMapper counselorAISettingsMapper;

    @Autowired private com.internship.agent.business.AgentVoiceSettings voiceSettings;

    private void saveVoiceUsage(AIModel row) {
        if (row.getUseForVoice() == null) return;
        if (row.getUseForVoice()) voiceSettings.save(row.getId());
        else if (voiceSettings.selectedId() == row.getId()) voiceSettings.save(0);
    }

    @Autowired
    public AIModelServiceImpl(AIModelMapper aiModelMapper) {
        this.aiModelMapper = aiModelMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(AIModel aiModel) {
        log.debug("插入AI模型配置");

        if (aiModel == null) {
            throw new BusinessException("AI模型信息不能为空");
        }

        normalizeForSave(aiModel, true);

        if (aiModel.getStatus() == null) {
            aiModel.setStatus(1);
        }

        if (aiModel.getIsDefault() == null) {
            aiModel.setIsDefault(0);
        }

        if (aiModel.getMaxTokens() == null) {
            aiModel.setMaxTokens(4096);
        } else if (aiModel.getMaxTokens() < 1 || aiModel.getMaxTokens() > 8192) {
            throw new BusinessException("最大Token数必须在 [1, 8192] 范围内");
        }

        if (aiModel.getTemperature() == null) {
            aiModel.setTemperature(new java.math.BigDecimal("0.70"));
        }

        boolean wantDefault = Integer.valueOf(1).equals(aiModel.getIsDefault());
        if (wantDefault) {
            aiModel.setIsDefault(0);
        }
        int result = aiModelMapper.insert(aiModel);
        saveVoiceUsage(aiModel);
        log.info("AI模型配置插入成功，ID: {}", aiModel.getId());

        if (wantDefault) {
            setAsDefault(aiModel.getId(), aiModel.getCreator());
        } else {
            refreshQuietly();
        }

        return result;
    }

    @Override
    public List<AIModel> findAll() {
        log.debug("查询所有AI模型配置");
        return aiModelMapper.findAll();
    }

    @Override
    public AIModel findById(Long id) {
        log.debug("根据ID查询AI模型配置，ID: {}", id);

        if (id == null || id <= 0) {
            throw new BusinessException("AI模型ID无效");
        }

        return aiModelMapper.findById(id);
    }

    @Override
    public AIModel findByModelCode(String modelCode) {
        log.debug("根据模型代码查询AI模型配置，模型代码: {}", modelCode);
        return aiModelMapper.findByModelCode(modelCode);
    }

    @Override
    public List<AIModel> findEnabledModels() {
        log.debug("查询启用的AI模型配置");
        return aiModelMapper.findEnabledModels();
    }

    @Override
    public AIModel findDefaultModel() {
        log.debug("查询默认AI模型配置");
        return aiModelMapper.findDefaultModel();
    }

    @Override
    public List<AIModel> findByProvider(String provider) {
        log.debug("根据提供商查询AI模型配置，提供商: {}", provider);
        return aiModelMapper.findByProvider(provider);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(AIModel aiModel) {
        log.debug("更新AI模型配置，ID: {}", aiModel.getId());

        if (aiModel == null || aiModel.getId() == null) {
            throw new BusinessException("AI模型信息或ID不能为空");
        }

        AIModel existing = aiModelMapper.findById(aiModel.getId());
        if (existing == null) {
            throw new BusinessException("AI模型不存在");
        }

        normalizeForSave(aiModel, false);

        String oldCode = existing.getModelCode();
        boolean codeChanged = aiModel.getModelCode() != null && !aiModel.getModelCode().equals(oldCode);
        if (codeChanged) {
            AIModel modelWithSameCode = aiModelMapper.findByModelCode(aiModel.getModelCode());
            if (modelWithSameCode != null && !modelWithSameCode.getId().equals(aiModel.getId())) {
                throw new BusinessException("实际请求模型已存在: " + aiModel.getModelCode());
            }
        }

        if (aiModel.getMaxTokens() != null && (aiModel.getMaxTokens() < 1 || aiModel.getMaxTokens() > 8192)) {
            throw new BusinessException("最大Token数必须在 [1, 8192] 范围内");
        }

        int result = aiModelMapper.update(aiModel);
        saveVoiceUsage(aiModel);
        // Revalidate selected voice configuration even for legacy callers omitting usage.
        var savedModel = aiModelMapper.findById(aiModel.getId());
        if (savedModel != null && Integer.valueOf(1).equals(savedModel.getIsDefault())
                && !com.internship.util.AIModelCapabilities.supported(savedModel.getModelCode()).contains("chat"))
            throw new BusinessException("该模型不支持聊天用途，请先选择其他聊天模型。");
        if (voiceSettings != null && voiceSettings.selectedId() == aiModel.getId()) voiceSettings.resolve();
        log.info("AI模型配置更新成功，ID: {}", aiModel.getId());

        if (codeChanged) {
            try {
                int synced = counselorAISettingsMapper.updateModelCode(oldCode, aiModel.getModelCode());
                log.info("实际请求模型改名 {} -> {}，同步辅导员配置 {} 条", oldCode, aiModel.getModelCode(), synced);
            } catch (Exception e) {
                log.error("同步辅导员模型代码失败，但模型数据已更新", e);
            }
        }

        boolean wantDefault = Integer.valueOf(1).equals(aiModel.getIsDefault());
        if (wantDefault) {
            setAsDefault(aiModel.getId(), aiModel.getUpdater());
        } else {
            refreshQuietly();
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int setAsDefault(Long id, String updater) {
        log.debug("设置默认AI模型，ID: {}", id);

        if (id == null || id <= 0) {
            throw new BusinessException("AI模型ID无效");
        }

        AIModel existing = aiModelMapper.findById(id);
        if (existing == null) {
            throw new BusinessException("AI模型不存在");
        }

        if (existing.getStatus() == 0) {
            throw new BusinessException("无法将禁用的模型设置为默认");
        }
        if (!com.internship.util.AIModelCapabilities.supported(existing.getModelCode()).contains("chat") || existing.getApiEndpoint()!=null && existing.getApiEndpoint().endsWith("/realtime")) {
            throw new BusinessException("实时语音模型请在语音选择中配置，不能设为通用对话默认模型。");
        }

        aiModelMapper.clearDefaultModel(updater);
        int result = aiModelMapper.setAsDefault(id, updater);
        log.info("AI模型设置为默认成功，ID: {}", id);

        try {
            aiConfigRefreshService.refreshAIConfig();
            log.info("AI模型配置已刷新，新配置将立即生效");

            // 同步更新所有辅导员的AI模型为新的默认模型
            counselorAISettingsMapper.updateAllModelCode(existing.getModelCode());
            log.info("所有辅导员的AI模型已同步更新为: {}", existing.getModelCode());
        } catch (Exception e) {
            log.error("刷新AI模型配置失败，但数据已更新", e);
        }

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteById(Long id) {
        log.debug("删除AI模型配置，ID: {}", id);

        if (id == null || id <= 0) {
            throw new BusinessException("AI模型ID无效");
        }

        AIModel existing = aiModelMapper.findById(id);
        if (existing == null) {
            throw new BusinessException("AI模型不存在");
        }

        if (existing.getIsDefault() == 1) {
            throw new BusinessException("无法删除默认模型，请先设置其他模型为默认");
        }

        int result = aiModelMapper.deleteById(id, "admin");
        log.info("AI模型配置删除成功，ID: {}", id);

        refreshQuietly();

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAll(String updater) {
        log.debug("删除所有AI模型配置");

        List<AIModel> allModels = aiModelMapper.findAll();
        if (allModels == null || allModels.isEmpty()) {
            log.info("没有AI模型需要删除");
            return 0;
        }

        int deletedCount = 0;
        for (AIModel model : allModels) {
            try {
                aiModelMapper.deleteById(model.getId(), updater);
                deletedCount++;
                log.debug("删除AI模型配置成功，ID: {}", model.getId());
            } catch (Exception e) {
                log.error("删除AI模型配置失败，ID: {}, 错误: {}", model.getId(), e.getMessage());
            }
        }

        log.info("批量删除AI模型配置完成，共删除 {} 个模型", deletedCount);

        refreshQuietly();

        return deletedCount;
    }

    @Override
    public List<AIModel> searchByKeyword(String keyword) {
        log.debug("搜索AI模型配置，关键词: {}", keyword);
        return aiModelMapper.searchByKeyword(keyword);
    }

    @Override
    public List<AIModel> findByStatus(Integer status) {
        log.debug("根据状态查询AI模型配置，状态: {}", status);
        return aiModelMapper.findByStatus(status);
    }

    @Override
    public AIModel maskForOutput(AIModel model) {
        if (model == null) {
            return null;
        }
        boolean hasKey = !AiEndpointSupport.isBlank(model.getApiKey());
        model.setHasApiKey(hasKey);
        model.setApiKey(AiEndpointSupport.maskApiKey(model.getApiKey()));
        model.setClearApiConfig(null);
        model.setSupportedUsages(com.internship.util.AIModelCapabilities.supported(model.getModelCode()));
        model.setUseForVoice(voiceSettings != null && model.getId() != null && voiceSettings.selectedId() == model.getId());
        return model;
    }

    @Override
    public List<AIModel> maskForOutput(List<AIModel> models) {
        if (models == null) {
            return new ArrayList<>();
        }
        for (AIModel model : models) {
            maskForOutput(model);
        }
        return models;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AIModel duplicateModel(Long id, String creator) {
        log.debug("复制AI模型配置，ID: {}", id);

        if (id == null || id <= 0) {
            throw new BusinessException("AI模型ID无效");
        }
        AIModel source = aiModelMapper.findById(id);
        if (source == null) {
            throw new BusinessException("AI模型不存在");
        }

        AIModel copy = new AIModel();
        String baseName = source.getModelName() == null ? "未命名模型" : source.getModelName();
        copy.setModelName(truncate(baseName + " 副本", 100));
        copy.setModelCode(uniqueCopyCode(source.getModelCode()));
        copy.setProvider(source.getProvider());
        copy.setApiEndpoint(source.getApiEndpoint());
        copy.setApiKey(source.getApiKey());
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTemperature(source.getTemperature());
        copy.setDescription(source.getDescription());
        copy.setStatus(source.getStatus());
        copy.setIsDefault(0);
        copy.setCreator(creator);

        normalizeForSave(copy, true);
        aiModelMapper.insert(copy);
        log.info("AI模型复制成功，来源ID: {}，新ID: {}，新代码: {}", id, copy.getId(), copy.getModelCode());
        refreshQuietly();
        return copy;
    }

    @Override
    public Map<String, Object> testConnection(AIModel probe) {
        if (probe == null) {
            throw new BusinessException("缺少测试参数");
        }

        AIModel stored = null;
        if (probe.getId() != null && probe.getId() > 0) {
            stored = aiModelMapper.findById(probe.getId());
            if (stored == null) {
                throw new BusinessException("AI模型不存在");
            }
        }

        String endpoint = AiEndpointSupport.isBlank(probe.getApiEndpoint())
                ? (stored == null ? null : stored.getApiEndpoint())
                : probe.getApiEndpoint();
        String apiKey = AiEndpointSupport.isBlank(probe.getApiKey())
                ? (stored == null ? null : stored.getApiKey())
                : probe.getApiKey();
        String modelCode = AiEndpointSupport.isBlank(probe.getModelCode())
                ? (stored == null ? null : stored.getModelCode())
                : probe.getModelCode().trim();

        String normalizedEndpoint;
        try {
            normalizedEndpoint = AiEndpointSupport.normalizeEndpoint(endpoint);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        if (normalizedEndpoint == null) {
            throw new BusinessException("未配置 API 地址，请先填写该模型的 API 地址");
        }
        if (AiEndpointSupport.isBlank(apiKey)) {
            throw new BusinessException("未配置 API Key，请先填写该模型的 API Key");
        }
        if (AiEndpointSupport.isBlank(modelCode)) {
            throw new BusinessException("未配置实际请求模型，请先填写 model");
        }

        if (normalizedEndpoint.endsWith("/realtime")) throw new BusinessException("实时语音模型请保存后通过录音验证；此按钮只测试通用对话接口。");
        String url = normalizedEndpoint + "/chat/completions";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelCode);
        body.put("messages", List.of(Map.of("role", "user", "content", "ping")));
        body.put("max_tokens", 8);
        body.put("temperature", 0);
        body.put("stream", false);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TEST_CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(TEST_READ_TIMEOUT);
        RestClient client = RestClient.builder().requestFactory(requestFactory).requestInterceptor(com.internship.util.QwenRequestSupport.interceptor()).build();

        log.info("测试AI模型连接，地址: {}，实际请求模型: {}", normalizedEndpoint, modelCode);
        long started = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", normalizedEndpoint);
        result.put("requestModel", modelCode);
        try {
            var request = client.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
            // 中转网关（如 Zen Go）要求 x-opencode-session 路由头，官方直连时为空 Map，无影响。
            for (Map.Entry<String, String> gatewayHeader
                    : AiEndpointSupport.extraHeaders(normalizedEndpoint, modelCode).entrySet()) {
                request = request.header(gatewayHeader.getKey(), gatewayHeader.getValue());
            }
            String raw = request
                    .body(body)
                    .retrieve()
                    .body(String.class);
            long latencyMs = System.currentTimeMillis() - started;
            result.put("ok", true);
            result.put("httpStatus", 200);
            result.put("latencyMs", latencyMs);
            result.put("reply", extractReply(raw));
            result.put("error", null);
            log.info("测试AI模型连接成功，延迟 {}ms", latencyMs);
        } catch (HttpStatusCodeException e) {
            long latencyMs = System.currentTimeMillis() - started;
            result.put("ok", false);
            result.put("httpStatus", e.getStatusCode().value());
            result.put("latencyMs", latencyMs);
            result.put("reply", null);
            result.put("error", "厂商返回 " + e.getStatusCode().value() + "：" + truncate(e.getResponseBodyAsString(), 500));
            log.warn("测试AI模型连接被厂商拒绝，状态: {}", e.getStatusCode());
        } catch (ResourceAccessException e) {
            long latencyMs = System.currentTimeMillis() - started;
            result.put("ok", false);
            result.put("httpStatus", null);
            result.put("latencyMs", latencyMs);
            result.put("reply", null);
            result.put("error", "网络不可达或超时（连接5秒/读取30秒）：" + rootMessage(e));
            log.warn("测试AI模型连接超时或不可达: {}", rootMessage(e));
        } catch (Exception e) {
            long latencyMs = System.currentTimeMillis() - started;
            result.put("ok", false);
            result.put("httpStatus", null);
            result.put("latencyMs", latencyMs);
            result.put("reply", null);
            result.put("error", "测试失败：" + rootMessage(e));
            log.warn("测试AI模型连接失败: {}", rootMessage(e));
        }
        return result;
    }

    /**
     * 新增/更新共用的字段归一化与校验。
     *
     * @param forInsert 新增时展示名/请求模型/提供商必填；更新时只校验“本次传了的字段”，
     *                  空 Key/空地址统一置 null（Mapper 层跳过 = 保持原值）。
     */
    private void normalizeForSave(AIModel aiModel, boolean forInsert) {
        if (!AiEndpointSupport.isBlank(aiModel.getModelName())) {
            aiModel.setModelName(aiModel.getModelName().trim());
        }
        if (!AiEndpointSupport.isBlank(aiModel.getModelCode())) {
            aiModel.setModelCode(aiModel.getModelCode().trim());
        }
        if (!AiEndpointSupport.isBlank(aiModel.getProvider())) {
            aiModel.setProvider(aiModel.getProvider().trim().toLowerCase());
        }

        if (forInsert) {
            // 新增时空 Key/空地址统一存 NULL（与“未配置”语义一致，避免 '' 残留）。
            if (AiEndpointSupport.isBlank(aiModel.getApiKey())) {
                aiModel.setApiKey(null);
            }
            if (AiEndpointSupport.isBlank(aiModel.getApiEndpoint())) {
                aiModel.setApiEndpoint(null);
            }
            if (AiEndpointSupport.isBlank(aiModel.getModelName())) {
                throw new BusinessException("模型名称不能为空");
            }
            if (AiEndpointSupport.isBlank(aiModel.getModelCode())) {
                throw new BusinessException("实际请求模型不能为空");
            }
            if (AiEndpointSupport.isBlank(aiModel.getProvider())) {
                throw new BusinessException("提供商不能为空");
            }
            if (aiModelMapper.findByModelCode(aiModel.getModelCode()) != null) {
                throw new BusinessException("实际请求模型已存在: " + aiModel.getModelCode());
            }
        } else {
            if (aiModel.getModelName() != null && aiModel.getModelName().trim().isEmpty()) {
                throw new BusinessException("模型名称不能为空");
            }
            if (aiModel.getModelCode() != null && aiModel.getModelCode().trim().isEmpty()) {
                throw new BusinessException("实际请求模型不能为空");
            }
            // 空 Key / 空地址 = 保持原值（清空请用 clearApiConfig）。
            if (AiEndpointSupport.isBlank(aiModel.getApiKey())) {
                aiModel.setApiKey(null);
            }
            if (AiEndpointSupport.isBlank(aiModel.getApiEndpoint())) {
                aiModel.setApiEndpoint(null);
            }
        }

        if (aiModel.getModelName() != null && aiModel.getModelName().length() > 100) {
            throw new BusinessException("模型名称过长（最多 100 个字符）");
        }
        if (aiModel.getModelCode() != null && !MODEL_CODE_PATTERN.matcher(aiModel.getModelCode()).matches()) {
            throw new BusinessException("实际请求模型格式不正确（字母数字打头，可含字母/数字/点/横线/下划线，最长 50）");
        }
        if (aiModel.getProvider() != null && !ALLOWED_PROVIDERS.contains(aiModel.getProvider())) {
            throw new BusinessException("不支持的提供商: " + aiModel.getProvider());
        }
        if (!AiEndpointSupport.isBlank(aiModel.getApiKey()) && aiModel.getApiKey().trim().length() > 255) {
            throw new BusinessException("API Key 过长（最多 255 个字符）");
        }
        if (!AiEndpointSupport.isBlank(aiModel.getApiEndpoint())) {
            try {
                aiModel.setApiEndpoint(AiEndpointSupport.normalizeEndpoint(aiModel.getApiEndpoint()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(e.getMessage());
            }
        }
        if (Boolean.TRUE.equals(aiModel.getClearApiConfig())) {
            aiModel.setApiEndpoint(null);
            aiModel.setApiKey(null);
        }
    }

    private String uniqueCopyCode(String baseCode) {
        String base = AiEndpointSupport.isBlank(baseCode) ? "custom-model" : baseCode.trim();
        String candidate = truncate(base + "-copy", 50);
        int index = 2;
        while (aiModelMapper.findByModelCode(candidate) != null) {
            candidate = truncate(base + "-copy-" + index, 50);
            index++;
        }
        return candidate;
    }

    private void refreshQuietly() {
        try {
            aiConfigRefreshService.refreshAIConfig();
            log.info("AI模型配置已刷新，新配置将立即生效");
        } catch (Exception e) {
            log.error("刷新AI模型配置失败，但数据已更新", e);
        }
    }

    private String extractReply(String raw) {
        if (AiEndpointSupport.isBlank(raw)) {
            return "";
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            JsonNode choices = root.path("choices");
            if (choices.isArray() && !choices.isEmpty()) {
                return truncate(choices.get(0).path("message").path("content").asText(""), 200);
            }
        } catch (Exception e) {
            log.debug("解析厂商回复失败，返回原文片段", e);
        }
        return truncate(raw, 200);
    }

    private String rootMessage(Exception e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String message = t.getMessage();
        return truncate(message == null ? e.toString() : message, 300);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
