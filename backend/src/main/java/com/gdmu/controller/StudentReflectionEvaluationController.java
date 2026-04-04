package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentReflectionEvaluation;
import com.gdmu.service.StudentReflectionEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/reflection/evaluation")
public class StudentReflectionEvaluationController {
    
    @Autowired
    private StudentReflectionEvaluationService evaluationService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "STUDENT_REFLECTION_EVALUATION", operationType = "INSERT", description = "保存学生实习心得评分")
    public Result saveEvaluation(@RequestBody StudentReflectionEvaluation evaluation) {
        log.info("保存学生实习心得评分: {}", evaluation);
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
            return Result.success(evaluation);
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
        try {
            StudentReflectionEvaluation evaluation = evaluationService.findByReflectionId(reflectionId);
            if (evaluation == null) {
                return Result.success(null);
            }
            return Result.success(evaluation);
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
        try {
            StudentReflectionEvaluation evaluation = evaluationService.findByStudentId(studentId);
            if (evaluation == null) {
                return Result.success(null);
            }
            return Result.success(evaluation);
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
            return Result.success(evaluations);
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
        log.info("计算总分和等级: {}", request);
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
