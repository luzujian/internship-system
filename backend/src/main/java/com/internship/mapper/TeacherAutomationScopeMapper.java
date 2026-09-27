package com.internship.mapper;

import com.internship.entity.TeacherUser;
import com.internship.entity.StudentUser;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** Automation uses the teacher's narrow business scope, never the broader summary scope. */
@Mapper
public interface TeacherAutomationScopeMapper {
    String SCOPE = "<choose>"
            + "<when test=\"teacher.teacherType == 'COUNSELOR'\"> AND EXISTS (SELECT 1 FROM class_counselor_relation r WHERE r.class_id=su.class_id AND r.counselor_id=#{teacher.id})</when>"
            + "<when test=\"teacher.teacherType == 'DEPARTMENT'\"> AND m.division_id=#{teacher.divisionId}</when>"
            + "<when test=\"teacher.teacherType == 'COLLEGE'\"> AND m.department_id=#{teacher.departmentId}</when>"
            + "<otherwise> AND 1=0</otherwise></choose>";

    @Select("<script>SELECT su.id FROM student_users su LEFT JOIN major m ON m.id=su.major_id WHERE su.status=1 "
            + SCOPE + " ORDER BY su.id</script>")
    List<Long> studentIds(@Param("teacher") TeacherUser teacher);

    @Select("<script>SELECT COUNT(*) FROM student_users su LEFT JOIN major m ON m.id=su.major_id WHERE su.status=1 AND su.id=#{studentId} "
            + SCOPE + "</script>")
    int contains(@Param("teacher") TeacherUser teacher, @Param("studentId") Long studentId);

    @Select("<script>SELECT su.id,su.username,su.name,su.student_user_id,su.email,su.phone FROM student_users su "
            + "LEFT JOIN major m ON m.id=su.major_id WHERE su.status=1 "
            + "AND (su.username=#{query} OR su.student_user_id=#{query} OR su.name=#{query}) "
            + SCOPE + " ORDER BY (su.username=#{query}) DESC,(su.student_user_id=#{query}) DESC,su.id LIMIT 21</script>")
    List<StudentUser> matchStudents(@Param("teacher") TeacherUser teacher, @Param("query") String query);
}
