package com.internship.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyRegistrationRateLimitServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void failsClosedAndDoesNotStoreCompanyNameOrEmailInRateKeys() {
        var service = new CompanyRegistrationRateLimitService();
        assertFalse(service.allow("测试企业", "owner@example.com"));

        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L, 0L);
        assertTrue(service.allow("测试企业", "owner@example.com"));
        assertFalse(service.allow("测试企业", "owner@example.com"));

        var keys = org.mockito.ArgumentCaptor.forClass(List.class);
        verify(redis, times(2)).execute(any(RedisScript.class), keys.capture());
        assertEquals(3, keys.getValue().size());
        assertTrue(keys.getValue().stream().noneMatch(key -> key.toString().contains("owner@example.com")
                || key.toString().contains("测试企业")));
    }
}
