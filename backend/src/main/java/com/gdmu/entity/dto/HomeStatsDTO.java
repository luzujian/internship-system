package com.gdmu.entity.dto;

import lombok.Data;

import java.util.List;

/**
 * 首页统计数据DTO
 */
@Data
public class HomeStatsDTO {
    private Double internshipRate;
    private Integer unreadCount;
    private Integer pendingApprovalCount;
    private Integer companyCount;
    private List<InternshipStatusDTO> statusData;
    private List<AnnouncementWithReadStatusDTO> announcements;
}
