package com.gdmu.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

/**
 * 文件上传记录实体类
 */
@Data
public class FileUpload {
    private Long id;
    
    @NotBlank(message = "文件名不能为空")
    @Size(min = 1, max = 255, message = "文件名长度必须在1-255个字符之间")
    private String fileName;
    
    @NotBlank(message = "文件路径不能为空")
    @Size(min = 1, max = 255, message = "文件路径长度必须在1-255个字符之间")
    private String filePath;
    
    @Min(value = 0, message = "文件大小不能为负数")
    private Long fileSize;
    
    @NotBlank(message = "文件类型不能为空")
    @Size(min = 1, max = 50, message = "文件类型长度必须在1-50个字符之间")
    private String fileType;
    
    @Min(value = 0, message = "上传人ID不能为负数")
    private Long uploaderId;
    
    @NotBlank(message = "上传人类型不能为空")
    @Size(min = 1, max = 20, message = "上传人类型长度必须在1-20个字符之间")
    private String uploaderType; // student/teacher/company
    
    private Date uploadTime;
    
    @NotBlank(message = "业务类型不能为空")
    @Size(min = 1, max = 50, message = "业务类型长度必须在1-50个字符之间")
    private String businessType; // 实习申请/家长同意书等
}