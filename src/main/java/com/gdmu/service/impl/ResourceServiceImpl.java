package com.gdmu.service.impl;

import com.gdmu.entity.LearningResource;
import com.gdmu.entity.ResourceCategory;
import com.gdmu.mapper.LearningResourceMapper;
import com.gdmu.mapper.ResourceCategoryMapper;
import com.gdmu.service.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import java.util.UUID;

/**
 * 学习资源服务实现类
 */
@Service
public class ResourceServiceImpl implements ResourceService {
    
    private final LearningResourceMapper learningResourceMapper;
    private final ResourceCategoryMapper resourceCategoryMapper;
    
    private static final Logger logger = LoggerFactory.getLogger(ResourceServiceImpl.class);
    
    // 搜索结果最大数量限制
    private static final int maxResults = 100;
    
    private static final Map<String, String> FILE_TYPE_MAPPING = new HashMap<>();
    
    // MIME类型到资源类别的映射
    private static final Map<String, String> CONTENT_TYPE_MAPPING = new HashMap<>();
    
    static {
        // 初始化文件类型映射
        FILE_TYPE_MAPPING.put("document", "文档");
        FILE_TYPE_MAPPING.put("video", "视频");
        FILE_TYPE_MAPPING.put("code", "代码");
        FILE_TYPE_MAPPING.put("dataset", "数据集");
        
        // MIME类型到资源类别的映射
        // 文档类型
        CONTENT_TYPE_MAPPING.put("application/pdf", "document");
        CONTENT_TYPE_MAPPING.put("application/msword", "document");
        CONTENT_TYPE_MAPPING.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "document");
        CONTENT_TYPE_MAPPING.put("application/vnd.ms-powerpoint", "document");
        CONTENT_TYPE_MAPPING.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", "document");
        CONTENT_TYPE_MAPPING.put("application/vnd.ms-excel", "document");
        CONTENT_TYPE_MAPPING.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "document");
        CONTENT_TYPE_MAPPING.put("text/plain", "document");
        CONTENT_TYPE_MAPPING.put("text/html", "document");
        
        // 视频类型
        CONTENT_TYPE_MAPPING.put("video/mp4", "video");
        CONTENT_TYPE_MAPPING.put("video/avi", "video");
        CONTENT_TYPE_MAPPING.put("video/mov", "video");
        CONTENT_TYPE_MAPPING.put("video/wmv", "video");
        
        // 代码类型
        CONTENT_TYPE_MAPPING.put("text/javascript", "code");
        CONTENT_TYPE_MAPPING.put("text/css", "code");
        CONTENT_TYPE_MAPPING.put("application/javascript", "code");
        CONTENT_TYPE_MAPPING.put("application/json", "code");
        CONTENT_TYPE_MAPPING.put("application/xml", "code");
        
        // 文件扩展名映射
        CONTENT_TYPE_MAPPING.put(".pdf", "document");
        CONTENT_TYPE_MAPPING.put(".doc", "document");
        CONTENT_TYPE_MAPPING.put(".docx", "document");
        CONTENT_TYPE_MAPPING.put(".ppt", "document");
        CONTENT_TYPE_MAPPING.put(".pptx", "document");
        CONTENT_TYPE_MAPPING.put(".xls", "document");
        CONTENT_TYPE_MAPPING.put(".xlsx", "document");
        CONTENT_TYPE_MAPPING.put(".txt", "document");
        CONTENT_TYPE_MAPPING.put(".html", "document");
        CONTENT_TYPE_MAPPING.put(".mp4", "video");
        CONTENT_TYPE_MAPPING.put(".avi", "video");
        CONTENT_TYPE_MAPPING.put(".mov", "video");
        CONTENT_TYPE_MAPPING.put(".wmv", "video");
        CONTENT_TYPE_MAPPING.put(".java", "code");
        CONTENT_TYPE_MAPPING.put(".py", "code");
        CONTENT_TYPE_MAPPING.put(".js", "code");
        CONTENT_TYPE_MAPPING.put(".css", "code");
        CONTENT_TYPE_MAPPING.put(".xml", "code");
        CONTENT_TYPE_MAPPING.put(".json", "code");
    }
    
    @Autowired
    public ResourceServiceImpl(LearningResourceMapper learningResourceMapper,
                             ResourceCategoryMapper resourceCategoryMapper) {
        this.learningResourceMapper = learningResourceMapper;
        this.resourceCategoryMapper = resourceCategoryMapper;
    }
    
    @Override
    @Transactional
    public Long uploadResource(MultipartFile file, LearningResource resource, Long uploaderId, String uploaderRole) {
        try {
            // 设置上传者信息
            resource.setUploaderId(uploaderId);
            resource.setUploaderRole(uploaderRole);
            
            // 根据角色设置状态
            if ("STUDENT".equals(uploaderRole)) {
                resource.setStatus("PENDING");
            } else {
                resource.setStatus("APPROVED");
            }
            
            // 设置创建时间
            resource.setCreatedTime(LocalDateTime.now());
            
            // 如果有文件，生成文件URL（简化实现）
            if (file != null && !file.isEmpty()) {
                String fileName = UUID.randomUUID() + "_" + file.getOriginalFilename();
                String fileUrl = "/api/uploads/" + fileName;
                resource.setFileUrl(fileUrl);
                resource.setFileType(file.getContentType());
            }
            
            // 生成简单的资源摘要（基于标题和描述）
            String summary = generateSimpleSummary(resource.getTitle(), resource.getDescription());
            resource.setAiSummary(summary);
            
            // 设置默认嵌入向量
            resource.setEmbeddingVector("[]");
            
            // 保存到数据库
            learningResourceMapper.insert(resource);
            
            logger.info("资源上传成功: id={}, title={}, uploaderId={}", resource.getId(), resource.getTitle(), uploaderId);
            return resource.getId();
        } catch (Exception e) {
            logger.error("资源上传失败: {}", e.getMessage(), e);
            throw new RuntimeException("资源上传失败", e);
        }
    }
    
    // 注意：已移除AI摘要生成相关代码，使用本地简单摘要生成方法
    
    @Override
    @Transactional
    public void reviewResource(Long resourceId, Boolean approved, Long reviewerId) {
        try {
            LearningResource resource = learningResourceMapper.selectById(resourceId);
            if (resource == null) {
                throw new RuntimeException("资源不存在");
            }
            
            resource.setStatus(approved ? "APPROVED" : "REJECTED");
            resource.setReviewerId(reviewerId);
            
            learningResourceMapper.updateById(resource);
            logger.info("资源审核完成: id={}, status={}, reviewerId={}", resourceId, resource.getStatus(), reviewerId);
        } catch (Exception e) {
            logger.error("资源审核失败: {}", e.getMessage(), e);
            throw new RuntimeException("资源审核失败", e);
        }
    }
    
    @Override
    public List<LearningResource> semanticSearch(String query) {
        try {
            // 简化为普通模糊搜索
            Map<String, Object> searchParams = new HashMap<>();
            searchParams.put("query", query); // 传递原始查询参数，通配符由SQL处理
            
            List<LearningResource> resources = learningResourceMapper.findSimilarResources(searchParams);
            logger.info("模糊搜索完成: query={}, results={}", query, resources.size());
            return resources;
        } catch (Exception e) {
            logger.error("模糊搜索失败: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
    /**
     * 生成简单的资源摘要
     */
    private String generateSimpleSummary(String title, String description) {
        StringBuilder summary = new StringBuilder();
        if (title != null) {
            summary.append(title);
        }
        if (description != null && !description.isEmpty()) {
            if (summary.length() > 0) {
                summary.append(": ");
            }
            // 取描述的前100个字符作为摘要
            int maxLength = Math.min(description.length(), 100);
            summary.append(description, 0, maxLength);
            if (description.length() > maxLength) {
                summary.append("...");
            }
        }
        return summary.toString();
    }
    
    @Override
    public LearningResource getResourceById(Long id) {
        LearningResource resource = learningResourceMapper.selectById(id);
        if (resource != null) {
            // 处理单个资源的类型
            resource.setFileType(mapContentTypeToResourceType(resource.getFileType()));
        }
        return resource;
    }
    
    @Override
    public List<LearningResource> getPendingResources() {
        List<LearningResource> resources = learningResourceMapper.selectByStatus("PENDING");
        // 处理资源类型，确保返回正确的资源类别
        return processResourceTypes(resources);
    }
    
    @Override
    public List<LearningResource> getApprovedResources() {
        List<LearningResource> resources = learningResourceMapper.selectByStatus("APPROVED");
        // 处理资源类型，确保返回正确的资源类别
        return processResourceTypes(resources);
    }
    
    @Override
    public List<LearningResource> getResourcesByUploaderId(Long uploaderId) {
        return learningResourceMapper.selectByUploaderId(uploaderId);
    }
    
    @Override
    public List<ResourceCategory> getAllCategories() {
        return resourceCategoryMapper.selectList();
    }
    
    @Override
    public void addCategory(ResourceCategory category) {
        resourceCategoryMapper.insert(category);
    }
    
    @Override
    @Transactional
    public void deleteResource(Long id) {
        learningResourceMapper.deleteById(id);
        logger.info("资源删除成功: id={}", id);
    }
    
    /**
     * AI智能资源搜索 - 优化版
     * 支持自然语言查询，资源类型过滤，分页查询，并返回相关性分数
     */
    @Override
    @Transactional(readOnly = true)
    public Object aiSmartSearch(String query, String fileType, int page, int pageSize) {
        logger.info("开始模糊搜索: query={}, fileType={}, page={}, pageSize={}", 
                   query, fileType, page, pageSize);
        
        try {
            // 参数验证
            if (page < 1) page = 1;
            if (pageSize < 1 || pageSize > 100) pageSize = 10;
            
            // 构建响应结果容器
            Map<String, Object> result = new HashMap<>();
            
            // 如果查询不为空，执行模糊搜索
            if (query != null && !query.trim().isEmpty()) {
                logger.debug("执行关键词模糊搜索: {}", query);
                
                try {
                    // 创建参数映射，用于模糊搜索
                    Map<String, Object> searchParams = new HashMap<>();
                    searchParams.put("query", query); // 传递原始查询参数，通配符由SQL处理
                    
                    // 处理文件类型参数
                    if (fileType != null && !fileType.trim().isEmpty()) {
                        String actualFileType = FILE_TYPE_MAPPING.getOrDefault(fileType, fileType);
                        searchParams.put("fileType", actualFileType);
                        logger.debug("按文件类型过滤: {}", actualFileType);
                    }
                    
                    // 计算分页参数
                    int offset = (page - 1) * pageSize;
                    searchParams.put("offset", offset);
                    searchParams.put("limit", pageSize);
                    
                    logger.debug("执行模糊搜索SQL，参数: query={}, offset={}, pageSize={}", 
                               searchParams.get("query"), offset, pageSize);
                    
                    // 继续使用findSimilarResourcesByType方法，但确保参数正确设置
                    // 重用之前定义的searchParams，更新必要参数
                    searchParams.put("fileType", null); // 设置为null以避免文件类型过滤
                    searchParams.put("offset", offset);
                    searchParams.put("limit", pageSize);
                    // 确保查询参数不会丢失
                    searchParams.put("query", query);
                    
                    // 详细记录查询参数和执行过程
                    logger.info("准备执行findSimilarResourcesByType查询，查询关键词: {}", query);
                    logger.debug("完整参数map: {}, SQL查询参数分析: query={}, offset={}, limit={}, fileType={}", 
                               searchParams, searchParams.get("query"), searchParams.get("offset"), 
                               searchParams.get("limit"), searchParams.get("fileType"));
                    
                    // 添加SQL执行前的标记日志
                    logger.debug("开始执行findSimilarResourcesByType SQL查询...");
                    
                    // 执行查询并详细记录结果
                    List<LearningResource> resources = learningResourceMapper.findSimilarResourcesByType(searchParams);
                    
                    // 添加详细的结果日志
                    logger.info("findSimilarResourcesByType查询完成，返回结果数量: {}", resources.size());
                    logger.debug("查询执行完成，返回资源列表数量: {}", resources.size());
                    
                    // 记录返回的所有资源标题（如果有）
                    if (!resources.isEmpty()) {
                        List<String> titles = resources.stream()
                            .map(LearningResource::getTitle)
                            .collect(Collectors.toList());
                        logger.debug("查询返回的资源标题列表: {}", titles);
                    } else {
                        logger.debug("查询未返回任何结果，可能是没有匹配的数据或者查询条件过于严格");
                    }
                    
                    // 转换为前端需要的格式
                    List<Map<String, Object>> searchResults = resources.stream()
                            .map(resource -> {
                                Map<String, Object> resourceMap = new HashMap<>();
                                resourceMap.put("id", resource.getId());
                                resourceMap.put("title", resource.getTitle());
                                resourceMap.put("description", resource.getDescription());
                                // 从fileUrl中提取文件名
                                String fileName = resource.getFileUrl() != null ? 
                                    resource.getFileUrl().substring(resource.getFileUrl().lastIndexOf('/') + 1) : "未知文件名";
                                resourceMap.put("fileName", fileName);
                                resourceMap.put("fileType", resource.getFileType());
                                resourceMap.put("uploadTime", resource.getCreatedTime()); // 使用createdTime代替不存在的uploadTime
                                resourceMap.put("uploaderName", ""); // uploaderName字段不存在，使用空字符串作为默认值
                                resourceMap.put("downloadCount", 0); // downloadCount字段不存在，使用0作为默认值
                                resourceMap.put("categoryName", ""); // categoryName字段不存在，使用空字符串作为默认值
                                return resourceMap;
                            })
                            .collect(Collectors.toList());
                    
                    // 获取总数（不考虑分页）- 添加详细调试日志
                    Map<String, Object> countParams = new HashMap<>();
                    countParams.put("query", query);
                    countParams.put("fileType", null); // 设置为null以避免文件类型过滤
                    logger.debug("使用countSimilarResourcesByType查询，参数map完整内容: query={}, fileType={}", 
                                countParams.get("query"), countParams.get("fileType"));
                    // 执行count查询并记录详细信息
                    // 直接使用Long接收返回值，确保类型匹配
                    logger.debug("准备执行countSimilarResourcesByType查询，参数: query={}, fileType={}", 
                               countParams.get("query"), countParams.get("fileType"));
                    
                    // 打印完整的SQL参数映射，帮助调试
                    logger.debug("count查询完整参数映射: {}", countParams);
                    
                    // 直接使用long类型接收，这应该与XML中的resultType="long"匹配
                    long countResult = learningResourceMapper.countSimilarResourcesByType(countParams);
                    long total = countResult;
                    
                    // 详细记录返回值
                    logger.info("countSimilarResourcesByType直接返回值: {}, 类型: long", countResult);
                    
                    // 添加额外的安全检查，确保值不为负数
                    if (total < 0) {
                        logger.warn("发现异常的count值: {}, 已重置为0", total);
                        total = 0;
                    }
                    
                    // 记录最终使用的总数
                    logger.info("最终使用的资源总数: {}", total);
                    logger.debug("countSimilarResourcesByType查询参数: {}", countParams);
                    
                    // 使用更简单的查询方法后，应该能正确返回结果

                    
                    result.put("total", total);
                    result.put("list", searchResults);
                    logger.info("模糊搜索完成，找到{}个匹配结果，当前页显示{}", total, searchResults.size());
                    
                } catch (Exception e) {
                    logger.error("模糊搜索执行异常: {}", e.getMessage(), e);
                    // 完全失败时返回空结果
                    result.put("total", 0);
                    result.put("list", new ArrayList<>());
                }
            } else {
                // 如果没有查询条件，获取所有资源（可选类型过滤）
                logger.debug("无查询条件，获取所有已批准资源");
                List<LearningResource> approvedResources = learningResourceMapper.selectByStatus("APPROVED");
                List<LearningResource> allResources;
                
                if (fileType != null && !fileType.isEmpty()) {
                    String actualFileType = FILE_TYPE_MAPPING.getOrDefault(fileType, fileType);
                    logger.debug("按文件类型过滤: {}", actualFileType);
                    allResources = approvedResources.stream()
                            .filter(r -> r.getFileType() != null && r.getFileType().contains(actualFileType))
                            .collect(Collectors.toList());
                } else {
                    allResources = approvedResources;
                }
                
                result = buildPagedResult(allResources, page, pageSize);
                logger.info("获取资源列表完成，总数: {}", result.get("total"));
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("模糊搜索失败: {}", e.getMessage(), e);
            // 构建错误响应
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("list", new ArrayList<>());
            errorResult.put("total", 0);
            errorResult.put("error", "搜索过程中发生错误，请稍后重试");
            return errorResult;
        }
    }
    
    // 注意：移除了向量搜索功能，直接使用SQL搜索和Java层过滤实现AI搜索
    
    /**
     * 计算相关性分数并过滤低相关性结果
     */
    private List<Map<String, Object>> scoreAndFilterResults(List<LearningResource> resources, String query) {
        List<Map<String, Object>> scoredResults = new ArrayList<>();
        
        // 与优化后的评分算法匹配的最小相关性阈值（调整为3.0，因为现在评分范围更大）
        double adjustedMinRelevanceScore = 3.0;
        
        logger.debug("开始对{}个资源进行相关性评分，查询关键词: {}", resources.size(), query);
        
        for (LearningResource resource : resources) {
            try {
                // 计算相关性分数（基于内容相似度）
                float relevanceScore = calculateRelevanceScore(query, resource);
                
                logger.debug("资源ID={}, 标题='{}', 相关性分数={}, 是否通过过滤={}", 
                           resource.getId(), resource.getTitle(), relevanceScore, 
                           relevanceScore >= adjustedMinRelevanceScore);
                
                // 过滤低相关性结果
                if (relevanceScore >= adjustedMinRelevanceScore) {
                    Map<String, Object> resourceMap = convertToResultMap(resource, relevanceScore);
                    scoredResults.add(resourceMap);
                }
            } catch (Exception e) {
                logger.warn("处理资源ID={}时出错: {}", resource.getId(), e.getMessage());
                // 为出错的资源提供默认分数，但仍然过滤掉低相关性的结果
                if (0.5 >= adjustedMinRelevanceScore) {
                    Map<String, Object> resourceMap = convertToResultMap(resource, 0.5f);
                    scoredResults.add(resourceMap);
                }
            }
        }
        
        // 按相关性分数排序
        scoredResults.sort((a, b) -> {
            float scoreA = (float) a.getOrDefault("relevanceScore", 0f);
            float scoreB = (float) b.getOrDefault("relevanceScore", 0f);
            return Float.compare(scoreB, scoreA); // 降序排列
        });
        
        // 限制最大结果数
        if (scoredResults.size() > maxResults) {
            logger.debug("结果数量超过最大值{}，截断结果", maxResults);
            scoredResults = scoredResults.subList(0, maxResults);
        }
        
        logger.debug("相关性评分完成，通过过滤的结果数量: {}", scoredResults.size());
        return scoredResults;
    }
    
    /**
     * 计算文本相关性分数
     */
    private float calculateRelevanceScore(String query, LearningResource resource) {
        try {
            // 简单但有效的相关性评分算法
            String searchText = query.toLowerCase().trim();
            String title = resource.getTitle() != null ? resource.getTitle().toLowerCase() : "";
            String description = resource.getDescription() != null ? resource.getDescription().toLowerCase() : "";
            String resourceText = title + " " + description;
            
            // 基础相关性分数
            float score = 1.0f; // 基础分
            
            // 1. 精确匹配检查
            if (title.equals(searchText)) {
                score += 10.0f; // 完全匹配标题给最高分
            } else if (description.contains(searchText)) {
                score += 5.0f; // 描述包含查询词
            }
            
            // 2. 分词匹配（简单版）
            String[] queryWords = searchText.split("\\s+");
            for (String word : queryWords) {
                if (!word.isEmpty()) {
                    // 标题中的关键词匹配权重更高
                    if (title.contains(word)) {
                        score += 2.0f;
                    }
                    // 描述中的关键词匹配
                    if (description.contains(word)) {
                        score += 1.0f;
                    }
                }
            }
            
            // 3. 时间因素（较新的资源可能更相关）
            if (resource.getCreatedTime() != null) {
                LocalDateTime now = LocalDateTime.now();
                long daysSinceCreation = java.time.temporal.ChronoUnit.DAYS.between(resource.getCreatedTime(), now);
                // 30天内的资源增加时间权重
                if (daysSinceCreation <= 30) {
                    score += 2.0f;
                } else if (daysSinceCreation <= 90) {
                    score += 1.0f;
                }
            }
            
            // 4. 调整最终分数范围
            score = Math.min(20.0f, score); // 最大分数为20
            
            logger.debug("资源ID={}标题='{}'相关性分数: {}", resource.getId(), resource.getTitle(), score);
            return score;
        } catch (Exception e) {
            logger.warn("计算资源ID={}的相关性分数时出错: {}", resource.getId(), e.getMessage());
            return 1.0f; // 出错时返回最低基础分
        }
    }
    
    /**
     * 将资源对象转换为结果映射
     */
    private Map<String, Object> convertToResultMap(LearningResource resource, float relevanceScore) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", resource.getId());
        result.put("title", resource.getTitle());
        result.put("description", resource.getDescription());
        result.put("fileType", resource.getFileType());
        result.put("createdTime", resource.getCreatedTime());
        result.put("uploader", resource.getUploaderId()); // 使用uploaderId替代不存在的uploaderName
        result.put("downloadCount", 0); // 默认下载次数为0
        result.put("relevanceScore", Math.round(relevanceScore)); // 四舍五入为整数
        result.put("aiSummary", resource.getAiSummary());
        return result;
    }
    
    /**
     * 处理资源列表中的类型字段
     */
    private List<LearningResource> processResourceTypes(List<LearningResource> resources) {
        if (resources == null || resources.isEmpty()) {
            return resources;
        }
        
        for (LearningResource resource : resources) {
            resource.setFileType(mapContentTypeToResourceType(resource.getFileType()));
        }
        
        return resources;
    }
    
    /**
     * 将MIME类型映射到资源类别
     */
    private String mapContentTypeToResourceType(String contentType) {
        if (contentType == null || contentType.trim().isEmpty()) {
            return "document"; // 默认返回文档类型
        }
        
        // 如果已经是标准资源类别，直接返回
        if (FILE_TYPE_MAPPING.containsKey(contentType)) {
            return contentType;
        }
        
        // 尝试直接映射
        String mappedType = CONTENT_TYPE_MAPPING.get(contentType.toLowerCase());
        if (mappedType != null) {
            return mappedType;
        }
        
        // 尝试根据文件扩展名映射
        for (Map.Entry<String, String> entry : CONTENT_TYPE_MAPPING.entrySet()) {
            if (contentType.toLowerCase().endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        
        // 尝试根据MIME类型前缀映射
        if (contentType.startsWith("text/")) {
            return "document";
        } else if (contentType.startsWith("application/")) {
            return "document";
        } else if (contentType.startsWith("video/")) {
            return "video";
        }
        
        // 默认返回document类型
        return "document";
    }
    
    /**
     * 当向量搜索失败时的备选搜索方案
     */
    private List<LearningResource> performFallbackSearch(String query, String fileType) {
        try {
            logger.info("执行备选搜索方案");
            
            // 获取所有已批准资源
            List<LearningResource> allApproved = learningResourceMapper.selectByStatus("APPROVED");
            
            // 关键词搜索
            List<LearningResource> results = allApproved.stream()
                    .filter(resource -> {
                        String text = (resource.getTitle() + " " + resource.getDescription()).toLowerCase();
                        boolean match = text.contains(query.toLowerCase());
                        
                        // 如果指定了文件类型，还需要过滤文件类型
                        if (match && fileType != null && !fileType.isEmpty()) {
                            String actualFileType = FILE_TYPE_MAPPING.getOrDefault(fileType, fileType);
                            return resource.getFileType() != null && 
                                   resource.getFileType().toLowerCase().contains(actualFileType.toLowerCase());
                        }
                        return match;
                    })
                    .collect(Collectors.toList());
                    
            return results;
        } catch (Exception e) {
            logger.error("备选搜索失败: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * 构建分页结果
     */
    private Map<String, Object> buildPagedResult(List<LearningResource> resources, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        
        // 计算分页参数
        int total = resources.size();
        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        
        // 获取当前页数据
        List<LearningResource> pageResources = start < total ? resources.subList(start, end) : new ArrayList<>();
        
        // 转换为前端需要的格式
        List<Map<String, Object>> list = new ArrayList<>();
        for (LearningResource resource : pageResources) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", resource.getId());
            item.put("title", resource.getTitle());
            item.put("description", resource.getDescription());
            item.put("fileType", resource.getFileType());
            item.put("createdTime", resource.getCreatedTime());
            item.put("uploader", resource.getUploaderId()); // 使用uploaderId替代不存在的uploaderName
            item.put("downloadCount", 0); // 默认下载次数为0
            item.put("relevanceScore", 5); // 默认中等相关性
            item.put("aiSummary", resource.getAiSummary());
            list.add(item);
        }
        
        result.put("list", list);
        result.put("total", total);
        return result;
    }
}