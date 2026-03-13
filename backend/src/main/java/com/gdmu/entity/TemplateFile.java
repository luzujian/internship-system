package com.gdmu.entity;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Date;

/**
 * 模板文件实体类
 */
@Data
public class TemplateFile {
    private Long id;
    
    @NotBlank(message = "模板名称不能为空")
    private String name;
    
    private String category;
    
    @NotBlank(message = "文件地址不能为空")
    private String fileUrl;
    
    private String description;
    
    private Integer downloadCount = 0;
    
    private Integer status = 1;
    
    private Date createTime;
    private Date updateTime;
}
