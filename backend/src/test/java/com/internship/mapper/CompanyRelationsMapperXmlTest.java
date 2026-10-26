package com.internship.mapper;

import com.internship.dto.TeacherCompanyDTO;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyRelationsMapperXmlTest {
    @Test
    void relationMapperXmlFilesParseWithoutDatabaseConnection() {
        List<String> resources = List.of(
                "com/internship/mapper/CompanyAlumniMemberMapper.xml",
                "com/internship/mapper/CompanySchoolLiaisonMapper.xml",
                "com/internship/mapper/CompanyVisitMapper.xml",
                "com/internship/mapper/CompanyUserMapper.xml",
                "com/internship/mapper/TeacherUserMapper.xml"
        );

        assertDoesNotThrow(() -> {
            Configuration configuration = new Configuration();
            for (String resource : resources) {
                try (InputStream input = Resources.getResourceAsStream(resource)) {
                    XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                            input, configuration, resource, configuration.getSqlFragments());
                    mapperBuilder.parse();
                }
            }
        });
    }

    @Test
    void teacherCompanyQueriesAndDtoExcludeSensitiveFields() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/CompanyUserMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            XMLMapperBuilder mapperBuilder = new XMLMapperBuilder(
                    input, configuration, resource, configuration.getSqlFragments());
            mapperBuilder.parse();
        }

        Map<String, Object> listParameters = new HashMap<>();
        listParameters.put("companyName", null);
        listParameters.put("status", null);
        listParameters.put("auditStatus", null);
        listParameters.put("recallStatus", null);
        listParameters.put("companyTags", null);
        listParameters.put("industry", null);
        listParameters.put("keyword", null);
        listParameters.put("visitedYear", 2026);
        listParameters.put("visitState", null);
        listParameters.put("visitTeacherId", null);
        listParameters.put("liaisonTeacherId", null);

        String listSql = normalizedSql(configuration
                .getMappedStatement("com.internship.mapper.CompanyUserMapper.findTeacherCompanies")
                .getBoundSql(listParameters).getSql());
        String detailSql = normalizedSql(configuration
                .getMappedStatement("com.internship.mapper.CompanyUserMapper.findTeacherCompanyById")
                .getBoundSql(Map.of("id", 1L)).getSql());

        Set<String> dtoFields = Arrays.stream(TeacherCompanyDTO.class.getDeclaredFields())
                .map(Field::getName)
                .collect(Collectors.toSet());

        assertAll(
                () -> assertTrue(listSql.contains("c.company_name")),
                () -> assertTrue(detailSql.contains("c.company_name")),
                () -> assertTrue(listSql.contains("c.audit_status")),
                () -> assertTrue(detailSql.contains("c.audit_status")),
                () -> assertTrue(dtoFields.contains("auditStatus")),
                () -> assertFalse(listSql.contains("c.*")),
                () -> assertFalse(detailSql.contains("c.*")),
                () -> assertSensitiveColumnsExcluded(listSql),
                () -> assertSensitiveColumnsExcluded(detailSql),
                () -> assertFalse(dtoFields.contains("password")),
                () -> assertFalse(dtoFields.contains("businessLicense")),
                () -> assertFalse(dtoFields.contains("legalIdCard")),
                () -> assertFalse(dtoFields.contains("plaquePhoto"))
        );
    }

    @Test
    void auditAndAccountFiltersRemainIndependentIncludingPendingAuditZero() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/CompanyUserMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(
                "com.internship.mapper.CompanyUserMapper.findTeacherCompanies");
        for (int auditStatus = 0; auditStatus <= 3; auditStatus++) {
            Map<String, Object> parameters = new HashMap<>();
            parameters.put("auditStatus", auditStatus);
            parameters.put("status", 1);
            var bound = statement.getBoundSql(parameters);
            String sql = normalizedSql(bound.getSql());
            String conditions = sql.substring(sql.indexOf(" where "));
            assertTrue(conditions.contains("c.status = ? and c.audit_status = ?"));
            assertEquals(List.of("status", "auditStatus"), bound.getParameterMappings().stream()
                    .map(mapping -> mapping.getProperty()).toList());

            parameters.put("status", null);
            sql = normalizedSql(statement.getBoundSql(parameters).getSql());
            conditions = sql.substring(sql.indexOf(" where "), sql.indexOf(" order by "));
            assertTrue(conditions.contains("c.audit_status = ?"));
            assertFalse(conditions.contains("c.status"));
        }
        var mappings = configuration.getResultMap(
                "com.internship.mapper.CompanyUserMapper.TeacherCompanyResultMap").getResultMappings();
        assertTrue(mappings.stream().anyMatch(mapping -> "auditStatus".equals(mapping.getProperty())
                && "audit_status".equals(mapping.getColumn())));
    }

    @Test
    void inactiveAccountFilterIncludesBothLegacyInactiveStates() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/CompanyUserMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        var statement = configuration.getMappedStatement(
                "com.internship.mapper.CompanyUserMapper.findTeacherCompanies");
        String sql = normalizedSql(statement.getBoundSql(Map.of("status", 0)).getSql());
        assertTrue(sql.contains("where c.status in (0, 3)"));
        assertFalse(sql.contains("c.audit_status ="));
        sql = normalizedSql(statement.getBoundSql(Map.of("status", 2)).getSql());
        assertTrue(sql.contains("where c.status = ?"));
        assertFalse(sql.contains("c.audit_status ="));
    }

    private String normalizedSql(String sql) {
        return sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private void assertSensitiveColumnsExcluded(String sql) {
        assertAll(
                () -> assertFalse(sql.contains("password")),
                () -> assertFalse(sql.contains("business_license")),
                () -> assertFalse(sql.contains("legal_id_card")),
                () -> assertFalse(sql.contains("plaque_photo")),
                () -> assertFalse(sql.contains("recall_reason")),
                () -> assertFalse(sql.contains("audit_remark"))
        );
    }
}
