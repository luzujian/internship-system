package com.gdmu.controller;

import com.gdmu.entity.InternshipAIAnalysis;
import com.gdmu.entity.Result;
import com.gdmu.service.InternshipAIAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/internship/ai-analysis")
public class InternshipAIAnalysisController {
    
    @Autowired
    private InternshipAIAnalysisService internshipAIAnalysisService;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result addAnalysis(@RequestBody InternshipAIAnalysis analysis) {
        log.info("添加AI分析结果，学生ID: {}", analysis.getStudentId());
        int result = internshipAIAnalysisService.insert(analysis);
        if (result > 0) {
            return Result.success("添加成功");
        }
        return Result.error("添加失败");
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    public Result getAnalysisById(@PathVariable Long id) {
        log.info("查询AI分析结果，ID: {}", id);
        InternshipAIAnalysis analysis = internshipAIAnalysisService.findById(id);
        if (analysis == null) {
            return Result.error("AI分析结果不存在");
        }
        return Result.success(analysis);
    }
    
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    public Result getAnalysisByStudentId(@PathVariable Long studentId) {
        log.info("查询学生AI分析结果，学生ID: {}", studentId);
        InternshipAIAnalysis analysis = internshipAIAnalysisService.findByStudentId(studentId);
        if (analysis == null) {
            return Result.error("AI分析结果不存在");
        }
        return Result.success(analysis);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result updateAnalysis(@PathVariable Long id, @RequestBody InternshipAIAnalysis analysis) {
        log.info("更新AI分析结果，ID: {}", id);
        analysis.setId(id);
        int result = internshipAIAnalysisService.update(analysis);
        if (result > 0) {
            return Result.success("更新成功");
        }
        return Result.error("更新失败");
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result deleteAnalysis(@PathVariable Long id) {
        log.info("删除AI分析结果，ID: {}", id);
        int result = internshipAIAnalysisService.deleteById(id);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
    
    @DeleteMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result deleteAnalysisByStudentId(@PathVariable Long studentId) {
        log.info("删除学生AI分析结果，学生ID: {}", studentId);
        int result = internshipAIAnalysisService.deleteByStudentId(studentId);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
    
    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getAllAnalysis() {
        log.info("查询所有AI分析结果");
        List<InternshipAIAnalysis> analysisList = internshipAIAnalysisService.findAll();
        return Result.success(analysisList);
    }
}
