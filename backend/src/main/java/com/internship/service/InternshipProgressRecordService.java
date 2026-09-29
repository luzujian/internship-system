package com.internship.service;

import com.internship.entity.InternshipProgressRecord;
import com.internship.entity.dto.RecentActivityPageDTO;
import java.util.List;

public interface InternshipProgressRecordService {
    void saveRecord(InternshipProgressRecord record);
    List<InternshipProgressRecord> getByStudentId(Long studentId);
    RecentActivityPageDTO getRecentActivities(Long studentId, int limit, String cursor);
    default RecentActivityPageDTO getRecentActivities(Long studentId, int limit) { return getRecentActivities(studentId, limit, null); }
}
