package com.gdmu.filter;

import com.gdmu.entity.AdminUser;
import com.gdmu.entity.CompanyUser;
import com.gdmu.entity.StudentUser;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.User;
import com.gdmu.service.AdminUserService;
import com.gdmu.service.CompanyUserService;
import com.gdmu.service.StudentUserService;
import com.gdmu.service.TeacherUserService;
import com.gdmu.service.UserService;
import com.gdmu.service.UserCacheService;
import com.gdmu.utils.CurrentHolder;
import com.gdmu.utils.JwtUtils;
import com.gdmu.config.SystemSettingsConfig;
import io.jsonwebtoken.Claims;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;

/**
 * Token 过滤器 - 验证 JWT Token 并设置用户上下文
 * 使用 Redis 缓存用户信息，支持分布式部署
 */
@Slf4j
@Component
public class TokenFilter extends OncePerRequestFilter implements ApplicationContextAware {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserCacheService userCacheService;

    private ApplicationContext applicationContext;
    private UserService userService;
    private UserDetailsService userDetailsService;
    private TeacherUserService teacherUserService;
    private StudentUserService studentUserService;
    private CompanyUserService companyUserService;
    private AdminUserService adminUserService;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    // 懒加载 UserService，避免循环依赖
    private UserService getUserService() {
        if (userService == null) {
            userService = applicationContext.getBean(UserService.class);
        }
        return userService;
    }

    // 懒加载 UserDetailsService，避免循环依赖
    private UserDetailsService getUserDetailsService() {
        if (userDetailsService == null) {
            userDetailsService = applicationContext.getBean(UserDetailsService.class);
        }
        return userDetailsService;
    }

    // 懒加载 TeacherUserService
    private TeacherUserService getTeacherUserService() {
        if (teacherUserService == null) {
            teacherUserService = applicationContext.getBean(TeacherUserService.class);
        }
        return teacherUserService;
    }

    // 懒加载 StudentUserService
    private StudentUserService getStudentUserService() {
        if (studentUserService == null) {
            studentUserService = applicationContext.getBean(StudentUserService.class);
        }
        return studentUserService;
    }

    // 懒加载 CompanyUserService
    private CompanyUserService getCompanyUserService() {
        if (companyUserService == null) {
            companyUserService = applicationContext.getBean(CompanyUserService.class);
        }
        return companyUserService;
    }

    // 懒加载 AdminUserService
    private AdminUserService getAdminUserService() {
        if (adminUserService == null) {
            adminUserService = applicationContext.getBean(AdminUserService.class);
        }
        return adminUserService;
    }

    private User getUserFromCacheOrDB(String username) {
        User cachedUser = userCacheService.getUser(username);
        if (cachedUser != null) {
            log.debug("从 Redis 缓存获取用户：{}", username);
            return cachedUser;
        }

        User user = getUserService().findByUsername(username);
        if (user != null) {
            // 对于企业用户，从company_users表获取完整信息
            if ("ROLE_COMPANY".equals(user.getRole())) {
                CompanyUser companyUser = getCompanyUserService().findByUsername(username);
                if (companyUser != null) {
                    // 创建User对象，使用CompanyUser的ID
                    User companyBaseUser = new User();
                    companyBaseUser.setId(companyUser.getId());
                    companyBaseUser.setUsername(companyUser.getUsername());
                    companyBaseUser.setPassword(companyUser.getPassword());
                    companyBaseUser.setRole(companyUser.getRole());
                    companyBaseUser.setName(companyUser.getCompanyName());
                    companyBaseUser.setCreateTime(companyUser.getCreateTime());
                    companyBaseUser.setUpdateTime(companyUser.getUpdateTime());
                    userCacheService.cacheUser(username, companyBaseUser);
                    log.debug("从数据库获取企业用户并缓存到 Redis: {}", username);
                    return companyBaseUser;
                }
            }
            userCacheService.cacheUser(username, user);
            log.debug("从数据库获取用户并缓存到 Redis: {}", username);
        }
        return user;
    }

    private AdminUser getAdminUserFromCacheOrDB(String username) {
        AdminUser cachedUser = userCacheService.getAdminUser(username);
        if (cachedUser != null) {
            log.debug("从 Redis 缓存获取管理员用户：{}", username);
            return cachedUser;
        }

        AdminUser user = getAdminUserService().findByUsername(username);
        if (user != null) {
            userCacheService.cacheAdminUser(username, user);
            log.debug("从数据库获取管理员用户并缓存到 Redis: {}", username);
        }
        return user;
    }

    private TeacherUser getTeacherUserFromCacheOrDB(String username) {
        TeacherUser cachedUser = userCacheService.getTeacherUser(username);
        if (cachedUser != null) {
            log.debug("从 Redis 缓存获取教师用户：{}", username);
            return cachedUser;
        }

        TeacherUser user = getTeacherUserService().findByTeacherUserId(username);
        if (user != null) {
            userCacheService.cacheTeacherUser(username, user);
            log.debug("从数据库获取教师用户并缓存到 Redis: {}", username);
        }
        return user;
    }

    private StudentUser getStudentUserFromCacheOrDB(String username) {
        StudentUser cachedUser = userCacheService.getStudentUser(username);
        if (cachedUser != null) {
            log.debug("从 Redis 缓存获取学生用户：{}", username);
            return cachedUser;
        }

        StudentUser user = getStudentUserService().findByStudentId(username);
        if (user != null) {
            userCacheService.cacheStudentUser(username, user);
            log.debug("从数据库获取学生用户并缓存到 Redis: {}", username);
        }
        return user;
    }

    private CompanyUser getCompanyUserFromCacheOrDB(String username) {
        CompanyUser cachedUser = userCacheService.getCompanyUser(username);
        if (cachedUser != null) {
            log.debug("从 Redis 缓存获取企业用户：{}", username);
            return cachedUser;
        }

        User user = getUserService().findByUsername(username);
        if (user != null) {
            CompanyUser companyUser = getCompanyUserService().findById(user.getId());
            if (companyUser != null) {
                userCacheService.cacheCompanyUser(username, companyUser);
                log.debug("从数据库获取企业用户并缓存到 Redis: {}", username);
            }
            return companyUser;
        }
        return null;
    }

    private boolean isUserDisabled(String username, String role) {
        try {
            log.info("检查用户状态：username={}, role={}", username, role);
            if (role != null && role.startsWith("ROLE_TEACHER")) {
                TeacherUser teacherUser = getTeacherUserFromCacheOrDB(username);
                boolean disabled = teacherUser != null && teacherUser.getStatus() != null && teacherUser.getStatus() == 0;
                log.info("教师用户状态检查：username={}, status={}, disabled={}", username,
                    teacherUser != null ? teacherUser.getStatus() : "null", disabled);
                return disabled;
            } else if ("ROLE_STUDENT".equals(role)) {
                StudentUser studentUser = getStudentUserFromCacheOrDB(username);
                boolean disabled = studentUser != null && studentUser.getStatus() != null && studentUser.getStatus() == 0;
                log.info("学生用户状态检查：username={}, status={}, disabled={}", username,
                    studentUser != null ? studentUser.getStatus() : "null", disabled);
                return disabled;
            } else if ("ROLE_COMPANY".equals(role)) {
                CompanyUser companyUser = getCompanyUserFromCacheOrDB(username);
                boolean disabled = companyUser != null && companyUser.getStatus() != null && companyUser.getStatus() == 0;
                log.info("企业用户状态检查：username={}, status={}, disabled={}", username,
                    companyUser != null ? companyUser.getStatus() : "null", disabled);
                return disabled;
            } else if ("ROLE_ADMIN".equals(role)) {
                AdminUser adminUser = getAdminUserFromCacheOrDB(username);
                boolean disabled = adminUser != null && adminUser.getStatus() != null && adminUser.getStatus() == 0;
                log.info("管理员用户状态检查：username={}, status={}, disabled={}", username,
                    adminUser != null ? adminUser.getStatus() : "null", disabled);
                return disabled;
            }
        } catch (Exception e) {
            log.error("检查用户状态时发生异常：{}", e.getMessage(), e);
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String requestUri = request.getRequestURI();
        log.debug("处理请求：{}", requestUri);

        boolean isLoginRequest = requestUri.contains("/api/auth/login");
        boolean isRefreshTokenRequest = requestUri.contains("/auth/refresh-token");
        // 检查是否是流式 API 请求
        boolean isStreamingRequest = requestUri.contains("/stream");

        // 对于登录请求和刷新 token 请求，直接放行，不进行 token 验证
        if (isLoginRequest || isRefreshTokenRequest) {
            log.debug("登录或刷新 token 请求，跳过 Token 验证，直接放行");
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader("Authorization");

        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            log.debug("提取到 Token: {}", token);
        }

        if (token != null && !token.isEmpty()) {
            try {
                if (jwtUtils.validateToken(token)) {
                    try {
                        Claims claims = jwtUtils.parseToken(token);
                        String username = claims.get("username", String.class);
                        String role = claims.get("role", String.class);

                        log.debug("Token 验证成功，用户：{}, 角色：{}", username, role);

                        // 检查会话是否超时
                        Long issuedAt = claims.getIssuedAt() != null ? claims.getIssuedAt().getTime() : 0;
                        long currentTime = System.currentTimeMillis();
                        int sessionTimeoutMinutes = SystemSettingsConfig.getSessionTimeout();
                        long sessionTimeoutMillis = sessionTimeoutMinutes * 60 * 1000L;

                        if (currentTime - issuedAt > sessionTimeoutMillis) {
                            log.warn("会话已超时：username={}, issuedAt={}, currentTime={}, timeout={}分钟",
                                    username, issuedAt, currentTime, sessionTimeoutMinutes);
                            // 对于登录请求，不拦截，让登录逻辑处理
                            if (!isLoginRequest) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.setCharacterEncoding("UTF-8");
                                response.getWriter().write("{\"code\": 401, \"message\": \"会话已超时，请重新登录\"}");
                                return;
                            } else {
                                log.debug("登录请求中的过期 token，继续处理请求");
                            }
                        }

                        // 先检查用户状态，如果被禁用则返回 403
                        if (isUserDisabled(username, role)) {
                            log.warn("用户已被禁用，拒绝访问：{}", username);
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json;charset=UTF-8");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"code\": 403, \"message\": \"您的账号已被禁用，请联系管理员\"}");
                            return;
                        }

                        // 使用 UserDetailsService 加载用户信息，这样可以获取完整的权限
                        try {
                            org.springframework.security.core.userdetails.UserDetails userDetails = getUserDetailsService().loadUserByUsername(username);

                            // 设置当前线程用户（从缓存获取）
                            User user = getUserFromCacheOrDB(username);
                            if (user != null) {
                                CurrentHolder.setUser(user);
                            }

                            // 设置 Spring Security 上下文，包含完整的权限信息
                            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                                    userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(authentication);

                            log.info("Security 上下文已设置，用户：{}, 角色：{}, 权限列表：{}",
                                    username, role, userDetails.getAuthorities());
                        } catch (Exception e) {
                            log.error("加载用户信息失败：{}", e.getMessage());
                        }
                    } catch (Exception e) {
                        log.error("JWT token 验证失败：{}", e.getMessage());
                        // 对于非登录请求，验证失败应该返回 401
                        // 但对于流式请求，不直接写入 response，让过滤器链继续执行
                        if (!isLoginRequest && !isStreamingRequest) {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"message\": \"JWT token 验证失败，请重新登录\"}");
                            return;
                        }
                    }
                } else {
                    // 对于登录请求，无效令牌不应阻止处理
                    if (isLoginRequest) {
                        log.debug("登录请求中的无效 JWT token，继续处理请求");
                    } else {
                        log.warn("无效的 JWT token");
                        // 对于非登录请求，无效令牌返回 401
                        // 但对于流式请求，不直接写入 response，让过滤器链继续执行
                        if (!isLoginRequest && !isStreamingRequest) {
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json;charset=UTF-8");
                            response.setCharacterEncoding("UTF-8");
                            response.getWriter().write("{\"message\": \"无效的 JWT token，请重新登录\"}");
                            return;
                        }
                    }
                }
            } catch (Exception e) {
                // 对于登录请求，处理 Token 异常不应阻止处理
                if (isLoginRequest) {
                    log.debug("登录请求处理 Token 时发生异常：{}，继续处理请求", e.getMessage());
                } else {
                    log.error("处理 Token 时发生异常：{}", e.getMessage());
                    // 对于非登录请求，Token 异常返回 401
                    // 但对于流式请求，不直接写入 response，让过滤器链继续执行
                    if (!isLoginRequest && !isStreamingRequest) {
                        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        response.setContentType("application/json;charset=UTF-8");
                        response.setCharacterEncoding("UTF-8");
                        response.getWriter().write("{\"message\": \"JWT token 处理异常，请重新登录\"}");
                        return;
                    }
                }
            }
        } else if (token != null) {
            log.warn("无效的 Authorization 头格式，正确格式：Bearer {token}");
        } else {
            log.debug("请求中未包含 Authorization 头");
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // 注意：不要清理 ThreadLocal 中的用户信息，因为后续的 Controller 方法还需要使用
            // 注意：不要清理 Security 上下文，因为后续的权限检查还需要使用
        }
    }
}
