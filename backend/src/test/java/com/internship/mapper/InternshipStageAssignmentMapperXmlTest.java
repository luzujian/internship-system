package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InternshipStageAssignmentMapperXmlTest {
    private Configuration configuration;

    @BeforeEach
    void setUp() throws Exception {
        String resource = "com/internship/mapper/InternshipStageAssignmentMapper.xml";
        configuration = new Configuration();
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void stableOfferSourceUsesSourceAssignmentId() {
        String sql = sql("findBySource", Map.of(
                "sourceType", "INTERVIEW_INVITATION", "sourceId", 80L));

        assertTrue(sql.contains("source_type = ? and source_assignment_id = ?"));
    }

    @Test
    void confirmationLookupPrefersCurrentPlacementAcrossLegacyContinuationChain() {
        String readSql = sql("findByConfirmationRecordId", Map.of("confirmationRecordId", 70L));
        String lockSql = sql("findByConfirmationRecordIdForUpdate", Map.of("confirmationRecordId", 70L));

        assertTrue(readSql.contains("order by current_flag desc, id desc limit 1"));
        assertTrue(lockSql.contains("order by current_flag desc, id desc limit 1 for update"));
    }

    private String sql(String statement, Map<String, Object> parameters) {
        return configuration.getMappedStatement(
                        "com.internship.mapper.InternshipStageAssignmentMapper." + statement)
                .getBoundSql(parameters)
                .getSql()
                .replaceAll("\\s+", " ")
                .trim()
                .toLowerCase();
    }
}
