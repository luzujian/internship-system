package com.internship.entity.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class AppraisalSubmitRequest {
    private List<RatingInput> ratings;

    @Data
    public static class RatingInput {
        private Long companyId;
        private BigDecimal stars;
    }
}
