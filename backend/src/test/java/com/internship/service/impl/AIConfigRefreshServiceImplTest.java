package com.internship.service.impl;

import com.internship.entity.AIModel;
import com.internship.mapper.AIModelMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AIConfigRefreshServiceImplTest {
    @Test void gatewayModelIdentifiersRemainCaseSensitiveAfterRefresh() {
        var mapper=mock(AIModelMapper.class);
        var service=new AIConfigRefreshServiceImpl();
        ReflectionTestUtils.setField(service,"aiModelMapper",mapper);
        var upper=new AIModel();upper.setModelCode("Gateway/Model");
        var lower=new AIModel();lower.setModelCode("gateway/model");
        when(mapper.findEnabledModels()).thenReturn(List.of(upper,lower));
        service.refreshAIConfig();
        assertSame(upper,service.getModelConfig("Gateway/Model"));
        assertSame(lower,service.getModelConfig("gateway/model"));
        assertEquals(2,service.getAllModelConfigs().size());
        assertNull(service.getModelConfig(null));
        when(mapper.findEnabledModels()).thenReturn(List.of(lower));
        service.refreshAIConfig();
        assertNull(service.getModelConfig("Gateway/Model"));
        assertSame(lower,service.getModelConfig("gateway/model"));
    }
}
