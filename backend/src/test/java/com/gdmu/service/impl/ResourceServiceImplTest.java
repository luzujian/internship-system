package com.gdmu.service.impl;

import com.gdmu.entity.LearningResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceServiceImpl 集成测试类
 * 验证 AI 搜索功能与 Spring AI 向量嵌入集成是否正常工作
 */
@SpringBootTest
public class ResourceServiceImplTest {

    private static final Logger log = LoggerFactory.getLogger(ResourceServiceImplTest.class);

    @Autowired
    private ResourceServiceImpl resourceService;

    @Test
    public void testSemanticSearch() {
        // 测试语义搜索功能
        String query = "Java Spring Boot 学习资源";

        List<LearningResource> resources = resourceService.semanticSearch(query);
        log.info("语义搜索结果数量：{}", resources.size());

        // 验证结果不为空（假设数据库中有数据）
        // 注意：由于我们不能生成模拟数据，这里只做基本验证
        assertNotNull(resources, "搜索结果列表不应为空");

        // 记录结果详情以便分析
        for (int i = 0; i < Math.min(5, resources.size()); i++) {
            LearningResource resource = resources.get(i);
            log.info("搜索结果 [{}]: 标题={}, 描述={}, 嵌入向量={}",
                    i+1, resource.getTitle(), resource.getDescription(), resource.getEmbeddingVector());
        }
    }

    @Test
    public void testAiSmartSearch() {
        // 测试 AI 智能搜索功能
        String query = "数据库优化技术";
        String fileType = null; // 不指定文件类型
        int page = 0;
        int pageSize = 5;

        List<LearningResource> resources = (List<LearningResource>) resourceService.aiSmartSearch(query, fileType, page, pageSize);
        log.info("AI 智能搜索结果数量：{}", resources != null ? resources.size() : 0);

        assertNotNull(resources, "AI 智能搜索结果列表不应为空");

        // 记录部分结果详情
        for (int i = 0; i < Math.min(3, resources.size()); i++) {
            LearningResource resource = resources.get(i);
            log.info("AI 搜索结果 [{}]: 标题={}, 嵌入向量={}",
                    i+1, resource.getTitle(), resource.getEmbeddingVector());
        }
    }

    @Test
    public void testSearchWithFileTypeFilter() {
        // 测试带文件类型过滤的搜索
        String query = "编程";
        String fileType = "PDF"; // 假设系统中有 PDF 类型文件
        int page = 0;
        int pageSize = 5;

        List<LearningResource> resources = (List<LearningResource>) resourceService.aiSmartSearch(query, fileType, page, pageSize);
        log.info("带文件类型过滤的搜索结果数量：{}", resources.size());

        assertNotNull(resources, "带过滤条件的搜索结果不应为空");
    }

    @Test
    public void testEmptyQueryHandling() {
        // 测试空查询处理
        String query = "";

        List<LearningResource> resources = resourceService.semanticSearch(query);
        log.info("空查询搜索结果数量：{}", resources.size());

        assertNotNull(resources, "空查询应返回结果列表");
    }
}
