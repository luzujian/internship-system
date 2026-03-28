package com.gdmu.mapper;

import com.gdmu.entity.InternshipEvaluationDetail;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface InternshipEvaluationDetailMapper {
    int insert(InternshipEvaluationDetail detail);
    
    int update(InternshipEvaluationDetail detail);
    
    int deleteById(Long id);
    
    int deleteByEvaluationId(Long evaluationId);
    
    int deleteByStudentId(Long studentId);
    
    InternshipEvaluationDetail findById(Long id);
    
    List<InternshipEvaluationDetail> findByEvaluationId(Long evaluationId);
    
    List<InternshipEvaluationDetail> findByStudentId(Long studentId);
    
    int batchInsert(@Param("details") List<InternshipEvaluationDetail> details);
    
    int deleteByEvaluationIdAndCategoryCode(@Param("evaluationId") Long evaluationId, @Param("categoryCode") String categoryCode);
}
