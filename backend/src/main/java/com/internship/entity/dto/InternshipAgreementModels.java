package com.internship.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.util.Date;
import java.util.List;

/** 协议的业务记录、不可变提交版本与文件元数据。 */
public final class InternshipAgreementModels {
    private InternshipAgreementModels() { }

    @Data
    public static class AgreementRecord {
        private String sourceType;
        private Long sourceId;
        private Long studentId;
        private String studentNumber;
        private String studentName;
        private Long classId;
        private String className;
        private String majorName;
        private String companyName;
        private String positionName;
        private String businessStatus;
        private String submissionStatus;
        private Integer version;
        private Integer fileCount;
        private Date submittedAt;
        private Date recordSubmittedAt;
    }

    @Data
    public static class AgreementVersion {
        private Long id;
        private Long studentId;
        private String sourceType;
        private Long sourceId;
        private Integer version;
        private String companyName;
        private String positionName;
        private Date submittedAt;
        private List<AgreementFile> files;
    }

    @Data
    public static class AgreementFile {
        private Long id;
        private Long uploadId;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private Date uploadTime;
        @JsonIgnore private String filePath;
        @JsonIgnore private Long studentId;
        @JsonIgnore private String sourceType;
        @JsonIgnore private Long sourceId;
    }

    @Data
    public static class AgreementDetail {
        private AgreementRecord record;
        private List<AgreementVersion> versions;
        private boolean editable;
    }

    @Data
    public static class CollectionPage {
        private List<AgreementRecord> rows;
        private long total;
        private int current;
        private int pageSize;
        private long responsibleRecordCount;
        private long submittedCount;
        private long notSubmittedCount;
        private List<TeacherAppraisalClassOption> classes;
    }

    @Data
    public static class AgreementQuery {
        private TeacherAppraisalScope scope;
        private Long studentId;
        private String sourceType;
        private Long sourceId;
        private String keyword;
        private Long classId;
        private String status = "ALL";
        private String businessStatus = "ALL";
        private Integer offset;
        private Integer pageSize;
    }

    @Data
    public static class SubmitAgreementRequest {
        private List<Long> fileIds;
        private Integer expectedVersion;
    }

    @Data
    public static class ExportFile {
        private Long id;
        private String sourceType;
        private Long sourceId;
        private String className;
        private String studentNumber;
        private String studentName;
        private String companyName;
        private Integer version;
        private String fileName;
        @JsonIgnore private String filePath;
    }
}
