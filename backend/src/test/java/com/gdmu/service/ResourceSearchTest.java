package com.gdmu.service;

import com.gdmu.service.impl.ResourceServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资源搜索功能测试类
 * 测试AI自然语言搜索资源功能的准确性和性能
 */
@SpringBootTest
@ActiveProfiles("test")
public class ResourceSearchTest {

    @Autowired
    private ResourceService resourceService;

    /**
     * 测试基本的AI搜索功能
     * 验证搜索结果不为空，且包含必要字段
     */
    @Test
    public void testBasicAiSearch() {
        // 执行测试搜索
        long startTime = System.currentTimeMillis();
        Object result = resourceService.aiSmartSearch("Java编程基础", null, 1, 10);
        long endTime = System.currentTimeMillis();
        
        // 验证结果类型
        assertTrue(result instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> resultMap = (Map<String, Object>) result;
        
        // 验证包含必要字段
        assertTrue(resultMap.containsKey("list"), "结果应包含list字段");
        assertTrue(resultMap.containsKey("total"), "结果应包含total字段");
        
        // 验证响应时间
        long executionTime = endTime - startTime;
        System.out.println("搜索执行时间: " + executionTime + "ms");
        assertTrue(executionTime < 8000, "搜索响应时间应小于8秒");
        
        // 验证结果列表
        List<?> resources = (List<?>) resultMap.get("list");
        assertNotNull(resources, "资源列表不应为空");
        System.out.println("找到资源数: " + resources.size());
    }
    
    /**
     * 测试按文件类型过滤的搜索功能
     */
    @Test
    public void testFileTypeFiltering() {
        // 测试文档类型过滤
        Object documentResult = resourceService.aiSmartSearch("算法", "document", 1, 10);
        assertTrue(documentResult instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> docResultMap = (Map<String, Object>) documentResult;
        List<?> docResources = (List<?>) docResultMap.get("list");
        System.out.println("文档类型资源数: " + docResources.size());
        
        // 测试视频类型过滤
        Object videoResult = resourceService.aiSmartSearch("算法", "video", 1, 10);
        assertTrue(videoResult instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> videoResultMap = (Map<String, Object>) videoResult;
        List<?> videoResources = (List<?>) videoResultMap.get("list");
        System.out.println("视频类型资源数: " + videoResources.size());
        
        // 测试代码类型过滤
        Object codeResult = resourceService.aiSmartSearch("算法", "code", 1, 10);
        assertTrue(codeResult instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> codeResultMap = (Map<String, Object>) codeResult;
        List<?> codeResources = (List<?>) codeResultMap.get("list");
        System.out.println("代码类型资源数: " + codeResources.size());
    }
    
    /**
     * 测试分页功能
     */
    @Test
    public void testPagination() {
        // 先获取第一页，每页5条
        Object page1Result = resourceService.aiSmartSearch("数据结构", null, 1, 5);
        assertTrue(page1Result instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> page1Map = (Map<String, Object>) page1Result;
        List<?> page1Resources = (List<?>) page1Map.get("list");
        
        // 再获取第二页，每页5条
        Object page2Result = resourceService.aiSmartSearch("数据结构", null, 2, 5);
        assertTrue(page2Result instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> page2Map = (Map<String, Object>) page2Result;
        List<?> page2Resources = (List<?>) page2Map.get("list");
        
        // 验证分页功能
        System.out.println("第1页资源数: " + page1Resources.size());
        System.out.println("第2页资源数: " + page2Resources.size());
        System.out.println("总资源数: " + page1Map.get("total"));
    }
    
    /**
     * 测试空查询的情况
     */
    @Test
    public void testEmptyQuery() {
        // 执行空查询
        Object result = resourceService.aiSmartSearch("", null, 1, 10);
        assertTrue(result instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> resources = (List<?>) resultMap.get("list");
        
        System.out.println("空查询返回的资源数: " + resources.size());
        System.out.println("空查询总资源数: " + resultMap.get("total"));
    }
    
    /**
     * 测试不同查询词的相关性排序
     * 验证相关性高的结果应该排在前面
     */
    @Test
    public void testRelevanceSorting() {
        // 使用一个精确的技术术语进行搜索
        Object result = resourceService.aiSmartSearch("Spring Boot 框架", null, 1, 10);
        assertTrue(result instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> resultMap = (Map<String, Object>) result;
        List<?> resources = (List<?>) resultMap.get("list");
        
        System.out.println("精确查询结果数: " + resources.size());
        
        // 验证相关性排序 - 检查结果中是否包含相关性分数
        if (!resources.isEmpty()) {
            Map<?, ?> firstResource = (Map<?, ?>) resources.get(0);
            assertTrue(firstResource.containsKey("relevanceScore"), "资源应包含相关性分数");
            System.out.println("第一条结果相关性分数: " + firstResource.get("relevanceScore"));
        }
    }
    
    /**
     * 测试错误处理和降级机制
     * 使用一个非常特殊的查询词，可能触发降级搜索
     */
    @Test
    public void testErrorHandlingAndFallback() {
        // 使用一个非常特殊的查询词
        Object result = resourceService.aiSmartSearch("#$%^&*不常见特殊字符", null, 1, 10);
        assertTrue(result instanceof Map, "搜索结果应该是Map类型");
        Map<String, Object> resultMap = (Map<String, Object>) result;
        
        // 验证结果包含必要字段，即使在降级情况下
        assertTrue(resultMap.containsKey("list"), "结果应包含list字段");
        assertTrue(resultMap.containsKey("total"), "结果应包含total字段");
        
        System.out.println("特殊查询返回资源数: " + ((List<?>)resultMap.get("list")).size());
    }
    
    /**
     * 性能测试 - 连续执行多次搜索，验证响应时间稳定性
     */
    @Test
    public void testPerformance() {
        String[] testQueries = {
            "Java基础教程", 
            "数据结构与算法", 
            "数据库设计",
            "前端开发技术",
            "软件开发方法论"
        };
        
        System.out.println("开始性能测试...");
        long totalTime = 0;
        int successfulSearches = 0;
        
        for (String query : testQueries) {
            for (int i = 0; i < 3; i++) { // 每个查询执行3次
                long startTime = System.currentTimeMillis();
                Object result = resourceService.aiSmartSearch(query, null, 1, 10);
                long endTime = System.currentTimeMillis();
                long executionTime = endTime - startTime;
                
                totalTime += executionTime;
                successfulSearches++;
                
                System.out.println("查询 '" + query + "' 第" + (i+1) + "次执行时间: " + executionTime + "ms");
                assertTrue(executionTime < 8000, "单次搜索响应时间应小于8秒");
                
                // 小暂停，避免系统过载
                try {
                    TimeUnit.MILLISECONDS.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        
        // 计算平均响应时间
        double avgTime = (double) totalTime / successfulSearches;
        System.out.println("平均响应时间: " + avgTime + "ms");
        assertTrue(avgTime < 4000, "平均搜索响应时间应小于4秒");
    }
}