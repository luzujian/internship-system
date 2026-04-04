package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Result;
import com.gdmu.entity.StudentReflectionEvaluation;
import com.gdmu.service.StudentReflectionEvaluationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/counselor/evaluation")
public class CounselorEvaluationController {

    @Autowired
    private StudentReflectionEvaluationService studentReflectionEvaluationService;

    @GetMapping("/reflection/{reflectionId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_EVALUATION", operationType = "SELECT", description = "获取实习心得评分")
    public Result getEvaluationByReflectionId(@PathVariable Long reflectionId) {
        log.info("获取实习心得评分，心得ID: {}", reflectionId);
        try {
            StudentReflectionEvaluation evaluation = studentReflectionEvaluationService.findByReflectionId(reflectionId);
            if (evaluation == null) {
                return Result.success(null);
            }
            return Result.success(evaluation);
        } catch (Exception e) {
            log.error("获取评分失败: {}", e.getMessage());
            return Result.error("获取评分失败: " + e.getMessage());
        }
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_EVALUATION", operationType = "SELECT", description = "获取学生评分")
    public Result getEvaluationByStudentId(@PathVariable Long studentId) {
        log.info("获取学生评分，学生ID: {}", studentId);
        try {
            StudentReflectionEvaluation evaluation = studentReflectionEvaluationService.findByStudentId(studentId);
            if (evaluation == null) {
                return Result.success(null);
            }
            return Result.success(evaluation);
        } catch (Exception e) {
            log.error("获取评分失败: {}", e.getMessage());
            return Result.error("获取评分失败: " + e.getMessage());
        }
    }

    @GetMapping("/counselor/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_EVALUATION", operationType = "SELECT", description = "获取辅导员所有评分")
    public Result getEvaluationsByCounselorId(@PathVariable Long counselorId) {
        log.info("获取辅导员所有评分，辅导员ID: {}", counselorId);
        try {
            List<StudentReflectionEvaluation> evaluations = studentReflectionEvaluationService.findByCounselorId(counselorId);
            return Result.success(evaluations);
        } catch (Exception e) {
            log.error("获取评分列表失败: {}", e.getMessage());
            return Result.error("获取评分列表失败: " + e.getMessage());
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_EVALUATION", operationType = "INSERT", description = "保存评分")
    public Result saveEvaluation(@RequestBody Map<String, Object> params) {
        log.info("保存评分，参数: {}", params);
        try {
            Long reflectionId = Long.parseLong(params.get("reflectionId").toString());
            Long counselorId = Long.parseLong(params.get("counselorId").toString());
            Double totalScore = Double.parseDouble(params.get("totalScore").toString());
            String grade = params.get("grade").toString();
            String scoreDetails = params.get("scoreDetails").toString();

            StudentReflectionEvaluation evaluation = studentReflectionEvaluationService.findByReflectionId(reflectionId);
            
            if (evaluation == null) {
                evaluation = new StudentReflectionEvaluation();
                evaluation.setReflectionId(reflectionId);
                evaluation.setCounselorId(counselorId);
            }

            evaluation.setTotalScore(java.math.BigDecimal.valueOf(totalScore));
            evaluation.setGrade(grade);
            evaluation.setScoreDetails(parseScoreDetails(scoreDetails));
            evaluation.setEvaluateTime(new java.util.Date());

            int result;
            if (evaluation.getId() == null) {
                result = studentReflectionEvaluationService.insert(evaluation);
            } else {
                result = studentReflectionEvaluationService.update(evaluation);
            }

            if (result > 0) {
                return Result.success("评分保存成功", evaluation);
            }
            return Result.error("评分保存失败");
        } catch (Exception e) {
            log.error("保存评分失败: {}", e.getMessage());
            return Result.error("保存评分失败: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_EVALUATION", operationType = "UPDATE", description = "更新评分")
    public Result updateEvaluation(@PathVariable Long id, @RequestBody Map<String, Object> params) {
        log.info("更新评分，ID: {}", id);
        try {
            StudentReflectionEvaluation evaluation = studentReflectionEvaluationService.findById(id);
            if (evaluation == null) {
                return Result.error("评分记录不存在");
            }

            if (params.containsKey("totalScore")) {
                evaluation.setTotalScore(java.math.BigDecimal.valueOf(Double.parseDouble(params.get("totalScore").toString())));
            }
            if (params.containsKey("grade")) {
                evaluation.setGrade(params.get("grade").toString());
            }
            if (params.containsKey("scoreDetails")) {
                evaluation.setScoreDetails(parseScoreDetails(params.get("scoreDetails").toString()));
            }
            evaluation.setEvaluateTime(new java.util.Date());

            int result = studentReflectionEvaluationService.update(evaluation);
            if (result > 0) {
                return Result.success("评分更新成功", evaluation);
            }
            return Result.error("评分更新失败");
        } catch (Exception e) {
            log.error("更新评分失败: {}", e.getMessage());
            return Result.error("更新评分失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'ADMIN')")
    @Log(module = "COUNSELOR_EVALUATION", operationType = "DELETE", description = "删除评分")
    public Result deleteEvaluation(@PathVariable Long id) {
        log.info("删除评分，ID: {}", id);
        try {
            int result = studentReflectionEvaluationService.deleteById(id);
            if (result > 0) {
                return Result.success("评分删除成功");
            }
            return Result.error("评分删除失败");
        } catch (Exception e) {
            log.error("删除评分失败: {}", e.getMessage());
            return Result.error("评分删除失败: " + e.getMessage());
        }
    }

    private Map<String, Object> parseScoreDetails(String scoreDetailsJson) {
        try {
            if (scoreDetailsJson == null || scoreDetailsJson.isEmpty()) {
                return new HashMap<>();
            }
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readValue(scoreDetailsJson, Map.class);
        } catch (Exception e) {
            log.error("解析评分详情失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }
}
