package com.internship.service;

import com.internship.entity.InternshipConfirmationRecord;
import java.util.List;

public interface InternshipConfirmationRecordService {

    int insert(InternshipConfirmationRecord record);

    int update(InternshipConfirmationRecord record);

    int submitPending(Long id, String remark);

    int resolvePending(Long id, Integer status, String remark);

    InternshipConfirmationRecord findById(Long id);

    InternshipConfirmationRecord findByIdForUpdate(Long id);

    Long lockStudent(Long studentId);

    InternshipConfirmationRecord findActiveByStudentForUpdate(Long studentId);

    InternshipConfirmationRecord findSubmittedPendingByStudentForUpdate(Long studentId);

    List<InternshipConfirmationRecord> findByStudentId(Long studentId);

    List<InternshipConfirmationRecord> findByStudentIdOrderByCreateTimeDesc(Long studentId);

    List<InternshipConfirmationRecord> findByCompanyId(Long companyId);

    List<InternshipConfirmationRecord> findByCompanyIdAndStatus(Long companyId, Integer status);

    List<InternshipConfirmationRecord> findByStudentIdAndStatus(Long studentId, Integer status);

    List<InternshipConfirmationRecord> findPendingRecallList(Long studentId, String name, String companyName);

    int recall(Long id, String recallReason);
}
