package com.gdmu.controller;

import com.gdmu.entity.InternshipConfirmationRecord;
import com.gdmu.entity.InternshipEvaluation;
import com.gdmu.entity.InternshipProgressRecord;
import com.gdmu.entity.InternshipReflection;
import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.entity.Result;
import com.gdmu.entity.User;
import com.gdmu.entity.StudentReflectionAIAnalysis;
import com.gdmu.entity.StudentReflectionEvaluation;
import com.gdmu.service.ClassCounselorRelationService;
import com.gdmu.service.CounselorAISettingsService;
import com.gdmu.service.InternshipConfirmationRecordService;
import com.gdmu.service.InternshipEvaluationService;
import com.gdmu.service.InternshipProgressRecordService;
import com.gdmu.service.InternshipReflectionService;
import com.gdmu.service.InternshipTimeSettingsService;
import com.gdmu.service.StudentReflectionAIAnalysisService;
import com.gdmu.service.StudentReflectionEvaluationService;
import com.gdmu.service.UserService;
import com.gdmu.utils.FileParserUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping("/api/student/internship-reflection")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInternshipReflectionController {

    @Autowired
    private UserService userService;

    @Autowired
    private InternshipReflectionService internshipReflectionService;

    @Autowired
    private InternshipProgressRecordService progressRecordService;

    @Autowired
    private ClassCounselorRelationService classCounselorRelationService;

    @Autowired
    private InternshipEvaluationService internshipEvaluationService;

    @Autowired
    private CounselorAISettingsService counselorAISettingsService;

    @Autowired
    private StudentReflectionAIAnalysisService studentReflectionAIAnalysisService;

    @Autowired
    private StudentReflectionEvaluationService studentReflectionEvaluationService;

    @Autowired
    private InternshipConfirmationRecordService confirmationRecordService;

    @Autowired
    private InternshipTimeSettingsService timeSettingsService;

    @Autowired
    private FileParserUtil fileParserUtil;

    @Autowired
    private com.gdmu.task.ReflectionTaskGenerator reflectionTaskGenerator;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User)) {
            return null;
        }
        String username = ((org.springframework.security.core.userdetails.User) auth.getPrincipal()).getUsername();
        return userService.findByUsername(username);
    }

    /**
     * 校验学生是否可以提交实习心得
     * 条件：1. 实习确认表已被企业确认（status=1）
     *       2. 当前时间在实习时间范围内
     */
    private Result validateCanSubmitReflection(User user) {
        // 1. 检查实习确认表是否已被企业确认
        List<InternshipConfirmationRecord> confirmedRecords = confirmationRecordService.findByStudentIdAndStatus(user.getId(), 1);
        if (confirmedRecords == null || confirmedRecords.isEmpty()) {
            return Result.error("您尚未完成实习确认，无法提交实习心得");
        }

        // 2. 检查当前时间是否在实习时间范围内
        InternshipTimeSettings timeSettings = timeSettingsService.findLatest();
        if (timeSettings == null) {
            return Result.error("实习时间设置不存在，无法提交实习心得");
        }

        Date now = new Date();
        // 检查是否到了实习开始时间
        if (timeSettings.getStartDate() != null && !timeSettings.getStartDate().isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                Date startDate = sdf.parse(timeSettings.getStartDate());
                if (now.before(startDate)) {
                    return Result.error("实习尚未开始，无法提交实习心得");
                }
            } catch (Exception e) {
                log.error("解析实习开始时间失败: {}", e.getMessage());
            }
        }

        // 检查是否超过了实习结束时间
        if (timeSettings.getEndDate() != null && !timeSettings.getEndDate().isEmpty()) {
            try {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                Date endDate = sdf.parse(timeSettings.getEndDate());
                // 设置为当天结束
                Calendar cal = Calendar.getInstance();
                cal.setTime(endDate);
                cal.set(Calendar.HOUR_OF_DAY, 23);
                cal.set(Calendar.MINUTE, 59);
                cal.set(Calendar.SECOND, 59);
                if (now.after(cal.getTime())) {
                    return Result.error("实习已结束，无法提交实习心得");
                }
            } catch (Exception e) {
                log.error("解析实习结束时间失败: {}", e.getMessage());
            }
        }

        return null; // 校验通过
    }

    /**
     * 上传Word文档并提交实习心得（自动触发AI分析）
     * @param file 上传的文件
     * @param taskId 可选的自动生成任务ID，如果有则更新该任务
     */
    @PostMapping("/upload")
    public Result uploadReflection(@RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "taskId", required = false) Long taskId) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            // 校验是否可以提交实习心得
            Result validateResult = validateCanSubmitReflection(user);
            if (validateResult != null) {
                return validateResult;
            }

            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || !fileParserUtil.isSupportedFile(originalFilename)) {
                return Result.error("不支持的文件格式，仅支持 .doc, .docx, .txt 格式");
            }

            long fileSize = file.getSize();
            if (fileSize > 10 * 1024 * 1024) {
                return Result.error("文件大小不能超过10MB");
            }

            String content = fileParserUtil.parseFile(file);
            if (content == null || content.trim().isEmpty()) {
                return Result.error("文件内容为空");
            }

            // 获取辅导员ID
            Long counselorId = classCounselorRelationService.findCounselorIdByStudentId(user.getId());

            InternshipReflection reflection;
            if (taskId != null) {
                // 更新已有的任务 - 检查阶段是否过期且状态为草稿
                reflection = internshipReflectionService.findById(taskId);
                if (reflection == null) {
                    return Result.error("任务不存在");
                }
                if (!reflection.getStudentId().equals(user.getId())) {
                    return Result.error("无权操作此任务");
                }
                // 检查状态：只有草稿状态(status=0/待分析)才能重新提交
                if (reflection.getStatus() != null && reflection.getStatus() != 0) {
                    return Result.error("该心得已进入批阅流程，无法修改提交");
                }
                // 检查阶段是否已过期（当前阶段大于心得所属阶段）
                Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
                if (currentPeriod != null && reflection.getPeriodNumber() != null && reflection.getPeriodNumber() < currentPeriod) {
                    return Result.error("第" + reflection.getPeriodNumber() + "期已过期，无法修改提交");
                }
                reflection.setContent(content);
                reflection.setStatus(0); // 待分析
                reflection.setRemark(null); // 清除草稿状态
                internshipReflectionService.updateById(reflection);
            } else {
                // 创建新的实习心得 - 检查是否已在当前阶段提交
                Integer periodNumber = internshipReflectionService.calculatePeriodNumber(new Date());
                if (periodNumber != null && internshipReflectionService.existsByStudentIdAndPeriodNumber(user.getId(), periodNumber)) {
                    return Result.error("第" + periodNumber + "期实习心得已提交，请等待下一阶段再提交");
                }
                reflection = new InternshipReflection();
                reflection.setStudentId(user.getId());
                reflection.setStudentName(user.getName());
                reflection.setStudentUserId(user.getUsername());
                reflection.setContent(content);
                reflection.setCounselorId(counselorId);
                reflection.setTaskType("manual");
                reflection.setSubmitTime(new Date());
                reflection.setStatus(0); // 待分析状态
                reflection.setCreateTime(new Date());
                reflection.setUpdateTime(new Date());
                reflection.setDeleted(0);
                // 计算并设置阶段编号
                reflection.setPeriodNumber(periodNumber);
                log.info("上传实习心得，学生ID={}, 计算阶段={}", user.getId(), periodNumber);
                internshipReflectionService.insert(reflection);

                // 同步写入进度记录
                InternshipProgressRecord record = new InternshipProgressRecord();
                record.setStudentId(user.getId());
                record.setEventType("reflection_submit");
                record.setEventTitle("提交实习心得");
                record.setDescription("第" + periodNumber + "期实习心得");
                record.setStatus("submitted");
                record.setRelatedId(reflection.getId());
                record.setEventTime(new Date());
                progressRecordService.saveRecord(record);
            }

            // 异步触发AI分析
            final Long reflectionId = reflection.getId();
            final Long finalCounselorId = counselorId;
            triggerAIAnalysisForReflection(reflectionId, user.getId(), finalCounselorId);

            // 如果是自动生成的任务提交后，生成下一次的任务
            if ("auto".equals(reflection.getTaskType()) && finalCounselorId != null) {
                reflectionTaskGenerator.generateNextTask(user.getId(), finalCounselorId);
            }

            return Result.success("上传并提交成功", reflection);
        } catch (IllegalArgumentException e) {
            log.error("文件格式错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("上传实习心得失败: {}", e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 提交文本格式实习心得（自动触发AI分析）
     * @param request 包含 content 和可选的 taskId
     */
    @PostMapping("/submit")
    public Result submitReflection(@RequestBody Map<String, Object> request) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            // 校验是否可以提交实习心得
            Result validateResult = validateCanSubmitReflection(user);
            if (validateResult != null) {
                return validateResult;
            }

            String content = (String) request.get("content");
            if (content == null || content.trim().isEmpty()) {
                return Result.error("实习心得内容不能为空");
            }

            Long taskId = request.get("taskId") != null ? ((Number) request.get("taskId")).longValue() : null;

            // 获取辅导员ID
            Long counselorId = classCounselorRelationService.findCounselorIdByStudentId(user.getId());

            InternshipReflection reflection;
            if (taskId != null) {
                // 更新已有的任务 - 检查阶段是否过期且状态为草稿
                reflection = internshipReflectionService.findById(taskId);
                if (reflection == null) {
                    return Result.error("任务不存在");
                }
                if (!reflection.getStudentId().equals(user.getId())) {
                    return Result.error("无权操作此任务");
                }
                // 检查状态：只有草稿状态(status=0/待分析)才能重新提交
                if (reflection.getStatus() != null && reflection.getStatus() != 0) {
                    return Result.error("该心得已进入批阅流程，无法修改提交");
                }
                // 检查阶段是否已过期（当前阶段大于心得所属阶段）
                Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
                if (currentPeriod != null && reflection.getPeriodNumber() != null && reflection.getPeriodNumber() < currentPeriod) {
                    return Result.error("第" + reflection.getPeriodNumber() + "期已过期，无法修改提交");
                }
                reflection.setContent(content);
                reflection.setStatus(0); // 待分析
                reflection.setRemark(null); // 清除草稿状态
                internshipReflectionService.updateById(reflection);
            } else {
                // 创建新的实习心得 - 检查是否已在当前阶段提交
                Integer periodNumber = internshipReflectionService.calculatePeriodNumber(new Date());
                if (periodNumber != null && internshipReflectionService.existsByStudentIdAndPeriodNumber(user.getId(), periodNumber)) {
                    return Result.error("第" + periodNumber + "期实习心得已提交，请等待下一阶段再提交");
                }
                reflection = new InternshipReflection();
                reflection.setStudentId(user.getId());
                reflection.setStudentName(user.getName());
                reflection.setStudentUserId(user.getUsername());
                reflection.setContent(content);
                reflection.setCounselorId(counselorId);
                reflection.setTaskType("manual");
                reflection.setSubmitTime(new Date());
                reflection.setStatus(0); // 待分析状态
                reflection.setCreateTime(new Date());
                reflection.setUpdateTime(new Date());
                reflection.setDeleted(0);
                // 计算并设置阶段编号
                reflection.setPeriodNumber(periodNumber);
                log.info("提交实习心得，学生ID={}, 计算阶段={}", user.getId(), periodNumber);
                internshipReflectionService.insert(reflection);

                // 同步写入进度记录
                InternshipProgressRecord record = new InternshipProgressRecord();
                record.setStudentId(user.getId());
                record.setEventType("reflection_submit");
                record.setEventTitle("提交实习心得");
                record.setDescription("第" + periodNumber + "期实习心得");
                record.setStatus("submitted");
                record.setRelatedId(reflection.getId());
                record.setEventTime(new Date());
                progressRecordService.saveRecord(record);
            }

            // 异步触发AI分析
            final Long reflectionId = reflection.getId();
            final Long finalCounselorId = counselorId;
            triggerAIAnalysisForReflection(reflectionId, user.getId(), finalCounselorId);

            // 如果是自动生成的任务提交后，生成下一次的任务
            if ("auto".equals(reflection.getTaskType()) && finalCounselorId != null) {
                reflectionTaskGenerator.generateNextTask(user.getId(), finalCounselorId);
            }

            return Result.success("提交成功", reflection);
        } catch (Exception e) {
            log.error("提交实习心得失败: {}", e.getMessage(), e);
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生自己的实习心得列表
     */
    @GetMapping("/list")
    public Result getMyReflections() {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            List<InternshipReflection> reflections = internshipReflectionService.list(user.getId(), null, null, null);

            // 转换为前端期望的格式
            List<Map<String, Object>> resultList = new java.util.ArrayList<>();
            for (InternshipReflection reflection : reflections) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", reflection.getId());
                item.put("content", reflection.getContent());
                item.put("submitTime", reflection.getSubmitTime());
                item.put("type", "text"); // 默认文本类型

                // 将数字status转换为前端期望的字符串格式
                // 0 = submitted, 1 = reviewing, 2 = approved, 3 = rejected
                String statusStr = "submitted";
                if (reflection.getStatus() != null) {
                    if ("1".equals(reflection.getRemark())) {
                        statusStr = "approved";
                    } else if ("0".equals(reflection.getRemark())) {
                        statusStr = "draft";
                    } else {
                        switch (reflection.getStatus()) {
                            case 0: statusStr = "submitted"; break;
                            case 1: statusStr = "reviewing"; break;
                            case 2: statusStr = "approved"; break;
                            case 3: statusStr = "rejected"; break;
                            default: statusStr = "submitted"; break;
                        }
                    }
                }
                item.put("status", statusStr);

                // 每个心得使用自己独立的AI分析评语和分数
                if (reflection.getAiScore() != null) {
                    item.put("aiScore", reflection.getAiScore());
                    item.put("totalScore", reflection.getAiScore().intValue());
                }
                if (reflection.getAiAnalysis() != null) {
                    item.put("aiAnalysis", reflection.getAiAnalysis());
                }

                // 从StudentReflectionEvaluation表获取教师评语
                try {
                    StudentReflectionEvaluation reflectionEval = studentReflectionEvaluationService.findByReflectionId(reflection.getId());
                    if (reflectionEval != null) {
                        if (reflectionEval.getTeacherComment() != null) {
                            item.put("teacherComment", reflectionEval.getTeacherComment());
                        }
                        // 如果AI分数为空，用教师评分
                        if (reflection.getAiScore() == null && reflectionEval.getTotalScore() != null) {
                            item.put("totalScore", reflectionEval.getTotalScore());
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取实习心得评价失败: {}", e.getMessage());
                }

                // 如果AI分析和教师评语都没有，尝试从InternshipEvaluation获取评语
                if (item.get("aiAnalysis") == null && item.get("teacherComment") == null) {
                    InternshipEvaluation evaluation = internshipEvaluationService.findByStudentId(reflection.getStudentId());
                    if (evaluation != null && evaluation.getComment() != null) {
                        item.put("aiAnalysis", evaluation.getComment());
                    }
                }

                // 如果AI分数和教师评分都没有，尝试获取总评成绩作为备用
                if (item.get("totalScore") == null) {
                    InternshipEvaluation evaluation = internshipEvaluationService.findByStudentId(reflection.getStudentId());
                    if (evaluation != null && evaluation.getTotalScore() != null) {
                        item.put("totalScore", evaluation.getTotalScore());
                    }
                }

                // 添加期数信息，用于前端显示"第X期实习心得"
                item.put("periodNumber", reflection.getPeriodNumber());

                // 返回remark字段，供前端判断撤回状态
                item.put("remark", reflection.getRemark());

                resultList.add(item);
            }

            return Result.success(resultList);
        } catch (Exception e) {
            log.error("获取实习心得列表失败: {}", e.getMessage(), e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前阶段提交状态（用于前端判断是否可以提交）
     * 返回当前阶段号和是否已提交
     * 注意：阶段（第几期）统一按系室设置的实习时间来计算
     * 提交条件：1. 实习确认表已被企业确认（status=1）
     *          2. 当前时间在实习时间范围内
     */
    @GetMapping("/current-period-status")
    public Result getCurrentPeriodStatus() {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            Map<String, Object> result = new HashMap<>();
            String submitReason = null;
            boolean canSubmit = false;

            // 1. 检查实习确认表是否已被企业确认
            List<InternshipConfirmationRecord> confirmedRecords = confirmationRecordService.findByStudentIdAndStatus(user.getId(), 1);
            if (confirmedRecords == null || confirmedRecords.isEmpty()) {
                submitReason = "您尚未完成实习确认，无法提交实习心得";
                result.put("currentPeriod", null);
                result.put("hasSubmitted", false);
                result.put("canSubmit", false);
                result.put("submitReason", submitReason);
                return Result.success(result);
            }

            // 2. 检查当前时间是否在实习时间范围内
            InternshipTimeSettings timeSettings = timeSettingsService.findLatest();
            if (timeSettings == null) {
                submitReason = "实习时间设置不存在，无法提交实习心得";
                result.put("currentPeriod", null);
                result.put("hasSubmitted", false);
                result.put("canSubmit", false);
                result.put("submitReason", submitReason);
                return Result.success(result);
            }

            Date now = new Date();
            // 检查是否到了实习开始时间
            if (timeSettings.getStartDate() != null && !timeSettings.getStartDate().isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    Date startDate = sdf.parse(timeSettings.getStartDate());
                    if (now.before(startDate)) {
                        submitReason = "实习尚未开始，无法提交实习心得";
                        result.put("currentPeriod", null);
                        result.put("hasSubmitted", false);
                        result.put("canSubmit", false);
                        result.put("submitReason", submitReason);
                        return Result.success(result);
                    }
                } catch (Exception e) {
                    log.error("解析实习开始时间失败: {}", e.getMessage());
                }
            }

            // 检查是否超过了实习结束时间
            if (timeSettings.getEndDate() != null && !timeSettings.getEndDate().isEmpty()) {
                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                    Date endDate = sdf.parse(timeSettings.getEndDate());
                    // 设置为当天结束
                    Calendar cal = Calendar.getInstance();
                    cal.setTime(endDate);
                    cal.set(Calendar.HOUR_OF_DAY, 23);
                    cal.set(Calendar.MINUTE, 59);
                    cal.set(Calendar.SECOND, 59);
                    if (now.after(cal.getTime())) {
                        submitReason = "实习已结束，无法提交实习心得";
                        result.put("currentPeriod", null);
                        result.put("hasSubmitted", false);
                        result.put("canSubmit", false);
                        result.put("submitReason", submitReason);
                        return Result.success(result);
                    }
                } catch (Exception e) {
                    log.error("解析实习结束时间失败: {}", e.getMessage());
                }
            }

            // 3. 阶段（第几期）统一按系室设置的实习时间来计算
            Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
            boolean hasSubmitted = false;
            if (currentPeriod != null) {
                hasSubmitted = internshipReflectionService.existsByStudentIdAndPeriodNumber(user.getId(), currentPeriod);
            }

            result.put("currentPeriod", currentPeriod);
            result.put("hasSubmitted", hasSubmitted);
            result.put("canSubmit", currentPeriod != null && !hasSubmitted);
            if (!result.get("canSubmit").equals(true) && hasSubmitted) {
                result.put("submitReason", "第" + currentPeriod + "期实习心得已提交，请等待下一阶段再提交");
            } else if (currentPeriod == null) {
                result.put("submitReason", "当前不在实习期间内，无法提交实习心得");
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取当前阶段状态失败: {}", e.getMessage(), e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 撤回实习心得（只能撤回待批阅状态的）
     * @param id 实习心得ID
     */
    @PutMapping("/withdraw/{id}")
    public Result withdrawReflection(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            InternshipReflection reflection = internshipReflectionService.findById(id);
            if (reflection == null) {
                return Result.error("实习心得不存在");
            }

            // 校验权限
            if (!reflection.getStudentId().equals(user.getId())) {
                return Result.error("无权操作此心得");
            }

            // 校验状态：已批阅不允许撤回
            if ("1".equals(reflection.getRemark())) {
                return Result.error("已批阅的心得不允许撤回");
            }

            // 重置为草稿状态
            reflection.setStatus(0);
            reflection.setRemark("0");  // 重置remark，前端根据remark判断显示状态
            reflection.setUpdateTime(new Date());
            // 清空AI分析相关字段
            reflection.setAiKeywords(null);
            reflection.setAiAnalysis(null);
            reflection.setAiImprovements(null);
            reflection.setAiScore(null);
            reflection.setAiAnalysisTime(null);
            internshipReflectionService.updateById(reflection);
            // 删除AI分析记录
            studentReflectionAIAnalysisService.deleteByReflectionId(id);

            return Result.success("撤回成功", reflection);
        } catch (Exception e) {
            log.error("撤回实习心得失败: {}", e.getMessage(), e);
            return Result.error("撤回失败: " + e.getMessage());
        }
    }

    /**
     * 查询实习心得的AI分析状态
     */
    @GetMapping("/ai-status/{id}")
    public Result getAIAnalysisStatus(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            InternshipReflection reflection = internshipReflectionService.findById(id);
            if (reflection == null) {
                return Result.error("实习心得不存在");
            }

            // 校验权限
            if (!reflection.getStudentId().equals(user.getId())) {
                return Result.error("无权操作此心得");
            }

            // 检查是否有AI分析记录
            StudentReflectionAIAnalysis aiAnalysis = studentReflectionAIAnalysisService.findByReflectionId(id);
            boolean hasAiAnalysis = (aiAnalysis != null);

            // 判断状态：remark = "1" 表示已批阅, remark = "0" 表示草稿(可能是被退回)
            String status = "pending";
            boolean isNotReflection = false;
            if ("1".equals(reflection.getRemark())) {
                status = "reviewed";
            } else if ("0".equals(reflection.getRemark())) {
                status = "draft";
                // 如果有AI分析记录且totalScore为0，说明内容被判定为不是实习心得
                if (aiAnalysis != null && aiAnalysis.getTotalScore() != null && aiAnalysis.getTotalScore().doubleValue() == 0) {
                    isNotReflection = true;
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("hasAiAnalysis", hasAiAnalysis);
            result.put("isNotReflection", isNotReflection);
            result.put("status", status);
            result.put("remark", reflection.getRemark());

            return Result.success(result);
        } catch (Exception e) {
            log.error("查询AI分析状态失败: {}", e.getMessage(), e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 触发AI分析（异步处理）
     * 逻辑：
     * 1. 检查辅导员是否开启AI评分
     * 2. 如果开启，则调用AI分析服务
     */
    private void triggerAIAnalysisForReflection(Long reflectionId, Long studentId, Long counselorId) {
        CompletableFuture.runAsync(() -> {
            try {
                if (counselorId == null) {
                    log.info("学生ID: {} 未分配辅导员，跳过AI分析", studentId);
                    return;
                }

                // 1. 检查辅导员是否开启AI评分
                boolean isAiScoringEnabled = counselorAISettingsService.isAiScoringEnabled(counselorId);
                if (!isAiScoringEnabled) {
                    log.info("辅导员ID: {} 未开启AI评分，跳过AI分析", counselorId);
                    return;
                }

                // 2. 获取实习心得内容
                InternshipReflection reflection = internshipReflectionService.findById(reflectionId);
                if (reflection == null) {
                    log.error("实习心得ID: {} 不存在", reflectionId);
                    return;
                }

                // 3. 调用AI分析服务
                Map<String, Object> analysisResult = studentReflectionAIAnalysisService.analyzeReflectionContent(
                        reflection.getContent(), counselorId, studentId, reflectionId);

                log.info("AI分析完成，反射ID: {}，结果: {}", reflectionId, analysisResult);
            } catch (Exception e) {
                log.error("AI分析失败，反射ID: {}，错误: {}", reflectionId, e.getMessage(), e);
            }
        });
    }
}