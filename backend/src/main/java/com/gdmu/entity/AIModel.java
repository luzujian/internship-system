package com.gdmu.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class AIModel {
    
    private Long id;
    
    private String modelName;
    
    private String modelCode;
    
    private String provider;
    
    private String apiEndpoint;
    
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
}
