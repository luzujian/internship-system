package com.internship.entity.dto;

import lombok.Data;

import java.util.List;

@Data
public class TeacherAppraisalListResponse {
    private Long total;
    private List<TeacherAppraisalStudent> rows;
    private Integer pages;
    private Integer current;
    private Integer pageSize;
    private Long responsibleStudentCount;
    private Long submittedCount;
    private Long notSubmittedCount;
    private List<TeacherAppraisalClassOption> classes;
}
