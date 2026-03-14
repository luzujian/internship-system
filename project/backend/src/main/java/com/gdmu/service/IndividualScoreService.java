package com.gdmu.service;

import com.gdmu.entity.IndividualScore;

import java.util.List;

public interface IndividualScoreService {
    
    boolean calculateIndividualScores(Long submissionId);
    
    List<IndividualScore> getBySubmissionId(Long submissionId);
    
    boolean publishScores(Long submissionId);
}
