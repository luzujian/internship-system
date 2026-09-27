package com.internship.service;

import com.internship.entity.AdminUser;
import com.internship.entity.CompanyUser;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.mapper.AdminUserMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Resolves the display name for a historical log identity without changing audit data.
 */
@Service
public class LogOperatorNameResolver {
    private final AdminUserMapper adminUserMapper;
    private final TeacherUserMapper teacherUserMapper;
    private final StudentUserMapper studentUserMapper;
    private final CompanyUserMapper companyUserMapper;

    private record OperatorIdentity(String name, String username) {
    }

    public LogOperatorNameResolver(AdminUserMapper adminUserMapper,
                                   TeacherUserMapper teacherUserMapper,
                                   StudentUserMapper studentUserMapper,
                                   CompanyUserMapper companyUserMapper) {
        this.adminUserMapper = adminUserMapper;
        this.teacherUserMapper = teacherUserMapper;
        this.studentUserMapper = studentUserMapper;
        this.companyUserMapper = companyUserMapper;
    }

    public String resolve(String role, String userId, String username) {
        OperatorIdentity identity = resolveIdentity(role, userId, username);
        return identity == null ? null : identity.name();
    }

    public String resolveUsername(String role, String userId, String username) {
        OperatorIdentity identity = resolveIdentity(role, userId, username);
        return identity == null ? null : identity.username();
    }

    private OperatorIdentity resolveIdentity(String role, String userId, String username) {
        if (!StringUtils.hasText(userId) && !StringUtils.hasText(username)) {
            return null;
        }

        Long id = parseId(userId);
        String normalizedRole = normalizeRole(role);
        OperatorIdentity identity = resolveByRole(normalizedRole, id, userId, username);
        if (identity != null) {
            return identity;
        }

        identity = resolveByRole("STUDENT", id, userId, username);
        if (identity != null) {
            return identity;
        }
        return firstNonNull(
                resolveByRole("TEACHER", id, userId, username),
                resolveByRole("ADMIN", id, userId, username),
                resolveByRole("COMPANY", id, userId, username));
    }

    private OperatorIdentity resolveByRole(String role, Long id, String userId, String username) {
        return switch (role) {
            case "STUDENT" -> {
                StudentUser user = findByIdThenIdentifier(
                        id, userId, username,
                        studentUserMapper::findById,
                        studentUserMapper::findByStudentId);
                yield user == null ? null : new OperatorIdentity(user.getName(), user.getUsername());
            }
            case "TEACHER" -> {
                TeacherUser user = findByIdThenIdentifier(
                        id, userId, username,
                        teacherUserMapper::findById,
                        teacherUserMapper::findByTeacherUserId);
                yield user == null ? null : new OperatorIdentity(user.getName(), user.getUsername());
            }
            case "ADMIN" -> {
                AdminUser user = findByIdThenIdentifier(
                        id, userId, username,
                        adminUserMapper::findById,
                        adminUserMapper::findByUsername);
                yield user == null ? null : new OperatorIdentity(user.getName(), user.getUsername());
            }
            case "COMPANY" -> {
                CompanyUser user = findByIdThenIdentifier(
                        id, userId, username,
                        companyUserMapper::findById,
                        companyUserMapper::findByUsername);
                yield user == null ? null : new OperatorIdentity(user.getCompanyName(), user.getUsername());
            }
            default -> null;
        };
    }

    private <T> T findByIdThenIdentifier(Long id,
                                         String userId,
                                         String username,
                                         java.util.function.Function<Long, T> findById,
                                         java.util.function.Function<String, T> findByIdentifier) {
        if (id != null) {
            T user = findById.apply(id);
            if (user != null) {
                return user;
            }
        }
        if (StringUtils.hasText(userId)) {
            T user = findByIdentifier.apply(userId);
            if (user != null) {
                return user;
            }
        }
        if (StringUtils.hasText(username)) {
            return findByIdentifier.apply(username);
        }
        return null;
    }

    private String normalizeRole(String role) {
        String normalized = StringUtils.hasText(role) ? role.trim().toUpperCase() : "";
        if (normalized.startsWith("ROLE_")) {
            normalized = normalized.substring(5);
        }
        if (normalized.startsWith("TEACHER")) {
            return "TEACHER";
        }
        return normalized;
    }

    private Long parseId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private OperatorIdentity firstNonNull(OperatorIdentity... values) {
        for (OperatorIdentity value : values) {
            if (value != null && (StringUtils.hasText(value.name()) || StringUtils.hasText(value.username()))) {
                return value;
            }
        }
        return null;
    }
}
