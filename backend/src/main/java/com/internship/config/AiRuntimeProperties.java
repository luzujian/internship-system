package com.internship.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

/** Conservative per-instance limits. All model entry points share the same call guard. */
@Component
@ConfigurationProperties(prefix = "ai.runtime")
@Validated
@Getter
@Setter
public class AiRuntimeProperties {
    @Min(1) @Max(64) private int maxConcurrentCalls = 8;
    @Min(1) private int maxRequestsPerMinute = 120;
    @Min(1) private int maxUserRequestsPerMinute = 10;
    @Min(1) @Max(30000) private int connectTimeoutMillis = 10000;
    @Min(1) @Max(60000) private int readTimeoutMillis = 40000;
    @Min(1) @Max(120000) private long streamTimeoutMillis = 90000;
    @Min(1) @Max(32) private int agentWorkers = 4;
    @Min(1) @Max(256) private int agentQueueCapacity = 16;
    @Min(1) @Max(60000) private long agentQueueTimeoutMillis = 30000;
    @Min(1) @Max(120000) private long agentTaskTimeoutMillis = 90000;
    @Min(1) @Max(5000) private long streamAuthRefreshMillis = 2000;
    @Min(1) @Max(64) private int titleQueueCapacity = 16;
}
