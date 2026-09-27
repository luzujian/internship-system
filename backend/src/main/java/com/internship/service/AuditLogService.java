package com.internship.service;

import com.internship.entity.LoginLog;
import com.internship.entity.OperateLog;
import com.internship.entity.SecurityEventLog;

import java.util.List;

/**
 * 统一审计日志写入服务（P1 扩展）。
 * 所有操作日志、登录日志、安全事件必须经由此服务写入，
 * 禁止在 Controller / Aspect / Filter 中直接调用对应 Mapper.insert。
 */
public interface AuditLogService {

    /** 风险等级常量 */
    String RISK_LOW = "LOW";
    String RISK_MEDIUM = "MEDIUM";
    String RISK_HIGH = "HIGH";
    String RISK_CRITICAL = "CRITICAL";

    /**
     * 记录一条操作审计日志（普通操作）。
     * 操作时间由服务端补全；写入失败时记录告警日志，但不抛出异常，
     * 以免审计写入失败覆盖业务操作结果。
     */
    void record(OperateLog operateLog);

    /**
     * 记录关键操作（权限调整、密码重置、批量删除、备份恢复、日志留存等）的 ATTEMPTED 审计。
     * 审计写入失败时抛出异常，禁止业务继续执行；写入成功后业务方可执行，
     * 业务结束后仍需调用 record() 写入最终结果。
     */
    void recordCriticalAttempt(OperateLog operateLog);

    /**
     * 记录一条安全事件（登录成功/失败、锁定、登出、Token异常、401/403 等）。
     * 事件时间由服务端补全；写入失败时仅记录告警，不抛出异常。
     */
    void recordSecurityEvent(SecurityEventLog eventLog);

    /**
     * 记录一条登录日志（统一入口，自动补全 requestId）。
     * 写入失败时仅记录告警，不抛出异常。
     */
    void recordLoginLog(LoginLog loginLog);

    /**
     * 后台任务以 SYSTEM 身份记录自动状态变更（每批次汇总一条，affectedCount 为影响数量）。
     */
    void recordSystemOperation(String module, String operationType, String description, String riskLevel, int affectedCount);

    /**
     * 动作规范化：将 ADD/INSERT/CREATE 统一为 CREATE 等规范枚举值
     */
    String normalizeAction(String operationType);

    /**
     * 动作别名扩展：筛选规范动作时返回包含历史别名的列表（如 CREATE -> [CREATE, ADD, INSERT]）
     */
    List<String> expandActionAliases(String action);
}
