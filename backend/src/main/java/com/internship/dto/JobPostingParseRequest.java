package com.internship.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class JobPostingParseRequest {
    @NotBlank(message = "请先粘贴岗位内容")
    @Size(max = 6000, message = "岗位内容不能超过 6000 字")
    private String text;
}
