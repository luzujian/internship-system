package com.internship.service;

import com.internship.entity.InternshipProgressRecord;
import java.util.List;

public interface InternshipProgressRecordService {
    void saveRecord(InternshipProgressRecord record);
    List<InternshipProgressRecord> getByStudentId(Long studentId);
    void updateStatusByRelatedId(Long relatedId, String eventType, String status);
}
