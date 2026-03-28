package com.gdmu.service;

import com.gdmu.entity.InternshipProgressRecord;
import java.util.List;

public interface InternshipProgressRecordService {
    void saveRecord(InternshipProgressRecord record);
    List<InternshipProgressRecord> getByStudentId(Long studentId);
    void updateStatusByRelatedId(Long relatedId, String eventType, String status);
}
