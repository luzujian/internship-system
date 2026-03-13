package com.gdmu.controller;

import com.gdmu.entity.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/check")
    public Result checkHealth() {
        Map<String, Object> healthInfo = new HashMap<>();
        
        try {
            Connection connection = dataSource.getConnection();
            boolean isValid = connection.isValid(5);
            connection.close();
            
            healthInfo.put("database", "connected");
            healthInfo.put("status", isValid ? "healthy" : "unhealthy");
            
            if (isValid) {
                try {
                    Integer count = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
                    healthInfo.put("queryTest", "success");
                } catch (Exception e) {
                    healthInfo.put("queryTest", "failed: " + e.getMessage());
                }
            }
            
            return Result.success(healthInfo);
        } catch (Exception e) {
            log.error("健康检查失败: {}", e.getMessage());
            healthInfo.put("database", "disconnected");
            healthInfo.put("status", "error");
            healthInfo.put("error", e.getMessage());
            return new Result(400, "数据库连接失败", healthInfo);
        }
    }
}
