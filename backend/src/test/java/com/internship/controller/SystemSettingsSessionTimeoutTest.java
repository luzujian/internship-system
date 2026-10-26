package com.internship.controller;

import com.internship.entity.SystemSettings;
import com.internship.service.SystemSettingsService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.HashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SystemSettingsSessionTimeoutTest {
    @Test void invalidTimeoutDoesNotWriteAnySettings() {
        for (Object value : new Object[]{null, 0, 14, 481, -1, "15.5", "abc", "999999999999999999999999"}) {
            var service = mock(SystemSettingsService.class);
            var controller = new SystemSettingsController();
            ReflectionTestUtils.setField(controller, "systemSettingsService", service);
            var security = new HashMap<String, Object>(); security.put("sessionTimeout", value);
            var result = controller.updateSettings(Map.of("basic", Map.of("systemStatus", 0), "security", security));
            assertEquals(400, result.getCode());
            verifyNoInteractions(service);
        }
    }

    @Test void supportedTimeoutValuesAreSaved() {
        for (int minutes : new int[]{15, 120, 480}) {
            var service = mock(SystemSettingsService.class);
            var settings = new SystemSettings();
            when(service.findLatest()).thenReturn(settings);
            var controller = new SystemSettingsController();
            ReflectionTestUtils.setField(controller, "systemSettingsService", service);
            assertEquals(200, controller.updateSettings(Map.of("security", Map.of("sessionTimeout", minutes))).getCode());
            verify(service).update(settings);
            assertEquals(minutes, settings.getSessionTimeout());
        }
    }
}
