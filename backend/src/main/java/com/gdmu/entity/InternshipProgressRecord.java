package com.gdmu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("internship_progress_record")
public class InternshipProgressRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private String eventType;

    private String eventTitle;

    private String description;

    private String status;

    private Long relatedId;

    private Date eventTime;

    private Date createTime;
}
