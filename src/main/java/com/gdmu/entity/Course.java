package com.gdmu.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程实体类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Course {
    private Long id;
    private String name; // 课程名称
    private String grade; // 年级
    private Long teacherId; // 授课教师ID
    private Long teacherUsersId; // 授课教师ID（与数据库字段对应）
    private String teacherName; // 授课教师名称（用于前端展示）
    private Long majorId; // 所属专业ID（兼容旧版）
    private List<Long> majorIds; // 多个专业ID（新版）
    private List<String> majorNames; // 多个专业名称（用于前端展示）
    private String majorName; // 专业名称字符串（临时字段，用于数据库查询结果映射）
    
    private LocalDateTime beginDate; //开课时间
    
    private LocalDateTime endDate; //结课时间
    
    private LocalDateTime createTime;
    
    private LocalDateTime updateTime;
    
    private Integer studentCount = 0; // 学生人数，默认0
    
    // 以下字段不在数据库中，但前端需要
    private String schedule = "周一 1-2节"; // 上课时间，默认值
    private String location = "教学楼101"; // 上课地点，默认值
    private Integer credit = 3; // 学分，默认值
    private Double score = null; // 成绩，默认为null
    
    // 添加一个方法，用于获取格式化的专业名称列表
    public String getFormattedMajorNames() {
        if (majorNames != null && !majorNames.isEmpty()) {
            return String.join("、", majorNames);
        }
        return majorName != null ? majorName : "";
    }
}