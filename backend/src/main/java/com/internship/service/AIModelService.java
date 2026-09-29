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
     * 复制一条模型配置（ccswitch 式“复制配置”）。
     * Key 随行复制（不出接口明文，由后端直接落库），is_default 恒为 0，
     * modelCode 自动生成唯一值。
     */
    AIModel duplicateModel(Long id, String creator);

    /**
     * 一键测连：用给定（或已保存）的地址/Key/实际请求模型向厂商发一次最小请求，
     * 返回 ok/httpStatus/latencyMs/requestModel/endpoint/reply/error。
     * 仅 ADMIN 可调；Key 永不记日志、不回显。
     */
    Map<String, Object> testConnection(AIModel probe);
}
