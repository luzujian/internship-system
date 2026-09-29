package com.internship.mapper;

import com.internship.dto.LogQueryParams;
import com.internship.entity.LoginLog;
import com.internship.entity.OperateLog;
import com.internship.entity.SecurityEventLog;
import com.internship.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P1 日志中心查询 SQL 集成验证（真实数据库）
 * 验证三类日志的组合筛选动态 SQL、统计与留存预览 SQL 语法正确可执行
 */
@SpringBootTest
class LogCenterMapperIntegrationTest {

    @Autowired
    private OperateLogMapper operateLogMapper;

    @Autowired
    private LoginLogMapper loginLogMapper;

    @Autowired
    private SecurityEventLogMapper securityEventLogMapper;

    @Autowired
    private AuditLogService auditLogService;

    private LogQueryParams baseQuery() {
        LogQueryParams q = new LogQueryParams();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -7);
        q.setStartTime(cal.getTime());
        q.setEndTime(new Date());
        q.setSortField("operate_time");
        q.setSortOrder("desc");
        return q;
    }

    @Test
    void operateLogFilterQueryWithAllConditionsExecutes() {
        LogQueryParams q = baseQuery();
        q.setOperatorName("admin");
        q.setRole("ADMIN");
        q.setModule("LOG_MANAGEMENT");
        q.setActionList(auditLogService.expandActionAliases("CREATE"));
        q.setResult("SUCCESS");
        q.setRiskLevel("LOW");
        q.setIp("192.168");
        q.setRequestId("test");

        List<OperateLog> rows = operateLogMapper.selectByFilter(q);
        assertNotNull(rows);
        long count = operateLogMapper.countByFilter(q);
        assertNotNull(count);
        // 动作别名扩展后必然包含历史 ADD/INSERT 类型查询能力
        assertFalse(q.getActionList().isEmpty());
    }

    @Test
    void loginLogFilterQueryExecutes() {
        LogQueryParams q = baseQuery();
        q.setOperatorName("s001");
        q.setRole("ROLE_STUDENT");
        q.setResult("SUCCESS");
        q.setSortField("login_time");

        List<LoginLog> rows = loginLogMapper.selectByFilter(q);
        assertNotNull(rows);
        long count = loginLogMapper.countByFilter(q);
        assertNotNull(count);
    }

    @Test
    void securityEventFilterQueryAndStatsExecute() {
        LogQueryParams q = baseQuery();
        q.setOperatorName("s001");
        q.setResult("SUCCESS");
        q.setSortField("event_time");
        q.setSortOrder("desc");

        List<SecurityEventLog> rows = securityEventLogMapper.selectByFilter(q);
        assertNotNull(rows);
        long count = securityEventLogMapper.countByFilter(q);
        assertNotNull(count);
        assertNotNull(securityEventLogMapper.countHighRiskByTimeRange(q.getStartTime(), q.getEndTime()));
        assertNotNull(securityEventLogMapper.selectDistinctEventTypes());
    }

    @Test
    void operateLogStatsAndOptionsExecute() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -1);
        assertNotNull(operateLogMapper.countByTimeRange(cal.getTime(), new Date(), null));
        assertNotNull(operateLogMapper.countByTimeRange(cal.getTime(), new Date(), "FAILURE"));
        assertNotNull(operateLogMapper.selectDistinctOperationTypes());
        assertNotNull(operateLogMapper.selectDistinctModules());
    }

    @Test
    void retentionPreviewCountSqlExecutes() {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -180);
        Date before = cal.getTime();
        assertNotNull(operateLogMapper.countBeforeTime(before));
        assertNotNull(operateLogMapper.countBeforeTimeExcludeHighRisk(before));
        assertNotNull(loginLogMapper.countBeforeTime(before));
        assertNotNull(securityEventLogMapper.countBeforeTime(before));
        assertNotNull(securityEventLogMapper.countBeforeTimeExcludeHighRisk(before));
    }

    @Test
    void detailQueriesExecute() {
        // 旧数据无新字段也应可查详情
        List<OperateLog> latest = operateLogMapper.selectAll("id", "desc");
        if (!latest.isEmpty()) {
            assertNotNull(operateLogMapper.selectById(latest.get(0).getId()));
        }
        List<LoginLog> recentLogins = loginLogMapper.selectPage(0, 1);
        if (!recentLogins.isEmpty()) {
            assertNotNull(loginLogMapper.selectById(recentLogins.get(0).getId()));
        }
        // 安全事件表时间列为 event_time（生产由 Controller 白名单保证）
        LogQueryParams secQuery = baseQuery();
        secQuery.setSortField("event_time");
        List<SecurityEventLog> events = securityEventLogMapper.selectByFilter(secQuery);
        if (!events.isEmpty()) {
            assertNotNull(securityEventLogMapper.selectById(events.get(0).getId()));
        }
    }
}
