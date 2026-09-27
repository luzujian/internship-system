package com.internship.utils;

import java.net.URI;
import java.util.regex.Pattern;

/**
 * A bare OSS key does not identify the business record or the user's right to read it.
 * Keep legacy key-based proxies closed until each caller uses an authorized record ID.
 */
public final class OssObjectReadPolicy {
    private OssObjectReadPolicy() { }

    private static final String FILE_NAME = "[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}\\.[A-Za-z0-9]{1,8}";
    private static final String CATEGORY = "(?:files|images|resources/(?:pdf|doc|excel|ppt))";
    private static final Pattern LEGACY_ATTACHMENT = Pattern.compile("^" + CATEGORY + "/\\d{4}/(?:0[1-9]|1[0-2])/" + FILE_NAME + "$");
    private static final Pattern STUDENT_ATTACHMENT = Pattern.compile("^student/applications/[1-9]\\d*/" + CATEGORY + "/\\d{4}/(?:0[1-9]|1[0-2])/" + FILE_NAME + "$");

    public static boolean mayReadGeneric(String objectKey) {
        return false;
    }

    /**
     * Only server-generated attachment paths from this bucket are eligible for record-bound reads.
     * The record and actor authorization must be checked separately before calling this method.
     */
    public static String attachmentObjectName(String storedUrl, String endpoint, String bucket,
                                              String deploymentPrefix, Long studentId) {
        if (storedUrl == null || endpoint == null || bucket == null || bucket.isBlank()
                || !bucket.matches("[A-Za-z0-9.-]+") || storedUrl.length() > 2048) {
            throw new IllegalArgumentException("Invalid attachment URL");
        }
        try {
            URI uri = URI.create(storedUrl);
            URI endpointUri = URI.create(endpoint.contains("://") ? endpoint : "https://" + endpoint);
            String expectedHost = bucket + "." + endpointUri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || !expectedHost.equalsIgnoreCase(uri.getHost()) || uri.getPort() != -1
                    || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || endpointUri.getHost() == null || endpointUri.getPort() != -1
                    || endpointUri.getRawUserInfo() != null || endpointUri.getRawPath() != null
                    && !endpointUri.getRawPath().isEmpty() && !"/".equals(endpointUri.getRawPath())) {
                throw new IllegalArgumentException("Invalid attachment URL");
            }
            String rawPath = uri.getRawPath();
            if (rawPath == null || !rawPath.startsWith("/") || rawPath.indexOf('%') >= 0
                    || rawPath.indexOf('\\') >= 0 || rawPath.indexOf(':') >= 0) {
                throw new IllegalArgumentException("Invalid attachment path");
            }
            String objectName = rawPath.substring(1);
            String prefix = deploymentPrefix == null ? "" : deploymentPrefix.trim();
            if (!prefix.isEmpty()) {
                if (!prefix.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*/?")) {
                    throw new IllegalStateException("Invalid OSS deployment prefix");
                }
                prefix = prefix.endsWith("/") ? prefix : prefix + "/";
                if (!objectName.startsWith(prefix)) throw new IllegalArgumentException("Wrong deployment path");
                objectName = objectName.substring(prefix.length());
            }
            if (studentId == null) {
                if (!LEGACY_ATTACHMENT.matcher(objectName).matches()) throw new IllegalArgumentException("Invalid attachment path");
            } else {
                if (studentId <= 0 || !STUDENT_ATTACHMENT.matcher(objectName).matches()
                        || !objectName.startsWith("student/applications/" + studentId + "/")) {
                    throw new IllegalArgumentException("Attachment owner mismatch");
                }
            }
            return prefix + objectName;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid attachment URL", e);
        }
    }

    /**
     * Legacy student uploads had no student ID in the key. Use this only after a
     * controller has loaded the application by record ID, authorized its owner,
     * and selected this URL from that record's material map. New submissions
     * still go through the strict attachmentObjectName check above.
     */
    public static String recordBoundAttachmentObjectName(String storedUrl, String endpoint, String bucket,
                                                         String deploymentPrefix, Long studentId) {
        if (studentId == null) {
            return attachmentObjectName(storedUrl, endpoint, bucket, deploymentPrefix, null);
        }
        if (studentId <= 0) throw new IllegalArgumentException("Invalid student ID");
        try {
            return attachmentObjectName(storedUrl, endpoint, bucket, deploymentPrefix, studentId);
        } catch (IllegalArgumentException ignored) {
            // This second parse accepts only the old generated files/images/resources
            // path in the expected bucket and deployment. It rejects other students'
            // new paths, backups, traversal, redirects, and foreign hosts.
            return attachmentObjectName(storedUrl, endpoint, bucket, deploymentPrefix, null);
        }
    }
}
