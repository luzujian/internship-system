package com.internship.service;

import com.internship.entity.CompanyRecommendationRating;
import com.internship.entity.dto.CompanyRatingSummary;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface CompanyRecommendationRatingService {
    List<CompanyRecommendationRating> findBySubmissionId(Long submissionId);

    void replaceSubmissionRatings(Long submissionId, List<CompanyRecommendationRating> ratings);

    Map<Long, CompanyRatingSummary> findSubmittedSummaries(Collection<Long> companyIds);
}
