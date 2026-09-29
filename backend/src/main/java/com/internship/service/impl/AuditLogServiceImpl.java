package com.internship.service.impl;

import com.internship.entity.LoginLog;
import com.internship.entity.OperateLog;
import com.internship.entity.SecurityEventLog;
import com.internship.exception.BusinessException;
import com.internship.filter.RequestIdFilter;
import com.internship.mapper.LoginLogMapper;
import com.internship.mapper.OperateLogMapper;
import com.internship.mapper.SecurityEventLogMapper;
import com.internship.service.AuditAlertService;
import com.internship.service.AuditHashService;
import com.internship.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
public class AuditLogServiceImpl implements AuditLogService {

    /** 历史动作 -> 规范动作映射 */
    private static final Map<String, String> ACTION_NORMALIZE = Map.ofEntries(
            Map.entry("ADD", "CREATE"),
            Map.entry("INSERT", "CREATE"),
            Map.entry("CREATE", "CREATE"),
            Map.entry("MODIFY", "UPDATE"),
            Map.entry("EDIT", "UPDATE"),
            Map.entry("REMOVE", "DELETE"),
            Map.entry("QUERY", "READ"),
            Map.entry("SEARCH", "READ"),
            Map.entry("SELECT", "READ"),
            Map.entry("VIEW", "READ")
    );

    /** 规范动作 -> 包含历史别名的筛选列表 */
    private static final Map<String, Set<String>> ACTION_ALIASES = Map.of(
            "CREATE", Set.of("CREATE", "ADD", "INSERT"),
            "UPDATE", Set.of("UPDATE", "MODIFY", "EDIT"),
            "DELETE", Set.of("DELETE", "REMOVE"),
            "READ", Set.of("READ", "QUERY", "SEARCH", "SELECT", "VIEW")
    );

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private SecurityEventLogMapper securityEventLogMapper;

    @Autowired
    private AuditHashService auditHashService;

    @Autowired
    private AuditAlertService auditAlertService;

    /** P2 哈希链：每表独立链锁，单实例内串行化"取链尾→计算→插入"，防止并发分叉 */
    private final ReentrantLock operateLogChainLock = new ReentrantLock();
    private final ReentrantLock loginLogChainLock = new ReentrantLock();
    private final ReentrantLock securityEventChainLock = new ReentrantLock();

    /**
     * 独立事务写入：审计提交/回滚与业务事务隔离，
     * 业务事务回滚时审计（含 FAILURE 记录）仍然落库。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(OperateLog operateLog) {
        persistOperateLog(operateLog, false);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCriticalAttempt(OperateLog operateLog) {
        if (operateLog == null) {
            throw new BusinessException("关键操作审计参数不能为空");
        }
        // 关键操作：先落 ATTEMPTED 审计；审计不可用则禁止执行
        operateLog.setOperationResult("ATTEMPTED");
        persistOperateLog(operateLog, true);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSecurityEvent(SecurityEventLog eventLog) {
        if (eventLog == null) {
            return;
        }
        // 事件时间一律由服务端生成
        eventLog.setEventTime(new Date());
        if (eventLog.getRequestId() == null) {
            eventLog.setRequestId(RequestIdFilter.currentRequestId());
        }
        if (eventLog.getRiskLevel() == null || eventLog.getRiskLevel().isEmpty()) {
            eventLog.setRiskLevel(RISK_LOW);
        }

        securityEventChainLock.lock();
        try {
            applySecurityEventChain(eventLog);
            try {
                securityEventLogMapper.insert(eventLog);
                log.debug("安全事件已记录：eventType={}, riskLevel={}, result={}",
                        eventLog.getEventType(), eventLog.getRiskLevel(), eventLog.getResult());
            } catch (Exception e) {
                log.error("安全事件写入失败：eventType={}, riskLevel={}, exceptionType={}",
                        eventLog.getEventType(), eventLog.getRiskLevel(), e.getClass().getSimpleName());
                auditAlertService.alert(AuditAlertService.TYPE_WRITE_FAILURE,
                        "security_event_log写入失败: eventType=" + eventLog.getEventType()
                                + ", exception=" + e.getClass().getSimpleName());
            }
        } finally {
            securityEventChainLock.unlock();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordLoginLog(LoginLog loginLog) {
        if (loginLog == null) {
            return;
        }
        if (loginLog.getLoginTime() == null) {
            loginLog.setLoginTime(new Date());
        }
        if (loginLog.getRequestId() == null) {
            loginLog.setRequestId(RequestIdFilter.currentRequestId());
        }

        loginLogChainLock.lock();
        try {
            applyLoginLogChain(loginLog);
            try {
                loginLogMapper.insert(loginLog);
                log.debug("登录日志已记录：username={}, status={}", loginLog.getUserName(), loginLog.getLoginStatus());
            } catch (Exception e) {
                log.error("登录日志写入失败：username={}, exceptionType={}",
                        loginLog.getUserName(), e.getClass().getSimpleName());
                auditAlertService.alert(AuditAlertService.TYPE_WRITE_FAILURE,
                        "login_log写入失败: username=" + loginLog.getUserName()
                                + ", exception=" + e.getClass().getSimpleName());
            }
        } finally {
            loginLogChainLock.unlock();
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSystemOperation(String module, String operationType, String description, String riskLevel, int affectedCount) {
        OperateLog systemLog = new OperateLog();
        systemLog.setOperatorName("SYSTEM");
        systemLog.setOperatorUsername("SYSTEM");
        systemLog.setOperatorRole("SYSTEM");
        systemLog.setOperationType(operationType);
        systemLog.setModule(module);
        systemLog.setDescription(description);
        systemLog.setRiskLevel(riskLevel != null && !riskLevel.isEmpty() ? riskLevel : RISK_LOW);
        systemLog.setAffectedCount(affectedCount);
        systemLog.setOperationResult("SUCCESS");
        systemLog.setDeviceType("SYSTEM");
        persistOperateLog(systemLog, false);
    }

    @Override
    public String normalizeAction(String operationType) {
        if (operationType == null || operationType.isEmpty()) {
            return operationType;
        }
        return ACTION_NORMALIZE.getOrDefault(operationType, operationType);
    }

    @Override
    public List<String> expandActionAliases(String action) {
        List<String> expanded = new ArrayList<>();
        if (action == null || action.isEmpty()) {
            return expanded;
        }
        Set<String> aliases = ACTION_ALIASES.get(action);
        if (aliases != null) {
            expanded.addAll(aliases);
        } else {
            expanded.add(action);
        }
        return expanded;
    }

    /**
     * 写入操作日志；critical=true 时写入失败抛出异常阻止业务执行
     */
    private void persistOperateLog(OperateLog operateLog, boolean critical) {
        if (operateLog == null) {
            if (critical) {
                throw new BusinessException("关键操作审计参数不能为空");
            }
            return;
        }

        // 审计时间与请求链路一律由服务端生成，不接受外部传入值
        operateLog.setOperateTime(new Date());
        if (operateLog.getRequestId() == null) {
            operateLog.setRequestId(RequestIdFilter.currentRequestId());
        }
        if (operateLog.getRiskLevel() == null || operateLog.getRiskLevel().isEmpty()) {
            operateLog.setRiskLevel(RISK_LOW);
        }
        operateLog.setOperationType(normalizeAction(operateLog.getOperationType()));

        operateLogChainLock.lock();
        try {
            applyOperateLogChain(operateLog);
            try {
                operateLogMapper.insert(operateLog);
                log.debug("操作日志已记录：module={}, operationType={}, result={}",
                        operateLog.getModule(), operateLog.getOperationType(), operateLog.getOperationResult());
            } catch (Exception e) {
                log.error("操作日志写入失败：module={}, operationType={}, result={}, exceptionType={}",
                        operateLog.getModule(), operateLog.getOperationType(), operateLog.getOperationResult(),
                        e.getClass().getSimpleName());
                // 计划要求：审计写入失败必须可观测告警
                auditAlertService.alert(AuditAlertService.TYPE_WRITE_FAILURE,
                        "operate_log写入失败: module=" + operateLog.getModule()
                                + ", operationType=" + operateLog.getOperationType()
                                + ", exception=" + e.getClass().getSimpleName());
                if (critical) {
                    // 审计不可用时禁止关键操作执行
                    throw new BusinessException("审计服务暂不可用，关键操作已被拒绝，请稍后重试");
                }
            }
        } finally {
            operateLogChainLock.unlock();
        }
    }

    /** P2 哈希链：取链尾→计算本节点哈希；失败时降级为无哈希写入并告警（可用性优先） */
    private void applyOperateLogChain(OperateLog operateLog) {
        try {
            String prev = operateLogMapper.selectLastChainHash();
            operateLog.setPrevHash(prev != null ? prev : AuditHashService.GENESIS_PREV_HASH);
            operateLog.setKeyVersion(auditHashService.currentKeyVersion());
            operateLog.setEntryHash(auditHashService.computeEntryHash(
                    operateLog.getPrevHash(), auditHashService.canonicalPayload(operateLog)));
        } catch (Exception e) {
            operateLog.setEntryHash(null);
            operateLog.setPrevHash(null);
            operateLog.setKeyVersion(null);
            log.error("操作日志哈希链计算降级: {}", e.getMessage());
            auditAlertService.alert(AuditAlertService.TYPE_WRITE_FAILURE,
                    "operate_log哈希链计算降级: " + e.getMessage());
        }
    }

    private void applyLoginLogChain(LoginLog loginLog) {
        try {
            String prev = loginLogMapper.selectLastChainHash();
            loginLog.setPrevHash(prev != null ? prev : AuditHashService.GENESIS_PREV_HASH);
            loginLog.setKeyVersion(auditHashService.currentKeyVersion());
            loginLog.setEntryHash(auditHashService.computeEntryHash(
                    loginLog.getPrevHash(), auditHashService.canonicalPayload(loginLog)));
        } catch (Exception e) {
            loginLog.setEntryHash(null);
            loginLog.setPrevHash(null);
            loginLog.setKeyVersion(null);
            log.error("登录日志哈希链计算降级: {}", e.getMessage());
            auditAlertService.alert(AuditAlertService.TYPE_WRITE_FAILURE,
                    "login_log哈希链计算降级: " + e.getMessage());
        }
    }

    private void applySecurityEventChain(SecurityEventLog eventLog) {
        try {
            String prev = securityEventLogMapper.selectLastChainHash();
            eventLog.setPrevHash(prev != null ? prev : AuditHashService.GENESIS_PREV_HASH);
            eventLog.setKeyVersion(auditHashService.currentKeyVersion());
            eventLog.setEntryHash(auditHashService.computeEntryHash(
                    eventLog.getPrevHash(), auditHashService.canonicalPayload(eventLog)));
        } catch (Exception e) {
            eventLog.setEntryHash(null);
            eventLog.setPrevHash(null);
            eventLog.setKeyVersion(null);
            log.error("安全事件哈希链计算降级: {}", e.getMessage());
            auditAlertService.alert(AuditAlertService.TYPE_WRITE_FAILURE,
                    "security_event_log哈希链计算降级: " + e.getMessage());
        }
    }
}
