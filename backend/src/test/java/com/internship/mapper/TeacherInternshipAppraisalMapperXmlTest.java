package com.internship.mapper;

import com.internship.entity.dto.TeacherAppraisalFileInfo;
import com.internship.entity.dto.TeacherAppraisalScope;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TeacherInternshipAppraisalMapperXmlTest {

    @Test
    void mapperParsesAndAppliesEachTeacherScopeToLatestSubmission() throws Exception {
        Configuration configuration = parseMappers();

        String collegeSql = sql(configuration, "findStudents",
                params(new TeacherAppraisalScope(10L, "COLLEGE", 2L, null), "SUBMITTED"));
        String departmentSql = sql(configuration, "findStudents",
                params(new TeacherAppraisalScope(11L, "DEPARTMENT", null, 3L), "ALL"));
        String counselorSql = sql(configuration, "findStudents",
                params(new TeacherAppraisalScope(12L, "COUNSELOR", null, null), "NOT_SUBMITTED"));
        String fileSql = sql(configuration, "findAuthorizedSubmittedFile",
                Map.of("scope", new TeacherAppraisalScope(12L, "COUNSELOR", null, null), "fileId", 99L));
        String classSql = sql(configuration, "findClasses",
                Map.of("scope", new TeacherAppraisalScope(10L, "COLLEGE", 2L, null)));

        assertTrue(collegeSql.contains("m.department_id = ?"));
        assertTrue(collegeSql.contains("ias.status = 'submitted'"));
        assertTrue(departmentSql.contains("m.division_id = ?"));
        assertTrue(counselorSql.contains("class_counselor_relation scope_ccr"));
        assertTrue(counselorSql.contains("ias.id is null or ias.status != 'submitted'"));
        assertTrue(fileSql.contains("ias.status = 'submitted'"));
        assertTrue(fileSql.contains("select max(latest_ias.id)"));
        assertTrue(fileSql.contains("scope_ccr.counselor_id = ?"));
        assertTrue(classSql.contains("select distinct c.id, c.name, c.grade"));
    }

    @Test
    void publicFileMetadataCannotExposeOssPath() {
        Set<String> fields = Arrays.stream(TeacherAppraisalFileInfo.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());
        assertFalse(fields.contains("filePath"));
        assertFalse(fields.contains("fileUrl"));
    }

    private Configuration parseMappers() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : new String[]{
                "com/internship/mapper/TeacherInternshipAppraisalMapper.xml"
        }) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }

    private Map<String, Object> params(TeacherAppraisalScope scope, String status) {
        Map<String, Object> params = new HashMap<>();
        params.put("scope", scope);
        params.put("keyword", "张");
        params.put("classId", 20L);
        params.put("status", status);
        params.put("offset", 0);
        params.put("pageSize", 20);
        return params;
    }

    private String sql(Configuration configuration, String method, Map<String, Object> params) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.internship.mapper.TeacherInternshipAppraisalMapper." + method)
                .getBoundSql(params);
        return boundSql.getSql().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
