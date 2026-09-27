package com.internship.entity;

import lombok.Data;
import java.util.Date;

/**
 * 教师自动化任务执行记录（每次定时触发写一条，供任务管理页查询）。
 */
@Data
public class TeacherAutoTaskLog {
    private Long id;
    private Long taskId;
    private Date executedAt;
    private String channel;
    private Integer targetCount;
    private Integer successCount;
    /** 明细：命中学生数、各渠道结果、跳过原因 */
    private String detail;
    private Date createTime;
}
