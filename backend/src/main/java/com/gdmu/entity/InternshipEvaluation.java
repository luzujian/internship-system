package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 实习评分实体类
 */
@Data
public class InternshipEvaluation {
    private Long id;
    private Long studentId;
    private Integer attitudeScore;
    private Integer performanceScore;
    private Integer reportScore;
    private Integer companyEvaluationScore;
    private Integer totalScore;
    private String grade;
    private String comment;
    private Long evaluatorId;
    private Date evaluateTime;
    private Date updateTime;
    private Integer gradePublished;  // 成绩是否已发布 0-未发布 1-已发布

    private StudentUser student;
    private TeacherUser evaluator;
}
