package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class TeacherPermission {
    private Long id;
    private String permissionName;
    private String permissionCode;
    private String permissionType;
    private Long parentId;
    private String path;
    private String icon;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
    private Integer deleted;
}
