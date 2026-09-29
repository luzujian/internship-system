package com.internship.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 安全事件日志实体类（P1 新增）
 * 记录登录成功/失败、锁定、登出、Token异常、401/403、限流等安全相关事件
 */
@Data
public class SecurityEventLog {
    private Long id;
    /** 服务端事件时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date eventTime;
    private String requestId;           // 请求链路ID
    private String eventType;           // 事件类型
    private String userId;              // 用户ID
    private String username;            // 用户名
    private String userRole;            // 用户角色
    private String realOperatorName;    // 真实管理员身份（角色切换场景）

    /** 详情展示字段：按业务用户表解析出的操作人姓名，不落库 */
    private String operatorName;
    private String riskLevel;           // 风险等级（LOW/MEDIUM/HIGH/CRITICAL）
    private String result;              // 结果（SUCCESS/FAILURE/ATTEMPTED）
    private String errorCode;           // 错误码
    private String targetType;          // 目标对象类型
    private String targetId;            // 目标对象ID
    private Integer affectedCount;      // 影响数量
    private String httpMethod;          // 请求方法
    private String requestPath;         // 请求路径
    private String ipAddress;           // 规范化IP
    private String deviceType;          // 设备类别（WEB/UNKNOWN）
    private Integer durationMs;         // 耗时（毫秒）
    private String description;         // 事件描述
    private String detailJson;          // 脱敏后的详情JSON

    // ===== P2 哈希链字段（旧数据允许为空） =====
    private String entryHash;           // 本条链节点哈希
    private String prevHash;            // 前一条链节点哈希
    private Integer keyVersion;         // 签名密钥版本
}
