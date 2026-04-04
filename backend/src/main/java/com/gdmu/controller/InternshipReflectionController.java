package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.InternshipReflection;
import com.gdmu.entity.Result;
import com.gdmu.service.InternshipReflectionService;
import com.gdmu.utils.FileParserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/internship-reflection")
public class InternshipReflectionController {

    @Autowired
    private InternshipReflectionService internshipReflectionService;

    @Autowired
    private FileParserUtil fileParserUtil;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "INSERT", description = "添加实习心得")
    public Result addInternshipReflection(@RequestBody InternshipReflection internshipReflection) {
        log.info("添加实习心得: {}", internshipReflection.getStudentName());
        try {
            int result = internshipReflectionService.insert(internshipReflection);
            return Result.success("添加成功", internshipReflection);
        } catch (Exception e) {
            log.error("添加实习心得失败: {}", e.getMessage());
            return Result.error("添加失败: " + e.getMessage());
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "SELECT", description = "查询实习心得列表")
    public Result getInternshipReflections(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) String studentName,
            @RequestParam(required = false) String studentUserId,
            @RequestParam(required = false) Integer status) {
        log.info("查询实习心得列表");
        try {
            List<InternshipReflection> reflections = internshipReflectionService.list(studentId, studentName, studentUserId, status);
            return Result.success(reflections);
        } catch (Exception e) {
            log.error("查询实习心得失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "SELECT", description = "查询实习心得详情")
    public Result getInternshipReflectionById(@PathVariable Long id) {
        log.info("查询实习心得详情，ID: {}", id);
        try {
            InternshipReflection reflection = internshipReflectionService.findById(id);
            return Result.success(reflection);
        } catch (Exception e) {
            log.error("查询实习心得详情失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "UPDATE", description = "更新实习心得")
    public Result updateInternshipReflection(@PathVariable Long id, @RequestBody InternshipReflection internshipReflection) {
        log.info("更新实习心得，ID: {}", id);
        try {
            internshipReflection.setId(id);
            int result = internshipReflectionService.updateById(internshipReflection);
            return Result.success("更新成功", internshipReflection);
        } catch (Exception e) {
            log.error("更新实习心得失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "DELETE", description = "删除实习心得")
    public Result deleteInternshipReflection(@PathVariable Long id) {
        log.info("删除实习心得，ID: {}", id);
        try {
            int result = internshipReflectionService.deleteById(id);
            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除实习心得失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    @PostMapping("/analyze/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "UPDATE", description = "AI分析实习心得")
    public Result analyzeInternshipReflection(@PathVariable Long id) {
        log.info("AI分析实习心得，ID: {}", id);
        try {
            Map<String, Object> analysisResult = internshipReflectionService.analyzeReflection(id);
            
            if ((Boolean) analysisResult.get("success")) {
                InternshipReflection reflection = internshipReflectionService.findById(id);
                if (reflection != null) {
                    Map<String, Object> data = (Map<String, Object>) analysisResult.get("data");

                    reflection.setAiKeywords(data.get("keywords") != null ? data.get("keywords").toString() : null);
                    reflection.setAiAnalysis(data.get("comment") != null ? data.get("comment").toString() : null);

                    // 保存改进建议
                    Object improvementsObj = data.get("improvements");
                    if (improvementsObj instanceof List) {
                        List<?> improvementsList = (List<?>) improvementsObj;
                        reflection.setAiImprovements(improvementsList.toString());
                    }

                    Object scoreObj = data.get("score");
                    if (scoreObj != null) {
                        reflection.setAiScore(new java.math.BigDecimal(scoreObj.toString()));
                    }

                    reflection.setAiAnalysisTime(new Date());
                    reflection.setStatus(1);

                    internshipReflectionService.updateById(reflection);
                }
                
                return Result.success("分析成功", analysisResult.get("data"));
            } else {
                return Result.error("分析失败: " + analysisResult.get("message"));
            }
        } catch (Exception e) {
            log.error("AI分析实习心得失败: {}", e.getMessage());
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    @PostMapping("/analyze-content")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "UPDATE", description = "AI分析实习心得内容")
    public Result analyzeReflectionContent(@RequestBody Map<String, String> request) {
        log.info("AI分析实习心得内容");
        try {
            String content = request.get("content");
            if (content == null || content.trim().isEmpty()) {
                return Result.error("实习心得内容不能为空");
            }
            
            Map<String, Object> analysisResult = internshipReflectionService.analyzeReflectionContent(content);
            
            if ((Boolean) analysisResult.get("success")) {
                return Result.success("分析成功", analysisResult.get("data"));
            } else {
                return Result.error("分析失败: " + analysisResult.get("message"));
            }
        } catch (Exception e) {
            log.error("AI分析实习心得内容失败: {}", e.getMessage());
            return Result.error("分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "SELECT", description = "查询实习心得统计")
    public Result getStatistics() {
        log.info("查询实习心得统计");
        try {
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("totalCount", internshipReflectionService.list(null, null, null, null).size());
            stats.put("pendingCount", internshipReflectionService.countByStatus(0));
            stats.put("analyzedCount", internshipReflectionService.countByStatus(1));
            stats.put("failedCount", internshipReflectionService.countByStatus(2));
            return Result.success(stats);
        } catch (Exception e) {
            log.error("查询实习心得统计失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/upload-and-analyze")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "INSERT", description = "上传并分析实习心得文件")
    public Result uploadAndAnalyze(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "modelCode", required = false) String modelCode,
            @RequestParam(value = "modelName", required = false) String modelName) {
        log.info("上传并分析实习心得文件: {}, 使用模型: {}", file.getOriginalFilename(), modelName != null ? modelName : "默认模型");
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !fileParserUtil.isSupportedFile(originalFilename)) {
                return Result.error("不支持的文件格式，仅支持 .doc, .docx, .pdf, .txt 格式");
            }

            long fileSize = file.getSize();
            long maxSize = 10 * 1024 * 1024;
            if (fileSize > maxSize) {
                return Result.error("文件大小不能超过10MB");
            }

            String content = fileParserUtil.parseFile(file);
            
            if (content == null || content.trim().isEmpty()) {
                return Result.error("文件内容为空");
            }

            Map<String, Object> analysisResult = internshipReflectionService.analyzeReflectionContent(content, modelCode);
            
            log.info("分析结果: success={}, isNotReflection={}, message={}", 
                analysisResult.get("success"), analysisResult.get("isNotReflection"), analysisResult.get("message"));
            
            if (!(Boolean) analysisResult.get("success")) {
                if (Boolean.TRUE.equals(analysisResult.get("isNotReflection"))) {
                    log.info("检测到非实习心得，返回错误消息: {}", analysisResult.get("message"));
                    return Result.error(analysisResult.get("message").toString());
                }
                log.info("分析失败，返回错误消息: {}", analysisResult.get("message"));
                return Result.error("分析失败: " + analysisResult.get("message"));
            }

            InternshipReflection reflection = new InternshipReflection();
            reflection.setStudentId(0L);
            reflection.setStudentName("测试学生");
            reflection.setStudentUserId("TEST000");
            reflection.setContent(content);
            reflection.setSubmitTime(new Date());
            
            Map<String, Object> data = (Map<String, Object>) analysisResult.get("data");
            reflection.setAiKeywords(data.get("keywords") != null ? data.get("keywords").toString() : null);
            reflection.setAiAnalysis(data.get("comment") != null ? data.get("comment").toString() : null);

            // 保存改进建议
            Object improvementsObj = data.get("improvements");
            if (improvementsObj instanceof List) {
                List<?> improvementsList = (List<?>) improvementsObj;
                reflection.setAiImprovements(improvementsList.toString());
            }

            Object scoreObj = data.get("score");
            if (scoreObj != null) {
                reflection.setAiScore(new java.math.BigDecimal(scoreObj.toString()));
            }

            reflection.setAiAnalysisTime(new Date());
            reflection.setStatus(1);
            
            internshipReflectionService.insert(reflection);
            
            Map<String, Object> response = new HashMap<>();
            response.put("reflection", reflection);
            response.put("analysis", data);
            
            return Result.success("上传并分析成功", response);
        } catch (IllegalArgumentException e) {
            log.error("文件格式错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传并分析实习心得文件失败: {}", e.getMessage(), e);
            return Result.error("上传并分析失败: " + e.getMessage());
        }
    }

    @PostMapping("/analyze-file")
    @PreAuthorize("hasRole('ADMIN')")
    @Log(module = "INTERNSHIP_REFLECTION", operationType = "UPDATE", description = "AI分析实习心得文件")
    public Result analyzeFile(@RequestParam("file") MultipartFile file) {
        log.info("AI分析实习心得文件: {}", file.getOriginalFilename());
        try {
            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !fileParserUtil.isSupportedFile(originalFilename)) {
                return Result.error("不支持的文件格式，仅支持 .doc, .docx, .pdf, .txt 格式");
            }

            long fileSize = file.getSize();
            long maxSize = 10 * 1024 * 1024;
            if (fileSize > maxSize) {
                return Result.error("文件大小不能超过10MB");
            }

            String content = fileParserUtil.parseFile(file);
            
            if (content == null || content.trim().isEmpty()) {
                return Result.error("文件内容为空");
            }

            Map<String, Object> analysisResult = internshipReflectionService.analyzeReflectionContent(content);
            
            if ((Boolean) analysisResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("content", content);
                response.put("analysis", analysisResult.get("data"));
                return Result.success("分析成功", response);
            } else {
                if (Boolean.TRUE.equals(analysisResult.get("isNotReflection"))) {
                    return Result.error(analysisResult.get("message").toString());
                }
                return Result.error("分析失败: " + analysisResult.get("message"));
            }
        } catch (IllegalArgumentException e) {
            log.error("文件格式错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("AI分析实习心得文件失败: {}", e.getMessage(), e);
            return Result.error("分析失败: " + e.getMessage());
        }
    }
}
