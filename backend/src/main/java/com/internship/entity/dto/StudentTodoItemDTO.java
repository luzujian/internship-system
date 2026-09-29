package com.internship.entity.dto;

import java.time.OffsetDateTime;

/** A student-home todo that still has a real action the current student can take. */
public record StudentTodoItemDTO(
        String id,
        String key,
        String title,
        String description,
        String priority,
        OffsetDateTime dueAt,
        Integer stageNo,
        RecentActivityActionDTO action) {
}
