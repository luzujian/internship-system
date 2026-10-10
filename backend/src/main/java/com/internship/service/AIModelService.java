package com.internship.service;

import com.internship.entity.AIModel;
import java.util.List;
import java.util.Map;

public interface AIModelService {

    int insert(AIModel aiModel);

    List<AIModel> findAll();

    AIModel findById(Long id);

    AIModel findByModelCode(String modelCode);

    List<AIModel> findEnabledModels();

    AIModel findDefaultModel();

    List<AIModel> findByProvider(String provider);

    int update(AIModel aiModel);

    int setAsDefault(Long id, String updater);

    int deleteById(Long id);

    int deleteAll(String updater);

    List<AIModel> searchByKeyword(String keyword);

    List<AIModel> findByStatus(Integer status);

    /**
     * 管理端输出脱敏：apiKey 只保留掩码并设置 hasApiKey；clearApiConfig 置空。
     * 注意返回的是同一对象（附带掩码），内部调用方如需真实 Key 请直接用 Mapper/未脱敏实体。
     */
    AIModel maskForOutput(AIModel model);

    List<AIModel> maskForOutput(List<AIModel> models);

    /**
     * 返回未保存的配置模板：保留厂商真实模型 ID，清除 id、Key 和默认状态。
     * 由管理端补充不同的真实模型 ID 与凭证后再保存，不伪造可调用的模型 ID。
     */
    AIModel duplicateModel(Long id, String creator);

    /**
     * 一键测连：用给定（或已保存）的地址/Key/实际请求模型向厂商发一次最小请求，
     * 返回 ok/httpStatus/latencyMs/requestModel/endpoint/reply/error。
     * 仅 ADMIN 可调；Key 永不记日志、不回显。
     */
    Map<String, Object> testConnection(AIModel probe);

    /** 从给定网关的 /models 获取模型 ID；表单和已保存配置的合并语义与测连一致。 */
    Map<String, Object> discoverModels(AIModel probe);
}
