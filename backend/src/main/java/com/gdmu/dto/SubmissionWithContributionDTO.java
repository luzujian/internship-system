package com.gdmu.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

/**
 * 作业提交包含贡献度的数据传输对象
 */
@Data
public class SubmissionWithContributionDTO {
    
    // 作业提交基本信息
    private Long assignmentId;
    private Long groupId;
    private String content;
    private String submitNote;
    private String onlineContent;
    
    // 文件相关
    private List<String> filePaths;
    
    // 贡献度评分信息
    private Map<Long, Double> contributionScores; // key: 学生ID, value: 贡献度分数(0-100)
    
    // 提交者ID（组长ID）
    private Long submitterId;
    
    // 小组人数
    private Integer groupMemberCount;
}