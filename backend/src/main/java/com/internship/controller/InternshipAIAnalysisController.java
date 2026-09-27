package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.InternshipAIAnalysis;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.InternshipAIAnalysisService;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@RestController
@RequestMapping("/api/internship/ai-analysis")
public class InternshipAIAnalysisController {
    
    @Autowired
    private InternshipAIAnalysisService internshipAIAnalysisService;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private TeacherAutomationScopeMapper teacherScopeMapper;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    @Log(operationType = "CREATE", module = "STUDENT_REFLECTION_AI_ANALYSIS", description = "新增心得AI分析", riskLevel = "LOW")
    public Result addAnalysis(@RequestBody InternshipAIAnalysis analysis) {
        requireStudentScope(analysis == null ? null : analysis.getStudentId(), false);
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
        requireStudentScope(analysis.getStudentId(), true);
        return Result.success(analysis);
    }
    
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    public Result getAnalysisByStudentId(@PathVariable Long studentId) {
        requireStudentScope(studentId, true);
        log.info("查询学生AI分析结果，学生ID: {}", studentId);
        InternshipAIAnalysis analysis = internshipAIAnalysisService.findByStudentId(studentId);
        if (analysis == null) {
            return Result.error("AI分析结果不存在");
        }
        return Result.success(analysis);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    @Log(operationType = "UPDATE", module = "STUDENT_REFLECTION_AI_ANALYSIS", description = "更新心得AI分析", riskLevel = "LOW")
    public Result updateAnalysis(@PathVariable Long id, @RequestBody InternshipAIAnalysis analysis) {
        InternshipAIAnalysis existing = internshipAIAnalysisService.findById(id);
        if (existing == null) return Result.error("AI分析结果不存在");
        requireStudentScope(existing.getStudentId(), false);
        if (analysis == null || !existing.getStudentId().equals(analysis.getStudentId())) {
            throw new AccessDeniedException("不能变更分析记录所属学生");
        }
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
    @Log(operationType = "DELETE", module = "STUDENT_REFLECTION_AI_ANALYSIS", description = "删除心得AI分析", riskLevel = "MEDIUM")
    public Result deleteAnalysis(@PathVariable Long id) {
        InternshipAIAnalysis existing = internshipAIAnalysisService.findById(id);
        if (existing == null) return Result.error("AI分析结果不存在");
        requireStudentScope(existing.getStudentId(), false);
        log.info("删除AI分析结果，ID: {}", id);
        int result = internshipAIAnalysisService.deleteById(id);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
    
    @DeleteMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    @Log(operationType = "DELETE", module = "STUDENT_REFLECTION_AI_ANALYSIS", description = "按学生删除心得AI分析", riskLevel = "MEDIUM")
    public Result deleteAnalysisByStudentId(@PathVariable Long studentId) {
        requireStudentScope(studentId, false);
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
        if (!"ROLE_ADMIN".equals(CurrentHolder.getUserRole())) {
            Set<Long> allowedIds = new HashSet<>(teacherScopeMapper.studentIds(requireTeacher()));
            analysisList = analysisList.stream()
                    .filter(item -> allowedIds.contains(item.getStudentId())).toList();
        }
        return Result.success(analysisList);
    }

    private void requireStudentScope(Long studentId, boolean allowStudentSelf) {
        if (studentId == null || studentId <= 0) throw new AccessDeniedException("学生对象不可用");
        String role = CurrentHolder.getUserRole();
        if ("ROLE_ADMIN".equals(role)) return;
        if (allowStudentSelf && "ROLE_STUDENT".equals(role)
                && studentId.equals(CurrentHolder.getUserId())) return;
        if (teacherScopeMapper.contains(requireTeacher(), studentId) <= 0) {
            throw new AccessDeniedException("无权访问该学生的分析记录");
        }
    }

    private TeacherUser requireTeacher() {
        Long id = CurrentHolder.getUserId();
        TeacherUser teacher = id == null ? null : teacherUserMapper.findById(id);
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                || teacher.getTeacherType() == null
                || !Set.of("COUNSELOR", "DEPARTMENT", "COLLEGE").contains(teacher.getTeacherType())
                || !("ROLE_TEACHER_" + teacher.getTeacherType()).equals(CurrentHolder.getUserRole())) {
            throw new AccessDeniedException("教师身份或负责范围不可用");
        }
        return teacher;
    }
}
