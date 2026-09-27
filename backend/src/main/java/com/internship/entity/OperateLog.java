package com.internship.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.util.Date;

@Data
public class OperateLog {
    private Long id;
    private Long operateAdminId;
    private String operatorName;
    private String operatorUsername;
    private String operatorRole;
    private String ipAddress;
    private String operationType;
    private String module;
    private String description;
    private String operationResult;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date operateTime;

    // ===== P1 审计扩展字段（旧数据允许为空） =====
    private String requestId;          // 请求链路ID
    private String riskLevel;          // 风险等级（LOW/MEDIUM/HIGH/CRITICAL）
    private String errorCode;          // 错误码
    private String targetType;         // 目标对象类型
    private String targetId;           // 目标对象ID
    private Integer affectedCount;     // 影响数量
    private String realOperatorName;   // 真实管理员身份（角色切换场景）
    private String httpMethod;         // 请求方法
    private String requestPath;        // 请求路径
    private String deviceType;         // 设备类别（WEB/UNKNOWN）
    private Integer durationMs;        // 操作耗时（毫秒）
    private String detailJson;         // 脱敏后的详情JSON

    // ===== P2 哈希链字段（旧数据允许为空） =====
    private String entryHash;          // 本条链节点哈希
    private String prevHash;           // 前一条链节点哈希
    private Integer keyVersion;        // 签名密钥版本
}
