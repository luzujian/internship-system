package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentUserMapperXmlTest {

    @Test
    void counselorStudentListFallsBackToStoredCompanyNameForSelfPractice() throws Exception {
        String resource = "com/internship/mapper/StudentUserMapper.xml";
        Configuration configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }

        BoundSql listSql = configuration
                .getMappedStatement("com.internship.mapper.StudentUserMapper.findByClassIdsAndSearch")
                .getBoundSql(Map.of("classIds", List.of(1L), "companyName", "望海康信"));
        String normalizedListSql = listSql.getSql().replaceAll("\\s+", " ").toLowerCase();

        assertTrue(normalizedListSql.contains(
                "coalesce(nullif(cu.company_name, ''), sis.company_name) as companyname"));
        assertTrue(normalizedListSql.contains(
                "coalesce(nullif(cu.company_name, ''), sis.company_name) like concat('%', ?, '%')"));

        BoundSql classSql = configuration
                .getMappedStatement("com.internship.mapper.StudentUserMapper.findByClassIdAsMap")
                .getBoundSql(Map.of("classId", 1L));
        String normalizedClassSql = classSql.getSql().replaceAll("\\s+", " ").toLowerCase();

        assertTrue(normalizedClassSql.contains(
                "coalesce(nullif(cu.company_name, ''), sis.company_name) as companyname"));
    }
}
