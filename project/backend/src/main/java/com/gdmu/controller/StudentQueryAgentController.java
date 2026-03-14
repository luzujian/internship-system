package com.gdmu.controller;

import com.gdmu.service.StudentQueryAgentService;
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
 * 学生查询Agent控制器
 */
@RestController
@RequestMapping("/api/ai/student")
public class StudentQueryAgentController {

    @Autowired
    private StudentQueryAgentService studentQueryAgentService;
    
    private final CopyOnWriteArrayList<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 处理学生用户查询或删除请求
     */
    @PostMapping("/query")
    public ResponseEntity<Map<String, Object>> processStudentOperation(@RequestBody Map<String, Object> request) {
        try {
            String query = (String) request.get("query");
            List<Map<String, Object>> context = (List<Map<String, Object>>) request.get("context");
            String model = (String) request.get("model"); // 获取模型参数
            
            if (query == null || query.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "查询内容不能为空"
                ));
            }
            
            // 将模型参数传递给服务层
            Map<String, Object> result = studentQueryAgentService.processStudentQuery(query, context, model);
            return ResponseEntity.ok(result);
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "查询处理失败：" + e.getMessage()
            ));
        }
    }
    
    /**
     * 处理学生用户查询或删除请求（流式响应）
     */
    @PostMapping(value = "/query/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter processStudentOperationStream(@RequestBody Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(120_000L); // 2分钟超时
        
        emitters.add(emitter);
        
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        
        try {
            String query = (String) request.get("query");
            List<Map<String, Object>> context = (List<Map<String, Object>>) request.get("context");
            String model = (String) request.get("model"); // 获取模型参数
            
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
                    Map<String, Object> result = studentQueryAgentService.processStudentQuery(query, context, model);
                    
                    // 构建流式响应
                    if ((Boolean) result.getOrDefault("success", false)) {
                        // 发送查询结果
                        Map<String, Object> data = new HashMap<>();
                        data.put("type", "chunk");
                        data.put("content", generateResponseContent(result));
                        emitter.send(SseEmitter.event().data(data));
                    } else {
                        // 发送错误信息
                        Map<String, Object> errorData = new HashMap<>();
                        errorData.put("type", "error");
                        errorData.put("message", result.getOrDefault("message", "查询处理失败").toString());
                        emitter.send(SseEmitter.event().data(errorData));
                    }
                    
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
     * 生成响应内容
     */
    private String generateResponseContent(Map<String, Object> result) {
        StringBuilder content = new StringBuilder();
        
        // 检查是否是资源查询结果
        if (result.containsKey("searchResult")) {
            Object searchResultObj = result.get("searchResult");
            if (searchResultObj instanceof Map) {
                Map<?, ?> searchResult = (Map<?, ?>) searchResultObj;
                return formatResourceResponse(result, searchResult);
            }
        }
        
        // 检查result中是否直接包含students
        if (result.containsKey("students")) {
            Object studentsObj = result.get("students");
            if (studentsObj instanceof List) {
                List<?> students = (List<?>) studentsObj;
                if (students != null && !students.isEmpty()) {
                    content.append("根据系统数据库最新检索结果，共找到 ").append(students.size()).append(" 名学生：\n\n");
                    
                    for (Object studentObj : students) {
                        // 处理StudentUserVO对象或Map对象
                        if (studentObj instanceof Map) {
                            Map<?, ?> student = (Map<?, ?>) studentObj;
                            // 安全地获取属性值
                            Object studentIdObj = student.get("studentId");
                            content.append("学号：").append(studentIdObj != null ? studentIdObj.toString() : "未知").append("\n");
                            
                            Object nameObj = student.get("name");
                            content.append("姓名：").append(nameObj != null ? nameObj.toString() : "未知").append("\n");
                            
                            // 安全处理status字段
                            Object statusObj = student.get("status");
                            boolean isEnabled = false;
                            if (statusObj instanceof Integer) {
                                isEnabled = (Integer) statusObj == 1;
                            } else if (statusObj instanceof String) {
                                try {
                                    isEnabled = Integer.parseInt((String) statusObj) == 1;
                                } catch (NumberFormatException e) {
                                    isEnabled = false;
                                }
                            }
                            content.append("账户状态：").append(isEnabled ? "已启用" : "已禁用").append("\n");
                            
                            Object majorNameObj = student.get("majorName");
                            content.append("专业：").append(majorNameObj != null ? majorNameObj.toString() : "未知").append("\n");
                            
                            Object departmentNameObj = student.get("departmentName");
                            content.append("学院：").append(departmentNameObj != null ? departmentNameObj.toString() : "未知").append("\n");
                            
                            Object classNameObj = student.get("className");
                            content.append("班级：").append(classNameObj != null ? classNameObj.toString() : "未知").append("\n");
                            
                            Object gradeObj = student.get("grade");
                            content.append("年级：").append(gradeObj != null ? gradeObj.toString() : "未知").append("\n");
                            
                            Object createTimeObj = student.get("createTime");
                            content.append("创建时间：").append(createTimeObj != null ? createTimeObj.toString() : "未知").append("\n\n");
                        } else {
                            // 对于StudentUserVO对象，使用反射获取属性
                            try {
                                // 获取StudentUserVO的属性值
                                String studentId = invokeGetter(studentObj, "getStudentId", "未知");
                                String name = invokeGetter(studentObj, "getName", "未知");
                                String statusStr = invokeGetter(studentObj, "getStatus", "0");
                                boolean isEnabled = "1".equals(statusStr) || "true".equalsIgnoreCase(statusStr);
                                String majorName = invokeGetter(studentObj, "getMajorName", "未知");
                                String departmentName = invokeGetter(studentObj, "getDepartmentName", "未知");
                                String className = invokeGetter(studentObj, "getClassName", "未知");
                                String grade = invokeGetter(studentObj, "getGrade", "未知");
                                String createTime = invokeGetter(studentObj, "getCreateTime", "未知");
                                
                                // 构建学生信息
                                content.append("学号：").append(studentId).append("\n");
                                content.append("姓名：").append(name).append("\n");
                                content.append("账户状态：").append(isEnabled ? "已启用" : "已禁用").append("\n");
                                content.append("专业：").append(majorName).append("\n");
                                content.append("学院：").append(departmentName).append("\n");
                                content.append("班级：").append(className).append("\n");
                                content.append("年级：").append(grade).append("\n");
                                content.append("创建时间：").append(createTime).append("\n\n");
                            } catch (Exception e) {
                                // 如果反射失败，使用toString
                                content.append(studentObj.toString()).append("\n\n");
                            }
                        }
                    }
                    
                    content.append("总计：").append(students.size()).append("名学生");
                } else {
                    content.append("未找到符合条件的学生用户");
                }
            } else {
                content.append("查询结果格式错误：students不是列表类型");
            }
        } else {
            // 尝试从data字段获取
            if (result.containsKey("data") && result.get("data") instanceof Map) {
                Map<?, ?> data = (Map<?, ?>) result.get("data");
                if (data.containsKey("students")) {
                    Object studentsObj = data.get("students");
                    if (studentsObj instanceof List) {
                        List<?> students = (List<?>) studentsObj;
                        if (students != null && !students.isEmpty()) {
                            content.append("根据系统数据库最新检索结果，共找到 ").append(students.size()).append(" 名学生：\n\n");
                            
                            for (Object studentObj : students) {
                                // 处理StudentUserVO对象或Map对象
                                if (studentObj instanceof Map) {
                                    Map<?, ?> student = (Map<?, ?>) studentObj;
                                    // 安全地获取属性值
                                    Object studentIdObj = student.get("studentId");
                                    content.append("学号：").append(studentIdObj != null ? studentIdObj.toString() : "未知").append("\n");
                                    
                                    Object nameObj = student.get("name");
                                    content.append("姓名：").append(nameObj != null ? nameObj.toString() : "未知").append("\n");
                                    
                                    // 安全处理status字段
                                    Object statusObj = student.get("status");
                                    boolean isEnabled = false;
                                    if (statusObj instanceof Integer) {
                                        isEnabled = (Integer) statusObj == 1;
                                    } else if (statusObj instanceof String) {
                                        try {
                                            isEnabled = Integer.parseInt((String) statusObj) == 1;
                                        } catch (NumberFormatException e) {
                                            isEnabled = false;
                                        }
                                    }
                                    content.append("账户状态：").append(isEnabled ? "已启用" : "已禁用").append("\n");
                                    
                                    Object majorNameObj = student.get("majorName");
                                    content.append("专业：").append(majorNameObj != null ? majorNameObj.toString() : "未知").append("\n");
                                    
                                    Object departmentNameObj = student.get("departmentName");
                                    content.append("学院：").append(departmentNameObj != null ? departmentNameObj.toString() : "未知").append("\n");
                                    
                                    Object classNameObj = student.get("className");
                                    content.append("班级：").append(classNameObj != null ? classNameObj.toString() : "未知").append("\n");
                                    
                                    Object gradeObj = student.get("grade");
                                    content.append("年级：").append(gradeObj != null ? gradeObj.toString() : "未知").append("\n");
                                    
                                    Object createTimeObj = student.get("createTime");
                                    content.append("创建时间：").append(createTimeObj != null ? createTimeObj.toString() : "未知").append("\n\n");
                                } else {
                                    // 对于StudentUserVO对象，使用反射获取属性
                                    try {
                                        // 获取StudentUserVO的属性值
                                        String studentId = invokeGetter(studentObj, "getStudentId", "未知");
                                        String name = invokeGetter(studentObj, "getName", "未知");
                                        String statusStr = invokeGetter(studentObj, "getStatus", "0");
                                        boolean isEnabled = "1".equals(statusStr) || "true".equalsIgnoreCase(statusStr);
                                        String majorName = invokeGetter(studentObj, "getMajorName", "未知");
                                        String departmentName = invokeGetter(studentObj, "getDepartmentName", "未知");
                                        String className = invokeGetter(studentObj, "getClassName", "未知");
                                        String grade = invokeGetter(studentObj, "getGrade", "未知");
                                        String createTime = invokeGetter(studentObj, "getCreateTime", "未知");
                                        
                                        // 构建学生信息
                                        content.append("学号：").append(studentId).append("\n");
                                        content.append("姓名：").append(name).append("\n");
                                        content.append("账户状态：").append(isEnabled ? "已启用" : "已禁用").append("\n");
                                        content.append("专业：").append(majorName).append("\n");
                                        content.append("学院：").append(departmentName).append("\n");
                                        content.append("班级：").append(className).append("\n");
                                        content.append("年级：").append(grade).append("\n");
                                        content.append("创建时间：").append(createTime).append("\n\n");
                                    } catch (Exception e) {
                                        // 如果反射失败，使用toString
                                        content.append(studentObj.toString()).append("\n\n");
                                    }
                                }
                            }
                            
                            content.append("总计：").append(students.size()).append("名学生");
                        } else {
                            content.append("未找到符合条件的学生用户");
                        }
                    } else {
                        content.append("查询结果格式错误：students不是列表类型");
                    }
                } else {
                    content.append(result.getOrDefault("message", "查询完成，但未返回学生数据").toString());
                }
            } else {
                content.append(result.getOrDefault("message", "查询完成，但未返回学生数据").toString());
            }
        }
        
        return content.toString();
    }
    
    /**
     * 获取学生管理Agent的介绍信息
     */
    /**
     * 使用反射安全地获取对象的getter方法返回值
     */
    /**
     * 格式化资源查询响应内容
     */
    private String formatResourceResponse(Map<String, Object> result, Map<?, ?> searchResult) {
        StringBuilder content = new StringBuilder();
        
        // 添加查询摘要
        if (result.containsKey("originalQuery")) {
            content.append("🔍 **查询内容**: ").append(result.get("originalQuery")).append("\n\n");
        }
        
        // 处理搜索结果
        Object resourcesObj = searchResult.get("list");
        if (resourcesObj instanceof List) {
            List<?> resources = (List<?>) resourcesObj;
            int total = resources.size();
            Object totalObj = searchResult.get("total");
            if (totalObj instanceof Number) {
                total = ((Number) totalObj).intValue();
            }
            
            content.append("📚 **找到 ").append(total).append(" 个相关资源**\n\n");
            
            if (!resources.isEmpty()) {
                for (int i = 0; i < resources.size(); i++) {
                    Object resourceObj = resources.get(i);
                    if (resourceObj instanceof Map) {
                        Map<?, ?> resource = (Map<?, ?>) resourceObj;
                        content.append("### ").append(i + 1).append(". ")
                               .append(resource.get("title") != null ? resource.get("title") : 
                                      resource.get("name") != null ? resource.get("name") : "未命名资源")
                               .append("\n\n");
                        
                        // 资源类型
                        Object fileType = resource.get("fileType");
                        if (fileType != null) {
                            content.append("📁 **类型**: ").append(fileType).append("\n");
                        }
                        
                        // 上传者
                        Object uploaderName = resource.get("uploaderName");
                        if (uploaderName != null) {
                            content.append("👤 **上传者**: ").append(uploaderName).append("\n");
                        }
                        
                        // 上传时间
                        Object uploadTime = resource.get("uploadTime");
                        if (uploadTime != null) {
                            content.append("📅 **上传时间**: ").append(uploadTime).append("\n");
                        }
                        
                        // 描述
                        Object description = resource.get("description");
                        if (description != null) {
                            String desc = description.toString();
                            if (desc.length() > 100) {
                                desc = desc.substring(0, 100) + "...";
                            }
                            content.append("📝 **描述**: ").append(desc).append("\n");
                        }
                        
                        content.append("---\n\n");
                    }
                }
            } else {
                content.append("❌ 未找到匹配的资源\n\n");
                content.append("💡 **建议**:\n");
                content.append("- 尝试使用其他关键词\n");
                content.append("- 减少过滤条件\n");
                content.append("- 检查关键词拼写\n");
            }
        } else {
            content.append("查询完成，但未返回资源数据");
        }
        
        return content.toString();
    }
    
    private String invokeGetter(Object obj, String methodName, String defaultValue) {
        if (obj == null) return defaultValue;
        
        try {
            // 尝试调用getter方法
            java.lang.reflect.Method method = obj.getClass().getMethod(methodName);
            Object result = method.invoke(obj);
            return result != null ? result.toString() : defaultValue;
        } catch (Exception e) {
            // 如果方法不存在，尝试直接获取字段（如果有权限）
            try {
                String fieldName = methodName.substring(3, 4).toLowerCase() + methodName.substring(4);
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object result = field.get(obj);
                return result != null ? result.toString() : defaultValue;
            } catch (Exception ex) {
                // 如果都失败，返回默认值
                return defaultValue;
            }
        }
    }
    
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getAgentInfo() {
        Map<String, Object> info = Map.of(
            "name", "学生管理AI助手",
            "description", "专门用于查询和管理学生用户信息的AI助手，支持自然语言查询和删除操作",
            "capabilities", new String[]{
                "按学号查询学生信息",
                "按姓名查询学生信息", 
                "按专业查询学生信息",
                "按学院查询学生信息",
                "按班级查询学生信息",
                "按激活状态查询学生信息",
                "组合条件查询学生信息",
                "删除单个学生用户",
                "批量删除学生用户",
                "根据条件删除学生用户"
            },
            "examples", new String[]{
                "查询计算机专业的学生",
                "查找张三的信息",
                "查看2023级的学生",
                "显示所有未激活的学生",
                "查询软件工程专业2023级的学生",
                "删除学号为2023001的学生",
                "删除张三",
                "删除选中的学生",
                "删除计算机专业的学生"
            }
        );
        
        return ResponseEntity.ok(Map.of(
            "success", true,
            "data", info
        ));
    }
}