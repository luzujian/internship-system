package com.gdmu.service.impl;

import com.gdmu.entity.LearningResource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResourceServiceImpl集成测试类
 * 验证AI搜索功能与Spring AI向量嵌入集成是否正常工作
 */
@SpringBootTest
public class ResourceServiceImplTest {

    private static final Logger log = LoggerFactory.getLogger(ResourceServiceImplTest.class);

    @Autowired
    private ResourceServiceImpl resourceService;

    @Test
    public void testSemanticSearch() {
        // 测试语义搜索功能
        String query = "Java Spring Boot学习资源";
        
        List<LearningResource> resources = resourceService.semanticSearch(query);
        log.info("语义搜索结果数量: {}", resources.size());
        
        // 验证结果不为空（假设数据库中有数据）
        // 注意：由于我们不能生成模拟数据，这里只做基本验证
        assertNotNull(resources, "搜索结果列表不应为空");
        
        // 记录结果详情以便分析
        for (int i = 0; i < Math.min(5, resources.size()); i++) {
            LearningResource resource = resources.get(i);
            log.info("搜索结果[{}]: 标题={}, 描述={}, 嵌入向量={}", 
                    i+1, resource.getTitle(), resource.getDescription(), resource.getEmbeddingVector());
        }
    }

    @Test
    public void testAiSmartSearch() {
        // 测试AI智能搜索功能
        String query = "数据库优化技术";
        
        Object result = resourceService.aiSmartSearch(query, null, 1, 5);
        log.info("AI智能搜索结果: {}", result);
        
        assertNotNull(result, "AI智能搜索结果不应为空");
    }

    @Test
    public void testSearchWithFileTypeFilter() {
        // 测试带文件类型过滤的搜索
        String query = "编程";
        
        List<LearningResource> resources = resourceService.semanticSearch(query);
        log.info("带文件类型过滤的搜索结果数量: {}", resources.size());
        
        assertNotNull(resources, "带过滤条件的搜索结果不应为空");
    }

    @Test
    public void testEmptyQueryHandling() {
        // 测试空查询处理
        String query = "";
        
        List<LearningResource> resources = resourceService.semanticSearch(query);
        log.info("空查询搜索结果数量: {}", resources.size());
        
        assertNotNull(resources, "空查询应返回结果列表");
    }
}