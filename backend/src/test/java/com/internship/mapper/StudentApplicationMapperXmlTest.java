package com.internship.mapper;

import com.internship.entity.StudentApplication;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StudentApplicationMapperXmlTest {
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        String resource = "com/internship/mapper/StudentApplicationMapper.xml";
        configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void selfPracticeContactFieldsAreMappedForReadInsertAndUpdate() {
        Set<String> mappings = configuration
                .getResultMap("com.internship.mapper.StudentApplicationMapper.StudentApplicationResultMap")
                .getResultMappings().stream()
                .map(mapping -> mapping.getProperty() + ":" + mapping.getColumn())
                .collect(Collectors.toSet());

        StudentApplication application = new StudentApplication();
        application.setId(1L);
        application.setCompanyCity("广东省广州市");
        application.setHrManagerName("张经理");
        application.setHrManagerPhone("020-12345678");

        BoundSql insert = sql("insert", application);
        BoundSql update = sql("update", application);
        String insertSql = normalize(insert.getSql());
        String updateSql = normalize(update.getSql());
        List<String> insertParameters = insert.getParameterMappings().stream()
                .map(mapping -> mapping.getProperty())
                .toList();

        assertAll(
                () -> assertTrue(mappings.contains("companyCity:company_city")),
                () -> assertTrue(mappings.contains("hrManagerName:hr_manager_name")),
                () -> assertTrue(mappings.contains("hrManagerPhone:hr_manager_phone")),
                () -> assertTrue(insertSql.contains("company_city, hr_manager_name, hr_manager_phone")),
                () -> assertTrue(insertParameters.containsAll(
                        List.of("companyCity", "hrManagerName", "hrManagerPhone"))),
                () -> assertTrue(updateSql.contains("company_city = ?")),
                () -> assertTrue(updateSql.contains("hr_manager_name = ?")),
                () -> assertTrue(updateSql.contains("hr_manager_phone = ?"))
        );
    }

    @Test
    void scopedListRestrictsStudentIdsAndEmptyScopeCannotExpandToAllStudents() {
        String restricted = normalize(configuration.getMappedStatement(
                "com.internship.mapper.StudentApplicationMapper.list").getBoundSql(Map.of(
                "applicationType", "delay", "status", "pending", "studentIds", List.of(501L, 502L)))
                .getSql());
        String empty = normalize(configuration.getMappedStatement(
                "com.internship.mapper.StudentApplicationMapper.list").getBoundSql(Map.of(
                "applicationType", "delay", "studentIds", List.of())).getSql());

        assertTrue(restricted.contains("student_id in (?,?)"));
        assertTrue(empty.contains("and 1 = 0"));
    }

    private BoundSql sql(String statement, StudentApplication application) {
        return configuration.getMappedStatement(
                        "com.internship.mapper.StudentApplicationMapper." + statement)
                .getBoundSql(application);
    }

    private String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
