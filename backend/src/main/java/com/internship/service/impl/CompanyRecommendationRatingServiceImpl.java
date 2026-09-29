package com.internship.service.impl;

import com.internship.entity.CompanyRecommendationRating;
import com.internship.entity.dto.CompanyRatingSummary;
import com.internship.mapper.CompanyRecommendationRatingMapper;
import com.internship.service.CompanyRecommendationRatingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CompanyRecommendationRatingServiceImpl implements CompanyRecommendationRatingService {
    private final CompanyRecommendationRatingMapper mapper;

    public CompanyRecommendationRatingServiceImpl(CompanyRecommendationRatingMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CompanyRecommendationRating> findBySubmissionId(Long submissionId) {
        if (submissionId == null) return Collections.emptyList();
        return mapper.findBySubmissionId(submissionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceSubmissionRatings(Long submissionId, List<CompanyRecommendationRating> ratings) {
        mapper.deleteBySubmissionId(submissionId);
        if (ratings != null && !ratings.isEmpty()) {
            mapper.insertBatch(ratings);
        }
    }

    @Override
    public Map<Long, CompanyRatingSummary> findSubmittedSummaries(Collection<Long> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) return Collections.emptyMap();
        Map<Long, CompanyRatingSummary> summaries = new LinkedHashMap<>();
        for (CompanyRatingSummary summary : mapper.findSubmittedSummariesByCompanyIds(companyIds)) {
            summaries.put(summary.getCompanyId(), summary);
        }
        return summaries;
    }
}
