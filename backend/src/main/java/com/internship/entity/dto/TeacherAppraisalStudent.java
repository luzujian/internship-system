package com.internship.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;

@Data
public class TeacherAppraisalStudent {
    private Long studentId;
    private String studentNumber;
    private String studentName;
    private Long classId;
    private String className;
    private String majorName;
    private String submissionStatus;
    private Date submittedAt;
    private Integer fileCount;
    @JsonIgnore
    private Long submissionId;
}
