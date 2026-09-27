package com.internship.mapper;

import com.internship.entity.CompanyUser;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompanyUserMapperRecallHistoryXmlTest {
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
    void ordinaryUpdateMustNotEraseRecallHistoryWhenCurrentStatusResets() {
        CompanyUser company = new CompanyUser();
        company.setId(7L);
        company.setRecallStatus(0);

        String sql = sql("update", company);

        assertFalse(sql.contains("recall_reason = null"));
        assertFalse(sql.contains("recall_apply_time = null"));
        assertFalse(sql.contains("recall_audit_time = null"));
        assertFalse(sql.contains("recall_audit_remark = null"));
    }

    @Test
    void recallRecordQueryKeepsCompletedHistoryAfterResubmission() {
        Map<String, Object> params = new HashMap<>();
        params.put("companyName", null);
        params.put("contactPerson", null);
        params.put("startTime", null);
        params.put("endTime", null);
        String sql = sql("findRecallRecords", params);

        assertTrue(sql.contains("recall_status in (1, 2, 3)"));
        assertTrue(sql.contains("or recall_reason is not null"));
        assertTrue(sql.contains("or recall_apply_time is not null"));
    }

    @Test
    void explicitRecallClearAlsoRemovesHistoricalFields() {
        String sql = sql("clearRecallData", null);

        assertTrue(sql.contains("recall_reason = null"));
        assertTrue(sql.contains("recall_apply_time = null"));
        assertTrue(sql.contains("or recall_reason is not null"));
    }

    private String sql(String statement, Object parameter) {
        BoundSql boundSql = configuration.getMappedStatement(
                        "com.internship.mapper.CompanyUserMapper." + statement)
                .getBoundSql(parameter);
        return boundSql.getSql().replaceAll("\\s+", " ").trim().toLowerCase();
    }
}
