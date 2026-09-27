package com.internship.entity;

import lombok.Data;
import java.util.Date;

/**
 * 教师自动化任务（一名教师多条）。
 * 流程：自然语言描述 → 模型解析预览 → 确认创建 → 启用 → 条件触发 → 去重执行 → 记录日志。
 * 范围：辅导员本人负责班级；条件复用学生首页待办口径，动作复用站内提醒/邮件。
 */
@Data
public class TeacherAutoTask {
    private Long id;
    private Long teacherId;
    private String teacherType;
    /** 任务名称（预览时自动生成，可修改） */
    private String name;
    /** 触发条件：REFLECTION_NOT_SUBMITTED / APPRAISAL_MISSING / OFFER_PENDING / SCHEDULED_REMINDER */
    private String triggerCondition;
    /** 临期天数：1-30；材料类条件距截止日 ≤ N 天或已逾期时触发；Offer 条件忽略 */
    private Integer triggerDays;
    /** 同一学生同一渠道最小重发间隔（分钟），60-10080，默认1440 */
    private Integer frequencyMinutes;
    /** 任务结束时间；到期后不再执行（null=不自动结束） */
    private Date endAt;
    /** 固定调度配置 JSON：mode/date/time/weekdays；为空时沿用截止窗口模式。 */
    private String scheduleConfig;
    /** 生命周期：ACTIVE / PAUSED / ENDED（blockedReason 非空表示因权限暂停） */
    private String status;
    /** 权限撤销等导致的停止原因；能力恢复后下次成功执行清除 */
    private String blockedReason;
    /** 对象范围 JSON（预留，当前为空=辅导员全部负责班级） */
    private String targetScope;
    /** Read-time presentation only; selected IDs are persisted in targetScope. */
    private java.util.List<Long> targetStudentIds;
    private String targetLabel;
    /** 执行动作：STATION_REMINDER / EMAIL */
    private String action;
    /** 提醒文案模板（1-200字） */
    private String messageTemplate;
    /** 兼容字段：1=ACTIVE，0=PAUSED；ENDED 时为0 */
    private Integer enabled;
    private Date lastRunAt;
    private Date createTime;
    private Date updateTime;
    /** Changes to configuration or lifecycle invalidate an in-flight run. */
    private long version;

    public boolean isActive() {
        return "ACTIVE".equals(status) || (status == null && Integer.valueOf(1).equals(enabled));
    }
}
