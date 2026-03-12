package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class CategoryWeight {
    
    private Long id;
    
    private String categoryCode;
    
    private String categoryName;
    
    private Integer weight;
    
    private Integer status;
    
    private Date createTime;
    
    private Date updateTime;
    
    private String creator;
    
    private String updater;
    
    private Integer deleted;
}
