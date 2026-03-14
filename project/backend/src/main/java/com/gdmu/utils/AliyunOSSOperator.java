package com.gdmu.utils;

import com.aliyun.oss.*;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.EnvironmentVariableCredentialsProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.OSSObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
public class AliyunOSSOperator {
    @Autowired
    private AliyunOSSProperties aliyunOSSProperties;

    public String upload(byte[] content, String originalFilename) throws Exception {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();
        String region = aliyunOSSProperties.getRegion();

        // 从环境变量中获取访问凭证。运行本代码示例之前，请确保已设置环境变量OSS_ACCESS_KEY_ID和OSS_ACCESS_KEY_SECRET。
        EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();

        // 填写Object完整路径，例如2024/06/1.png。Object完整路径中不能包含Bucket名称。
        //获取当前系统日期的字符串,格式为 yyyy/MM
        String dir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        //生成一个新的不重复的文件名
        String newFileName = UUID.randomUUID() + originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = dir + "/" + newFileName;

        // 创建OSSClient实例。
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        OSS ossClient = OSSClientBuilder.create()
                .endpoint(endpoint)
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(region)
                .build();

        try {
            ossClient.putObject(bucketName, objectName, new ByteArrayInputStream(content));
        } finally {
        ossClient.shutdown();
    }

    return endpoint.split("//")[0] + "//" + bucketName + "." + endpoint.split("//")[1] + "/" + objectName;
}

    /**
     * 从阿里云OSS下载文件
     * @param fileUrl 文件的完整URL或相对路径
     * @return 文件字节数组
     * @throws Exception 下载过程中的异常
     */
    public byte[] downloadFile(String fileUrl) throws Exception {
        String endpoint = aliyunOSSProperties.getEndpoint();
        String bucketName = aliyunOSSProperties.getBucketName();
        String region = aliyunOSSProperties.getRegion();
        
        // 从环境变量中获取访问凭证
        EnvironmentVariableCredentialsProvider credentialsProvider = CredentialsProviderFactory.newEnvironmentVariableCredentialsProvider();
        
        // 解析文件URL，提取objectName
        String objectName;
        if (fileUrl.startsWith("http://") || fileUrl.startsWith("https://")) {
            // 完整URL格式：https://bucket-name.oss-cn-region.aliyuncs.com/path/to/file
            if (fileUrl.contains(bucketName + ".")) {
                String baseUrl = "//" + bucketName + ".";
                int startIndex = fileUrl.indexOf(baseUrl) + baseUrl.length();
                objectName = fileUrl.substring(startIndex);
            } else {
                throw new IllegalArgumentException("Invalid file URL format: " + fileUrl);
            }
        } else {
            // 相对路径格式：/files/archives/xxx.pdf
            // 直接使用相对路径作为objectName
            objectName = fileUrl;
        }
        
        // 创建OSSClient实例
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        OSS ossClient = OSSClientBuilder.create()
                .endpoint(endpoint)
                .credentialsProvider(credentialsProvider)
                .clientConfiguration(clientBuilderConfiguration)
                .region(region)
                .build();
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            // 获取文件对象
            OSSObject ossObject = ossClient.getObject(bucketName, objectName);
            
            // 读取文件内容
            InputStream inputStream = ossObject.getObjectContent();
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            
            // 关闭输入流
            inputStream.close();
        } finally {
            // 关闭输出流和OSS客户端
            outputStream.close();
            ossClient.shutdown();
        }
        
        return outputStream.toByteArray();
    }

}