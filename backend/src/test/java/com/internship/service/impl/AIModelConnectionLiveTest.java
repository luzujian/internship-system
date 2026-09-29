package com.internship.service.impl;

import com.internship.entity.AIModel;
import com.internship.mapper.AIModelMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 真实厂商联调：直连 DeepSeek 官方接口，验证管理端“一键测连”整条代码路径。
 * 默认跳过；本地验证时以环境变量注入 Key 后单跑：
 *   DEEPSEEK_API_KEY=sk-xxx mvn -Dtest=AIModelConnectionLiveTest test
 * Key 只走进程环境变量，不进仓库、不打日志。
 */
@EnabledIfEnvironmentVariable(named = "DEEPSEEK_API_KEY", matches = ".+")
class AIModelConnectionLiveTest {

    @Test
    void liveProbeAgainstDeepSeek() {
        String key = System.getenv("DEEPSEEK_API_KEY");

        AIModelServiceImpl service = new AIModelServiceImpl(mock(AIModelMapper.class));

        // 网关联调（如 Zen Go）时覆盖接入点与模型：AI_LIVE_ENDPOINT / AI_LIVE_MODEL；Key 仍只走 DEEPSEEK_API_KEY 环境变量。
        String endpoint = System.getenv().getOrDefault("AI_LIVE_ENDPOINT", "https://api.deepseek.com");
        String model = System.getenv().getOrDefault("AI_LIVE_MODEL", "deepseek-chat");
        AIModel probe = new AIModel();
        probe.setApiEndpoint(endpoint);
        probe.setApiKey(key);
        probe.setModelCode(model);

        Map<String, Object> result = service.testConnection(probe);
        System.out.printf("LIVE_PROBE ok=%s http=%s latencyMs=%s reply=%s error=%s%n",
                result.get("ok"), result.get("httpStatus"), result.get("latencyMs"),
                result.get("reply"), result.get("error"));

        assertEquals(true, result.get("ok"));
        assertEquals(200, result.get("httpStatus"));
    }
}
