package com.internship.utils;

import org.junit.jupiter.api.Test;

import com.internship.entity.StudentUser;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentProfileCompletenessTest {

    private StudentUser completeStudent() {
        StudentUser student = new StudentUser();
        student.setName("张三");
        student.setStudentUserId("20230001");
        student.setPhone("13800138000");
        student.setEmail("zhangsan@example.com");
        return student;
    }

    @Test
    void completeProfileHasNoMissingFields() {
        assertTrue(StudentProfileCompleteness.missingRequiredFields(completeStudent()).isEmpty());
        assertTrue(StudentProfileCompleteness.isComplete(completeStudent()));
    }

    @Test
    void blankAndNullFieldsAreReportedInFixedOrder() {
        StudentUser student = completeStudent();
        student.setPhone("   ");
        student.setEmail(null);
        List<String> missing = StudentProfileCompleteness.missingRequiredFields(student);
        assertEquals(List.of("电话", "邮箱"), missing);
        assertFalse(StudentProfileCompleteness.isComplete(student));
    }

    @Test
    void nullStudentReportsAllRequiredFields() {
        assertEquals(List.of("姓名", "学号", "电话", "邮箱"),
                StudentProfileCompleteness.missingRequiredFields(null));
    }

    @Test
    void messageListsMissingFields() {
        assertEquals("请先完善个人资料（缺少：学号、邮箱），再提交申请",
                StudentProfileCompleteness.incompleteMessage(List.of("学号", "邮箱")));
    }
}
