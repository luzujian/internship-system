package com.gdmu.controller;

import com.gdmu.entity.*;
import com.gdmu.service.*;
import com.gdmu.utils.CurrentHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationManagementController {
    
    @Autowired
    private StudentUserService studentUserService;
    
    @Autowired
    private StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    private InternshipAIAnalysisService internshipAIAnalysisService;
    
    @Autowired
    private InternshipEvaluationService internshipEvaluationService;
    
    @Autowired
    private ClassService classService;
    
    @Autowired
    private SystemSettingsService systemSettingsService;
    
    @Autowired
    private MajorService majorService;
    
    @Autowired
    private DepartmentService departmentService;
    
    @Autowired
    private ClassCounselorRelationService classCounselorRelationService;
    
    @Autowired
    private InternshipEvaluationDetailService internshipEvaluationDetailService;
    
    @Autowired
    private CounselorAISettingsService counselorAISettingsService;

    @Autowired
    private StudentReflectionAIAnalysisService studentReflectionAIAnalysisService;

    @Autowired
    private InternshipReflectionService internshipReflectionService;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Data
    public static class StudentEvaluationInfo {
        private Long id;
        private String name;
        private String studentId;
        private String grade;
        private String className;
        private String majorName;
        private String department;
        private String company;
        private Integer reportCount;
        private Boolean hasReports;
        private Boolean hasCurrentPeriodReport;
        private Boolean aiAnalysis;
        private Boolean isEvaluated;
        private Boolean gradePublished;  // 成绩是否已发布
        private List<ReportInfo> reports;
        private AIAnalysisInfo aiAnalysisData;
        private EvaluationInfo evaluation;
    }
    
    @Data
    public static class ReportInfo {
        private Long id;
        private String date;
        private String content;
        private Integer periodNumber;  // 阶段编号
        private Boolean isDraft;  // 是否为草稿状态
        private String aiComment;  // AI生成的综合评语（comment）
        private String aiImprovements;  // AI生成的改进建议（improvements）
    }
    
    @Data
    public static class AIAnalysisInfo {
        private String overall;
        private List<String> keywords;
        private SentimentInfo sentiment;
        private SuggestedScoresInfo suggestedScores;
    }
    
    @Data
    public static class SentimentInfo {
        private Integer positive;
        private Integer neutral;
        private Integer negative;
    }
    
    @Data
    public static class SuggestedScoresInfo {
        private Map<String, Integer> scores;
        private Integer attitude;
        private Integer performance;
        private Integer report;
    }
    
    @Data
    public static class EvaluationInfo {
        private Map<String, Integer> scores;
        private Integer attitude;
        private Integer performance;
        private Integer report;
        private Integer companyEvaluation;
        private String comment;
        private Integer totalScore;
        private String grade;
    }
    
    @Data
    public static class PeriodInfo {
        private Integer period;
        private String periodName;
        private String startDate;
        private String endDate;
    }
    
    @GetMapping("/periods")
    public Result getInternshipPeriods() {
        log.info("获取实习时间阶段列表");

        try {
            // 优先使用 InternshipTimeSettings（系室教师设置），如果不存在则回退到 SystemSettings
            InternshipTimeSettings timeSettings = internshipTimeSettingsService.findLatest();
            SystemSettings systemSettings = systemSettingsService.findLatest();

            if (timeSettings == null && systemSettings == null) {
                return Result.error("系统设置未配置");
            }

            String startDateStr;
            String endDateStr;
            Integer cycle;

            if (timeSettings != null && timeSettings.getStartDate() != null && timeSettings.getEndDate() != null) {
                // 使用 InternshipTimeSettings（系室教师设置）
                startDateStr = timeSettings.getStartDate();
                endDateStr = timeSettings.getEndDate();
                cycle = timeSettings.getReportCycle();
                log.info("使用系室教师设置的实习时间: {} 至 {}, 周期: {} 天", startDateStr, endDateStr, cycle);
            } else if (systemSettings != null && systemSettings.getInternshipStartDate() != null && systemSettings.getInternshipEndDate() != null) {
                // 回退到 SystemSettings
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                startDateStr = sdf.format(systemSettings.getInternshipStartDate());
                endDateStr = sdf.format(systemSettings.getInternshipEndDate());
                cycle = systemSettings.getReportSubmissionCycle();
                log.info("使用系统设置的实习时间: {} 至 {}, 周期: {} 天", startDateStr, endDateStr, cycle);
            } else {
                return Result.error("实习起止日期未配置");
            }

            if (cycle == null || cycle <= 0) {
                cycle = 7;
            }

            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.util.Date startDate = sdf.parse(startDateStr);
            java.util.Date endDate = sdf.parse(endDateStr);
            
            long diffDays = (endDate.getTime() - startDate.getTime()) / (1000 * 60 * 60 * 24);
            int totalPeriods = (int) Math.ceil((double) diffDays / cycle);

            List<PeriodInfo> periods = new ArrayList<>();

            for (int i = 0; i < totalPeriods; i++) {
                PeriodInfo periodInfo = new PeriodInfo();
                periodInfo.setPeriod(i + 1);
                periodInfo.setPeriodName("第" + (i + 1) + "阶段");
                
                java.util.Calendar cal = java.util.Calendar.getInstance();
                cal.setTime(startDate);
                cal.add(java.util.Calendar.DAY_OF_MONTH, i * cycle);
                String periodStart = sdf.format(cal.getTime());
                
                cal.add(java.util.Calendar.DAY_OF_MONTH, cycle - 1);
                if (cal.getTime().after(endDate)) {
                    cal.setTime(endDate);
                }
                String periodEnd = sdf.format(cal.getTime());
                
                periodInfo.setStartDate(periodStart);
                periodInfo.setEndDate(periodEnd);
                periods.add(periodInfo);
            }
            
            return Result.success(periods);
        } catch (Exception e) {
            log.error("获取实习时间阶段失败", e);
            return Result.error("获取实习时间阶段失败：" + e.getMessage());
        }
    }
    
    @GetMapping("/students")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    public Result getStudentList(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) Long companyId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String className,
            @RequestParam(required = false) String hasReport,
            @RequestParam(required = false) String isEvaluated,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String majorName) {
        log.info("获取评分管理学生列表，周期: {}, 姓名: {}, 学号: {}, 公司ID: {}, 状态: {}, 班级: {}, 是否提交心得: {}, 是否评分: {}, 院系: {}, 专业: {}", 
            period, name, studentId, companyId, status, className, hasReport, isEvaluated, department, majorName);
        
        Long currentUserId = CurrentHolder.getUserId();
        String currentUserRole = CurrentHolder.getUserRole();
        log.info("获取评分管理学生列表，当前登录用户 ID: {}, 角色：{}", currentUserId, currentUserRole);
        
        List<Long> counselorClassIds = null;
        if (currentUserId != null) {
            List<com.gdmu.entity.Class> counselorClasses = classCounselorRelationService.findClassesByCounselorId(currentUserId);
            if (counselorClasses != null && !counselorClasses.isEmpty()) {
                counselorClassIds = counselorClasses.stream()
                        .map(com.gdmu.entity.Class::getId)
                        .collect(Collectors.toList());
                log.info("用户 {} 是辅导员，管理的班级 ID 列表：{}, 班级数量：{}", currentUserId, counselorClassIds, counselorClassIds.size());
            } else {
                log.info("用户 {} 没有关联的班级，将返回所有学生（可能是管理员或其他教师角色）", currentUserId);
            }
        } else {
            log.warn("当前登录用户 ID 为空");
        }
        
        List<StudentInternshipStatus> statusList = studentInternshipStatusService.list(
                null, name, null, status, companyId, null, null, null, className, studentId);
        
        List<StudentEvaluationInfo> studentList = new ArrayList<>();
        
        for (StudentInternshipStatus sis : statusList) {
            if (sis.getStudent() == null) {
                continue;
            }
            
            StudentUser student = sis.getStudent();
            
            if (counselorClassIds != null && student.getClassId() != null) {
                if (!counselorClassIds.contains(student.getClassId())) {
                    continue;
                }
            }
            
            StudentEvaluationInfo info = new StudentEvaluationInfo();
            info.setId(student.getId());
            info.setName(student.getName());
            info.setStudentId(student.getStudentUserId());
            info.setGrade(student.getGrade() != null ? student.getGrade().toString() : "");
            info.setClassName(getClassName(student.getClassId()));
            info.setMajorName(getMajorName(student.getMajorId()));
            info.setDepartment(getDepartmentByMajorId(student.getMajorId()));
            
            String companyName = "";
            if (sis.getCompany() != null && sis.getCompany().getCompanyName() != null) {
                companyName = sis.getCompany().getCompanyName();
            } else if (sis.getCompanyId() != null) {
                log.warn("学生 {} (ID: {}) 的company_id为 {}，但company对象为null", student.getName(), student.getId(), sis.getCompanyId());
            }
            info.setCompany(companyName);

            // 检查实习心得 - 获取学生所有阶段的心得（排除草稿状态）
            List<InternshipReflection> allReflections = internshipReflectionService.list(student.getId(), null, null, null);
            // 过滤掉草稿状态（remark = "0"）的心得
            List<InternshipReflection> reflections = new ArrayList<>();
            if (allReflections != null) {
                for (InternshipReflection r : allReflections) {
                    if (!"0".equals(r.getRemark())) {
                        reflections.add(r);
                    }
                }
            }
            boolean hasReflection = reflections != null && !reflections.isEmpty();
            int reportCount = hasReflection ? reflections.size() : 0;
            info.setReportCount(reportCount);
            info.setHasReports(hasReflection);

            final Integer selectedPeriod = (period != null && !period.isEmpty()) ?
                Integer.parseInt(period) : null;

            boolean hasCurrentPeriodReport = false;
            Integer effectivePeriod = selectedPeriod;

            // 如果没有选择阶段，使用当前阶段
            if (effectivePeriod == null) {
                effectivePeriod = internshipReflectionService.calculatePeriodNumber(new Date());
            }

            // 检查学生是否有指定阶段（当前阶段）的非草稿心得，并且AI分析已完成
            // 只有当实习心得提交后AI分析也完成时，教师才能看到评分按钮
            if (effectivePeriod != null && reflections != null) {
                for (InternshipReflection r : reflections) {
                    if (r.getPeriodNumber() != null && effectivePeriod.equals(r.getPeriodNumber())) {
                        // 检查该心得是否已有AI分析结果
                        StudentReflectionAIAnalysis aiResult = studentReflectionAIAnalysisService.findByReflectionId(r.getId());
                        if (aiResult != null) {
                            hasCurrentPeriodReport = true;
                            break;
                        }
                    }
                }
            }
            info.setHasCurrentPeriodReport(hasCurrentPeriodReport);

            if (hasReport != null && !hasReport.isEmpty()) {
                boolean filterHasReport = Boolean.parseBoolean(hasReport);
                if (filterHasReport != hasCurrentPeriodReport) {
                    continue;
                }
            }

            InternshipAIAnalysis aiAnalysis = internshipAIAnalysisService.findByStudentId(student.getId());
            info.setAiAnalysis(aiAnalysis != null);

            // 检查评价状态 - 需要按阶段检查
            Long currentPeriodReflectionId = null;
            if (effectivePeriod != null && reflections != null) {
                for (InternshipReflection r : reflections) {
                    if (effectivePeriod.equals(r.getPeriodNumber())) {
                        currentPeriodReflectionId = r.getId();
                        break;
                    }
                }
            }

            boolean evaluated = false;
            boolean gradePublished = false;
            if (currentPeriodReflectionId != null) {
                // 按学生检查 - 查找该学生的评价记录（评价保存在InternshipEvaluation表）
                InternshipEvaluation periodEvaluation = internshipEvaluationService.findByStudentId(student.getId());
                // 只有评分了（totalScore不为空且大于0）才算已评分，评语不是必填项
                evaluated = periodEvaluation != null && periodEvaluation.getTotalScore() != null && periodEvaluation.getTotalScore() > 0;
                gradePublished = periodEvaluation != null && periodEvaluation.getGradePublished() != null && periodEvaluation.getGradePublished() == 1;
            } else {
                // 当前阶段没有非草稿心得时，设为未评分
                evaluated = false;
            }
            info.setIsEvaluated(evaluated);
            info.setGradePublished(gradePublished);
            
            if (isEvaluated != null && !isEvaluated.isEmpty()) {
                boolean filterIsEvaluated = Boolean.parseBoolean(isEvaluated);
                if (filterIsEvaluated != evaluated) {
                    continue;
                }
            }
            
            if (department != null && !department.isEmpty()) {
                String studentDepartment = getDepartmentByMajorId(student.getMajorId());
                if (!department.equals(studentDepartment)) {
                    continue;
                }
            }
            
            if (majorName != null && !majorName.isEmpty()) {
                String studentMajor = getMajorName(student.getMajorId());
                if (!majorName.equals(studentMajor)) {
                    continue;
                }
            }
            
            studentList.add(info);
        }
        
        return Result.success(studentList);
    }
    
    @GetMapping("/student/{studentId}")
    public Result getStudentEvaluationInfo(@PathVariable Long studentId,
                                          @RequestParam(required = false) String period) {
        log.info("获取学生评分详情，学生ID: {}, 周期: {}", studentId, period);

        // 解析周期参数
        final Integer selectedPeriod = (period != null && !period.isEmpty()) ? Integer.parseInt(period) : null;

        StudentUser student = studentUserService.findById(studentId);
        if (student == null) {
            return Result.error("学生不存在");
        }

        StudentInternshipStatus status = studentInternshipStatusService.findByStudentId(studentId);

        StudentEvaluationInfo info = new StudentEvaluationInfo();
        info.setId(student.getId());
        info.setName(student.getName());
        info.setStudentId(student.getStudentUserId());
        info.setGrade(student.getGrade() != null ? student.getGrade().toString() : "");
        info.setClassName(getClassName(student.getClassId()));
        info.setMajorName(getMajorName(student.getMajorId()));
        info.setDepartment(getDepartmentByMajorId(student.getMajorId()));

        String companyName = "";
        if (status != null && status.getCompany() != null && status.getCompany().getCompanyName() != null) {
            companyName = status.getCompany().getCompanyName();
        } else if (status != null && status.getCompanyId() != null) {
            log.warn("学生 {} (ID: {}) 的company_id为 {}，但company对象为null", student.getName(), student.getId(), status.getCompanyId());
        }
        info.setCompany(companyName);

        // 获取学生所有阶段的心得（使用list方法而不是findByStudentId）
        List<InternshipReflection> allReflections = internshipReflectionService.list(studentId, null, null, null);

        // 计算有效的阶段（如果没选择阶段，使用当前阶段）
        Integer effectivePeriod = selectedPeriod;
        if (effectivePeriod == null) {
            effectivePeriod = internshipReflectionService.calculatePeriodNumber(new Date());
        }

        // 过滤掉草稿状态（remark = "0"）的心得（用于 isEvaluated 判断）
        List<InternshipReflection> reflections = new ArrayList<>();
        boolean currentPeriodIsDraft = false;
        if (allReflections != null) {
            for (InternshipReflection r : allReflections) {
                if (!"0".equals(r.getRemark())) {
                    reflections.add(r);
                }
                // 检查当前选中周期是否有草稿状态的心得
                if (effectivePeriod != null && effectivePeriod.equals(r.getPeriodNumber()) && "0".equals(r.getRemark())) {
                    currentPeriodIsDraft = true;
                }
            }
        }

        // 判断当前选中周期是否有心得（包括草稿状态）
        boolean hasCurrentPeriodReflection = false;
        if (effectivePeriod != null && allReflections != null) {
            for (InternshipReflection r : allReflections) {
                if (effectivePeriod.equals(r.getPeriodNumber())) {
                    hasCurrentPeriodReflection = true;
                    break;
                }
            }
        }

        int reflectionCount = (reflections != null) ? reflections.size() : 0;
        boolean hasReflection = reflectionCount > 0;
        info.setReportCount(reflectionCount);
        info.setHasReports(hasReflection);
        info.setHasCurrentPeriodReport(hasCurrentPeriodReflection && !currentPeriodIsDraft);

        List<ReportInfo> reportInfoList = new ArrayList<>();
        // 包含所有心得（包括草稿状态），让前端能正确判断当前选中周期是否有心得
        if (allReflections != null) {
            for (InternshipReflection reflection : allReflections) {
                ReportInfo reportInfo = new ReportInfo();
                reportInfo.setId(reflection.getId());
                reportInfo.setDate(reflection.getSubmitTime() != null ? reflection.getSubmitTime().toString() : "");
                reportInfo.setContent(reflection.getContent());
                reportInfo.setPeriodNumber(reflection.getPeriodNumber());
                reportInfo.setIsDraft("0".equals(reflection.getRemark()));
                // AI分析结果：使用StudentReflectionAIAnalysis的overallAnalysis
                StudentReflectionAIAnalysis aiAnalysisForReport = studentReflectionAIAnalysisService.findByReflectionId(reflection.getId());
                if (aiAnalysisForReport != null && aiAnalysisForReport.getOverallAnalysis() != null) {
                    reportInfo.setAiComment(aiAnalysisForReport.getOverallAnalysis());
                    reportInfo.setAiImprovements(null);
                } else {
                    reportInfo.setAiComment(reflection.getAiAnalysis());  // 回退到旧字段
                    reportInfo.setAiImprovements(reflection.getAiImprovements());
                }
                reportInfoList.add(reportInfo);
            }
        }
        info.setReports(reportInfoList);

        // 查找当前选中周期的心得ID
        Long currentPeriodReflectionId = null;
        if (effectivePeriod != null && reflections != null) {
            for (InternshipReflection r : reflections) {
                if (effectivePeriod.equals(r.getPeriodNumber())) {
                    currentPeriodReflectionId = r.getId();
                    break;
                }
            }
        }

        // 根据当前周期心得ID查询AI分析
        StudentReflectionAIAnalysis aiAnalysis = null;
        if (currentPeriodReflectionId != null) {
            aiAnalysis = studentReflectionAIAnalysisService.findByReflectionId(currentPeriodReflectionId);
        }
        info.setAiAnalysis(aiAnalysis != null);

        if (aiAnalysis != null) {
            AIAnalysisInfo aiInfo = new AIAnalysisInfo();
            aiInfo.setOverall(aiAnalysis.getOverallAnalysis());
            aiInfo.setKeywords(aiAnalysis.getKeywords());

            SentimentInfo sentiment = new SentimentInfo();
            sentiment.setPositive(aiAnalysis.getSentimentPositive());
            sentiment.setNeutral(aiAnalysis.getSentimentNeutral());
            sentiment.setNegative(aiAnalysis.getSentimentNegative());
            aiInfo.setSentiment(sentiment);

            SuggestedScoresInfo suggestedScores = new SuggestedScoresInfo();

            // 创建动态评分项Map
            Map<String, Integer> scores = new HashMap<>();
            // 尝试从 scoreDetails 获取评分
            if (aiAnalysis.getScoreDetails() != null) {
                Object scoreDetailsObj = aiAnalysis.getScoreDetails();
                log.info("scoreDetailsObj type: {}, value: {}", scoreDetailsObj.getClass().getName(), scoreDetailsObj);
                if (scoreDetailsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> scoreDetails = (Map<String, Object>) scoreDetailsObj;
                    log.info("scoreDetails content: {}", scoreDetails);
                    // 计算各维度得分（取等级中的最高分）
                    calculateDimensionScores(scoreDetails, scores);
                }
            } else {
                log.info("aiAnalysis.getScoreDetails() is null");
            }
            log.info("Final scores map: {}", scores);
            suggestedScores.setScores(scores);

            // 保持向后兼容
            suggestedScores.setAttitude(scores.getOrDefault("attitude", 0));
            suggestedScores.setPerformance(scores.getOrDefault("performance", 0));
            suggestedScores.setReport(scores.getOrDefault("report", 0));

            aiInfo.setSuggestedScores(suggestedScores);

            info.setAiAnalysisData(aiInfo);
        }

        // 获取学生综合评价（用于显示评价详情）
        InternshipEvaluation evaluation = internshipEvaluationService.findByStudentId(studentId);
        // 根据周期参数判断评价状态 - 只有评分了（totalScore不为空且大于0）才算已评分，评语不是必填项
        boolean evaluated = evaluation != null && evaluation.getTotalScore() != null && evaluation.getTotalScore() > 0;
        boolean gradePublished = evaluation != null && evaluation.getGradePublished() != null && evaluation.getGradePublished() == 1;
        info.setIsEvaluated(evaluated);
        info.setGradePublished(gradePublished);

        if (evaluation != null) {
            EvaluationInfo evalInfo = new EvaluationInfo();
            
            // 获取类别评分详情
            List<InternshipEvaluationDetail> evaluationDetails = internshipEvaluationDetailService.findByEvaluationId(evaluation.getId());
            Map<String, Integer> scores = new HashMap<>();
            
            if (evaluationDetails != null && !evaluationDetails.isEmpty()) {
                // 优先使用类别评分详情
                for (InternshipEvaluationDetail detail : evaluationDetails) {
                    scores.put(detail.getCategoryCode(), detail.getScore());
                }
            } else {
                // 如果没有类别评分详情，使用传统的评分字段
                scores.put("attitude", evaluation.getAttitudeScore());
                scores.put("performance", evaluation.getPerformanceScore());
                scores.put("report", evaluation.getReportScore());
                scores.put("companyEvaluation", evaluation.getCompanyEvaluationScore());
            }
            
            evalInfo.setScores(scores);
            
            // 保持向后兼容
            evalInfo.setAttitude(evaluation.getAttitudeScore());
            evalInfo.setPerformance(evaluation.getPerformanceScore());
            evalInfo.setReport(evaluation.getReportScore());
            evalInfo.setCompanyEvaluation(evaluation.getCompanyEvaluationScore());
            evalInfo.setComment(evaluation.getComment());
            evalInfo.setTotalScore(evaluation.getTotalScore());
            evalInfo.setGrade(evaluation.getGrade());
            info.setEvaluation(evalInfo);
        }
        
        return Result.success(info);
    }
    
    @PostMapping("/submit")
    public Result submitEvaluation(@RequestBody Map<String, Object> params) {
        log.info("提交学生评价，参数: {}", params);
        
        Object studentIdObj = params.get("studentId");
        Object commentObj = params.get("comment");
        if (studentIdObj == null || commentObj == null) {
            return Result.error("缺少必要参数");
        }
        Long studentId = Long.valueOf(studentIdObj.toString());
        String comment = commentObj.toString();
        
        Long teacherId = CurrentHolder.getUserId();
        if (teacherId == null) {
            return Result.error("无法获取当前教师信息");
        }

        // 获取评分规则的权重配置
        List<CounselorCategoryWeight> categoryWeights = counselorAISettingsService.getCategoryWeights(teacherId);
        Map<String, Integer> weightMap = new HashMap<>();
        for (CounselorCategoryWeight weight : categoryWeights) {
            weightMap.put(weight.getCategoryCode(), weight.getWeight() != null ? weight.getWeight() : 1);
        }

        // 动态处理评分项，从params中提取所有评分项（排除studentId、comment等非评分字段）
        Map<String, Object> scoreDetails = new HashMap<>();
        int totalWeightedScore = 0;
        int totalWeight = 0;

        // 定义非评分字段
        Set<String> excludeFields = new HashSet<>(Arrays.asList("studentId", "comment"));

        // 遍历params，提取评分项
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (!excludeFields.contains(key)) {
                try {
                    Integer score = Integer.valueOf(entry.getValue().toString());
                    scoreDetails.put(key, score);
                    int weight = weightMap.getOrDefault(key, 1);
                    totalWeightedScore += score * weight;
                    totalWeight += weight;
                } catch (NumberFormatException e) {
                    log.warn("评分项 {} 的值不是有效数字: {}", key, entry.getValue());
                }
            }
        }

        // 按权重计算加权平均分
        int totalScore = totalWeight > 0 ? Math.round((float) totalWeightedScore / totalWeight) : 0;

        // 计算等级
        String grade = calculateGrade(totalScore);
        
        // 创建评价对象
        InternshipEvaluation evaluation = new InternshipEvaluation();
        evaluation.setStudentId(studentId);
        evaluation.setComment(comment);
        evaluation.setTotalScore(totalScore);
        evaluation.setGrade(grade);
        evaluation.setEvaluatorId(teacherId);
        
        // 设置传统的评分字段（保持向后兼容）
        // 如果没有传递固定字段名，从动态评分项中获取或设置为默认值0
        if (scoreDetails.containsKey("attitude")) {
            evaluation.setAttitudeScore((Integer) scoreDetails.get("attitude"));
        } else if (scoreDetails.containsKey("attitudeScore")) {
            evaluation.setAttitudeScore((Integer) scoreDetails.get("attitudeScore"));
        } else {
            evaluation.setAttitudeScore(0);
        }
        if (scoreDetails.containsKey("performance")) {
            evaluation.setPerformanceScore((Integer) scoreDetails.get("performance"));
        } else if (scoreDetails.containsKey("performanceScore")) {
            evaluation.setPerformanceScore((Integer) scoreDetails.get("performanceScore"));
        } else {
            evaluation.setPerformanceScore(0);
        }
        if (scoreDetails.containsKey("report")) {
            evaluation.setReportScore((Integer) scoreDetails.get("report"));
        } else if (scoreDetails.containsKey("reportScore")) {
            evaluation.setReportScore((Integer) scoreDetails.get("reportScore"));
        } else {
            evaluation.setReportScore(0);
        }
        if (scoreDetails.containsKey("companyEvaluation")) {
            evaluation.setCompanyEvaluationScore((Integer) scoreDetails.get("companyEvaluation"));
        } else if (scoreDetails.containsKey("companyEvaluationScore")) {
            evaluation.setCompanyEvaluationScore((Integer) scoreDetails.get("companyEvaluationScore"));
        } else {
            evaluation.setCompanyEvaluationScore(0);
        }
        
        InternshipEvaluation existing = internshipEvaluationService.findByStudentId(studentId);

        int result;
        Long evaluationId;
        if (existing != null) {
            evaluation.setId(existing.getId());
            // 更新时保留原有的固定评分字段值（如果没有传递）
            if (evaluation.getAttitudeScore() == null) {
                evaluation.setAttitudeScore(existing.getAttitudeScore());
            }
            if (evaluation.getPerformanceScore() == null) {
                evaluation.setPerformanceScore(existing.getPerformanceScore());
            }
            if (evaluation.getReportScore() == null) {
                evaluation.setReportScore(existing.getReportScore());
            }
            if (evaluation.getCompanyEvaluationScore() == null) {
                evaluation.setCompanyEvaluationScore(existing.getCompanyEvaluationScore());
            }
            // 动态评分项已经通过scoreDetails计算了totalScore，这里保留总分不变
            evaluation.setTotalScore(totalScore);
            evaluation.setGrade(calculateGrade(totalScore));
            result = internshipEvaluationService.update(evaluation);
            evaluationId = existing.getId();
        } else {
            // 新增评价
            result = internshipEvaluationService.insert(evaluation);
            evaluationId = evaluation.getId();
        }
        
        if (result > 0) {
            // 更新实习进展记录状态为已评分（success）
            try {
                Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
                List<InternshipReflection> reflections = internshipReflectionService.list(studentId, null, null, null);
                if (reflections != null && currentPeriod != null) {
                    for (InternshipReflection r : reflections) {
                        if (currentPeriod.equals(r.getPeriodNumber())) {
                            progressRecordService.updateStatusByRelatedId(r.getId(), "reflection", "success");
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.error("更新实习进展记录状态失败: {}", e.getMessage(), e);
            }

            // 保存类别评分详情
            try {
                List<InternshipEvaluationDetail> details = new ArrayList<>();

                for (CounselorCategoryWeight weight : categoryWeights) {
                    if (scoreDetails.containsKey(weight.getCategoryCode())) {
                        InternshipEvaluationDetail detail = new InternshipEvaluationDetail();
                        detail.setEvaluationId(evaluationId);
                        detail.setStudentId(studentId);
                        detail.setCategoryCode(weight.getCategoryCode());
                        detail.setCategoryName(weight.getCategoryName());
                        detail.setScore((Integer) scoreDetails.get(weight.getCategoryCode()));
                        detail.setWeight(weight.getWeight());
                        details.add(detail);
                    }
                }

                if (!details.isEmpty()) {
                    internshipEvaluationDetailService.saveEvaluationDetails(evaluationId, studentId, details);
                }
            } catch (Exception e) {
                log.error("保存类别评分详情失败: {}", e.getMessage(), e);
            }

            // 更新对应学生的实习心得remark为"1"（已批阅）
            try {
                // 计算当前阶段号
                Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
                // 获取学生的所有实习心得
                List<InternshipReflection> reflections = internshipReflectionService.list(studentId, null, null, null);
                // 查找当前阶段的实习心得
                InternshipReflection reflectionToUpdate = null;
                if (reflections != null && currentPeriod != null) {
                    for (InternshipReflection r : reflections) {
                        if (currentPeriod.equals(r.getPeriodNumber())) {
                            reflectionToUpdate = r;
                            break;
                        }
                    }
                }
                if (reflectionToUpdate != null) {
                    reflectionToUpdate.setRemark("1");
                    reflectionToUpdate.setUpdateTime(new Date());
                    internshipReflectionService.updateById(reflectionToUpdate);
                    log.info("更新实习心得remark为已批阅，学生ID: {}, 阶段: {}", studentId, currentPeriod);
                } else {
                    log.warn("未找到学生ID: {} 当前阶段: {} 的实习心得", studentId, currentPeriod);
                }
            } catch (Exception e) {
                log.error("更新实习心得remark失败: {}", e.getMessage(), e);
            }

            return Result.success("评价提交成功");
        }
        return Result.error("评价提交失败");
    }

    /**
     * 批量上传学生成绩（标记为已提交）
     * @param params 包含学生ID列表
     * @return 上传结果
     */
    @PostMapping("/batch-upload")
    public Result batchUploadGrades(@RequestBody Map<String, Object> params) {
        log.info("批量上传学生成绩，参数: {}", params);

        try {
            @SuppressWarnings("unchecked")
            List<?> rawList = (List<?>) params.get("studentIds");
            if (rawList == null || rawList.isEmpty()) {
                return Result.error("学生ID列表不能为空");
            }

            // 将列表元素转换为Long类型（JSON解析可能返回Integer）
            List<Long> studentIds = new ArrayList<>();
            for (Object item : rawList) {
                if (item instanceof Number) {
                    studentIds.add(((Number) item).longValue());
                }
            }

            if (studentIds.isEmpty()) {
                return Result.error("学生ID列表不能为空");
            }

            // 调用批量发布成绩方法
            try {
                int publishedCount = internshipEvaluationService.publishGrades(studentIds);
                Map<String, Object> result = Map.of(
                        "success", publishedCount,
                        "fail", studentIds.size() - publishedCount,
                        "total", studentIds.size()
                );
                return Result.success("成绩上传完成，成功 " + publishedCount + " 条，失败 " + (studentIds.size() - publishedCount) + " 条", result);
            } catch (Exception e) {
                log.error("批量发布成绩失败: {}", e.getMessage(), e);
                return Result.error("批量上传成绩失败：" + e.getMessage());
            }
        } catch (Exception e) {
            log.error("批量上传成绩失败: {}", e.getMessage(), e);
            return Result.error("批量上传成绩失败：" + e.getMessage());
        }
    }

    private String calculateGrade(int totalScore) {
        if (totalScore >= 90) return "优秀";
        if (totalScore >= 80) return "良好";
        if (totalScore >= 70) return "中等";
        if (totalScore >= 60) return "及格";
        return "不及格";
    }
    
    @GetMapping("/statistics")
    public Result getEvaluationStatistics() {
        log.info("获取评分统计信息");
        
        List<InternshipEvaluation> allEvaluations = internshipEvaluationService.findAll();
        
        Map<String, Long> gradeStats = allEvaluations.stream()
                .collect(Collectors.groupingBy(InternshipEvaluation::getGrade, Collectors.counting()));
        
        Map<String, Object> statistics = Map.of(
                "total", allEvaluations.size(),
                "excellent", gradeStats.getOrDefault("优秀", 0L),
                "good", gradeStats.getOrDefault("良好", 0L),
                "medium", gradeStats.getOrDefault("中等", 0L),
                "pass", gradeStats.getOrDefault("及格", 0L),
                "fail", gradeStats.getOrDefault("不及格", 0L)
        );
        
        return Result.success(statistics);
    }
    
    private String getClassName(Long classId) {
        if (classId == null) {
            return "";
        }
        
        try {
            com.gdmu.entity.Class clazz = classService.findById(classId);
            if (clazz != null) {
                return clazz.getName();
            }
            return "";
        } catch (Exception e) {
            log.error("获取班级名称失败，班级ID: {}", classId, e);
            return "";
        }
    }
    
    private String getMajorName(Long majorId) {
        if (majorId == null) {
            return "";
        }
        
        try {
            com.gdmu.entity.Major major = majorService.findById(majorId);
            if (major != null) {
                return major.getName();
            }
            return "";
        } catch (Exception e) {
            log.error("获取专业名称失败，专业ID: {}", majorId, e);
            return "";
        }
    }
    
    private String getDepartmentByMajorId(Long majorId) {
        if (majorId == null) {
            return "";
        }

        try {
            com.gdmu.entity.Major major = majorService.findById(majorId);
            if (major != null && major.getDepartmentId() != null) {
                com.gdmu.entity.Department department = departmentService.findById(major.getDepartmentId());
                if (department != null) {
                    return department.getName();
                }
            }
            return "";
        } catch (Exception e) {
            log.error("获取院系失败，专业ID: {}", majorId, e);
            return "";
        }
    }

    /**
     * 从AI返回的评分详情中计算各维度得分
     * 新格式: {"innovation_ability": {"score": 64, "grade": "及格", "name": "创新能力", "category": "innovation_ability"}, ...}
     * 旧格式: {"innovation_ability_优秀": 0, "innovation_ability_良好": 80, ...}
     */
    private void calculateDimensionScores(Map<String, Object> scoreDetails, Map<String, Integer> scores) {
        if (scoreDetails == null || scores == null) {
            return;
        }

        for (Map.Entry<String, Object> entry : scoreDetails.entrySet()) {
            String key = entry.getKey();
            Object rawValue = entry.getValue();

            Integer value = null;

            // 处理新嵌套格式: {"score": 64, "grade": "及格", ...}
            if (rawValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) rawValue;
                Object scoreObj = nestedMap.get("score");
                if (scoreObj instanceof Number) {
                    value = ((Number) scoreObj).intValue();
                } else if (scoreObj instanceof String) {
                    try {
                        value = Integer.parseInt(((String) scoreObj).trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            // 处理旧格式: 直接是数字或字符串
            else if (rawValue instanceof Number) {
                value = ((Number) rawValue).intValue();
            } else if (rawValue instanceof String) {
                try {
                    value = Integer.parseInt(((String) rawValue).trim());
                } catch (NumberFormatException ignored) {
                }
            }

            // 直接使用原始类别名称作为key，避免映射冲突
            if (value != null && value > 0) {
                scores.put(key, value);
            }
        }

        log.info("Final scores output: {}", scores);
    }
}
