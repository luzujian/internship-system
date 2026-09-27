package com.internship.mapper;

import com.internship.entity.TeacherUser;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

class TeacherIdentifierUpdateTest {
    private BoundSql updateSql(TeacherUser teacher) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/TeacherUserMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement("com.internship.mapper.TeacherUserMapper.update")
                .getBoundSql(teacher);
    }

    @Test
    void renameReleasesOldUsernameEvenWhenRequestStillContainsIt() throws Exception {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(12L);
        teacher.setTeacherUserId("tb001");
        teacher.setUsername("t002");

        BoundSql sql = updateSql(teacher);
        String normalized = sql.getSql().replaceAll("\\s+", " ");
        assertTrue(normalized.contains("teacher_user_id = ?, username = ?"));
        assertEquals(2, sql.getParameterMappings().stream()
                .filter(mapping -> mapping.getProperty().equals("teacherUserId")).count());
        assertFalse(sql.getParameterMappings().stream()
                .anyMatch(mapping -> mapping.getProperty().equals("username")));
        assertTrue(normalized.contains("where id = ?"));
    }

    @Test
    void savingExistingNumberAlsoRepairsLegacyUsername() throws Exception {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(12L);
        teacher.setTeacherUserId("tb001");
        assertTrue(updateSql(teacher).getSql().contains("username = ?"));
    }

    @Test
    void statusUpdatesCannotClearIdentifiers() throws Exception {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(12L);
        teacher.setStatus(0);
        for (String identifier : new String[] {null, ""}) {
            teacher.setTeacherUserId(identifier);
            String sql = updateSql(teacher).getSql();
            assertTrue(sql.contains("status = ?"));
            assertFalse(sql.contains("username ="));
            assertFalse(sql.contains("teacher_user_id ="));
        }
    }
}
