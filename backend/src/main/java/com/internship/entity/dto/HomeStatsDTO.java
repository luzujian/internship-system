package com.internship.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 首页统计数据DTO
 */
@Data
public class HomeStatsDTO {
    private Double internshipRate;
    private String divisionName;
    private String departmentName;
    private Integer unreadCount;
    private Integer pendingApprovalCount;
    private Integer companyCount;
    private Integer pendingReflectionCount;
    private List<InternshipStatusDTO> statusData;
    private List<AnnouncementWithReadStatusDTO> announcements;
}
