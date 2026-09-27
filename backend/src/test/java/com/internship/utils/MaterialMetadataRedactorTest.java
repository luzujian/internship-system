package com.internship.utils;

import com.internship.entity.CompanyUser;
import com.internship.entity.StudentApplication;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MaterialMetadataRedactorTest {
    @Test
    void responsePreservesPresenceAndExtensionWithoutMutatingStoredRecord() {
        StudentApplication source = new StudentApplication();
        source.setMaterials(Map.of("delayPlan", "https://bucket.example/student/applications/5/files/x.pdf"));
        StudentApplication visible = MaterialMetadataRedactor.studentApplication(source);
        assertEquals("uploaded.pdf", visible.getMaterials().get("delayPlan"));
        assertTrue(source.getMaterials().get("delayPlan").startsWith("https://"));

        CompanyUser company = new CompanyUser();
        company.setBusinessLicense("https://bucket.example/images/uuid.png");
        company.setLegalIdCard("front.jpg,back.jpg");
        CompanyUser redacted = MaterialMetadataRedactor.company(company);
        assertEquals("uploaded.png", redacted.getBusinessLicense());
        assertEquals("uploaded.jpg,uploaded.jpg", redacted.getLegalIdCard());
        assertEquals("https://bucket.example/images/uuid.png", company.getBusinessLicense());
    }
}
