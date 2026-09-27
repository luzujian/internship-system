package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Result;
import com.internship.entity.StudentReflectionEvaluation;
import com.internship.service.EvaluationAccessPolicy;
import com.internship.service.StudentReflectionEvaluationService;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reflection/evaluation")
public class StudentReflectionEvaluationController {
    
    @Autowired
    private StudentReflectionEvaluationService evaluationService;

    @Autowired
    private EvaluationAccessPolicy evaluationAccessPolicy;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "INSERT", description = "保存学生实习心得评分")
    public Result saveEvaluation(@RequestBody StudentReflectionEvaluation evaluation) {
        if (evaluation == null) throw new AccessDeniedException("评分对象不可用");
        Long studentId = evaluationAccessPolicy.requireReflection(evaluation.getReflectionId(),
                evaluation.getStudentId(), false);
        StudentReflectionEvaluation existing = evaluationService.findByReflectionId(evaluation.getReflectionId());
        if (existing != null) evaluationAccessPolicy.requireEvaluation(existing, false);
        Long requestedEvaluatorId = evaluation.getCounselorId() != null ? evaluation.getCounselorId()
                : existing == null ? null : existing.getCounselorId();
        evaluation.setStudentId(studentId);
        evaluation.setCounselorId(evaluationAccessPolicy.evaluatorId(requestedEvaluatorId));
        log.info("保存学生实习心得评分，心得ID: {}", evaluation.getReflectionId());
        try {
            int result = evaluationService.saveEvaluation(evaluation);
            if (result > 0) {
                return Result.success("评分成功");
            }
            return Result.error("评分失败");
        } catch (Exception e) {
            log.error("保存学生实习心得评分失败: {}", e.getMessage());
            return Result.error("评分失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "SELECT", description = "查询评分详情")
    public Result getEvaluationById(@PathVariable Long id) {
        log.info("查询评分详情，ID: {}", id);
        try {
            StudentReflectionEvaluation evaluation = evaluationService.findById(id);
            if (evaluation == null) {
                return Result.error("评分不存在");
            }
            evaluationAccessPolicy.requireEvaluation(evaluation, true);
            return Result.success(evaluation);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询评分详情失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/reflection/{reflectionId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "SELECT", description = "根据心得ID查询评分")
    public Result getEvaluationByReflectionId(@PathVariable Long reflectionId) {
        log.info("根据心得ID查询评分，心得ID: {}", reflectionId);
        evaluationAccessPolicy.requireReflection(reflectionId, null, true);
        try {
            StudentReflectionEvaluation evaluation = evaluationService.findByReflectionId(reflectionId);
            if (evaluation == null) {
                return Result.success(null);
            }
            evaluationAccessPolicy.requireEvaluation(evaluation, true);
            return Result.success(evaluation);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询评分失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "SELECT", description = "根据学生ID查询评分")
    public Result getEvaluationByStudentId(@PathVariable Long studentId) {
        log.info("根据学生ID查询评分，学生ID: {}", studentId);
        evaluationAccessPolicy.requireStudent(studentId, true);
        try {
            StudentReflectionEvaluation evaluation = evaluationService.findByStudentId(studentId);
            if (evaluation == null) {
                return Result.success(null);
            }
            evaluationAccessPolicy.requireEvaluation(evaluation, true);
            return Result.success(evaluation);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("查询评分失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @GetMapping("/counselor/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "SELECT", description = "查询辅导员的所有评分")
    public Result getEvaluationsByCounselorId(@PathVariable Long counselorId) {
        log.info("查询辅导员的所有评分，辅导员ID: {}", counselorId);
        try {
            List<StudentReflectionEvaluation> evaluations = evaluationService.findByCounselorId(counselorId);
            return Result.success(evaluationAccessPolicy.readableEvaluations(evaluations));
        } catch (Exception e) {
            log.error("查询评分失败: {}", e.getMessage());
            return Result.error("查询失败: " + e.getMessage());
        }
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "UPDATE", description = "更新学生实习心得评分")
    public Result updateEvaluation(@PathVariable Long id, @RequestBody StudentReflectionEvaluation evaluation) {
        log.info("更新学生实习心得评分，ID: {}", id);
        StudentReflectionEvaluation existing = evaluationService.findById(id);
        evaluationAccessPolicy.requireEvaluation(existing, false);
        if (evaluation == null || (evaluation.getReflectionId() != null
                && !evaluation.getReflectionId().equals(existing.getReflectionId()))
                || (evaluation.getStudentId() != null
                && !evaluation.getStudentId().equals(existing.getStudentId()))) {
            throw new AccessDeniedException("不能变更评分记录所属学生或心得");
        }
        evaluation.setReflectionId(existing.getReflectionId());
        evaluation.setStudentId(existing.getStudentId());
        Long requestedEvaluatorId = evaluation.getCounselorId() != null ? evaluation.getCounselorId()
                : existing.getCounselorId();
        evaluation.setCounselorId(evaluationAccessPolicy.evaluatorId(requestedEvaluatorId));
        try {
            evaluation.setId(id);
            int result = evaluationService.saveEvaluation(evaluation);
            if (result > 0) {
                return Result.success("更新成功");
            }
            return Result.error("更新失败");
        } catch (Exception e) {
            log.error("更新学生实习心得评分失败: {}", e.getMessage());
            return Result.error("更新失败: " + e.getMessage());
        }
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "DELETE", description = "删除评分")
    public Result deleteEvaluation(@PathVariable Long id) {
        log.info("删除评分，ID: {}", id);
        evaluationAccessPolicy.requireEvaluation(evaluationService.findById(id), false);
        try {
            int result = evaluationService.deleteById(id);
            if (result > 0) {
                return Result.success("删除成功");
            }
            return Result.error("删除失败");
        } catch (Exception e) {
            log.error("删除评分失败: {}", e.getMessage());
            return Result.error("删除失败: " + e.getMessage());
        }
    }
    
    @PostMapping("/calculate")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "SELECT", description = "计算总分和等级")
    public Result calculateTotalScore(@RequestBody Map<String, Object> request) {
        log.debug("收到总分和等级计算请求");
        if (!"ROLE_ADMIN".equals(CurrentHolder.getUserRole())) {
            Long counselorId = request == null || request.get("counselorId") == null ? null
                    : Long.valueOf(request.get("counselorId").toString());
            if (CurrentHolder.getUserId() == null || !CurrentHolder.getUserId().equals(counselorId)) {
                throw new AccessDeniedException("只能使用当前辅导员评分规则");
            }
        }
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            Map<String, Object> scoreDetails = (Map<String, Object>) request.get("scoreDetails");
            
            Map<String, Object> result = evaluationService.calculateTotalScoreAndGrade(scoreDetails, counselorId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("计算总分和等级失败: {}", e.getMessage());
            return Result.error("计算失败: " + e.getMessage());
        }
    }
}
