package com.gdmu.service.impl;

import com.gdmu.entity.InternshipConfirmationRecord;
import com.gdmu.mapper.InternshipConfirmationRecordMapper;
import com.gdmu.service.InternshipConfirmationRecordService;
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
    public InternshipConfirmationRecord findById(Long id) {
        return confirmationRecordMapper.findById(id);
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
    public int recall(Long id, String recallReason) {
        InternshipConfirmationRecord record = findById(id);
        if (record == null) return 0;
        record.setRecallStatus(1);  // 撤回申请中
        record.setRecallReason(recallReason);
        record.setRecallApplyTime(java.time.LocalDateTime.now());
        return update(record);
    }
}
