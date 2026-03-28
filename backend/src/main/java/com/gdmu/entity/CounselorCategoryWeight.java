package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class CounselorCategoryWeight {
    
    private Long id;
    
    private Long counselorId;
    
    private String categoryCode;
    
    private String categoryName;
    
    private Integer weight;
    
    private Integer status;
    
    private Date createTime;
    
    private Date updateTime;
    
    private Integer deleted;
}
