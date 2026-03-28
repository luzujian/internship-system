package com.gdmu.service;

import com.gdmu.entity.Class;
import com.gdmu.entity.ClassCounselorRelation;

import java.util.List;
import java.util.Map;

public interface ClassCounselorRelationService {
    
    int assignClassToCounselor(Long classId, Long counselorId, String className, String counselorName);
    
    int assignClassesToCounselor(Long counselorId, List<Long> classIds);
    
    int unassignClassFromCounselor(Long counselorId, Long classId);
    
    List<ClassCounselorRelation> findByCounselorId(Long counselorId);
    
    List<ClassCounselorRelation> findByClassId(Long classId);
    
    List<Class> findClassesByCounselorId(Long counselorId);
    
    List<Map<String, Object>> findStudentsByClassId(Long classId);
    
    List<Map<String, Object>> findStudentsByClassIdWithCounselor(Long classId, Long counselorId);
    
    List<Map<String, Object>> findStudentsByCounselorId(Long counselorId, String searchName, Long classId, String major, String grade, Integer status, String companyName);
    
    Map<String, Object> getCounselorStatistics(Long counselorId);
    
    Long findCounselorIdByStudentId(Long studentId);
    
    ClassCounselorRelation findById(Long id);
}
