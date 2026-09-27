package com.internship.service.impl;

import com.internship.service.VectorEmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.Random;

/**
 * 向量嵌入服务实现类
 * 提供本地文本到向量嵌入的转换功能
 */
@Service
public class VectorEmbeddingServiceImpl implements VectorEmbeddingService {
    
    private static final Logger log = LoggerFactory.getLogger(VectorEmbeddingServiceImpl.class);
    
    // 默认向量维度（DeepSeek模型的维度）
    private static final int DEFAULT_EMBEDDING_DIMENSION = 768;
    
    // 向量格式验证正则表达式
    private static final Pattern EMBEDDING_PATTERN = Pattern.compile("\\[([-\\d.]+,\\s*)*[-\\d.]+\\]");
    
    @Override
    public String generateEmbedding(String text) {
        try {
            if (text == null || text.trim().isEmpty()) {
                log.warn("尝试对空文本生成嵌入");
                return "[]";
            }
            
            // 本地实现：基于文本内容生成伪嵌入向量；同一段文本结果保持稳定。
            long seed = text.hashCode();
            Random random = new Random(seed);
            
            List<String> embedding = new ArrayList<>(DEFAULT_EMBEDDING_DIMENSION);
            for (int i = 0; i < DEFAULT_EMBEDDING_DIMENSION; i++) {
                // 生成范围在[-1.0, 1.0]之间的随机值
                double value = (random.nextDouble() * 2) - 1;
                // 固定保留 6 位小数，避免 Java 序列化为科学计数法。
                embedding.add(String.format(Locale.ROOT, "%.6f", value));
            }
            
            // 将嵌入向量转换为JSON字符串
            String embeddingJson = "[" + String.join(",", embedding) + "]";
            log.info("成功生成文本嵌入向量（本地实现），文本长度: {}, 向量维度: {}", 
                     text.length(), embedding.size());
            
            return embeddingJson;
        } catch (Exception e) {
            log.error("生成嵌入向量失败: {}", e.getMessage(), e);
            // 降级返回默认向量
            return "[0.0]";
        }
    }
    
    @Override
    public List<String> generateEmbeddings(List<String> texts) {
        try {
            if (texts == null || texts.isEmpty()) {
                log.warn("尝试对空文本列表生成嵌入");
                return List.of();
            }
            
            List<String> result = new ArrayList<>(texts.size());
            
            // 批量处理文本嵌入
            for (String text : texts) {
                result.add(generateEmbedding(text));
            }
            
            log.info("成功批量生成嵌入向量，处理文本数量: {}", texts.size());
            return result;
        } catch (Exception e) {
            log.error("批量生成嵌入向量失败: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    @Override
    public boolean isValidEmbedding(String embedding) {
        if (embedding == null || embedding.trim().isEmpty()) {
            return false;
        }
        
        // 检查格式是否为有效的JSON数组
        boolean matchesPattern = EMBEDDING_PATTERN.matcher(embedding).matches();
        
        // 检查是否为默认空向量
        boolean isEmptyVector = embedding.equals("[]") || embedding.equals("[0.0]");
        
        return matchesPattern && !isEmptyVector;
    }
    
    @Override
    public int getEmbeddingDimension() {
        // 返回DeepSeek模型的默认维度
        // 在实际应用中，可以通过Spring AI的配置或动态检测获取
        return DEFAULT_EMBEDDING_DIMENSION;
    }
}
