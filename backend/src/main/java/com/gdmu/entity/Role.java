package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class Role {
    private Long id;
    private String roleCode;
    private String roleName;
    private String roleDesc;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
