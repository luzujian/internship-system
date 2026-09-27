package com.internship.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OssObjectReadPolicyTest {
    private static final String ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String HOST = "https://school-files.oss-cn-beijing.aliyuncs.com/";
    @Test
    void rejectsGeneratedKeysWithoutBusinessAuthorization() {
        assertFalse(OssObjectReadPolicy.mayReadGeneric("images/2026/09/abc-123.png"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric("resources/doc/2026/09/abc-123.docx"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric("students/resumes/2026/09/abc-123.pdf"));
    }

    @Test
    void rejectsBackupNamespaceEvenBehindDeploymentPrefix() {
        assertFalse(OssObjectReadPolicy.mayReadGeneric("backups/backup_internship_20260923_120000.sql"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric("school-prod/backups/incremental_internship_20260923_120000.sql"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric("SCHOOL-PROD/BACKUPS/backup.sql"));
    }

    @Test
    void rejectsAmbiguousKeysAndUrls() {
        assertFalse(OssObjectReadPolicy.mayReadGeneric("resources%2f..%2fbackups/backup.sql"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric("resources/../backups/backup.sql"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric("https://example.test/backups/backup.sql"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric("resources\\backups\\backup.sql"));
        assertFalse(OssObjectReadPolicy.mayReadGeneric(""));
    }

    @Test
    void acceptsOnlyGeneratedStudentAttachmentBelongingToRequestedStudent() {
        String key = "student/applications/52/resources/pdf/2026/09/" + ID + ".pdf";
        assertEquals(key, OssObjectReadPolicy.attachmentObjectName(HOST + key,
                "https://oss-cn-beijing.aliyuncs.com", "school-files", "", 52L));
        assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.attachmentObjectName(HOST + key,
                "https://oss-cn-beijing.aliyuncs.com", "school-files", "", 53L));
        assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.attachmentObjectName(
                HOST + "resources/pdf/2026/09/" + ID + ".pdf",
                "https://oss-cn-beijing.aliyuncs.com", "school-files", "", 52L));
    }

    @Test
    void recordBoundStudentReadAcceptsLegacyKeyWithoutOpeningGenericOrNewWrites() {
        String legacy = "resources/pdf/2026/09/" + ID + ".pdf";
        assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.attachmentObjectName(HOST + legacy,
                "oss-cn-beijing.aliyuncs.com", "school-files", "", 52L));
        assertEquals(legacy, OssObjectReadPolicy.recordBoundAttachmentObjectName(HOST + legacy,
                "oss-cn-beijing.aliyuncs.com", "school-files", "", 52L));
        assertFalse(OssObjectReadPolicy.mayReadGeneric(legacy));

        String otherStudent = "student/applications/53/" + legacy;
        assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.recordBoundAttachmentObjectName(
                HOST + otherStudent, "oss-cn-beijing.aliyuncs.com", "school-files", "", 52L));
        assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.recordBoundAttachmentObjectName(
                HOST + "backups/2026/09/" + ID + ".sql",
                "oss-cn-beijing.aliyuncs.com", "school-files", "", 52L));
        assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.recordBoundAttachmentObjectName(
                "https://other-bucket.oss-cn-beijing.aliyuncs.com/" + legacy,
                "oss-cn-beijing.aliyuncs.com", "school-files", "", 52L));
    }

    @Test
    void rejectsBackupsForeignHostsEncodedTraversalAndOtherDeployments() {
        String file = "resources/pdf/2026/09/" + ID + ".pdf";
        assertEquals(file, OssObjectReadPolicy.attachmentObjectName(HOST + file,
                "oss-cn-beijing.aliyuncs.com", "school-files", "", null));
        for (String url : new String[] {
                HOST + "backups/2026/09/" + ID + ".sql",
                HOST + "resources/pdf/2026/09/../../backups/backup.sql",
                HOST + "resources/pdf/2026/09/%2e%2e%2fbackup.sql",
                "https://school-files.oss-cn-beijing.aliyuncs.com.evil.test/" + file,
                "https://other-bucket.oss-cn-beijing.aliyuncs.com/" + file,
                HOST + file + "?versionId=old",
                "http://school-files.oss-cn-beijing.aliyuncs.com/" + file
        }) {
            assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.attachmentObjectName(url,
                    "oss-cn-beijing.aliyuncs.com", "school-files", "", null), url);
        }
        assertEquals("preprod/" + file, OssObjectReadPolicy.attachmentObjectName(HOST + "preprod/" + file,
                "oss-cn-beijing.aliyuncs.com", "school-files", "preprod", null));
        assertThrows(IllegalArgumentException.class, () -> OssObjectReadPolicy.attachmentObjectName(HOST + file,
                "oss-cn-beijing.aliyuncs.com", "school-files", "preprod", null));
    }
}
