package com.internship.mapper;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TeacherRecruitmentMapperSqlTest {
    @Test void combinedFiltersApplyBeforePaginationAndKeepCompaniesTogether() throws Exception {
        BoundSql bound = configuration().getMappedStatement(
                "com.internship.mapper.TeacherRecruitmentMapper.findPublishedPositions")
                .getBoundSql(Map.of("companyName", "企业", "positionName", "工程师",
                        "status", "paused", "categoryId", 4L));
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(
                "SELECT COUNT(0) FROM (" + sql + ") tmp_count"));
        assertEquals(List.of("companyName", "positionName", "status", "categoryId"),
                bound.getParameterMappings().stream().map(item -> item.getProperty()).toList());
        assertTrue(sql.contains("AND p.category_id = ?"));
        assertTrue(sql.contains("ORDER BY cu.company_name ASC, cu.id ASC, p.publish_date DESC, p.create_time DESC, p.id DESC"));
        assertTrue(sql.contains("published.status IN ('active', 'paused')"));
        assertFalse(sql.contains("published.category_id"));
    }

    @Test void noCategoryFilterIncludesUncategorizedPublishedPositions() throws Exception {
        Configuration configuration = configuration();
        BoundSql bound = configuration.getMappedStatement(
                "com.internship.mapper.TeacherRecruitmentMapper.findPublishedPositions").getBoundSql(Map.of());
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertTrue(sql.contains("LEFT JOIN position_category pc ON pc.id = p.category_id"));
        assertTrue(sql.contains("WHERE p.status IN ('active', 'paused')"));
        assertFalse(sql.contains("AND p.category_id ="));
        assertTrue(bound.getParameterMappings().isEmpty());
        assertTrue(configuration.getResultMap("com.internship.mapper.TeacherRecruitmentMapper.RecruitmentPosition")
                .getResultMappings().stream().anyMatch(item ->
                        "categoryName".equals(item.getProperty()) && "category_name".equals(item.getColumn())));
    }

    @Test void detailReturnsTheSameCategoryAsTheCatalogue() throws Exception {
        BoundSql bound = configuration().getMappedStatement(
                "com.internship.mapper.TeacherRecruitmentMapper.findPublishedPositionById")
                .getBoundSql(Map.of("id", 19L));
        String sql = bound.getSql().replaceAll("\\s+", " ");
        assertDoesNotThrow(() -> net.sf.jsqlparser.parser.CCJSqlParserUtil.parse(sql));
        assertTrue(sql.contains("pc.name AS category_name"));
        assertEquals(List.of("id"), bound.getParameterMappings().stream().map(item -> item.getProperty()).toList());
    }

    private Configuration configuration() throws Exception {
        Configuration configuration = new Configuration();
        for (String resource : List.of("com/internship/mapper/PositionMapper.xml",
                "com/internship/mapper/TeacherRecruitmentMapper.xml")) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        return configuration;
    }
}
