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
}
