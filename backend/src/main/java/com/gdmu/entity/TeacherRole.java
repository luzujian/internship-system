package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class TeacherRole {
    private Long id;
    private String roleName;
    private String roleCode;
    private String description;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
}
