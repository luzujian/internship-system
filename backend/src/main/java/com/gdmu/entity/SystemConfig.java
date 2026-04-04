package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class SystemConfig {
    
    private Long id;
    
    private String configKey;
    
    private String configValue;
    
    private String configType;
    
    private String configName;
    
    private String description;
    
    private Integer status;
    
    private Date createTime;
    
    private Date updateTime;
    
    private Integer deleted;
}
