package com.gdmu.entity;

import lombok.Data;
import java.util.Date;

@Data
public class CompanyNotification {
    private Long id;
    private Long companyId;
    private String notificationType;
    private String title;
    private String content;
    private String priority;
    private Long positionId;
    private Boolean isRead;
    private Date createTime;
    private Date readTime;
}