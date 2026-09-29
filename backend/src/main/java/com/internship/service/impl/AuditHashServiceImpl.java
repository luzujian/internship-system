package com.internship.service.impl;

import com.internship.entity.LoginLog;
import com.internship.entity.OperateLog;
import com.internship.entity.SecurityEventLog;
import com.internship.entity.SystemConfig;
import com.internship.service.AuditHashService;
import com.internship.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;

/**
 * 审计日志哈希链服务实现。
 * 密钥来源优先级：环境变量 AUDIT_HASH_KEY（服务器 .env，DB账号不可读，防篡改能力最强）
 * → system_config(audit.hash.key.v{N}，首次自动生成)。
 * 密钥版本记录于 system_config(audit.hash.key.currentVersion)，轮换时插入新版本并递增。
 */
@Slf4j
@Service
public class AuditHashServiceImpl implements AuditHashService {

    private static final String CONFIG_KEY_PREFIX = "audit.hash.key.v";
    private static final String CONFIG_CURRENT_VERSION = "audit.hash.key.currentVersion";
    private static final String FIELD_SEPARATOR = "\u0001";
    /** 时间统一到秒级：DB datetime 列为秒精度，写入与读回重算必须一致 */
    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

    @Value("${audit.hash.key:}")
    private String envHashKey;

    @Autowired
    private SystemConfigService systemConfigService;

    /** 缓存的当前密钥（版本, 密钥字节） */
    private volatile KeyMaterial cachedKey;

    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public synchronized int currentKeyVersion() {
        return currentKeyMaterial().version;
    }

    @Override
    public String computeEntryHash(String prevHash, String canonicalPayload) {
        KeyMaterial key = currentKeyMaterial();
        return computeEntryHash(prevHash, canonicalPayload, key.version());
    }

    @Override
    public String computeEntryHash(String prevHash, String canonicalPayload, int keyVersion) {
        byte[] keyBytes = keyBytesForVersion(keyVersion);
        String message = prevHash + FIELD_SEPARATOR + canonicalPayload + FIELD_SEPARATOR + keyVersion;
        return hmacSha256Hex(keyBytes, message);
    }

    @Override
    public String canonicalPayload(OperateLog log) {
        StringBuilder sb = new StringBuilder(256);
        appendField(sb, formatTime(log.getOperateTime()));
        appendField(sb, log.getRequestId());
        appendField(sb, log.getOperateAdminId());
        appendField(sb, log.getOperatorName());
        appendField(sb, log.getOperatorUsername());
        appendField(sb, log.getOperatorRole());
        appendField(sb, log.getIpAddress());
        appendField(sb, log.getOperationType());
        appendField(sb, log.getModule());
        appendField(sb, log.getDescription());
        appendField(sb, log.getOperationResult());
        appendField(sb, log.getRiskLevel());
        appendField(sb, log.getErrorCode());
        appendField(sb, log.getTargetType());
        appendField(sb, log.getTargetId());
        appendField(sb, log.getAffectedCount());
        appendField(sb, log.getRealOperatorName());
        appendField(sb, log.getHttpMethod());
        appendField(sb, log.getRequestPath());
        appendField(sb, log.getDeviceType());
        appendField(sb, log.getDurationMs());
        appendField(sb, log.getDetailJson());
        return sb.toString();
    }

    @Override
    public String canonicalPayload(LoginLog log) {
        StringBuilder sb = new StringBuilder(192);
        appendField(sb, formatTime(log.getLoginTime()));
        appendField(sb, log.getUserId());
        appendField(sb, log.getUserType());
        appendField(sb, log.getUserName());
        appendField(sb, log.getIpAddress());
        appendField(sb, log.getDeviceInfo());
        appendField(sb, log.getLoginStatus());
        appendField(sb, log.getRequestId());
        appendField(sb, log.getRiskLevel());
        appendField(sb, log.getErrorCode());
        appendField(sb, log.getDeviceType());
        appendField(sb, log.getDurationMs());
        appendField(sb, log.getDetailJson());
        return sb.toString();
    }

    @Override
    public String canonicalPayload(SecurityEventLog event) {
        StringBuilder sb = new StringBuilder(256);
        appendField(sb, formatTime(event.getEventTime()));
        appendField(sb, event.getRequestId());
        appendField(sb, event.getEventType());
        appendField(sb, event.getUserId());
        appendField(sb, event.getUsername());
        appendField(sb, event.getUserRole());
        appendField(sb, event.getRealOperatorName());
        appendField(sb, event.getRiskLevel());
        appendField(sb, event.getResult());
        appendField(sb, event.getErrorCode());
        appendField(sb, event.getTargetType());
        appendField(sb, event.getTargetId());
        appendField(sb, event.getAffectedCount());
        appendField(sb, event.getHttpMethod());
        appendField(sb, event.getRequestPath());
        appendField(sb, event.getIpAddress());
        appendField(sb, event.getDeviceType());
        appendField(sb, event.getDurationMs());
        appendField(sb, event.getDescription());
        appendField(sb, event.getDetailJson());
        return sb.toString();
    }

    @Override
    public String signRoot(String tableName, LocalDate rootDate, long lastEntryId, String lastEntryHash, long entryCount) {
        KeyMaterial key = currentKeyMaterial();
        String message = "AUDIT_ROOT" + FIELD_SEPARATOR + tableName + FIELD_SEPARATOR + rootDate
                + FIELD_SEPARATOR + lastEntryId + FIELD_SEPARATOR + lastEntryHash
                + FIELD_SEPARATOR + entryCount + FIELD_SEPARATOR + key.version();
        return hmacSha256Hex(key.keyBytes(), message);
    }

    @Override
    public boolean verifyRootSignature(String tableName, LocalDate rootDate, long lastEntryId,
                                        String lastEntryHash, long entryCount, int keyVersion, String signature) {
        if (signature == null) {
            return false;
        }
        byte[] keyBytes = keyBytesForVersion(keyVersion);
        String message = "AUDIT_ROOT" + FIELD_SEPARATOR + tableName + FIELD_SEPARATOR + rootDate
                + FIELD_SEPARATOR + lastEntryId + FIELD_SEPARATOR + lastEntryHash
                + FIELD_SEPARATOR + entryCount + FIELD_SEPARATOR + keyVersion;
        return signature.equalsIgnoreCase(hmacSha256Hex(keyBytes, message));
    }

    // ================================================================
    // 私有方法
    // ================================================================

    private void appendField(StringBuilder sb, Object value) {
        if (value != null) {
            sb.append(value);
        }
        sb.append(FIELD_SEPARATOR);
    }

    private String formatTime(Date time) {
        if (time == null) {
            return "";
        }
        return new SimpleDateFormat(TIME_FORMAT).format(time);
    }

    /** 获取当前密钥（懒加载：env 优先，否则生成/读取 system_config） */
    private KeyMaterial currentKeyMaterial() {
        if (cachedKey != null) {
            return cachedKey;
        }
        if (envHashKey != null && !envHashKey.trim().isEmpty()) {
            // env 模式：版本号取配置（默认1），密钥不落库
            int version = readConfigInt(CONFIG_CURRENT_VERSION, 1);
            cachedKey = new KeyMaterial(version, envHashKey.trim().getBytes(StandardCharsets.UTF_8));
            log.info("审计哈希密钥已从环境变量加载，版本: {}", version);
            return cachedKey;
        }
        // system_config 模式
        int version = readConfigInt(CONFIG_CURRENT_VERSION, -1);
        if (version <= 0) {
            // 首次使用：生成密钥 v1 并落库
            byte[] generated = new byte[32];
            RANDOM.nextBytes(generated);
            String encoded = Base64.getEncoder().encodeToString(generated);
            saveConfig(CONFIG_KEY_PREFIX + "1", encoded, "审计哈希密钥 v1");
            saveConfig(CONFIG_CURRENT_VERSION, "1", "审计哈希密钥当前版本");
            log.info("审计哈希密钥已自动生成并存储（版本1）");
            cachedKey = new KeyMaterial(1, generated);
            return cachedKey;
        }
        String encoded = systemConfigService.getConfigValue(CONFIG_KEY_PREFIX + version);
        if (encoded == null || encoded.isEmpty()) {
            log.error("审计哈希密钥缺失: 版本{}，完整性保护降级（哈希字段将为空）", version);
            return null;
        }
        cachedKey = new KeyMaterial(version, Base64.getDecoder().decode(encoded));
        return cachedKey;
    }

    /** 取指定版本密钥字节；取不到时抛出 IllegalStateException 由调用方降级 */
    private byte[] keyBytesForVersion(int keyVersion) {
        // 缓存命中：当前版本密钥（含 env 模式与自动生成模式）直接返回
        if (cachedKey != null && cachedKey.version() == keyVersion) {
            return cachedKey.keyBytes();
        }
        if (envHashKey != null && !envHashKey.trim().isEmpty()) {
            throw new IllegalStateException("env密钥模式下仅支持当前版本: 当前=" + currentKeyVersion() + " 请求=" + keyVersion);
        }
        String encoded = systemConfigService.getConfigValue(CONFIG_KEY_PREFIX + keyVersion);
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalStateException("审计哈希密钥不存在: 版本" + keyVersion);
        }
        return Base64.getDecoder().decode(encoded);
    }

    private int readConfigInt(String key, int defaultValue) {
        try {
            Integer value = systemConfigService.getConfigValueAsInt(key);
            return value != null ? value : defaultValue;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private void saveConfig(String key, String value, String name) {
        try {
            SystemConfig existing = systemConfigService.findByConfigKey(key);
            if (existing != null) {
                existing.setConfigValue(value);
                systemConfigService.update(existing);
            } else {
                SystemConfig config = new SystemConfig();
                config.setConfigKey(key);
                config.setConfigValue(value);
                config.setConfigType("AUDIT_HASH");
                config.setConfigName(name);
                config.setDescription("审计哈希链密钥（P2完整性），请勿泄露");
                config.setStatus(1);
                systemConfigService.insert(config);
            }
        } catch (Exception e) {
            log.error("保存审计哈希配置失败: key={}, {}", key, e.getMessage());
        }
    }

    private String hmacSha256Hex(byte[] keyBytes, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA256"));
            byte[] digest = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC计算失败", e);
        }
    }

    private record KeyMaterial(int version, byte[] keyBytes) {
    }
}
