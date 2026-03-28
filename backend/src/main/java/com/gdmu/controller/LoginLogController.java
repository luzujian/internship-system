package com.gdmu.controller;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.gdmu.mapper.LoginLogMapper;
import com.gdmu.entity.LoginLog;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 登录日志控制器
 */
@RestController
@RequestMapping("/api/login-logs")
public class LoginLogController {
    
    private static final Logger log = LoggerFactory.getLogger(LoginLogController.class);
    
    @Autowired
    private LoginLogMapper loginLogMapper;
    
    /**
     * 查询登录日志（分页）
     * @param page 页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result queryLoginLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        try {
            log.info("查询登录日志，页码: {}, 每页条数: {}", page, pageSize);
            
            // 使用PageHelper进行分页
            PageHelper.startPage(page, pageSize);
            List<LoginLog> logs = loginLogMapper.selectAll();
            
            // 封装分页结果
            PageInfo<LoginLog> pageInfo = new PageInfo<>(logs);
            
            PageResult<LoginLog> pageResult = new PageResult<>();
            pageResult.setRows(pageInfo.getList());
            pageResult.setTotal(pageInfo.getTotal());
            pageResult.setPages(pageInfo.getPages());
            pageResult.setCurrent(pageInfo.getPageNum());
            pageResult.setPageSize(pageInfo.getPageSize());
            
            return Result.success(pageResult);
        } catch (Exception e) {
            log.error("查询登录日志失败: {}", e.getMessage(), e);
            return Result.error("查询登录日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户ID查询登录日志
     * @param userId 用户ID
     * @return 登录日志列表
     */
    @GetMapping("/user")
    @PreAuthorize("hasRole('ADMIN')")
    public Result queryLoginLogsByUserId(@RequestParam String userId) {
        try {
            log.info("根据用户ID查询登录日志，用户ID: {}", userId);
            List<LoginLog> logs = loginLogMapper.selectByUserId(userId);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("根据用户ID查询登录日志失败: {}", e.getMessage(), e);
            return Result.error("查询登录日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 根据用户类型查询登录日志
     * @param userType 用户类型
     * @return 登录日志列表
     */
    @GetMapping("/type")
    @PreAuthorize("hasRole('ADMIN')")
    public Result queryLoginLogsByUserType(@RequestParam String userType) {
        try {
            log.info("根据用户类型查询登录日志，用户类型: {}", userType);
            List<LoginLog> logs = loginLogMapper.selectByUserType(userType);
            return Result.success(logs);
        } catch (Exception e) {
            log.error("根据用户类型查询登录日志失败: {}", e.getMessage(), e);
            return Result.error("查询登录日志失败: " + e.getMessage());
        }
    }
    
    /**
     * 查询登录日志总数
     * @return 登录日志总数
     */
    @GetMapping("/count")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getLoginLogCount() {
        try {
            int count = loginLogMapper.countAll();
            return Result.success(count);
        } catch (Exception e) {
            log.error("查询登录日志总数失败: {}", e.getMessage(), e);
            return Result.error("查询登录日志总数失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取用户活跃度统计数据
     * @param days 统计天数，默认7天
     * @param startDate 开始日期，格式: yyyy-MM-dd
     * @param endDate 结束日期，格式: yyyy-MM-dd
     * @return 活跃度统计数据
     */
    @GetMapping("/active-users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getActiveUsersStats(
            @RequestParam(required = false) Integer days,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date startDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") Date endDate) {
        try {
            log.info("获取用户活跃度统计数据，天数: {}, 开始日期: {}, 结束日期: {}", days, startDate, endDate);
            
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            
            if (startDate == null || endDate == null) {
                if (days == null || days <= 0) {
                    days = 7;
                }
                endDate = new Date();
                Calendar cal = Calendar.getInstance();
                cal.setTime(endDate);
                cal.add(Calendar.DAY_OF_MONTH, -(days - 1));
                startDate = cal.getTime();
            } else {
                Calendar startCal = Calendar.getInstance();
                startCal.setTime(startDate);
                startCal.set(Calendar.HOUR_OF_DAY, 0);
                startCal.set(Calendar.MINUTE, 0);
                startCal.set(Calendar.SECOND, 0);
                startCal.set(Calendar.MILLISECOND, 0);
                startDate = startCal.getTime();
                
                Calendar endCal = Calendar.getInstance();
                endCal.setTime(endDate);
                endCal.set(Calendar.HOUR_OF_DAY, 23);
                endCal.set(Calendar.MINUTE, 59);
                endCal.set(Calendar.SECOND, 59);
                endCal.set(Calendar.MILLISECOND, 999);
                endDate = endCal.getTime();
                
                long diffTime = endDate.getTime() - startDate.getTime();
                long diffDays = diffTime / (1000 * 60 * 60 * 24) + 1;
                
                if (diffDays > 365) {
                    return Result.error("日期范围不能超过365天");
                }
            }
            
            List<LoginLog> logs = loginLogMapper.selectByDateRange(startDate, endDate);
            
            List<String> dateList = new ArrayList<>();
            Calendar cal = Calendar.getInstance();
            cal.setTime(startDate);
            while (!cal.getTime().after(endDate)) {
                dateList.add(dateFormat.format(cal.getTime()));
                cal.add(Calendar.DAY_OF_MONTH, 1);
            }
            
            Map<String, Map<String, Set<String>>> dailyActiveUsers = new HashMap<>();
            for (String date : dateList) {
                Map<String, Set<String>> userSets = new HashMap<>();
                userSets.put("student", new HashSet<>());
                userSets.put("teacher", new HashSet<>());
                userSets.put("admin", new HashSet<>());
                dailyActiveUsers.put(date, userSets);
            }
            
            for (LoginLog log : logs) {
                if (log.getLoginTime() == null) {
                    continue;
                }
                
                String logDate = dateFormat.format(log.getLoginTime());
                if (dailyActiveUsers.containsKey(logDate)) {
                    String userType = log.getUserType();
                    if (userType == null || userType.trim().isEmpty()) {
                        continue;
                    }
                    
                    String normalizedType = userType.toUpperCase().trim();
                    String roleType = "student";
                    
                    if (normalizedType.contains("TEACHER") || normalizedType.contains("INSTRUCTOR")) {
                        roleType = "teacher";
                    } else if (normalizedType.contains("ADMIN") || normalizedType.contains("ADMINISTRATOR")) {
                        roleType = "admin";
                    } else if (normalizedType.contains("STUDENT")) {
                        roleType = "student";
                    }
                    
                    String userId = log.getUserId();
                    if (userId != null && !userId.trim().isEmpty()) {
                        dailyActiveUsers.get(logDate).get(roleType).add(userId);
                    }
                }
            }
            
            List<Map<String, Object>> studentData = new ArrayList<>();
            List<Map<String, Object>> teacherData = new ArrayList<>();
            List<Map<String, Object>> adminData = new ArrayList<>();
            
            for (String date : dateList) {
                Map<String, Set<String>> userSets = dailyActiveUsers.get(date);
                
                Map<String, Object> studentItem = new HashMap<>();
                studentItem.put("date", date.substring(5));
                studentItem.put("count", userSets.get("student").size());
                studentData.add(studentItem);
                
                Map<String, Object> teacherItem = new HashMap<>();
                teacherItem.put("date", date.substring(5));
                teacherItem.put("count", userSets.get("teacher").size());
                teacherData.add(teacherItem);
                
                Map<String, Object> adminItem = new HashMap<>();
                adminItem.put("date", date.substring(5));
                adminItem.put("count", userSets.get("admin").size());
                adminData.add(adminItem);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("studentData", studentData);
            result.put("teacherData", teacherData);
            result.put("adminData", adminData);
            result.put("dateRange", dateFormat.format(startDate) + " 至 " + dateFormat.format(endDate));
            
            log.info("用户活跃度统计数据获取成功，日期范围: {}, 学生数据: {}, 教师数据: {}, 管理员数据: {}", 
                result.get("dateRange"), studentData.size(), teacherData.size(), adminData.size());
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取用户活跃度统计数据失败: {}", e.getMessage(), e);
            return Result.error("获取用户活跃度统计数据失败: " + e.getMessage());
        }
    }
}