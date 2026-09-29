package com.internship.utils;

import java.math.BigDecimal;

/**
 * 岗位薪资展示格式化工具，统一企业端、学生端和 AI 推荐的薪资语义。
 */
public final class SalaryFormatter {
    public static final String UNIT_MONTH_K = "K/月";
    public static final String UNIT_DAY = "元/天";
    public static final String UNIT_HOUR = "元/小时";
    public static final String UNIT_NEGOTIABLE = "面议";

    private SalaryFormatter() {
    }

    public static boolean isSupportedInputUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return true;
        }
        String normalized = unit.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("k/月") || normalized.equals("k") || normalized.equals("元/月")
                || normalized.equals("元/天") || normalized.equals("元/日")
                || normalized.equals("元/小时") || normalized.equals("元/时")
                || normalized.equals(UNIT_NEGOTIABLE);
    }

    public static String normalizeUnit(String unit) {
        if (unit == null || unit.isBlank()) {
            return UNIT_MONTH_K;
        }
        String normalized = unit.trim();
        return switch (normalized.toLowerCase(java.util.Locale.ROOT)) {
            case "k/月", "k", "元/月" -> UNIT_MONTH_K;
            case "元/天", "元/日" -> UNIT_DAY;
            case "元/小时", "元/时" -> UNIT_HOUR;
            case "面议" -> UNIT_NEGOTIABLE;
            default -> UNIT_MONTH_K;
        };
    }

    public static String format(BigDecimal salaryMin, BigDecimal salaryMax, String salaryUnit) {
        String unit = normalizeUnit(salaryUnit);
        BigDecimal min = positiveOrNull(salaryMin);
        BigDecimal max = positiveOrNull(salaryMax);

        if (UNIT_NEGOTIABLE.equals(unit) || (min == null && max == null)) {
            return UNIT_NEGOTIABLE;
        }

        String suffix = unit;
        if (min != null && max != null) {
            return toPlainString(min) + "-" + toPlainString(max) + suffix;
        }
        if (min != null) {
            return toPlainString(min) + suffix + "起";
        }
        return "最高" + toPlainString(max) + suffix;
    }

    private static BigDecimal positiveOrNull(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0 ? value : null;
    }

    private static String toPlainString(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
