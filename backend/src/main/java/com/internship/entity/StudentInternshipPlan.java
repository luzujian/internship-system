package com.internship.entity;

import lombok.Data;
import java.util.Date;

/**
 * 学生实习计划快照（一生一单）。
 * 阶段与事项每次查询时按真实业务状态重算，已完成不重复安排；
 * 本表只存快照供 continuity（重登继续）与版本比对，不做后台推进。
 */
@Data
public class StudentInternshipPlan {
    private Long id;
    private Long studentId;
    /** 当前阶段码：PREPARE/SEARCH/OFFER/CONFIRM/ONBOARD/IN_PROGRESS/FINISHED/INTERRUPTED/DELAYED */
    private String currentStage;
    /** 事项快照 JSON：[{key,title,state,detail}]，state=DONE/TODO/WAITING */
    private String items;
    private Integer version;
    private Date createTime;
    private Date updateTime;
}
