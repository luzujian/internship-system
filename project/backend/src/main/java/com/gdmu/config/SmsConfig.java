package com.gdmu.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "sms")
public class SmsConfig {
    
    private String accessKeyId;
    
    private String accessKeySecret;
    
    private String signName;
    
    private String templateCode;
    
    private Boolean enabled = false;
    
    private Boolean debug = true;
}
