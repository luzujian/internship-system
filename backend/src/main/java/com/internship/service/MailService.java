package com.internship.service;

import com.internship.utils.AuditFailureContext;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.io.UnsupportedEncodingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务
 * 生成6位验证码 → 存Redis → 发邮件 → 校验时比对Redis
 */
@Slf4j
@Service
public class MailService {
    @Autowired
    private com.internship.config.LocalRuntimeGuard localRuntimeGuard;

    @Autowired
    private JavaMailSender mailSender;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Value("${spring.mail.username}")
    private String from;

    private static final String CODE_PREFIX = "mail:code:";
    private static final String LIMIT_PREFIX = "mail:limit:";
    private static final int CODE_TTL_MINUTES = 5;
    private static final int RATE_LIMIT_SECONDS = 60;
    private static final int MAX_REQUESTS_PER_MINUTE = 3;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /** 忘记密码业务隔离前缀：mail:code:password-reset:<username>:<email>，与企业注册互不串用 */
    private static final String PWD_RESET_CODE_PREFIX = "mail:code:password-reset:";
    private static final String PWD_RESET_LIMIT_UE_PREFIX = "mail:limit:pwdreset:ue:";
    private static final String PWD_RESET_LIMIT_USER_PREFIX = "mail:limit:pwdreset:user:";
    private static final String PWD_RESET_LIMIT_IP_PREFIX = "mail:limit:pwdreset:ip:";
    /** 验证码输错计数：mail:attempt:pwdreset:<username>:<email>，满 5 次作废验证码 */
    private static final String PWD_RESET_ATTEMPT_PREFIX = "mail:attempt:pwdreset:";
    private static final int PWD_RESET_MAX_ATTEMPTS = 5;
    /** /reset 验码频率限制 Key（与发码限流隔离），60 秒内最多 10 次 */
    private static final String PWD_VERIFY_LIMIT_UE_PREFIX = "mail:limit:pwdverify:ue:";
    private static final String PWD_VERIFY_LIMIT_IP_PREFIX = "mail:limit:pwdverify:ip:";
    private static final int PWD_VERIFY_MAX_PER_MINUTE = 10;

    /** Lua 脚本：原子限流 */
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT;

    static {
        RATE_LIMIT_SCRIPT = new DefaultRedisScript<>();
        RATE_LIMIT_SCRIPT.setScriptText("""
                local c = redis.call('INCR', KEYS[1])
                if c == 1 then
                    redis.call('EXPIRE', KEYS[1], ARGV[1])
                end
                return c
                """);
        RATE_LIMIT_SCRIPT.setResultType(Long.class);
    }

    /**
     * 发送邮箱验证码
     */
    public boolean sendVerifyCode(String email) {
        if (localRuntimeGuard != null && localRuntimeGuard.isLocal()) return false;
        if (!isValidEmail(email)) {
            log.error("邮箱格式不正确: {}", maskEmail(email));
            return false;
        }

        // 归一化邮箱
        String normalizedEmail = email.toLowerCase();

        try {
            String code = generateVerifyCode();

            // 存入 Redis（归一化 key）
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(CODE_PREFIX + normalizedEmail, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            }

            // 发送邮件
            sendMail(normalizedEmail, code);

            log.info("验证码已发送至邮箱: {}", maskEmail(normalizedEmail));
            return true;
        } catch (Exception e) {
            log.error("发送邮箱验证码失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 校验验证码
     */
    public boolean verifyCode(String email, String code) {
        if (email == null || code == null) {
            return false;
        }

        // 归一化邮箱
        String normalizedEmail = email.toLowerCase();

        if (redisTemplate == null) {
            log.error("Redis 不可用，无法校验验证码");
            return false;
        }

        try {
            String storedCode = redisTemplate.opsForValue().get(CODE_PREFIX + normalizedEmail);
            if (storedCode == null) {
                log.warn("验证码不存在或已过期: {}", maskEmail(normalizedEmail));
                return false;
            }
            boolean valid = storedCode.equals(code);
            if (valid) {
                // 验证成功后删除，防止重复使用
                redisTemplate.delete(CODE_PREFIX + normalizedEmail);
                log.info("验证码校验成功: {}", maskEmail(normalizedEmail));
            } else {
                log.warn("验证码校验失败: {}", maskEmail(normalizedEmail));
            }
            return valid;
        } catch (Exception e) {
            log.error("校验验证码异常: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 限流检查：同一邮箱 60 秒内最多 3 次（Lua 原子操作）
     */
    public boolean checkRateLimit(String email) {
        if (redisTemplate == null) {
            return true;
        }

        String key = LIMIT_PREFIX + email.toLowerCase();
        try {
            Long count = redisTemplate.execute(RATE_LIMIT_SCRIPT, List.of(key), String.valueOf(RATE_LIMIT_SECONDS));
            return count != null && count <= MAX_REQUESTS_PER_MINUTE;
        } catch (Exception e) {
            log.error("限流检查失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 发送邮件
     */
    private void sendMail(String to, String code) throws MessagingException, UnsupportedEncodingException {
        sendMail(to, code, "企业注册验证码 - DeepIntern", String.format("""
                您好！

                您正在申请注册企业账号，验证码为：%s

                验证码 %d 分钟内有效，请勿泄露给他人。

                如非本人操作，请忽略此邮件。

                DeepIntern
                """, code, CODE_TTL_MINUTES));
    }

    /**
     * 发送邮件（自定义主题与正文）
     */
    private void sendMail(String to, String code, String subject, String content) throws MessagingException, UnsupportedEncodingException {
        if (localRuntimeGuard != null) localRuntimeGuard.requireExternalService("邮件发送");
        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
        helper.setFrom(from, "DeepIntern");
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(content, false);
        mailSender.send(message);
    }

    /**
     * 发送忘记密码验证码（业务隔离 Key：mail:code:password-reset:<username>:<email>）
     * 邮件发送失败时清理已写入的验证码 Key，避免残留不可用的验证码。
     */
    public boolean sendPasswordResetCode(String username, String email) {
        if (localRuntimeGuard != null && localRuntimeGuard.isLocal()) return false;
        String normalizedUser = normalizeUsername(username);
        String normalizedEmail = normalizeEmail(email);
        if (normalizedUser.isEmpty() || !isValidEmail(normalizedEmail)) {
            log.error("忘记密码发码参数非法");
            return false;
        }
        String key = pwdResetCodeKey(normalizedUser, normalizedEmail);
        try {
            String code = generateVerifyCode();
            if (redisTemplate != null) {
                redisTemplate.opsForValue().set(key, code, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            sendMail(normalizedEmail, code, "找回密码验证码 - DeepIntern", String.format("""
                    您好！

                    您正在找回登录密码，验证码为：%s

                    验证码 %d 分钟内有效，请勿泄露给他人。

                    如非本人操作，请忽略此邮件。

                    DeepIntern
                    """, code, CODE_TTL_MINUTES));
            log.info("找回密码验证码已发送至邮箱: {}", maskEmail(normalizedEmail));
            return true;
        } catch (Exception e) {
            log.error("发送忘记密码验证码失败: {}", e.getMessage(), e);
            AuditFailureContext.authenticationFailure("MAIL_SEND_FAILED", "验证码邮件发送或缓存处理失败", e);
            // 失败补偿：清理可能已写入的验证码，避免残留
            try {
                if (redisTemplate != null) {
                    redisTemplate.delete(key);
                }
            } catch (Exception ex) {
                log.warn("清理忘记密码验证码 Key 失败: {}", ex.getMessage());
            }
            return false;
        }
    }

    /** 忘记密码验证码校验结果 */
    public enum PwdResetVerifyResult {
        /** 校验通过（已消费，一次性） */
        SUCCESS,
        /** 验证码错误（未达上限，可重试） */
        CODE_ERROR,
        /** 验证码不存在或已过期 */
        EXPIRED,
        /** 错误次数超限，验证码已作废 */
        TOO_MANY_ATTEMPTS
    }

    /**
     * 校验并消费忘记密码验证码（成功即删，一次性使用）。
     * 同一 username + email 输错满 {@link #PWD_RESET_MAX_ATTEMPTS} 次后验证码立即作废。
     */
    public PwdResetVerifyResult verifyPasswordResetCode(String username, String email, String code) {
        if (username == null || email == null || code == null) {
            return PwdResetVerifyResult.EXPIRED;
        }
        if (redisTemplate == null) {
            log.error("Redis 不可用，无法校验忘记密码验证码");
            AuditFailureContext.authenticationFailure("AUTH_STATE_UNAVAILABLE", "验证码存储服务不可用");
            return PwdResetVerifyResult.EXPIRED;
        }
        try {
            String user = normalizeUsername(username);
            String mail = normalizeEmail(email);
            String codeKey = pwdResetCodeKey(user, mail);
            String attemptKey = pwdResetAttemptKey(user, mail);

            String failCount = redisTemplate.opsForValue().get(attemptKey);
            if (failCount != null && Integer.parseInt(failCount) >= PWD_RESET_MAX_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                log.warn("忘记密码验证码错误次数超限，已作废");
                return PwdResetVerifyResult.TOO_MANY_ATTEMPTS;
            }

            String storedCode = redisTemplate.opsForValue().get(codeKey);
            if (storedCode == null) {
                log.warn("忘记密码验证码不存在或已过期");
                return PwdResetVerifyResult.EXPIRED;
            }
            if (storedCode.equals(code)) {
                redisTemplate.delete(codeKey);
                redisTemplate.delete(attemptKey);
                log.info("忘记密码验证码校验成功");
                return PwdResetVerifyResult.SUCCESS;
            }
            // 记一次失败；满上限则作废验证码
            Long count = redisTemplate.opsForValue().increment(attemptKey);
            if (count != null && count == 1) {
                redisTemplate.expire(attemptKey, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            if (count != null && count >= PWD_RESET_MAX_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                log.warn("忘记密码验证码错误次数超限，已作废");
                return PwdResetVerifyResult.TOO_MANY_ATTEMPTS;
            }
            log.warn("忘记密码验证码校验失败");
            return PwdResetVerifyResult.CODE_ERROR;
        } catch (Exception e) {
            log.error("校验忘记密码验证码异常: {}", e.getMessage(), e);
            AuditFailureContext.authenticationFailure("AUTH_STATE_UNAVAILABLE", "验证码存储服务异常，未能完成校验", e);
            return PwdResetVerifyResult.EXPIRED;
        }
    }

    /**
     * 预校验忘记密码验证码（不消费，成功不删除，供第一步身份确认使用）。
     * 错误计次规则与 {@link #verifyPasswordResetCode} 一致，满上限同样作废验证码；
     * 最终重置时仍需调用消费式校验，防止绕过第一步直接重置。
     */
    public PwdResetVerifyResult preVerifyPasswordResetCode(String username, String email, String code) {
        if (username == null || email == null || code == null) {
            return PwdResetVerifyResult.EXPIRED;
        }
        if (redisTemplate == null) {
            log.error("Redis 不可用，无法预校验忘记密码验证码");
            AuditFailureContext.authenticationFailure("AUTH_STATE_UNAVAILABLE", "验证码存储服务不可用");
            return PwdResetVerifyResult.EXPIRED;
        }
        try {
            String user = normalizeUsername(username);
            String mail = normalizeEmail(email);
            String codeKey = pwdResetCodeKey(user, mail);
            String attemptKey = pwdResetAttemptKey(user, mail);

            String failCount = redisTemplate.opsForValue().get(attemptKey);
            if (failCount != null && Integer.parseInt(failCount) >= PWD_RESET_MAX_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                log.warn("忘记密码验证码错误次数超限，已作废");
                return PwdResetVerifyResult.TOO_MANY_ATTEMPTS;
            }

            String storedCode = redisTemplate.opsForValue().get(codeKey);
            if (storedCode == null) {
                log.warn("忘记密码验证码不存在或已过期");
                return PwdResetVerifyResult.EXPIRED;
            }
            if (storedCode.equals(code)) {
                log.info("忘记密码验证码预校验通过（未消费）");
                return PwdResetVerifyResult.SUCCESS;
            }
            // 记一次失败；满上限则作废验证码
            Long count = redisTemplate.opsForValue().increment(attemptKey);
            if (count != null && count == 1) {
                redisTemplate.expire(attemptKey, CODE_TTL_MINUTES, TimeUnit.MINUTES);
            }
            if (count != null && count >= PWD_RESET_MAX_ATTEMPTS) {
                redisTemplate.delete(codeKey);
                log.warn("忘记密码验证码错误次数超限，已作废");
                return PwdResetVerifyResult.TOO_MANY_ATTEMPTS;
            }
            log.warn("忘记密码验证码预校验失败");
            return PwdResetVerifyResult.CODE_ERROR;
        } catch (Exception e) {
            log.error("预校验忘记密码验证码异常: {}", e.getMessage(), e);
            AuditFailureContext.authenticationFailure("AUTH_STATE_UNAVAILABLE", "验证码存储服务异常，未能完成预校验", e);
            return PwdResetVerifyResult.EXPIRED;
        }
    }

    /**
     * 忘记密码三维限流：邮箱+账号、账号、IP 任一维度 60 秒内超 3 次即限流
     */
    public boolean checkPasswordResetRateLimit(String username, String email, String ip) {
        if (redisTemplate == null) {
            return true;
        }
        String user = normalizeUsername(username);
        String mail = normalizeEmail(email);
        String clientIp = normalizeIp(ip);
        try {
            Long c1 = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                    List.of(PWD_RESET_LIMIT_UE_PREFIX + user + ":" + mail), String.valueOf(RATE_LIMIT_SECONDS));
            Long c2 = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                    List.of(PWD_RESET_LIMIT_USER_PREFIX + user), String.valueOf(RATE_LIMIT_SECONDS));
            Long c3 = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                    List.of(PWD_RESET_LIMIT_IP_PREFIX + clientIp), String.valueOf(RATE_LIMIT_SECONDS));
            return (c1 == null || c1 <= MAX_REQUESTS_PER_MINUTE)
                    && (c2 == null || c2 <= MAX_REQUESTS_PER_MINUTE)
                    && (c3 == null || c3 <= MAX_REQUESTS_PER_MINUTE);
        } catch (Exception e) {
            log.error("忘记密码限流检查失败: {}", e.getMessage(), e);
            AuditFailureContext.authenticationFailure("AUTH_STATE_UNAVAILABLE", "验证码发送限流服务不可用", e);
            return false;
        }
    }

    /**
     * 重置接口验码频率限制（防暴力猜码）：账号+邮箱、IP 任一维度 60 秒内超 10 次即限流。
     * 与发码限流 Key 隔离，互不占用配额；配合 5 次输错作废，单验证码最多被猜 5 次。
     */
    public boolean checkPasswordResetVerifyRateLimit(String username, String email, String ip) {
        if (redisTemplate == null) {
            return true;
        }
        String user = normalizeUsername(username);
        String mail = normalizeEmail(email);
        String clientIp = normalizeIp(ip);
        try {
            Long c1 = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                    List.of(PWD_VERIFY_LIMIT_UE_PREFIX + user + ":" + mail), String.valueOf(RATE_LIMIT_SECONDS));
            Long c2 = redisTemplate.execute(RATE_LIMIT_SCRIPT,
                    List.of(PWD_VERIFY_LIMIT_IP_PREFIX + clientIp), String.valueOf(RATE_LIMIT_SECONDS));
            return (c1 == null || c1 <= PWD_VERIFY_MAX_PER_MINUTE)
                    && (c2 == null || c2 <= PWD_VERIFY_MAX_PER_MINUTE);
        } catch (Exception e) {
            log.error("忘记密码验码限流检查失败: {}", e.getMessage(), e);
            AuditFailureContext.authenticationFailure("AUTH_STATE_UNAVAILABLE", "验证码校验限流服务不可用", e);
            return false;
        }
    }

    private String pwdResetCodeKey(String normalizedUser, String normalizedEmail) {
        return PWD_RESET_CODE_PREFIX + normalizedUser + ":" + normalizedEmail;
    }

    private String pwdResetAttemptKey(String normalizedUser, String normalizedEmail) {
        return PWD_RESET_ATTEMPT_PREFIX + normalizedUser + ":" + normalizedEmail;
    }

    /**
     * username 规范化：trim（大小写保持原样，与登录一致）
     */
    public static String normalizeUsername(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * email 规范化：trim + lowercase
     */
    public static String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private static String normalizeIp(String ip) {
        return (ip == null || ip.trim().isEmpty()) ? "unknown" : ip.trim();
    }

    /**
     * 生成6位数字验证码
     */
    private String generateVerifyCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(900000) + 100000);
    }

    /**
     * 邮箱格式校验
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.isEmpty()) {
            return false;
        }
        return email.matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    }

    /**
     * 脱敏邮箱用于日志输出：仅保留前2位和域名，如 us***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return email;
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 2) {
            return email;
        }
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
