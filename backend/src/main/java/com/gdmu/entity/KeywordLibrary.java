package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class KeywordLibrary {
    
    private Long id;
    
    private Date createTime;
    
    private Date updateTime;
    
    private String creator;
    
    private String updater;
    
    private Integer deleted;
    
    private String keyword;
    
    private String category;
    
    private String description;
    
    private Integer weight;
    
    private Integer status;
    
    private String usageType;
    
    private String relatedTags;
}
