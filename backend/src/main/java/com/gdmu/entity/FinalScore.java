package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 最终成绩实体类
 */
@Data
public class FinalScore {
    private Long id;
    private Long studentId;
    private Long assignmentId;
    private Double score;

    // 新增字段：课程ID
    private Long courseId;
    
    // 新增字段：评分类型（如：EXAM考试、ASSIGNMENT作业、ATTENDANCE出勤等）
    private String type;
    
    // 新增字段：权重（用于计算总评成绩）
    private Double weight = 1.0;
    
    // 新增字段：评分时间
    private Date gradedTime;
    
    // 新增字段：评分人ID（教师ID）
    private Integer gradedBy;
    
    // 新增字段：评语
    private String comment;
    
    private Date createTime;
    private Date updateTime;
}