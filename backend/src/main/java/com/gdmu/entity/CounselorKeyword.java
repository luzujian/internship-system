package com.gdmu.entity;

import lombok.Data;

import java.util.Date;

@Data
public class CounselorKeyword {

    private Long id;
    
    private Long counselorId;
    
    private String keyword;
    
    private Integer status;
    
    private Date createTime;
    
    private Date updateTime;
}
