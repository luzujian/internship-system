package com.internship.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class CompanyLiaisonBatchRequest {
    @NotNull(message = "对接人列表不能为空")
    private List<Long> teacherIds;
}
