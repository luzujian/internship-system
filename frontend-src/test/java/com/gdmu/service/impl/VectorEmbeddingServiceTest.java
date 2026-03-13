package com.gdmu.service.impl;

import com.gdmu.service.VectorEmbeddingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VectorEmbeddingService测试类
 * 验证Spring AI向量嵌入功能是否正常工作
 */
@SpringBootTest
public class VectorEmbeddingServiceTest {

    private static final Logger log = LoggerFactory.getLogger(VectorEmbeddingServiceTest.class);

    @Autowired
    private VectorEmbeddingService vectorEmbeddingService;

    @Test
    public void testGenerateEmbedding() {
        // 测试正常文本生成嵌入
        String text = "Java Spring Boot学习资源";
        String embedding = vectorEmbeddingService.generateEmbedding(text);
        
        log.info("生成的嵌入向量: {}", embedding);
        assertNotNull(embedding, "嵌入向量不应为空");
        assertFalse(embedding.equals("[]"), "嵌入向量不应为空数组");
        assertFalse(embedding.equals("[0.0]"), "嵌入向量不应为默认值");
        assertTrue(vectorEmbeddingService.isValidEmbedding(embedding), "嵌入向量应被验证为有效");
    }

    @Test
    public void testEmptyTextHandling() {
        // 测试空文本处理
        String emptyEmbedding = vectorEmbeddingService.generateEmbedding("");
        assertEquals("[]", emptyEmbedding, "空文本应返回空数组");
        assertFalse(vectorEmbeddingService.isValidEmbedding(emptyEmbedding), "空嵌入应被验证为无效");
    }

    @Test
    public void testEmbeddingDimension() {
        // 验证嵌入维度
        int dimension = vectorEmbeddingService.getEmbeddingDimension();
        assertTrue(dimension > 0, "嵌入维度应大于0");
        log.info("嵌入向量维度: {}", dimension);
    }

    @Test
    public void testSimilarTexts() {
        // 测试相似文本生成的嵌入（仅作日志记录，不做严格断言，因为向量会变化）
        String text1 = "Java编程教程";
        String text2 = "Java开发指南";
        
        String embedding1 = vectorEmbeddingService.generateEmbedding(text1);
        String embedding2 = vectorEmbeddingService.generateEmbedding(text2);
        
        log.info("文本1嵌入: {}", embedding1);
        log.info("文本2嵌入: {}", embedding2);
        
        assertTrue(vectorEmbeddingService.isValidEmbedding(embedding1), "文本1嵌入应有效");
        assertTrue(vectorEmbeddingService.isValidEmbedding(embedding2), "文本2嵌入应有效");
    }
}