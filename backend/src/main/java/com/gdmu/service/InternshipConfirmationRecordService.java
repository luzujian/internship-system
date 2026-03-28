package com.gdmu.service;

import com.gdmu.entity.InternshipConfirmationRecord;
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

    int recall(Long id, String recallReason);
}
