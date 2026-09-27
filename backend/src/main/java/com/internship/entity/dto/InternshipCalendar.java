package com.internship.entity.dto;

import lombok.Data;

/**
 * 全校统一实习日历。version 是更新时必须原样回传的并发令牌。
 */
@Data
public class InternshipCalendar {
    private String startDate;
    private String endDate;
    private Integer reportCycle;
    private String appraisalDeadline;
    private String version;
}
