package com.internship.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SalaryFormatterTest {

    @Test
    void formatDifferentSalaryUnits() {
        assertEquals("3-5K/月", SalaryFormatter.format(new BigDecimal("3"), new BigDecimal("5"), "K/月"));
        assertEquals("150-200元/天", SalaryFormatter.format(new BigDecimal("150"), new BigDecimal("200"), "元/天"));
        assertEquals("20-30元/小时", SalaryFormatter.format(new BigDecimal("20"), new BigDecimal("30"), "元/小时"));
    }

    @Test
    void formatPartialSalaryRanges() {
        assertEquals("5K/月起", SalaryFormatter.format(new BigDecimal("5"), null, "K/月"));
        assertEquals("最高8K/月", SalaryFormatter.format(null, new BigDecimal("8"), "K/月"));
        assertEquals("150元/天起", SalaryFormatter.format(new BigDecimal("150"), null, "元/天"));
    }

    @Test
    void formatNegotiableAndInvalidValues() {
        assertEquals("面议", SalaryFormatter.format(null, null, "K/月"));
        assertEquals("面议", SalaryFormatter.format(new BigDecimal("0"), new BigDecimal("0"), "K/月"));
        assertEquals("面议", SalaryFormatter.format(new BigDecimal("3"), new BigDecimal("5"), "面议"));
    }

    @Test
    void normalizeUnknownUnitToMonthlyK() {
        assertEquals("K/月", SalaryFormatter.normalizeUnit(null));
        assertEquals("K/月", SalaryFormatter.normalizeUnit(" "));
        assertEquals("K/月", SalaryFormatter.normalizeUnit("年薪"));
        assertEquals("K/月", SalaryFormatter.normalizeUnit("元/月"));
        assertEquals("元/天", SalaryFormatter.normalizeUnit("元/日"));
        assertEquals("元/小时", SalaryFormatter.normalizeUnit("元/时"));
        assertEquals("元/天", SalaryFormatter.normalizeUnit("元/天"));
    }
}
