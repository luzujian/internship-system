package com.internship.mapper;

import com.internship.entity.StudentUser;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AccountIdentifierMapperTest {
    @Test
    void studentLoginAndNumberChangeTogether() throws Exception {
        Configuration config = load("StudentUserMapper");
        StudentUser student = new StudentUser();
        student.setId(8L);
        student.setStudentId("s002");
        var sql = config.getMappedStatement("com.internship.mapper.StudentUserMapper.update").getBoundSql(student);
        String normalized = sql.getSql().replaceAll("\\s+", " ");
        assertTrue(normalized.contains("student_user_id = ?, username = ?"));
        assertEquals(List.of("studentId", "studentId", "status", "id"),
                sql.getParameterMappings().stream().map(p -> p.getProperty()).toList());
    }

    @Test
    void referenceUpdatesUseOldTeacherNumberOrImmutableStudentIdOnly() {
        Configuration config = new Configuration();
        config.addMapper(AccountIdentifierMapper.class);
        for (String method : List.of("renameApplicationStudent", "renameStudentApplicationStudent",
                "renameReflectionStudent", "renameConfirmationStudent")) {
            var sql = config.getMappedStatement(AccountIdentifierMapper.class.getName()+"."+method)
                    .getBoundSql(Map.of("id", 8L, "newId", "s002"));
            assertTrue(sql.getSql().endsWith("WHERE student_id=?"));
            assertEquals(List.of("newId", "id"), sql.getParameterMappings().stream().map(p -> p.getProperty()).toList());
        }
        var teacher = config.getMappedStatement(AccountIdentifierMapper.class.getName()+".renameTeacher")
                .getBoundSql(Map.of("oldId", "t003", "newId", "x003"));
        assertTrue(teacher.getSql().endsWith("WHERE teacher_user_id=?"));
    }

    @Test
    void teacherReadCountsJoinStableIdInsteadOfChangeableNumber() throws Exception {
        var sql = load("AnnouncementReadRecordMapper")
                .getMappedStatement("com.internship.mapper.AnnouncementReadRecordMapper.countUnreadByAnnouncementId")
                .getBoundSql(Map.of("announcementId", 1L, "targetType", "TEACHER_TYPE", "targetValue", "COUNSELOR"));
        assertTrue(sql.getSql().contains("arr.user_id = CAST(tu.id AS CHAR)"));
        assertFalse(sql.getSql().contains("tu.teacher_user_id"));
    }

    private Configuration load(String name) throws Exception {
        var config = new Configuration();
        String resource = "com/internship/mapper/"+name+".xml";
        try (var input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        return config;
    }
}
