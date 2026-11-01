package com.internship.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;
import java.util.List;

public final class JobApplicationMaterialModels {
    private JobApplicationMaterialModels() { }

    @Data
    public static class Submission {
        private Long id;
        private Long jobApplicationId;
        private Long companyApplicationId;
        private Long studentId;
        private Long companyId;
        private Date submittedAt;
    }

    @Data
    public static class MaterialFile {
        private Long id;
        private Long submissionId;
        private String fileType;
        private String fileName;
        @JsonIgnore private String filePath;
        private Long fileSize;
        private Integer sortOrder;
    }

    public record Selection(List<Long> archiveIds) { }
    public record View(boolean snapshotAvailable, boolean accessRestricted, Date submittedAt,
                       List<MaterialFile> files) { }
}
