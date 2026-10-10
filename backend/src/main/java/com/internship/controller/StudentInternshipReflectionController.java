package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.InternshipConfirmationRecord;
import com.internship.entity.InternshipEvaluation;
import com.internship.entity.InternshipReflection;
import com.internship.entity.InternshipStageAssignment;
import com.internship.entity.Result;
import com.internship.entity.User;
import com.internship.entity.StudentReflectionAIAnalysis;
import com.internship.entity.StudentReflectionEvaluation;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.InternshipConfirmationRecordService;
import com.internship.service.InternshipEvaluationService;
import com.internship.service.InternshipProgressRecordService;
import com.internship.service.InternshipReflectionService;
import com.internship.service.InternshipStageAssignmentService;
import com.internship.service.StudentReflectionAIAnalysisService;
import com.internship.service.StudentReflectionEvaluationService;
import com.internship.service.UserService;
import com.internship.utils.FileParserUtil;
import com.internship.websocket.AnnouncementWebSocketHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.NoTransactionException;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.internship.task.ReflectionAiAnalysisDispatcher;

import static com.internship.service.InternshipProgressEvents.append;

@Slf4j
@RestController
@RequestMapping("/api/student/internship-reflection")
@PreAuthorize("hasRole('STUDENT')")
public class StudentInternshipReflectionController {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> REFLECTION_PLACEMENT_STATUSES =
            Set.of("CONFIRMED", "IN_PROGRESS", "REPLACED", "INTERRUPTED", "ENDED");

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
    private StudentReflectionAIAnalysisService studentReflectionAIAnalysisService;

    @Autowired
    private ReflectionAiAnalysisDispatcher reflectionAiDispatcher;

    @Autowired
    private StudentReflectionEvaluationService studentReflectionEvaluationService;

    @Autowired
    private InternshipConfirmationRecordService confirmationRecordService;

    @Autowired
    private InternshipStageAssignmentService stageAssignmentService;

    @Autowired
    private FileParserUtil fileParserUtil;

    @Autowired
    private com.internship.task.ReflectionTaskGenerator reflectionTaskGenerator;

    @Autowired
    private AnnouncementWebSocketHandler webSocketHandler;

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
     *       2. 当前处于全局实习日历内，且存在覆盖今天的已确认实习经历
     */
    private Result validateCanSubmitReflection(User user) {
        // 1. 检查实习确认表是否已被企业确认
        List<InternshipConfirmationRecord> confirmedRecords = confirmationRecordService.findByStudentIdAndStatus(user.getId(), 1);
        if (confirmedRecords == null || confirmedRecords.isEmpty()) {
            return Result.error("您尚未完成实习确认，无法提交实习心得");
        }

        // 2. 心得期次只按全局日历计算；岗位切换不会重置期次。
        if (internshipReflectionService.calculatePeriodNumber(new Date()) == null) {
            return Result.error("当前不在全局实习日历内，无法提交实习心得");
        }
        if (findPlacementForDate(user.getId(), LocalDate.now(BUSINESS_ZONE)) == null) {
            return Result.error("当前没有生效中的实习经历，无法提交实习心得");
        }

        return null; // 校验通过
    }

    /**
     * 上传Word文档并提交实习心得（自动触发AI分析）
     * @param file 上传的文件
     * @param taskId 可选的自动生成任务ID，如果有则更新该任务
     */
    @PostMapping("/upload")
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "UPLOAD", module = "INTERNSHIP_REFLECTION", description = "上传实习心得", riskLevel = "MEDIUM")
    public synchronized Result uploadReflection(@RequestParam("file") MultipartFile file,
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
            boolean resubmitted = false;
            if (taskId != null) {
                // 更新已有的任务 - 检查期次是否过期且状态为草稿
                reflection = internshipReflectionService.findById(taskId);
                if (reflection == null) {
                    return Result.error("任务不存在");
                }
                if (!reflection.getStudentId().equals(user.getId())) {
                    return Result.error("无权操作此任务");
                }
                if (hasActiveReflectionSubmission(user.getId(), reflection.getId())) {
                    return Result.error("该心得已提交，请勿重复操作");
                }
                // 检查状态：只有草稿状态(status=0/待分析)才能重新提交
                if (reflection.getStatus() != null && reflection.getStatus() != 0) {
                    return Result.error("该心得已进入批阅流程，无法修改提交");
                }
                // 检查期次是否已过期
                Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
                if (currentPeriod != null && reflection.getPeriodNumber() != null && reflection.getPeriodNumber() < currentPeriod) {
                    return Result.error("第" + reflection.getPeriodNumber() + "期已过期，无法修改提交");
                }
                resubmitted = "0".equals(reflection.getRemark())
                        && reflection.getContent() != null && !reflection.getContent().isBlank();
                reflection.setContent(content);
                reflection.setStatus(0); // 待分析
                reflection.setRemark(null); // 清除草稿状态
                reflection.setSubmitTime(new Date());
                reflection.setUpdateTime(new Date());
                internshipReflectionService.updateById(reflection);
            } else {
                // 创建新的实习心得 - 检查是否已在当前期次提交
                Integer periodNumber = internshipReflectionService.calculatePeriodNumber(new Date());
                if (periodNumber != null && internshipReflectionService.existsByStudentIdAndPeriodNumber(user.getId(), periodNumber)) {
                    return Result.error("第" + periodNumber + "期实习心得已提交，请等待下一期再提交");
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
                // 记录提交时实际生效的实习经历；数据库旧字段名暂保留作兼容。
                reflection.setPeriodNumber(periodNumber);
                InternshipStageAssignment currentPlacement =
                        findPlacementForDate(user.getId(), LocalDate.now(BUSINESS_ZONE));
                if (currentPlacement != null) reflection.setStageAssignmentId(currentPlacement.getId());
                log.info("上传实习心得，学生ID={}, 全局期次={}", user.getId(), periodNumber);
                internshipReflectionService.insert(reflection);
            }

            appendReflectionSubmission(user.getId(), reflection, resubmitted);

            // 异步触发AI分析，分析完成后会自动推送待评分数据给辅导员
            final Long reflectionId = reflection.getId();
            final Long finalCounselorId = counselorId;
            triggerAIAnalysisForReflection(reflectionId);

            // 如果是自动生成的任务提交后，生成下一次的任务
            if ("auto".equals(reflection.getTaskType()) && finalCounselorId != null) {
                reflectionTaskGenerator.generateNextTask(user.getId(), finalCounselorId);
            }

            return Result.success("上传并提交成功", reflection);
        } catch (IllegalArgumentException e) {
            rollbackCurrentTransaction();
            log.error("文件格式错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("上传实习心得失败: {}", e.getMessage(), e);
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 提交文本格式实习心得（自动触发AI分析）
     * @param request 包含 content 和可选的 taskId
     */
    @PostMapping("/submit")
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "CREATE", module = "INTERNSHIP_REFLECTION", description = "提交实习心得", riskLevel = "MEDIUM")
    public synchronized Result submitReflection(@RequestBody Map<String, Object> request) {
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
            boolean resubmitted = false;
            if (taskId != null) {
                // 更新已有的任务 - 检查期次是否过期且状态为草稿
                reflection = internshipReflectionService.findById(taskId);
                if (reflection == null) {
                    return Result.error("任务不存在");
                }
                if (!reflection.getStudentId().equals(user.getId())) {
                    return Result.error("无权操作此任务");
                }
                if (hasActiveReflectionSubmission(user.getId(), reflection.getId())) {
                    return Result.error("该心得已提交，请勿重复操作");
                }
                // 检查状态：只有草稿状态(status=0/待分析)才能重新提交
                if (reflection.getStatus() != null && reflection.getStatus() != 0) {
                    return Result.error("该心得已进入批阅流程，无法修改提交");
                }
                // 检查期次是否已过期
                Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
                if (currentPeriod != null && reflection.getPeriodNumber() != null && reflection.getPeriodNumber() < currentPeriod) {
                    return Result.error("第" + reflection.getPeriodNumber() + "期已过期，无法修改提交");
                }
                resubmitted = "0".equals(reflection.getRemark())
                        && reflection.getContent() != null && !reflection.getContent().isBlank();
                reflection.setContent(content);
                reflection.setStatus(0); // 待分析
                reflection.setRemark(null); // 清除草稿状态
                reflection.setSubmitTime(new Date());
                reflection.setUpdateTime(new Date());
                internshipReflectionService.updateById(reflection);
            } else {
                // 创建新的实习心得 - 检查是否已在当前期次提交
                Integer periodNumber = internshipReflectionService.calculatePeriodNumber(new Date());
                if (periodNumber != null && internshipReflectionService.existsByStudentIdAndPeriodNumber(user.getId(), periodNumber)) {
                    return Result.error("第" + periodNumber + "期实习心得已提交，请等待下一期再提交");
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
                // 记录提交时实际生效的实习经历；数据库旧字段名暂保留作兼容。
                reflection.setPeriodNumber(periodNumber);
                InternshipStageAssignment currentPlacement =
                        findPlacementForDate(user.getId(), LocalDate.now(BUSINESS_ZONE));
                if (currentPlacement != null) reflection.setStageAssignmentId(currentPlacement.getId());
                log.info("提交实习心得，学生ID={}, 全局期次={}", user.getId(), periodNumber);
                internshipReflectionService.insert(reflection);
            }

            appendReflectionSubmission(user.getId(), reflection, resubmitted);

            // 异步触发AI分析，分析完成后会自动推送待评分数据给辅导员
            final Long reflectionId = reflection.getId();
            final Long finalCounselorId = counselorId;
            triggerAIAnalysisForReflection(reflectionId);

            // 如果是自动生成的任务提交后，生成下一次的任务
            if ("auto".equals(reflection.getTaskType()) && finalCounselorId != null) {
                reflectionTaskGenerator.generateNextTask(user.getId(), finalCounselorId);
            }

            return Result.success("提交成功", reflection);
        } catch (Exception e) {
            rollbackCurrentTransaction();
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

            if (reflections.isEmpty()) {
                return Result.success(java.util.Collections.emptyList());
            }

            // 优化：批量获取评价数据，避免N+1查询
            // 1. 批量获取所有心得的教师评价
            List<Long> reflectionIds = reflections.stream()
                    .map(InternshipReflection::getId)
                    .collect(java.util.stream.Collectors.toList());
            Map<Long, StudentReflectionEvaluation> evaluationMap = new HashMap<>();
            try {
                List<StudentReflectionEvaluation> evaluations = studentReflectionEvaluationService.findByReflectionIds(reflectionIds);
                for (StudentReflectionEvaluation eval : evaluations) {
                    evaluationMap.put(eval.getReflectionId(), eval);
                }
            } catch (Exception e) {
                log.warn("批量获取评价失败: {}", e.getMessage());
            }

            // 2. 批量获取实习总评（先收集学生ID列表，再按studentId去重）
            List<Long> studentIds = reflections.stream()
                    .map(InternshipReflection::getStudentId)
                    .distinct()
                    .collect(java.util.stream.Collectors.toList());
            Map<Long, InternshipEvaluation> studentEvaluationMap = new HashMap<>();
            try {
                if (!studentIds.isEmpty()) {
                    List<InternshipEvaluation> allEvaluations = internshipEvaluationService.findByStudentIds(studentIds);
                    for (InternshipEvaluation eval : allEvaluations) {
                        if (eval.getStudentId() != null) {
                            studentEvaluationMap.put(eval.getStudentId(), eval);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("批量获取实习总评失败: {}", e.getMessage());
            }

            // 转换为前端期望的格式
            List<Map<String, Object>> resultList = new java.util.ArrayList<>();
            for (InternshipReflection reflection : reflections) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", reflection.getId());
                item.put("content", reflection.getContent());
                item.put("submitTime", reflection.getSubmitTime());
                item.put("type", "text"); // 默认文本类型

                // 将数字status转换为前端期望的字符串格式
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

                // 从缓存的Map中获取教师评语（替代循环内单独查询）
                StudentReflectionEvaluation reflectionEval = evaluationMap.get(reflection.getId());
                if (reflectionEval != null) {
                    if (reflectionEval.getTeacherComment() != null) {
                        item.put("teacherComment", reflectionEval.getTeacherComment());
                    }
                    // 如果AI分数为空，用教师评分
                    if (reflection.getAiScore() == null && reflectionEval.getTotalScore() != null) {
                        item.put("totalScore", reflectionEval.getTotalScore());
                    }
                }

                // 如果AI分析和教师评语都没有，从缓存的实习总评获取评语（仅用于展示，不显示分数）
                if (item.get("aiAnalysis") == null && item.get("teacherComment") == null) {
                    InternshipEvaluation evaluation = studentEvaluationMap.get(reflection.getStudentId());
                    if (evaluation != null && evaluation.getComment() != null) {
                        item.put("aiAnalysis", evaluation.getComment());
                    }
                }

                // 【重要】不再从实习总评(InternshipEvaluation)获取分数显示在心得卡片上
                // 实习心得的评分应该由教师单独评分得出，不应直接显示实习总评的分数
                // totalScore只从以下两个来源获取：
                // 1. reflection.getAiScore() - AI评分（如有）
                // 2. reflectionEval.getTotalScore() - 教师对该心得的评分（如有）
                // 注意：InternshipEvaluation是整个实习的总评，不是本次心得的评分，不应显示在此

                // 添加期数信息，用于前端显示"第X期实习心得"
                item.put("periodNumber", reflection.getPeriodNumber());
                item.put("placementId", reflection.getStageAssignmentId());
                // 旧响应字段保留一个兼容周期，前端新代码统一读取 placementId。
                item.put("stageAssignmentId", reflection.getStageAssignmentId());
                if (reflection.getStageAssignmentId() != null) {
                    InternshipStageAssignment assignment = stageAssignmentService.findById(reflection.getStageAssignmentId());
                    if (assignment != null) {
                        item.put("companyName", assignment.getCompanyName());
                        item.put("positionName", assignment.getPositionName());
                    }
                }

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
     * 获取当前全局期次提交状态（用于前端判断是否可以提交）
     * 返回当前期次和是否已提交；岗位切换不会重置期次。
     * 提交条件：1. 实习确认表已被企业确认（status=1）
     *          2. 当前处于全局实习日历内，且存在覆盖今天的实习经历
     */
    @GetMapping("/current-period-status")
    public Result getCurrentPeriodStatus() {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            Map<String, Object> result = new HashMap<>();
            Result validation = validateCanSubmitReflection(user);
            if (validation != null) {
                result.put("currentPeriod", null);
                result.put("hasSubmitted", false);
                result.put("canSubmit", false);
                result.put("submitReason", validation.getMessage());
                return Result.success(result);
            }

            Integer currentPeriod = internshipReflectionService.calculatePeriodNumber(new Date());
            if (currentPeriod == null) {
                result.put("currentPeriod", null);
                result.put("hasSubmitted", false);
                result.put("canSubmit", false);
                result.put("submitReason", "当前不在全局实习日历内，无法提交实习心得");
                return Result.success(result);
            }

            boolean hasSubmitted = internshipReflectionService
                    .existsByStudentIdAndPeriodNumber(user.getId(), currentPeriod);

            result.put("currentPeriod", currentPeriod);
            result.put("hasSubmitted", hasSubmitted);
            result.put("canSubmit", !hasSubmitted);
            if (hasSubmitted) {
                result.put("submitReason", "第" + currentPeriod + "期实习心得已提交，请等待下一期再提交");
            }

            return Result.success(result);
        } catch (Exception e) {
            log.error("获取当前期次状态失败: {}", e.getMessage(), e);
            return Result.error("获取失败: " + e.getMessage());
        }
    }

    /**
     * 撤回实习心得（只能撤回待批阅状态的）
     * @param id 实习心得ID
     */
    @PutMapping("/withdraw/{id}")
    @Transactional(rollbackFor = Exception.class)
    @Log(operationType = "UPDATE", module = "INTERNSHIP_REFLECTION", description = "撤回实习心得", riskLevel = "MEDIUM")
    public synchronized Result withdrawReflection(@PathVariable Long id) {
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
            if (!hasActiveReflectionSubmission(user.getId(), reflection.getId())) {
                return Result.error("该心得尚未提交或已撤回，请勿重复操作");
            }

            // 数据库条件更新是重复撤回的最终门闩，避免多实例同时写入两条撤回动态。
            Date withdrawnAt = new Date();
            if (internshipReflectionService.withdrawSubmitted(
                    reflection.getId(), user.getId(), withdrawnAt) <= 0) {
                return Result.error("该心得已撤回或状态已变化，请刷新后重试");
            }
            reflection.setStatus(0);
            reflection.setRemark("0");
            reflection.setUpdateTime(withdrawnAt);
            // 删除AI分析记录
            studentReflectionAIAnalysisService.deleteByReflectionId(id);

            append(progressRecordService,
                    user.getId(),
                    "reflection_withdrawn",
                    "已撤回实习心得",
                    reflectionDescription(reflection),
                    "cancelled",
                    reflection.getId(),
                    reflection.getUpdateTime());

            // 推送待评分数据更新给辅导员
            if (reflection.getCounselorId() != null) {
                pushTeacherTodoUpdate(reflection.getCounselorId());
            }

            return Result.success("撤回成功", reflection);
        } catch (Exception e) {
            rollbackCurrentTransaction();
            log.error("撤回实习心得失败: {}", e.getMessage(), e);
            return Result.error("撤回失败: " + e.getMessage());
        }
    }

    /**
     * 删除实习心得（只能删除草稿状态的）
     */
    @DeleteMapping("/{id}")
    @Log(operationType = "DELETE", module = "INTERNSHIP_REFLECTION", description = "删除实习心得", riskLevel = "MEDIUM")
    public Result deleteReflection(@PathVariable Long id) {
        try {
            User user = getCurrentUser();
            if (user == null) {
                return Result.error("未登录");
            }

            InternshipReflection reflection = internshipReflectionService.findById(id);
            if (reflection == null) {
                return Result.error("实习心得不存在");
            }

            if (!reflection.getStudentId().equals(user.getId())) {
                return Result.error("无权操作此心得");
            }

            // 只允许删除草稿状态
            if (!"0".equals(reflection.getRemark())) {
                return Result.error("只能删除草稿状态的心得");
            }

            reflection.setDeleted(1);
            reflection.setUpdateTime(new Date());
            internshipReflectionService.updateById(reflection);

            return Result.success("删除成功");
        } catch (Exception e) {
            log.error("删除实习心得失败: {}", e.getMessage(), e);
            return Result.error("删除失败: " + e.getMessage());
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

            int attempts = reflectionAiDispatcher.failureCount(id, user.getId());
            if ("pending".equals(status) && hasAiAnalysis) status = "analysed";
            if ("pending".equals(status) && attempts >= ReflectionAiAnalysisDispatcher.MAX_ATTEMPTS) status = "failed";

            Map<String, Object> result = new HashMap<>();
            result.put("attempts", attempts);
            result.put("retryable", "pending".equals(status) && attempts < ReflectionAiAnalysisDispatcher.MAX_ATTEMPTS);
            if ("failed".equals(status)) result.put("message", ReflectionAiAnalysisDispatcher.FAILURE_MESSAGE);
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
    private void triggerAIAnalysisForReflection(Long reflectionId) {
        reflectionAiDispatcher.dispatchAfterCommit(reflectionId);
    }

    private void appendReflectionSubmission(Long studentId,
                                            InternshipReflection reflection,
                                            boolean resubmitted) {
        append(progressRecordService,
                studentId,
                resubmitted ? "reflection_resubmitted" : "reflection_submitted",
                resubmitted ? "已重新提交实习心得" : "已提交实习心得",
                reflectionDescription(reflection),
                "pending",
                reflection.getId(),
                reflection.getSubmitTime());
    }

    private boolean hasActiveReflectionSubmission(Long studentId, Long reflectionId) {
        List<com.internship.entity.InternshipProgressRecord> events =
                progressRecordService.getByStudentId(studentId);
        if (events == null) return false;
        return events.stream()
                .filter(event -> reflectionId.equals(event.getRelatedId()))
                .filter(event -> "reflection_submitted".equals(event.getEventType())
                        || "reflection_resubmitted".equals(event.getEventType())
                        || "reflection_withdrawn".equals(event.getEventType())
                        || "reflection_submit".equals(event.getEventType()))
                .findFirst()
                .map(event -> !"reflection_withdrawn".equals(event.getEventType()))
                .orElse(false);
    }

    private String reflectionDescription(InternshipReflection reflection) {
        StringBuilder description = new StringBuilder();
        if (reflection.getStageAssignmentId() != null) {
            InternshipStageAssignment assignment = stageAssignmentService.findById(reflection.getStageAssignmentId());
            if (assignment != null && assignment.getCompanyName() != null) {
                description.append(assignment.getCompanyName());
                if (assignment.getPositionName() != null) {
                    description.append(" · ").append(assignment.getPositionName());
                }
                description.append(" · ");
            }
        }
        description.append("第")
                .append(reflection.getPeriodNumber() == null ? "?" : reflection.getPeriodNumber())
                .append("期实习心得");
        return description.toString();
    }

    private InternshipStageAssignment findPlacementForDate(Long studentId, LocalDate date) {
        List<InternshipStageAssignment> placements = stageAssignmentService.findByStudentId(studentId);
        if (placements == null || date == null) return null;
        return placements.stream()
                .filter(item -> item != null
                        && item.getInternshipStartTime() != null
                        && REFLECTION_PLACEMENT_STATUSES.contains(item.getStatus()))
                .filter(item -> {
                    LocalDate start = item.getInternshipStartTime().toInstant()
                            .atZone(BUSINESS_ZONE).toLocalDate();
                    LocalDate end = item.getInternshipEndTime() == null ? null
                            : item.getInternshipEndTime().toInstant()
                            .atZone(BUSINESS_ZONE).toLocalDate();
                    return !date.isBefore(start) && (end == null || !date.isAfter(end));
                })
                .max(Comparator
                        .comparing(InternshipStageAssignment::getInternshipStartTime)
                        .thenComparing(InternshipStageAssignment::getId,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private void rollbackCurrentTransaction() {
        try {
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
        } catch (NoTransactionException ignored) {
            // Direct controller unit tests do not run through a transactional proxy.
        }
    }

    /**
     * 推送辅导员待评分数据更新
     * @param counselorId 辅导员ID
     */
    private void pushTeacherTodoUpdate(Long counselorId) {
        try {
            // 推送待评分数据更新（具体数量由前端刷新时从后端获取）
            webSocketHandler.sendTeacherTodoUpdate(counselorId, 1L);
            log.info("推送待评分数据更新成功：辅导员={}", counselorId);
        } catch (Exception e) {
            log.error("推送待评分数据失败: {}", e.getMessage(), e);
        }
    }
}
