package com.internship.mapper;

import com.internship.entity.InterviewInvitation;
import com.internship.entity.dto.StudentPositionKey;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.nologging.NoLoggingImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.opentest4j.TestAbortedException;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

/** Opt-in comparison against existing local data. Never loads Spring or creates/changes data. */
@EnabledIfEnvironmentVariable(named = "PERFORMANCE_LOCAL_DB_TESTS", matches = "true")
class PerformanceReadLocalMapperTest {
    private static final String LOCAL_URL = "jdbc:mysql://127.0.0.1:3306/internship"
            + "?serverTimezone=Asia/Shanghai&connectTimeout=2000&socketTimeout=5000";

    @Test
    void batchDirectHireFlagsMatchExistingSinglePairLookup() {
        try (SqlSession session = openReadOnlySession()) {
            List<StudentPositionKey> pairs = new ArrayList<>();
            try (Statement statement = session.getConnection().createStatement()) {
                statement.setQueryTimeout(5);
                statement.setMaxRows(200);
                try (ResultSet rows = statement.executeQuery("SELECT DISTINCT student_id, position_id "
                        + "FROM interview_invitation WHERE student_id IS NOT NULL "
                        + "AND position_id IS NOT NULL LIMIT 200")) {
                    while (rows.next()) {
                        pairs.add(new StudentPositionKey(rows.getLong(1), rows.getLong(2)));
                    }
                }
            }
            assumeFalse(pairs.isEmpty(), "No existing local interview pairs available for comparison");
            InterviewInvitationMapper mapper = session.getMapper(InterviewInvitationMapper.class);
            List<InterviewInvitation> batch = mapper.findLatestForStudentPositions(pairs);
            assertTrue(batch.size() == pairs.size(), "Batch lookup must return one row for each requested pair");
            Map<StudentPositionKey, Boolean> flags = new HashMap<>();
            for (InterviewInvitation invitation : batch) {
                flags.put(new StudentPositionKey(invitation.getStudentId(), invitation.getPositionId()),
                        "direct_hire".equals(invitation.getInterviewMethod()));
            }
            for (StudentPositionKey pair : pairs) {
                InterviewInvitation original = mapper.findByStudentAndPosition(pair.studentId(), pair.positionId());
                boolean expected = original != null && "direct_hire".equals(original.getInterviewMethod());
                assertTrue(flags.containsKey(pair), "Batch lookup must preserve the requested pair");
                assertTrue(expected == flags.get(pair), "Batch and original latest-invitation flags must match");
            }
            System.out.printf("Local read-only verification: %d interview pairs matched.%n", pairs.size());
        } catch (TestAbortedException exception) {
            throw exception;
        } catch (Exception exception) {
            // Report failure without logging SQL parameters, credentials, or returned personal data.
            fail("Local interview mapper comparison failed: " + exception.getClass().getSimpleName());
        }
    }

    @Test
    void companyPositionCountMatchesExistingListSize() {
        try (SqlSession session = openReadOnlySession()) {
            List<Long> companyIds = new ArrayList<>();
            try (Statement statement = session.getConnection().createStatement()) {
                statement.setQueryTimeout(5);
                statement.setMaxRows(5);
                try (ResultSet rows = statement.executeQuery(
                        "SELECT DISTINCT company_id FROM position WHERE company_id IS NOT NULL LIMIT 5")) {
                    while (rows.next()) {
                        companyIds.add(rows.getLong(1));
                    }
                }
            }
            assumeFalse(companyIds.isEmpty(), "No existing local company positions available for comparison");
            PositionMapper mapper = session.getMapper(PositionMapper.class);
            for (Long companyId : companyIds) {
                Long count = mapper.countByCompanyId(companyId);
                assertNotNull(count, "Position count must be available");
                assertTrue(count.longValue() == mapper.findByCompanyId(companyId).size(),
                        "COUNT and original company position list size must match");
            }
            System.out.printf("Local read-only verification: %d company position counts matched.%n", companyIds.size());
        } catch (TestAbortedException exception) {
            throw exception;
        } catch (Exception exception) {
            fail("Local position mapper comparison failed: " + exception.getClass().getSimpleName());
        }
    }

    private SqlSession openReadOnlySession() throws Exception {
        String password = System.getenv("LOCAL_DB_PASSWORD");
        assertNotNull(password, "LOCAL_DB_PASSWORD must be explicitly supplied");
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "com.mysql.cj.jdbc.Driver", LOCAL_URL, "user", password);
        Configuration configuration = new Configuration(new Environment(
                "local-read-only", new JdbcTransactionFactory(), dataSource));
        configuration.setLogImpl(NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDefaultStatementTimeout(5);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        for (String resource : List.of("com/internship/mapper/InterviewInvitationMapper.xml",
                "com/internship/mapper/PositionMapper.xml")) {
            try (InputStream input = Resources.getResourceAsStream(resource)) {
                new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
            }
        }
        SqlSession session = new SqlSessionFactoryBuilder().build(configuration).openSession(false);
        try {
            Connection connection = session.getConnection();
            connection.setReadOnly(true);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            return session;
        } catch (Exception exception) {
            session.close();
            throw exception;
        }
    }
}
