package com.gdmu.utils;

import com.aliyun.oss.*;
import com.aliyun.oss.model.OSSObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Component
public class AliyunOSSOperator {

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
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();

        // 获取访问凭证
        String accessKeyId = getAccessKeyId();
        String accessKeySecret = getAccessKeySecret();

        if (accessKeyId == null || accessKeyId.isEmpty() ||
            accessKeySecret == null || accessKeySecret.isEmpty()) {
            throw new IllegalStateException("未找到阿里云 OSS 访问凭证，请设置环境变量 ALIYUN_OSS_ACCESS_KEY_ID 和 ALIYUN_OSS_ACCESS_KEY_SECRET");
        }

        // 生成 Object 路径：category/yyyy/MM/uuid.ext
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        String fileExtension = getFileExtension(originalFilename);
        String newFileName = UUID.randomUUID() + "." + fileExtension;
        String objectName = category + "/" + dir + "/" + newFileName;

        // 上传到 OSS
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(content));
        } finally {
            ossClient.shutdown();
        }

        return buildFileUrl(endpoint, bucketName, objectName);
    }

    /**
     * 使用自定义 objectName 上传文件到阿里云 OSS
     */
    public String uploadWithObjectName(byte[] content, String objectName) throws Exception {
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
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(content));
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

        String accessKeyId = getAccessKeyId();
        String accessKeySecret = getAccessKeySecret();

        if (accessKeyId == null || accessKeyId.isEmpty() ||
            accessKeySecret == null || accessKeySecret.isEmpty()) {
            throw new IllegalStateException("未找到阿里云 OSS 访问凭证");
        }

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
                throw new IllegalArgumentException("Invalid file URL format: " + fileUrl, e);
            }
        } else {
            // 相对路径格式：/files/archives/xxx.pdf 或 files/archives/xxx.pdf
            objectName = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
        }

        log.info("下载 OSS 文件：bucket={}, objectName={}", bucketName, objectName);

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
            log.error("OSS 异常：{}", e.getErrorMessage(), e);
            throw new RuntimeException("下载文件失败：文件不存在或无权限", e);
        } finally {
            outputStream.close();
            ossClient.shutdown();
        }

        return outputStream.toByteArray();
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

        // 解析文件 URL，提取 objectName
        String objectName;
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            if (fileUrl.contains(bucketName + ".")) {
                String baseUrl = "//" + bucketName + ".";
                int startIndex = fileUrl.indexOf(baseUrl) + baseUrl.length();
                objectName = fileUrl.substring(startIndex);
            } else {
                throw new IllegalArgumentException("Invalid file URL format: " + fileUrl);
            }
        } else {
            objectName = fileUrl.startsWith("/") ? fileUrl.substring(1) : fileUrl;
        }

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucketName, objectName);
        } finally {
            ossClient.shutdown();
        }
    }

    // ========== 辅助方法 ==========

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
}
