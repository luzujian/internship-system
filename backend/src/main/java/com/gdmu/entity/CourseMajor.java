package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * 课程与专业的多对多关联实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CourseMajor {
    private Long id;
    private Long courseId; // 课程ID
    private Long majorId; // 专业ID
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime createTime;
    
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private LocalDateTime updateTime;
    
    // 用于前端显示的专业名称
    private transient String majorName;
}