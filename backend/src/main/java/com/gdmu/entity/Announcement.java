package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

/**
 * 通知公告实体类
 */
@Data
public class Announcement {
    private Long id;

    @NotBlank(message = "公告标题不能为空")
    @Size(min = 2, max = 255, message = "公告标题长度必须在 2-255 个字符之间")
    private String title;

    @NotBlank(message = "公告内容不能为空")
    private String content;

    @NotBlank(message = "发布人不能为空")
    @Size(min = 2, max = 50, message = "发布人长度必须在 2-50 个字符之间")
    private String publisher;

    private String publisherRole; // 发布人身份：ADMIN-管理员，COLLEGE-学院教师，DEPARTMENT-系室教师，COUNSELOR-辅导员

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date publishTime;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date validFrom;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Date validTo;

    private String targetType; // 目标类型：JSON 数组，如 ["TEACHER","TEACHER_TYPE"]。支持：STUDENT-全体学生，TEACHER-全体教师，TEACHER_TYPE-特定教师类别，MAJOR-特定专业学生，COMPANY-企业用户
    private String targetValue; // 目标值：JSON 对象，如 {"teacherTypes":["COLLEGE","DEPARTMENT"],"majorIds":["1","2"]}。当 targetType 为 TEACHER_TYPE 时存教师类型（COLLEGE/DEPARTMENT/COUNSELOR），为 MAJOR 时存专业 ID

    private String priority; // 优先级：normal-普通，important-重要（重要公告置顶显示）

    private String attachments; // 附件列表，JSON 格式

    private String status; // 公告状态：DRAFT-草稿，PUBLISHED-已发布，EXPIRED-已过期

    private Date createTime;

    private Date updateTime;

    private Integer readCount;

    // 非数据库字段，仅用于前端显示当前用户是否已读
    private Boolean isRead;

    // 为前端提供 publishDate 字段，映射到 publishTime
    public Date getPublishDate() {
        return this.publishTime;
    }

    public void setPublishDate(Date publishDate) {
        this.publishTime = publishDate;
    }

    // 为前端提供 expireDate 字段，映射到 validTo
    public Date getExpireDate() {
        return this.validTo;
    }

    public void setExpireDate(Date expireDate) {
        this.validTo = expireDate;
    }
}
