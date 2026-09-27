package com.internship.service;

import com.internship.entity.TeacherUser;
import com.internship.entity.StudentUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.TeacherAutomationScopeMapper;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Set;

@Service
public class TeacherAutomationScopeService {
    private final TeacherAutomationScopeMapper mapper;
    private final TeacherPermissionService permissions;
    public TeacherAutomationScopeService(TeacherAutomationScopeMapper mapper, TeacherPermissionService permissions) { this.mapper = mapper; this.permissions = permissions; }

    public void requireAuthorization(TeacherUser teacher) {
        requireTeacher(teacher);
        List<String> granted = permissions.getPermissionCodesByRoleCode(teacher.getTeacherType());
        if (granted == null || !(granted.contains("view:student_tracking") || granted.contains("menu:student-tracking")))
            throw new BusinessException("当前教师不再拥有学生跟进权限，自动跟进已暂停。");
    }

    public static String scopeOf(String type) {
        return switch (type == null ? "" : type) {
            case "COUNSELOR" -> "OWN_CLASSES";
            case "DEPARTMENT" -> "OWN_DIVISION";
            case "COLLEGE" -> "OWN_COLLEGE";
            default -> "";
        };
    }

    public static void requireTeacher(TeacherUser teacher) {
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                || !Set.of("COUNSELOR", "DEPARTMENT", "COLLEGE").contains(String.valueOf(teacher.getTeacherType()))) {
            throw new BusinessException("教师账号已停用或当前身份不能管理自动跟进任务。");
        }
        if (("DEPARTMENT".equals(teacher.getTeacherType()) && !validId(teacher.getDivisionId()))
                || ("COLLEGE".equals(teacher.getTeacherType()) && !validId(teacher.getDepartmentId()))) {
            throw new BusinessException("教师负责范围未配置，不能执行自动跟进。");
        }
    }

    private static boolean validId(String value) {
        try { return value != null && Long.parseLong(value.trim()) > 0; }
        catch (NumberFormatException e) { return false; }
    }

    public List<Long> studentIds(TeacherUser teacher) { requireAuthorization(teacher); return mapper.studentIds(teacher); }
    public List<StudentUser> matchStudents(TeacherUser teacher, String query) {
        requireAuthorization(teacher);
        List<StudentUser> matches = mapper.matchStudents(teacher, query);
        // A unique account/student number takes priority over an identical display name.
        List<StudentUser> accounts = matches.stream().filter(s -> query.equalsIgnoreCase(s.getUsername()) || query.equalsIgnoreCase(s.getStudentUserId())).toList();
        return accounts.isEmpty() ? matches : accounts;
    }
    public boolean contains(TeacherUser teacher, Long studentId) {
        requireAuthorization(teacher); return mapper.contains(teacher, studentId) > 0;
    }
    public String label(TeacherUser teacher) {
        return switch (scopeOf(teacher.getTeacherType())) {
            case "OWN_CLASSES" -> "本人负责班级";
            case "OWN_DIVISION" -> "本系学生";
            case "OWN_COLLEGE" -> "本学院学生";
            default -> "未配置范围";
        };
    }
}
