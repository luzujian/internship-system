package com.gdmu.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class Resource {
    private Long id;
    private String name;
    private String type;
    private String description;
    private String fileUrl;
    private Long fileSize;
    private Integer downloadCount;
    private Long uploaderId;
    private String uploaderName;
    private String uploaderRole;
    private String content;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
