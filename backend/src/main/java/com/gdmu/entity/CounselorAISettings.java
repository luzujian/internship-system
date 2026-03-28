package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class CounselorAISettings {
    private Long id;
    private Long counselorId;
    private Integer enableAiScoring;
    private String aiModelCode;
    private Date createTime;
    private Date updateTime;
    
    private TeacherUser counselor;
}
