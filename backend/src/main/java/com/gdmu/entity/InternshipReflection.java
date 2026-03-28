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

    // 关联的实习状态ID
    private Long internshipStatusId;

    // 第几期（如第1期、第2期）
    private Integer periodNumber;

    // 任务类型: 'auto'(自动生成) / 'manual'(手动补充)
    private String taskType;

    // 提交截止日期
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date deadline;

    // 辅导员ID（用于AI分析时获取评分规则）
    private Long counselorId;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date submitTime;

    private String aiKeywords;

    private String aiAnalysis;

    private String aiImprovements;

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

    // 非持久化字段：来自InternshipEvaluation的成绩（用于API返回）
    private Integer totalScore;

    // 非持久化字段：来自InternshipEvaluation的评语（用于API返回）
    private String teacherComment;
}
