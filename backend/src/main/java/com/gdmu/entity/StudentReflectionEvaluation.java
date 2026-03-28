package com.gdmu.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;

@Data
public class StudentReflectionEvaluation {
    private Long id;
    private Long reflectionId;
    private Long studentId;
    private Long counselorId;
    private Long aiAnalysisId;
    private Map<String, Object> scoreDetails;
    private BigDecimal totalScore;
    private String grade;
    private String teacherComment;
    private Integer isAiScoreModified;
    private Date evaluateTime;
    private Date createTime;
    private Date updateTime;
    
    private StudentUser student;
    private TeacherUser counselor;
    private StudentReflectionAIAnalysis aiAnalysis;
}
