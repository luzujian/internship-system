package com.gdmu.controller;

import com.gdmu.entity.*;
import com.gdmu.mapper.*;
import com.gdmu.utils.MapUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

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
        try {
            log.debug("开始获取实习状态看板统计数据，时间范围：{} - {}", startDate, endDate);
            Map<String, Object> result = new HashMap<>();

            Map<String, Object> dashboardStats = internshipStatusMapper.getDashboardStats(startDate, endDate);
            if (dashboardStats == null) {
                log.warn("看板统计数据为空，使用默认值");
                dashboardStats = new HashMap<>();
                dashboardStats.put("totalStudents", 0);
                dashboardStats.put("confirmed", 0);
                dashboardStats.put("offer", 0);
                dashboardStats.put("noOffer", 0);
                dashboardStats.put("delay", 0);
            }

            result.put("totalStudents", dashboardStats.get("totalStudents"));
            result.put("confirmed", dashboardStats.get("confirmed"));
            result.put("offer", dashboardStats.get("offer"));
            result.put("noOffer", dashboardStats.get("noOffer"));
            result.put("delay", dashboardStats.get("delay"));

            List<Map<String, Object>> gradeData = internshipStatusMapper.getStatsByGrade(startDate, endDate);
            result.put("gradeData", gradeData != null ? gradeData : new ArrayList<>());

            List<Map<String, Object>> majorData = internshipStatusMapper.getStatsByMajor(startDate, endDate);
            result.put("majorData", majorData != null ? majorData : new ArrayList<>());

            List<Map<String, Object>> classData = internshipStatusMapper.getStatsByClass(startDate, endDate);
            result.put("classData", classData != null ? classData : new ArrayList<>());

            log.debug("实习状态看板统计数据获取成功");
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取实习状态看板统计数据失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("totalStudents", 0);
            errorResult.put("confirmed", 0);
            errorResult.put("offer", 0);
            errorResult.put("noOffer", 0);
            errorResult.put("delay", 0);
            errorResult.put("gradeData", new ArrayList<>());
            errorResult.put("majorData", new ArrayList<>());
            errorResult.put("classData", new ArrayList<>());
            return Result.success(errorResult);
        }
    }
}
    

