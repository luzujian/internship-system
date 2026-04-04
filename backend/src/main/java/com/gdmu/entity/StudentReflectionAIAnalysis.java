package com.gdmu.entity;

import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Data
public class StudentReflectionAIAnalysis {
    private Long id;
    private Long reflectionId;
    private Long studentId;
    private Long counselorId;
    private String overallAnalysis;
    private List<String> keywords;
    private Integer sentimentPositive;
    private Integer sentimentNeutral;
    private Integer sentimentNegative;
    private Map<String, Object> scoreDetails;
    private BigDecimal totalScore;
    private String grade;
    private String analysisReport;
    private String aiModelCode;
    private Date analysisTime;
    private Date createTime;
    private Date updateTime;
    
    private StudentUser student;
    private TeacherUser counselor;
}
