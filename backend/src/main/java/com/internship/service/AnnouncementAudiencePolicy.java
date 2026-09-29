package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.Announcement;
import com.internship.entity.User;
import com.internship.mapper.StudentUserMapper;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class AnnouncementAudiencePolicy {
    private final StudentUserMapper students;
    private final ObjectMapper json = new ObjectMapper();
    public AnnouncementAudiencePolicy(StudentUserMapper students) { this.students = students; }

    public boolean canReadPublished(Announcement a, User user) {
        if (a == null || user == null || user.getRole() == null || !"PUBLISHED".equals(a.getStatus())) return false;
        Date now = new Date();
        if ((a.getPublishTime() != null && a.getPublishTime().after(now))
                || (a.getValidFrom() != null && a.getValidFrom().after(now))) return false;
        if ("ROLE_ADMIN".equals(user.getRole())) return true;
        List<String> types = targetTypes(a.getTargetType());
        if (types.contains("ALL")) return true;
        String role = user.getRole();
        if ("ROLE_STUDENT".equals(role)) {
            if (types.contains("STUDENT")) return true;
            if (types.contains("MAJOR")) {
                var student = students.findById(user.getId());
                return student != null && student.getMajorId() != null && contains(a.getTargetValue(), "majorIds", student.getMajorId().toString());
            }
        }
        if (role.startsWith("ROLE_TEACHER")) {
            return types.contains("TEACHER") || (types.contains("TEACHER_TYPE")
                    && contains(a.getTargetValue(), "teacherTypes", role.replace("ROLE_TEACHER_", "")));
        }
        return "ROLE_COMPANY".equals(role) && types.contains("COMPANY");
    }

    public static List<String> targetTypes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        try {
            if (raw.trim().startsWith("[")) return new ObjectMapper().readValue(raw,
                    new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {});
            return Arrays.stream(raw.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (Exception e) { return List.of(); }
    }

    public boolean contains(String value, String key, String expected) {
        try {
            var array = json.readTree(value).path(key);
            if (array.isArray()) for (var item : array) if (expected.equals(item.asText())) return true;
        } catch (Exception ignored) { }
        return false;
    }
}
