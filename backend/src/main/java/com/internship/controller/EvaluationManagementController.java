package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.*;
import com.internship.entity.dto.InternshipCalendar;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.service.*;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.internship.service.InternshipProgressEvents.append;

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
    private MajorService majorService;
    
    @Autowired
    private DepartmentService departmentService;
    
    @Autowired
    private InternshipEvaluationDetailService internshipEvaluationDetailService;
    
    @Autowired
    private CounselorAISettingsService counselorAISettingsService;

    @Autowired
    private StudentReflectionAIAnalysisService studentReflectionAIAnalysisService;

    @Autowired
    private StudentReflectionEvaluationService studentReflectionEvaluationService;

    @Autowired
    private InternshipReflectionService internshipReflectionService;

    @Autowired
    private InternshipReflectionMapper reflectionMapper;

    @Autowired
    private InternshipCalendarService internshipCalendarService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

    @Autowired
    private EvaluationAccessPolicy evaluationAccessPolicy;

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
        private Integer periodNumber;  // 全局实习期次编号
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
        log.info("获取全局心得期次列表");

        try {
            InternshipCalendar calendar = internshipCalendarService.getCalendar();
            if (calendar == null || calendar.getStartDate() == null || calendar.getEndDate() == null
                    || calendar.getReportCycle() == null || calendar.getReportCycle() <= 0) {
                return Result.error("全局实习日历或心得周期未配置");
            }
            List<PeriodInfo> periods = new ArrayList<>();
            LocalDate cursor = LocalDate.parse(calendar.getStartDate());
            LocalDate internshipEnd = LocalDate.parse(calendar.getEndDate());
            int periodNumber = 1;
            while (!cursor.isAfter(internshipEnd)) {
                LocalDate periodEnd = cursor.plusDays(calendar.getReportCycle() - 1L);
                if (periodEnd.isAfter(internshipEnd)) periodEnd = internshipEnd;
                PeriodInfo periodInfo = new PeriodInfo();
                periodInfo.setPeriod(periodNumber);
                periodInfo.setPeriodName("第" + periodNumber + "期");
                periodInfo.setStartDate(cursor.toString());
                periodInfo.setEndDate(periodEnd.toString());
                periods.add(periodInfo);
                periodNumber++;
                cursor = periodEnd.plusDays(1);
            }
            return Result.success(periods);
        } catch (Exception e) {
            log.error("获取全局心得期次失败", e);
            return Result.error("获取全局心得期次失败：" + e.getMessage());
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
        log.info("获取评分管理学生列表：period={}, hasFilters={}", period,
                name != null || studentId != null || companyId != null || status != null
                        || className != null || hasReport != null || isEvaluated != null
                        || department != null || majorName != null);
        
        Set<Long> readableStudentIds = evaluationAccessPolicy.readableStudentIds();
        Long currentUserId = CurrentHolder.getUserId();
        
        List<StudentInternshipStatus> statusList = studentInternshipStatusService.list(
                null, name, null, status, companyId, null, null, null, className, studentId);
        
        List<StudentEvaluationInfo> studentList = new ArrayList<>();
        
        for (StudentInternshipStatus sis : statusList) {
            if (sis.getStudent() == null) {
                continue;
            }
            
            StudentUser student = sis.getStudent();

            if (readableStudentIds != null && !readableStudentIds.contains(student.getId())) {
                continue;
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
                log.warn("学生企业关联缺失：studentId={}, companyId={}", student.getId(), sis.getCompanyId());
            }
            info.setCompany(companyName);

            // 检查学生所有期次的心得（排除草稿状态）
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

            // 如果没有选择期次，使用当前全局期次
            if (effectivePeriod == null) {
                effectivePeriod = internshipReflectionService.calculatePeriodNumber(new Date());
            }

            // A submitted report remains submitted while AI is pending or unavailable.
            if (effectivePeriod != null) {
                for (InternshipReflection reflection : reflections) {
                    if (effectivePeriod.equals(reflection.getPeriodNumber())
                            && reflection.getSubmitTime() != null && reflection.getContent() != null
                            && !reflection.getContent().isBlank()) {
                        hasCurrentPeriodReport = true;
                        break;
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

            // 检查当前期次评价状态
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
                // 按当前期次检查对应的心得评价
                StudentReflectionEvaluation periodEvaluation = studentReflectionEvaluationService.findByReflectionId(currentPeriodReflectionId);
                // 0分同样是已保存的人工评分，评语不是必填项。
                evaluated = periodEvaluation != null && periodEvaluation.getTotalScore() != null;
            } else {
                // 当前期次没有非草稿心得时，设为未评分
                evaluated = false;
            }
            // gradePublished从InternshipEvaluation获取（因为一键上传操作的是这个表的gradePublished字段）
            InternshipEvaluation existingEvaluation = internshipEvaluationService.findByStudentId(student.getId());
            if (existingEvaluation != null) {
                gradePublished = existingEvaluation.getGradePublished() != null && existingEvaluation.getGradePublished() == 1;
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
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    public Result getStudentEvaluationInfo(@PathVariable Long studentId,
                                          @RequestParam(required = false) String period) {
        log.info("获取学生评分详情，学生ID: {}, 周期: {}", studentId, period);
        evaluationAccessPolicy.requireStudent(studentId, false);

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
            log.warn("学生企业关联缺失：studentId={}, companyId={}", student.getId(), status.getCompanyId());
        }
        info.setCompany(companyName);

        // 获取学生所有期次的心得（使用list方法而不是findByStudentId）
        List<InternshipReflection> allReflections = internshipReflectionService.list(studentId, null, null, null);

        // 计算有效期次（如果没选择则使用当前全局期次）
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
                log.debug("scoreDetailsObj type: {}", scoreDetailsObj.getClass().getName());
                if (scoreDetailsObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> scoreDetails = (Map<String, Object>) scoreDetailsObj;
                    log.debug("scoreDetails 已加载：dimensionCount={}", scoreDetails.size());
                    // 计算各维度得分（取等级中的最高分）
                    calculateDimensionScores(scoreDetails, scores);
                }
            } else {
                log.info("aiAnalysis.getScoreDetails() is null");
            }
            log.debug("AI 建议评分已计算：dimensionCount={}", scores.size());
            suggestedScores.setScores(scores);

            // 保持向后兼容
            suggestedScores.setAttitude(scores.getOrDefault("attitude", 0));
            suggestedScores.setPerformance(scores.getOrDefault("performance", 0));
            suggestedScores.setReport(scores.getOrDefault("report", 0));

            aiInfo.setSuggestedScores(suggestedScores);

            info.setAiAnalysisData(aiInfo);
        }

        // 获取当前期次心得的评价
        StudentReflectionEvaluation periodEval = null;
        if (currentPeriodReflectionId != null) {
            periodEval = studentReflectionEvaluationService.findByReflectionId(currentPeriodReflectionId);
        }
        // 0分同样是已保存的人工评分，评语不是必填项。
        boolean evaluated = periodEval != null && periodEval.getTotalScore() != null;
        // gradePublished从InternshipEvaluation获取（因为一键上传操作的是这个表的gradePublished字段）
        boolean gradePublished = false;
        InternshipEvaluation existingEvaluation = internshipEvaluationService.findByStudentId(studentId);
        if (existingEvaluation != null) {
            gradePublished = existingEvaluation.getGradePublished() != null && existingEvaluation.getGradePublished() == 1;
        }
        info.setIsEvaluated(evaluated);
        info.setGradePublished(gradePublished);

        if (periodEval != null) {
            EvaluationInfo evalInfo = new EvaluationInfo();

            // 使用StudentReflectionEvaluation的评分详情
            if (periodEval.getScoreDetails() != null) {
                Map<String, Integer> scoresInt = new HashMap<>();
                for (Map.Entry<String, Object> entry : periodEval.getScoreDetails().entrySet()) {
                    if (entry.getValue() instanceof Number) {
                        scoresInt.put(entry.getKey(), ((Number) entry.getValue()).intValue());
                    }
                }
                evalInfo.setScores(scoresInt);
            }

            // 保持向后兼容
            evalInfo.setComment(periodEval.getTeacherComment());
            evalInfo.setTotalScore(periodEval.getTotalScore() != null ? periodEval.getTotalScore().intValue() : null);
            evalInfo.setGrade(periodEval.getGrade());
            info.setEvaluation(evalInfo);
        }

        return Result.success(info);
    }
    
    @PostMapping("/submit")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "CREATE", module = "COUNSELOR_EVALUATION", description = "提交学生评价", riskLevel = "MEDIUM")
    public Result submitEvaluation(@RequestBody Map<String, Object> params) {
        log.info("收到学生评价提交请求");
        
        Object studentIdObj = params.get("studentId");
        Object commentObj = params.get("comment");
        if (studentIdObj == null || commentObj == null) {
            return Result.error("缺少必要参数");
        }
        Long studentId = Long.valueOf(studentIdObj.toString());
        String comment = commentObj.toString();

        evaluationAccessPolicy.requireStudent(studentId, false);
        
        Long teacherId = CurrentHolder.getUserId();
        if (teacherId == null) {
            return Result.error("无法获取当前教师信息");
        }

        // Lock the actual report before any grade write, using the same order as AI completion.
        Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
        List<InternshipReflection> reflections = internshipReflectionService.list(studentId, null, null, null);
        Long currentReflectionId = null;
        if (reflections != null && currentPeriod != null) {
            for (InternshipReflection reflection : reflections) {
                if (currentPeriod.equals(reflection.getPeriodNumber())) {
                    InternshipReflection current = reflectionMapper.lockById(reflection.getId());
                    if (current == null || !studentId.equals(current.getStudentId())
                            || Integer.valueOf(1).equals(current.getDeleted()) || "0".equals(current.getRemark())
                            || current.getSubmitTime() == null || current.getContent() == null
                            || current.getContent().isBlank()) {
                        return Result.error("实习心得尚未提交或已撤回，请刷新后重新评分");
                    }
                    currentReflectionId = current.getId();
                    break;
                }
            }
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
                    log.warn("评分项 {} 的值不是有效数字", key);
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

            // 注意：不再在此处设置实习心得remark为"已批阅"
            // remark的设置应该在"一键上传学生成绩"时统一处理

            // 同时保存到StudentReflectionEvaluation（每篇心得一条评价）
            try {
                if (currentReflectionId != null) {
                    // 转换scoreDetails为适合StudentReflectionEvaluation的格式
                    Map<String, Object> reflectionScoreDetails = new HashMap<>();
                    for (Map.Entry<String, Object> entry : scoreDetails.entrySet()) {
                        if (entry.getValue() instanceof Integer) {
                            reflectionScoreDetails.put(entry.getKey(), entry.getValue());
                        }
                    }

                    // 创建或更新StudentReflectionEvaluation
                    StudentReflectionEvaluation reflectionEval = studentReflectionEvaluationService.findByReflectionId(currentReflectionId);
                    boolean firstEvaluation = reflectionEval == null;
                    if (reflectionEval == null) {
                        reflectionEval = new StudentReflectionEvaluation();
                        reflectionEval.setReflectionId(currentReflectionId);
                        reflectionEval.setStudentId(studentId);
                        reflectionEval.setCounselorId(teacherId);
                    }
                    reflectionEval.setScoreDetails(reflectionScoreDetails);
                    reflectionEval.setTotalScore(BigDecimal.valueOf(totalScore));
                    reflectionEval.setGrade(grade);
                    reflectionEval.setTeacherComment(comment);
                    reflectionEval.setEvaluateTime(new Date());
                    reflectionEval.setUpdateTime(new Date());

                    studentReflectionEvaluationService.saveEvaluation(reflectionEval);
                    if (firstEvaluation) {
                        InternshipReflection currentReflection = internshipReflectionService.findById(currentReflectionId);
                        StringBuilder description = new StringBuilder();
                        description.append("第").append(currentPeriod).append("期实习心得 · ")
                                .append(totalScore).append("分");
                        append(progressRecordService,
                                studentId,
                                "reflection_scored",
                                "实习心得已评分",
                                description.toString(),
                                "success",
                                currentReflectionId,
                                reflectionEval.getEvaluateTime());
                    }
                    log.info("保存学生心得评价，reflectionId: {}, 总分: {}", currentReflectionId, totalScore);
                } else {
                    log.warn("未找到学生ID: {} 当前期次: {} 的实习心得，跳过保存StudentReflectionEvaluation", studentId, currentPeriod);
                }
            } catch (Exception e) {
                log.error("保存StudentReflectionEvaluation失败: {}", e.getMessage(), e);
                throw new BusinessException("保存实习心得评分失败: " + e.getMessage());
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
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(operationType = "IMPORT", module = "COUNSELOR_EVALUATION", description = "批量上传学生成绩", riskLevel = "MEDIUM")
    public Result batchUploadGrades(@RequestBody Map<String, Object> params) {
        log.info("收到批量上传学生成绩请求");

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

            // Validate the entire batch before publishing any grade or notification.
            for (Long studentId : studentIds) {
                evaluationAccessPolicy.requireStudent(studentId, false);
            }

            // 调用批量发布成绩方法
            try {
                int publishedCount = internshipEvaluationService.publishGrades(studentIds);

                // 同时更新已发布成绩的学生的实习心得remark为"1"（已批阅）
                try {
                    Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
                    for (Long studentId : studentIds) {
                        List<InternshipReflection> reflections = internshipReflectionService.list(studentId, null, null, null);
                        if (reflections != null && currentPeriod != null) {
                            for (InternshipReflection r : reflections) {
                                if (currentPeriod.equals(r.getPeriodNumber())) {
                                    r.setRemark("1");
                                    r.setUpdateTime(new Date());
                                    internshipReflectionService.updateById(r);
                                    log.info("批量上传成绩时更新实习心得remark为已批阅，学生ID: {}, 期次: {}", studentId, currentPeriod);
                                    break;
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("批量上传成绩时更新实习心得remark失败: {}", e.getMessage(), e);
                }

                Map<String, Object> result = Map.of(
                        "success", publishedCount,
                        "fail", studentIds.size() - publishedCount,
                        "total", studentIds.size()
                );

                // 推送成绩发布更新给学生（让他们知道评分已发布）
                for (Long studentId : studentIds) {
                    pushGradePublishedToStudent(studentId);
                }

                return Result.success("成绩上传完成，成功 " + publishedCount + " 条，失败 " + (studentIds.size() - publishedCount) + " 条", result);
            } catch (Exception e) {
                log.error("批量发布成绩失败: {}", e.getMessage(), e);
                return Result.error("批量上传成绩失败：" + e.getMessage());
            }
        } catch (org.springframework.security.access.AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("批量上传成绩失败: {}", e.getMessage(), e);
            return Result.error("批量上传成绩失败：" + e.getMessage());
        }
    }

    /**
     * 推送成绩发布更新给学生
     * @param studentId 学生ID
     */
    private void pushGradePublishedToStudent(Long studentId) {
        try {
            webSocketHandler.sendGradePublishedToStudent(studentId);
            log.info("推送成绩发布更新成功：学生ID={}", studentId);
        } catch (Exception e) {
            log.error("推送成绩发布更新失败：studentId={}, error={}", studentId, e.getMessage());
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
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    public Result getEvaluationStatistics() {
        log.info("获取评分统计信息");

        Set<Long> readableStudentIds = evaluationAccessPolicy.readableStudentIds();

        List<InternshipEvaluation> allEvaluations = internshipEvaluationService.findAll();
        Map<String, Long> gradeStats;
        if (readableStudentIds != null) {
            gradeStats = new HashMap<>();
            for (InternshipEvaluation e : allEvaluations) {
                if (e.getStudentId() != null && readableStudentIds.contains(e.getStudentId())) {
                    String grade = e.getGrade() != null ? e.getGrade() : "未评级";
                    gradeStats.merge(grade, 1L, Long::sum);
                }
            }
        } else {
            gradeStats = allEvaluations.stream()
                .collect(Collectors.groupingBy(InternshipEvaluation::getGrade, Collectors.counting()));
        }

        long total = gradeStats.values().stream().mapToLong(Long::longValue).sum();
        Map<String, Object> statistics = Map.of(
                "total", total,
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
            com.internship.entity.Class clazz = classService.findById(classId);
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
            com.internship.entity.Major major = majorService.findById(majorId);
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
            com.internship.entity.Major major = majorService.findById(majorId);
            if (major != null && major.getDepartmentId() != null) {
                com.internship.entity.Department department = departmentService.findById(major.getDepartmentId());
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

        log.debug("最终评分计算完成：dimensionCount={}", scores.size());
    }
}
