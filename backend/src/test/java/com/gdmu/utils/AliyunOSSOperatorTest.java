package com.gdmu.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AliyunOSSOperator 集成测试
 * 验证 OSS 文件下载功能是否正常
 */
@SpringBootTest
public class AliyunOSSOperatorTest {

    private static final Logger log = LoggerFactory.getLogger(AliyunOSSOperatorTest.class);

    @Autowired
    private AliyunOSSOperator aliyunOSSOperator;

    @Test
    public void testDownloadFile() {
        // 测试下载 OSS 文件
        String fileUrl = "https://lzj-java-ai.oss-cn-beijing.aliyuncs.com/resources/pdf/2026/03/c6292245-6882-4415-ac17-f7f9d0c1735b.pdf";

        log.info("开始测试 OSS 文件下载：{}", fileUrl);

        try {
            byte[] fileContent = aliyunOSSOperator.downloadFile(fileUrl);

            assertNotNull(fileContent, "下载的文件内容不应为 null");
            assertTrue(fileContent.length > 0, "下载的文件内容不应为空");

            log.info("✅ OSS 文件下载成功！文件大小：{} bytes", fileContent.length);
        } catch (Exception e) {
            log.error("❌ OSS 文件下载失败：{}", e.getMessage(), e);
            fail("下载失败：" + e.getMessage());
        }
    }

    @Test
    public void testDownloadFileWithRelativePath() {
        // 测试使用相对路径下载文件
        String objectName = "resources/pdf/2026/03/c6292245-6882-4415-ac17-f7f9d0c1735b.pdf";

        log.info("开始测试相对路径 OSS 文件下载：{}", objectName);

        try {
            byte[] fileContent = aliyunOSSOperator.downloadFile(objectName);

            assertNotNull(fileContent, "下载的文件内容不应为 null");
            assertTrue(fileContent.length > 0, "下载的文件内容不应为空");

            log.info("✅ OSS 文件下载成功（相对路径）！文件大小：{} bytes", fileContent.length);
        } catch (Exception e) {
            log.error("❌ OSS 文件下载失败（相对路径）：{}", e.getMessage(), e);
            fail("下载失败：" + e.getMessage());
        }
    }
}
