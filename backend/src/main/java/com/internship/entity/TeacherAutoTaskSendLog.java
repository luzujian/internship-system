package com.internship.entity;

import lombok.Data;
import java.util.Date;

/** 教师自动化任务单学生发送明细：用于冷却去重、失败重试与执行历史核对。 */
@Data
public class TeacherAutoTaskSendLog {
    private Long id;
    private Long taskId;
    private Long studentId;
    private String studentName;
    /** STATION_REMINDER / EMAIL */
    private String channel;
    private Date sentAt;
    /** SENT / SKIPPED_COOLDOWN / FAILED */
    private String status;
    private String detail;
    private Date createTime;
    private String todoId;
    private String runId;
}
