package com.gdmu.auth.config;

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
import com.gdmu.filter.TokenFilter;

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
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        // 允许所有 OPTIONS 请求通过（用于 CORS 预检）
                        .requestMatchers(HttpMethod.OPTIONS).permitAll()
                        // 允许登录、注册和刷新令牌接口无需认证
                        .requestMatchers("/api/auth/login", "/api/auth/auto-login", "/api/auth/register", "/api/auth/refresh-token").permitAll()
                        // 允许企业注册相关接口无需认证
                        .requestMatchers("/api/company/register", "/api/company/check-username", "/api/company/check-status", "/api/company/send-verify-code", "/api/company/recall", "/api/company/update/**").permitAll()
                        // 允许文件上传接口无需认证
                        .requestMatchers("/api/upload/**").permitAll()
                        // 允许上传的静态资源访问
                        .requestMatchers("/api/uploads/**").permitAll()
                        // 允许测试接口无需认证（调试用）
                        .requestMatchers("/api/test/**").permitAll()
                        // 允许教师登录接口无需认证
                        .requestMatchers("/api/teacher/login").permitAll()
                        // 允许注册页面访问专业、班级和院系相关接口
                        .requestMatchers(HttpMethod.GET, "/api/majors", "/api/majors/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/classes", "/api/admin/classes/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/admin/departments", "/api/admin/departments/**").permitAll()
                        // 允许 AI 聊天接口无需认证
                        .requestMatchers("/api/ai/chat", "/api/ai/chat/stream").permitAll()
                        // 允许学生查询 Agent 接口无需认证
                        .requestMatchers("/api/ai/student/query", "/api/ai/student/query/stream", "/api/ai/student/info").permitAll()
                        // 允许资源查询 Agent 接口无需认证
                        .requestMatchers("/api/ai/resource/advanced/query", "/api/ai/resource/advanced/query/stream").permitAll()
                        // 允许 WebSocket 端点访问（认证在握手拦截器中处理）
                        .requestMatchers("/ws/**").permitAll()
                        // 允许公告相关接口访问（权限由Controller的@PreAuthorize控制）
                        .requestMatchers("/api/announcement/**").authenticated()
                        // 允许静态资源访问
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/favicon.ico").permitAll()
                        // 允许 Swagger 文档访问
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 教师企业审核接口（基于权限控制，需要放在更宽泛的路径之前）
                        .requestMatchers("/api/teacher/companies/audit/pending").hasAuthority("user:company:audit")
                        .requestMatchers("/api/teacher/companies/statistics").hasAuthority("user:company:audit")
                        .requestMatchers("/api/teacher/companies/*/audit").hasAuthority("user:company:audit")
                        // 管理员企业管理接口（基于权限控制）
                        .requestMatchers("/api/admin/companies/*/reset-password").hasAuthority("user:company:reset")
                        .requestMatchers("/api/admin/companies/*/recall-audit").hasAuthority("company:recall:audit")
                        // 按角色授权教师接口
                        .requestMatchers("/api/teacher/college/**").hasRole("TEACHER_COLLEGE")
                        .requestMatchers("/api/teacher/department/**").hasRole("TEACHER_DEPARTMENT")
                        .requestMatchers("/api/teacher/counselor/**").hasRole("TEACHER_COUNSELOR")
                        // 其他所有请求需要认证
                        .anyRequest().authenticated()
                );

        // 添加 JWT 令牌过滤器
        http.addFilterBefore(tokenFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public TokenFilter tokenFilter() {
        return new TokenFilter();
    }
}
