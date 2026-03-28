package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ClassCounselorRelation {
    private Long id;
    private Long classId;
    private String className;
    private Long counselorId;
    private String counselorName;
    private Date createTime;
    private Date updateTime;
}
