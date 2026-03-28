package com.gdmu.controller;

import com.gdmu.entity.Result;
import com.gdmu.entity.TeacherUser;
import com.gdmu.service.TeacherUserService;
import com.gdmu.service.SmsService;
import com.gdmu.utils.CurrentHolder;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/account-settings")
public class AccountSettingsController {

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private SmsService smsService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/info")
    public Result getAccountInfo(HttpServletRequest request) {
        try {
            Long userId = CurrentHolder.getUserId();
            if (userId == null) {
                return Result.error("未登录或登录已过期");
            }

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

            if (!smsService.checkRateLimit(phone)) {
                return Result.error("验证码发送过于频繁，请稍后再试");
            }

            boolean success = smsService.sendVerifyCode(phone);
            if (success) {
                return Result.success("验证码已发送");
            } else {
                return Result.error("验证码发送失败");
            }
        } catch (Exception e) {
            log.error("发送验证码失败: {}", e.getMessage(), e);
            return Result.error("发送验证码失败");
        }
    }

    @PostMapping("/change-password")
    public Result changePassword(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            Long userId = CurrentHolder.getUserId();
            if (userId == null) {
                return Result.error("未登录或登录已过期");
            }

            TeacherUser teacherUser = teacherUserService.findById(userId);
            if (teacherUser == null) {
                return Result.error("用户不存在");
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

            if (newPassword.length() < 6 || newPassword.length() > 20) {
                return Result.error("新密码长度必须在6-20位之间");
            }

            if (!newPassword.equals(confirmPassword)) {
                return Result.error("两次输入的密码不一致");
            }

            // 使用 PasswordEncoder 加密新密码
            teacherUser.setPassword(passwordEncoder.encode(newPassword));
            teacherUserService.update(teacherUser);

            log.info("用户 {} 修改密码成功", teacherUser.getUsername());
            return Result.success("密码修改成功");
        } catch (Exception e) {
            log.error("修改密码失败: {}", e.getMessage(), e);
            return Result.error("修改密码失败");
        }
    }

    @PostMapping("/update-contact")
    public Result updateContact(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        try {
            Long userId = CurrentHolder.getUserId();
            if (userId == null) {
                return Result.error("未登录或登录已过期");
            }

            TeacherUser teacherUser = teacherUserService.findById(userId);
            if (teacherUser == null) {
                return Result.error("用户不存在");
            }

            String phone = request.get("phone");
            String email = request.get("email");

            if (phone != null && !phone.isEmpty()) {
                teacherUser.setPhone(phone);
            }

            if (email != null && !email.isEmpty()) {
                teacherUser.setEmail(email);
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
    public Result logout(HttpServletRequest request) {
        try {
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
}
