package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.mapper.OperateLogMapper;
import com.gdmu.mapper.LoginLogMapper;
import com.gdmu.entity.OperateLog;
import com.gdmu.entity.LoginLog;
import com.gdmu.entity.User;
import com.gdmu.entity.StudentUser;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.AdminUser;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.Permission;
import com.gdmu.entity.Result;
import com.gdmu.exception.BusinessException;
import com.gdmu.service.UserService;
import com.gdmu.service.StudentUserService;
import com.gdmu.service.TeacherUserService;
import com.gdmu.service.AdminUserService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.PermissionService;
import com.gdmu.service.AdminPermissionService;
import com.gdmu.utils.JwtUtils;
import com.gdmu.utils.PasswordValidator;
import com.gdmu.config.SystemSettingsConfig;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private AuthenticationManager authenticationManager;
    
    @Autowired
    private ApplicationContext applicationContext;
    
    @Autowired
    private OperateLogMapper operateLogMapper;
    
    @Autowired
    private LoginLogMapper loginLogMapper;
    
    @Autowired
    private TeacherUserService teacherUserService;
    
    @Autowired
    private StudentUserService studentUserService;
    
    @Autowired
    private AdminUserService adminUserService;
    
    @Autowired
    private CompanyUserService companyUserService;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private PermissionService permissionService;
    
    private static final Map<String, LoginAttemptInfo> LOGIN_ATTEMPT_CACHE = new ConcurrentHashMap<>();
    
    private static class LoginAttemptInfo {
        private int attemptCount;
        private long lockUntil;
        
        public LoginAttemptInfo() {
            this.attemptCount = 0;
            this.lockUntil = 0;
        }
        
        public boolean isLocked() {
            return System.currentTimeMillis() < lockUntil;
        }
        
        public long getRemainingLockTime() {
            long remaining = lockUntil - System.currentTimeMillis();
            return remaining > 0 ? remaining / 1000 : 0;
        }
    }

    @PostMapping("/login")
    public Result login(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        String username = (String) loginRequest.get("username");
        String password = (String) loginRequest.get("password");
        String userType = (String) loginRequest.get("userType");
        
        log.info("用户登录请求: username={}, userType={}", username, userType);
        
        if (username == null || username.isEmpty()) {
            return Result.error("用户名不能为空");
        }
        
        if (password == null || password.isEmpty()) {
            return Result.error("密码不能为空");
        }
        
        if (userType == null || userType.isEmpty()) {
            return Result.error("用户类型不能为空");
        }
        
        if (!SystemSettingsConfig.isSystemEnabled()) {
            if (!"admin".equals(userType)) {
                log.warn("系统维护中，拒绝非管理员用户登录: username={}, userType={}", username, userType);
                return Result.error("系统维护中，暂时无法登录，请稍后再试");
            }
            log.info("系统维护中，允许管理员登录: username={}", username);
        }
        
        String cacheKey = username + ":" + userType;
        LoginAttemptInfo attemptInfo = LOGIN_ATTEMPT_CACHE.get(cacheKey);
        
        if (attemptInfo == null) {
            attemptInfo = new LoginAttemptInfo();
            LOGIN_ATTEMPT_CACHE.put(cacheKey, attemptInfo);
        }
        
        if (attemptInfo.isLocked()) {
            long remainingTime = attemptInfo.getRemainingLockTime();
            log.warn("用户账号已被锁定: username={}, userType={}, 剩余锁定时间={}秒", username, userType, remainingTime);
            return Result.error("账号已被锁定，请" + remainingTime + "秒后再试");
        }
        
        try {
            Map<String, Object> result = new HashMap<>();
            
            if ("teacher".equals(userType)) {
                result = handleTeacherLogin(username, password, request);
            } else {
                result = handleOtherUserLogin(username, password, userType, request);
            }
            
            LOGIN_ATTEMPT_CACHE.remove(cacheKey);
            log.info("用户登录成功，清除登录失败记录: username={}", username);
            
            return Result.success(result);
        } catch (AuthenticationException e) {
            log.error("用户登录失败: {}, 错误信息: {}", username, e.getMessage());
            
            attemptInfo.attemptCount++;
            
            int maxAttempts = SystemSettingsConfig.getMaxLoginAttempts();
            int lockTime = SystemSettingsConfig.getLockTime();
            
            if (attemptInfo.attemptCount >= maxAttempts) {
                attemptInfo.lockUntil = System.currentTimeMillis() + (lockTime * 60 * 1000L);
                log.warn("用户账号已被锁定: username={}, userType={}, 锁定时间={}分钟", username, userType, lockTime);
            }
            
            try {
                LoginLog loginLog = new LoginLog();
                loginLog.setUserId(username);
                loginLog.setUserType(userType.toUpperCase());
                loginLog.setUserName(username);
                loginLog.setLoginTime(new Date());
                loginLog.setIpAddress(request.getRemoteAddr());
                loginLog.setDeviceInfo(request.getHeader("User-Agent"));
                loginLog.setLoginStatus("FAILURE");
                
                loginLogMapper.insert(loginLog);
                log.info("用户登录失败日志记录成功: {}", loginLog);
            } catch (Exception ex) {
                log.error("记录用户登录失败日志失败: {}", ex.getMessage());
            }
            
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("账号已被禁用")) {
                return Result.error("您的账号已被禁用，请联系管理员");
            }
            
            if (attemptInfo.isLocked()) {
                return Result.error("登录失败次数过多，账号已被锁定" + lockTime + "分钟");
            }
            
            int remainingAttempts = maxAttempts - attemptInfo.attemptCount;
            return Result.error("用户名或密码错误，剩余尝试次数: " + remainingAttempts);
        } catch (Exception e) {
            log.error("登录过程中发生异常: {}", e.getMessage(), e);
            return Result.error("登录失败: " + e.getMessage());
        }
    }
    
    private Map<String, Object> handleTeacherLogin(String username, String password, HttpServletRequest request) {
        log.info("处理教师登录: 教师编号={}", username);
        
        TeacherUser teacher = teacherUserService.findByTeacherUserId(username);
        if (teacher == null) {
            log.warn("教师登录失败: 教师编号不存在 - {}", username);
            throw new AuthenticationException("教师编号或密码错误") {};
        }

        if (!passwordEncoder.matches(password, teacher.getPassword())) {
            log.warn("教师登录失败: 密码错误 - {}", username);
            throw new AuthenticationException("教师编号或密码错误") {};
        }

        if (teacher.getStatus() == 0) {
            log.warn("教师登录失败: 账号已被禁用 - {}", username);
            throw new AuthenticationException("账号已被禁用") {};
        }

        String teacherType = teacher.getTeacherType();
        if (teacherType == null || teacherType.isEmpty()) {
            teacherType = identifyTeacherType(teacher);
            teacher.setTeacherType(teacherType);
            teacherUserService.update(teacher);
            log.info("自动识别教师类型: {} -> {}", username, teacherType);
        }

        String role = getRoleByTeacherType(teacherType);
        teacher.setRole(role);

        List<Permission> permissions = permissionService.getPermissionsByTeacherType(teacherType);
        List<String> permissionCodes = permissions.stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toList());

        log.info("教师 {} (类型: {}) 的权限列表: {}", username, teacherType, permissionCodes);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", teacher.getId());
        claims.put("username", teacher.getTeacherUserId());
        claims.put("role", role);
        claims.put("teacherType", teacherType);
        claims.put("permissions", permissionCodes);

        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);

        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userInfo", teacher);
        result.put("permissions", permissionCodes);
        
        log.info("教师登录成功: {}, 类型: {}, 权限数: {}", username, teacherType, permissionCodes.size());
        
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(String.valueOf(teacher.getId()));
            loginLog.setUserType(role);
            loginLog.setUserName(teacher.getTeacherUserId());
            loginLog.setLoginTime(new Date());
            loginLog.setIpAddress(request.getRemoteAddr());
            loginLog.setDeviceInfo(request.getHeader("User-Agent"));
            loginLog.setLoginStatus("SUCCESS");
            
            loginLogMapper.insert(loginLog);
            log.info("教师登录日志记录成功: {}", loginLog);
        } catch (Exception e) {
            log.error("记录教师登录日志失败: {}", e.getMessage());
        }
        
        try {
            userService.clearAllUserDetailsCache();
            log.info("教师登录成功，已清除UserDetails缓存");
        } catch (Exception e) {
            log.warn("清除UserDetails缓存失败: {}", e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, Object> handleOtherUserLogin(String username, String password, String userType, HttpServletRequest request) {
        log.info("处理非教师登录: username={}, userType={}", username, userType);

        // 在认证之前先检查用户状态
        User preCheckUser = userService.findByUsername(username);
        if (preCheckUser != null) {
            Integer status = 1;
            String role = preCheckUser.getRole();
            if (role != null && role.startsWith("ROLE_TEACHER")) {
                TeacherUser teacherUser = teacherUserService.findByTeacherUserId(username);
                if (teacherUser != null && teacherUser.getStatus() != null) {
                    status = teacherUser.getStatus();
                }
            } else if ("ROLE_STUDENT".equals(role)) {
                StudentUser studentUser = studentUserService.findByStudentId(username);
                if (studentUser != null && studentUser.getStatus() != null) {
                    status = studentUser.getStatus();
                }
            } else if ("ROLE_COMPANY".equals(role)) {
                var companyUser = companyUserService.findById(preCheckUser.getId());
                if (companyUser != null && companyUser.getStatus() != null) {
                    status = companyUser.getStatus();
                }
            } else if ("ROLE_ADMIN".equals(role)) {
                AdminUser adminUser = adminUserService.findByUsername(username);
                if (adminUser != null && adminUser.getStatus() != null) {
                    status = adminUser.getStatus();
                }
            }

            if (status == 0) {
                log.warn("用户登录失败: 账号已被禁用 - {}", username);
                throw new AuthenticationException("账号已被禁用") {};
            }
        }

        // 直接使用 passwordEncoder 进行密码验证，避免 DelegatingPasswordEncoder 问题
        User loginUser = userService.findByUsername(username);
        if (loginUser == null || loginUser.getPassword() == null) {
            log.warn("用户不存在或密码为空：{}", username);
            throw new AuthenticationException("用户名或密码错误") {};
        }

        // 使用 CustomPasswordEncoder 的 matches 方法进行密码验证
        if (!passwordEncoder.matches(password, loginUser.getPassword())) {
            log.warn("密码验证失败：{}", username);
            throw new AuthenticationException("用户名或密码错误") {};
        }

        log.info("密码验证成功：{}", username);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", loginUser.getId());
        claims.put("username", loginUser.getUsername());
        claims.put("role", loginUser.getRole());
        
        String displayName = loginUser.getName();
        if ("ROLE_COMPANY".equals(loginUser.getRole())) {
            var companyUser = companyUserService.findById(loginUser.getId());
            if (companyUser != null) {
                displayName = companyUser.getCompanyName();
            }
        }
        claims.put("name", displayName);
        
        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);

        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        
        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", loginUser.getId());
        safeUser.put("username", loginUser.getUsername());
        safeUser.put("role", loginUser.getRole());
        safeUser.put("name", displayName);
        safeUser.put("createTime", loginUser.getCreateTime());
        
        if ("ROLE_COMPANY".equals(loginUser.getRole())) {
            var companyUser = companyUserService.findById(loginUser.getId());
            if (companyUser != null) {
                safeUser.put("name", companyUser.getCompanyName());
            }
        }
        
        result.put("user", safeUser);
        
        if ("ROLE_ADMIN".equals(loginUser.getRole())) {
            try {
                AdminPermissionService adminPermissionService = applicationContext.getBean(AdminPermissionService.class);
                List<Permission> permissions = adminPermissionService.getRolePermissionsByCode(loginUser.getRole());
                result.put("permissions", permissions);
                log.info("管理员登录成功，已返回权限信息: {} 个权限", permissions.size());
            } catch (Exception e) {
                log.warn("获取管理员权限失败: {}", e.getMessage());
            }
        }
        
        log.info("用户登录成功: {}", username);
        
        try {
            LoginLog loginLog = new LoginLog();
            String userId = loginUser.getId() != null ? String.valueOf(loginUser.getId()) : loginUser.getUsername();
            loginLog.setUserId(userId);
            loginLog.setUserType(loginUser.getRole());
            loginLog.setUserName(loginUser.getUsername());
            loginLog.setLoginTime(new Date());
            loginLog.setIpAddress(request.getRemoteAddr());
            loginLog.setDeviceInfo(request.getHeader("User-Agent"));
            loginLog.setLoginStatus("SUCCESS");
            
            loginLogMapper.insert(loginLog);
            log.info("用户登录日志记录成功: {}", loginLog);
        } catch (Exception e) {
            log.error("记录用户登录日志失败: {}", e.getMessage());
        }
        
        if ("ROLE_ADMIN".equals(loginUser.getRole())) {
            try {
                OperateLog operateLog = new OperateLog();
                operateLog.setOperateAdminId(loginUser.getId());
                operateLog.setOperatorName(loginUser.getUsername());
                operateLog.setOperatorUsername(loginUser.getUsername());
                operateLog.setOperatorRole("ADMIN");
                operateLog.setIpAddress(request.getRemoteAddr());
                operateLog.setOperationType("LOGIN");
                operateLog.setModule("USER_MANAGEMENT");
                operateLog.setDescription("管理员登录系统: " + loginUser.getUsername());
                operateLog.setOperationResult("SUCCESS");
                operateLog.setOperateTime(new Date());
                
                operateLogMapper.insert(operateLog);
                log.info("管理员操作日志记录成功: {}", operateLog);
            } catch (Exception e) {
                log.error("记录管理员操作日志失败: {}", e.getMessage());
            }
        }
        
        return result;
    }
    
    private String getRoleByTeacherType(String teacherType) {
        return switch (teacherType) {
            case "COLLEGE" -> "ROLE_TEACHER_COLLEGE";
            case "DEPARTMENT" -> "ROLE_TEACHER_DEPARTMENT";
            case "COUNSELOR" -> "ROLE_TEACHER_COUNSELOR";
            default -> "ROLE_TEACHER";
        };
    }
    
    private String identifyTeacherType(TeacherUser teacher) {
        String name = teacher.getName();
        if (name == null) {
            return "DEPARTMENT";
        }
        
        if (name.contains("院长") || name.contains("书记") || name.contains("主任")) {
            return "COLLEGE";
        } else if (name.contains("辅导员") || name.contains("导员")) {
            return "COUNSELOR";
        } else {
            return "DEPARTMENT";
        }
    }

    @PostMapping("/register")
    public Result register(@RequestBody Map<String, Object> requestData) {
        log.info("用户注册请求: {}", requestData.get("username"));
        try {
            String password = (String) requestData.get("password");
            
            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(password);
            if (!validationResult.isValid()) {
                log.warn("用户注册失败：密码不符合复杂度要求 - {}", validationResult.getMessage());
                return Result.error(validationResult.getMessage());
            }
            
            User user = new User();
            user.setUsername((String) requestData.get("username"));
            user.setPassword(password);
            user.setName((String) requestData.get("name"));
            
            // 设置默认角色为学生
            String role = (String) requestData.get("role");
            if (role == null) {
                role = "ROLE_STUDENT";
            }
            user.setRole(role);

            // 根据角色将用户注册到不同的表中
            if ("ROLE_STUDENT".equals(role)) {
                // 使用StudentUserService检查学号是否已存在
                StudentUserService studentUserService = applicationContext.getBean(StudentUserService.class);
                String studentId = (String) requestData.get("username");
                if (studentUserService.findByStudentId(studentId) != null) {
                    log.warn("学生用户注册失败: 学号已存在 - {}", studentId);
                    return Result.error("用户名已存在");
                }
                
                StudentUser studentUser = new StudentUser();
                // 复制公共字段
                BeanUtils.copyProperties(user, studentUser);
                // 显式设置学号字段
                studentUser.setUsername(studentId);
                studentUser.setStudentId(studentId);
                
                // 直接从请求数据中获取学生特有字段
                try {
                    if (requestData.containsKey("grade")) {
                        studentUser.setGrade(Integer.valueOf(requestData.get("grade").toString()));
                    }
                    if (requestData.containsKey("majorId")) {
                        studentUser.setMajorId(Long.valueOf(requestData.get("majorId").toString()));
                    }
                    if (requestData.containsKey("classId")) {
                        studentUser.setClassId(Long.valueOf(requestData.get("classId").toString()));
                    }
                    log.debug("成功设置学生特有字段 - 年级: {}, 专业ID: {}, 班级ID: {}", 
                              studentUser.getGrade(), studentUser.getMajorId(), studentUser.getClassId());
                } catch (Exception e) {
                    log.warn("设置学生特有字段时出错: {}", e.getMessage());
                }
                
                // 注册学生用户
                studentUserService.register(studentUser);
            } else if ("ROLE_TEACHER".equals(role)) {
                // 使用TeacherUserService检查用户名是否已存在
                TeacherUserService teacherUserService = applicationContext.getBean(TeacherUserService.class);
                String teacherId = (String) requestData.get("username");
                try {
                    teacherUserService.findByTeacherUserId(teacherId);
                    log.warn("教师用户注册失败: 教师编号已存在 - {}", teacherId);
                    return Result.error("教师编号已存在");
                } catch (Exception e) {
                    // 如果抛出异常，说明教师编号不存在，可以继续注册
                    log.debug("教师编号未存在，可以注册: {}", teacherId);
                }
                
                TeacherUser teacherUser = new TeacherUser();
                // 复制公共字段
                BeanUtils.copyProperties(user, teacherUser);
                // 显式设置教师编号
                teacherUser.setUsername(teacherId);
                teacherUser.setTeacherUserId(teacherId);
                
                // 设置院系ID
                if (requestData.containsKey("departmentId")) {
                    try {
                        String departmentIdStr = requestData.get("departmentId").toString();
                        if (departmentIdStr != null && !departmentIdStr.isEmpty()) {
                            teacherUser.setDepartmentId(departmentIdStr);
                            log.debug("成功设置教师院系ID: {}", teacherUser.getDepartmentId());
                        }
                    } catch (Exception e) {
                        log.warn("设置教师院系ID时出错: {}", e.getMessage());
                    }
                }
                
                // 注册教师用户
                teacherUserService.register(teacherUser);
            } else if ("ROLE_ADMIN".equals(role)) {
                // 使用AdminUserService检查用户名是否已存在
                AdminUserService adminUserService = applicationContext.getBean(AdminUserService.class);
                String username = (String) requestData.get("username");
                if (adminUserService.findByUsername(username) != null) {
                    log.warn("管理员用户注册失败: 用户名已存在 - {}", username);
                    return Result.error("用户名已存在");
                }
                
                AdminUser adminUser = new AdminUser();
                // 复制公共字段
                BeanUtils.copyProperties(user, adminUser);
                
                // 注册管理员用户
                adminUserService.register(adminUser);
            } else {
                log.warn("用户注册失败: 无效的角色 - {}", role);
                return Result.error("无效的角色");
            }
            
            log.info("用户注册成功: {}", requestData.get("username"));
            return Result.success();
        } catch (Exception e) {
            log.error("用户注册失败: {}, 错误信息: {}", requestData.get("username"), e.getMessage());
            return Result.error("注册失败，请稍后再试");
        }
    }
    
    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/current-user")
    public Result getCurrentUser(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
            return Result.error("未登录");
        }
        
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String username = userDetails.getUsername();
        
        // 查询用户信息
        User user = userService.findByUsername(username);
        if (user == null) {
            return Result.error("用户不存在");
        }
        
        // 创建一个不包含敏感信息的用户对象
        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", user.getId());
        safeUser.put("username", user.getUsername());
        safeUser.put("role", user.getRole());
        safeUser.put("name", user.getName());
        safeUser.put("createTime", user.getCreateTime());
        
        return Result.success(safeUser);
    }
    
    /**
     * 刷新访问令牌
     * 实现滑动窗口刷新策略，只在必要时生成新的刷新令牌
     */
    @PostMapping("/refresh-token")
    public Result refreshToken(@RequestBody Map<String, String> refreshData) {
        String refreshToken = refreshData.get("refreshToken");
        
        if (!StringUtils.hasText(refreshToken)) {
            return Result.error("刷新令牌不能为空");
        }
        
        try {
            // 验证刷新令牌
            Map<String, Object> claims = jwtUtils.parseToken(refreshToken);
            
            // 从刷新令牌中获取用户信息
            String username = (String) claims.get("username");
            String role = (String) claims.get("role");
            // userId 可能是 Long 或 Integer 类型，需要安全转换
            Object userIdObj = claims.get("userId");
            Long userId = null;
            if (userIdObj instanceof Long) {
                userId = (Long) userIdObj;
            } else if (userIdObj instanceof Integer) {
                userId = ((Integer) userIdObj).longValue();
            } else if (userIdObj instanceof String) {
                userId = Long.parseLong((String) userIdObj);
            }
            
            if (username == null || role == null || userId == null) {
                return Result.error("刷新令牌无效");
            }
            
            // 生成新的访问令牌
            Map<String, Object> newClaims = new HashMap<>();
            newClaims.put("userId", userId);
            newClaims.put("username", username);
            newClaims.put("role", role);
            String newAccessToken = jwtUtils.generateAccessToken(newClaims);
            
            // 使用滑动窗口策略决定是否生成新的刷新令牌
            // 只有当刷新令牌即将过期时（剩余有效期小于滑动窗口阈值），才生成新的刷新令牌
            String newRefreshToken = refreshToken; // 默认复用原刷新令牌
            if (jwtUtils.shouldRefreshToken(refreshToken)) {
                newRefreshToken = jwtUtils.generateRefreshToken(newClaims);
                log.info("用户 {} 的刷新令牌已更新（滑动窗口策略）", username);
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("accessToken", newAccessToken);
            result.put("refreshToken", newRefreshToken);
            
            log.info("用户刷新令牌成功: {}", username);
            return Result.success(result);
        } catch (JwtUtils.JwtException e) {
            log.error("刷新令牌失败: {}, 错误码: {}", e.getMessage(), e.getErrorCode());
            return Result.error("刷新令牌无效或已过期");
        } catch (Exception e) {
            log.error("刷新令牌过程发生异常: {}", e.getMessage());
            return Result.error("刷新令牌失败，请重新登录");
        }
    }
    
    /**
     * 用户登出接口
     * 将访问令牌加入黑名单
     */
    @PostMapping("/logout")
    @Log(operationType = "LOGOUT", module = "AUTH", description = "管理员登出系统")
    public Result logout(HttpServletRequest request, Authentication authentication) {
        try {
            Long adminId = null;
            String username = null;
            
            if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
                UserDetails userDetails = (UserDetails) authentication.getPrincipal();
                username = userDetails.getUsername();
                
                try {
                    AdminUser adminUser = adminUserService.findByUsername(username);
                    if (adminUser != null) {
                        adminId = adminUser.getId();
                    }
                } catch (Exception e) {
                    log.warn("获取管理员ID失败: {}", e.getMessage());
                }
                
                String authorizationHeader = request.getHeader("Authorization");
                if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                    String accessToken = authorizationHeader.substring(7);
                    jwtUtils.blacklistToken(accessToken);
                    log.info("管理员 {} (ID: {}) 登出成功，访问令牌已加入黑名单", username, adminId);
                }
            }
            
            return Result.success("登出成功");
        } catch (Exception e) {
            log.error("用户登出失败: {}", e.getMessage());
            return Result.error("登出失败");
        }
    }
    
    /**
     * 管理员角色切换接口
     * 允许管理员切换到教师、学生或企业端账号进行测试
     */
    @PostMapping("/switch-role")
    @Log(operationType = "SWITCH_ROLE", module = "AUTH", description = "管理员切换角色")
    public Result switchRole(@RequestBody Map<String, String> switchRequest, Authentication authentication, HttpServletRequest request) {
        try {
            if (authentication == null || !(authentication.getPrincipal() instanceof UserDetails)) {
                return Result.error("未登录或无权限");
            }
            
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String adminUsername = userDetails.getUsername();
            
            AdminUser adminUser = adminUserService.findByUsername(adminUsername);
            if (adminUser == null || !"ROLE_ADMIN".equals(adminUser.getRole())) {
                return Result.error("只有管理员才能切换角色");
            }
            
            String adminPassword = switchRequest.get("adminPassword");
            if (!StringUtils.hasText(adminPassword)) {
                return Result.error("请输入管理员密码进行验证");
            }
            
            if (!passwordEncoder.matches(adminPassword, adminUser.getPassword())) {
                log.warn("管理员 {} 切换角色时密码验证失败", adminUsername);
                return Result.error("管理员密码错误");
            }
            
            String targetRole = switchRequest.get("targetRole");
            String targetUsername = switchRequest.get("targetUsername");
            
            if (!StringUtils.hasText(targetRole) || !StringUtils.hasText(targetUsername)) {
                return Result.error("目标角色和用户名不能为空");
            }
            
            Map<String, Object> result = new HashMap<>();
            
            if ("teacher".equals(targetRole)) {
                TeacherUser teacher = teacherUserService.findByTeacherUserId(targetUsername);
                if (teacher == null) {
                    return Result.error("教师工号不存在");
                }
                
                if (teacher.getStatus() == 0) {
                    return Result.error("该教师账号已被禁用");
                }
                
                String teacherType = teacher.getTeacherType();
                if (teacherType == null || teacherType.isEmpty()) {
                    teacherType = identifyTeacherType(teacher);
                    teacher.setTeacherType(teacherType);
                    teacherUserService.update(teacher);
                }
                
                String role = getRoleByTeacherType(teacherType);
                List<Permission> permissions = permissionService.getPermissionsByTeacherType(teacherType);
                List<String> permissionCodes = permissions.stream()
                        .map(Permission::getPermissionCode)
                        .collect(Collectors.toList());
                
                Map<String, Object> claims = new HashMap<>();
                claims.put("userId", teacher.getId());
                claims.put("username", teacher.getTeacherUserId());
                claims.put("role", role);
                claims.put("teacherType", teacherType);
                claims.put("permissions", permissionCodes);
                claims.put("isReadOnly", true);
                claims.put("originalAdminId", adminUser.getId());
                claims.put("originalAdminUsername", adminUser.getUsername());
                
                String accessToken = jwtUtils.generateAccessToken(claims);
                String refreshToken = jwtUtils.generateRefreshToken(claims);
                
                result.put("token", accessToken);
                result.put("refreshToken", refreshToken);
                result.put("userInfo", teacher);
                result.put("permissions", permissionCodes);
                result.put("role", role);
                result.put("isReadOnly", true);
                result.put("originalAdminUsername", adminUser.getUsername());
                
                log.info("管理员 {} 切换到教师角色: {} ({}), 进入只读模式", adminUsername, teacher.getName(), teacher.getTeacherUserId());
                
            } else if ("student".equals(targetRole)) {
                StudentUser student = studentUserService.findByStudentId(targetUsername);
                if (student == null) {
                    return Result.error("学生学号不存在");
                }
                
                if (student.getStatus() == 0) {
                    return Result.error("该学生账号已被禁用");
                }
                
                User user = userService.findByUsername(targetUsername);
                if (user == null) {
                    return Result.error("学生用户信息不存在");
                }
                
                Map<String, Object> claims = new HashMap<>();
                claims.put("userId", student.getId());
                claims.put("username", student.getStudentId());
                claims.put("role", "ROLE_STUDENT");
                claims.put("name", student.getName());
                claims.put("isReadOnly", true);
                claims.put("originalAdminId", adminUser.getId());
                claims.put("originalAdminUsername", adminUser.getUsername());
                
                String accessToken = jwtUtils.generateAccessToken(claims);
                String refreshToken = jwtUtils.generateRefreshToken(claims);
                
                Map<String, Object> safeUser = new HashMap<>();
                safeUser.put("id", student.getId());
                safeUser.put("username", student.getStudentId());
                safeUser.put("role", "ROLE_STUDENT");
                safeUser.put("name", student.getName());
                
                result.put("accessToken", accessToken);
                result.put("refreshToken", refreshToken);
                result.put("user", safeUser);
                result.put("role", "ROLE_STUDENT");
                result.put("isReadOnly", true);
                result.put("originalAdminUsername", adminUser.getUsername());
                
                log.info("管理员 {} 切换到学生角色: {} ({}), 进入只读模式", adminUsername, student.getName(), student.getStudentId());
                
            } else if ("company".equals(targetRole)) {
                User user = userService.findByUsername(targetUsername);
                if (user == null) {
                    return Result.error("企业用户不存在");
                }
                
                if (!"ROLE_COMPANY".equals(user.getRole())) {
                    return Result.error("该用户不是企业用户");
                }
                
                var companyUser = companyUserService.findById(user.getId());
                if (companyUser == null) {
                    return Result.error("企业信息不存在");
                }
                
                if (companyUser.getStatus() == 0) {
                    return Result.error("该企业账号已被禁用");
                }
                
                Map<String, Object> claims = new HashMap<>();
                claims.put("userId", user.getId());
                claims.put("username", user.getUsername());
                claims.put("role", "ROLE_COMPANY");
                claims.put("name", companyUser.getCompanyName());
                claims.put("isReadOnly", true);
                claims.put("originalAdminId", adminUser.getId());
                claims.put("originalAdminUsername", adminUser.getUsername());
                
                String accessToken = jwtUtils.generateAccessToken(claims);
                String refreshToken = jwtUtils.generateRefreshToken(claims);
                
                Map<String, Object> safeUser = new HashMap<>();
                safeUser.put("id", user.getId());
                safeUser.put("username", user.getUsername());
                safeUser.put("role", "ROLE_COMPANY");
                safeUser.put("name", companyUser.getCompanyName());
                
                result.put("accessToken", accessToken);
                result.put("refreshToken", refreshToken);
                result.put("user", safeUser);
                result.put("role", "ROLE_COMPANY");
                result.put("isReadOnly", true);
                result.put("originalAdminUsername", adminUser.getUsername());
                
                log.info("管理员 {} 切换到企业角色: {}, 进入只读模式", adminUsername, companyUser.getCompanyName());
                
            } else {
                return Result.error("不支持的目标角色");
            }
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("角色切换失败: {}", e.getMessage(), e);
            return Result.error("角色切换失败: " + e.getMessage());
        }
    }

    /**
     * 验证密码（仅用于安全验证，不修改密码，不增加令牌版本号）
     * 用于清除数据等危险操作前的安全验证
     */
    @PostMapping("/verify-password")
    public Result verifyPassword(@RequestBody Map<String, String> passwordData, Authentication authentication) {
        if (authentication == null) {
            return Result.error("未登录");
        }
        
        String username = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof User) {
            username = ((User) principal).getUsername();
        } else if (principal != null) {
            username = principal.toString();
        }
        
        if (username == null) {
            return Result.error("未登录");
        }
        
        String password = passwordData.get("password");
        
        if (!StringUtils.hasText(password)) {
            return Result.error("密码不能为空");
        }
        
        try {
            User currentUser = userService.findByUsername(username);
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            
            if (!passwordEncoder.matches(password, currentUser.getPassword())) {
                log.warn("用户 {} 密码验证失败：密码错误", username);
                return Result.error("密码错误");
            }
            
            log.info("用户 {} 密码验证成功", username);
            return Result.success("密码验证成功");
        } catch (Exception e) {
            log.error("密码验证失败: {}", e.getMessage(), e);
            return Result.error("密码验证失败，请稍后再试");
        }
    }

    /**
     * 修改密码
     */
    @PutMapping("/change-password")
    public Result changePassword(@RequestBody Map<String, String> passwordData, Authentication authentication, HttpServletRequest request) {
        if (authentication == null) {
            return Result.error("未登录");
        }
        
        // 获取用户名，支持多种 principal 类型
        String username = null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof UserDetails) {
            username = ((UserDetails) principal).getUsername();
        } else if (principal instanceof User) {
            username = ((User) principal).getUsername();
        } else if (principal != null) {
            username = principal.toString();
        }
        
        if (username == null) {
            return Result.error("未登录");
        }
        
        String oldPassword = passwordData.get("oldPassword");
        String newPassword = passwordData.get("newPassword");
        
        // 参数验证 - 使用hasText方法替代过时的isEmpty方法
        if (!StringUtils.hasText(oldPassword) || !StringUtils.hasText(newPassword)) {
            return Result.error("密码不能为空");
        }
        
        // 验证新密码复杂度：必须包含字母和数字
        PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
        if (!validationResult.isValid()) {
            log.warn("用户 {} 修改密码失败：新密码不符合复杂度要求 - {}", username, validationResult.getMessage());
            return Result.error(validationResult.getMessage());
        }
        
        try {
            // 直接从数据库查询用户信息，避免使用缓存
            User currentUser = userService.findByUsername(username);
            if (currentUser == null) {
                return Result.error("用户不存在");
            }
            
            // 直接比较密码（系统使用NoOpPasswordEncoder，密码是明文存储）
            if (!passwordEncoder.matches(oldPassword, currentUser.getPassword())) {
                log.warn("用户 {} 修改密码失败：旧密码错误", username);
                return Result.error("旧密码错误");
            }
            
            // 修改密码
            userService.updatePassword(username, newPassword);
            
            // 增加用户令牌版本号，使之前的令牌失效（安全措施）
            jwtUtils.incrementUserTokenVersion(username);
            
            // 从请求头获取访问令牌并加入黑名单
            String authorizationHeader = request.getHeader("Authorization");
            if (StringUtils.hasText(authorizationHeader) && authorizationHeader.startsWith("Bearer ")) {
                String accessToken = authorizationHeader.substring(7);
                jwtUtils.blacklistToken(accessToken);
            }
            
            log.info("用户 {} 修改密码成功，已增加令牌版本号使旧令牌失效", username);
            return Result.success("密码修改成功，请重新登录");
        } catch (AuthenticationException e) {
            log.error("用户 {} 修改密码失败：认证异常", username, e);
            return Result.error("旧密码错误");
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage(), e);
            return Result.error("修改密码失败，请稍后再试");
        }
    }

    // 检查当前用户状态
    @GetMapping("/check-status")
    public Result checkUserStatus(HttpServletRequest request) {
        try {
            String username = null;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            
            if (authentication == null) {
                log.warn("检查用户状态失败: SecurityContext中的authentication为null");
                return Result.error("未登录");
            }
            
            Object principal = authentication.getPrincipal();
            
            if (principal == null) {
                log.warn("检查用户状态失败: authentication中的principal为null");
                return Result.error("未登录");
            }
            
            if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
                username = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            } else if (principal instanceof User) {
                username = ((User) principal).getUsername();
            } else if (principal != null) {
                username = principal.toString();
            }
            
            if (username == null || username.isEmpty()) {
                log.warn("检查用户状态失败: 无法从principal中获取username, principal类型: {}", principal.getClass().getName());
                return Result.error("未登录");
            }
            
            User user = userService.findByUsername(username);
            if (user == null) {
                log.warn("检查用户状态失败: 用户不存在 - {}", username);
                return Result.error("用户不存在");
            }
            
            // 根据用户角色检查状态
            Integer status = 1;
            String role = user.getRole();
            if (role != null && role.startsWith("ROLE_TEACHER")) {
                try {
                    TeacherUser teacherUser = teacherUserService.findByTeacherUserId(username);
                    if (teacherUser != null && teacherUser.getStatus() != null) {
                        status = teacherUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询教师用户状态失败: {}", e.getMessage());
                }
            } else if ("ROLE_STUDENT".equals(role)) {
                try {
                    StudentUser studentUser = studentUserService.findByStudentId(username);
                    if (studentUser != null && studentUser.getStatus() != null) {
                        status = studentUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询学生用户状态失败: {}", e.getMessage());
                }
            } else if ("ROLE_COMPANY".equals(role)) {
                try {
                    var companyUser = companyUserService.findById(user.getId());
                    if (companyUser != null && companyUser.getStatus() != null) {
                        status = companyUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询企业用户状态失败: {}", e.getMessage());
                }
            } else if ("ROLE_ADMIN".equals(role)) {
                try {
                    AdminUser adminUser = adminUserService.findByUsername(username);
                    if (adminUser != null && adminUser.getStatus() != null) {
                        status = adminUser.getStatus();
                    }
                } catch (Exception e) {
                    log.error("查询管理员用户状态失败: {}", e.getMessage());
                }
            }
            
            if (status == 0) {
                return Result.error("您的账号已被禁用，请联系管理员");
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("status", status);
            result.put("username", user.getUsername());
            result.put("name", user.getName());
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("检查用户状态失败: {}", e.getMessage(), e);
            return Result.error("检查用户状态失败");
        }
    }

    /**
     * 修改用户名
     */
    @PutMapping("/update-username")
    public Result updateUsername(@RequestBody Map<String, String> usernameData, Authentication authentication) {
        if (authentication == null) {
            return Result.error("未登录");
        }
        
        String oldUsername = usernameData.get("oldUsername");
        String newUsername = usernameData.get("newUsername");
        
        if (!StringUtils.hasText(oldUsername) || !StringUtils.hasText(newUsername)) {
            return Result.error("用户名不能为空");
        }
        
        if (newUsername.length() < 3 || newUsername.length() > 20) {
            return Result.error("用户名长度必须在3-20个字符之间");
        }
        
        if (oldUsername.equals(newUsername)) {
            return Result.error("新用户名不能与旧用户名相同");
        }
        
        try {
            int result = userService.updateUsername(oldUsername, newUsername);
            if (result > 0) {
                log.info("用户名修改成功: {} -> {}", oldUsername, newUsername);
                return Result.success("用户名修改成功");
            } else {
                return Result.error("用户名修改失败");
            }
        } catch (BusinessException e) {
            log.warn("用户名修改失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("用户名修改失败: {}", e.getMessage());
            return Result.error("用户名修改失败");
        }
    }

    @PostMapping("/auto-login")
    public Result autoLogin(@RequestBody Map<String, Object> loginRequest, HttpServletRequest request) {
        String username = (String) loginRequest.get("username");
        String password = (String) loginRequest.get("password");
        
        log.info("自动登录请求: username={}", username);
        
        if (username == null || username.isEmpty()) {
            return Result.error("用户名不能为空");
        }
        
        if (password == null || password.isEmpty()) {
            return Result.error("密码不能为空");
        }
        
        if (!SystemSettingsConfig.isSystemEnabled()) {
            log.warn("系统维护中，拒绝自动登录: username={}", username);
            return Result.error("系统维护中，暂时无法登录，请稍后再试");
        }
        
        String cacheKey = username + ":auto";
        LoginAttemptInfo attemptInfo = LOGIN_ATTEMPT_CACHE.get(cacheKey);
        
        if (attemptInfo == null) {
            attemptInfo = new LoginAttemptInfo();
            LOGIN_ATTEMPT_CACHE.put(cacheKey, attemptInfo);
        }
        
        if (attemptInfo.isLocked()) {
            long remainingTime = attemptInfo.getRemainingLockTime();
            log.warn("用户账号已被锁定: username={}, 剩余锁定时间={}秒", username, remainingTime);
            return Result.error("账号已被锁定，请" + remainingTime + "秒后再试");
        }
        
        try {
            Map<String, Object> result = handleAutoLogin(username, password, request);
            
            LOGIN_ATTEMPT_CACHE.remove(cacheKey);
            log.info("自动登录成功，清除登录失败记录: username={}", username);
            
            return Result.success(result);
        } catch (AuthenticationException e) {
            log.error("自动登录失败: {}, 错误信息: {}", username, e.getMessage());
            
            attemptInfo.attemptCount++;
            
            int maxAttempts = SystemSettingsConfig.getMaxLoginAttempts();
            int lockTime = SystemSettingsConfig.getLockTime();
            
            if (attemptInfo.attemptCount >= maxAttempts) {
                attemptInfo.lockUntil = System.currentTimeMillis() + (lockTime * 60 * 1000L);
                log.warn("用户账号已被锁定: username={}, 锁定时间={}分钟", username, lockTime);
            }
            
            try {
                LoginLog loginLog = new LoginLog();
                loginLog.setUserId(username);
                loginLog.setUserType("AUTO");
                loginLog.setUserName(username);
                loginLog.setLoginTime(new Date());
                loginLog.setIpAddress(request.getRemoteAddr());
                loginLog.setDeviceInfo(request.getHeader("User-Agent"));
                loginLog.setLoginStatus("FAILURE");
                
                loginLogMapper.insert(loginLog);
                log.info("自动登录失败日志记录成功: {}", loginLog);
            } catch (Exception ex) {
                log.error("记录自动登录失败日志失败: {}", ex.getMessage());
            }
            
            String errorMessage = e.getMessage();
            if (errorMessage != null && errorMessage.contains("账号已被禁用")) {
                return Result.error("您的账号已被禁用，请联系管理员");
            }
            
            if (attemptInfo.isLocked()) {
                return Result.error("登录失败次数过多，账号已被锁定" + lockTime + "分钟");
            }
            
            int remainingAttempts = maxAttempts - attemptInfo.attemptCount;
            return Result.error("用户名或密码错误，剩余尝试次数: " + remainingAttempts);
        } catch (Exception e) {
            log.error("自动登录过程中发生异常: {}", e.getMessage(), e);
            return Result.error("登录失败: " + e.getMessage());
        }
    }
    
    private Map<String, Object> handleAutoLogin(String username, String password, HttpServletRequest request) {
        log.info("开始自动识别用户身份: username={}", username);
        
        Map<String, Object> result = null;
        
        log.info("尝试学生登录...");
        try {
            StudentUser student = studentUserService.findByStudentId(username);
            if (student != null) {
                log.info("找到学生用户: {}", username);
                if (!passwordEncoder.matches(password, student.getPassword())) {
                    throw new AuthenticationException("用户名或密码错误") {};
                }
                if (student.getStatus() == 0) {
                    throw new AuthenticationException("账号已被禁用") {};
                }
                result = handleStudentAutoLogin(student, request);
                return result;
            }
        } catch (AuthenticationException e) {
            log.warn("学生登录失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.debug("学生用户不存在: {}", username);
        }
        
        log.info("尝试教师登录...");
        try {
            TeacherUser teacher = teacherUserService.findByTeacherUserId(username);
            if (teacher != null) {
                log.info("找到教师用户: {}", username);
                if (!passwordEncoder.matches(password, teacher.getPassword())) {
                    throw new AuthenticationException("用户名或密码错误") {};
                }
                if (teacher.getStatus() == 0) {
                    throw new AuthenticationException("账号已被禁用") {};
                }
                result = handleTeacherAutoLogin(teacher, request);
                return result;
            }
        } catch (AuthenticationException e) {
            log.warn("教师登录失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.debug("教师用户不存在: {}", username);
        }
        
        log.info("尝试企业登录...");
        try {
            CompanyUser company = companyUserService.findByUsername(username);
            if (company != null) {
                log.info("找到企业用户: {}", username);
                if (!passwordEncoder.matches(password, company.getPassword())) {
                    throw new AuthenticationException("用户名或密码错误") {};
                }
                if (company.getStatus() == 0) {
                    throw new AuthenticationException("账号已被禁用") {};
                }
                result = handleCompanyAutoLogin(company, request);
                return result;
            }
        } catch (AuthenticationException e) {
            log.warn("企业登录失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.debug("企业用户不存在: {}", username);
        }
        
        log.info("尝试管理员登录...");
        try {
            AdminUser admin = adminUserService.findByUsername(username);
            if (admin != null) {
                log.info("找到管理员用户: {}", username);
                if (!passwordEncoder.matches(password, admin.getPassword())) {
                    throw new AuthenticationException("用户名或密码错误") {};
                }
                if (admin.getStatus() == 0) {
                    throw new AuthenticationException("账号已被禁用") {};
                }
                result = handleAdminAutoLogin(admin, request);
                return result;
            }
        } catch (AuthenticationException e) {
            log.warn("管理员登录失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.debug("管理员用户不存在: {}", username);
        }
        
        log.warn("未找到任何用户: {}", username);
        throw new AuthenticationException("用户名或密码错误") {};
    }
    
    private Map<String, Object> handleStudentAutoLogin(StudentUser student, HttpServletRequest request) {
        log.info("处理学生自动登录: 学号={}", student.getStudentId());
        
        String role = "ROLE_STUDENT";
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", student.getId());
        claims.put("username", student.getStudentId());
        claims.put("role", role);
        
        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);
        
        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        
        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", student.getId());
        safeUser.put("username", student.getStudentId());
        safeUser.put("role", role);
        safeUser.put("name", student.getName());
        result.put("user", safeUser);
        
        log.info("学生自动登录成功: {}", student.getStudentId());
        
        recordLoginLog(String.valueOf(student.getId()), role, student.getStudentId(), request, "SUCCESS");
        
        return result;
    }
    
    private Map<String, Object> handleTeacherAutoLogin(TeacherUser teacher, HttpServletRequest request) {
        log.info("处理教师自动登录: 教师编号={}", teacher.getTeacherUserId());
        
        String teacherType = teacher.getTeacherType();
        if (teacherType == null || teacherType.isEmpty()) {
            teacherType = identifyTeacherType(teacher);
            teacher.setTeacherType(teacherType);
            teacherUserService.update(teacher);
            log.info("自动识别教师类型: {} -> {}", teacher.getTeacherUserId(), teacherType);
        }
        
        String role = getRoleByTeacherType(teacherType);
        teacher.setRole(role);
        
        List<Permission> permissions = permissionService.getPermissionsByTeacherType(teacherType);
        List<String> permissionCodes = permissions.stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toList());
        
        log.info("教师 {} (类型: {}) 的权限列表: {}", teacher.getTeacherUserId(), teacherType, permissionCodes);
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", teacher.getId());
        claims.put("username", teacher.getTeacherUserId());
        claims.put("role", role);
        claims.put("teacherType", teacherType);
        claims.put("permissions", permissionCodes);
        
        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);
        
        Map<String, Object> result = new HashMap<>();
        result.put("token", accessToken);
        result.put("refreshToken", refreshToken);
        result.put("userInfo", teacher);
        result.put("permissions", permissionCodes);
        
        log.info("教师自动登录成功: {}, 类型: {}, 权限数: {}", teacher.getTeacherUserId(), teacherType, permissionCodes.size());
        
        recordLoginLog(String.valueOf(teacher.getId()), role, teacher.getTeacherUserId(), request, "SUCCESS");
        
        try {
            userService.clearAllUserDetailsCache();
            log.info("教师自动登录成功，已清除UserDetails缓存");
        } catch (Exception e) {
            log.warn("清除UserDetails缓存失败: {}", e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, Object> handleAdminAutoLogin(AdminUser admin, HttpServletRequest request) {
        log.info("处理管理员自动登录: 用户名={}", admin.getUsername());
        
        String role = "ROLE_ADMIN";
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", admin.getId());
        claims.put("username", admin.getUsername());
        claims.put("role", role);
        
        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);
        
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        
        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", admin.getId());
        safeUser.put("username", admin.getUsername());
        safeUser.put("role", role);
        safeUser.put("name", admin.getName());
        result.put("user", safeUser);
        
        if ("ROLE_ADMIN".equals(role)) {
            try {
                AdminPermissionService adminPermissionService = applicationContext.getBean(AdminPermissionService.class);
                List<Permission> permissions = adminPermissionService.getRolePermissionsByCode(role);
                result.put("permissions", permissions);
                log.info("管理员自动登录成功，已返回权限信息: {} 个权限", permissions.size());
            } catch (Exception e) {
                log.warn("获取管理员权限失败: {}", e.getMessage());
            }
        }
        
        log.info("管理员自动登录成功: {}", admin.getUsername());
        
        recordLoginLog(String.valueOf(admin.getId()), role, admin.getUsername(), request, "SUCCESS");
        
        try {
            OperateLog operateLog = new OperateLog();
            operateLog.setOperateAdminId(admin.getId());
            operateLog.setOperatorName(admin.getUsername());
            operateLog.setOperatorUsername(admin.getUsername());
            operateLog.setOperatorRole("ADMIN");
            operateLog.setIpAddress(request.getRemoteAddr());
            operateLog.setOperationType("LOGIN");
            operateLog.setModule("USER_MANAGEMENT");
            operateLog.setDescription("管理员自动登录系统: " + admin.getUsername());
            operateLog.setOperationResult("SUCCESS");
            operateLog.setOperateTime(new Date());
            
            operateLogMapper.insert(operateLog);
            log.info("管理员操作日志记录成功: {}", operateLog);
        } catch (Exception e) {
            log.error("记录管理员操作日志失败: {}", e.getMessage());
        }
        
        return result;
    }
    
    private Map<String, Object> handleCompanyAutoLogin(CompanyUser company, HttpServletRequest request) {
        log.info("处理企业自动登录: 用户名={}", company.getUsername());
        
        String role = "ROLE_COMPANY";
        
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", company.getId());
        claims.put("username", company.getUsername());
        claims.put("role", role);
        
        String accessToken = jwtUtils.generateAccessToken(claims);
        String refreshToken = jwtUtils.generateRefreshToken(claims);
        
        Map<String, Object> result = new HashMap<>();
        result.put("accessToken", accessToken);
        result.put("refreshToken", refreshToken);
        
        Map<String, Object> safeUser = new HashMap<>();
        safeUser.put("id", company.getId());
        safeUser.put("username", company.getUsername());
        safeUser.put("role", role);
        safeUser.put("name", company.getCompanyName());
        result.put("user", safeUser);
        
        log.info("企业自动登录成功: {}", company.getUsername());
        
        recordLoginLog(String.valueOf(company.getId()), role, company.getUsername(), request, "SUCCESS");
        
        return result;
    }
    
    private void recordLoginLog(String userId, String userType, String userName, HttpServletRequest request, String status) {
        try {
            LoginLog loginLog = new LoginLog();
            loginLog.setUserId(userId);
            loginLog.setUserType(userType);
            loginLog.setUserName(userName);
            loginLog.setLoginTime(new Date());
            loginLog.setIpAddress(request.getRemoteAddr());
            loginLog.setDeviceInfo(request.getHeader("User-Agent"));
            loginLog.setLoginStatus(status);
            
            loginLogMapper.insert(loginLog);
            log.info("登录日志记录成功: {}", loginLog);
        } catch (Exception e) {
            log.error("记录登录日志失败: {}", e.getMessage());
        }
    }
}