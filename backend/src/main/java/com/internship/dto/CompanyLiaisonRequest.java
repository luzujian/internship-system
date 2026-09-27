package com.internship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CompanyLiaisonRequest {
    @NotNull(message = "教师不能为空")
    private Long teacherId;
}
