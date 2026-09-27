package com.internship.entity.dto;

import java.util.Map;

public record RecentActivityActionDTO(
        String key,
        Long targetId,
        Map<String, Object> params) {
}
