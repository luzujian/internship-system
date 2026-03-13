package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 登录日志实体类
 */
@Data
public class LoginLog {
    private Long id; // ID，自增主键
    private String userId; // 用户ID
    private String userType; // 用户类型
    private String userName; // 用户名
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date loginTime; // 登录时间，默认当前时间戳
    
    private String ipAddress; // IP地址
    private String deviceInfo; // 设备信息
    private String loginStatus; // 登录状态，默认SUCCESS
}