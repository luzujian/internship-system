package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 贡献度记录实体类
 */
@Data
public class ContributionData {
    private Long id;
    private Long submissionId;
    private Long studentId;
    private Double contributionRatio = 0.00; // 贡献度比例(0-100)
    private String contributionDesc; // 贡献描述
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
}