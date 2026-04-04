package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 作业实体类
 */
@Data
public class Assignment {
    private Long id;

    private String title;
    private String description;
    private Long teacherId;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime deadline;
    
    // 课程ID
    private Long courseId;
    
    // 作业类型（如：普通作业、小组作业）
    private String type = "NORMAL";
    
    // 状态（如：DRAFT草稿、PUBLISHED已发布、ENDED已结束）
    private String status = "DRAFT";
    
    // 新增字段：作业要求说明
    private String requirements;
    
    // 新增字段：允许的文件类型（逗号分隔，如：pdf,docx,zip）
    private String allowedFileTypes;
    
    // 新增字段：最大文件大小（MB）
    private Integer maxFileSize = 10;
    
    // 新增字段：是否允许在线编辑
    private Boolean allowOnlineEdit = false;
    
    // 新增字段：是否允许批量提交
    private Boolean allowBatchSubmit = true;
    
    // 新增字段：是否需要提交贡献度
    private Boolean requireContribution = true;
    
    // 新增字段：是否允许学生查看成绩
    private Boolean allowScoreView = true;
    
    // 新增字段：是否允许学生查看评语
    private Boolean allowCommentView = true;
    
    // 新增字段：是否允许学生查看其他小组提交
    private Boolean allowPeerView = false;
    
    // 新增字段：评分标准说明
    private String gradingCriteria;
    
    // 新增字段：总分值
    private Double totalScore = 100.0;
    
    // 新增字段：是否启用智能评分
    private Boolean enableIntelligentScoring = true;
    
    // 用于存储课程名称的额外字段（不映射到数据库）
    private String courseName;
    
    // 用于存储教师名称的额外字段（不映射到数据库）
    private String teacherName;
    
    // 用于存储额外信息的字段（如统计信息，不映射到数据库）
    private Map<String, Object> extraInfo;
    
    public Map<String, Object> getExtraInfo() {
        return extraInfo;
    }
    
    public void setExtraInfo(Map<String, Object> extraInfo) {
        this.extraInfo = extraInfo;
    }
}