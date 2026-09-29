package com.internship.service.impl;

import com.internship.entity.AIModel;
import com.internship.mapper.AIModelMapper;
import com.internship.service.AIConfigRefreshService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AIModelEditFunctionalityTest {
    @Mock
    private AIModelMapper aiModelMapper;

    @Mock
    private AIConfigRefreshService aiConfigRefreshService;

    private AIModelServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AIModelServiceImpl(aiModelMapper);
        ReflectionTestUtils.setField(service, "aiConfigRefreshService", aiConfigRefreshService);
    }

    @Test
    void updatesMaxTokens() {
        AIModel existing = model();
        when(aiModelMapper.findById(1L)).thenReturn(existing);
        when(aiModelMapper.update(existing)).thenReturn(1);

        existing.setMaxTokens(8192);

        assertEquals(1, service.update(existing));
        ArgumentCaptor<AIModel> captor = ArgumentCaptor.forClass(AIModel.class);
        verify(aiModelMapper).update(captor.capture());
        assertEquals(8192, captor.getValue().getMaxTokens());
    }

    @Test
    void updatesTemperature() {
        AIModel existing = model();
        when(aiModelMapper.findById(1L)).thenReturn(existing);
        when(aiModelMapper.update(existing)).thenReturn(1);

        existing.setTemperature(new BigDecimal("1.50"));

        assertEquals(1, service.update(existing));
        ArgumentCaptor<AIModel> captor = ArgumentCaptor.forClass(AIModel.class);
        verify(aiModelMapper).update(captor.capture());
        assertEquals(new BigDecimal("1.50"), captor.getValue().getTemperature());
    }

    @Test
    void updatesMaxTokensAndTemperatureTogether() {
        AIModel existing = model();
        when(aiModelMapper.findById(1L)).thenReturn(existing);
        when(aiModelMapper.update(existing)).thenReturn(1);

        existing.setMaxTokens(4096);
        existing.setTemperature(new BigDecimal("0.95"));

        assertEquals(1, service.update(existing));
        ArgumentCaptor<AIModel> captor = ArgumentCaptor.forClass(AIModel.class);
        verify(aiModelMapper).update(captor.capture());
        assertEquals(4096, captor.getValue().getMaxTokens());
        assertEquals(new BigDecimal("0.95"), captor.getValue().getTemperature());
    }

    private AIModel model() {
        AIModel model = new AIModel();
        model.setId(1L);
        model.setModelName("DeepSeek");
        model.setModelCode("deepseek-chat");
        model.setProvider("deepseek");
        model.setMaxTokens(2048);
        model.setTemperature(new BigDecimal("0.70"));
        return model;
    }
}
