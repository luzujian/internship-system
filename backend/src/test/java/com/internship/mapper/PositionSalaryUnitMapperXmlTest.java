package com.internship.mapper;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PositionSalaryUnitMapperXmlTest {

    @Test
    void persistsUnitAndClearsAmountsForNegotiableSalary() throws Exception {
        String xml = Files.readString(Path.of(
                "src/main/resources/com/internship/mapper/PositionMapper.xml"));

        assertTrue(xml.contains("<result property=\"salaryUnit\" column=\"salary_unit\" />"));
        assertTrue(xml.contains("#{salaryMin}, #{salaryMax}, #{salaryUnit}"));
        assertTrue(xml.contains("<when test=\"salaryUnit == '面议'\">"));
        assertTrue(xml.contains("salary_min = NULL"));
        assertTrue(xml.contains("salary_max = NULL"));
        assertTrue(xml.contains("p.salary_unit AS salaryUnit"));
    }
}
