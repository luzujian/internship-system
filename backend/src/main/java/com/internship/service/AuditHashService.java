package com.internship.service;

import java.time.LocalDate;

/**
 * 审计日志哈希链服务（P2 完整性增强）。
 * 三张日志表各自维护独立哈希链：entry_hash = HMAC-SHA256(key, prev_hash || canonical_payload || key_version)。
 * 密钥版本化管理：环境变量优先（服务器 .env），否则首次自动生成并存入 system_config。
 */
public interface AuditHashService {

    /** 创世前驱哈希（链首节点使用） */
    String GENESIS_PREV_HASH = "0000000000000000000000000000000000000000000000000000000000000000";

    /** 当前密钥版本 */
    int currentKeyVersion();

    /**
     * 计算链节点哈希（使用当前密钥版本）
     */
    String computeEntryHash(String prevHash, String canonicalPayload);

    /**
     * 按指定密钥版本重算链节点哈希（校验用；env 密钥模式仅支持当前版本）
     */
    String computeEntryHash(String prevHash, String canonicalPayload, int keyVersion);

    /** 规范化载荷（字段顺序固定，null→空串，时间统一到秒级，分隔符 \u0001） */
    String canonicalPayload(com.internship.entity.OperateLog log);

    String canonicalPayload(com.internship.entity.LoginLog log);

    String canonicalPayload(com.internship.entity.SecurityEventLog log);

    /** 计算每日签名根（覆盖指定表截至签署时的链状态） */
    String signRoot(String tableName, LocalDate rootDate, long lastEntryId, String lastEntryHash, long entryCount);

    /** 校验每日签名根（按签署时的密钥版本） */
    boolean verifyRootSignature(String tableName, LocalDate rootDate, long lastEntryId,
                                String lastEntryHash, long entryCount, int keyVersion, String signature);
}
