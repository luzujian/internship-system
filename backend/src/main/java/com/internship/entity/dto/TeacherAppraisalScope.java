package com.internship.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/** 当前登录教师可查看鉴定表的后端数据范围，不接收任何前端传入的教师 ID。 */
@Data
@AllArgsConstructor
public class TeacherAppraisalScope {
    private Long teacherId;
    private String teacherType;
    private Long departmentId;
    private Long divisionId;
}
