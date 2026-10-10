package com.internship.mapper;

import com.internship.entity.FileUpload;
import com.internship.entity.dto.InternshipAgreementModels.*;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InternshipAgreementMapper {
    List<AgreementRecord> findRecords(AgreementQuery query);
    long countRecords(AgreementQuery query);
    List<ExportFile> findExportFiles(AgreementQuery query);
    List<AgreementVersion> findVersions(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);
    List<AgreementVersion> findVersionsForUpdate(@Param("sourceType") String sourceType, @Param("sourceId") Long sourceId);
    List<AgreementFile> findVersionFiles(@Param("versionId") Long versionId);
    AgreementFile findFile(@Param("fileId") Long fileId);
    FileUpload findUploadForUpdate(@Param("uploadId") Long uploadId);
    List<AgreementVersion> findUploadBindings(@Param("uploadId") Long uploadId);
    int insertVersion(AgreementVersion version);
    int insertFile(@Param("versionId") Long versionId, @Param("uploadId") Long uploadId, @Param("sortOrder") int sortOrder);
    Long lockStudent(@Param("studentId") Long studentId);
}
