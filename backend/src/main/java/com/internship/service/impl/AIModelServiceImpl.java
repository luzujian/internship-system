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
import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

@Slf4j
@Service
public class AIModelServiceImpl implements AIModelService {

    /** 管理端可选的提供商；openai/custom 用于任意 OpenAI 兼容网关。 */
    private static final List<String> ALLOWED_PROVIDERS =
            List.of("deepseek", "openai", "custom", "qwen", "zhipu", "moonshot", "doubao",
                    "baidu", "tencent", "minimax", "spark", "siliconflow", "stepfun", "baichuan", "yi");

    /** 保留厂商命名空间和版本分隔符；长度仍与现有 DB varchar(50) 对齐。 */
    private static final Pattern MODEL_CODE_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:/@+-]{0,49}$");

    /** 推理模型也需要足够的读取与输出预算；管理端单次请求不重试。 */
    private static final Duration TEST_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration TEST_READ_TIMEOUT = Duration.ofSeconds(85);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AIModelMapper aiModelMapper;

    @Value("${spring.ai.deepseek.base-url:https://api.deepseek.com}")
    private String globalEndpoint = "https://api.deepseek.com";

    @Value("${spring.ai.deepseek.api-key:}")
    private String globalApiKey;

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
        validateCredentialChange(aiModel, null);

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
        if (aiModel == null || aiModel.getId() == null) {
            throw new BusinessException("AI模型信息或ID不能为空");
        }
        log.debug("更新AI模型配置，ID: {}", aiModel.getId());

        AIModel existing = aiModelMapper.findById(aiModel.getId());
        if (existing == null) {
            throw new BusinessException("AI模型不存在");
        }

        boolean currentChatModel = Integer.valueOf(1).equals(existing.getIsDefault());
        boolean currentVoiceModel = voiceSettings != null && voiceSettings.selectedId() == existing.getId();
        if (aiModel.getStatus() != null && !Integer.valueOf(1).equals(aiModel.getStatus())
                && (currentChatModel || currentVoiceModel)) {
            throw new BusinessException("当前聊天或语音模型不能停用，请先选择其他模型作为对应用途的默认模型。");
        }
        if (currentChatModel && aiModel.getIsDefault() != null
                && !Integer.valueOf(1).equals(aiModel.getIsDefault())) {
            throw new BusinessException("当前聊天默认模型不能通过编辑取消，请先将其他聊天模型设为默认。");
        }

        normalizeForSave(aiModel, false);
        validateCredentialChange(aiModel, existing);

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
        copy.setModelCode(source.getModelCode());
        copy.setProvider(source.getProvider());
        copy.setApiEndpoint(source.getApiEndpoint());
        copy.setHasApiKey(false);
        copy.setMaxTokens(source.getMaxTokens());
        copy.setTemperature(source.getTemperature());
        copy.setDescription(source.getDescription());
        copy.setStatus(0);
        copy.setIsDefault(0);
        copy.setUseForVoice(false);
        copy.setCreator(creator);
        return copy;
    }

    @Override
    public Map<String, Object> testConnection(AIModel probe) {
        ProbeConfiguration configuration = resolveProbe(probe);
        String normalizedEndpoint = configuration.connection().endpoint();
        String apiKey = configuration.connection().apiKey();
        String modelCode = configuration.modelCode();
        if (AiEndpointSupport.isBlank(modelCode)) {
            throw new BusinessException("未配置实际请求模型，请先填写 model");
        }
        if (normalizedEndpoint.endsWith("/realtime")) throw new BusinessException("实时语音模型请保存后通过录音验证；此按钮只测试通用对话接口。");
        if (!com.internship.util.AIModelCapabilities.supported(modelCode).contains("chat")) {
            throw new BusinessException("该模型不支持通用对话，请选择聊天模型后测试。");
        }
        int maxTokens = configuration.maxTokens() == null ? 4096 : configuration.maxTokens();
        if (maxTokens < 1 || maxTokens > 8192) {
            throw new BusinessException("最大Token数必须在 [1, 8192] 范围内");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", modelCode);
        body.put("messages", List.of(Map.of("role", "user", "content", "请只回复 OK。")));
        body.put("max_tokens", maxTokens);
        body.put("stream", false);
        long started = System.currentTimeMillis();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("endpoint", normalizedEndpoint);
        result.put("requestModel", modelCode);
        result.put("ok", false);
        result.put("httpStatus", null);
        result.put("reply", null);
        result.put("error", null);
        try {
            var request = probeClient().post()
                    .uri(normalizedEndpoint + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
            for (Map.Entry<String, String> gatewayHeader
                    : AiEndpointSupport.extraHeaders(normalizedEndpoint, modelCode).entrySet()) {
                request = request.header(gatewayHeader.getKey(), gatewayHeader.getValue());
            }
            var response = request.body(body).retrieve().toEntity(String.class);
            result.put("httpStatus", response.getStatusCode().value());
            String reply = extractReply(response.getBody());
            result.put("ok", true);
            result.put("reply", redact(reply, apiKey));
        } catch (HttpStatusCodeException e) {
            result.put("httpStatus", e.getStatusCode().value());
            result.put("error", vendorError(e.getStatusCode().value(), e.getResponseBodyAsString()));
        } catch (BusinessException e) {
            result.put("error", e.getMessage());
        } catch (ResourceAccessException e) {
            result.put("error", "网络不可达或超时（连接 5 秒 / 读取 85 秒），请检查网关地址、网络或稍后重试。");
        } catch (Exception e) {
            result.put("error", "无法读取网关响应，请确认使用 OpenAI Chat Completions 兼容接口。");
        }
        result.put("latencyMs", System.currentTimeMillis() - started);
        return result;
    }

    @Override
    public Map<String, Object> discoverModels(AIModel probe) {
        ProbeConfiguration configuration = resolveProbe(probe);
        String endpoint = configuration.connection().endpoint();
        if (endpoint.endsWith("/realtime")) {
            throw new BusinessException("实时语音地址不提供通用模型列表，请使用 OpenAI 兼容基地址。");
        }
        try {
            var request = probeClient().get().uri(endpoint + "/models")
                    .header("Authorization", "Bearer " + configuration.connection().apiKey())
                    .header("User-Agent", AiEndpointSupport.CLIENT_USER_AGENT);
            for (var entry : AiEndpointSupport.extraHeaders(endpoint, configuration.modelCode()).entrySet()) {
                request = request.header(entry.getKey(), entry.getValue());
            }
            JsonNode root = readResponse(request.retrieve().body(String.class));
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                throw new BusinessException("网关没有返回 OpenAI 兼容模型列表，请手动填写实际模型 ID。");
            }
            var models = new LinkedHashSet<String>();
            for (JsonNode row : data) {
                JsonNode id = row.path("id");
                if (id.isTextual() && !AiEndpointSupport.isBlank(id.asText())) {
                    String code = id.asText().trim();
                    if (!code.contains(configuration.connection().apiKey())) models.add(code);
                }
            }
            return Map.of("models", new ArrayList<>(models), "endpoint", endpoint);
        } catch (HttpStatusCodeException e) {
            throw new BusinessException(vendorError(e.getStatusCode().value(), e.getResponseBodyAsString())
                    + " 若网关不提供模型列表，可手动填写模型 ID。");
        } catch (BusinessException e) {
            throw e;
        } catch (ResourceAccessException e) {
            throw new BusinessException("获取模型列表时网络不可达或超时，请检查网关地址或手动填写模型 ID。");
        } catch (Exception e) {
            throw new BusinessException("无法读取网关模型列表，请手动填写实际模型 ID。");
        }
    }

    private record ProbeConfiguration(AiEndpointSupport.Connection connection, String modelCode, Integer maxTokens) {}

    private ProbeConfiguration resolveProbe(AIModel probe) {
        if (probe == null) throw new BusinessException("缺少连接参数");
        AIModel stored = null;
        if (probe.getId() != null) {
            if (probe.getId() <= 0) throw new BusinessException("AI模型ID无效");
            stored = aiModelMapper.findById(probe.getId());
            if (stored == null) throw new BusinessException("AI模型不存在");
        }
        String endpoint = firstConfigured(probe.getApiEndpoint(), stored == null ? null : stored.getApiEndpoint());
        String apiKey = firstConfigured(probe.getApiKey(), stored == null ? null : stored.getApiKey());
        String modelCode = firstConfigured(probe.getModelCode(), stored == null ? null : stored.getModelCode());
        Integer maxTokens = probe.getMaxTokens() == null
                ? (stored == null ? null : stored.getMaxTokens()) : probe.getMaxTokens();
        if (Boolean.TRUE.equals(probe.getClearApiConfig())) {
            endpoint = null;
            apiKey = null;
        } else {
            validateCredentialChange(probe, stored);
        }
        try {
            return new ProbeConfiguration(AiEndpointSupport.resolveConnection(endpoint, apiKey, globalEndpoint, globalApiKey), modelCode, maxTokens);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
    }

    private void validateCredentialChange(AIModel input, AIModel stored) {
        if (Boolean.TRUE.equals(input.getClearApiConfig())) return;
        String providedKey = input.getApiKey();
        if (!AiEndpointSupport.isBlank(providedKey) && providedKey.contains("****")) {
            throw new BusinessException("不能使用脱敏后的 API Key，请留空沿用已保存 Key，或重新输入完整 Key。");
        }
        if (AiEndpointSupport.isBlank(input.getApiEndpoint())) return;
        String endpoint;
        String previousEndpoint;
        try {
            endpoint = AiEndpointSupport.normalizeEndpoint(input.getApiEndpoint());
            previousEndpoint = AiEndpointSupport.normalizeEndpoint(stored == null ? null : stored.getApiEndpoint());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(e.getMessage());
        }
        if (AiEndpointSupport.isBlank(providedKey)
                && (!Objects.equals(endpoint, previousEndpoint) || stored == null || AiEndpointSupport.isBlank(stored.getApiKey()))) {
            throw new BusinessException("新增或更换网关地址时，请重新填写该网关的 API Key。");
        }
    }

    private String firstConfigured(String value, String fallback) {
        return AiEndpointSupport.isBlank(value) ? fallback : value.trim();
    }

    private RestClient probeClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TEST_CONNECT_TIMEOUT);
        factory.setReadTimeout(TEST_READ_TIMEOUT);
        return RestClient.builder().requestFactory(factory)
                .requestInterceptor(com.internship.util.QwenRequestSupport.interceptor()).build();
    }

    /**
     * 新增/更新共用的字段归一化与校验。
     *
     * @param forInsert 新增时展示名/请求模型/提供商必填；更新时只校验“本次传了的字段”，
     *                  空 Key/空地址统一置 null（Mapper 层跳过 = 保持原值）。
     */
    private void normalizeForSave(AIModel aiModel, boolean forInsert) {
        if (Boolean.TRUE.equals(aiModel.getClearApiConfig())) {
            aiModel.setApiEndpoint(null);
            aiModel.setApiKey(null);
        }
        if (!AiEndpointSupport.isBlank(aiModel.getModelName())) {
            aiModel.setModelName(aiModel.getModelName().trim());
        }
        if (!AiEndpointSupport.isBlank(aiModel.getModelCode())) {
            aiModel.setModelCode(aiModel.getModelCode().trim());
        }
        if (!AiEndpointSupport.isBlank(aiModel.getProvider())) {
            aiModel.setProvider(aiModel.getProvider().trim().toLowerCase(Locale.ROOT));
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
            throw new BusinessException("实际请求模型格式不正确（字母数字打头，可含 . _ - / : @ +，最长 50 个字符）");
        }
        if (aiModel.getProvider() != null && !ALLOWED_PROVIDERS.contains(aiModel.getProvider())) {
            throw new BusinessException("不支持的提供商: " + aiModel.getProvider());
        }
        if (!AiEndpointSupport.isBlank(aiModel.getApiKey()) && aiModel.getApiKey().trim().length() > 255) {
            throw new BusinessException("API Key 过长（最多 255 个字符）");
        }
        if (!AiEndpointSupport.isBlank(aiModel.getApiKey())) {
            aiModel.setApiKey(aiModel.getApiKey().trim());
            if (aiModel.getApiKey().chars().anyMatch(Character::isISOControl)) {
                throw new BusinessException("API Key 不能包含换行或控制字符");
            }
        }
        if (!AiEndpointSupport.isBlank(aiModel.getApiEndpoint())) {
            try {
                aiModel.setApiEndpoint(AiEndpointSupport.normalizeEndpoint(aiModel.getApiEndpoint()));
            } catch (IllegalArgumentException e) {
                throw new BusinessException(e.getMessage());
            }
        }
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
        JsonNode root = readResponse(raw);
        JsonNode choices = root.path("choices");
        if (choices.isArray() && !choices.isEmpty()) {
            JsonNode message = choices.get(0).path("message");
            JsonNode content = message.path("content");
            if (content.isTextual() && !AiEndpointSupport.isBlank(content.asText())) {
                return content.asText().trim();
            }
            if (!AiEndpointSupport.isBlank(message.path("reasoning_content").asText())
                    || "length".equals(choices.get(0).path("finish_reason").asText())) {
                throw new BusinessException("网关已返回，但模型在输出预算内未产生聊天正文；请检查推理设置或模型配置后重试。");
            }
        }
        throw new BusinessException("网关未返回有效的聊天正文，请确认模型支持 OpenAI Chat Completions 接口。");
    }

    private JsonNode readResponse(String raw) {
        if (AiEndpointSupport.isBlank(raw)) throw new BusinessException("网关返回了空响应，请检查 API 地址与模型 ID。");
        JsonNode root;
        try {
            root = OBJECT_MAPPER.readTree(raw);
        } catch (Exception e) {
            throw new BusinessException("网关未返回有效 JSON，可能填写了网页地址，请检查 API 基地址。");
        }
        if (root == null || !root.isObject()) throw new BusinessException("网关响应格式不兼容，请检查 API 地址。");
        if (root.hasNonNull("error")) throw new BusinessException(vendorError(200, raw));
        return root;
    }

    /** 只分类厂商错误，绝不将厂商原文或异常消息中的凭证带回页面/日志。 */
    private String vendorError(int status, String raw) {
        String hint = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        String prefix = "网关返回 HTTP " + status + "：";
        if (status == 401 || status == 403 || hint.contains("invalid_api_key") || hint.contains("authentication")) {
            return prefix + "鉴权失败，请检查 API Key、所属网关与模型访问权限。";
        }
        if (status == 429 || hint.contains("insufficient_quota") || hint.contains("rate_limit")) {
            return prefix + "调用频率或可用额度受限，请检查配额并稍后重试。";
        }
        if (status == 404 || hint.contains("model_not_found") || hint.contains("model_not_exist")) {
            return prefix + "接口或模型不存在，请检查基地址、真实模型 ID 及网关支持情况。";
        }
        if (status >= 500) return prefix + "上游服务暂时不可用，请稍后重试。";
        return prefix + "请求被拒绝，请检查模型权限与 OpenAI Chat Completions 协议兼容性。";
    }

    private String redact(String value, String apiKey) {
        String safe = AiEndpointSupport.isBlank(apiKey) ? value : value.replace(apiKey, "[已隐藏凭证]");
        return truncate(safe, 200);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
