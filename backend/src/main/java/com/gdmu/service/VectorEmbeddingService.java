package com.gdmu.service;

import java.util.List;

/**
 * 向量嵌入服务接口
 * 基于Spring AI框架提供文本到向量嵌入的转换功能
 */
public interface VectorEmbeddingService {
    
    /**
     * 生成单个文本的向量嵌入
     * @param text 需要生成嵌入的文本
     * @return 嵌入向量的JSON字符串表示
     */
    String generateEmbedding(String text);
    
    /**
     * 批量生成文本的向量嵌入
     * @param texts 需要生成嵌入的文本列表
     * @return 嵌入向量的JSON字符串表示列表
     */
    List<String> generateEmbeddings(List<String> texts);
    
    /**
     * 验证嵌入向量的有效性
     * @param embedding 嵌入向量的JSON字符串
     * @return 是否有效
     */
    boolean isValidEmbedding(String embedding);
    
    /**
     * 获取嵌入向量的维度
     * @return 嵌入向量的维度
     */
    int getEmbeddingDimension();
}