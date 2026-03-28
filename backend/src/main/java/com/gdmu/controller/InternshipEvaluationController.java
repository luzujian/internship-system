package com.gdmu.controller;

import com.gdmu.entity.InternshipEvaluation;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Result;
import com.gdmu.service.InternshipEvaluationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/internship/evaluation")
public class InternshipEvaluationController {
    
    @Autowired
    private InternshipEvaluationService internshipEvaluationService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result addEvaluation(@RequestBody InternshipEvaluation evaluation) {
        log.info("添加实习评分，学生ID: {}", evaluation.getStudentId());
        int result = internshipEvaluationService.insert(evaluation);
        if (result > 0) {
            return Result.success("评分成功");
        }
        return Result.error("评分失败");
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    public Result getEvaluationById(@PathVariable Long id) {
        log.info("查询实习评分，ID: {}", id);
        InternshipEvaluation evaluation = internshipEvaluationService.findById(id);
        if (evaluation == null) {
            return Result.error("实习评分不存在");
        }
        return Result.success(evaluation);
    }
    
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    public Result getEvaluationByStudentId(@PathVariable Long studentId) {
        log.info("查询学生实习评分，学生ID: {}", studentId);
        InternshipEvaluation evaluation = internshipEvaluationService.findByStudentId(studentId);
        if (evaluation == null) {
            return Result.success(null);
        }
        return Result.success(evaluation);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result updateEvaluation(@PathVariable Long id, @RequestBody InternshipEvaluation evaluation) {
        log.info("更新实习评分，ID: {}", id);
        evaluation.setId(id);
        int result = internshipEvaluationService.update(evaluation);
        if (result > 0) {
            return Result.success("更新成功");
        }
        return Result.error("更新失败");
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result deleteEvaluation(@PathVariable Long id) {
        log.info("删除实习评分，ID: {}", id);
        int result = internshipEvaluationService.deleteById(id);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
    
    @DeleteMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result deleteEvaluationByStudentId(@PathVariable Long studentId) {
        log.info("删除学生实习评分，学生ID: {}", studentId);
        int result = internshipEvaluationService.deleteByStudentId(studentId);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getAllEvaluations() {
        log.info("查询所有实习评分");
        List<InternshipEvaluation> evaluations = internshipEvaluationService.findAll();
        return Result.success(evaluations);
    }
    
    @GetMapping("/evaluator/{evaluatorId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getEvaluationsByEvaluatorId(@PathVariable Long evaluatorId) {
        log.info("查询评分人的实习评分，评分人ID: {}", evaluatorId);
        List<InternshipEvaluation> evaluations = internshipEvaluationService.findByEvaluatorId(evaluatorId);
        return Result.success(evaluations);
    }
    
    @GetMapping("/count/{evaluatorId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result countEvaluationsByEvaluatorId(@PathVariable Long evaluatorId) {
        log.info("统计评分人评分数量，评分人ID: {}", evaluatorId);
        int count = internshipEvaluationService.countByEvaluatorId(evaluatorId);
        return Result.success(count);
    }
    
    @GetMapping("/grade/{grade}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getEvaluationsByGrade(@PathVariable String grade) {
        log.info("查询指定等级的实习评分，等级: {}", grade);
        List<InternshipEvaluation> evaluations = internshipEvaluationService.findByGrade(grade);
        return Result.success(evaluations);
    }
    
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getEvaluationsByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long evaluatorId) {
        log.info("分页查询实习评分，页码: {}, 每页条数: {}, 评分人ID: {}", page, pageSize, evaluatorId);
        PageResult<InternshipEvaluation> pageResult = internshipEvaluationService.findPage(page, pageSize, evaluatorId);
        return Result.success(pageResult);
    }
}
