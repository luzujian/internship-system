package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.exception.BusinessException;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.entity.AdminUser;
import com.gdmu.service.AdminUserService;
import com.gdmu.utils.JwtUtils;
import com.gdmu.utils.PasswordValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 管理员用户控制器
 */
@Slf4j
@RestController
@RequestMapping({"/api/admin/admin-users", "/api/admin/admins"})
public class AdminUserController {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AdminUserService adminUserService;
    
    @Autowired
    private JwtUtils jwtUtils;

    // 获取所有管理员用户（分页）
    @GetMapping
    @PreAuthorize("hasAuthority('user:admin:view')")
    public Result getAllAdminUsers(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "10") Integer pageSize) {
        log.info("分页获取管理员用户列表，页码: {}, 每页条数: {}", page, pageSize);
        PageResult<AdminUser> pageResult = adminUserService.findPage(page, pageSize);
        return Result.success(pageResult);
    }

    // 根据ID获取管理员用户
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('user:admin:view')")
    public Result getAdminUserById(@PathVariable Long id) {
        log.info("根据ID获取管理员用户: {}", id);
        AdminUser adminUser = adminUserService.findById(id);
        if (adminUser == null) {
            return Result.error("用户不存在");
        }
        // 安全处理：移除密码信息
        adminUser.setPassword(null);
        return Result.success(adminUser);
    }

    // 添加管理员用户
    @Log(operationType = "ADD", module = "USER_MANAGEMENT", description = "添加管理员用户")
    @PostMapping
    @PreAuthorize("hasAuthority('user:admin:add')")
    public Result addAdminUser(@RequestBody @Validated AdminUser adminUser) {
        log.info("添加管理员用户: {}", adminUser.getUsername());
        try {
            adminUser.setRole("ROLE_ADMIN"); // 确保角色正确
            adminUserService.register(adminUser);
            return Result.success("添加成功");
        } catch (BusinessException e) {
            log.warn("添加管理员用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量添加管理员用户
    @Log(operationType = "ADD", module = "USER_MANAGEMENT", description = "批量添加管理员用户")
    @PostMapping("/batch")
    @PreAuthorize("hasAuthority('user:admin:add')")
    public Result batchAddAdminUsers(@RequestBody List<AdminUser> adminUsers) {
        log.info("批量添加管理员用户，数量: {}", adminUsers.size());
        try {
            int successCount = 0;
            for (AdminUser adminUser : adminUsers) {
                try {
                    adminUser.setRole("ROLE_ADMIN"); // 确保角色正确
                    adminUserService.register(adminUser);
                    successCount++;
                } catch (Exception e) {
                    log.warn("添加管理员用户 {} 失败: {}", adminUser.getUsername(), e.getMessage());
                }
            }
            return Result.success("批量添加完成，成功: " + successCount + " 个用户");
        } catch (Exception e) {
            log.error("批量添加管理员用户失败: {}", e.getMessage());
            return Result.error("批量添加用户失败");
        }
    }

    // 更新管理员用户
    @Log(operationType = "UPDATE", module = "USER_MANAGEMENT", description = "更新管理员用户信息")
    @PutMapping
    @PreAuthorize("hasAuthority('user:admin:edit')")
    public Result updateAdminUser(@RequestBody AdminUser adminUser) {
        log.info("更新管理员用户: {}", adminUser.getUsername());
        try {
            // 安全处理：不允许通过此接口修改密码
            adminUser.setPassword(null);
            
            // 确保保留原始用户名，防止设置为null
            if (StringUtils.isBlank(adminUser.getUsername())) {
                AdminUser originalUser = adminUserService.findById(adminUser.getId());
                if (originalUser != null) {
                    adminUser.setUsername(originalUser.getUsername());
                }
            }
            
            adminUserService.update(adminUser);
            return Result.success("更新成功");
        } catch (BusinessException e) {
            log.warn("更新管理员用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 删除管理员用户
    @Log(operationType = "DELETE", module = "USER_MANAGEMENT", description = "删除管理员用户")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('user:admin:delete')")
    public Result deleteAdminUser(@PathVariable Long id) {
        log.info("删除管理员用户，ID: {}", id);
        try {
            adminUserService.delete(id);
            return Result.success("删除成功");
        } catch (BusinessException e) {
            log.warn("删除管理员用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    // 批量删除管理员用户
    @Log(operationType = "DELETE", module = "USER_MANAGEMENT", description = "批量删除管理员用户")
    @DeleteMapping("/batch")
    @PreAuthorize("hasAuthority('user:admin:delete')")
    public Result batchDeleteAdminUsers(@RequestBody List<Long> ids) {
        log.info("批量删除管理员用户，ID列表: {}", ids);
        try {
            for (Long id : ids) {
                adminUserService.delete(id);
            }
            return Result.success("批量删除成功");
        } catch (BusinessException e) {
            log.warn("批量删除管理员用户失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 重置管理员用户密码
     *
     * @param id          用户ID
     * @param passwordDTO 包含新密码的DTO对象
     * @return 操作结果
     */
    @Log(operationType = "UPDATE", module = "USER_MANAGEMENT", description = "重置管理员用户密码")
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAuthority('user:admin:reset')")
    public Result resetAdminUserPassword(@PathVariable Long id, @RequestBody Map<String, String> passwordDTO) {
        log.info("重置管理员用户ID: {} 的密码", id);
        try {
            AdminUser adminUser = adminUserService.findById(id);
            if (adminUser == null) {
                return Result.error("用户不存在");
            }

            String newPassword = passwordDTO.get("password");
            if (StringUtils.isBlank(newPassword)) {
                newPassword = UUID.randomUUID().toString().substring(0, 8);
            }

            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
            if (!validationResult.isValid()) {
                log.warn("重置管理员用户密码失败：新密码不符合复杂度要求 - {}", validationResult.getMessage());
                return Result.error(validationResult.getMessage());
            }

            adminUser.setPassword(newPassword);
            adminUserService.update(adminUser);

            jwtUtils.incrementUserTokenVersion(adminUser.getUsername());

            log.info("管理员用户ID: {} 密码重置成功", id);
            return Result.success("密码重置成功", newPassword);
        } catch (Exception e) {
            log.error("重置管理员用户密码失败: {}", e.getMessage());
            return Result.error("重置密码失败：" + e.getMessage());
        }
    }

    /**
     * 更新管理员用户状态
     *
     * @param id        用户ID
     * @param statusDTO 包含用户状态的DTO对象
     * @return 操作结果
     */
    @Log(operationType = "UPDATE", module = "USER_MANAGEMENT", description = "更新管理员用户状态")
    @PutMapping("/{id}/status")
    @PreAuthorize("hasAuthority('user:admin:edit')")
    public Result updateAdminUserStatus(@PathVariable Long id, @RequestBody Map<String, String> statusDTO) {
        log.info("更新管理员用户ID: {} 的状态", id);
        try {
            AdminUser adminUser = adminUserService.findById(id);
            if (adminUser == null) {
                return Result.error("用户不存在");
            }

            String status = statusDTO.get("status");
            if (StringUtils.isBlank(status)) {
                return Result.error("状态不能为空");
            }

            adminUser.setUpdateTime(new Date());
            adminUserService.update(adminUser);

            log.info("管理员用户ID: {} 状态更新成功，新状态: {}", id, status);
            return Result.success("状态更新成功");
        } catch (BusinessException e) {
            log.warn("更新管理员用户状态失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("更新管理员用户状态失败: {}", e.getMessage());
            return Result.error("更新状态失败");
        }
    }

    /**
     * 批量更新管理员用户状态
     *
     * @param statusDTOList 包含用户ID和状态的DTO列表
     * @return 操作结果
     */
    @Log(operationType = "UPDATE", module = "USER_MANAGEMENT", description = "批量更新管理员用户状态")
    @PutMapping("/batch/status")
    @PreAuthorize("hasAuthority('user:admin:edit')")
    public Result batchUpdateAdminUserStatus(@RequestBody List<Map<String, Object>> statusDTOList) {
        log.info("批量更新管理员用户状态，数量: {}", statusDTOList.size());
        try {
            int successCount = 0;
            for (Map<String, Object> statusDTO : statusDTOList) {
                try {
                    Long id = Long.valueOf(statusDTO.get("id").toString());
                    String status = statusDTO.get("status").toString();

                    AdminUser adminUser = adminUserService.findById(id);
                    if (adminUser != null) {
                        adminUser.setUpdateTime(new Date());
                        adminUserService.update(adminUser);
                        successCount++;
                    }
                } catch (Exception e) {
                    log.warn("更新管理员用户状态失败: {}", e.getMessage());
                }
            }
            return Result.success("批量更新完成，成功: " + successCount + " 个用户状态");
        } catch (Exception e) {
            log.error("批量更新管理员用户状态失败: {}", e.getMessage());
            return Result.error("批量更新失败");
        }
    }
}