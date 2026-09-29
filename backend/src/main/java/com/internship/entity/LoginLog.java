package com.internship.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 登录日志实体类
 */
@Data
public class LoginLog {
    private Long id; // ID，自增主键
    private String userId; // 用户ID
    private String userType; // 用户类型
    private String userName; // 用户名

    /** 详情展示字段：按业务用户表解析出的操作人姓名，不落库 */
    private String operatorName;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date loginTime; // 登录时间，默认当前时间戳

    private String ipAddress; // IP地址
    private String deviceInfo; // 设备信息
    private String loginStatus; // 登录状态，默认SUCCESS

    // ===== P1 审计扩展字段（旧数据允许为空） =====
    private String requestId;      // 请求链路ID
    private String riskLevel;      // 风险等级（LOW/MEDIUM/HIGH/CRITICAL）
    private String errorCode;      // 错误码
    private String deviceType;     // 设备类别（WEB/UNKNOWN）
    private Integer durationMs;    // 登录耗时（毫秒）
    private String detailJson;     // 脱敏后的详情JSON

    // ===== P2 哈希链字段（旧数据允许为空） =====
    private String entryHash;      // 本条链节点哈希
    private String prevHash;       // 前一条链节点哈希
    private Integer keyVersion;    // 签名密钥版本
}
