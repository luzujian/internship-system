package com.gdmu.entity;

import lombok.Data;
import java.util.Date;
import java.util.List;

/**
 * 实习AI分析结果实体类
 */
@Data
public class InternshipAIAnalysis {
    private Long id;
    private Long studentId;
    private String overallAnalysis;
    private List<String> keywords;
    private Integer sentimentPositive;
    private Integer sentimentNeutral;
    private Integer sentimentNegative;
    private Integer suggestedAttitudeScore;
    private Integer suggestedPerformanceScore;
    private Integer suggestedReportScore;
    private Date analysisTime;
    private Date updateTime;
    
    private StudentUser student;
}
