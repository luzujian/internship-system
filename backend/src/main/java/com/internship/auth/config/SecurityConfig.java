package com.internship.auth.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.http.HttpMethod;
import com.internship.filter.TokenFilter;
import jakarta.servlet.DispatcherType;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    @Autowired
    private CustomAccessDeniedHandler customAccessDeniedHandler;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new CustomPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public TokenFilter tokenFilter() {
        return new TokenFilter();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 跳过 ASYNC 调度（SSE 等），避免响应已提交后的重复安全检查
                .securityMatcher(request -> request.getDispatcherType() != DispatcherType.ASYNC)
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 旧文件管理入口无前端调用，且含客户端指定上传者与本地文件读取，整体关闭。
                        .requestMatchers("/api/file-management", "/api/file-management/**").denyAll()
                        // 旧学习资源上传仅生成文件 URL，未保存字节；资源文档上传使用独立的受保护流程。
                        .requestMatchers(HttpMethod.POST, "/api/resources/upload").denyAll()
                        // 分类接口无对应表，也不被现有资源管理页面使用；关闭遗留入口，避免返回数据库错误。
                        .requestMatchers("/api/resources/categories", "/api/resources/categories/**").denyAll()
                        // 学生账号只允许由管理员创建或批量导入；保留旧路径拦截，防止旧客户端重开注册。
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").denyAll()
                        // 企业申请无需邮箱验证码；已停用的发码和申请管理凭据入口不再开放。
                        .requestMatchers(HttpMethod.POST, "/api/company/send-verify-code",
                                "/api/company/send-management-code", "/api/company/verify-management-code",
                                "/api/company/verify-management-secret", "/api/company/application-detail").denyAll()
                        // 学生账号发放必须由管理员发起，原有 user:student:add 功能权限仍在控制器核验。
                        .requestMatchers(HttpMethod.POST,
                                "/api/admin/student-users", "/api/admin/student-users/batch", "/api/admin/student-users/import",
                                "/api/admin/students", "/api/admin/students/batch", "/api/admin/students/import")
                            .hasRole("ADMIN")
                        // 允许所有 OPTIONS 请求通过（用于 CORS 预检）
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        // 登录和刷新令牌接口无需认证。
                        .requestMatchers("/api/auth/login", "/api/auth/refresh-token", "/api/auth/password-rules", "/api/auth/forgot-password/**").permitAll()
                        // 允许企业注册相关接口无需认证
                        .requestMatchers("/api/company/register", "/api/company/check-username", "/api/company/check-status",
                                "/api/company/recall", "/api/company/update/**").permitAll()
                        // 遗留无校验上传和按 URL 删除接口不对外开放。
                        .requestMatchers(HttpMethod.POST, "/api/upload").denyAll()
                        .requestMatchers(HttpMethod.DELETE, "/api/upload/image").denyAll()
                        // 上传要求登录；未绑定业务记录的通用对象键读取由 OssObjectReadPolicy 拒绝。
                        .requestMatchers(HttpMethod.POST, "/api/upload/**").authenticated()
                        .requestMatchers("/api/upload/**").authenticated()
                        .requestMatchers("/api/uploads/**").authenticated()
                        .requestMatchers("/api/test/**").denyAll()
                        // 允许教师登录接口无需认证
                        .requestMatchers("/api/teacher/login").permitAll()
                        // 保留现有专业、班级和院系只读接口。
                        .requestMatchers(HttpMethod.GET, "/api/majors", "/api/majors/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/classes", "/api/admin/classes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/departments", "/api/admin/departments/**").permitAll()
                        // AI 聊天接口需要认证（仅在已登录布局中使用）
                        .requestMatchers("/api/ai/chat", "/api/ai/chat/stream").authenticated()
                        // 允许获取启用AI模型的公开接口无需认证
                        .requestMatchers("/api/admin/ai-model/public/**").permitAll()
                        // 允许错误页面访问（防止异常处理时的连环报错）
                        .requestMatchers("/error").permitAll()
                        // 允许健康检查接口无需认证
                        .requestMatchers("/api/health/**").permitAll()
                        // 允许公开岗位类别接口无需认证
                        .requestMatchers("/api/admin/position-categories/public/**").permitAll()
                        // 允许短信验证码接口无需认证
                        .requestMatchers("/sms/**").permitAll()
                        // 文件下载需要认证
                        .requestMatchers("/api/download/**").authenticated()
                        // 遗留自然语言入口含删除能力；只读Agent通过 /api/agent 使用独立授权工具。
                        .requestMatchers("/api/ai/student/query", "/api/ai/student/query/stream", "/api/ai/student/info").denyAll()
                        .requestMatchers("/api/ai/resource/advanced/query", "/api/ai/resource/advanced/query/stream").authenticated()
                        // 允许 WebSocket 端点访问（认证在握手拦截器中处理）
                        .requestMatchers("/ws/**").permitAll()
                        // 允许公告相关接口访问（权限由Controller的@PreAuthorize控制）
                        .requestMatchers("/api/announcement/**").authenticated()
                        // 允许静态资源访问
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // 模板文件由管理员管理，下载入口也必须遵守同一权限。
                        .requestMatchers("/api/admin/templates/*/download").hasRole("ADMIN")
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").hasRole("ADMIN")
                        // The company-specific list verifies object ownership in its controller.
                        .requestMatchers(HttpMethod.GET, "/api/admin/applications/company/*")
                            .hasAnyRole("ADMIN", "COMPANY")
                        .requestMatchers("/api/admin/applications", "/api/admin/applications/**")
                            .hasRole("ADMIN")
                        .requestMatchers("/api/admin/category-weight", "/api/admin/category-weight/**")
                            .hasRole("ADMIN")
                        // 教师企业审核接口（基于权限控制，需要放在更宽泛的路径之前）
                        .requestMatchers("/api/teacher/companies/audit/pending").hasAuthority("user:company:audit")
                        .requestMatchers("/api/teacher/companies/statistics").hasAuthority("user:company:audit")
                        .requestMatchers("/api/teacher/companies/*/audit").hasAuthority("user:company:audit")
                        // 管理员企业管理接口（基于权限控制）
                        .requestMatchers("/api/admin/companies/*/reset-password").hasAuthority("user:company:reset")
                        // 按角色授权教师接口
                        .requestMatchers("/api/teacher/college/**").hasRole("TEACHER_COLLEGE")
                        .requestMatchers("/api/teacher/department/**").hasRole("TEACHER_DEPARTMENT")
                        .requestMatchers("/api/teacher/counselor/**").hasRole("TEACHER_COUNSELOR")
                        // 教师班级接口（所有教师角色都可访问）
                        .requestMatchers("/api/teacher/classes/**").hasAnyRole("TEACHER", "TEACHER_COLLEGE", "TEACHER_DEPARTMENT", "TEACHER_COUNSELOR")
                        // 教师看板接口（所有教师角色都可访问）
                        .requestMatchers("/api/teacher/dashboard/**").hasAnyRole("TEACHER", "TEACHER_COLLEGE", "TEACHER_DEPARTMENT", "TEACHER_COUNSELOR")
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                );

        // 添加 JWT 令牌过滤器
        http.addFilterBefore(tokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
