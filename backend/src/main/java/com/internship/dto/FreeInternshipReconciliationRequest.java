package com.internship.dto;

import java.util.List;

public record FreeInternshipReconciliationRequest(
        List<StudentIdMapping> mappings,
        String confirmationToken
) {
    public List<StudentIdMapping> safeMappings() {
        return mappings == null ? List.of() : List.copyOf(mappings);
    }

    public record StudentIdMapping(Long legacyStudentId, Long targetStudentId) {
    }
}
