package com.internship.service;

import com.internship.entity.InternshipConfirmationRecord;
import java.util.List;

public interface InternshipConfirmationRecordService {

    int insert(InternshipConfirmationRecord record);

    int update(InternshipConfirmationRecord record);

    InternshipConfirmationRecord findById(Long id);

    List<InternshipConfirmationRecord> findByStudentId(Long studentId);

    List<InternshipConfirmationRecord> findByStudentIdOrderByCreateTimeDesc(Long studentId);

    List<InternshipConfirmationRecord> findByCompanyId(Long companyId);

    List<InternshipConfirmationRecord> findByCompanyIdAndStatus(Long companyId, Integer status);

    List<InternshipConfirmationRecord> findByStudentIdAndStatus(Long studentId, Integer status);

    List<InternshipConfirmationRecord> findPendingRecallList(Long studentId, String name, String companyName);

    int recall(Long id, String recallReason);
}
