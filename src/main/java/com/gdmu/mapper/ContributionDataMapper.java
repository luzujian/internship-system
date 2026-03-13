package com.gdmu.mapper;

import com.gdmu.entity.ContributionData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface ContributionDataMapper {
    
    // 根据提交ID查询贡献度记录
    List<ContributionData> selectBySubmissionId(@Param("submissionId") Long submissionId);
    
    // 根据学生ID查询贡献度记录
    List<ContributionData> selectByStudentId(@Param("studentId") Long studentId);
    
    // 根据提交ID和学生ID查询贡献度记录
    ContributionData selectBySubmissionAndStudent(@Param("submissionId") Long submissionId, @Param("studentId") Long studentId);
    
    // 插入贡献度记录
    int insert(ContributionData contributionData);
    
    // 更新贡献度记录
    int update(ContributionData contributionData);
    
    // 删除贡献度记录
    int deleteById(@Param("id") Long id);
    
    // 根据提交ID删除贡献度记录
    int deleteBySubmissionId(@Param("submissionId") Long submissionId);
    
    // 统计提交的总贡献度
    Double sumContributionBySubmissionId(@Param("submissionId") Long submissionId);
}