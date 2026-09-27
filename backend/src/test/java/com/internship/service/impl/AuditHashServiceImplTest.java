package com.internship.service.impl;

import com.internship.entity.OperateLog;
import com.internship.service.AuditHashService;
import com.internship.service.SystemConfigService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2 哈希链服务单元测试：密钥自动生成、签名/验证往返、规范化载荷确定性
 */
@ExtendWith(MockitoExtension.class)
class AuditHashServiceImplTest {

    @Mock
    private SystemConfigService systemConfigService;

    @InjectMocks
    private AuditHashServiceImpl auditHashService;

    @Test
    void keyAutoGeneratesOnFirstUseAndVersionIsOne() {
        assertEquals(1, auditHashService.currentKeyVersion(), "无配置时首次使用自动生成 v1 密钥");
    }

    @Test
    void entryHashIsDeterministicForSameInput() {
        String hash1 = auditHashService.computeEntryHash(AuditHashService.GENESIS_PREV_HASH, "payload-a");
        String hash2 = auditHashService.computeEntryHash(AuditHashService.GENESIS_PREV_HASH, "payload-a");
        String hash3 = auditHashService.computeEntryHash(AuditHashService.GENESIS_PREV_HASH, "payload-b");

        assertEquals(hash1, hash2, "相同输入必须产生相同哈希");
        assertNotEquals(hash1, hash3, "不同载荷必须产生不同哈希");
        assertEquals(64, hash1.length(), "HMAC-SHA256 hex 长度为64");
    }

    @Test
    void entryHashDependsOnKeyVersion() {
        // 先引导密钥生成（缓存当前版本），再按版本计算
        int version = auditHashService.currentKeyVersion();
        String hash = auditHashService.computeEntryHash(AuditHashService.GENESIS_PREV_HASH, "payload", version);
        assertNotNull(hash);
        assertEquals(64, hash.length());
        // 版本密钥不存在时必须显式失败（用于校验旧版本数据时发现密钥缺失）
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> auditHashService.computeEntryHash(AuditHashService.GENESIS_PREV_HASH, "payload", 99));
    }

    @Test
    void rootSignatureRoundtripVerifies() {
        LocalDate rootDate = LocalDate.of(2026, 8, 28);
        String signature = auditHashService.signRoot("operate_log", rootDate, 100L, "abc", 42L);

        assertTrue(auditHashService.verifyRootSignature("operate_log", rootDate, 100L, "abc", 42L, 1, signature),
                "正确参数必须验证通过");
        org.junit.jupiter.api.Assertions.assertFalse(
                auditHashService.verifyRootSignature("operate_log", rootDate, 100L, "tampered-hash", 42L, 1, signature),
                "参数被篡改必须验证失败");
        org.junit.jupiter.api.Assertions.assertFalse(
                auditHashService.verifyRootSignature("operate_log", rootDate, 101L, "abc", 42L, 1, signature),
                "边界ID被篡改必须验证失败");
    }

    @Test
    void canonicalPayloadIsDeterministicAndSensitiveToContent() {
        OperateLog log1 = new OperateLog();
        log1.setModule("LOG_MANAGEMENT");
        log1.setOperationType("CREATE");
        log1.setDescription("测试");
        log1.setRiskLevel("LOW");

        OperateLog log2 = new OperateLog();
        log2.setModule("LOG_MANAGEMENT");
        log2.setOperationType("CREATE");
        log2.setDescription("测试");
        log2.setRiskLevel("LOW");

        OperateLog log3 = new OperateLog();
        log3.setModule("LOG_MANAGEMENT");
        log3.setOperationType("DELETE");
        log3.setDescription("测试");
        log3.setRiskLevel("LOW");

        assertEquals(auditHashService.canonicalPayload(log1), auditHashService.canonicalPayload(log2),
                "相同内容必须产生相同规范化载荷");
        assertNotEquals(auditHashService.canonicalPayload(log1), auditHashService.canonicalPayload(log3),
                "内容变化必须改变载荷");
    }
}
