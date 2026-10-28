package com.internship.support;

import com.internship.entity.SystemConfig;
import com.internship.service.ReflectionFeatureService;
import com.internship.service.SystemConfigService;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.*;

/** Legacy behavior is tested with an explicit opt-in; production still defaults to off. */
public final class ReflectionFeatureTestSupport {
    private ReflectionFeatureTestSupport() { }

    public static void enable(Object target) {
        ReflectionFeatureService feature = mock(ReflectionFeatureService.class);
        lenient().when(feature.enabled()).thenReturn(true);
        ReflectionTestUtils.setField(target, "reflectionFeature", feature);
    }

    public static void enableConfig(SystemConfigService configs) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(ReflectionFeatureService.CONFIG_KEY);
        config.setConfigValue("true");
        config.setStatus(1);
        lenient().when(configs.findByConfigKey(ReflectionFeatureService.CONFIG_KEY)).thenReturn(config);
    }
}
