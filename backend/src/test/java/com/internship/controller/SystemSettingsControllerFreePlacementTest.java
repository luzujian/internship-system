package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.SystemSettings;
import com.internship.service.SystemSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SystemSettingsControllerFreePlacementTest {
    private SystemSettingsService service;
    private SystemSettingsController controller;

    @BeforeEach
    void setUp() {
        service = mock(SystemSettingsService.class);
        controller = new SystemSettingsController();
        ReflectionTestUtils.setField(controller, "systemSettingsService", service);
    }

    @Test
    void basicSettingsNoLongerExposeDualPhaseOrSelectionWindows() {
        SystemSettings settings = legacySettings();
        when(service.findLatest()).thenReturn(settings);

        Result result = controller.getSettings();

        Map<?, ?> root = (Map<?, ?>) result.getData();
        Map<?, ?> basic = (Map<?, ?>) root.get("basic");
        assertEquals(1, basic.get("systemStatus"));
        assertFalse(basic.containsKey("enableDualPhase"));
        assertFalse(basic.containsKey("dualSelectionStartDate"));
        assertFalse(basic.containsKey("dualSelectionEndDate"));
    }

    @Test
    void legacyDualPhaseWritesAreIgnoredWhileOtherBasicSettingsStillUpdate() {
        SystemSettings settings = legacySettings();
        Date oldStart = settings.getDualSelectionStartDate();
        Date oldEnd = settings.getDualSelectionEndDate();
        when(service.findLatest()).thenReturn(settings);

        Result result = controller.updateSettings(Map.of("basic", Map.of(
                "systemStatus", 0,
                "enableDualPhase", 0,
                "dualSelectionStartDate", "2030-01-01",
                "dualSelectionEndDate", "2030-02-01")));

        assertEquals(200, result.getCode());
        ArgumentCaptor<SystemSettings> saved = ArgumentCaptor.forClass(SystemSettings.class);
        verify(service).update(saved.capture());
        assertEquals(0, saved.getValue().getSystemStatus());
        assertEquals(1, saved.getValue().getEnableDualPhase());
        assertEquals(oldStart, saved.getValue().getDualSelectionStartDate());
        assertEquals(oldEnd, saved.getValue().getDualSelectionEndDate());
    }

    private SystemSettings legacySettings() {
        SystemSettings settings = new SystemSettings();
        settings.setSystemStatus(1);
        settings.setEnableDualPhase(1);
        settings.setDualSelectionStartDate(new Date(1_000L));
        settings.setDualSelectionEndDate(new Date(2_000L));
        return settings;
    }
}
