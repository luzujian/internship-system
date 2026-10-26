package com.internship.service;

import com.internship.entity.CompanyUser;
import com.internship.entity.SecurityEventLog;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.service.MailService.PwdResetVerifyResult;
import com.internship.utils.JwtUtils;
import com.internship.utils.PasswordValidator;
import com.internship.utils.AuditFailureContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 忘记密码服务（邮箱验证重置）
 * 覆盖学生 / 教师 / 企业三类账号，管理员暂不支持自助找回。
 * 身份校验失败统一对外提示，防止账号枚举。
 */
@Slf4j
@Service
public class PasswordResetService {

    /** 统一身份校验失败提示（防枚举，不区分账号不存在 / 邮箱不匹配 / 管理员 / 禁用） */
    public static final String UNIFIED_AUTH_FAIL_MSG = "无法通过当前账号和邮箱完成身份验证，请检查信息或联系管理员。";

    /** 新密码与旧密码相同时的提示 */
    public static final String SAME_AS_OLD_MSG = "新密码不能与当前密码相同。";

    private static final String RESET_PATH = "/api/auth/forgot-password/reset";

    @Autowired
    private StudentUserService studentUserService;

    @Autowired
    private TeacherUserService teacherUserService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private CompanyUserService companyUserService;

    @Autowired
    private MailService mailService;

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuditLogService auditLogService;

    /**
     * 通过归属校验的账号信息（邮箱与用户名取数据库绑定值，规范化规则集中在 MailService）
     */
    private static class BoundAccount {
        Long id;
        String username;
        String email;
        String role;
        String passwordHash;
    }

    /**
     * 发送找回密码验证码
     */
    public void sendResetCode(String username, String email, String ip) {
        AuditFailureContext.beginAuthentication("RESET_SEND_CODE");
        BoundAccount account = resolve(username, email);
        if (account == null) {
            log.warn("忘记密码发码身份校验失败");
            throw new BusinessException(UNIFIED_AUTH_FAIL_MSG);
        }
        if (!mailService.checkPasswordResetRateLimit(account.username, account.email, ip)) {
            failureIfAbsent("RATE_LIMITED", "发送验证码触发频率限制");
            log.warn("忘记密码发码被限流: username={}", account.username);
            throw new BusinessException("发送过于频繁，请稍后再试");
        }
        if (!mailService.sendPasswordResetCode(account.username, account.email)) {
            failureIfAbsent("MAIL_SEND_FAILED", "邮件发送服务未能完成验证码发送");
            throw new BusinessException("发送验证码失败，请稍后再试");
        }
        log.info("忘记密码验证码已发送: username={}, role={}", account.username, account.role);
    }

    /**
     * 第一步身份确认：归属校验 + 验证码预校验（不消费验证码）。
     * 全部通过才允许前端进入重置密码页；最终改密码时 /reset 会再次消费式校验，
     * 防止绕过第一步直接调用重置接口。
     */
    public void verifyResetCode(String username, String email, String code, String ip) {
        AuditFailureContext.beginAuthentication("RESET_VERIFY_CODE");
        BoundAccount account = resolve(username, email);
        if (account == null) {
            log.warn("忘记密码身份确认失败");
            throw new BusinessException(UNIFIED_AUTH_FAIL_MSG);
        }
        if (!mailService.checkPasswordResetVerifyRateLimit(account.username, account.email, ip)) {
            failureIfAbsent("RATE_LIMITED", "验证码校验触发频率限制");
            log.warn("忘记密码身份确认被限流: username={}", account.username);
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        if (code == null || code.trim().isEmpty()) {
            AuditFailureContext.authenticationFailure("CODE_REQUIRED", "未提供验证码");
            throw new BusinessException("验证码不能为空");
        }
        PwdResetVerifyResult verifyResult =
                mailService.preVerifyPasswordResetCode(account.username, account.email, code.trim());
        switch (verifyResult) {
            case SUCCESS -> log.info("忘记密码身份确认通过: username={}, role={}", account.username, account.role);
            case TOO_MANY_ATTEMPTS -> {
                AuditFailureContext.authenticationFailure("CODE_ATTEMPTS_EXCEEDED", "验证码错误次数超限，已作废");
                recordResetEvent(account, ip, "FAILURE", "忘记密码验证码错误次数超限，已作废");
                throw new BusinessException("验证码错误次数过多，请重新获取");
            }
            default -> {
                failureIfAbsent("CODE_INVALID_OR_EXPIRED", "验证码错误或已过期");
                recordResetEvent(account, ip, "FAILURE", "忘记密码身份确认时验证码校验失败");
                throw new BusinessException("验证码错误或已过期");
            }
        }
    }

    /**
     * 归属查询：返回账号所属角色（TEACHER / STUDENT / COMPANY），供操作日志补操作人角色。
     * 校验不通过（账号不存在、邮箱不匹配、禁用等）返回 null，调用方按未知处理。
     * 方法内所有查询均有异常兜底，不会抛异常。
     */
    public String resolveAccountRole(String username, String email) {
        try {
            BoundAccount account = resolve(username, email);
            return account == null ? null : account.role;
        } catch (Exception e) {
            log.warn("查询找回密码账号角色异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 校验验证码并重置密码。
     * 顺序：归属校验 → 验码限流 → 密码复杂度 → 与旧密码相同性 → 验证并消费验证码
     * → 修改密码（事务提交）→ 旧 Token 失效 → 清失败锁定 → 安全日志。
     * 复杂度/相同性检查在消费验证码之前，不合格不消耗验证码。
     */
    public void resetPassword(String username, String email, String code, String newPassword, String ip) {
        AuditFailureContext.beginAuthentication("RESET_PASSWORD");
        BoundAccount account = resolve(username, email);
        if (account == null) {
            log.warn("忘记密码重置身份校验失败");
            throw new BusinessException(UNIFIED_AUTH_FAIL_MSG);
        }
        if (!mailService.checkPasswordResetVerifyRateLimit(account.username, account.email, ip)) {
            failureIfAbsent("RATE_LIMITED", "密码重置触发验证码校验频率限制");
            log.warn("忘记密码验码被限流: username={}", account.username);
            throw new BusinessException("操作过于频繁，请稍后再试");
        }
        PasswordValidator.ValidationResult validationResult = PasswordValidator.validatePassword(newPassword);
        if (!validationResult.isValid()) {
            AuditFailureContext.authenticationFailure("PASSWORD_POLICY", "新密码不符合当前密码规则");
            throw new BusinessException(validationResult.getMessage());
        }
        if (account.passwordHash != null && passwordEncoder.matches(newPassword, account.passwordHash)) {
            AuditFailureContext.authenticationFailure("PASSWORD_UNCHANGED", "新密码与当前密码相同");
            throw new BusinessException(SAME_AS_OLD_MSG);
        }
        if (code == null || code.trim().isEmpty()) {
            AuditFailureContext.authenticationFailure("CODE_REQUIRED", "未提供验证码");
            throw new BusinessException("验证码不能为空");
        }
        PwdResetVerifyResult verifyResult =
                mailService.verifyPasswordResetCode(account.username, account.email, code.trim());
        switch (verifyResult) {
            case SUCCESS -> { /* 继续 */ }
            case TOO_MANY_ATTEMPTS -> {
                AuditFailureContext.authenticationFailure("CODE_ATTEMPTS_EXCEEDED", "验证码错误次数超限，已作废");
                recordResetEvent(account, ip, "FAILURE", "忘记密码验证码错误次数超限，已作废");
                throw new BusinessException("验证码错误次数过多，请重新获取");
            }
            default -> {
                failureIfAbsent("CODE_INVALID_OR_EXPIRED", "验证码错误或已过期");
                recordResetEvent(account, ip, "FAILURE", "忘记密码验证码校验失败");
                throw new BusinessException("验证码错误或已过期");
            }
        }
        // 事务内提交密码修改；后续副作用各自守卫，避免“改了密码但旧 Token 没失效”无声失败
        userService.updatePassword(account.username, newPassword);
        boolean tokenInvalidated = true;
        try {
            jwtUtils.incrementUserTokenVersion(account.username);
        } catch (Exception e) {
            tokenInvalidated = false;
            AuditFailureContext.authenticationFailure("TOKEN_INVALIDATION_FAILED", "密码已重置，但旧会话失效处理失败", e);
            recordResetEvent(account, ip, "FAILURE", "密码已重置，但旧会话失效处理失败");
            log.error("重置密码后旧 Token 失效失败: username={}, {}", account.username, e.getMessage());
        }
        try {
            loginAttemptService.clearFailures(account.username);
        } catch (Exception e) {
            AuditFailureContext.authenticationFailure("LOGIN_STATE_CLEAR_FAILED", "密码已重置，但登录失败状态清理失败", e);
            recordResetEvent(account, ip, "FAILURE", "密码已重置，但登录失败状态清理失败");
            log.warn("重置密码后清除登录失败记录失败: username={}, {}", account.username, e.getMessage());
        }
        recordResetEvent(account, ip, "SUCCESS",
                tokenInvalidated ? "邮箱验证重置密码成功" : "邮箱验证重置密码成功，但旧 Token 失效可能未完成");
        log.info("邮箱验证重置密码成功: username={}, role={}, tokenInvalidated={}",
                account.username, account.role, tokenInvalidated);
    }

    /**
     * 归属校验：账号存在、状态启用、输入邮箱与绑定邮箱一致。
     * 企业仅审核通过后可自助找回，只认注册申请时登记的个人邮箱 email 字段（非 contactEmail）。
     * 教师优先（与登录优先级一致），其次学生，最后企业。
     *
     * @return 校验通过返回归属账号，否则返回 null（调用方统一提示）
     */
    private BoundAccount resolve(String username, String email) {
        String inputUser = MailService.normalizeUsername(username);
        String inputEmail = MailService.normalizeEmail(email);
        if (inputUser.isEmpty() || inputEmail.isEmpty()) {
            AuditFailureContext.authenticationFailure("IDENTITY_INPUT_REQUIRED", "找回密码缺少账号或邮箱");
            return null;
        }
        Exception lookupFailure = null;

        // 管理员暂不支持自助找回：命中管理员账号同样返回 null，对外不区分
        try {
            if (adminUserService.findByUsername(inputUser) != null) {
                AuditFailureContext.authenticationFailure("SELF_SERVICE_UNAVAILABLE", "该类账号不支持邮箱自助找回");
                log.warn("管理员账号尝试邮箱自助找回，已拒绝: username={}", inputUser);
                return null;
            }
        } catch (Exception e) {
            lookupFailure = e;
            log.warn("查询管理员账号异常: {}", e.getMessage());
        }

        try {
            TeacherUser teacher = teacherUserService.findByTeacherUserId(inputUser);
            if (teacher != null) {
                return match(teacher.getId(), teacher.getTeacherUserId(), teacher.getEmail(), teacher.getStatus(),
                        teacher.getPassword(), "TEACHER", inputEmail);
            }
        } catch (Exception e) {
            lookupFailure = e;
            log.warn("查询教师账号异常: {}", e.getMessage());
        }

        try {
            StudentUser student = studentUserService.findByStudentId(inputUser);
            if (student != null) {
                return match(student.getId(), student.getStudentId(), student.getEmail(), student.getStatus(),
                        student.getPassword(), "STUDENT", inputEmail);
            }
        } catch (Exception e) {
            lookupFailure = e;
            log.warn("查询学生账号异常: {}", e.getMessage());
        }

        try {
            CompanyUser company = companyUserService.findByUsername(inputUser);
            if (company != null) {
                // 待审核/驳回申请的邮箱尚未验证，不能用邮箱重置申请管理凭据。
                if (!Integer.valueOf(1).equals(company.getAuditStatus())) {
                    AuditFailureContext.authenticationFailure("COMPANY_NOT_APPROVED", "企业注册申请尚未审核通过");
                    return null;
                }
                // 仅认注册邮箱 email，不认业务联系邮箱 contactEmail
                return match(company.getId(), company.getUsername(), company.getEmail(), company.getStatus(),
                        company.getPassword(), "COMPANY", inputEmail);
            }
        } catch (Exception e) {
            lookupFailure = e;
            log.warn("查询企业账号异常: {}", e.getMessage());
        }

        if (lookupFailure != null) {
            AuditFailureContext.authenticationFailure("ACCOUNT_LOOKUP_FAILED", "账号查询服务异常，未能完成身份校验", lookupFailure);
        } else {
            AuditFailureContext.authenticationFailure("ACCOUNT_NOT_FOUND", "未找到对应找回密码账号");
        }
        return null;
    }

    /**
     * 单账号匹配：状态启用且绑定邮箱非空且与输入邮箱一致（大小写不敏感）。
     * 输入邮箱已在 resolve 中规范化（trim + lowercase），此处同样规范化绑定值后比较。
     */
    private BoundAccount match(Long id, String username, String boundEmail, Integer status, String passwordHash,
                               String role, String normalizedInputEmail) {
        if (status != null && status == 0) {
            AuditFailureContext.authenticationFailure("ACCOUNT_DISABLED", "账号已被禁用");
            return null;
        }
        String normalizedBound = MailService.normalizeEmail(boundEmail);
        if (normalizedBound.isEmpty() || !normalizedBound.equals(normalizedInputEmail)) {
            AuditFailureContext.authenticationFailure(normalizedBound.isEmpty() ? "EMAIL_UNBOUND" : "EMAIL_MISMATCH",
                    normalizedBound.isEmpty() ? "账号尚未绑定找回密码邮箱" : "输入邮箱与账号绑定邮箱不一致");
            return null;
        }
        BoundAccount account = new BoundAccount();
        account.id = id;
        account.username = username;
        account.email = normalizedBound;
        account.role = role;
        account.passwordHash = passwordHash;
        return account;
    }

    private void failureIfAbsent(String code, String reason) {
        if (!AuditFailureContext.hasAuthenticationFailure()) AuditFailureContext.authenticationFailure(code, reason);
    }

    private void recordResetEvent(BoundAccount account, String ip, String result, String description) {
        try {
            SecurityEventLog event = new SecurityEventLog();
            event.setEventType("PASSWORD_RESET");
            event.setUserId("SUCCESS".equals(result) && account.id != null ? String.valueOf(account.id) : null);
            event.setUsername(account.username);
            event.setUserRole("SUCCESS".equals(result) ? "ROLE_" + account.role : "UNKNOWN");
            event.setRiskLevel(AuditLogService.RISK_HIGH);
            event.setResult(result);
            event.setIpAddress(ip);
            event.setHttpMethod("POST");
            event.setRequestPath(RESET_PATH);
            if (org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
                    instanceof org.springframework.web.context.request.ServletRequestAttributes attributes) {
                event.setRequestPath(attributes.getRequest().getRequestURI());
            }
            event.setDescription(description);
            if ("FAILURE".equals(result)) {
                event.setErrorCode("PASSWORD_RESET_FAILED");
                event.setDetailJson(AuditFailureContext.authenticationDetailJson());
            }
            auditLogService.recordSecurityEvent(event);
        } catch (Exception e) {
            log.error("记录找回密码安全事件失败: {}", e.getMessage());
        }
    }
}
