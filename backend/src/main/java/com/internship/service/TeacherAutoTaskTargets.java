package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.exception.BusinessException;
import java.util.*;

/** Existing target_scope stores either the legacy role scope or an explicit, immutable student selection. */
public final class TeacherAutoTaskTargets {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> LEGACY = Set.of("OWN_CLASSES", "OWN_DIVISION", "OWN_COLLEGE");
    private TeacherAutoTaskTargets() {}

    public static List<Long> ids(Object value) {
        if (value == null) return List.of();
        if (!(value instanceof List<?> values) || values.size() > 20) throw new BusinessException("指定学生一次最多20人，请重新选择。");
        TreeSet<Long> ids = new TreeSet<>();
        for (Object item : values) {
            try {
                String text = String.valueOf(item);
                if (!text.matches("[1-9][0-9]*")) throw new NumberFormatException();
                ids.add(Long.parseLong(text));
            } catch (RuntimeException e) { throw new BusinessException("指定学生无效，请重新核对。"); }
        }
        return List.copyOf(ids);
    }

    public static String encode(String scope, List<Long> selected) {
        List<Long> ids = ids(selected);
        if (ids.isEmpty()) return scope;
        try {
            String value = JSON.writeValueAsString(Map.of("scope", scope, "studentIds", ids));
            if (value.length() > 500) throw new IllegalArgumentException();
            return value;
        } catch (Exception e) { throw new BusinessException("指定学生范围过长，请减少选择。"); }
    }

    public static List<Long> decode(String value) {
        if (value == null || value.isBlank() || LEGACY.contains(value)) return List.of();
        try {
            var root = JSON.readTree(value);
            if (!root.isObject() || !LEGACY.contains(root.path("scope").asText()) || !root.path("studentIds").isArray() || root.path("studentIds").isEmpty()) throw new IllegalArgumentException();
            return ids(JSON.convertValue(root.get("studentIds"), List.class));
        } catch (Exception e) { throw new BusinessException("任务的指定学生配置无效，请重新预览，不能扩大为全部学生。"); }
    }
}
