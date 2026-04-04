package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

@Data
public class ResourceDocument {
    private Long id;
    
    @NotBlank(message = "文档标题不能为空")
    @Size(min = 2, max = 255, message = "文档标题长度必须在2-255个字符之间")
    private String title;
    
    private String description;
    
    private String fileUrl;
    
    private String fileName;
    
    private String fileType;
    
    private Long fileSize;
    
    private Long publisherId;
    
    @NotBlank(message = "发布人不能为空")
    @Size(min = 2, max = 50, message = "发布人长度必须在2-50个字符之间")
    private String publisher;
    
    private String publisherRole;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date publishTime;
    
    private String targetType;
    
    private String targetValue;
    
    private String status;
    
    private Integer downloadCount;
    
    private Integer viewCount;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;
}
