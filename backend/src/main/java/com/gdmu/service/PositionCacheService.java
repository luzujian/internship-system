package com.gdmu.service;

import com.gdmu.entity.dto.PositionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.List;

/**
 * 职位相关缓存服务
 */
@Slf4j
@Service
public class PositionCacheService {

    private static final String POSITIONS_ALL_KEY = "positions:all";
    private static final String POSITIONS_INDUSTRIES_KEY = "positions:industries";
    private static final String POSITIONS_COMPANIES_KEY = "positions:companies";
    private static final String POSITIONS_REGIONS_KEY = "positions:regions";

    // 缓存过期时间：30分钟
    private static final long CACHE_EXPIRE_MINUTES = 30;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取缓存的职位列表
     */
    @SuppressWarnings("unchecked")
    public List<PositionVO> getCachedPositions() {
        try {
            return (List<PositionVO>) redisTemplate.opsForValue().get(POSITIONS_ALL_KEY);
        } catch (Exception e) {
            log.warn("获取职位缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置职位列表缓存
     */
    public void cachePositions(List<PositionVO> positions) {
        try {
            redisTemplate.opsForValue().set(POSITIONS_ALL_KEY, positions, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("职位列表缓存已更新，数量: {}", positions.size());
        } catch (Exception e) {
            log.warn("设置职位缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存的行业选项
     */
    @SuppressWarnings("unchecked")
    public List<Object> getCachedIndustries() {
        try {
            return (List<Object>) redisTemplate.opsForValue().get(POSITIONS_INDUSTRIES_KEY);
        } catch (Exception e) {
            log.warn("获取行业缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置行业选项缓存
     */
    public void cacheIndustries(List<Object> industries) {
        try {
            redisTemplate.opsForValue().set(POSITIONS_INDUSTRIES_KEY, industries, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("行业选项缓存已更新");
        } catch (Exception e) {
            log.warn("设置行业缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存的公司选项
     */
    @SuppressWarnings("unchecked")
    public List<Object> getCachedCompanies() {
        try {
            return (List<Object>) redisTemplate.opsForValue().get(POSITIONS_COMPANIES_KEY);
        } catch (Exception e) {
            log.warn("获取公司缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置公司选项缓存
     */
    public void cacheCompanies(List<Object> companies) {
        try {
            redisTemplate.opsForValue().set(POSITIONS_COMPANIES_KEY, companies, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("公司选项缓存已更新");
        } catch (Exception e) {
            log.warn("设置公司缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 获取缓存的地区选项
     */
    @SuppressWarnings("unchecked")
    public List<Object> getCachedRegions() {
        try {
            return (List<Object>) redisTemplate.opsForValue().get(POSITIONS_REGIONS_KEY);
        } catch (Exception e) {
            log.warn("获取地区缓存失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 设置地区选项缓存
     */
    public void cacheRegions(List<Object> regions) {
        try {
            redisTemplate.opsForValue().set(POSITIONS_REGIONS_KEY, regions, CACHE_EXPIRE_MINUTES, TimeUnit.MINUTES);
            log.debug("地区选项缓存已更新");
        } catch (Exception e) {
            log.warn("设置地区缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 清除所有职位相关缓存
     */
    public void clearAllCaches() {
        try {
            redisTemplate.delete(POSITIONS_ALL_KEY);
            redisTemplate.delete(POSITIONS_INDUSTRIES_KEY);
            redisTemplate.delete(POSITIONS_COMPANIES_KEY);
            redisTemplate.delete(POSITIONS_REGIONS_KEY);
            log.info("所有职位相关缓存已清除");
        } catch (Exception e) {
            log.warn("清除缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 清除职位列表缓存（数据更新时调用）
     */
    public void clearPositionsCache() {
        try {
            redisTemplate.delete(POSITIONS_ALL_KEY);
            log.debug("职位列表缓存已清除");
        } catch (Exception e) {
            log.warn("清除职位缓存失败: {}", e.getMessage());
        }
    }
}
