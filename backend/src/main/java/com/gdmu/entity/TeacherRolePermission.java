package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class TeacherRolePermission {
    private Long id;
    private Long roleId;
    private Long permissionId;
    private Integer sortOrder;
    private Date createTime;
    private Integer deleted;
}
