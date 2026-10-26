package com.internship.controller;

import com.internship.utils.ExcelExports;

import com.internship.anno.Log;
import com.internship.exception.BusinessException;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.AdminUser;
import com.internship.service.AdminUserService;
import com.internship.utils.JwtUtils;
import com.internship.utils.PasswordValidator;
import com.internship.utils.UserCredentialDefaults;
import com.internship.utils.CurrentHolder;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

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

    @Autowired(required = false)
    private com.internship.service.UserCacheService userCacheService;

    public record ProfileUpdate(@NotBlank(message = "用户名不能为空") @Size(max = 50) String username,
                                @Size(max = 20) String phone,
                                @Email(message = "邮箱格式不正确") @Size(max = 254) String email) {
        public ProfileUpdate {
            username = StringUtils.trimToEmpty(username);
            phone = StringUtils.trimToEmpty(phone);
            email = StringUtils.trimToEmpty(email);
        }
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getCurrentProfile() {
        AdminUser admin = adminUserService.findById(requireCurrentAdminId());
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) return Result.error("当前管理员账号不可用");
        admin.setPassword(null);
        return Result.success(admin);
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(operationType = "UPDATE", module = "USER_MANAGEMENT", description = "更新管理员个人资料")
    public Result updateCurrentProfile(@RequestBody @Validated ProfileUpdate profile) {
        AdminUser admin = adminUserService.findById(requireCurrentAdminId());
        if (admin == null || !Integer.valueOf(1).equals(admin.getStatus())) return Result.error("当前管理员账号不可用");
        String oldUsername = admin.getUsername();
        admin.setUsername(profile.username());
        admin.setPhone(profile.phone());
        admin.setEmail(profile.email());
        admin.setPassword(null);
        adminUserService.update(admin);
        if (userCacheService != null) {
            userCacheService.evictUserCache(oldUsername);
            userCacheService.evictUserCache(admin.getUsername());
        }
        return Result.success("资料更新成功");
    }

    private Long requireCurrentAdminId() {
        Long id = CurrentHolder.getUserId();
        if (id == null || !"ROLE_ADMIN".equals(CurrentHolder.getUserRole())) {
            throw new AccessDeniedException("当前身份不是管理员");
        }
        return id;
    }

    // 获取所有管理员用户（分页）
    @GetMapping
    @PreAuthorize("hasAuthority('user:admin:view')")
    public Result getAllAdminUsers(@RequestParam(defaultValue = "1") Integer page,
                                  @RequestParam(defaultValue = "10") Integer pageSize,
                                  @RequestParam(required = false) String keyword) {
        if (StringUtils.isBlank(keyword)) return Result.success(adminUserService.findPage(page,pageSize));
        List<AdminUser> all = filteredAdmins(keyword);
        int size = Math.max(1,pageSize), start = (int)Math.min(all.size(),(long)Math.max(0,page-1)*size);
        return Result.success(PageResult.build((long)all.size(),all.subList(start,(int)Math.min(all.size(),(long)start+size))));
    }

    private List<AdminUser> filteredAdmins(String keyword) {
        return adminUserService.findAll().stream().filter(r -> StringUtils.isBlank(keyword)
                || ExcelExports.text(r.getUsername()).contains(keyword) || ExcelExports.text(r.getPhone()).contains(keyword)).toList();
    }

    @Log(operationType = "EXPORT", module = "USER_MANAGEMENT", description = "导出管理员用户", riskLevel = "MEDIUM", critical = true)
    @GetMapping("/export")
    @PreAuthorize("hasAuthority('user:admin:view')")
    public void exportAdmins(jakarta.servlet.http.HttpServletResponse response,
                             @RequestParam(required = false) String keyword) throws java.io.IOException {
        List<Object[]> rows = filteredAdmins(keyword).stream().map(r -> new Object[]{r.getId(),r.getUsername(),r.getPhone(),
                "admin".equals(r.getUsername())?"超级管理员":"普通管理员",r.getCreateTime(),r.getUpdateTime()}).toList();
        ExcelExports.write(response,"管理员数据",new String[]{"用户ID","用户名","手机号","角色","创建时间","更新时间"},rows);
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
    @Log(operationType = "DELETE", module = "USER_MANAGEMENT", description = "批量删除管理员用户", riskLevel = "HIGH", critical = true)
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
            // 管理员端统一重置口令，服务端固定生效，不信任前端传值。
            String newPassword = UserCredentialDefaults.PASSWORD;

            adminUser.setPassword(newPassword);
            adminUserService.update(adminUser);
            // 清除 Redis 用户缓存，避免被重置用户登录时命中旧密码哈希
            if (userCacheService != null) {
                userCacheService.evictUserCache(adminUser.getUsername());
            }

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
            // 优化：先提取所有ID，批量查询一次，避免N+1问题
            java.util.List<Long> ids = new java.util.ArrayList<>();
            java.util.Map<Long, String> statusMap = new java.util.HashMap<>();
            for (Map<String, Object> statusDTO : statusDTOList) {
                Long id = Long.valueOf(statusDTO.get("id").toString());
                String status = statusDTO.get("status").toString();
                ids.add(id);
                statusMap.put(id, status);
            }

            // 批量查询所有用户
            java.util.List<AdminUser> adminUsers = adminUserService.findByIds(ids);
            java.util.Map<Long, AdminUser> userMap = new java.util.HashMap<>();
            for (AdminUser user : adminUsers) {
                userMap.put(user.getId(), user);
            }

            // 批量更新
            int successCount = 0;
            for (Long id : ids) {
                try {
                    AdminUser adminUser = userMap.get(id);
                    if (adminUser != null) {
                        adminUser.setStatus(Integer.valueOf(statusMap.get(id)));
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
