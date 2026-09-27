package com.internship.entity.dto;

import java.time.OffsetDateTime;

public record RecentActivityItemDTO(
        Long id,
        String eventType,
        String category,
        String title,
        String description,
        String status,
        String statusText,
        OffsetDateTime occurredAt,
        Integer stageNo,
        Long relatedId,
        RecentActivityActionDTO action) {
}
