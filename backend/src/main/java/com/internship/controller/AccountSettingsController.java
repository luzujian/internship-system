package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.service.TeacherUserService;
import com.internship.service.UserCacheService;
import com.internship.utils.PasswordValidator;
import com.internship.service.SmsService;
import com.internship.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/account-settings")
@PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR')")
public class AccountSettingsController {
    @Autowired private com.internship.utils.JwtUtils jwtUtils;
    @Autowired
    private com.internship.service.JwtRedisService jwtRedisService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserCacheService userCacheService;

    @GetMapping("/info")
    public Result getAccountInfo(HttpServletRequest request) {
        Long userId = requireCurrentTeacherId();
        try {
            TeacherUser teacherUser = teacherUserService.findById(userId);
            if (teacherUser == null) {
                return Result.error("用户不存在");
            }

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("id", teacherUser.getId());
            userInfo.put("username", teacherUser.getUsername());
            userInfo.put("name", teacherUser.getName());
            userInfo.put("teacherId", teacherUser.getTeacherUserId());
            userInfo.put("phone", teacherUser.getPhone());
            userInfo.put("email", teacherUser.getEmail());
            userInfo.put("gender", teacherUser.getGender());
            userInfo.put("departmentId", teacherUser.getDepartmentId());
            userInfo.put("teacherType", teacherUser.getTeacherType());

            return Result.success(userInfo);
        } catch (Exception e) {
            log.error("获取账号信息失败: {}", e.getMessage(), e);
            return Result.error("获取账号信息失败");
        }
    }

    @PostMapping("/send-verify-code")
    public Result sendVerifyCode(@RequestBody Map<String, String> request) {
        try {
            String phone = request.get("phone");
            if (phone == null || phone.isEmpty()) {
                return Result.error("手机号不能为空");
            }

            boolean success = smsService.sendVerifyCode(phone);
            if (success) {
                return Result.success("验证码已发送");
            } else {
                return Result.error("验证码发送失败或过于频繁，请稍后再试");
            }
        } catch (Exception e) {
            log.error("发送验证码失败: {}", e.getMessage(), e);
            return Result.error("发送验证码失败");
        }
    }

    @PostMapping("/change-password")
    @Log(operationType = "RESET_PASSWORD", module = "AUTH", description = "修改密码", riskLevel = "HIGH", targetType = "USER", critical = true)
    public Result changePassword(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        Long userId = requireCurrentTeacherId();
        try {
            TeacherUser teacherUser = teacherUserService.findById(userId);
            if (teacherUser == null) {
                return Result.error("用户不存在");
            }

            if (teacherUser.getEmail() == null || teacherUser.getEmail().trim().isEmpty()) {
                return Result.error("请先绑定邮箱后再修改密码，绑定后可使用忘记密码找回");
            }

            String oldPassword = request.get("oldPassword");
            String newPassword = request.get("newPassword");
            String confirmPassword = request.get("confirmPassword");

            if (oldPassword == null || oldPassword.isEmpty()) {
                return Result.error("原密码不能为空");
            }

            if (!passwordEncoder.matches(oldPassword, teacherUser.getPassword())) {
                return Result.error("原密码错误");
            }

            if (newPassword == null || newPassword.isEmpty()) {
                return Result.error("新密码不能为空");
            }

            if (!newPassword.equals(confirmPassword)) {
                return Result.error("两次输入的密码不一致");
            }

            PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
            if (!validationResult.isValid()) {
                return Result.error(validationResult.getMessage());
            }

            if (teacherUser.getPassword() != null && passwordEncoder.matches(newPassword, teacherUser.getPassword())) {
                return Result.error("新密码不能与当前密码相同。");
            }

            jwtRedisService.incrementUserTokenVersion(teacherUser.getTeacherUserId());
            teacherUser.setPassword(newPassword);
            teacherUserService.update(teacherUser);
            // 清除该用户在 Redis 中的缓存，避免登录时仍命中旧密码哈希
            // JWT 中的 username 用的是工号（teacherUserId），缓存 key 基于此
            userCacheService.evictUserCache(teacherUser.getTeacherUserId());

            log.info("用户 {} 修改密码成功", teacherUser.getUsername());
            return Result.success("密码修改成功");
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage(), e);
            return Result.error("修改密码失败");
        }
    }

    @PostMapping("/update-contact")
    @Log(operationType = "UPDATE", module = "AUTH", description = "更新联系方式", riskLevel = "LOW")
    public Result updateContact(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        Long userId = requireCurrentTeacherId();
        try {
            TeacherUser teacherUser = teacherUserService.findById(userId);
            if (teacherUser == null) {
                return Result.error("用户不存在");
            }

            String phone = request.get("phone");
            String email = request.get("email");

            if (phone != null && !phone.trim().isEmpty()) {
                if (!phone.trim().matches("^1[3-9]\\d{9}$")) {
                    return Result.error("请输入正确的手机号码");
                }
                teacherUser.setPhone(phone.trim());
            }

            if (email != null && !email.trim().isEmpty()) {
                if (!email.trim().matches("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$")) {
                    return Result.error("邮箱格式不正确");
                }
                teacherUser.setEmail(email.trim());
            }

            teacherUserService.update(teacherUser);

            log.info("用户 {} 更新联系方式成功", teacherUser.getUsername());
            return Result.success("联系方式更新成功");
        } catch (Exception e) {
            log.error("更新联系方式失败: {}", e.getMessage(), e);
            return Result.error("更新联系方式失败");
        }
    }

    @PostMapping("/logout")
    @Log(operationType = "LOGOUT", module = "AUTH", description = "退出登录", riskLevel = "LOW")
    public Result logout(HttpServletRequest request) {
        try {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) return Result.error("登录令牌不可用");
            jwtUtils.revokeSession(authorization.substring(7));
            Long userId = CurrentHolder.getUserId();
            if (userId != null) {
                log.info("用户 {} 退出登录成功", userId);
            }
            return Result.success("退出登录成功");
        } catch (Exception e) {
            log.error("退出登录失败: {}", e.getMessage(), e);
            return Result.error("退出登录失败");
        }
    }

    private Long requireCurrentTeacherId() {
        String role = CurrentHolder.getUserRole();
        Long userId = CurrentHolder.getUserId();
        if (userId == null || !("ROLE_TEACHER".equals(role)
                || "ROLE_TEACHER_COLLEGE".equals(role)
                || "ROLE_TEACHER_DEPARTMENT".equals(role)
                || "ROLE_TEACHER_COUNSELOR".equals(role))) {
            throw new AccessDeniedException("当前身份不是教师");
        }
        return userId;
    }
}
