package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Date;

/**
 * 通知公告实体类
 */
public class Announcement {
    private Long id;
    
    @NotBlank(message = "公告标题不能为空")
    @Size(min = 2, max = 255, message = "公告标题长度必须在2-255个字符之间")
    private String title;
    
    @NotBlank(message = "公告内容不能为空")
    private String content;
    
    @NotBlank(message = "发布人不能为空")
    @Size(min = 2, max = 50, message = "发布人长度必须在2-50个字符之间")
    private String publisher;
    
    private String publisherRole; // 发布人身份：ADMIN-管理员，COLLEGE-学院教师，DEPARTMENT-系室教师，COUNSELOR-辅导员
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date publishTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date validFrom;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date validTo;
    
    private String targetType; // 目标类型：ALL-全体师生，STUDENT-全体学生，TEACHER-全体教师，TEACHER_TYPE-特定教师类别，MAJOR-特定专业学生
    
    private String targetValue; // 目标值：当target_type为TEACHER_TYPE时存教师类型（COLLEGE/DEPARTMENT/COUNSELOR），为MAJOR时存专业ID
    
    private String priority; // 优先级：normal-普通，important-重要（重要公告置顶显示）
    
    private String attachments; // 附件列表，JSON格式
    
    private String status; // 公告状态：DRAFT-草稿，PUBLISHED-已发布，EXPIRED-已过期
    
    private Date createTime;
    
    private Date updateTime;
    
    private Integer readCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }

    public String getPublisherRole() {
        return publisherRole;
    }

    public void setPublisherRole(String publisherRole) {
        this.publisherRole = publisherRole;
    }

    public Date getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(Date publishTime) {
        this.publishTime = publishTime;
    }

    public Date getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(Date validFrom) {
        this.validFrom = validFrom;
    }

    public Date getValidTo() {
        return validTo;
    }

    public void setValidTo(Date validTo) {
        this.validTo = validTo;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetValue() {
        return targetValue;
    }

    public void setTargetValue(String targetValue) {
        this.targetValue = targetValue;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getAttachments() {
        return attachments;
    }

    public void setAttachments(String attachments) {
        this.attachments = attachments;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    public Integer getReadCount() {
        return readCount;
    }

    public void setReadCount(Integer readCount) {
        this.readCount = readCount;
    }
    
    // 为前端提供publishDate字段，映射到publishTime
    public Date getPublishDate() {
        return this.publishTime;
    }
    
    public void setPublishDate(Date publishDate) {
        this.publishTime = publishDate;
    }
    
    // 为前端提供expireDate字段，映射到validTo
    public Date getExpireDate() {
        return this.validTo;
    }
    
    public void setExpireDate(Date expireDate) {
        this.validTo = expireDate;
    }
}