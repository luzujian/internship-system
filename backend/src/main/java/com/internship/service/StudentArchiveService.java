package com.internship.service;

import com.internship.entity.PageResult;
import com.internship.entity.StudentArchive;

import java.util.List;
import java.util.Map;

public interface StudentArchiveService {
    
    int insert(StudentArchive archive);
    
    StudentArchive findById(Long id);
    
    int update(StudentArchive archive);
    
    int delete(Long id);
    
    PageResult<Map<String, Object>> findPage(Integer page, Integer pageSize, String studentName, String fileType, Integer status);
    
    List<StudentArchive> findByStudentId(Long studentId);
    
    Map<String, Object> getStatistics();
    
    int updateStatus(Long id, Integer status);
    
    int batchDelete(List<Long> ids);
}
