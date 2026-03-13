package com.gdmu.dto;

import com.gdmu.entity.ScoringRule;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ScoringRuleBatchDTO {
    
    @NotNull(message = "分类不能为空")
    private String category;
    
    @NotEmpty(message = "评分规则列表不能为空")
    private List<ScoringRule> rules;
}
