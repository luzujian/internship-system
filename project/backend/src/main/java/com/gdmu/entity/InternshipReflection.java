package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Date;

@Data
public class InternshipReflection {
    private Long id;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotBlank(message = "学生姓名不能为空")
    @Size(max = 100, message = "学生姓名长度不能超过100个字符")
    private String studentName;

    @NotBlank(message = "学号不能为空")
    @Size(max = 50, message = "学号长度不能超过50个字符")
    private String studentUserId;

    @NotBlank(message = "实习心得内容不能为空")
    private String content;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;

    private String aiKeywords;

    private String aiAnalysis;

    private BigDecimal aiScore;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date aiAnalysisTime;

    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date updateTime;

    private Integer deleted;

    private StudentUser student;
}
