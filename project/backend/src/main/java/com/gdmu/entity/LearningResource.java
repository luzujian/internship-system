package com.gdmu.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 学习资源实体类
 */
@Data
public class LearningResource {
    private Long id;
    private String title;
    private String description;
    private String fileUrl;
    private String fileType;
    private Long uploaderId;
    private String uploaderRole; // STUDENT, TEACHER, ADMIN
    private String status; // PENDING, APPROVED, REJECTED
    private Long reviewerId;
    private String aiSummary;
    private String embeddingVector;
    private LocalDateTime createdTime;
}