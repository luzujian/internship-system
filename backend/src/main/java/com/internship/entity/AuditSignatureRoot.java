package com.internship.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 审计日志每日签名根（P2 完整性增强）
 * 每天 03:00 为三张日志表签署前一日的链状态，作为完整性校验锚点
 */
@Data
public class AuditSignatureRoot {
    private Long id;
    /** 签名覆盖的数据日期（任务签署前一日） */
    @JsonFormat(pattern = "yyyy-MM-dd", timezone = "GMT+8")
    private Date rootDate;
    /** 日志表名（operate_log/login_log/security_event_log） */
    private String tableName;
    /** 签署时该表最后一条已链式记录ID */
    private Long lastEntryId;
    /** 签署时链尾哈希 */
    private String lastEntryHash;
    /** 截至签署时的累计链式记录数 */
    private Long entryCount;
    /** HMAC-SHA256 签名（hex） */
    private String signature;
    /** 签名密钥版本 */
    private Integer keyVersion;
    /** 签署时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createdAt;
}
