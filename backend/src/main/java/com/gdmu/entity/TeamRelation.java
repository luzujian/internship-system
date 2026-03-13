package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 小组关系实体类
 */
@Data
public class TeamRelation {
    private Long id;
    private Long groupId;
    private Long studentId;
    private String role = "MEMBER"; // LEADER组长, MEMBER成员
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime joinTime;
    
    private String status = "ACTIVE"; // ACTIVE活跃, INACTIVE非活跃
}