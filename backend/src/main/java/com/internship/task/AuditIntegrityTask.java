package com.internship.task;

import com.internship.entity.AuditSignatureRoot;
import com.internship.entity.LoginLog;
import com.internship.entity.OperateLog;
import com.internship.entity.SecurityEventLog;
import com.internship.mapper.AuditSignatureRootMapper;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditAlertService;
import com.internship.service.AuditHashService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计完整性任务（P2 完整性增强）：
 * - 03:00 为三张日志表签署前一日的链状态（每日签名根，留存清理 02:30 之后执行，签署的是清理后的最终状态）
 * - 04:00 增量校验（自上一签名根边界起的链连续性 + 哈希重算 + 全部签名根验证）
 * - 校验失败立即告警（ERROR 日志 + 安全事件）
 */
@Slf4j
@Component
public class AuditIntegrityTask {

    private static final String TABLE_OPERATE = "operate_log";
    private static final String TABLE_LOGIN = "login_log";
    private static final String TABLE_SECURITY = "security_event_log";
    private static final int VERIFY_BATCH = 500;

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private SecurityEventLogMapper securityEventLogMapper;

    @Autowired
    private AuditSignatureRootMapper signatureRootMapper;

    @Autowired
    private AuditHashService auditHashService;

    @Autowired
    private AuditAlertService auditAlertService;

    /** 每日签名：为前一日数据签署链状态根（幂等，重复执行覆盖更新） */
    @Scheduled(cron = "0 0 3 * * ?")
    public void signDailyRoots() {
        LocalDate rootDate = LocalDate.now().minusDays(1);
        try {
            int signed = 0;

            OperateLog lastOperate = operateLogMapper.selectLastChainEntry();
            if (lastOperate != null && lastOperate.getEntryHash() != null) {
                saveRoot(TABLE_OPERATE, rootDate, lastOperate.getId(), lastOperate.getEntryHash(),
                        operateLogMapper.countChained());
                signed++;
            }

            LoginLog lastLogin = loginLogMapper.selectLastChainEntry();
            if (lastLogin != null && lastLogin.getEntryHash() != null) {
                saveRoot(TABLE_LOGIN, rootDate, lastLogin.getId(), lastLogin.getEntryHash(),
                        loginLogMapper.countChained());
                signed++;
            }

            SecurityEventLog lastEvent = securityEventLogMapper.selectLastChainEntry();
            if (lastEvent != null && lastEvent.getEntryHash() != null) {
                saveRoot(TABLE_SECURITY, rootDate, lastEvent.getId(), lastEvent.getEntryHash(),
                        securityEventLogMapper.countChained());
                signed++;
            }

            log.info("每日审计签名完成：{} 个表签署了 {} 的链状态", signed, rootDate);
        } catch (Exception e) {
            log.error("每日审计签名失败: {}", e.getMessage(), e);
            auditAlertService.alert(AuditAlertService.TYPE_INTEGRITY_FAILURE,
                    "每日签名任务失败: " + e.getMessage());
        }
    }

    /** 每日增量校验（自上一签名根边界起）；校验失败立即告警 */
    @Scheduled(cron = "0 0 4 * * ?")
    public void verifyDaily() {
        Map<String, Object> report = verifyAll(false);
        List<String> allIssues = collectIssues(report);
        if (!allIssues.isEmpty()) {
            auditAlertService.alert(AuditAlertService.TYPE_INTEGRITY_FAILURE,
                    "每日完整性校验发现 " + allIssues.size() + " 个问题: " + String.join("; ", allIssues));
        } else {
            log.info("每日审计完整性校验通过");
        }
    }

    /**
     * 完整性校验（手动触发用全量，定时任务用增量）。
     * 返回报告：每表的校验条数/问题列表 + 签名根统计。
     */
    public Map<String, Object> verifyAll(boolean fullVerify) {
        Map<String, Object> report = new LinkedHashMap<>();
        List<String> allIssues = new ArrayList<>();

        report.put(TABLE_OPERATE, verifyOperateChain(fullVerify, allIssues));
        report.put(TABLE_LOGIN, verifyLoginChain(fullVerify, allIssues));
        report.put(TABLE_SECURITY, verifySecurityChain(fullVerify, allIssues));

        Map<String, Object> rootsSummary = new LinkedHashMap<>();
        rootsSummary.put("operate_log", signatureRootMapper.countByTable(TABLE_OPERATE));
        rootsSummary.put("login_log", signatureRootMapper.countByTable(TABLE_LOGIN));
        rootsSummary.put("security_event_log", signatureRootMapper.countByTable(TABLE_SECURITY));
        report.put("signatureRoots", rootsSummary);
        report.put("passed", allIssues.isEmpty());
        report.put("totalIssues", allIssues.size());
        return report;
    }

    // ================================================================
    // 各表校验（链连续性 + 哈希重算 + 签名根锚点核对）
    // ================================================================

    private Map<String, Object> verifyOperateChain(boolean fullVerify, List<String> allIssues) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        long startId = fullVerify ? 0 : boundaryId(TABLE_OPERATE);
        long verified = verifyChainLoop(issues,
                (afterId) -> operateLogMapper.selectChainedBatch(afterId, VERIFY_BATCH),
                row -> auditHashService.computeEntryHash(row.getPrevHash(), auditHashService.canonicalPayload(row), row.getKeyVersion()),
                row -> row.getId(), row -> row.getPrevHash(), row -> row.getEntryHash(), startId);
        verifyRoots(TABLE_OPERATE, issues, id -> operateLogMapper.selectById(id));
        result.put("verifiedEntries", verified);
        result.put("issues", issues);
        allIssues.addAll(issues);
        return result;
    }

    private Map<String, Object> verifyLoginChain(boolean fullVerify, List<String> allIssues) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        long startId = fullVerify ? 0 : boundaryId(TABLE_LOGIN);
        long verified = verifyChainLoop(issues,
                (afterId) -> loginLogMapper.selectChainedBatch(afterId, VERIFY_BATCH),
                row -> auditHashService.computeEntryHash(row.getPrevHash(), auditHashService.canonicalPayload(row), row.getKeyVersion()),
                row -> row.getId(), row -> row.getPrevHash(), row -> row.getEntryHash(), startId);
        verifyRoots(TABLE_LOGIN, issues, id -> loginLogMapper.selectById(id));
        result.put("verifiedEntries", verified);
        result.put("issues", issues);
        allIssues.addAll(issues);
        return result;
    }

    private Map<String, Object> verifySecurityChain(boolean fullVerify, List<String> allIssues) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<String> issues = new ArrayList<>();
        long startId = fullVerify ? 0 : boundaryId(TABLE_SECURITY);
        long verified = verifyChainLoop(issues,
                (afterId) -> securityEventLogMapper.selectChainedBatch(afterId, VERIFY_BATCH),
                row -> auditHashService.computeEntryHash(row.getPrevHash(), auditHashService.canonicalPayload(row), row.getKeyVersion()),
                row -> row.getId(), row -> row.getPrevHash(), row -> row.getEntryHash(), startId);
        verifyRoots(TABLE_SECURITY, issues, id -> securityEventLogMapper.selectById(id));
        result.put("verifiedEntries", verified);
        result.put("issues", issues);
        allIssues.addAll(issues);
        return result;
    }

    /** 泛型链校验循环：链连续性 + 逐条哈希重算 */
    private <T> long verifyChainLoop(List<String> issues,
                                     java.util.function.LongFunction<List<T>> batchFetcher,
                                     java.util.function.Function<T, String> hashComputer,
                                     java.util.function.Function<T, Long> idGetter,
                                     java.util.function.Function<T, String> prevHashGetter,
                                     java.util.function.Function<T, String> entryHashGetter,
                                     long startId) {
        long afterId = startId;
        String prevHash = null;
        long verified = 0;
        List<T> batch;
        while (!(batch = batchFetcher.apply(afterId)).isEmpty()) {
            for (T row : batch) {
                afterId = idGetter.apply(row);
                // 链连续性：prev_hash 必须等于前一条的 entry_hash
                if (prevHash != null && !prevHash.equals(prevHashGetter.apply(row))) {
                    issues.add("链断裂 id=" + afterId + "（prev_hash 与前一节点不符，可能被删除或篡改）");
                }
                // 哈希重算：内容与哈希一致
                try {
                    String recomputed = hashComputer.apply(row);
                    if (!recomputed.equals(entryHashGetter.apply(row))) {
                        issues.add("哈希不匹配 id=" + afterId + "（记录内容与哈希不一致，可能被篡改）");
                    }
                } catch (Exception e) {
                    issues.add("密钥缺失 id=" + afterId + "（版本密钥不可用）");
                }
                prevHash = entryHashGetter.apply(row);
                verified++;
            }
        }
        return verified;
    }

    /** 签名根验证：签名自验证 + 锚点行核对（行已被留存删除时仅验证签名本身） */
    private void verifyRoots(String tableName, List<String> issues,
                             java.util.function.LongFunction<?> rowFetcher) {
        List<AuditSignatureRoot> roots = signatureRootMapper.selectByTable(tableName);
        for (AuditSignatureRoot root : roots) {
            boolean signatureOk = auditHashService.verifyRootSignature(tableName,
                    toLocalDate(root.getRootDate()), root.getLastEntryId(), root.getLastEntryHash(),
                    root.getEntryCount(), root.getKeyVersion(), root.getSignature());
            if (!signatureOk) {
                issues.add("签名根被篡改 table=" + tableName + " date=" + root.getRootDate());
                continue;
            }
            // 锚点行仍存在时核对哈希；已被留存清理的行跳过（属预期删除）
            Object anchor = rowFetcher.apply(root.getLastEntryId());
            if (anchor != null) {
                String anchorHash = extractEntryHash(anchor);
                if (anchorHash == null || !anchorHash.equals(root.getLastEntryHash())) {
                    issues.add("根锚点不一致 table=" + tableName + " date=" + root.getRootDate()
                            + " id=" + root.getLastEntryId());
                }
            }
        }
    }

    /** 增量校验起点：上一签名根的边界ID（无根则从头校验） */
    private long boundaryId(String tableName) {
        AuditSignatureRoot latest = signatureRootMapper.selectLatest(tableName);
        return latest != null ? latest.getLastEntryId() : 0L;
    }

    private void saveRoot(String tableName, LocalDate rootDate, Long lastEntryId, String lastEntryHash, long entryCount) {
        AuditSignatureRoot root = new AuditSignatureRoot();
        root.setRootDate(java.sql.Date.valueOf(rootDate));
        root.setTableName(tableName);
        root.setLastEntryId(lastEntryId);
        root.setLastEntryHash(lastEntryHash);
        root.setEntryCount(entryCount);
        root.setKeyVersion(auditHashService.currentKeyVersion());
        root.setSignature(auditHashService.signRoot(tableName, rootDate, lastEntryId, lastEntryHash, entryCount));
        signatureRootMapper.upsert(root);
    }

    private LocalDate toLocalDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return new java.sql.Date(date.getTime()).toLocalDate();
    }

    private String extractEntryHash(Object entity) {
        if (entity instanceof OperateLog o) {
            return o.getEntryHash();
        }
        if (entity instanceof LoginLog l) {
            return l.getEntryHash();
        }
        if (entity instanceof SecurityEventLog s) {
            return s.getEntryHash();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private List<String> collectIssues(Map<String, Object> report) {
        List<String> all = new ArrayList<>();
        for (String table : List.of(TABLE_OPERATE, TABLE_LOGIN, TABLE_SECURITY)) {
            Object tableReport = report.get(table);
            if (tableReport instanceof Map<?, ?> m && m.get("issues") instanceof List<?> list) {
                for (Object issue : list) {
                    all.add("[" + table + "] " + issue);
                }
            }
        }
        return all;
    }
}
