package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Permission {
    private Long id;
    private String permissionCode;
    private String permissionName;
    private String permissionDesc;
    private String module;
    private Date createTime;
    private Date updateTime;
}
