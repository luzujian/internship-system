package com.gdmu.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * ResourceController测试类
 * 验证API层面的AI搜索功能是否正常工作
 */
@SpringBootTest
@AutoConfigureMockMvc
public class ResourceControllerTest {

    private static final Logger log = LoggerFactory.getLogger(ResourceControllerTest.class);

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testSearchResources() throws Exception {
        // 测试资源搜索API
        String query = "Java Spring Boot";
        
        mockMvc.perform(MockMvcRequestBuilders.get("/api/resources/search")
                .param("query", query)
                .param("page", "1")
                .param("limit", "10")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andExpect(jsonPath("$.data.list").isArray())
                .andDo(result -> {
                    // 记录响应内容以便分析
                    log.info("搜索API响应: {}", result.getResponse().getContentAsString());
                });
    }

    @Test
    public void testSearchResourcesWithFileType() throws Exception {
        // 测试带文件类型的资源搜索
        mockMvc.perform(MockMvcRequestBuilders.get("/api/resources/search")
                .param("query", "编程教程")
                .param("fileType", "PDF")
                .param("page", "1")
                .param("limit", "5")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andDo(result -> {
                    log.info("带文件类型的搜索API响应: {}", result.getResponse().getContentAsString());
                });
    }

    @Test
    public void testAIResourceSearch() throws Exception {
        // 测试AI智能搜索API
        String query = "如何优化Spring Boot应用性能";
        
        mockMvc.perform(MockMvcRequestBuilders.get("/api/resources/ai-search")
                .param("query", query)
                .param("page", "1")
                .param("limit", "5")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andDo(result -> {
                    log.info("AI搜索API响应: {}", result.getResponse().getContentAsString());
                });
    }

    @Test
    public void testEmptyQuerySearch() throws Exception {
        // 测试空查询处理
        mockMvc.perform(MockMvcRequestBuilders.get("/api/resources/search")
                .param("query", "")
                .param("page", "1")
                .param("limit", "5")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").exists())
                .andDo(result -> {
                    log.info("空查询搜索API响应: {}", result.getResponse().getContentAsString());
                });
    }
}