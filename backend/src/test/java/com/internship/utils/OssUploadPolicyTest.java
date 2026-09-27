package com.internship.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssUploadPolicyTest {
    private static final String ID = "d1d3d74a-1995-4e5a-a06e-7ec794fca902";

    @Test
    void onlyExplicitBusinessMediaNamesMayBePublic() {
        assertTrue(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "company/media/7/logo/2026/09/" + ID + ".png"));
        assertTrue(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "company/media/7/photo/2026/09/" + ID + ".jpg"));
        assertTrue(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "company/media/7/video/2026/09/" + ID + ".mp4"));
        assertTrue(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "student/avatar/8/2026/09/24/" + ID + ".webp"));
        assertTrue(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "system/logo/2026/09/" + ID + ".bmp"));
    }

    @Test
    void credentialsApplicationsAndArbitraryGenericPathsRemainPrivate() {
        assertFalse(AliyunOSSOperator.isAllowedPublicMediaObjectName(null));
        assertFalse(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "images/2026/09/" + ID + ".png"));
        assertFalse(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "student/applications/8/images/2026/09/" + ID + ".png"));
        assertFalse(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "backups/" + ID + ".sql"));
        assertFalse(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "company/media/7/logo/2026/09/" + ID + ".mp4"));
        assertFalse(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "company/media/7/photo/2026/09/" + ID + ".html"));
        assertFalse(AliyunOSSOperator.isAllowedPublicMediaObjectName(
                "company/media/7/../logo/2026/09/" + ID + ".png"));
    }
}
