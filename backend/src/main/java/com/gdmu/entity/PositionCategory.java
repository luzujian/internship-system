package com.gdmu.entity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.Date;

@Data
public class PositionCategory {
    private Long id;

    @NotBlank(message = "类别名称不能为空")
    @Size(min = 2, max = 50, message = "类别名称长度必须在2-50个字符之间")
    private String name;

    private String description;

    private Date createTime;

    private Date updateTime;

    private Integer positionCount = 0;
}
