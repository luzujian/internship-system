package com.gdmu.service;

import com.gdmu.entity.Contribution;
import java.util.List;

public interface ContributionService {
    List<Contribution> getBySubmissionId(Long submissionId);
    Contribution getBySubmissionIdAndStudentId(Long submissionId, Long studentId);
    int save(Contribution contribution);
    int update(Contribution contribution);
    int deleteBySubmissionId(Long submissionId);
    // 根据提交ID和评分人ID查询贡献度评分
    List<Contribution> getBySubmissionIdAndRatedBy(Long submissionId, Long ratedBy);
    // 根据提交ID、学生ID和评分人ID查询贡献度评分
    Contribution getBySubmissionIdAndStudentIdAndRatedBy(Long submissionId, Long studentId, Long ratedBy);
}