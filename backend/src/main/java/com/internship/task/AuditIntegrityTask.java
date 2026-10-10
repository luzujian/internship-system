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
        } else if (Boolean.TRUE.equals(report.get("passed"))) {
            log.info("每日审计完整性校验通过");
        } else {
            log.warn("每日审计完整性校验无法给出完整结论：status={}, warnings={}",
                    report.get("status"), report.get("continuityWarnings"));
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
        List<String> warnings = new ArrayList<>();
        boolean hasData = false;
        for (String table : List.of(TABLE_OPERATE, TABLE_LOGIN, TABLE_SECURITY)) {
            Map<?, ?> tableReport = (Map<?, ?>) report.get(table);
            rootsSummary.put(table, tableReport.get("signatureRoots"));
            hasData |= !"NO_DATA".equals(tableReport.get("status"));
            for (Object warning : (List<?>) tableReport.get("continuityWarnings")) {
                warnings.add("[" + table + "] " + warning);
            }
        }
        String status = !allIssues.isEmpty() ? "FAILED"
                : !warnings.isEmpty() ? "INCOMPLETE" : hasData ? "PASSED" : "NO_DATA";
        report.put("signatureRoots", rootsSummary);
        report.put("status", status);
        report.put("passed", "PASSED".equals(status));
        report.put("totalIssues", allIssues.size());
        report.put("continuityWarnings", warnings);
        report.put("totalWarnings", warnings.size());
        if (fullVerify && !allIssues.isEmpty()) {
            auditAlertService.alert(AuditAlertService.TYPE_INTEGRITY_FAILURE,
                    "手动完整性校验发现 " + allIssues.size() + " 个问题: " + String.join("; ", allIssues));
        }
        return report;
    }

    // ================================================================
    // 各表校验（链连续性 + 哈希重算 + 签名根锚点核对）
    // ================================================================

    private Map<String, Object> verifyOperateChain(boolean fullVerify, List<String> allIssues) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AuditSignatureRoot boundary = fullVerify ? null : signatureRootMapper.selectLatest(TABLE_OPERATE);
        long verified = verifyChainLoop(issues, warnings,
                (afterId) -> operateLogMapper.selectChainedBatch(afterId, VERIFY_BATCH),
                row -> auditHashService.computeEntryHash(row.getPrevHash(), auditHashService.canonicalPayload(row), row.getKeyVersion()),
                row -> row.getId(), row -> row.getPrevHash(), row -> row.getEntryHash(), boundary);
        long roots = verifyRoots(TABLE_OPERATE, issues, warnings, id -> operateLogMapper.selectById(id));
        allIssues.addAll(issues);
        return tableResult(verified, operateLogMapper.countUnchained(), roots, issues, warnings);
    }

    private Map<String, Object> verifyLoginChain(boolean fullVerify, List<String> allIssues) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AuditSignatureRoot boundary = fullVerify ? null : signatureRootMapper.selectLatest(TABLE_LOGIN);
        long verified = verifyChainLoop(issues, warnings,
                (afterId) -> loginLogMapper.selectChainedBatch(afterId, VERIFY_BATCH),
                row -> auditHashService.computeEntryHash(row.getPrevHash(), auditHashService.canonicalPayload(row), row.getKeyVersion()),
                row -> row.getId(), row -> row.getPrevHash(), row -> row.getEntryHash(), boundary);
        long roots = verifyRoots(TABLE_LOGIN, issues, warnings, id -> loginLogMapper.selectById(id));
        allIssues.addAll(issues);
        return tableResult(verified, loginLogMapper.countUnchained(), roots, issues, warnings);
    }

    private Map<String, Object> verifySecurityChain(boolean fullVerify, List<String> allIssues) {
        List<String> issues = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        AuditSignatureRoot boundary = fullVerify ? null : signatureRootMapper.selectLatest(TABLE_SECURITY);
        long verified = verifyChainLoop(issues, warnings,
                (afterId) -> securityEventLogMapper.selectChainedBatch(afterId, VERIFY_BATCH),
                row -> auditHashService.computeEntryHash(row.getPrevHash(), auditHashService.canonicalPayload(row), row.getKeyVersion()),
                row -> row.getId(), row -> row.getPrevHash(), row -> row.getEntryHash(), boundary);
        long roots = verifyRoots(TABLE_SECURITY, issues, warnings, id -> securityEventLogMapper.selectById(id));
        allIssues.addAll(issues);
        return tableResult(verified, securityEventLogMapper.countUnchained(), roots, issues, warnings);
    }

    private Map<String, Object> tableResult(long verified, long unsigned, long roots,
                                          List<String> issues, List<String> warnings) {
        if (unsigned > 0) {
            warnings.add("存在 " + unsigned + " 条未纳入哈希链的记录，无法验证其内容完整性");
        }
        if (verified > 0 && roots == 0) {
            warnings.add("尚无签名根锚点，当前仅验证记录哈希和现存链链接");
        }
        String status = !issues.isEmpty() ? "FAILED" : !warnings.isEmpty() ? "INCOMPLETE"
                : verified == 0 && roots == 0 ? "NO_DATA" : "PASSED";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verifiedEntries", verified);
        result.put("unsignedEntries", unsigned);
        result.put("signatureRoots", roots);
        result.put("issues", issues);
        result.put("continuityWarnings", warnings);
        result.put("status", status);
        return result;
    }

    /** 泛型链校验循环：链连续性 + 逐条哈希重算 */
    private <T> long verifyChainLoop(List<String> issues, List<String> warnings,
                                     java.util.function.LongFunction<List<T>> batchFetcher,
                                     java.util.function.Function<T, String> hashComputer,
                                     java.util.function.Function<T, Long> idGetter,
                                     java.util.function.Function<T, String> prevHashGetter,
                                     java.util.function.Function<T, String> entryHashGetter,
                                     AuditSignatureRoot boundary) {
        long afterId = boundary == null ? 0 : boundary.getLastEntryId();
        String prevHash = boundary == null ? AuditHashService.GENESIS_PREV_HASH : boundary.getLastEntryHash();
        long verified = 0;
        List<T> batch;
        while (!(batch = batchFetcher.apply(afterId)).isEmpty()) {
            for (T row : batch) {
                afterId = idGetter.apply(row);
                // 链连续性：prev_hash 必须等于前一条的 entry_hash
                if (prevHash != null && !prevHash.equals(prevHashGetter.apply(row))) {
                    warnings.add("链链接无法连续验证 id=" + afterId
                            + "（前驱节点缺失或链接不符，需结合留存清理记录与归档核对）");
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

    /** 签名根验证：缺少在线锚点只验证签名，明确报告覆盖不完整。 */
    private long verifyRoots(String tableName, List<String> issues, List<String> warnings,
                             java.util.function.LongFunction<?> rowFetcher) {
        List<AuditSignatureRoot> roots = signatureRootMapper.selectByTable(tableName);
        for (AuditSignatureRoot root : roots) {
            boolean signatureOk;
            try {
                signatureOk = auditHashService.verifyRootSignature(tableName,
                        toLocalDate(root.getRootDate()), root.getLastEntryId(), root.getLastEntryHash(),
                        root.getEntryCount(), root.getKeyVersion(), root.getSignature());
            } catch (Exception e) {
                issues.add("签名根无法校验 table=" + tableName + " date=" + root.getRootDate() + "（密钥或签名数据不可用）");
                continue;
            }
            if (!signatureOk) {
                issues.add("签名根被篡改 table=" + tableName + " date=" + root.getRootDate());
                continue;
            }
            // 缺失行可能来自正常留存或其他删除，不把无法证明的原因判定为篡改。
            Object anchor = rowFetcher.apply(root.getLastEntryId());
            if (anchor != null) {
                String anchorHash = extractEntryHash(anchor);
                if (anchorHash == null || !anchorHash.equals(root.getLastEntryHash())) {
                    issues.add("根锚点不一致 table=" + tableName + " date=" + root.getRootDate()
                            + " id=" + root.getLastEntryId());
                }
            } else {
                warnings.add("签名根在线锚点缺失 table=" + tableName + " id=" + root.getLastEntryId()
                        + "（根签名有效，仍需结合归档核对原记录）");
            }
        }
        return roots.size();
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
