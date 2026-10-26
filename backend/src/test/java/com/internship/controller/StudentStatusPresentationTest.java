package com.internship.controller;

import com.internship.entity.StudentInternshipStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

class StudentStatusPresentationTest {
    @Test
    void studentHomeUsesTheSameOfficialStateAsTheTeacherListAtDateBoundaries() {
        StudentHomeController home = new StudentHomeController();
        for (int code = 2; code <= 6; code++) {
            StudentInternshipStatus row = new StudentInternshipStatus();
            row.setStatus(code);
            row.setInternshipStartTime(new Date(System.currentTimeMillis() + 86400000));
            row.setInternshipEndTime(new Date(System.currentTimeMillis() + 172800000));
            Integer actual = ReflectionTestUtils.invokeMethod(home, "calculateCurrentStatus", row, 1L, false);
            assertEquals(Integer.valueOf(code), actual);
        }
    }

    @Test
    void teacherExportUsesDerivedStatusAndPreservesPendingApplicationContext() {
        StudentInternshipStatus row = new StudentInternshipStatus();
        row.setStatus(0);
        row.setJobSearchStatusText("已录用待确认");
        row.setPendingApplicationTexts(List.of("延期申请待审核"));
        assertEquals("已录用待确认 延期申请待审核",
                ReflectionTestUtils.invokeMethod(new TeacherController(), "exportStatusText", row));
        assertEquals("已录用待确认 延期申请待审核",
                ReflectionTestUtils.invokeMethod(new StudentInternshipStatusController(), "exportStatusText", row));
        row.setStatus(3);
        row.setJobSearchStatusText(null);
        row.setPendingApplicationTexts(List.of());
        assertEquals("实习进行中", ReflectionTestUtils.invokeMethod(new TeacherController(), "exportStatusText", row));
        row.setStatus(6);
        assertEquals("已延期实习", ReflectionTestUtils.invokeMethod(new TeacherController(), "exportStatusText", row));
    }
}
