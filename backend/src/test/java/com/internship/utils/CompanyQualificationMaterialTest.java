package com.internship.utils;

import com.internship.controller.CompanyUserController;
import com.internship.entity.CompanyUser;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.junit.jupiter.api.Assertions.*;

class CompanyQualificationMaterialTest {
    @Test
    void selectsOnlyNamedFieldsAndDocumentSide() throws Exception {
        CompanyUser company = new CompanyUser();
        company.setBusinessLicense("license.pdf");
        company.setLegalIdCard("front.png,back.png");
        company.setPlaquePhoto("plaque.png");
        assertEquals("license.pdf", CompanyQualificationMaterial.storedUrl(company, "businessLicense", 0));
        assertEquals("back.png", CompanyQualificationMaterial.storedUrl(company, "legalIdCard", 1));
        assertEquals("plaque.png", CompanyQualificationMaterial.storedUrl(company, "plaquePhoto", 0));
        assertNull(CompanyQualificationMaterial.storedUrl(company, "businessLicense", 1));
        assertNull(CompanyQualificationMaterial.storedUrl(company, "password", 0));
        assertNull(CompanyQualificationMaterial.storedUrl(company, "legalIdCard", 2));

        assertEquals("hasRole('ADMIN') and hasAuthority('user:company:audit')", CompanyUserController.class
                .getMethod("downloadCompanyQualificationMaterial", Long.class, String.class, int.class)
                .getAnnotation(PreAuthorize.class).value());
    }
}
