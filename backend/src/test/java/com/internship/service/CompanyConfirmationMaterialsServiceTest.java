package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.StudentApplication;
import com.internship.entity.dto.InternshipAgreementModels.AgreementFile;
import com.internship.entity.dto.InternshipAgreementModels.AgreementVersion;
import com.internship.mapper.InternshipAgreementMapper;
import com.internship.mapper.InternshipConfirmationRecordMapper;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.LegacyWordPreviewConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompanyConfirmationMaterialsServiceTest {
    @Mock InternshipConfirmationRecordMapper records;
    @Mock InternshipAgreementMapper agreements;
    @Mock InternshipAgreementService agreementService;
    @Mock AliyunOSSOperator oss;
    @Mock LegacyWordPreviewConverter converter;
    private final ObjectMapper json = new ObjectMapper();
    private CompanyConfirmationMaterialsService service;

    @BeforeEach void setup() {
        service = new CompanyConfirmationMaterialsService(records, agreements, agreementService, json, oss, converter);
    }

    private InternshipConfirmationRecord record() {
        var record = new InternshipConfirmationRecord();
        record.setId(10L); record.setCompanyId(20L); record.setStudentId(7L);
        record.setSourceApplicationId(30L); record.setAgreementSubmissionId(40L);
        when(records.findById(10L)).thenReturn(record);
        return record;
    }

    @Test void anotherCompanyCannotReadAnyApplicationOrAgreementMaterial() {
        record();
        assertThrows(AccessDeniedException.class, () -> service.application(99L, 10L));
        assertThrows(AccessDeniedException.class, () -> service.agreementFile(99L, 10L, 50L, true));
        verifyNoInteractions(agreements, agreementService, oss);
    }

    @Test void resubmissionDoesNotExposeNewCompanyAgreementVersionToPreviousCompany() throws Exception {
        record();
        var old = new AgreementVersion(); old.setId(40L); old.setStudentId(7L);
        var newer = new AgreementVersion(); newer.setId(41L); newer.setStudentId(7L);
        var oldFile = new AgreementFile(); oldFile.setId(50L);
        when(agreements.findVersions("SELF_PRACTICE", 30L)).thenReturn(List.of(newer, old));
        when(agreements.findVersionFiles(40L)).thenReturn(List.of(oldFile));
        assertEquals(List.of(old), service.versions(20L, 10L));
        assertThrows(AccessDeniedException.class, () -> service.agreementFile(20L, 10L, 51L, true));
        verify(agreements, never()).findVersionFiles(41L);
        verifyNoInteractions(agreementService, oss);
    }

    @Test void onlyThePinnedAgreementFileCanBeDownloaded() throws Exception {
        record();
        var version = new AgreementVersion(); version.setId(40L); version.setStudentId(7L);
        var file = new AgreementFile(); file.setId(50L);
        when(agreements.findVersions("SELF_PRACTICE", 30L)).thenReturn(List.of(version));
        when(agreements.findVersionFiles(40L)).thenReturn(List.of(file));
        service.agreementFile(20L, 10L, 50L, false);
        verify(agreementService).loadFile(file, false);
    }

    @Test void applicationSnapshotSurvivesResubmissionAndBoundsAttachmentReads() throws Exception {
        var record = record();
        var application = new StudentApplication();
        application.setStudentId(7L); application.setCompany("原企业");
        application.setMaterials(Map.of("接收函", "https://example.test/old-letter.pdf"));
        record.setApplicationSnapshot(json.writeValueAsString(application));
        assertEquals("原企业", service.application(20L, 10L).getCompany());
        when(oss.downloadRecordAttachment("https://example.test/old-letter.pdf", 7L)).thenReturn(new byte[]{1});
        service.applicationMaterial(20L, 10L, "接收函", false);
        assertThrows(AccessDeniedException.class, () -> service.applicationMaterial(20L, 10L, "新企业材料", false));
        verify(oss).downloadRecordAttachment("https://example.test/old-letter.pdf", 7L);
        verifyNoMoreInteractions(oss);
    }

    @Test void wrongStudentSnapshotOrPinnedProtocolCannotExposeFiles() throws Exception {
        var record = record();
        var application = new StudentApplication(); application.setStudentId(8L);
        record.setApplicationSnapshot(json.writeValueAsString(application));
        var version = new AgreementVersion(); version.setId(40L); version.setStudentId(8L);
        when(agreements.findVersions("SELF_PRACTICE", 30L)).thenReturn(List.of(version));
        assertThrows(AccessDeniedException.class, () -> service.applicationMaterial(20L, 10L, "接收函", false));
        assertThrows(AccessDeniedException.class, () -> service.agreementFile(20L, 10L, 50L, false));
        verifyNoInteractions(agreementService, oss);
        verify(agreements, never()).findVersionFiles(40L);
    }
}
