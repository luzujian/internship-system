package com.internship.mapper;

import com.internship.entity.CompanyRecommendationRating;
import com.internship.entity.dto.CompanyRatingSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface CompanyRecommendationRatingMapper {
    List<CompanyRecommendationRating> findBySubmissionId(@Param("submissionId") Long submissionId);

    int deleteBySubmissionId(@Param("submissionId") Long submissionId);

    int insertBatch(@Param("ratings") List<CompanyRecommendationRating> ratings);

    List<CompanyRatingSummary> findSubmittedSummariesByCompanyIds(
            @Param("companyIds") Collection<Long> companyIds);
}
