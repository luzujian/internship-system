package com.internship.entity.dto;

import lombok.Data;

import java.util.Date;

/** Internal projection used by the paged recent-activity query. */
@Data
public class RecentActivityRecordRow {
    private Long id;
    private Long studentId;
    private String eventType;
    private String eventTitle;
    private String description;
    private String status;
    private Long relatedId;
    private Date occurredAt;
    private Integer stageNo;
    private Boolean targetExists;
}
