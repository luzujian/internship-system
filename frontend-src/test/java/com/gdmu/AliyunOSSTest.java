package com.gdmu;

import com.gdmu.utils.AliyunOSSOperator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 阿里云 OSS 上传测试
 *
 * 使用方法：
 * 1. 确保已配置 .env 文件中的 OSS 凭证
 * 2. 运行测试：mvn test -Dtest=AliyunOSSTest
 */
@SpringBootTest
public class AliyunOSSTest {

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Test
    public void testUpload() throws Exception {
        System.out.println("======================================");
        System.out.println("开始测试阿里云 OSS 上传功能");
        System.out.println("======================================");

        // 创建一个简单的测试文件内容
        String testContent = "OSS 上传测试 - " + System.currentTimeMillis();
        byte[] content = testContent.getBytes("UTF-8");
        String fileName = "test-oss-upload-" + System.currentTimeMillis() + ".txt";

        try {
            System.out.println("正在上传文件：" + fileName);
            String url = aliyunOSSOperator.upload(content, fileName);
            System.out.println("上传成功！");
            System.out.println("文件 URL: " + url);
            System.out.println("======================================");
            System.out.println("测试通过 - OSS 配置正确");
            System.out.println("======================================");
        } catch (Exception e) {
            System.out.println("上传失败！");
            System.out.println("错误信息：" + e.getMessage());
            e.printStackTrace();
            throw e;
        }
    }
}
