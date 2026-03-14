package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class AdminUser {
    private Long id;
    
    private String username;
    
    private String password;
    
    private String realName;
    
    private String phone;
    
    private String email;
    
    private Integer status;
    
    private String role;
    
    private Integer adminLevel;
    
    private Date createTime;
    
    private Date updateTime;
}
