package com.internship.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class TeacherMacroStatisticsDTO {
    private Long withoutOfferStudentCount;
    private Long totalRemainingQuota;
}
