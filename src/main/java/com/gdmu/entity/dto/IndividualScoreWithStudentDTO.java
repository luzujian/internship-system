package com.gdmu.entity.dto;

import com.gdmu.entity.IndividualScore;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 包含学生信息的个人评分DTO类
 */
@Data
public class IndividualScoreWithStudentDTO {
    // 个人评分信息
    private Long id;
    private Long submissionId;
    private Long studentUserId;
    private String studentName;
    private String studentNumber;
    private BigDecimal teacherScore; // 教师给小组的评分
    private BigDecimal contributionRatio; // 个人贡献度百分比
    private BigDecimal individualFinalScore; // 个人最终得分
    private BigDecimal difficultyFactor;
    private BigDecimal collaborationFactor;
    private boolean isPublished;
    
    // 用于教师手动修改评分的字段
    private boolean isModified = false;
    private BigDecimal manualScore;
    
    /**
     * 从IndividualScore转换为DTO，需要手动设置学生信息
     */
    public static IndividualScoreWithStudentDTO fromIndividualScore(IndividualScore score) {
        IndividualScoreWithStudentDTO dto = new IndividualScoreWithStudentDTO();
        dto.setId(score.getId());
        dto.setSubmissionId(score.getSubmissionId());
        dto.setStudentUserId(score.getStudentUserId());
        dto.setTeacherScore(score.getTeacherScore());
        dto.setContributionRatio(score.getContributionRatio());
        dto.setIndividualFinalScore(score.getIndividualFinalScore());
        dto.setDifficultyFactor(score.getDifficultyFactor());
        dto.setCollaborationFactor(score.getCollaborationFactor());
        dto.setPublished(score.isPublished());
        dto.setManualScore(score.getIndividualFinalScore()); // 默认使用计算分数
        return dto;
    }
}