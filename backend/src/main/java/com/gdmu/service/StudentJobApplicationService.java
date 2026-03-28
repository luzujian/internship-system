package com.gdmu.service;

import com.gdmu.entity.StudentJobApplication;
import java.util.List;

public interface StudentJobApplicationService {
    List<StudentJobApplication> findByStudentId(Long studentId);
    StudentJobApplication findById(Long id);
    int create(StudentJobApplication application);
    int update(StudentJobApplication application);
    int deleteById(Long id, Long studentId);
    List<StudentJobApplication> findByCompanyId(Long companyId);
}
