package com.internship.service;

import com.internship.entity.StudentJobApplication;
import java.util.List;

public interface StudentJobApplicationService {
    List<StudentJobApplication> findByStudentId(Long studentId);
    StudentJobApplication findById(Long id);
    int create(StudentJobApplication application);
    int update(StudentJobApplication application);
    int updateStatusIfActive(Long id, String status, String rejectReason);
    int updateLatestStatusIfActive(Long studentId, Long positionId, String status, String rejectReason);
    int claimPendingForDirectHire(Long id, Long companyId);
    int deleteById(Long id, Long studentId);
    List<StudentJobApplication> findByCompanyId(Long companyId);
}
