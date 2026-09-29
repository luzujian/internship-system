package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyUserMapperCompanyMatchXmlTest {
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        String resource = "com/internship/mapper/CompanyUserMapper.xml";
        configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void studentCompanyMatchingOnlyUsesActiveApprovedCompanies() {
        String exactSql = sql("findApprovedByCompanyName", "companyName", "企业1");
        String suggestionSql = sql("findApprovedCompanyMatches", "keyword", "企业");

        assertAll(
                () -> assertActiveApprovedFilter(exactSql),
                () -> assertActiveApprovedFilter(suggestionSql),
                () -> assertTrue(exactSql.contains("lower(trim(company_name)) = lower(trim(?))")),
                () -> assertTrue(suggestionSql.contains("company_name like concat('%', ?, '%')")),
                () -> assertTrue(suggestionSql.contains("? like concat('%', company_name, '%')")),
                () -> assertTrue(suggestionSql.contains("limit 8"))
        );
    }

    @Test
    void findAllApprovedCompaniesOnlyUsesActiveApprovedCompanies() {
        assertActiveApprovedFilter(sql("findAllApprovedCompanies", "ignored", ""));
    }

    private String sql(String statement, String parameterName, String value) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.internship.mapper.CompanyUserMapper." + statement)
                .getBoundSql(Map.of(parameterName, value));
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }

    private void assertActiveApprovedFilter(String sql) {
        assertTrue(sql.contains("status = 1"));
        assertTrue(sql.contains("audit_status = 1"));
        assertTrue(sql.contains("coalesce(recall_status, 0) != 1"));
    }
}
