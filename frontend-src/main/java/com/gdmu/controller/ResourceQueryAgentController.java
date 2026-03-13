package com.gdmu.controller;

import com.gdmu.service.ResourceQueryAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 资源查询AI代理控制器
 * 负责处理自然语言形式的资源查询请求
 */
@RestController
@RequestMapping("/api/ai/resource/advanced")
public class ResourceQueryAgentController {
    private static final Logger log = LoggerFactory.getLogger(ResourceQueryAgentController.class);
    
    private final ResourceQueryAgentService resourceQueryAgentService;
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();
    
    @Autowired
    public ResourceQueryAgentController(ResourceQueryAgentService resourceQueryAgentService) {
        this.resourceQueryAgentService = resourceQueryAgentService;
    }
    
    /**
     * 处理资源查询请求
     * @param request 包含查询内容、上下文和模型信息
     * @return 查询结果
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> queryResources(@RequestBody Map<String, Object> request) {
        try {
            String query = (String) request.get("query");
            Object contextObj = request.get("context");
            String context = processContext(contextObj);
            String model = (String) request.get("model");
            
            log.info("收到资源查询请求: query={}, model={}", query, model);
            
            // 参数验证
            if (query == null || query.trim().isEmpty()) {
                log.warn("查询内容为空");
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "查询内容不能为空");
                errorResponse.put("timestamp", System.currentTimeMillis());
                return ResponseEntity.badRequest().body(errorResponse);
            }
            
            // 调用服务处理查询
            Map<String, Object> result = resourceQueryAgentService.processResourceQuery(query, context, model);
            
            // 封装响应
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", result);
            response.put("timestamp", System.currentTimeMillis());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("资源查询处理失败", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "资源查询服务暂时不可用: " + e.getMessage());
            errorResponse.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
    
    /**
     * 处理上下文参数，将不同类型的context转换为字符串格式
     */
    private String processContext(Object contextObj) {
        if (contextObj == null) {
            return "";
        }
        
        if (contextObj instanceof String) {
            return (String) contextObj;
        }
        
        if (contextObj instanceof List) {
            List<?> contextList = (List<?>) contextObj;
            StringBuilder sb = new StringBuilder();
            for (Object item : contextList) {
                if (item instanceof Map) {
                    Map<?, ?> itemMap = (Map<?, ?>) item;
                    Object role = itemMap.get("role");
                    Object content = itemMap.get("content");
                    if (role != null && content != null) {
                        sb.append(role).append(": ").append(content).append("\n");
                    }
                }
            }
            return sb.toString();
        }
        
        return contextObj.toString();
    }
    
    /**
     * 处理资源查询请求（流式响应）
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter queryResourcesStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时
        
        emitters.add(emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        
        try {
            String query = (String) request.get("query");
            Object contextObj = request.get("context");
            String context = processContext(contextObj);
            String model = (String) request.get("model");
            
            log.info("收到资源查询流式请求: query={}, model={}", query, model);
            
            if (query == null || query.trim().isEmpty()) {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("type", "error");
                errorData.put("message", "查询内容不能为空");
                emitter.send(SseEmitter.event().data(errorData));
                emitter.complete();
                return emitter;
            }
            
            // 异步处理查询
            new Thread(() -> {
                try {
                    // 通知客户端开始处理
                    Map<String, Object> startData = new HashMap<>();
                    startData.put("type", "start");
                    emitter.send(SseEmitter.event().data(startData));
                    
                    // 调用服务层处理查询
                    Map<String, Object> result = resourceQueryAgentService.processResourceQuery(query, context, model);
                    
                    // 构建响应内容
                    Map<String, Object> responseContent = new HashMap<>();
                    responseContent.put("originalQuery", result.get("originalQuery"));
                    responseContent.put("extractedParams", result.get("extractedParams"));
                    responseContent.put("searchResult", result.get("searchResult"));
                    
                    // 发送查询结果
                    Map<String, Object> data = new HashMap<>();
                    data.put("type", "chunk");
                    data.put("content", responseContent);
                    emitter.send(SseEmitter.event().data(data));
                    
                    // 通知客户端结束
                    Map<String, Object> endData = new HashMap<>();
                    endData.put("type", "end");
                    emitter.send(SseEmitter.event().data(endData));
                    
                } catch (Exception e) {
                    try {
                        Map<String, Object> errorData = new HashMap<>();
                        errorData.put("type", "error");
                        errorData.put("message", "处理查询时发生错误：" + e.getMessage());
                        emitter.send(SseEmitter.event().data(errorData));
                    } catch (IOException ex) {
                        // 忽略发送错误
                    } finally {
                        emitter.complete();
                    }
                } finally {
                    emitter.complete();
                }
            }).start();
            
        } catch (Exception e) {
            try {
                Map<String, Object> errorData = new HashMap<>();
                errorData.put("type", "error");
                errorData.put("message", "服务暂时不可用：" + e.getMessage());
                emitter.send(SseEmitter.event().data(errorData));
            } catch (IOException ex) {
                // 忽略发送错误
            } finally {
                emitter.complete();
            }
        }
        
        return emitter;
    }
    
    /**
     * 获取AI助手信息
     * @return AI助手功能介绍和支持的查询类型
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "资源查询助手");
        info.put("description", "基于自然语言的资源库智能查询系统");
        info.put("supportedFeatures", new String[]{
            "根据资源标题模糊查询",
            "根据资源描述内容查询",
            "按资源类型过滤",
            "按上传时间筛选",
            "按上传者信息查询",
            "综合条件组合查询"
        });
        info.put("examples", new String[]{
            "查找所有PDF格式的Java学习资料",
            "搜索最近一周上传的Spring Boot相关文档",
            "有哪些关于数据库优化的视频教程",
            "查找学生上传的机器学习相关资源"
        });
        return ResponseEntity.ok(info);
    }
}