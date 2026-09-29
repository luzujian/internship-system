package com.internship.service;

import com.internship.entity.CompanyUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Short-lived proof that the caller knows the application's BCrypt-protected credential. */
@Slf4j
@Service
public class CompanyApplicationAccessService {
    private static final String TOKEN_PREFIX = "company:application:token:";
    private static final String SECRET_ATTEMPT_PREFIX = "company:application:secret-attempt:";
    private static final int TOKEN_TTL_SECONDS = 600;
    private static final SecureRandom RANDOM = new SecureRandom();

    private static final DefaultRedisScript<Long> SECRET_ATTEMPT_SCRIPT = new DefaultRedisScript<>();

    static {
        SECRET_ATTEMPT_SCRIPT.setResultType(Long.class);
        SECRET_ATTEMPT_SCRIPT.setScriptText("""
                local count = redis.call('INCR', KEYS[1])
                if count == 1 then redis.call('EXPIRE', KEYS[1], 3600) end
                return count
                """);
    }

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    /** Uses the BCrypt-protected application credential; guesses are limited per company. */
    public String verifySecretAndIssueToken(CompanyUser company, String secret) {
        if (!validCompany(company) || redisTemplate == null || passwordEncoder == null
                || secret == null || secret.isBlank() || company.getPassword() == null
                || !company.getPassword().startsWith("$2")) return null;
        try {
            Long attempts = redisTemplate.execute(SECRET_ATTEMPT_SCRIPT,
                    List.of(SECRET_ATTEMPT_PREFIX + company.getId()));
            if (attempts == null || attempts > 10) return null;
            if (!passwordEncoder.matches(secret, company.getPassword())) return null;
            return issueToken(company);
        } catch (Exception e) {
            log.warn("企业申请凭据校验暂不可用: companyId={}", company.getId(), e);
            return null;
        }
    }

    private String issueToken(CompanyUser company) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        redisTemplate.opsForValue().set(tokenKey(token), companyBinding(company),
                TOKEN_TTL_SECONDS, TimeUnit.SECONDS);
        return token;
    }

    public boolean hasValidToken(CompanyUser company, String token) {
        if (!validCompany(company) || !validTokenSyntax(token) || redisTemplate == null) return false;
        try {
            return companyBinding(company).equals(redisTemplate.opsForValue().get(tokenKey(token)));
        } catch (Exception e) {
            log.warn("企业申请凭据读取失败: companyId={}", company.getId(), e);
            return false;
        }
    }

    /** A token can authorize one write only, even under concurrent requests. */
    public boolean consumeToken(CompanyUser company, String token) {
        if (!validCompany(company) || !validTokenSyntax(token) || redisTemplate == null) return false;
        try {
            return companyBinding(company).equals(redisTemplate.opsForValue().getAndDelete(tokenKey(token)));
        } catch (Exception e) {
            log.warn("企业申请凭据消费失败: companyId={}", company.getId(), e);
            return false;
        }
    }

    private boolean validCompany(CompanyUser company) {
        String email = company == null ? "" : MailService.normalizeEmail(company.getEmail());
        return company != null && company.getId() != null
                && email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    private boolean validTokenSyntax(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private String companyBinding(CompanyUser company) {
        String account = company.getId() + "|" + MailService.normalizeEmail(company.getEmail())
                + "|" + company.getPassword();
        return sha256(account);
    }

    private String tokenKey(String token) {
        return TOKEN_PREFIX + sha256(token);
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
