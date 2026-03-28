package com.gdmu.service;

import com.gdmu.entity.InternshipReflection;

import java.util.List;
import java.util.Map;

public interface InternshipReflectionService {

    int insert(InternshipReflection internshipReflection);

    int updateById(InternshipReflection internshipReflection);

    int deleteById(Long id);

    InternshipReflection findById(Long id);

    InternshipReflection findByStudentId(Long studentId);

    List<InternshipReflection> findAll();

    List<InternshipReflection> findByStatus(Integer status);

    List<InternshipReflection> list(Long studentId, String studentName, String studentUserId, Integer status);

    int countByStatus(Integer status);

    Map<String, Object> analyzeReflection(Long id);

    Map<String, Object> analyzeReflectionContent(String content);

    Map<String, Object> analyzeReflectionContent(String content, String modelCode);

    /**
     * 根据提交时间计算实习心得所属的阶段编号
     * @param submitTime 提交时间
     * @return 阶段编号（从1开始），如果不在实习期间内返回null
     */
    Integer calculatePeriodNumber(java.util.Date submitTime);

    /**
     * 检查学生是否在指定阶段已提交实习心得
     * @param studentId 学生ID
     * @param periodNumber 阶段编号
     * @return 已提交返回true，否则返回false
     */
    boolean existsByStudentIdAndPeriodNumber(Long studentId, Integer periodNumber);
}
