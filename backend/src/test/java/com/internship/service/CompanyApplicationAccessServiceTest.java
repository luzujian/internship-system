package com.internship.service;

import com.internship.entity.CompanyUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class CompanyApplicationAccessServiceTest {
    @Test
    @SuppressWarnings("unchecked")
    void tokenIsBoundToCompanyAndConsumedOnce() {
        var service = new CompanyApplicationAccessService();
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> stored = new ConcurrentHashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            stored.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        when(values.get(anyString())).thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
        when(values.getAndDelete(anyString())).thenAnswer(invocation -> stored.remove(invocation.getArgument(0)));
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        var encoder = new BCryptPasswordEncoder();
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);

        CompanyUser original = company(7L, "Original@Example.com");
        original.setPassword(encoder.encode("application-secret"));
        String token = service.verifySecretAndIssueToken(original, "application-secret");
        assertNotNull(token);
        assertTrue(service.hasValidToken(original, token));
        assertFalse(service.hasValidToken(company(8L, "Original@Example.com"), token));
        assertFalse(service.hasValidToken(company(7L, "changed@example.com"), token));
        assertTrue(service.consumeToken(original, token));
        assertFalse(service.consumeToken(original, token));
        assertFalse(service.hasValidToken(original, token));
        assertFalse(stored.keySet().stream().anyMatch(key -> key.contains(token)));
    }

    @Test
    void missingRedisFailsClosed() {
        var service = new CompanyApplicationAccessService();
        CompanyUser original = company(7L, "original@example.com");
        assertNull(service.verifySecretAndIssueToken(original, "secret"));
        assertFalse(service.hasValidToken(original, "x".repeat(43)));
        assertFalse(service.consumeToken(original, "x".repeat(43)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void applicationSecretIssuesRevocableTokenAndLimitsGuesses() {
        var service = new CompanyApplicationAccessService();
        RedisTemplate<String, String> redis = mock(RedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        Map<String, String> stored = new ConcurrentHashMap<>();
        when(redis.opsForValue()).thenReturn(values);
        doAnswer(invocation -> {
            stored.put(invocation.getArgument(0), invocation.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS));
        when(values.get(anyString())).thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
        when(redis.execute(any(RedisScript.class), anyList())).thenReturn(1L, 1L, 11L);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        var encoder = new BCryptPasswordEncoder();
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);

        CompanyUser company = company(7L, "original@example.com");
        company.setPassword(encoder.encode("application-secret"));
        assertNull(service.verifySecretAndIssueToken(company, "wrong"));
        String token = service.verifySecretAndIssueToken(company, "application-secret");
        assertNotNull(token);
        assertTrue(service.hasValidToken(company, token));
        company.setPassword(encoder.encode("changed-secret"));
        assertFalse(service.hasValidToken(company, token));
        assertNull(service.verifySecretAndIssueToken(company, "changed-secret"));
    }

    private CompanyUser company(Long id, String email) {
        CompanyUser company = new CompanyUser();
        company.setId(id);
        company.setEmail(email);
        return company;
    }
}
