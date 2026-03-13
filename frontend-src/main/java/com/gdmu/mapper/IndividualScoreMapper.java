package com.gdmu.mapper;

import com.gdmu.entity.IndividualScore;
import com.gdmu.entity.dto.IndividualScoreWithStudentDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface IndividualScoreMapper {
    
    // 根据提交ID查询所有个人评分
    List<IndividualScore> selectBySubmissionId(@Param("submissionId") Long submissionId);
    
    // 根据提交ID和学生ID查询个人评分
    IndividualScore selectBySubmissionIdAndStudentId(@Param("submissionId") Long submissionId, @Param("studentUserId") Long studentUserId);
    
    // 批量插入个人评分
    int batchInsert(@Param("individualScores") List<IndividualScore> individualScores);
    
    // 更新个人评分
    int update(IndividualScore individualScore);
    
    // 根据提交ID删除个人评分
    int deleteBySubmissionId(@Param("submissionId") Long submissionId);
    
    // 批量更新发布状态
    int batchUpdatePublishStatus(@Param("submissionId") Long submissionId, @Param("isPublished") boolean isPublished, @Param("publishTime") java.util.Date publishTime);
    
    // 根据作业ID获取待发布的个人成绩
    List<IndividualScore> selectByAssignmentIdForPublish(Long assignmentId);
    
    // 查询提交ID对应的所有个人评分，包含学生信息
    List<IndividualScoreWithStudentDTO> selectWithStudentInfoBySubmissionId(@Param("submissionId") Long submissionId);
    
    // 手动更新个人评分
    int updateIndividualScore(@Param("id") Long id, @Param("teacherScore") BigDecimal teacherScore, @Param("individualFinalScore") BigDecimal individualFinalScore);
}