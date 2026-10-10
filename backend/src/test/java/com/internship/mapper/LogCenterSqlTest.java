package com.internship.mapper;

import com.internship.dto.LogQueryParams;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** 在内存中解析动态 SQL，验证新增筛选/导出/归档语句，不打开数据库连接。 */
class LogCenterSqlTest {
    @ParameterizedTest
    @ValueSource(classes = {OperateLogMapper.class, LoginLogMapper.class, SecurityEventLogMapper.class})
    void filteredExportHasAParameterizedLimitAndStableOrdering(Class<?> mapper) throws Exception {
        LogQueryParams query = new LogQueryParams();
        query.setStartTime(new Date(0));
        query.setEndTime(new Date());
        query.setOperatorName("张老师");
        query.setRole("TEACHER");
        query.setModule("REPORT");
        query.setActionList(List.of("UPDATE", "修改"));
        query.setResult("FAILURE");
        query.setRiskLevel("HIGH,CRITICAL");
        query.setIp("192.168");
        query.setTargetId("12");
        query.setRequestId("request-123");
        query.setSortField("id");
        query.setSortOrder("desc");
        query.setLimit(100001);
        String script = String.join(" ", mapper.getMethod("selectByFilter", LogQueryParams.class).getAnnotation(Select.class).value());
        BoundSql sql = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class)
                .getBoundSql(Map.of("q", query));
        assertNotNull(CCJSqlParserUtil.parse(sql.getSql()));
        assertTrue(sql.getSql().contains("limit ?"));
        assertTrue(sql.getParameterMappings().stream().anyMatch(mapping -> "q.limit".equals(mapping.getProperty())));
        assertTrue(sql.getSql().contains("risk_level in ('HIGH','CRITICAL')"));
        query.setLimit(null);
        BoundSql pageSql = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class)
                .getBoundSql(Map.of("q", query));
        assertFalse(pageSql.getSql().contains("limit ?"), "普通分页由 PageHelper 提供 LIMIT，避免重复分页");
    }

    @ParameterizedTest
    @ValueSource(classes = {OperateLogMapper.class, SecurityEventLogMapper.class})
    void archiveDeletionBindsOnlyTheArchivedIds(Class<?> mapper) throws Exception {
        String script = String.join(" ", mapper.getMethod("deleteArchivedHighRisk", List.class).getAnnotation(Delete.class).value());
        BoundSql sql = new XMLLanguageDriver().createSqlSource(new Configuration(), script, Map.class)
                .getBoundSql(Map.of("ids", List.of(42L, 48L)));
        assertNotNull(CCJSqlParserUtil.parse(sql.getSql()));
        assertEquals(2, sql.getParameterMappings().size());
        assertTrue(sql.getSql().contains("risk_level in ('HIGH','CRITICAL')"));
        assertTrue(sql.getSql().contains("id in"));
    }
}
