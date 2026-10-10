package com.internship.controller;

import com.internship.entity.Result;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class HealthControllerTest {
    private final DataSource dataSource = mock(DataSource.class);
    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final Connection connection = mock(Connection.class);
    private final HealthController controller = new HealthController();

    @BeforeEach
    void setUp() throws SQLException {
        ReflectionTestUtils.setField(controller, "dataSource", dataSource);
        ReflectionTestUtils.setField(controller, "jdbcTemplate", jdbcTemplate);
        when(dataSource.getConnection()).thenReturn(connection);
    }

    @Test
    void releasesConnectionEvenWhenValidationThrows() throws SQLException {
        when(connection.isValid(5)).thenThrow(new SQLException("validation failed"));

        Result result = controller.checkHealth();

        assertEquals(400, result.getCode());
        assertEquals("error", ((Map<?, ?>) result.getData()).get("status"));
        verify(connection).close();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void returnsInvalidConnectionWithoutRunningQuery() throws SQLException {
        when(connection.isValid(5)).thenReturn(false);

        Result result = controller.checkHealth();

        assertEquals(200, result.getCode());
        assertEquals("unhealthy", ((Map<?, ?>) result.getData()).get("status"));
        verify(connection).close();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void releasesValidationConnectionBeforeQueryAndPreservesHealthyResponse() throws SQLException {
        when(connection.isValid(5)).thenReturn(true);
        when(jdbcTemplate.queryForObject("SELECT 1", Integer.class)).thenReturn(1);

        Result result = controller.checkHealth();

        assertEquals(200, result.getCode());
        assertEquals(Map.of("database", "connected", "status", "healthy", "queryTest", "success"), result.getData());
        var order = inOrder(connection, jdbcTemplate);
        order.verify(connection).isValid(5);
        order.verify(connection).close();
        order.verify(jdbcTemplate).queryForObject("SELECT 1", Integer.class);
    }
}
