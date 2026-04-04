package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentReflectionAIAnalysis;
import com.gdmu.service.StudentReflectionAIAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reflection/ai-analysis")
public class StudentReflectionAIAnalysisController {
    
    @Autowired
    private StudentReflectionAIAnalysisService aiAnalysisService;
    
    @PostMapping("/analyze")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_AI_ANALYSIS", operationType = "INSERT", description = "AI分析学生实习心得")
    public Result analyzeReflection(@RequestBody Map<String, Object> request) {
        log.info("AI分析学生实习心得: {}", request);
        try {
            Long reflectionId = Long.parseLong(request.get("reflectionId").toString());
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            Long studentId = Long.parseLong(request.get("studentId").toString());
            String content = (String) request.get("content");
            
            Map<String, Object> result = aiAnalysisService.analyzeReflectionContent(content, counselorId, studentId, reflectionId);
            
            if ((Boolean) result.get("success")) {
                return Result.success("分析成功", result.get("data"));
            } else {
                return Result.error((String) result.get("message"));
            }
        } catch (Exception e) {
            log.error("AI分析学生实习心得失败: {}", e.getMessage());
            return Result.error("分析失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    @Log(module = "STUDENT_REFLECTION_AI_ANALYSIS", operationType = "SELECT", description = "查询AI分析结果")
    public Result getAnalysisById(@PathVariable Long id) {
        log.info("查询AI分析结果，ID: {}", id);
        try {
            StudentReflectionAIAnalysis analysis = aiAnalysisService.findById(id);
            if (analysis == null) {
                return Result.error("AI分析结果不存在");
            }
            return Result.success(analysis);
        } catch (Exception e) {
            log.error("查询AI分析结果失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/reflection/{reflectionId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    @Log(module = "STUDENT_REFLECTION_AI_ANALYSIS", operationType = "SELECT", description = "根据心得ID查询AI分析结果")
    public Result getAnalysisByReflectionId(@PathVariable Long reflectionId) {
        log.info("根据心得ID查询AI分析结果，心得ID: {}", reflectionId);
        try {
            StudentReflectionAIAnalysis analysis = aiAnalysisService.findByReflectionId(reflectionId);
            if (analysis == null) {
                return Result.success(null);
            }
            // 解析 analysisReport 为分开的字段
            return Result.success(parseAnalysisReport(analysis));
        } catch (Exception e) {
            log.error("查询AI分析结果失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 解析 AI 分析报告，提取各部分内容
     */
    private Map<String, Object> parseAnalysisReport(StudentReflectionAIAnalysis analysis) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", analysis.getId());
        result.put("reflectionId", analysis.getReflectionId());
        result.put("studentId", analysis.getStudentId());
        result.put("counselorId", analysis.getCounselorId());
        result.put("totalScore", analysis.getTotalScore());
        result.put("grade", analysis.getGrade());
        result.put("keywords", analysis.getKeywords());
        result.put("sentimentPositive", analysis.getSentimentPositive());
        result.put("sentimentNeutral", analysis.getSentimentNeutral());
        result.put("sentimentNegative", analysis.getSentimentNegative());
        result.put("scoreDetails", analysis.getScoreDetails());
        result.put("overallAnalysis", analysis.getOverallAnalysis());
        result.put("analysisTime", analysis.getAnalysisTime());

        if (analysis.getAnalysisReport() != null && !analysis.getAnalysisReport().isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> parsedReport = objectMapper.readValue(analysis.getAnalysisReport(), Map.class);
                result.put("summary", parsedReport.get("summary"));
                result.put("highlights", parsedReport.get("strengths"));
                result.put("improvements", parsedReport.get("suggestions"));
                result.put("comment", parsedReport.get("comment"));
                result.put("aspects", parsedReport.get("aspects"));
            } catch (Exception e) {
                // 如果不是 JSON，尝试解析 Markdown
                Map<String, Object> parsed = parseMarkdownReport(analysis.getAnalysisReport());
                result.put("summary", parsed.get("summary"));
                result.put("highlights", parsed.get("strengths"));
                result.put("improvements", parsed.get("suggestions"));
                result.put("comment", parsed.get("comment"));
                result.put("aspects", parsed.get("aspects"));
            }
        }

        if (analysis.getStudent() != null) {
            Map<String, Object> studentInfo = new HashMap<>();
            studentInfo.put("id", analysis.getStudent().getId());
            studentInfo.put("name", analysis.getStudent().getName());
            studentInfo.put("studentUserId", analysis.getStudent().getStudentUserId());
            result.put("student", studentInfo);
        }

        return result;
    }

    /**
     * 解析 Markdown 格式的报告文本
     */
    private Map<String, Object> parseMarkdownReport(String markdown) {
        Map<String, Object> result = new HashMap<>();
        result.put("summary", "");
        result.put("strengths", new java.util.ArrayList<>());
        result.put("suggestions", new java.util.ArrayList<>());
        result.put("comment", "");

        if (markdown == null || markdown.isEmpty()) {
            return result;
        }

        try {
            java.util.List<String> strengths = new java.util.ArrayList<>();
            java.util.List<String> suggestions = new java.util.ArrayList<>();

            String[] sections = markdown.split("(?=##\\s)");
            for (String section : sections) {
                String trimmed = section.trim();
                if (trimmed.isEmpty()) continue;

                java.util.regex.Matcher titleMatcher = java.util.regex.Pattern.compile("^##\\s*([^#\\n]+)").matcher(trimmed);
                String title = "";
                if (titleMatcher.find()) {
                    title = titleMatcher.group(1).trim();
                }

                String content = trimmed.replaceFirst("^##\\s*[^#\\n]+\\n?", "").trim();

                if (title.contains("亮点") || title.contains("优点")) {
                    parseListItems(content, strengths);
                } else if (title.contains("改进") || title.contains("建议")) {
                    parseListItems(content, suggestions);
                } else if (title.contains("内容") || title.contains("概述") || title.contains("总结")) {
                    result.put("summary", cleanText(content));
                } else if (title.contains("综合") || title.contains("评价")) {
                    result.put("comment", cleanText(content));
                }
            }

            if (strengths.isEmpty() && suggestions.isEmpty() && "".equals(result.get("summary"))) {
                String summaryOnly = markdown.replaceAll("##\\s*[^#\\n]+\\n?", "").replaceAll("#+\\s*", "").trim();
                result.put("summary", summaryOnly);
            }

            result.put("strengths", strengths);
            result.put("suggestions", suggestions);
        } catch (Exception e) {
            log.error("解析 Markdown 报告失败: {}", e.getMessage());
            result.put("summary", markdown.replaceAll("^#+\\s*", "").replaceAll("\\*\\*", "").trim());
        }

        return result;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\*+", "").replaceAll("\\n+", "\n").trim();
    }

    private void parseListItems(String text, java.util.List<String> items) {
        if (text == null || text.isEmpty()) return;

        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String cleanedLine = trimmed.replaceFirst("^\\d+[.、:：]\\s*", "")
                    .replaceFirst("^[-*]\\s*", "")
                    .trim();

            if (cleanedLine.isEmpty()) continue;

            if (cleanedLine.contains("：") || cleanedLine.contains(":")) {
                String[] parts = cleanedLine.split("[:：]", 2);
                if (parts.length == 2) {
                    String title = parts[0].replaceAll("\\*+", "").trim();
                    String content = parts[1].replaceAll("\\*+", "").trim();
                    if (!title.isEmpty() && !content.isEmpty()) {
                        items.add(title + "：" + content);
                        continue;
                    }
                }
            }

            if (!cleanedLine.isEmpty()) {
                items.add(cleanedLine.replaceAll("\\*+", ""));
            }
        }
    }
    
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    @Log(module = "STUDENT_REFLECTION_AI_ANALYSIS", operationType = "SELECT", description = "根据学生ID查询AI分析结果")
    public Result getAnalysisByStudentId(@PathVariable Long studentId) {
        log.info("根据学生ID查询AI分析结果，学生ID: {}", studentId);
        try {
            StudentReflectionAIAnalysis analysis = aiAnalysisService.findByStudentId(studentId);
            if (analysis == null) {
                return Result.success(null);
            }
            return Result.success(analysis);
        } catch (Exception e) {
            log.error("查询AI分析结果失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/counselor/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_AI_ANALYSIS", operationType = "SELECT", description = "查询辅导员的所有AI分析结果")
    public Result getAnalysesByCounselorId(@PathVariable Long counselorId) {
        log.info("查询辅导员的所有AI分析结果，辅导员ID: {}", counselorId);
        try {
            List<StudentReflectionAIAnalysis> analyses = aiAnalysisService.findByCounselorId(counselorId);
            return Result.success(analyses);
        } catch (Exception e) {
            log.error("查询AI分析结果失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/download/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    @Log(module = "STUDENT_REFLECTION_AI_ANALYSIS", operationType = "SELECT", description = "下载AI分析报告")
    public ResponseEntity<byte[]> downloadAnalysisReport(@PathVariable Long id) {
        log.info("下载AI分析报告，ID: {}", id);
        try {
            StudentReflectionAIAnalysis analysis = aiAnalysisService.findById(id);
            if (analysis == null || analysis.getAnalysisReport() == null) {
                return ResponseEntity.notFound().build();
            }
            
            String report = analysis.getAnalysisReport();
            String studentName = analysis.getStudent() != null ? analysis.getStudent().getName() : "学生";
            String fileName = studentName + "_AI分析报告.md";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(report.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.error("下载AI分析报告失败: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_AI_ANALYSIS", operationType = "DELETE", description = "删除AI分析结果")
    public Result deleteAnalysis(@PathVariable Long id) {
        log.info("删除AI分析结果，ID: {}", id);
        try {
            int result = aiAnalysisService.deleteById(id);
            if (result > 0) {
                return Result.success("删除成功");
            }
            return Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除AI分析结果失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }
}
