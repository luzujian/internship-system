package com.internship.entity;

import lombok.Data;
import java.util.Date;

@Data
public class ResourceViewRecord {
    private Long id;
    private Long resourceId;
    private String userId;
    private String userType;
    private String actionType; // view / download
    private Date createTime;
}
