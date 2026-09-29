package com.internship.entity.dto;

import java.util.List;

public record RecentActivityPageDTO(
        List<RecentActivityItemDTO> items,
        String nextCursor,
        boolean hasMore) {
}
