package com.internship.entity;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionSalaryValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void rejectsUnsupportedUnitAndReversedRange() {
        Position position = new Position();
        position.setPositionName("测试岗位");
        position.setSalaryUnit("万元/年");
        position.setSalaryMin(new BigDecimal("12"));
        position.setSalaryMax(new BigDecimal("8"));

        var messages = validator.validate(position).stream()
                .map(violation -> violation.getMessage())
                .toList();

        assertTrue(messages.contains("薪资单位不合法"));
        assertTrue(messages.contains("最低薪资不能高于最高薪资"));
    }
}
