package com.internship.service;

import com.internship.config.SmsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SmsServiceDisabledModeTest {
    private static final String PHONE = "13800138000";
    private static final String KEY = "sms:code:" + PHONE;

    @Test
    void disabledSmsCannotSendOrVerifyInProductionOrLocal() {
        for (String profile : new String[] { "prod", "local" }) {
            RedisTemplate<String, String> redis = redis();
            SmsService service = service(profile, false, true, redis);

            assertFalse(service.checkRateLimit(PHONE));
            assertFalse(service.sendVerifyCode(PHONE));
            assertFalse(service.verifyCode(PHONE, "123456"));
            verifyNoInteractions(redis);
        }
    }

    @Test
    void debugModeCannotSendOrVerifyInProductionEvenWhenEnabled() {
        RedisTemplate<String, String> redis = redis();
        SmsService service = service("prod", true, true, redis);

        assertFalse(service.sendVerifyCode(PHONE));
        assertFalse(service.verifyCode(PHONE, "123456"));
        verifyNoInteractions(redis);
    }

    @Test
    void localDebugRequiresRedisAndConsumesMatchingCodeOnlyOnce() {
        SmsService withoutRedis = service("local", true, true, null);
        assertFalse(withoutRedis.checkRateLimit(PHONE));
        assertFalse(withoutRedis.sendVerifyCode(PHONE));
        assertFalse(withoutRedis.verifyCode(PHONE, "123456"));

        RedisTemplate<String, String> redis = redis();
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        AtomicReference<String> stored = new AtomicReference<>();
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(values).set(eq(KEY), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        when(values.get(KEY)).thenAnswer(invocation -> stored.get());
        when(redis.delete(KEY)).thenAnswer(invocation -> stored.getAndSet(null) != null);
        SmsService service = service("local", true, true, redis);

        assertTrue(service.sendVerifyCode(PHONE));
        String code = stored.get();
        assertNotNull(code);
        assertTrue(code.matches("[0-9]{6}"));
        verify(values).set(eq(KEY), eq(code), eq(5L), eq(TimeUnit.MINUTES));
        assertFalse(service.verifyCode(PHONE, "000000"));
        assertTrue(service.verifyCode(PHONE, code));
        assertFalse(service.verifyCode(PHONE, code));
    }

    @Test
    void malformedPhoneIsRejectedBeforeAnyProviderOrRedisCall() {
        RedisTemplate<String, String> redis = redis();
        SmsService service = service("local", true, true, redis);

        assertFalse(service.sendVerifyCode("not-a-phone"));
        assertFalse(service.verifyCode("not-a-phone", "123456"));
        verifyNoInteractions(redis);
    }

    @Test
    void realSmsSendFailsClosedWhenRedisIsMissingOrThrows() {
        SmsService withoutRedis = service("prod", true, false, null);
        assertFalse(withoutRedis.checkRateLimit(PHONE));
        assertFalse(withoutRedis.sendVerifyCode(PHONE));

        RedisTemplate<String, String> failingRedis = redis();
        when(failingRedis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));
        SmsService withFailure = service("prod", true, false, failingRedis);
        assertFalse(withFailure.checkRateLimit(PHONE));
        assertFalse(withFailure.sendVerifyCode(PHONE));
    }

    private SmsService service(String profile, boolean enabled, boolean debug,
                               RedisTemplate<String, String> redis) {
        SmsConfig config = new SmsConfig();
        config.setEnabled(enabled);
        config.setDebug(debug);
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        SmsService service = new SmsService();
        ReflectionTestUtils.setField(service, "smsConfig", config);
        ReflectionTestUtils.setField(service, "environment", environment);
        ReflectionTestUtils.setField(service, "redisTemplate", redis);
        return service;
    }

    @SuppressWarnings("unchecked")
    private RedisTemplate<String, String> redis() {
        return mock(RedisTemplate.class);
    }
}
