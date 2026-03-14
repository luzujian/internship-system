package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 作业提交实体类
 */
@Data
public class Submission {
    private Long id;
    private Long assignmentId;
    private Long groupId;
    private String content;
    
    // 与数据库表字段对应
    private String filePath;
    
    private Date submitTime;
    private Double teacherScore;
    
    // 新增字段：提交状态（SUBMITTED已提交，GRADED已评分，REJECTED被退回）
    private String status = "SUBMITTED";
    
    // 新增字段：提交者ID（组长ID）
    private Long submitterId;
    
    // 新增字段：提交备注
    private String submitNote;
    
    // 新增字段：在线编辑内容（JSON格式存储）
    private String onlineContent;
    
    // 新增字段：文件数量
    private Integer fileCount = 0;
    
    // 新增字段：是否包含贡献度评分
    private Boolean hasContribution = false;
    
    // 新增字段：贡献度评分JSON（存储所有成员的贡献度评分）
    private String contributionScores;
    
    // 新增字段：小组人数（用于评分算法）
    private Integer groupMemberCount = 0;
    
    // 新增字段：是否已计算最终成绩
    private Boolean isFinalScoreCalculated = false;
    
    // 新增字段：教师评语
    private String teacherComment;
    
    // 新增字段：评分时间
    private Date gradedTime;
    
    // 新增字段：评分教师ID
    private Long gradedBy;
    
    // 新增字段：是否已发布成绩
    private Boolean isScorePublished = false;
    
    // 新增字段：成绩发布时间
    private Date scorePublishTime;
}