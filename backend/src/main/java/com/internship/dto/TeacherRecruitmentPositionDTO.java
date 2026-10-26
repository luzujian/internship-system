package com.internship.dto;

import com.internship.entity.Position;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TeacherRecruitmentPositionDTO extends Position {
    private String companyName;
    private Integer companyPositionCount;
    private String categoryName;
}
