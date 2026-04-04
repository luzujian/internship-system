package com.gdmu.entity.dto;

import lombok.Data;

/**
 * 带有阅读状态的公告DTO
 */
@Data
public class AnnouncementWithReadStatusDTO {
    private Long id;
    private String title;
    private String content;
    private String time;
    private Boolean isRead;
}
