package com.gdmu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdmu.entity.AdminUser;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.StudentUser;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 用户缓存服务
 * 使用 Redis 缓存用户信息，支持分布式部署
 *
 * Redis 不可用时自动降级到内存存储，保证系统可用性
 */
@Slf4j
@Service
public class UserCacheService {

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // 内存降级存储（Redis 不可用时使用）
    private final ConcurrentHashMap<String, Object> userCacheMemory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> cacheTimestampMemory = new ConcurrentHashMap<>();

    // 缓存 TTL（5 分钟）
    private static final long CACHE_TTL = 5 * 60 * 1000;
    private static final String CACHE_PREFIX = "cache:user:";

    /**
     * 检查 Redis 是否可用
     */
    private boolean isRedisAvailable() {
        try {
            if (redisTemplate == null) {
                return false;
            }
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.debug("Redis 不可用，使用内存存储：{}", e.getMessage());
            return false;
        }
    }

    // ==================== 普通用户缓存 ====================

    /**
     * 获取缓存的用户
     * @param username 用户名
     * @return 用户对象，如果不存在或已过期返回 null
     */
    public User getUser(String username) {
        String cacheKey = CACHE_PREFIX + "user:" + username;
        return getUserByType(cacheKey, User.class);
    }

    /**
     * 缓存用户
     * @param username 用户名
     * @param user 用户对象
     */
    public void cacheUser(String username, User user) {
        String cacheKey = CACHE_PREFIX + "user:" + username;
        cacheObject(cacheKey, user);
    }

    // ==================== 管理员用户缓存 ====================

    /**
     * 获取缓存的管理员用户
     * @param username 用户名
     * @return 管理员用户对象
     */
    public AdminUser getAdminUser(String username) {
        String cacheKey = CACHE_PREFIX + "admin:" + username;
        return getUserByType(cacheKey, AdminUser.class);
    }

    /**
     * 缓存管理员用户
     * @param username 用户名
     * @param adminUser 管理员用户对象
     */
    public void cacheAdminUser(String username, AdminUser adminUser) {
        String cacheKey = CACHE_PREFIX + "admin:" + username;
        cacheObject(cacheKey, adminUser);
    }

    // ==================== 教师用户缓存 ====================

    /**
     * 获取缓存的教师用户
     * @param username 用户名
     * @return 教师用户对象
     */
    public TeacherUser getTeacherUser(String username) {
        String cacheKey = CACHE_PREFIX + "teacher:" + username;
        return getUserByType(cacheKey, TeacherUser.class);
    }

    /**
     * 缓存教师用户
     * @param username 用户名
     * @param teacherUser 教师用户对象
     */
    public void cacheTeacherUser(String username, TeacherUser teacherUser) {
        String cacheKey = CACHE_PREFIX + "teacher:" + username;
        cacheObject(cacheKey, teacherUser);
    }

    // ==================== 学生用户缓存 ====================

    /**
     * 获取缓存的学生用户
     * @param username 用户名
     * @return 学生用户对象
     */
    public StudentUser getStudentUser(String username) {
        String cacheKey = CACHE_PREFIX + "student:" + username;
        return getUserByType(cacheKey, StudentUser.class);
    }

    /**
     * 缓存学生用户
     * @param username 用户名
     * @param studentUser 学生用户对象
     */
    public void cacheStudentUser(String username, StudentUser studentUser) {
        String cacheKey = CACHE_PREFIX + "student:" + username;
        cacheObject(cacheKey, studentUser);
    }

    // ==================== 企业用户缓存 ====================

    /**
     * 获取缓存的企业用户
     * @param username 用户名
     * @return 企业用户对象
     */
    public CompanyUser getCompanyUser(String username) {
        String cacheKey = CACHE_PREFIX + "company:" + username;
        return getUserByType(cacheKey, CompanyUser.class);
    }

    /**
     * 缓存企业用户
     * @param username 用户名
     * @param companyUser 企业用户对象
     */
    public void cacheCompanyUser(String username, CompanyUser companyUser) {
        String cacheKey = CACHE_PREFIX + "company:" + username;
        cacheObject(cacheKey, companyUser);
    }

    // ==================== 通用缓存方法 ====================

    /**
     * 根据类型获取缓存对象
     */
    @SuppressWarnings("unchecked")
    private <T> T getUserByType(String cacheKey, Class<T> clazz) {
        if (isRedisAvailable()) {
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached != null) {
                    // 如果是 String 类型（JSON 序列化），需要反序列化
                    if (cached instanceof String) {
                        return objectMapper.readValue((String) cached, clazz);
                    }
                    return (T) cached;
                }
                return null;
            } catch (Exception e) {
                log.error("Redis 缓存查询失败，降级到内存：{}", e.getMessage());
                return getUserByTypeMemory(cacheKey, clazz);
            }
        } else {
            return getUserByTypeMemory(cacheKey, clazz);
        }
    }

    /**
     * 从内存获取缓存
     */
    @SuppressWarnings("unchecked")
    private <T> T getUserByTypeMemory(String cacheKey, Class<T> clazz) {
        Object cached = userCacheMemory.get(cacheKey);
        if (cached == null) {
            return null;
        }
        // 检查缓存是否过期
        Long timestamp = cacheTimestampMemory.get(cacheKey);
        if (timestamp == null || System.currentTimeMillis() - timestamp > CACHE_TTL) {
            userCacheMemory.remove(cacheKey);
            cacheTimestampMemory.remove(cacheKey);
            return null;
        }
        return (T) cached;
    }

    /**
     * 缓存对象
     */
    private void cacheObject(String cacheKey, Object obj) {
        if (isRedisAvailable()) {
            try {
                // 使用 JSON 序列化存储
                String json = objectMapper.writeValueAsString(obj);
                redisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL, TimeUnit.MILLISECONDS);
                log.debug("对象已缓存到 Redis: {}", cacheKey);
                return;
            } catch (Exception e) {
                log.error("Redis 缓存失败，降级到内存：{}", e.getMessage());
            }
        }
        // 降级到内存
        userCacheMemory.put(cacheKey, obj);
        cacheTimestampMemory.put(cacheKey, System.currentTimeMillis());
        log.debug("对象已缓存到内存：{}", cacheKey);
    }

    // ==================== 缓存清理 ====================

    /**
     * 清除用户的缓存
     * @param username 用户名
     */
    public void evictUserCache(String username) {
        if (isRedisAvailable()) {
            try {
                redisTemplate.delete(CACHE_PREFIX + "user:" + username);
                redisTemplate.delete(CACHE_PREFIX + "admin:" + username);
                redisTemplate.delete(CACHE_PREFIX + "teacher:" + username);
                redisTemplate.delete(CACHE_PREFIX + "student:" + username);
                redisTemplate.delete(CACHE_PREFIX + "company:" + username);
                log.debug("已删除 Redis 中用户 {} 的所有缓存", username);
            } catch (Exception e) {
                log.error("Redis 缓存删除失败：{}", e.getMessage());
            }
        }
        // 清理内存缓存
        evictUserCacheMemory(username);
    }

    /**
     * 清除内存中的用户缓存
     */
    private void evictUserCacheMemory(String username) {
        userCacheMemory.remove(CACHE_PREFIX + "user:" + username);
        userCacheMemory.remove(CACHE_PREFIX + "admin:" + username);
        userCacheMemory.remove(CACHE_PREFIX + "teacher:" + username);
        userCacheMemory.remove(CACHE_PREFIX + "student:" + username);
        userCacheMemory.remove(CACHE_PREFIX + "company:" + username);

        cacheTimestampMemory.remove(CACHE_PREFIX + "user:" + username);
        cacheTimestampMemory.remove(CACHE_PREFIX + "admin:" + username);
        cacheTimestampMemory.remove(CACHE_PREFIX + "teacher:" + username);
        cacheTimestampMemory.remove(CACHE_PREFIX + "student:" + username);
        cacheTimestampMemory.remove(CACHE_PREFIX + "company:" + username);
    }

    /**
     * 获取缓存统计信息
     * @return 缓存统计信息
     */
    public CacheStats getCacheStats() {
        CacheStats stats = new CacheStats();
        if (isRedisAvailable()) {
            stats.setUsingRedis(true);
            stats.setMemorySize(0);
            // Redis 缓存大小需要通过 SCAN 命令获取，这里简化处理
            stats.setRedisSize("active");
        } else {
            stats.setUsingRedis(false);
            stats.setMemorySize(userCacheMemory.size());
        }
        return stats;
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        private boolean usingRedis;
        private int memorySize;
        private String redisSize;

        public boolean isUsingRedis() { return usingRedis; }
        public void setUsingRedis(boolean usingRedis) { this.usingRedis = usingRedis; }
        public int getMemorySize() { return memorySize; }
        public void setMemorySize(int memorySize) { this.memorySize = memorySize; }
        public String getRedisSize() { return redisSize; }
        public void setRedisSize(String redisSize) { this.redisSize = redisSize; }
    }
}
