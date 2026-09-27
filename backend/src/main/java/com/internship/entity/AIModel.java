package com.internship.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class AIModel {

    private Long id;

    /** 展示名：管理端/页面上显示的名字，可随意修改。 */
    private String modelName;

    /**
     * 实际请求模型：真正发给厂商 API 的 {@code model} 参数。
     * 与展示名分离，二者都允许修改；修改时做唯一性校验。
     */
    private String modelCode;

    private String provider;

    /** 该模型独立的 API 接入点（OpenAI 兼容根地址）；为空则回退全局配置。 */
    private String apiEndpoint;

    /** 该模型独立的 API Key；列表/详情接口只返回脱敏值，空值更新语义为“保持不变”。 */
    private String apiKey;

    private Integer maxTokens;

    private BigDecimal temperature;

    private String description;

    private Integer status;

    private Integer isDefault;

    private Date createTime;

    private Date updateTime;

    private String creator;

    private String updater;

    private Integer deleted;

    /** 非持久化：该模型是否已配置 Key（列表页“已配置/未配置”展示用）。 */
    private Boolean hasApiKey;

    /**
     * 非持久化：更新时传 true 表示清空该模型的独立 API 地址与 Key，
     * 之后该模型回退到全局环境变量配置。
     */
    private Boolean clearApiConfig;

    /** Non-column fields: active voice assignment and server-owned adapter capabilities. */
    private Boolean useForVoice;
    private java.util.List<String> supportedUsages;
}
