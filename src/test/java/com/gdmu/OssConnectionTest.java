package com.gdmu;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.common.comm.SignVersion;

import java.io.ByteArrayInputStream;

/**
 * 阿里云 OSS 连接测试
 *
 * 使用方法：
 * mvn exec:java -Dexec.mainClass="com.gdmu.OssConnectionTest"
 */
public class OssConnectionTest {

    public static void main(String[] args) {
        System.out.println("======================================");
        System.out.println("阿里云 OSS 连接测试");
        System.out.println("======================================");

        // 从环境变量获取配置
        String endpoint = System.getenv("ALIYUN_OSS_ENDPOINT");
        String bucketName = System.getenv("ALIYUN_OSS_BUCKET_NAME");
        String region = System.getenv("ALIYUN_OSS_REGION");
        String accessKeyId = System.getenv("ALIYUN_OSS_ACCESS_KEY_ID");
        String accessKeySecret = System.getenv("ALIYUN_OSS_ACCESS_KEY_SECRET");

        // 如果环境变量没有，尝试从系统属性获取
        if (endpoint == null) endpoint = System.getProperty("ALIYUN_OSS_ENDPOINT", "https://oss-cn-beijing.aliyuncs.com");
        if (bucketName == null) bucketName = System.getProperty("ALIYUN_OSS_BUCKET_NAME", "lzj-java-ai");
        if (region == null) region = System.getProperty("ALIYUN_OSS_REGION", "cn-beijing");
        if (accessKeyId == null) accessKeyId = System.getProperty("ALIYUN_OSS_ACCESS_KEY_ID");
        if (accessKeySecret == null) accessKeySecret = System.getProperty("ALIYUN_OSS_ACCESS_KEY_SECRET");

        System.out.println("配置信息:");
        System.out.println("  Endpoint: " + endpoint);
        System.out.println("  Bucket: " + bucketName);
        System.out.println("  Region: " + region);
        System.out.println("  AccessKeyId: " + (accessKeyId != null ? accessKeyId.substring(0, 10) + "..." : "null"));
        System.out.println("  AccessKeySecret: " + (accessKeySecret != null ? "***" : "null"));
        System.out.println();

        OSS ossClient = null;
        try {
            // 检查凭证是否存在
            if (accessKeyId == null || accessKeyId.isEmpty() ||
                accessKeySecret == null || accessKeySecret.isEmpty()) {
                System.out.println("错误：未找到 OSS 访问凭证！");
                System.out.println("请设置环境变量或使用 -D 参数传递:");
                System.out.println("  ALIYUN_OSS_ACCESS_KEY_ID");
                System.out.println("  ALIYUN_OSS_ACCESS_KEY_SECRET");
                return;
            }

            // 使用静态凭证创建 OSS 客户端
            com.aliyun.oss.OSSClientBuilder builder = new com.aliyun.oss.OSSClientBuilder();
            ossClient = builder.build(endpoint, accessKeyId, accessKeySecret);

            // 测试连接 - 检查 Bucket 是否存在
            System.out.println("正在测试 OSS 连接...");
            boolean exists = ossClient.doesBucketExist(bucketName);

            if (exists) {
                System.out.println("成功！Bucket 存在");

                // 测试上传
                String testKey = "test/oss-connection-test-" + System.currentTimeMillis() + ".txt";
                String testContent = "OSS 连接测试成功 - " + new java.util.Date();

                System.out.println("正在测试上传文件：" + testKey);
                ossClient.putObject(bucketName, testKey,
                    new ByteArrayInputStream(testContent.getBytes("UTF-8")));

                System.out.println("上传成功！");

                // 生成访问 URL
                String fileUrl = "https://" + bucketName + "." +
                    endpoint.replace("https://", "") + "/" + testKey;
                System.out.println("文件访问 URL: " + fileUrl);

                System.out.println();
                System.out.println("======================================");
                System.out.println("OSS 配置正确，可以正常上传文件！");
                System.out.println("======================================");
            } else {
                System.out.println("错误：Bucket " + bucketName + " 不存在！");
                System.out.println("请在阿里云 OSS 控制台创建该 Bucket");
            }

        } catch (OSSException e) {
            System.out.println("OSS 错误:");
            System.out.println("  Error Message: " + e.getErrorMessage());
            System.out.println("  Error Code: " + e.getErrorCode());
            System.out.println("  Request ID: " + e.getRequestId());
        } catch (Exception e) {
            System.out.println("错误：" + e.getMessage());
            e.printStackTrace();
        } finally {
            if (ossClient != null) {
                try {
                    ossClient.shutdown();
                } catch (Exception e) {
                    // 忽略关闭错误
                }
            }
        }
    }
}
