package com.internship.service;

import com.internship.entity.SecurityEventLog;
import com.internship.mapper.SecurityEventLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 审计告警服务（P2 完整性增强）。
 * 计划要求：审计写入失败、哈希校验失败和异常清理告警均必须可观测。
 * 实现：ERROR 级别日志（可被日志采集/监控告警捕获）+ 内存计数指标 + best-effort 安全事件入库。
 */
@Slf4j
@Service
public class AuditAlertService {

    public static final String TYPE_WRITE_FAILURE = "AUDIT_WRITE_FAILURE";
    public static final String TYPE_INTEGRITY_FAILURE = "AUDIT_INTEGRITY_FAILURE";
    public static final String TYPE_RETENTION_FAILURE = "AUDIT_RETENTION_FAILURE";

    @Autowired
    private SecurityEventLogMapper securityEventLogMapper;

    /** 可观测指标：各类告警累计次数（JVM 生命周期内） */
    private final AtomicLong writeFailureCount = new AtomicLong();
    private final AtomicLong integrityFailureCount = new AtomicLong();
    private final AtomicLong retentionFailureCount = new AtomicLong();

    /**
     * 发出审计告警：ERROR 日志 + 计数 + best-effort 安全事件（直接走 Mapper，避免与审计写入互相递归）
     */
    public void alert(String alertType, String detail) {
        log.error("[AUDIT_ALERT] type={}, detail={}", alertType, detail);

        switch (alertType) {
            case TYPE_WRITE_FAILURE -> writeFailureCount.incrementAndGet();
            case TYPE_INTEGRITY_FAILURE -> integrityFailureCount.incrementAndGet();
            case TYPE_RETENTION_FAILURE -> retentionFailureCount.incrementAndGet();
            default -> { /* 自定义类型不计数 */ }
        }

        try {
            SecurityEventLog event = new SecurityEventLog();
            event.setEventTime(new Date());
            event.setEventType(alertType);
            event.setUsername("SYSTEM");
            event.setUserRole("SYSTEM");
            event.setRiskLevel("HIGH");
            event.setResult("FAILURE");
            event.setDescription("审计告警：" + truncate(detail, 480));
            event.setDeviceType("SYSTEM");
            securityEventLogMapper.insert(event);
        } catch (Exception e) {
            // 安全事件也写不进去（数据库不可用）：仅保留 ERROR 日志告警
            log.error("[AUDIT_ALERT] 告警安全事件入库失败: {}", e.getMessage());
        }
    }

    public long getWriteFailureCount() {
        return writeFailureCount.get();
    }

    public long getIntegrityFailureCount() {
        return integrityFailureCount.get();
    }

    public long getRetentionFailureCount() {
        return retentionFailureCount.get();
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
