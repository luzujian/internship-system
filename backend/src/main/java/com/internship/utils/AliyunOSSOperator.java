package com.internship.utils;

import com.aliyun.oss.*;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class AliyunOSSOperator {
    private static final Pattern PUBLIC_COMPANY_IMAGE = Pattern.compile(
            "company/media/[1-9][0-9]*/(logo|photo)/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.(png|jpg|jpeg|gif|webp)");
    private static final Pattern PUBLIC_COMPANY_VIDEO = Pattern.compile(
            "company/media/[1-9][0-9]*/video/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.(mp4|webm|ogg|ogv|mov|avi|wmv)");
    private static final Pattern PUBLIC_STUDENT_AVATAR = Pattern.compile(
            "student/avatar/[1-9][0-9]*/[0-9]{4}/[0-9]{2}/[0-9]{2}/[0-9a-f-]{36}\\.(png|jpg|jpeg|gif|webp|bmp)");
    private static final Pattern PUBLIC_SYSTEM_LOGO = Pattern.compile(
            "system/logo/[0-9]{4}/[0-9]{2}/[0-9a-f-]{36}\\.(png|jpg|jpeg|gif|webp|bmp)");
    @org.springframework.beans.factory.annotation.Value("${aliyun.oss.object-prefix:}")
    private String objectPrefix = "";

    @Autowired
    private AliyunOSSProperties aliyunOSSProperties;

    /**
     * 上传文件到阿里云 OSS（默认到 files 目录）
     */
    public String upload(byte[] content, String originalFilename) throws Exception {
        return upload(content, originalFilename, "files");
    }

    /**
     * 上传文件到阿里云 OSS（指定分类目录）
     */
    public String upload(byte[] content, String originalFilename, String category) throws Exception {
        // 生成 Object 路径：category/yyyy/MM/uuid.ext
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileExtension = getFileExtension(originalFilename);
        String newFileName = UUID.randomUUID() + "." + fileExtension;
        String objectName = isolatedObjectName(category + "/" + dir + "/" + newFileName);

        return putObject(content, objectName, CannedAccessControlList.Private);
    }

    /**
     * 使用自定义 objectName 上传文件到阿里云 OSS
     */
    public String uploadWithObjectName(byte[] content, String objectName) throws Exception {
        return putObject(content, isolatedObjectName(objectName), CannedAccessControlList.Private);
    }

    /** Public objects are only created by explicit authenticated business-media handlers. */
    public String uploadPublicMediaWithObjectName(byte[] content, String objectName) throws Exception {
        if (!isAllowedPublicMediaObjectName(objectName)) {
            throw new IllegalArgumentException("Not an approved public media object name");
        }
        return putObject(content, isolatedObjectName(objectName), CannedAccessControlList.PublicRead);
    }

    static boolean isAllowedPublicMediaObjectName(String objectName) {
        return objectName != null && (PUBLIC_COMPANY_IMAGE.matcher(objectName).matches()
                || PUBLIC_COMPANY_VIDEO.matcher(objectName).matches()
                || PUBLIC_STUDENT_AVATAR.matcher(objectName).matches()
                || PUBLIC_SYSTEM_LOGO.matcher(objectName).matches());
    }

    private String putObject(byte[] content, String objectName, CannedAccessControlList acl) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("Empty OSS object");
        }
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();

        String accessKeyId = getAccessKeyId();
        String accessKeySecret = getAccessKeySecret();

        if (accessKeyId == null || accessKeyId.isEmpty() ||
            accessKeySecret == null || accessKeySecret.isEmpty()) {
            throw new IllegalStateException("未找到阿里云 OSS 访问凭证");
        }

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(content.length);
            metadata.setObjectAcl(acl);
            ossClient.putObject(new PutObjectRequest(bucketName, objectName,
                    new ByteArrayInputStream(content), metadata));
        } finally {
            ossClient.shutdown();
        }

        return buildFileUrl(endpoint, bucketName, objectName);
    }

    /**
     * 从阿里云 OSS 下载文件
     */
    public byte[] downloadFile(String fileUrl) throws Exception {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();

        // 解析文件 URL，提取 objectName
        String objectName;
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            // URL 格式：https://bucketName.endpoint/objectName 或 https://endpoint/bucketName/objectName
            try {
                java.net.URL url = new java.net.URL(fileUrl);
                String host = url.getHost();
                String path = url.getPath();

                // 去掉路径开头的 /
                objectName = path.startsWith("/") ? path.substring(1) : path;

                // 如果 host 包含 bucketName，说明是虚拟主机格式：bucketName.endpoint
                // 此时 objectName 已经是正确的
                if (!host.contains(bucketName)) {
                    // 可能是 path 格式：/bucketName/objectName
                    if (objectName.startsWith(bucketName + "/")) {
                        objectName = objectName.substring(bucketName.length() + 1);
                    }
                }
            } catch (java.net.MalformedURLException e) {
                throw new IllegalArgumentException("Invalid file URL format", e);
            }
        } else {
            // 相对路径格式：/files/archives/xxx.pdf 或 files/archives/xxx.pdf
            objectName = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
        }

        return downloadObject(endpoint, bucketName, objectName);
    }

    /** Only a resource document selected and authorized by record ID may use this read path. */
    public byte[] downloadResourceDocumentFile(String storedUrl) throws Exception {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();
        String objectName = OssObjectReadPolicy.attachmentObjectName(
                storedUrl, endpoint, bucketName, objectPrefix, null);
        return downloadObject(endpoint, bucketName, objectName);
    }

    protected byte[] downloadObject(String endpoint, String bucketName, String objectName) throws Exception {
        String accessKeyId = getAccessKeyId();
        String accessKeySecret = getAccessKeySecret();
        if (accessKeyId == null || accessKeyId.isEmpty() ||
            accessKeySecret == null || accessKeySecret.isEmpty()) {
            throw new IllegalStateException("未找到阿里云 OSS 访问凭证");
        }

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            OSSObject ossObject = ossClient.getObject(bucketName, objectName);
            InputStream inputStream = ossObject.getObjectContent();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
        } catch (OSSException e) {
            log.error("OSS 下载失败：errorCode={}", e.getErrorCode());
            throw new RuntimeException("下载文件失败：文件不存在或无权限", e);
        } finally {
            outputStream.close();
            ossClient.shutdown();
        }

        return outputStream.toByteArray();
    }

    /** Resolve a stored record attachment without accepting caller-supplied OSS keys. */
    public void validateRecordAttachment(String storedUrl, Long studentId) {
        OssObjectReadPolicy.attachmentObjectName(storedUrl, aliyunOSSProperties.getEndpoint(),
                aliyunOSSProperties.getBucketName(), objectPrefix, studentId);
    }

    public byte[] downloadRecordAttachment(String storedUrl, Long studentId) throws Exception {
        String objectName = OssObjectReadPolicy.recordBoundAttachmentObjectName(storedUrl,
                aliyunOSSProperties.getEndpoint(), aliyunOSSProperties.getBucketName(), objectPrefix, studentId);
        String accessKeyId = getAccessKeyId();
        String accessKeySecret = getAccessKeySecret();
        if (accessKeyId == null || accessKeyId.isBlank() || accessKeySecret == null || accessKeySecret.isBlank()) {
            throw new IllegalStateException("未找到阿里云 OSS 访问凭证");
        }
        // Uploaded files are limited to 10 MiB. Bound legacy objects as well before buffering them.
        final int maxBytes = 20 * 1024 * 1024;
        OSS client = new OSSClientBuilder().build(aliyunOSSProperties.getEndpoint(), accessKeyId, accessKeySecret);
        try {
            OSSObject object = client.getObject(aliyunOSSProperties.getBucketName(), objectName);
            try (InputStream input = object.getObjectContent(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    if (output.size() > maxBytes - read) throw new IllegalArgumentException("Attachment exceeds read limit");
                    output.write(buffer, 0, read);
                }
                return output.toByteArray();
            }
        } finally {
            client.shutdown();
        }
    }

    /**
     * 从 OSS 删除文件
     */
    public void deleteFile(String fileUrl) throws Exception {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();

        String accessKeyId = getAccessKeyId();
        String accessKeySecret = getAccessKeySecret();

        if (accessKeyId == null || accessKeyId.isEmpty() ||
            accessKeySecret == null || accessKeySecret.isEmpty()) {
            throw new IllegalStateException("未找到阿里云 OSS 访问凭证");
        }

        String objectName = extractObjectName(fileUrl, bucketName);
        // A cloned deployment may read inherited attachments, but must never delete the source site's files.
        if (!ownsObject(objectName)) {
            log.info("保留来自其他部署的附件，仅解除当前数据库关联");
            return;
        }

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucketName, objectName);
        } finally {
            ossClient.shutdown();
        }
    }

    // ========== 辅助方法 ==========

    String isolatedObjectName(String name) {
        String prefix = normalizedPrefix();
        if (prefix.isEmpty()) return name;
        if (name == null || name.isBlank() || name.startsWith("/") || name.contains("\\")
                || java.util.Arrays.stream(name.split("/")).anyMatch(p -> p.equals("..") || p.equals("."))) {
            throw new IllegalArgumentException("Invalid object name");
        }
        return name.startsWith(prefix) ? name : prefix + name;
    }

    boolean ownsObject(String name) {
        String prefix = normalizedPrefix();
        return prefix.isEmpty() || (name != null && name.startsWith(prefix));
    }

    private String normalizedPrefix() {
        String prefix = objectPrefix == null ? "" : objectPrefix.trim();
        if (prefix.isEmpty()) return "";
        if (!prefix.matches("[A-Za-z0-9_-]+(?:/[A-Za-z0-9_-]+)*/?")) {
            throw new IllegalStateException("Invalid OSS deployment prefix");
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private String getAccessKeyId() {
        String id = System.getenv("ALIYUN_OSS_ACCESS_KEY_ID");
        if (id == null || id.isEmpty()) {
            id = System.getProperty("ALIYUN_OSS_ACCESS_KEY_ID");
        }
        if (id == null || id.isEmpty()) {
            id = System.getenv("OSS_ACCESS_KEY_ID");
        }
        if (id == null || id.isEmpty()) {
            id = aliyunOSSProperties.getAccessKeyId();
        }
        return id;
    }

    private String extractObjectName(String fileUrl, String bucketName) {
        if (fileUrl == null || fileUrl.isBlank()) {
            throw new IllegalArgumentException("File URL cannot be empty");
        }
        if (!fileUrl.startsWith("http://") && !fileUrl.startsWith("https://")) {
            return fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
        }
        try {
            java.net.URI uri = java.net.URI.create(fileUrl);
            String host = uri.getHost();
            String objectName = uri.getPath();
            objectName = objectName.startsWith("/") ? objectName.substring(1) : objectName;
            if (host == null || !host.startsWith(bucketName + ".")) {
                String bucketPrefix = bucketName + "/";
                if (objectName.startsWith(bucketPrefix)) {
                    objectName = objectName.substring(bucketPrefix.length());
                } else {
                    throw new IllegalArgumentException("Invalid file URL format");
                }
            }
            return objectName;
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid file URL format", e);
        }
    }

    private String getAccessKeySecret() {
        String secret = System.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET");
        if (secret == null || secret.isEmpty()) {
            secret = System.getProperty("ALIYUN_OSS_ACCESS_KEY_SECRET");
        }
        if (secret == null || secret.isEmpty()) {
            secret = System.getenv("OSS_ACCESS_KEY_SECRET");
        }
        if (secret == null || secret.isEmpty()) {
            secret = aliyunOSSProperties.getAccessKeySecret();
        }
        return secret;
    }

    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "jpg";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "jpg";
        }
        return filename.substring(lastDotIndex + 1).toLowerCase();
    }

    private String buildFileUrl(String endpoint, String bucketName, String objectName) {
        String cleanEndpoint = endpoint.replace("https://", "").replace("http://", "");
        return "https://" + bucketName + "." + cleanEndpoint + "/" + objectName;
    }

    /**
     * 根据文件路径生成 OSS URL
     */
    public String getFileUrl(String filePath) {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();
        String cleanEndpoint = endpoint.replace("https://", "").replace("http://", "");
        return "https://" + bucketName + "." + cleanEndpoint + "/" + filePath;
    }
}
