package com.gdmu.service;

import com.gdmu.entity.InternshipEvaluationDetail;
import java.util.List;

public interface InternshipEvaluationDetailService {
    int insert(InternshipEvaluationDetail detail);
    
    int update(InternshipEvaluationDetail detail);
    
    int deleteById(Long id);
    
    int deleteByEvaluationId(Long evaluationId);
    
    int deleteByStudentId(Long studentId);
    
    InternshipEvaluationDetail findById(Long id);
    
    List<InternshipEvaluationDetail> findByEvaluationId(Long evaluationId);
    
    List<InternshipEvaluationDetail> findByStudentId(Long studentId);
    
    int batchInsert(List<InternshipEvaluationDetail> details);
    
    int saveEvaluationDetails(Long evaluationId, Long studentId, List<InternshipEvaluationDetail> details);
}
