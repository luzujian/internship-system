package com.internship.agent.business;

import com.internship.entity.*;
import com.internship.mapper.*;
import com.internship.service.TeacherPermissionService;
import com.internship.config.SystemSettingsConfig;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.*;
import static com.internship.agent.business.AgentTypes.*;

/** Re-resolves signed identity and live account/scope for every task and tool. */
@Service
@RequiredArgsConstructor
public class AgentIdentityService {
    private final JwtUtils jwtUtils;
    private final StudentUserMapper students;
    private final TeacherUserMapper teachers;
    private final CompanyUserMapper companies;
    private final AdminUserMapper admins;
    private final TeacherPermissionService permissions;

    public Actor resolve(String authorization) {
        try {
            if (authorization == null || !authorization.startsWith("Bearer ")) throw unauthorized();
            String token = authorization.substring(7);
            if (!jwtUtils.validateToken(token)) throw unauthorized();
            Claims claims = jwtUtils.parseToken(token);
            if (!"access".equals(claims.get("typ", String.class))) throw unauthorized();
            if (claims.getIssuedAt() == null || System.currentTimeMillis() - claims.getIssuedAt().getTime()
                    > SystemSettingsConfig.getSessionTimeout() * 60_000L) throw unauthorized();
            String username = claims.get("username", String.class);
            String role = claims.get("role", String.class);
            boolean readOnly = Boolean.TRUE.equals(claims.get("isReadOnly", Boolean.class));
            String admin = Objects.toString(claims.get("originalAdminUsername"), "");
            Long id; String name; String type = null; Long department = null, division = null;
            Set<String> codes = Set.of();
            if ("ROLE_STUDENT".equals(role)) {
                StudentUser user = students.findByStudentId(username);
                if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw unauthorized();
                id = user.getId(); name = user.getName();
            } else if (role != null && role.startsWith("ROLE_TEACHER")) {
                TeacherUser user = teachers.findByTeacherUserId(username);
                if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw unauthorized();
                id = user.getId(); name = user.getName(); type = user.getTeacherType();
                if (type == null || !Set.of("COLLEGE", "DEPARTMENT", "COUNSELOR").contains(type)) throw forbidden();
                if (!role.equals("ROLE_TEACHER") && !role.equals("ROLE_TEACHER_" + type)) throw forbidden();
                role = "ROLE_TEACHER_" + type;
                department = positiveOrNull(user.getDepartmentId()); division = positiveOrNull(user.getDivisionId());
                List<String> granted = permissions.getPermissionCodesByRoleCode(type);
                codes = granted == null ? Set.of() : Set.copyOf(granted);
            } else if ("ROLE_COMPANY".equals(role)) {
                CompanyUser user = companies.findByUsername(username);
                if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw unauthorized();
                id = user.getId(); name = user.getCompanyName();
            } else if ("ROLE_ADMIN".equals(role)) {
                AdminUser user = admins.findByUsername(username);
                if (user == null || !Integer.valueOf(1).equals(user.getStatus())) throw unauthorized();
                id = user.getId(); name = user.getName();
            } else throw forbidden();
            String owner = role + ":" + id + ":" + readOnly + ":" + admin;
            return new Actor(owner, id, role, name, type, department, division, readOnly, codes);
        } catch (AgentException e) { throw e; }
        catch (Exception e) { throw unauthorized(); }
    }

    public static void requireStudent(Actor actor) {
        if (!"ROLE_STUDENT".equals(actor.role())) throw forbidden();
    }

    public static void requireTeacher(Actor actor) {
        if (actor.teacherType() == null || !(actor.permissions().contains("view:student_tracking")
                || actor.permissions().contains("menu:student-tracking"))) throw forbidden();
        if (("COLLEGE".equals(actor.teacherType()) && actor.departmentId() == null)
                || ("DEPARTMENT".equals(actor.teacherType()) && actor.divisionId() == null)) {
            throw new AgentException("SCOPE_UNAVAILABLE", "当前教师尚未配置有效负责范围，不能查询全校数据。", 403);
        }
    }

    public static void requireCompany(Actor actor) {
        if (!"ROLE_COMPANY".equals(actor.role())) throw forbidden();
    }

    public static void requireAdmin(Actor actor) {
        if (!"ROLE_ADMIN".equals(actor.role())) throw forbidden();
    }

    private static Long positiveOrNull(String value) {
        try { long id = Long.parseLong(value); return id > 0 ? id : null; }
        catch (Exception e) { return null; }
    }
    public static AgentException forbidden() { return new AgentException("FORBIDDEN", "当前身份没有此查询权限。", 403); }
    private static AgentException unauthorized() { return new AgentException("AUTH_REQUIRED", "登录已失效或账号不可用，请重新登录。", 401); }
}
