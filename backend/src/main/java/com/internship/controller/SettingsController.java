package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.AIAnalysisSettings;
import com.internship.entity.KeywordLibrary;
import com.internship.entity.Result;
import com.internship.entity.ScoringRule;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.service.AIAnalysisSettingsService;
import com.internship.service.InternshipCalendarService;
import com.internship.service.KeywordLibraryService;
import com.internship.service.ScoringRuleService;
import com.internship.service.TeacherPermissionService;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/settings")
@SuppressWarnings("unchecked")
public class SettingsController {
    
    @Autowired
    private InternshipCalendarService internshipCalendarService;

    @Autowired
    private AIAnalysisSettingsService aiAnalysisSettingsService;
    
    @Autowired
    private KeywordLibraryService keywordLibraryService;
    
    @Autowired
    private ScoringRuleService scoringRuleService;

    @Autowired
    private TeacherPermissionService teacherPermissionService;
    
    /**
     * 判断实习是否已开始（是否需要提交实习心得）
     * 实习开始条件：当前日期 >= 实习开始日期(startDate)
     */
    @GetMapping("/internship-status")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    public Result getInternshipStatus() {
        log.info("判断实习是否已开始");
        try {
            InternshipCalendar settings = internshipCalendarService.getCalendar();

            Map<String, Object> result = new HashMap<>();

            if (settings.getStartDate() == null || settings.getStartDate().isEmpty()) {
                // 未设置实习开始日期，默认为未开始
                result.put("started", false);
                result.put("message", "尚未设置实习开始日期");
                return Result.success(result);
            }

            LocalDate startDate = LocalDate.parse(settings.getStartDate(), DateTimeFormatter.ISO_LOCAL_DATE);
            LocalDate today = LocalDate.now();
            boolean started = !today.isBefore(startDate);

            result.put("started", started);
            result.put("startDate", settings.getStartDate());
            result.put("today", today.toString());

            if (started) {
                result.put("message", "实习已开始，修改评分规则需等下一周期提交实习心得时才会生效");
            } else {
                result.put("message", "实习尚未开始，评分规则修改立即生效");
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("判断实习状态失败: {}", e.getMessage());
            return Result.error("判断实习状态失败");
        }
    }

    /**
     * 获取实习时间设置（学生端实习确认表使用）
     */
    @GetMapping("/internship-time")
    public Result getInternshipTime() {
        log.info("获取实习时间设置");
        try {
            InternshipCalendar settings = internshipCalendarService.getCalendar();

            Map<String, Object> result = new HashMap<>();
            result.put("startDate", settings.getStartDate());
            result.put("endDate", settings.getEndDate());

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取实习时间设置失败: {}", e.getMessage());
            return Result.error("获取实习时间设置失败");
        }
    }

    @GetMapping("/internship-nodes")
    @PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_COUNSELOR', 'TEACHER_DEPARTMENT', 'STUDENT')")
    public Result getInternshipNodes() {
        log.info("获取实习时间节点设置");
        try {
            return Result.success(toLegacyNodes(internshipCalendarService.getCalendar()));
        } catch (Exception e) {
            log.error("获取实习时间节点设置失败: {}", e.getMessage());
            return Result.error("获取实习时间节点设置失败");
        }
    }
    
    @PutMapping("/internship-nodes")
    @PreAuthorize("hasRole('TEACHER_DEPARTMENT')")
    @Log(module = "SETTINGS", operationType = "UPDATE", description = "更新实习时间节点设置")
    public Result updateInternshipNodes(@RequestBody Map<String, Object> settings) {
        log.info("更新实习时间节点设置");
        try {
            InternshipCalendar currentSettings = internshipCalendarService.getCalendar();
            if (settings.containsKey("reportCycle")) {
                currentSettings.setReportCycle(toInteger(settings.get("reportCycle")));
            }
            if (settings.containsKey("startDate")) {
                currentSettings.setStartDate((String) settings.get("startDate"));
            }
            if (settings.containsKey("endDate")) {
                currentSettings.setEndDate((String) settings.get("endDate"));
            }
            if (settings.containsKey("evaluationDeadline")) {
                currentSettings.setAppraisalDeadline((String) settings.get("evaluationDeadline"));
            }
            if (settings.containsKey("appraisalDeadline")) {
                currentSettings.setAppraisalDeadline((String) settings.get("appraisalDeadline"));
            }
            if (settings.containsKey("version")) {
                currentSettings.setVersion((String) settings.get("version"));
            }
            InternshipCalendar saved = internshipCalendarService.saveCalendar(
                    currentSettings, currentUpdaterId(), currentUpdaterName());
            return Result.success("实习时间节点设置更新成功", toLegacyNodes(saved));
        } catch (Exception e) {
            log.error("更新实习时间节点设置失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    @GetMapping("/internship-calendar")
    public Result getInternshipCalendar() {
        return Result.success(internshipCalendarService.getCalendar());
    }

    @PutMapping("/internship-calendar")
    @PreAuthorize("hasRole('TEACHER_DEPARTMENT')")
    @Log(module = "SETTINGS", operationType = "UPDATE", description = "更新全校统一实习日历")
    public Result updateInternshipCalendar(@RequestBody InternshipCalendar calendar) {
        try {
            return Result.success(internshipCalendarService.saveCalendar(
                    calendar, currentUpdaterId(), currentUpdaterName()));
        } catch (Exception e) {
            log.error("更新全校统一实习日历失败: {}", e.getMessage());
            return Result.error(e.getMessage());
        }
    }

    /**
     * 心得周期配置项是否可见。
     * 仅当管理员已将"智慧评分"菜单权限（menu:evaluation）授予至少一类教师角色时，重要设置页面的心得周期配置项才显示。
     */
    @GetMapping("/report-cycle-visible")
    @PreAuthorize("hasAnyRole('TEACHER_COLLEGE', 'TEACHER_COUNSELOR', 'TEACHER_DEPARTMENT', 'TEACHER')")
    public Result getReportCycleVisible() {
        boolean visible = teacherPermissionService.isPermissionGrantedToAnyTeacherRole("menu:evaluation");
        Map<String, Object> result = new HashMap<>();
        result.put("visible", visible);
        return Result.success(result);
    }
    
    @GetMapping("/ai-analysis")
    @PreAuthorize("hasRole('TEACHER_COUNSELOR')")
    public Result getAIAnalysisSettings() {
        log.info("获取AI分析设置");
        try {
            AIAnalysisSettings settings = aiAnalysisSettingsService.findLatest();
            List<KeywordLibrary> keywordLibraryList = keywordLibraryService.findAll();
            List<ScoringRule> scoringRuleList = scoringRuleService.findAll();
            
            Map<String, Object> result = new HashMap<>();
            
            Map<String, Object> aiAnalysis = new HashMap<>();
            if (settings != null) {
                aiAnalysis.put("enableKeywordLibrary", settings.getEnableKeywordLibrary());
                aiAnalysis.put("enableScoringRules", settings.getEnableScoringRules());
            } else {
                aiAnalysis.put("enableKeywordLibrary", 1);
                aiAnalysis.put("enableScoringRules", 1);
            }
            
            List<Map<String, Object>> keywordLibrary = new java.util.ArrayList<>();
            for (KeywordLibrary kl : keywordLibraryList) {
                Map<String, Object> klMap = new HashMap<>();
                klMap.put("id", kl.getId());
                klMap.put("category", kl.getCategory());
                klMap.put("keyword", kl.getKeyword());
                klMap.put("weight", kl.getWeight());
                klMap.put("description", kl.getDescription());
                keywordLibrary.add(klMap);
            }
            aiAnalysis.put("keywordLibrary", keywordLibrary);
            
            List<Map<String, Object>> scoringRules = new java.util.ArrayList<>();
            for (ScoringRule sr : scoringRuleList) {
                Map<String, Object> srMap = new HashMap<>();
                srMap.put("id", sr.getId());
                srMap.put("ruleName", sr.getRuleName());
                srMap.put("ruleType", sr.getRuleType());
                srMap.put("weight", sr.getWeight());
                srMap.put("description", sr.getDescription());
                scoringRules.add(srMap);
            }
            aiAnalysis.put("scoringRules", scoringRules);
            
            result.put("aiAnalysis", aiAnalysis);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取AI分析设置失败: {}", e.getMessage());
            return Result.error("获取AI分析设置失败");
        }
    }
    
    @PutMapping("/ai-analysis")
    @PreAuthorize("hasRole('TEACHER_COUNSELOR')")
    @Log(module = "SETTINGS", operationType = "UPDATE", description = "更新AI分析设置")
    public Result updateAIAnalysisSettings(@RequestBody Map<String, Object> settings) {
        log.info("更新AI分析设置");
        try {
            Map<String, Object> aiAnalysis = (Map<String, Object>) settings.get("aiAnalysis");
            if (aiAnalysis == null) {
                return Result.error("AI分析设置不能为空");
            }
            
            AIAnalysisSettings currentSettings = aiAnalysisSettingsService.findLatest();
            if (currentSettings == null) {
                currentSettings = new AIAnalysisSettings();
                aiAnalysisSettingsService.insert(currentSettings);
            }
            
            if (aiAnalysis.containsKey("enableKeywordLibrary")) {
                currentSettings.setEnableKeywordLibrary(toInteger(aiAnalysis.get("enableKeywordLibrary")));
            }
            if (aiAnalysis.containsKey("enableScoringRules")) {
                currentSettings.setEnableScoringRules(toInteger(aiAnalysis.get("enableScoringRules")));
            }
            
            aiAnalysisSettingsService.update(currentSettings);
            
            if (aiAnalysis.containsKey("keywordLibrary")) {
                List<Map<String, Object>> keywordLibraryList = (List<Map<String, Object>>) aiAnalysis.get("keywordLibrary");
                
                for (Map<String, Object> kl : keywordLibraryList) {
                    KeywordLibrary keywordLibrary = new KeywordLibrary();
                    if (kl.containsKey("id")) {
                        keywordLibrary = keywordLibraryService.findById(toLong(kl.get("id")));
                        if (keywordLibrary == null) {
                            continue;
                        }
                    }
                    
                    if (kl.containsKey("category")) {
                        keywordLibrary.setCategory((String) kl.get("category"));
                    }
                    if (kl.containsKey("keyword")) {
                        keywordLibrary.setKeyword((String) kl.get("keyword"));
                    }
                    if (kl.containsKey("weight")) {
                        keywordLibrary.setWeight(toInteger(kl.get("weight")));
                    }
                    if (kl.containsKey("description")) {
                        keywordLibrary.setDescription((String) kl.get("description"));
                    }
                    
                    if (keywordLibrary.getId() != null) {
                        keywordLibraryService.update(keywordLibrary);
                    } else {
                        keywordLibraryService.insert(keywordLibrary);
                    }
                }
            }
            
            if (aiAnalysis.containsKey("scoringRules")) {
                List<Map<String, Object>> scoringRuleList = (List<Map<String, Object>>) aiAnalysis.get("scoringRules");
                
                for (Map<String, Object> sr : scoringRuleList) {
                    ScoringRule scoringRule = new ScoringRule();
                    if (sr.containsKey("id")) {
                        scoringRule = scoringRuleService.findById(toLong(sr.get("id")));
                        if (scoringRule == null) {
                            continue;
                        }
                    }
                    
                    if (sr.containsKey("ruleName")) {
                        scoringRule.setRuleName((String) sr.get("ruleName"));
                    }
                    if (sr.containsKey("ruleType")) {
                        scoringRule.setRuleType((String) sr.get("ruleType"));
                    }
                    if (sr.containsKey("weight")) {
                        scoringRule.setWeight(toInteger(sr.get("weight")));
                    }
                    if (sr.containsKey("description")) {
                        scoringRule.setDescription((String) sr.get("description"));
                    }
                    
                    if (scoringRule.getId() != null) {
                        scoringRuleService.update(scoringRule);
                    } else {
                        scoringRuleService.insert(scoringRule);
                    }
                }
            }
            
            return Result.success("AI分析设置更新成功");
        } catch (Exception e) {
            log.error("更新AI分析设置失败: {}", e.getMessage());
            return Result.error("更新AI分析设置失败");
        }
    }
    
    private Map<String, Object> toLegacyNodes(InternshipCalendar calendar) {
        Map<String, Object> result = new HashMap<>();
        result.put("applicationStartTime", null);
        result.put("applicationEndTime", null);
        result.put("companyConfirmationDeadline", null);
        result.put("delayApplicationDeadline", null);
        result.put("reportDeadline", null);
        result.put("startDate", calendar.getStartDate());
        result.put("endDate", calendar.getEndDate());
        result.put("reportCycle", calendar.getReportCycle());
        result.put("evaluationDeadline", calendar.getAppraisalDeadline());
        result.put("appraisalDeadline", calendar.getAppraisalDeadline());
        result.put("version", calendar.getVersion());
        return result;
    }

    private Long currentUpdaterId() {
        return CurrentHolder.getUserId();
    }

    private String currentUpdaterName() {
        return CurrentHolder.getUser() == null ? null : CurrentHolder.getUser().getUsername();
    }
    
    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
    
    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        }
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        if (value instanceof String) {
            try {
                return Long.parseLong((String) value);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }
}
