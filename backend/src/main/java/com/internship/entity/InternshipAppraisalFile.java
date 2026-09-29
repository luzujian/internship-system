package com.internship.entity;

import lombok.Data;

import java.util.Date;

/** 最终鉴定表提交中的单个文件。 */
@Data
public class InternshipAppraisalFile {
    private Long id;
    private Long submissionId;
    private Long stageAssignmentId;
    private Long companyId;
    private String companyName;
    private Long fileUploadId;
    private String fileName;
    private String filePath;
    private Long fileSize;
    private String fileType;
    private Date uploadTime;
}
