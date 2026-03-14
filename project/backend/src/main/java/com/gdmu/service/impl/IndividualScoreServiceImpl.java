package com.gdmu.service.impl;

import com.gdmu.entity.IndividualScore;
import com.gdmu.mapper.IndividualScoreMapper;
import com.gdmu.service.IndividualScoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class IndividualScoreServiceImpl implements IndividualScoreService {

    @Autowired
    private IndividualScoreMapper individualScoreMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean calculateIndividualScores(Long submissionId) {
        log.debug("计算个人评分，提交ID: {}", submissionId);
        
        if (submissionId == null || submissionId <= 0) {
            log.warn("提交ID无效");
            return false;
        }
        
        try {
            List<IndividualScore> scores = individualScoreMapper.selectBySubmissionId(submissionId);
            if (scores == null || scores.isEmpty()) {
                log.warn("未找到提交ID对应的评分数据");
                return false;
            }
            
            for (IndividualScore score : scores) {
                double teacherScore = score.getTeacherScore() != null ? score.getTeacherScore().doubleValue() : 0.0;
                double contributionRatio = score.getContributionRatio() != null ? score.getContributionRatio().doubleValue() : 1.0;
                double difficultyFactor = score.getDifficultyFactor() != null ? score.getDifficultyFactor().doubleValue() : 1.0;
                double collaborationFactor = score.getCollaborationFactor() != null ? score.getCollaborationFactor().doubleValue() : 1.0;
                
                double finalScore = calculateScoreByAlgorithm(teacherScore, contributionRatio, difficultyFactor, collaborationFactor);
                score.setIndividualFinalScore(java.math.BigDecimal.valueOf(finalScore));
                
                individualScoreMapper.update(score);
            }
            
            log.info("个人评分计算完成，提交ID: {}", submissionId);
            return true;
        } catch (Exception e) {
            log.error("计算个人评分失败: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public List<IndividualScore> getBySubmissionId(Long submissionId) {
        log.debug("获取个人评分，提交ID: {}", submissionId);
        return individualScoreMapper.selectBySubmissionId(submissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean publishScores(Long submissionId) {
        log.debug("发布个人评分，提交ID: {}", submissionId);
        
        if (submissionId == null || submissionId <= 0) {
            log.warn("提交ID无效");
            return false;
        }
        
        try {
            List<IndividualScore> scores = individualScoreMapper.selectBySubmissionId(submissionId);
            if (scores == null || scores.isEmpty()) {
                log.warn("未找到提交ID对应的评分数据");
                return false;
            }
            
            Date publishTime = new Date();
            individualScoreMapper.batchUpdatePublishStatus(submissionId, true, publishTime);
            
            log.info("个人评分发布完成，提交ID: {}", submissionId);
            return true;
        } catch (Exception e) {
            log.error("发布个人评分失败: {}", e.getMessage(), e);
            return false;
        }
    }

    public static double calculateScoreByAlgorithm(double teacherScore, double contributionRatio, 
                                            double difficultyFactor, double collaborationFactor) {
        return teacherScore * contributionRatio * (difficultyFactor * 0.5 + collaborationFactor * 0.5);
    }
}
