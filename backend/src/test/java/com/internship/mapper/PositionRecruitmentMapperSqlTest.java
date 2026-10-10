package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PositionRecruitmentMapperSqlTest {
    @ParameterizedTest
    @MethodSource("recruitmentFilters")
    void administratorRecruitmentListRendersValidQuotaSubqueries(
            Map<String, Object> filters, List<String> expectedParameters) throws Exception {
        BoundSql bound = sql("findPageWithCompany", filters);
        String rendered = bound.getSql().replaceAll("\\s+", " ");
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(rendered));
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(
                "SELECT COUNT(0) FROM (" + rendered + ") tmp_count"));

        assertFalse(rendered.contains("p.idAND"));
        assertEquals(2, occurrences(rendered, "sis.position_id = p.id AND sis.status IN (1, 2, 3) AND COALESCE(sis.recall_status, 0) != 2"));
        assertEquals(expectedParameters, bound.getParameterMappings().stream()
                .map(item -> item.getProperty()).toList());
    }

    private static Stream<Arguments> recruitmentFilters() {
        return Stream.of(
                Arguments.of(Map.of(), List.of()),
                Arguments.of(Map.of("companyName", "", "positionName", ""), List.of()),
                Arguments.of(Map.of("companyName", "测试"), List.of("companyName")),
                Arguments.of(Map.of("positionName", "岗位"), List.of("positionName")),
                Arguments.of(Map.of("companyName", "测试", "positionName", "岗位"),
                        List.of("companyName", "positionName"))
        );
    }

    @Test
    void quotaRecalculationRendersExactlyThreePositionParameters() throws Exception {
        BoundSql bound = sql("updateRecruitedCount", Map.of("positionId", 9L));
        String rendered = bound.getSql().replaceAll("\\s+", " ");
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(rendered));

        assertEquals(2, occurrences(rendered, "sis.position_id = ? AND sis.status IN (1, 2, 3) AND COALESCE(sis.recall_status, 0) != 2"));
        assertTrue(rendered.contains("WHERE p.id = ?"));
        assertEquals(List.of("positionId", "positionId", "positionId"), bound.getParameterMappings().stream()
                .map(item -> item.getProperty()).toList());
    }

    private BoundSql sql(String statement, Map<String, Object> parameters) throws Exception {
        Configuration configuration = new Configuration();
        String resource = "com/internship/mapper/PositionMapper.xml";
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        return configuration.getMappedStatement("com.internship.mapper.PositionMapper." + statement)
                .getBoundSql(parameters);
    }

    private int occurrences(String text, String fragment) {
        return (text.length() - text.replace(fragment, "").length()) / fragment.length();
    }
}
