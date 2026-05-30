package com.internship.service;

import com.internship.entity.dto.TeacherDashboardStatsDTO;

public interface TeacherDashboardService {

    TeacherDashboardStatsDTO getDashboardStats(Long userId, String teacherType, String startDate, String endDate);

    TeacherDashboardStatsDTO getCounselorDashboardStats(Long counselorId, String startDate, String endDate);
}
