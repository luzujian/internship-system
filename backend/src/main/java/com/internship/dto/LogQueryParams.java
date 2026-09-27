package com.internship.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 日志中心统一查询参数（操作日志/登录日志/安全事件共用）
 * 时间范围、操作人、角色、模块、动作、结果、风险、IP、目标ID、requestId
 */
@Data
public class LogQueryParams {
    /** 开始时间（默认由 Controller 设置为最近7天前） */
    private Date startTime;
    /** 结束时间 */
    private Date endTime;
    /** 操作人姓名/用户名（模糊匹配） */
    private String operatorName;
    /** 角色 */
    private String role;
    /** 模块 */
    private String module;
    /** 动作/事件类型（动作已由后端规范化扩展为别名列表） */
    private List<String> actionList;
    /** 结果（SUCCESS/FAILURE；安全事件含 ATTEMPTED） */
    private String result;
    /** 风险等级（LOW/MEDIUM/HIGH/CRITICAL） */
    private String riskLevel;
    /** IP（模糊匹配） */
    private String ip;
    /** 目标ID */
    private String targetId;
    /** 请求链路ID */
    private String requestId;
    /** 排序字段（Controller 白名单校验后传入） */
    private String sortField;
    /** 排序方向（asc/desc，Controller 校验） */
    private String sortOrder;
}
