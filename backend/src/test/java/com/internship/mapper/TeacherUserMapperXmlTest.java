package com.internship.mapper;

import com.internship.entity.TeacherUser;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherUserMapperXmlTest {
    @Test
    void singleAndBulkTeacherCreationPersistRoleAndDivision() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/TeacherUserMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        TeacherUser teacher = new TeacherUser();
        BoundSql single = configuration.getMappedStatement("com.internship.mapper.TeacherUserMapper.insert")
                .getBoundSql(teacher);
        BoundSql bulk = configuration.getMappedStatement("com.internship.mapper.TeacherUserMapper.batchInsert")
                .getBoundSql(Map.of("teacherUsers", List.of(teacher)));

        assertMapping(single, "role", "division_id", "role", "divisionId");
        assertMapping(bulk, "role", "division_id", "role", "divisionId");
    }

    private void assertMapping(BoundSql boundSql, String roleColumn, String divisionColumn,
                               String roleParameter, String divisionParameter) {
        String sql = boundSql.getSql().toLowerCase(Locale.ROOT);
        assertTrue(sql.contains(roleColumn));
        assertTrue(sql.contains(divisionColumn));
        List<String> properties = boundSql.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty()).toList();
        assertTrue(properties.stream().anyMatch(property -> property.equals(roleParameter)
                || property.endsWith("." + roleParameter)));
        assertTrue(properties.stream().anyMatch(property -> property.equals(divisionParameter)
                || property.endsWith("." + divisionParameter)));
    }
}
