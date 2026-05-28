package com.gdmu.service;

import com.gdmu.entity.dto.TeacherDashboardStatsDTO;

public interface TeacherDashboardService {

    TeacherDashboardStatsDTO getDashboardStats(Long userId, String teacherType, String startDate, String endDate);

    TeacherDashboardStatsDTO getCounselorDashboardStats(Long counselorId, String startDate, String endDate);
}
