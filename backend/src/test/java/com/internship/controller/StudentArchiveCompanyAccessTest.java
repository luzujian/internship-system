package com.internship.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import static org.junit.jupiter.api.Assertions.*;

class StudentArchiveCompanyAccessTest {
    private final StudentArchiveController controller = new StudentArchiveController();

    @Test void companyMustUseAnApplicationInsteadOfTheCurrentPersonalLibrary() {
        assertEquals(HttpStatus.GONE, controller.getCompanyStudentArchives(42L).getStatusCode());
    }
    @Test void anOldDownloadLinkCannotExposeAReplacedPersonalFile() {
        assertEquals(HttpStatus.GONE, controller.downloadArchiveForCompany(11L).getStatusCode());
    }
    @Test void anOldPreviewLinkCannotExposeAReplacedPersonalFile() {
        assertEquals(HttpStatus.GONE, controller.previewArchiveForCompany(11L).getStatusCode());
    }
}
