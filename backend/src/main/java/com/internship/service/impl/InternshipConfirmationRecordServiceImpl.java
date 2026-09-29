package com.internship.service.impl;

import com.internship.entity.InternshipConfirmationRecord;
import com.internship.mapper.InternshipConfirmationRecordMapper;
import com.internship.service.InternshipConfirmationRecordService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class InternshipConfirmationRecordServiceImpl implements InternshipConfirmationRecordService {

    @Autowired
    private InternshipConfirmationRecordMapper confirmationRecordMapper;

    @Override
    public int insert(InternshipConfirmationRecord record) {
        return confirmationRecordMapper.insert(record);
    }

    @Override
    public int update(InternshipConfirmationRecord record) {
        return confirmationRecordMapper.update(record);
    }

    @Override
    public int submitPending(Long id, String remark) {
        return confirmationRecordMapper.submitPending(id, remark);
    }

    @Override
    public int resolvePending(Long id, Integer status, String remark) {
        return confirmationRecordMapper.resolvePending(id, status, remark);
    }

    @Override
    public InternshipConfirmationRecord findById(Long id) {
        return confirmationRecordMapper.findById(id);
    }

    @Override
    public InternshipConfirmationRecord findByIdForUpdate(Long id) {
        return confirmationRecordMapper.findByIdForUpdate(id);
    }

    @Override
    public Long lockStudent(Long studentId) {
        return confirmationRecordMapper.lockStudent(studentId);
    }

    @Override
    public InternshipConfirmationRecord findActiveByStudentForUpdate(Long studentId) {
        return confirmationRecordMapper.findActiveByStudentForUpdate(studentId);
    }

    @Override
    public InternshipConfirmationRecord findSubmittedPendingByStudentForUpdate(Long studentId) {
        return confirmationRecordMapper.findSubmittedPendingByStudentForUpdate(studentId);
    }

    @Override
    public List<InternshipConfirmationRecord> findByStudentId(Long studentId) {
        return confirmationRecordMapper.findByStudentId(studentId);
    }

    @Override
    public List<InternshipConfirmationRecord> findByStudentIdOrderByCreateTimeDesc(Long studentId) {
        return confirmationRecordMapper.findByStudentIdOrderByCreateTimeDesc(studentId);
    }

    @Override
    public List<InternshipConfirmationRecord> findByCompanyId(Long companyId) {
        return confirmationRecordMapper.findByCompanyId(companyId);
    }

    @Override
    public List<InternshipConfirmationRecord> findByCompanyIdAndStatus(Long companyId, Integer status) {
        return confirmationRecordMapper.findByCompanyIdAndStatus(companyId, status);
    }

    @Override
    public List<InternshipConfirmationRecord> findByStudentIdAndStatus(Long studentId, Integer status) {
        return confirmationRecordMapper.findByStudentIdAndStatus(studentId, status);
    }

    @Override
    public List<InternshipConfirmationRecord> findPendingRecallList(Long studentId, String name, String companyName) {
        return confirmationRecordMapper.findPendingRecallList(studentId, name, companyName);
    }

    @Override
    public int recall(Long id, String recallReason) {
        return confirmationRecordMapper.recallPending(id, recallReason);
    }
}
