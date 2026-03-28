package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentReflectionAIAnalysis;
import com.gdmu.service.ClassCounselorRelationService;
import com.gdmu.service.CounselorAISettingsService;
import com.gdmu.service.StudentReflectionAIAnalysisService;
import com.gdmu.service.StudentReflectionEvaluationService;
import com.gdmu.service.InternshipReflectionService;
import com.gdmu.entity.InternshipReflection;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/counselor/ai-analysis")
public class CounselorAIAnalysisController {

    @Autowired
    private StudentReflectionAIAnalysisService studentReflectionAIAnalysisService;

    @Autowired
    private StudentReflectionEvaluationService studentReflectionEvaluationService;

    @Autowired
    private CounselorAISettingsService counselorAISettingsService;

    @Autowired
    private ClassCounselorRelationService classCounselorRelationService;

    @Autowired
    private InternshipReflectionService internshipReflectionService;

    @GetMapping("/reflection/{reflectionId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_ANALYSIS", operationType = "SELECT", description = "获取实习心得AI分析结果")
    public Result getAnalysisByReflectionId(@PathVariable Long reflectionId) {
        log.info("获取实习心得AI分析结果，心得ID: {}", reflectionId);
        try {
            StudentReflectionAIAnalysis analysis = studentReflectionAIAnalysisService.findByReflectionId(reflectionId);
            if (analysis == null) {
                return Result.error("未找到AI分析结果");
            }

            // 构建与管理员端相同格式的返回数据
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("reflectionId", analysis.getReflectionId());
            resultData.put("studentId", analysis.getStudentId());
            resultData.put("counselorId", analysis.getCounselorId());
            resultData.put("totalScore", analysis.getTotalScore());
            resultData.put("grade", analysis.getGrade());
            resultData.put("keywords", analysis.getKeywords());
            resultData.put("sentimentPositive", analysis.getSentimentPositive());
            resultData.put("sentimentNeutral", analysis.getSentimentNeutral());
            resultData.put("sentimentNegative", analysis.getSentimentNegative());
            resultData.put("scoreDetails", analysis.getScoreDetails());
            resultData.put("overallAnalysis", analysis.getOverallAnalysis());
            resultData.put("analysisTime", analysis.getAnalysisTime());

            // 解析 analysisReport 为分开的字段
            if (analysis.getAnalysisReport() != null && !analysis.getAnalysisReport().isEmpty()) {
                try {
                    // 尝试作为 JSON 解析
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> parsedReport = objectMapper.readValue(analysis.getAnalysisReport(), Map.class);
                    resultData.put("summary", parsedReport.get("summary"));
                    resultData.put("highlights", parsedReport.get("strengths"));
                    resultData.put("improvements", parsedReport.get("suggestions"));
                    resultData.put("comment", parsedReport.get("comment"));
                    resultData.put("aspects", parsedReport.get("aspects"));
                    resultData.put("analysisReport", null); // 清除原始 Markdown，避免前端重复解析
                } catch (Exception e) {
                    // 如果不是 JSON，说明 analysisReport 存储的是原始 Markdown 文本
                    // 从 Markdown 中解析各部分
                    Map<String, Object> parsedMarkdown = parseMarkdownReport(analysis.getAnalysisReport());
                    resultData.put("summary", parsedMarkdown.get("summary"));
                    resultData.put("highlights", parsedMarkdown.get("strengths"));
                    resultData.put("improvements", parsedMarkdown.get("suggestions"));
                    resultData.put("comment", parsedMarkdown.get("comment"));
                    resultData.put("aspects", parsedMarkdown.get("aspects"));
                    resultData.put("analysisReport", null);
                }
            }

            // 包含学生信息
            if (analysis.getStudent() != null) {
                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("id", analysis.getStudent().getId());
                studentInfo.put("name", analysis.getStudent().getName());
                studentInfo.put("studentUserId", analysis.getStudent().getStudentUserId());
                resultData.put("student", studentInfo);
            }

            return Result.success(resultData);
        } catch (Exception e) {
            log.error("获取AI分析结果失败: {}", e.getMessage());
            return Result.error("获取AI分析结果失败: " + e.getMessage());
        }
    }

    /**
     * 解析 Markdown 格式的报告文本，提取各部分内容
     * 支持格式：## 一、内容概述 / ## 亮点分析 / ## 改进建议 / ## 综合评价
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

            // 规范化：移除回车符，使用 \n 作为换行符
            String normalized = markdown.replace("\r\n", "\n").replace("\r", "\n");

            // 移除顶部的 # 标题行（如 # 实习心得分析报告）
            normalized = normalized.replaceFirst("^#\\s*[^\\n]*\\n*", "");

            // 按 ## 分割 section（保留标题）
            // 使用 (?=##) 来分割，这样分割符保留在结果字符串的开头
            String[] sections = normalized.split("(?=##)");

            for (String section : sections) {
                String trimmed = section.trim();
                if (trimmed.isEmpty()) continue;

                // 找到标题行（以 ## 开头的行）
                int titleEndIndex = -1;
                for (int i = 0; i < trimmed.length(); i++) {
                    if (trimmed.charAt(i) == '\n') {
                        titleEndIndex = i;
                        break;
                    }
                }

                if (titleEndIndex == -1) continue;

                String titleLine = trimmed.substring(0, titleEndIndex);
                String sectionContent = trimmed.substring(titleEndIndex).trim();

                // 移除 ## 前缀获取标题文本
                String title = titleLine.replaceFirst("^##\\s*", "").replaceFirst("^##", "").trim();

                if (title.contains("亮点") || title.contains("优点")) {
                    parseListItems(sectionContent, strengths);
                } else if (title.contains("改进") || title.contains("建议")) {
                    parseListItems(sectionContent, suggestions);
                } else if (title.contains("内容") || title.contains("概述") || title.contains("总结")) {
                    result.put("summary", cleanText(sectionContent));
                } else if (title.contains("综合") || title.contains("评价")) {
                    result.put("comment", cleanText(sectionContent));
                }
            }

            // 如果没有提取到任何内容，把整个 Markdown 作为 summary
            if (strengths.isEmpty() && suggestions.isEmpty() &&
                (result.get("summary") == null || "".equals(result.get("summary").toString())) &&
                (result.get("comment") == null || "".equals(result.get("comment").toString()))) {
                // 移除所有 # 标题行，保留正文
                String summaryOnly = normalized
                    .replaceAll("^#+\\s*[^\\n]*\\n*", "")  // 移除 # 标题行
                    .replaceAll("##\\s*[^\\n]*\\n*", "")   // 移除 ## 标题行
                    .replaceAll("\\*+", "")                  // 移除 * 标记
                    .trim();
                result.put("summary", summaryOnly);
            }

            result.put("strengths", strengths);
            result.put("suggestions", suggestions);

            log.info("Markdown解析完成: summary长度={}, strengths数量={}, suggestions数量={}, comment长度={}",
                result.get("summary").toString().length(), strengths.size(), suggestions.size(), result.get("comment").toString().length());
        } catch (Exception e) {
            log.error("解析 Markdown 报告失败: {}", e.getMessage(), e);
            result.put("summary", markdown.replaceAll("^#+\\s*", "").replaceAll("\\*\\*", "").trim());
        }

        return result;
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("\\*+", "")   // 移除粗体标记
                    .replaceAll("\\n+", "\n")  // 规范换行
                    .trim();
    }

    /**
     * 解析文本中的列表项
     * 支持格式：1. xxx、2. xxx、- xxx、* xxx、带粗体的列表项
     */
    private void parseListItems(String text, java.util.List<String> items) {
        if (text == null || text.isEmpty()) return;

        String[] lines = text.split("\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            // 移除列表标记（数字+点、破折号、星号）
            String cleanedLine = trimmed
                    .replaceFirst("^\\d+[.、、.、]\\s*", "")  // 1. 2. 等
                    .replaceFirst("^[-*●]\\s*", "")        // - * 等
                    .trim();

            if (cleanedLine.isEmpty()) continue;

            // 移除文本中的粗体标记 **xxx** -> xxx
            cleanedLine = cleanedLine.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");

            // 如果包含冒号，分割标题和内容
            if (cleanedLine.contains("：") || cleanedLine.contains(":")) {
                String[] parts = cleanedLine.split("[:：]", 2);
                if (parts.length == 2) {
                    String itemTitle = parts[0].replaceAll("\\*+", "").trim();
                    String itemContent = parts[1].replaceAll("\\*+", "").trim();
                    if (!itemTitle.isEmpty() && !itemContent.isEmpty()) {
                        items.add(itemTitle + "：" + itemContent);
                        continue;
                    }
                }
            }

            // 否则直接添加整行（移除剩余的 * 标记）
            if (!cleanedLine.isEmpty()) {
                items.add(cleanedLine.replaceAll("\\*+", ""));
            }
        }
    }

    @GetMapping("/reflection-detail/{reflectionId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_ANALYSIS", operationType = "SELECT", description = "获取实习心得详情")
    public Result getReflectionDetail(@PathVariable Long reflectionId) {
        log.info("获取实习心得详情，心得ID: {}", reflectionId);
        try {
            InternshipReflection reflection = internshipReflectionService.findById(reflectionId);
            if (reflection == null) {
                return Result.error("未找到实习心得");
            }
            return Result.success(reflection);
        } catch (Exception e) {
            log.error("获取实习心得详情失败: {}", e.getMessage());
            return Result.error("获取实习心得详情失败: " + e.getMessage());
        }
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_ANALYSIS", operationType = "SELECT", description = "获取学生AI分析结果")
    public Result getAnalysisByStudentId(@PathVariable Long studentId) {
        log.info("获取学生AI分析结果，学生ID: {}", studentId);
        try {
            StudentReflectionAIAnalysis analysis = studentReflectionAIAnalysisService.findByStudentId(studentId);
            if (analysis == null) {
                return Result.error("未找到AI分析结果");
            }

            // 构建与管理员端相同格式的返回数据
            Map<String, Object> resultData = new HashMap<>();
            resultData.put("id", analysis.getId());
            resultData.put("reflectionId", analysis.getReflectionId());
            resultData.put("studentId", analysis.getStudentId());
            resultData.put("counselorId", analysis.getCounselorId());
            resultData.put("totalScore", analysis.getTotalScore());
            resultData.put("grade", analysis.getGrade());
            resultData.put("keywords", analysis.getKeywords());
            resultData.put("sentimentPositive", analysis.getSentimentPositive());
            resultData.put("sentimentNeutral", analysis.getSentimentNeutral());
            resultData.put("sentimentNegative", analysis.getSentimentNegative());
            resultData.put("scoreDetails", analysis.getScoreDetails());
            resultData.put("overallAnalysis", analysis.getOverallAnalysis());
            resultData.put("createTime", analysis.getAnalysisTime());

            // 解析 analysisReport 为分开的字段
            String analysisReport = analysis.getAnalysisReport();
            if (analysisReport != null && !analysisReport.isEmpty()) {
                log.info("analysisReport 内容长度: {}, 前100字符: {}", analysisReport.length(), analysisReport.substring(0, Math.min(100, analysisReport.length())));
                try {
                    // 尝试作为 JSON 解析
                    com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    Map<String, Object> parsedReport = objectMapper.readValue(analysisReport, Map.class);
                    log.info("JSON解析成功: summary={}, strengths={}, suggestions={}, comment={}",
                        parsedReport.get("summary"), parsedReport.get("strengths"), parsedReport.get("suggestions"), parsedReport.get("comment"));
                    resultData.put("summary", parsedReport.get("summary"));
                    resultData.put("highlights", parsedReport.get("strengths"));
                    resultData.put("improvements", parsedReport.get("suggestions"));
                    resultData.put("comment", parsedReport.get("comment"));
                    resultData.put("aspects", parsedReport.get("aspects"));
                } catch (Exception e) {
                    log.warn("JSON解析失败，尝试Markdown解析，错误: {}", e.getMessage());
                    // 如果不是 JSON，说明 analysisReport 存储的是原始 Markdown 文本
                    // 从 Markdown 中解析各部分
                    Map<String, Object> parsedMarkdown = parseMarkdownReport(analysisReport);
                    log.info("Markdown解析结果: summary={}, strengths={}, suggestions={}, comment={}",
                        parsedMarkdown.get("summary"), parsedMarkdown.get("strengths"), parsedMarkdown.get("suggestions"), parsedMarkdown.get("comment"));
                    resultData.put("summary", parsedMarkdown.get("summary"));
                    resultData.put("highlights", parsedMarkdown.get("strengths"));
                    resultData.put("improvements", parsedMarkdown.get("suggestions"));
                    resultData.put("comment", parsedMarkdown.get("comment"));
                    resultData.put("aspects", parsedMarkdown.get("aspects"));
                }
            } else {
                log.warn("analysisReport 字段为空，使用 overallAnalysis 作为 summary");
                resultData.put("summary", analysis.getOverallAnalysis());
            }

            // 包含学生信息
            if (analysis.getStudent() != null) {
                Map<String, Object> studentInfo = new HashMap<>();
                studentInfo.put("id", analysis.getStudent().getId());
                studentInfo.put("name", analysis.getStudent().getName());
                studentInfo.put("studentUserId", analysis.getStudent().getStudentUserId());
                resultData.put("student", studentInfo);
            }

            return Result.success(resultData);
        } catch (Exception e) {
            log.error("获取AI分析结果失败: {}", e.getMessage());
            return Result.error("获取AI分析结果失败: " + e.getMessage());
        }
    }

    @GetMapping("/counselor/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_ANALYSIS", operationType = "SELECT", description = "获取辅导员所有AI分析结果")
    public Result getAnalysesByCounselorId(@PathVariable Long counselorId) {
        log.info("获取辅导员所有AI分析结果，辅导员ID: {}", counselorId);
        try {
            List<StudentReflectionAIAnalysis> analyses = studentReflectionAIAnalysisService.findByCounselorId(counselorId);
            return Result.success(analyses);
        } catch (Exception e) {
            log.error("获取AI分析结果失败: {}", e.getMessage());
            return Result.error("获取AI分析结果失败: " + e.getMessage());
        }
    }

    @PostMapping("/trigger/{reflectionId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_ANALYSIS", operationType = "UPDATE", description = "手动触发AI分析")
    public Result triggerAnalysis(@PathVariable Long reflectionId, @RequestBody Map<String, Long> params) {
        log.info("手动触发AI分析，心得ID: {}", reflectionId);
        try {
            Long counselorId = params.get("counselorId");
            if (counselorId == null) {
                return Result.error("辅导员ID不能为空");
            }

            InternshipReflection report = internshipReflectionService.findById(reflectionId);
            if (report == null) {
                return Result.error("实习心得不存在");
            }

            Long studentId = report.getStudentId();
            Long counselorIdForStudent = classCounselorRelationService.findCounselorIdByStudentId(studentId);
            if (counselorIdForStudent == null || !counselorIdForStudent.equals(counselorId)) {
                return Result.error("您没有权限分析此学生的实习心得");
            }

            if (!counselorAISettingsService.isAiScoringEnabled(counselorId)) {
                return Result.error("AI评分功能未启用");
            }

            Map<String, Object> result = studentReflectionAIAnalysisService.analyzeReflectionContent(
                    report.getContent(), counselorId, studentId, reflectionId);

            if (result != null && !result.isEmpty()) {
                return Result.success("AI分析完成", result);
            } else {
                return Result.error("AI分析失败");
            }
        } catch (Exception e) {
            log.error("触发AI分析失败: {}", e.getMessage());
            return Result.error("触发AI分析失败: " + e.getMessage());
        }
    }

    @GetMapping("/download/{reflectionId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_ANALYSIS", operationType = "DOWNLOAD", description = "下载AI分析报告")
    public void downloadAnalysisReport(@PathVariable Long reflectionId, HttpServletResponse response) {
        log.info("下载AI分析报告，心得ID: {}", reflectionId);
        try {
            StudentReflectionAIAnalysis analysis = studentReflectionAIAnalysisService.findByReflectionId(reflectionId);
            if (analysis == null) {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"未找到AI分析结果\"}");
                return;
            }

            // 优先从 analysis 对象获取学生信息（已通过 LEFT JOIN 加载）
            String studentName = "未知学生";
            if (analysis.getStudent() != null && analysis.getStudent().getName() != null) {
                studentName = analysis.getStudent().getName();
            }

            response.setContentType("application/pdf");
            String fileName = URLEncoder.encode("AI分析报告_" + studentName + "_" + System.currentTimeMillis(), StandardCharsets.UTF_8) + ".pdf";
            response.setHeader("Content-Disposition", "attachment; filename=\"" + fileName + "\"");

            Document document = new Document();
            OutputStream outputStream = response.getOutputStream();
            PdfWriter.getInstance(document, outputStream);

            document.open();

            Font titleFont = new Font(Font.FontFamily.HELVETICA, 18, Font.BOLD);
            Font headerFont = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
            Font contentFont = new Font(Font.FontFamily.HELVETICA, 12, Font.NORMAL);

            // 标题
            Paragraph title = new Paragraph("AI Analysis Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" "));

            // 基本信息
            document.add(new Paragraph("Student: " + studentName, contentFont));
            document.add(new Paragraph("Analysis Time: " + (analysis.getAnalysisTime() != null ? analysis.getAnalysisTime().toString() : ""), contentFont));
            document.add(new Paragraph("Total Score: " + (analysis.getTotalScore() != null ? analysis.getTotalScore().toString() : ""), contentFont));
            document.add(new Paragraph("Grade: " + (analysis.getGrade() != null ? analysis.getGrade() : ""), contentFont));

            // 情感分析
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Sentiment Analysis", headerFont));
            document.add(new Paragraph("Positive: " + (analysis.getSentimentPositive() != null ? analysis.getSentimentPositive() : 0) + "%", contentFont));
            document.add(new Paragraph("Neutral: " + (analysis.getSentimentNeutral() != null ? analysis.getSentimentNeutral() : 0) + "%", contentFont));
            document.add(new Paragraph("Negative: " + (analysis.getSentimentNegative() != null ? analysis.getSentimentNegative() : 0) + "%", contentFont));

            // 关键词
            if (analysis.getKeywords() != null && !analysis.getKeywords().isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Keywords", headerFont));
                // 过滤空字符串
                List<String> validKeywords = analysis.getKeywords().stream()
                    .filter(k -> k != null && !k.trim().isEmpty())
                    .collect(java.util.stream.Collectors.toList());
                if (!validKeywords.isEmpty()) {
                    document.add(new Paragraph(String.join(", ", validKeywords), contentFont));
                }
            }

            // 解析 analysisReport 为结构化内容
            boolean reportContentAdded = false;
            if (analysis.getAnalysisReport() != null && !analysis.getAnalysisReport().trim().isEmpty()) {
                String analysisReport = analysis.getAnalysisReport().trim();

                // 判断是否为有效内容（非仅包含引号和空白）
                boolean isValidContent = !analysisReport.matches("^[\\s\"\'\\*]+$");

                if (isValidContent) {
                    boolean parsed = false;

                    // 尝试 JSON 解析
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        Map<String, Object> parsedReport = objectMapper.readValue(analysisReport, Map.class);

                        Object summary = parsedReport.get("summary");
                        Object strengths = parsedReport.get("strengths");
                        Object suggestions = parsedReport.get("suggestions");
                        Object comment = parsedReport.get("comment");

                        // 如果 JSON 解析有有效内容
                        if ((summary != null && !summary.toString().trim().isEmpty()) ||
                            (strengths instanceof List && !((List<?>)strengths).isEmpty()) ||
                            (suggestions instanceof List && !((List<?>)suggestions).isEmpty()) ||
                            (comment != null && !comment.toString().trim().isEmpty())) {

                            if (summary != null && !summary.toString().trim().isEmpty()) {
                                document.add(new Paragraph(" "));
                                document.add(new Paragraph("Summary", headerFont));
                                document.add(new Paragraph(summary.toString(), contentFont));
                            }

                            if (strengths instanceof List) {
                                List<?> strengthsList = (List<?>) strengths;
                                List<String> validStrengths = strengthsList.stream()
                                    .filter(s -> s != null && !s.toString().trim().isEmpty())
                                    .map(s -> s.toString().trim())
                                    .collect(java.util.stream.Collectors.toList());
                                if (!validStrengths.isEmpty()) {
                                    document.add(new Paragraph(" "));
                                    document.add(new Paragraph("Strengths", headerFont));
                                    for (String s : validStrengths) {
                                        document.add(new Paragraph("• " + s, contentFont));
                                    }
                                }
                            }

                            if (suggestions instanceof List) {
                                List<?> suggestionsList = (List<?>) suggestions;
                                List<String> validSuggestions = suggestionsList.stream()
                                    .filter(s -> s != null && !s.toString().trim().isEmpty())
                                    .map(s -> s.toString().trim())
                                    .collect(java.util.stream.Collectors.toList());
                                if (!validSuggestions.isEmpty()) {
                                    document.add(new Paragraph(" "));
                                    document.add(new Paragraph("Suggestions for Improvement", headerFont));
                                    for (String s : validSuggestions) {
                                        document.add(new Paragraph("• " + s, contentFont));
                                    }
                                }
                            }

                            if (comment != null && !comment.toString().trim().isEmpty()) {
                                document.add(new Paragraph(" "));
                                document.add(new Paragraph("Overall Comment", headerFont));
                                document.add(new Paragraph(comment.toString(), contentFont));
                            }

                            parsed = true;
                            reportContentAdded = true;
                        }
                    } catch (Exception e) {
                        log.debug("JSON 解析失败，尝试 Markdown: {}", e.getMessage());
                    }

                    // 如果 JSON 解析失败或无有效内容，尝试 Markdown
                    if (!parsed) {
                        Map<String, Object> parsedMarkdown = parseMarkdownReport(analysisReport);

                        Object summary = parsedMarkdown.get("summary");
                        Object strengths = parsedMarkdown.get("strengths");
                        Object suggestions = parsedMarkdown.get("suggestions");
                        Object comment = parsedMarkdown.get("comment");

                        boolean hasValidContent = (summary != null && !summary.toString().trim().isEmpty()) ||
                            (strengths instanceof List && !((List<?>)strengths).isEmpty()) ||
                            (suggestions instanceof List && !((List<?>)suggestions).isEmpty()) ||
                            (comment != null && !comment.toString().trim().isEmpty());

                        if (hasValidContent) {
                            if (summary != null && !summary.toString().trim().isEmpty()) {
                                document.add(new Paragraph(" "));
                                document.add(new Paragraph("Summary", headerFont));
                                document.add(new Paragraph(summary.toString(), contentFont));
                            }

                            if (strengths instanceof List) {
                                List<?> strengthsList = (List<?>) strengths;
                                List<String> validStrengths = strengthsList.stream()
                                    .filter(s -> s != null && !s.toString().trim().isEmpty())
                                    .map(s -> s.toString().trim())
                                    .collect(java.util.stream.Collectors.toList());
                                if (!validStrengths.isEmpty()) {
                                    document.add(new Paragraph(" "));
                                    document.add(new Paragraph("Strengths", headerFont));
                                    for (String s : validStrengths) {
                                        document.add(new Paragraph("• " + s, contentFont));
                                    }
                                }
                            }

                            if (suggestions instanceof List) {
                                List<?> suggestionsList = (List<?>) suggestions;
                                List<String> validSuggestions = suggestionsList.stream()
                                    .filter(s -> s != null && !s.toString().trim().isEmpty())
                                    .map(s -> s.toString().trim())
                                    .collect(java.util.stream.Collectors.toList());
                                if (!validSuggestions.isEmpty()) {
                                    document.add(new Paragraph(" "));
                                    document.add(new Paragraph("Suggestions for Improvement", headerFont));
                                    for (String s : validSuggestions) {
                                        document.add(new Paragraph("• " + s, contentFont));
                                    }
                                }
                            }

                            if (comment != null && !comment.toString().trim().isEmpty()) {
                                document.add(new Paragraph(" "));
                                document.add(new Paragraph("Overall Comment", headerFont));
                                document.add(new Paragraph(comment.toString(), contentFont));
                            }

                            reportContentAdded = true;
                        }
                    }
                }
            }

            // 如果 analysisReport 解析后没有内容，使用 overallAnalysis 作为 fallback
            if (!reportContentAdded && analysis.getOverallAnalysis() != null && !analysis.getOverallAnalysis().trim().isEmpty()) {
                document.add(new Paragraph(" "));
                document.add(new Paragraph("Overall Analysis", headerFont));
                document.add(new Paragraph(analysis.getOverallAnalysis(), contentFont));
            }

            document.close();
            outputStream.flush();

            log.info("AI分析报告下载成功");
        } catch (Exception e) {
            log.error("下载AI分析报告失败: {}", e.getMessage(), e);
            try {
                response.setContentType("application/json;charset=utf-8");
                response.getWriter().write("{\"code\": 500, \"message\": \"下载失败: " + e.getMessage() + "\"}");
            } catch (Exception ex) {
                log.error("写入错误响应失败", ex);
            }
        }
    }

    @GetMapping("/check-enabled/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_AI_ANALYSIS", operationType = "SELECT", description = "检查辅导员AI评分是否启用")
    public Result checkAiScoringEnabled(@PathVariable Long counselorId) {
        log.info("检查辅导员AI评分是否启用，辅导员ID: {}", counselorId);
        try {
            boolean enabled = counselorAISettingsService.isAiScoringEnabled(counselorId);
            Map<String, Object> data = new HashMap<>();
            data.put("aiScoringEnabled", enabled);
            return Result.success(data);
        } catch (Exception e) {
            log.error("检查AI评分状态失败: {}", e.getMessage());
            return Result.error("检查失败: " + e.getMessage());
        }
    }
}
