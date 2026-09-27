package com.internship.entity.dto;

import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class TeacherAppraisalDetail {
    private Long studentId;
    private String studentNumber;
    private String studentName;
    private Long classId;
    private String className;
    private String majorName;
    private Long submissionId;
    private String status;
    private Date submittedAt;
    private List<TeacherAppraisalFileInfo> files;
}
