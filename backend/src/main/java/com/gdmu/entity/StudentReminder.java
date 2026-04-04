package com.gdmu.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("student_reminder")
public class StudentReminder {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long teacherId;

    private String content;

    @TableField("is_confirmed")
    private Integer isConfirmed;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime confirmTime;
}