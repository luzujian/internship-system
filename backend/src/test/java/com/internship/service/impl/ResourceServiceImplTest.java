package com.internship.service.impl;

import com.internship.entity.LearningResource;
import com.internship.mapper.LearningResourceMapper;
import com.internship.mapper.ResourceCategoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResourceServiceImplTest {
    @Mock
    private LearningResourceMapper learningResourceMapper;

    @Mock
    private ResourceCategoryMapper resourceCategoryMapper;

    private ResourceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResourceServiceImpl(learningResourceMapper, resourceCategoryMapper);
    }

    @Test
    void semanticSearchReturnsMapperResources() {
        when(learningResourceMapper.findSimilarResources(any()))
                .thenReturn(List.of(resource(1L, "Java Spring Boot")));

        List<LearningResource> resources = service.semanticSearch("Java Spring Boot");

        assertEquals(1, resources.size());
        assertEquals("Java Spring Boot", resources.getFirst().getTitle());
    }

    @Test
    void aiSmartSearchReturnsPagedMapContract() {
        when(learningResourceMapper.findSimilarResourcesByType(any()))
                .thenReturn(List.of(resource(2L, "数据库优化技术")));
        when(learningResourceMapper.countSimilarResourcesByType(any())).thenReturn(1L);

        Object result = service.aiSmartSearch("数据库优化技术", null, 0, 5);

        Map<?, ?> pagedResult = assertMap(result);
        assertEquals(1, ((Number) pagedResult.get("total")).intValue());
        List<?> list = assertList(pagedResult.get("list"));
        assertEquals(1, list.size());
        assertEquals("数据库优化技术", ((Map<?, ?>) list.getFirst()).get("title"));
    }

    @Test
    void aiSmartSearchIgnoresFileTypeForCurrentSearchContract() {
        when(learningResourceMapper.findSimilarResourcesByType(any())).thenReturn(List.of());
        when(learningResourceMapper.countSimilarResourcesByType(any())).thenReturn(0L);

        Map<?, ?> result = assertMap(service.aiSmartSearch("编程", "PDF", 1, 5));

        assertEquals(0, ((Number) result.get("total")).intValue());
        assertTrue(assertList(result.get("list")).isEmpty());
        verify(learningResourceMapper).findSimilarResourcesByType(any());
    }

    @Test
    void emptyQueryReturnsPublishedResourcesWithPagedMapContract() {
        when(learningResourceMapper.selectByStatus("APPROVED"))
                .thenReturn(List.of(resource(3L, "空查询资源")));

        Map<?, ?> result = assertMap(service.aiSmartSearch("", null, 2, 10));

        assertEquals(1, ((Number) result.get("total")).intValue());
        assertTrue(assertList(result.get("list")).isEmpty());
    }

    @Test
    void publishedResourcesUseTheStatusAllowedByTheLearningResourcesTable() {
        when(learningResourceMapper.selectByStatus("APPROVED"))
                .thenReturn(List.of(resource(4L, "已审核资源")));

        Map<?, ?> result = assertMap(service.getPublishedResources(1, 10));

        assertEquals(1, ((Number) result.get("total")).intValue());
        verify(learningResourceMapper).selectByStatus("APPROVED");
    }

    private LearningResource resource(Long id, String title) {
        LearningResource resource = new LearningResource();
        resource.setId(id);
        resource.setTitle(title);
        resource.setDescription("测试资源");
        resource.setFileUrl("/api/uploads/test.pdf");
        resource.setFileType("application/pdf");
        return resource;
    }

    private Map<?, ?> assertMap(Object result) {
        assertNotNull(result);
        assertTrue(result instanceof Map, "搜索结果应为 Map");
        return (Map<?, ?>) result;
    }

    private List<?> assertList(Object result) {
        assertNotNull(result);
        assertTrue(result instanceof List, "搜索结果 list 应为 List");
        return (List<?>) result;
    }
}
