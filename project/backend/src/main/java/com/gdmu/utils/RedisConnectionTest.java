package com.gdmu.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
public class RedisConnectionTest implements CommandLineRunner {

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Override
    public void run(String... args) throws Exception {
        if (redisTemplate == null) {
            log.error("========================================");
            log.error("Redis未配置或连接失败！");
            log.error("系统将使用内存存储作为降级方案");
            log.error("========================================");
            return;
        }

        try {
            String testKey = "redis:connection:test";
            String testValue = "test-" + System.currentTimeMillis();

            redisTemplate.opsForValue().set(testKey, testValue, 10, TimeUnit.SECONDS);

            String retrievedValue = redisTemplate.opsForValue().get(testKey);

            if (testValue.equals(retrievedValue)) {
                log.info("========================================");
                log.info("✅ Redis连接测试成功！");
                log.info("Redis服务器正常运行");
                log.info("========================================");
            } else {
                log.error("========================================");
                log.error("❌ Redis连接测试失败！");
                log.error("写入值: {}, 读取值: {}", testValue, retrievedValue);
                log.error("========================================");
            }

            redisTemplate.delete(testKey);
        } catch (Exception e) {
            log.error("========================================");
            log.error("❌ Redis连接测试失败！");
            log.error("错误信息: {}", e.getMessage());
            log.error("系统将使用内存存储作为降级方案");
            log.error("========================================");
            log.error("请检查：");
            log.error("1. Redis服务是否已启动");
            log.error("2. Redis配置是否正确（localhost:6379）");
            log.error("3. 防火墙是否阻止了连接");
            log.error("========================================");
        }
    }
}
