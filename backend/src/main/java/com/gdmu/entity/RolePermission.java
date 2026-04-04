package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class RolePermission {
    private Long id;
    private String roleCode;
    private String permissionCode;
    private Date createTime;
}
