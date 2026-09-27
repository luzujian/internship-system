package com.internship.dto;

import lombok.Data;

/**
 * 日志导出请求（P1）：只导出当前筛选范围，上限 100000 条
 */
@Data
public class LogExportRequest {
    /** 日志类别：operations / login-events / security-events */
    private String category;
    /** 开始时间（yyyy-MM-dd HH:mm:ss） */
    private String startTime;
    /** 结束时间（yyyy-MM-dd HH:mm:ss） */
    private String endTime;
    private String operatorName;
    private String role;
    private String module;
    private String action;
    private String result;
    private String riskLevel;
    private String ip;
    private String targetId;
    private String requestId;
}
