package com.internship.entity;

import lombok.Data;

import java.util.Date;

/** 全校统一的两阶段实习设置。 */
@Data
public class InternshipStageSetting {
    private Long id;
    private Integer stageNo;
    private String stageName;
    private String selectionStartDate;
    private String selectionEndDate;
    private String changeDeadline;
    private String internshipStartDate;
    private String internshipEndDate;
    private String confirmationDeadline;
    private Integer reportCycle;
    private String reportDeadline;
    private String appraisalDeadline;
    private Integer enabled = 1;
    private Integer version = 0;
    private Long updaterId;
    private String updaterName;
    private Date createTime;
    private Date updateTime;
}
