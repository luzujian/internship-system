package com.internship.entity.dto;

import lombok.Data;

import java.util.Date;

/** 可安全返回给辅导员端的鉴定表文件元数据，不包含 OSS 路径。 */
@Data
public class TeacherAppraisalFileInfo {
    private Long id;
    private Long companyId;
    private String companyName;
    private String fileName;
    private Long fileSize;
    private String fileType;
    private Date uploadTime;
}
