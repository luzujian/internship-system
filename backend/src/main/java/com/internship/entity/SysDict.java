package com.internship.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class SysDict {
    private Long id;
    private String dictType;
    private String dictCode;
    private String dictLabel;
    private String dictValue;
    private Integer sortOrder;
    private String remark;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
