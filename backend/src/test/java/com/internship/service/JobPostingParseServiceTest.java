package com.internship.service;

import com.internship.dto.JobPostingParseResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JobPostingParseServiceTest {

    private JobPostingParseService service;

    @BeforeEach
    void setUp() {
        service = new JobPostingParseService(null, null);
    }

    @Test
    void normalizesSnakeCaseSalaryAndRejectsUnsafeValues() {
        JobPostingParseResult result = parseAndNormalize("""
                {
                  "position_name": " Java开发实习生 ",
                  "salary_unit": "元/月",
                  "salary_min": 12000,
                  "salary_max": 8000,
                  "planned_recruit": 101,
                  "required_majors": ["专业不限", "专业不限"],
                  "internship_environment": "居家办公",
                  "provides_accommodation": 2,
                  "interview_time": "2026-02-30 14:00:00",
                  "confidence": {
                    "position_name": 0.96,
                    "salary_min": 1.2,
                    "unknown_field": 0.99
                  },
                  "warnings": []
                }
                """);

        assertEquals("Java开发实习生", result.getPositionName());
        assertEquals("K/月", result.getSalaryUnit());
        assertEquals(new BigDecimal("8"), result.getSalaryMin());
        assertEquals(new BigDecimal("12"), result.getSalaryMax());
        assertNull(result.getPlannedRecruit());
        assertEquals(java.util.List.of("不限制专业"), result.getRequiredMajors());
        assertNull(result.getInternshipEnvironment());
        assertNull(result.getProvidesAccommodation());
        assertNull(result.getInterviewTime());
        assertEquals(new BigDecimal("0.96"), result.getConfidence().get("positionName"));
        assertEquals(BigDecimal.ZERO, result.getConfidence().get("salaryMin"));
        assertFalse(result.getConfidence().containsKey("unknown_field"));
        assertTrue(result.getWarnings().stream().anyMatch(item -> item.contains("招聘人数")));
        assertTrue(result.getWarnings().stream().anyMatch(item -> item.contains("薪资上下限")));
    }

    @Test
    void repairsCommonJsonFormattingAndParsesChineseInterviewTime() {
        JobPostingParseResult result = parseAndNormalize("""
                ```json
                {
                  “positionName”: “测试实习生”,
                  “interviewTime”: “9月2日下午两点半”,
                  “confidence”: {
                    “positionName”: 0.95,
                    “interviewTime”: 0.91,
                  },
                }
                ```
                """);

        assertEquals("测试实习生", result.getPositionName());
        assertEquals("2026-09-02 14:30:00", result.getInterviewTime());
        assertEquals(new BigDecimal("0.91"), result.getConfidence().get("interviewTime"));
    }

    @Test
    void missingConfidenceIsForcedToZeroInsteadOfAutoFillEligible() {
        JobPostingParseResult result = parseAndNormalize("""
                {
                  "positionName": "数据分析实习生",
                  "description": "负责日报整理",
                  "confidence": {"positionName": 0.88}
                }
                """);

        assertEquals(new BigDecimal("0.88"), result.getConfidence().get("positionName"));
        assertEquals(BigDecimal.ZERO, result.getConfidence().get("description"));
        assertTrue(result.getWarnings().stream().anyMatch(item -> item.contains("description")));
    }

    @Test
    void unknownSalaryUnitIsDiscardedInsteadOfGuessed() {
        JobPostingParseResult result = parseAndNormalize("""
                {
                  "positionName": "产品实习生",
                  "salaryUnit": "万元/年",
                  "salaryMin": 10,
                  "salaryMax": 15,
                  "confidence": {
                    "positionName": 0.95,
                    "salaryUnit": 0.95,
                    "salaryMin": 0.95,
                    "salaryMax": 0.95
                  }
                }
                """);

        assertNull(result.getSalaryUnit());
        assertNull(result.getSalaryMin());
        assertNull(result.getSalaryMax());
        assertTrue(result.getWarnings().stream().anyMatch(item -> item.contains("薪资单位")));
    }

    @Test
    void promptFormatsYearBeforeJobText() {
        String prompt = ReflectionTestUtils.invokeMethod(
                service, "buildUserPrompt", "岗位名称：Java开发实习生");

        assertTrue(prompt.contains("文本没有年份时使用2026年"));
        assertTrue(prompt.contains("岗位名称：Java开发实习生"));
    }

    private JobPostingParseResult parseAndNormalize(String response) {
        JobPostingParseResult result = ReflectionTestUtils.invokeMethod(service, "parseAiResponse", response);
        ReflectionTestUtils.invokeMethod(service, "normalizeResult", result);
        return result;
    }
}
