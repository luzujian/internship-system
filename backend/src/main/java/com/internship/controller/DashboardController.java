package com.internship.controller;

import com.internship.entity.*;
import com.internship.mapper.*;
import com.internship.utils.MapUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 数据看板控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/dashboard")
public class DashboardController {

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private CompanyUserMapper companyUserMapper;
    
    @Autowired(required = false)
    private TeacherUserMapper teacherUserMapper;
    
    @Autowired(required = false)
    private MajorMapper majorMapper;
    
    @Autowired(required = false)
    private PositionMapper positionMapper;
    
    @Autowired(required = false)
    private DepartmentMapper departmentMapper;

    @Autowired
    private StudentInternshipStatusMapper internshipStatusMapper;

    @Autowired(required = false)
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 获取系统统计数据（核心看板数据）
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getInternshipStats() {
        log.info("获取系统统计数据");
        try {
            Map<String, Object> stats = MapUtils.builder();

            Long studentCountLong = studentUserMapper.count();
            stats.put("totalStudents", studentCountLong != null ? studentCountLong.intValue() : 0);

            Long teacherCountLong = teacherUserMapper != null ? teacherUserMapper.count() : null;
            stats.put("totalTeachers", teacherCountLong != null ? teacherCountLong.intValue() : 0);

            Long companyCountLong = companyUserMapper.count();
            stats.put("totalCompanies", companyCountLong != null ? companyCountLong.intValue() : 0);

            Long departmentCountLong = departmentMapper != null ? departmentMapper.count() : null;
            stats.put("totalDepartments", departmentCountLong != null ? departmentCountLong.intValue() : 0);

            Long majorCountLong = majorMapper != null ? majorMapper.count() : null;
            stats.put("totalMajors", majorCountLong != null ? majorCountLong.intValue() : 0);

            Long positionCountLong = positionMapper != null ? positionMapper.count() : null;
            stats.put("totalPositions", positionCountLong != null ? positionCountLong.intValue() : 0);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取系统统计数据失败: {}", e.getMessage());
            return Result.success(getDefaultStats());
        }
    }

    private Map<String, Object> getDefaultStats() {
        Map<String, Object> defaultStats = MapUtils.builder();
        defaultStats.put("totalStudents", 0);
        defaultStats.put("totalTeachers", 0);
        defaultStats.put("totalCompanies", 0);
        defaultStats.put("totalDepartments", 0);
        defaultStats.put("totalMajors", 0);
        defaultStats.put("totalPositions", 0);
        return defaultStats;
    }

    /**
     * 获取实习状态看板统计数据
     */
    @GetMapping("/internship-stats")
    public Result getInternshipDashboardStats(@RequestParam(required = false) String startDate,
                                               @RequestParam(required = false) String endDate) {
        // Redis 缓存，30 秒 TTL，防止高并发下重复聚合查询
        String cacheKey = "dashboard:istats:" + (startDate != null ? startDate : "_") +
            ":" + (endDate != null ? endDate : "_");
        if (redisTemplate != null) {
            try {
                Object cached = redisTemplate.opsForValue().get(cacheKey);
                if (cached instanceof Map) return Result.success(cached);
            } catch (Exception ignored) {}
        }
        try {
            Map<String, Object> result = new HashMap<>();
            Map<String, Object> dashboardStats = internshipStatusMapper.getDashboardStats(startDate, endDate);
            if (dashboardStats == null) { dashboardStats = new HashMap<>(); }
            result.put("totalStudents", dashboardStats.getOrDefault("totalStudents", 0));
            result.put("confirmed", dashboardStats.getOrDefault("confirmed", 0));
            result.put("offer", dashboardStats.getOrDefault("offer", 0));
            result.put("noOffer", dashboardStats.getOrDefault("noOffer", 0));
            result.put("delay", dashboardStats.getOrDefault("delay", 0));
            result.put("gradeData", internshipStatusMapper.getStatsByGrade(startDate, endDate));
            result.put("majorData", internshipStatusMapper.getStatsByMajor(startDate, endDate));
            result.put("classData", internshipStatusMapper.getStatsByClass(startDate, endDate));
            if (redisTemplate != null) {
                try { redisTemplate.opsForValue().set(cacheKey, result, 30, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取实习状态看板统计数据失败", e);
            return Result.success(Map.of(
                "totalStudents", 0, "confirmed", 0, "offer", 0, "noOffer", 0, "delay", 0,
                "gradeData", List.of(), "majorData", List.of(), "classData", List.of()
            ));
        }
    }
}
    

