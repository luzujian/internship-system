package com.internship.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** Bounds anonymous registration writes without trusting proxy-supplied IP headers. */
@Slf4j
@Service
public class CompanyRegistrationRateLimitService {
    private static final DefaultRedisScript<Long> LIMIT_SCRIPT = new DefaultRedisScript<>();

    static {
        LIMIT_SCRIPT.setResultType(Long.class);
        LIMIT_SCRIPT.setScriptText("""
                local global = redis.call('INCR', KEYS[1])
                if global == 1 then redis.call('EXPIRE', KEYS[1], 3600) end
                local company = redis.call('INCR', KEYS[2])
                if company == 1 then redis.call('EXPIRE', KEYS[2], 3600) end
                local email = redis.call('INCR', KEYS[3])
                if email == 1 then redis.call('EXPIRE', KEYS[3], 3600) end
                if global > 200 or company > 10 or email > 10 then return 0 end
                return 1
                """);
    }

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    public boolean allow(String companyName, String email) {
        if (redisTemplate == null || companyName == null || companyName.isBlank()
                || email == null || email.isBlank()) return false;
        try {
            Long allowed = redisTemplate.execute(LIMIT_SCRIPT, List.of(
                    "company:registration:hour",
                    "company:registration:name:" + hash(companyName),
                    "company:registration:email:" + hash(email)));
            return Long.valueOf(1L).equals(allowed);
        } catch (Exception e) {
            log.warn("企业注册限流暂不可用", e);
            return false;
        }
    }

    private String hash(String value) throws Exception {
        byte[] bytes = value.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
