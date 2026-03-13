package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

/**
 * 学生材料归档实体类
 */
@Data
public class StudentArchive {
    private Long id;
    private Long studentId;
    private String fileType;
    private String fileName;
    private String fileUrl;
    private Date uploadTime;
    private String remark;
    private Integer status; // 0待审核,1已通过,2已拒绝
    
    // 关联字段
    private String studentName;
    private String studentUserId;
    private String majorName;
    private String className;
}
