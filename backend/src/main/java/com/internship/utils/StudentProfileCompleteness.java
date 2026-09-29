package com.internship.utils;

import com.internship.entity.StudentUser;

import java.util.ArrayList;
import java.util.List;

/**
 * 学生个人资料完整性校验。
 * 岗位申请、自主实习审核提交、考研延迟申请前，
 * 必须已完善：姓名、学号、电话、邮箱，全部齐全才允许进入下一步。
 */
public final class StudentProfileCompleteness {

    private StudentProfileCompleteness() {
    }

    /** 返回缺失的必填资料字段中文名（按 姓名、学号、电话、邮箱 顺序）。 */
    public static List<String> missingRequiredFields(StudentUser student) {
        List<String> missing = new ArrayList<>();
        if (student == null) {
            missing.add("姓名");
            missing.add("学号");
            missing.add("电话");
            missing.add("邮箱");
            return missing;
        }
        if (isBlank(student.getName())) missing.add("姓名");
        if (isBlank(student.getStudentUserId())) missing.add("学号");
        if (isBlank(student.getPhone())) missing.add("电话");
        if (isBlank(student.getEmail())) missing.add("邮箱");
        return missing;
    }

    public static boolean isComplete(StudentUser student) {
        return missingRequiredFields(student).isEmpty();
    }

    /** 统一的拒绝文案，例如：请先完善个人资料（缺少：电话、邮箱），再提交申请。 */
    public static String incompleteMessage(List<String> missing) {
        return "请先完善个人资料（缺少：" + String.join("、", missing) + "），再提交申请";
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
