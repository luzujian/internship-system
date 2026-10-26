package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.StudentApplication;
import com.internship.entity.dto.InternshipAgreementModels.*;
import com.internship.entity.dto.TeacherAppraisalFileContent;
import com.internship.mapper.InternshipAgreementMapper;
import com.internship.mapper.InternshipConfirmationRecordMapper;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.LegacyWordPreviewConverter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.List;

/** 企业只读取本次确认对应的申请快照和协议版本，重提不能扩大历史企业的材料权限。 */
@Service
public class CompanyConfirmationMaterialsService {
    private final InternshipConfirmationRecordMapper records;
    private final InternshipAgreementMapper agreements;
    private final InternshipAgreementService agreementService;
    private final ObjectMapper json;
    private final AliyunOSSOperator oss;
    private final LegacyWordPreviewConverter wordPreview;

    public CompanyConfirmationMaterialsService(InternshipConfirmationRecordMapper records, InternshipAgreementMapper agreements,
            InternshipAgreementService agreementService, ObjectMapper json, AliyunOSSOperator oss, LegacyWordPreviewConverter wordPreview) {
        this.records = records; this.agreements = agreements; this.agreementService = agreementService;
        this.json = json; this.oss = oss; this.wordPreview = wordPreview;
    }

    public InternshipConfirmationRecord owned(Long companyId, Long recordId) {
        var record = records.findById(recordId);
        if (record == null || companyId == null || !companyId.equals(record.getCompanyId()))
            throw new AccessDeniedException("无权读取该实习确认记录");
        return record;
    }

    public StudentApplication application(Long companyId, Long recordId) throws Exception {
        var record = owned(companyId, recordId);
        if (record.getApplicationSnapshot() == null) return null;
        StudentApplication application = json.readValue(record.getApplicationSnapshot(), StudentApplication.class);
        if (!record.getStudentId().equals(application.getStudentId())) throw new AccessDeniedException("申请材料与实习确认的学生不一致");
        return application;
    }

    public List<AgreementVersion> versions(Long companyId, Long recordId) {
        var record = owned(companyId, recordId);
        String source = record.getSourceApplicationId() == null ? "CONFIRMATION" : "SELF_PRACTICE";
        Long sourceId = record.getSourceApplicationId() == null ? recordId : record.getSourceApplicationId();
        List<AgreementVersion> versions = agreements.findVersions(source, sourceId).stream()
                .filter(v -> record.getStudentId().equals(v.getStudentId())).toList();
        // 自主申请必须固定到本轮提交的版本；不能返回该申请后来更换企业的新版本。
        if (record.getSourceApplicationId() != null) versions = versions.stream()
                .filter(v -> record.getAgreementSubmissionId() != null && record.getAgreementSubmissionId().equals(v.getId())).toList();
        versions.forEach(v -> v.setFiles(agreements.findVersionFiles(v.getId())));
        return versions;
    }

    public TeacherAppraisalFileContent agreementFile(Long companyId, Long recordId, Long fileId, boolean preview) throws Exception {
        var file = versions(companyId, recordId).stream().flatMap(v -> v.getFiles().stream())
                .filter(f -> fileId.equals(f.getId())).findFirst()
                .orElseThrow(() -> new AccessDeniedException("该协议文件不属于本次实习确认"));
        return agreementService.loadFile(file, preview);
    }

    public TeacherAppraisalFileContent applicationMaterial(Long companyId, Long recordId, String material, boolean preview) throws Exception {
        StudentApplication application = application(companyId, recordId);
        String stored = application == null || application.getMaterials() == null ? null : application.getMaterials().get(material);
        if (stored == null || stored.isBlank()) throw new AccessDeniedException("该申请材料不存在");
        byte[] bytes = oss.downloadRecordAttachment(stored, application.getStudentId());
        String path = java.net.URI.create(stored).getPath();
        String extension = path.substring(path.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
        if (preview && "doc".equals(extension)) return new TeacherAppraisalFileContent(material + ".pdf", "application/pdf", wordPreview.convertToPdf(bytes));
        String type = switch (extension) {
            case "pdf" -> "application/pdf";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "doc" -> "application/msword";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            default -> "application/octet-stream";
        };
        return new TeacherAppraisalFileContent(material + "." + extension.replaceAll("[^a-z0-9]", ""), type, bytes);
    }
}
