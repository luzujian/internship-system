package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.User;
import com.gdmu.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 通用用户控制器
 * 处理所有角色通用的用户查询请求
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    @Autowired
    private UserService userService;

    /**
     * 根据ID获取用户信息
     * @param id 用户ID
     * @return 用户信息
     */
    @GetMapping("/{id}")
    public Result getUserById(@PathVariable Long id) {
        User user = userService.findById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        // 安全处理：移除密码信息
        user.setPassword(null);
        return Result.success(user);
    }

    /**
     * 根据角色获取用户列表
     * @param role 用户角色
     * @return 用户列表
     */
    @GetMapping("/role/{role}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result getUsersByRole(@PathVariable String role) {
        List<User> users = userService.findByRole(role);
        // 安全处理：移除所有用户的密码信息
        users.forEach(user -> user.setPassword(null));
        return Result.success(users);
    }
}