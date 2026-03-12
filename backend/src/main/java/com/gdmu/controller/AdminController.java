package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.exception.BusinessException;
import com.gdmu.entity.GroupTable;
import com.gdmu.entity.LearningResource;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.User;
import com.gdmu.service.ResourceService;
import com.gdmu.service.UserService;
import com.gdmu.utils.JwtUtils;
import com.gdmu.utils.PasswordValidator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    
    @Autowired
    private ResourceService resourceService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 获取资源详情 - 管理员路径
     */
    @GetMapping("/resources/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getResource(@PathVariable Long id) {
        try {
            LearningResource resource = resourceService.getResourceById(id);
            if (resource == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("资源不存在");
            }
            return ResponseEntity.ok(resource);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
        }
    }
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private com.gdmu.service.ClassService classService;
    
    // 处理admin根路径请求
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result adminRoot() {
        log.info("管理员访问系统管理首页");
        return Result.success("欢迎访问管理员系统");
    }

    // 获取所有用户（分页）
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAllUsers(@RequestParam(defaultValue = "1") Integer page,
                              @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("分页获取用户列表，页码: {}, 每页条数: {}", page, pageSize);
        PageResult<User> pageResult = userService.findPage(page, pageSize);
        return Result.success(pageResult);
    }

    // 根据ID获取用户
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getUserById(@PathVariable Long id) {
        log.info("根据ID获取用户: {}", id);
        User user = userService.findById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        // 安全处理：移除密码信息
        user.setPassword(null);
        return Result.success(user);
    }

    // 根据角色获取用户
    @GetMapping("/users/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getUsersByRole(@PathVariable String role,
                                 @RequestParam(defaultValue = "1") Integer page,
                                 @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("根据角色获取用户列表，角色: {}, 页码: {}, 每页条数: {}", role, page, pageSize);
        List<User> users = userService.findByRole(role);
        return Result.success(users);
    }

    // 添加用户
    @Log
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result addUser(@RequestBody @Validated User user) {
        log.info("添加用户: {}", user.getUsername());
        try {
            userService.register(user);
            return Result.success("添加成功");
        } catch (BusinessException e) {
            log.warn("添加用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量添加用户
    @Log
    @PostMapping("/users/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchAddUsers(@RequestBody List<User> users) {
        log.info("批量添加用户，数量: {}", users.size());
        try {
            int successCount = 0;
            for (User user : users) {
                try {
                    userService.register(user);
                    successCount++;
                } catch (Exception e) {
                    log.warn("添加用户 {} 失败: {}", user.getUsername(), e.getMessage());
                }
            }
            return Result.success("批量添加完成，成功: " + successCount + " 个用户");
        } catch (Exception e) {
            log.error("批量添加用户失败: {}", e.getMessage());
            return Result.error("批量添加用户失败");
        }
    }

    // 更新用户
    @Log
    @PutMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateUser(@RequestBody User user) {
        log.info("更新用户: {}", user.getUsername());
        try {
            // 安全处理：防止普通管理员修改超级管理员信息
            User existingUser = userService.findById(user.getId());
            if (existingUser != null && "admin".equals(existingUser.getUsername())) {
                throw new BusinessException("无法修改超级管理员信息");
            }

            // 不允许通过此接口修改密码
            user.setPassword(null);
            userService.update(user);
            return Result.success("更新成功");
        } catch (BusinessException e) {
            log.warn("更新用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 删除用户
    @Log(operationType = "DELETE", module = "USER_MANAGEMENT", description = "删除用户")
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result deleteUser(@PathVariable Long id) {
        log.info("删除用户，ID: {}", id);
        try {
            // 安全处理：防止删除超级管理员
            User user = userService.findById(id);
            if (user != null && "admin".equals(user.getUsername())) {
                throw new BusinessException("无法删除超级管理员");
            }

            userService.delete(id);
            return Result.success("删除成功");
        } catch (BusinessException e) {
            log.warn("删除用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量删除用户
    @Log(operationType = "DELETE", module = "USER_MANAGEMENT", description = "批量删除用户")
    @DeleteMapping("/users/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result batchDeleteUsers(@RequestBody List<Long> ids) {
        log.info("批量删除用户，ID列表: {}", ids);
        try {
            for (Long id : ids) {
                // 安全检查：不允许删除超级管理员
                User user = userService.findById(id);
                if (user != null && "admin".equals(user.getUsername())) {
                    throw new BusinessException("无法删除超级管理员");
                }
                userService.delete(id);
            }
            return Result.success("批量删除成功");
        } catch (BusinessException e) {
            log.warn("批量删除用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 重置用户密码
     *
     * @param id          用户ID
     * @param passwordDTO 包含新密码的DTO对象
     * @return 操作结果
     */
    @PostMapping("/users/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result resetUserPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordDTO) {
        log.info("重置用户ID: {} 的密码", id);
        try {
            User user = userService.findById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }

            String newPassword = passwordDTO.get("password");
            if (StringUtils.isBlank(newPassword)) {
                newPassword = UUID.randomUUID().toString().substring(0, 8);
            }

            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
            if (!validationResult.isValid()) {
                log.warn("重置用户密码失败：新密码不符合复杂度要求 - {}", validationResult.getMessage());
                return Result.error(validationResult.getMessage());
            }

            user.setPassword(newPassword);
            userService.update(user);

            jwtUtils.incrementUserTokenVersion(user.getUsername());

            log.info("用户ID: {} 密码重置成功", id);
            return Result.success("密码重置成功", newPassword);
        } catch (Exception e) {
            log.error("重置用户密码失败: {}", e.getMessage());
            return Result.error("重置密码失败：" + e.getMessage());
        }
    }

    /**
     * 更新用户状态
     *
     * @param id        用户ID
     * @param statusDTO 包含用户状态的DTO对象
     * @return 操作结果
     */
    @PutMapping("/users/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public Result updateUserStatus(@PathVariable Long id, @RequestBody Map<String, String> statusDTO) {
        log.info("更新用户ID: {} 的状态", id);
        try {
            User user = userService.findById(id);
            if (user == null) {
                return Result.error("用户不存在");
            }

            // 安全检查：不允许修改超级管理员状态
            if ("admin".equals(user.getUsername())) {
                throw new BusinessException("无法修改超级管理员状态");
            }

            String status = statusDTO.get("status");
            if (StringUtils.isBlank(status)) {
                return Result.error("状态不能为空");
            }

            user.setUpdateTime(new Date());
            userService.update(user);

            log.info("用户ID: {} 状态更新成功，新状态: {}", id, status);
            return Result.success("状态更新成功");
        } catch (BusinessException e) {
            log.warn("更新用户状态失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新用户状态失败: {}", e.getMessage());
            return Result.error("更新状态失败");
        }
    }

    /**
     * 获取用户统计信息
     * 统计学生、教师和管理员的数量
     * @return 用户统计数据
     */
    @GetMapping("/stats/users")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getUserStats() {
        log.info("获取用户统计信息");
        try {
            // 获取各类用户数量（使用ROLE_前缀，因为findByRole方法需要完整角色名）
            int studentCount = userService.findByRole("ROLE_STUDENT").size();
            int teacherCount = userService.findByRole("ROLE_TEACHER").size();
            int adminCount = userService.findByRole("ROLE_ADMIN").size();
            int companyCount = userService.findByRole("ROLE_COMPANY").size();
            int totalCount = studentCount + teacherCount + adminCount + companyCount;

            Map<String, Integer> stats = new HashMap<>();
            stats.put("studentCount", studentCount);
            stats.put("teacherCount", teacherCount);
            stats.put("adminCount", adminCount);
            stats.put("companyCount", companyCount);
            stats.put("totalCount", totalCount);

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取用户统计失败: {}", e.getMessage());
            return Result.error("获取用户统计失败");
        }
    }


    
    /**
     * 获取系统统计信息
     * 统计各类系统数据
     * @return 系统统计数据
     */
    @GetMapping("/stats/system")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getSystemStats() {
        log.info("获取系统统计信息");
        try {
            // 这里可以添加更多系统级统计数据
            // 目前返回一个基本结构，后续可以扩展
            Map<String, Object> stats = new HashMap<>();
            stats.put("systemName", "实习管理系统");
            stats.put("version", "1.0.0");
            stats.put("currentTime", new Date());
            stats.put("systemStatus", "正常运行中");

            return Result.success(stats);
        } catch (Exception e) {
            log.error("获取系统统计失败: {}", e.getMessage());
            return Result.error("获取系统统计失败");
        }
    }

    /**
     * 获取所有班级列表
     * 用于管理员端学生管理页面的班级筛选
     */
    @GetMapping("/classes")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getAllClasses() {
        log.info("获取所有班级列表");
        try {
            List<com.gdmu.entity.Class> classes = classService.findAll();
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取班级列表失败: {}", e.getMessage());
            return Result.error("获取班级列表失败");
        }
    }

    /**
     * 清除用户缓存
     * 用于权限更新后清除缓存
     */
    @PostMapping("/cache/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public Result clearCache() {
        log.info("清除用户缓存");
        try {
            userService.clearAllUserDetailsCache();
            return Result.success("缓存清除成功");
        } catch (Exception e) {
            log.error("清除缓存失败: {}", e.getMessage());
            return Result.error("清除缓存失败");
        }
    }
}