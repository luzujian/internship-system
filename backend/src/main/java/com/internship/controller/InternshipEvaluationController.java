package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.InternshipEvaluation;
import com.internship.entity.PageResult;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.mapper.TeacherAutomationScopeMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.InternshipEvaluationService;
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
@RequestMapping("/api/internship/evaluation")
public class InternshipEvaluationController {
    
    @Autowired
    private InternshipEvaluationService internshipEvaluationService;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private TeacherAutomationScopeMapper teacherScopeMapper;
    
    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    @Log(operationType = "CREATE", module = "STUDENT_REFLECTION_EVALUATION", description = "新增心得评分", riskLevel = "LOW")
    public Result addEvaluation(@RequestBody InternshipEvaluation evaluation) {
        requireStudentScope(evaluation == null ? null : evaluation.getStudentId(), false);
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
        requireStudentScope(evaluation.getStudentId(), true);
        requirePublishedForStudent(evaluation);
        return Result.success(evaluation);
    }
    
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN', 'STUDENT')")
    public Result getEvaluationByStudentId(@PathVariable Long studentId) {
        requireStudentScope(studentId, true);
        log.info("查询学生实习评分，学生ID: {}", studentId);
        InternshipEvaluation evaluation = internshipEvaluationService.findByStudentId(studentId);
        if (evaluation == null) {
            return Result.success(null);
        }
        requirePublishedForStudent(evaluation);
        return Result.success(evaluation);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    @Log(operationType = "UPDATE", module = "STUDENT_REFLECTION_EVALUATION", description = "更新心得评分", riskLevel = "LOW")
    public Result updateEvaluation(@PathVariable Long id, @RequestBody InternshipEvaluation evaluation) {
        InternshipEvaluation existing = internshipEvaluationService.findById(id);
        if (existing == null) return Result.error("实习评分不存在");
        requireStudentScope(existing.getStudentId(), false);
        if (evaluation == null || !existing.getStudentId().equals(evaluation.getStudentId())) {
            throw new AccessDeniedException("不能变更评分记录所属学生");
        }
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
    @Log(operationType = "DELETE", module = "STUDENT_REFLECTION_EVALUATION", description = "删除心得评分", riskLevel = "MEDIUM")
    public Result deleteEvaluation(@PathVariable Long id) {
        InternshipEvaluation existing = internshipEvaluationService.findById(id);
        if (existing == null) return Result.error("实习评分不存在");
        requireStudentScope(existing.getStudentId(), false);
        log.info("删除实习评分，ID: {}", id);
        int result = internshipEvaluationService.deleteById(id);
        if (result > 0) {
            return Result.success("删除成功");
        }
        return Result.error("删除失败");
    }
    
    @DeleteMapping("/student/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    @Log(operationType = "DELETE", module = "STUDENT_REFLECTION_EVALUATION", description = "按学生删除心得评分", riskLevel = "MEDIUM")
    public Result deleteEvaluationByStudentId(@PathVariable Long studentId) {
        requireStudentScope(studentId, false);
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
        List<InternshipEvaluation> evaluations = scopedEvaluations();
        return Result.success(evaluations);
    }
    
    @GetMapping("/evaluator/{evaluatorId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getEvaluationsByEvaluatorId(@PathVariable Long evaluatorId) {
        log.info("查询评分人的实习评分，评分人ID: {}", evaluatorId);
        List<InternshipEvaluation> evaluations = "ROLE_ADMIN".equals(CurrentHolder.getUserRole())
                ? internshipEvaluationService.findByEvaluatorId(evaluatorId)
                : scopedEvaluations().stream().filter(item -> evaluatorId.equals(item.getEvaluatorId())).toList();
        return Result.success(evaluations);
    }
    
    @GetMapping("/count/{evaluatorId}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result countEvaluationsByEvaluatorId(@PathVariable Long evaluatorId) {
        log.info("统计评分人评分数量，评分人ID: {}", evaluatorId);
        int count = "ROLE_ADMIN".equals(CurrentHolder.getUserRole())
                ? internshipEvaluationService.countByEvaluatorId(evaluatorId)
                : (int) scopedEvaluations().stream().filter(item -> evaluatorId.equals(item.getEvaluatorId())).count();
        return Result.success(count);
    }
    
    @GetMapping("/grade/{grade}")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getEvaluationsByGrade(@PathVariable String grade) {
        log.info("查询指定等级的实习评分，等级: {}", grade);
        List<InternshipEvaluation> evaluations = "ROLE_ADMIN".equals(CurrentHolder.getUserRole())
                ? internshipEvaluationService.findByGrade(grade)
                : scopedEvaluations().stream().filter(item -> grade.equals(item.getGrade())).toList();
        return Result.success(evaluations);
    }
    
    @GetMapping("/page")
    @PreAuthorize("hasAnyRole('TEACHER', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT', 'TEACHER_COUNSELOR', 'ADMIN')")
    public Result getEvaluationsByPage(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) Long evaluatorId) {
        log.info("分页查询实习评分，页码: {}, 每页条数: {}, 评分人ID: {}", page, pageSize, evaluatorId);
        PageResult<InternshipEvaluation> pageResult;
        if ("ROLE_ADMIN".equals(CurrentHolder.getUserRole())) {
            pageResult = internshipEvaluationService.findPage(page, pageSize, evaluatorId);
        } else {
            List<InternshipEvaluation> scoped = scopedEvaluations().stream()
                    .filter(item -> evaluatorId == null || evaluatorId.equals(item.getEvaluatorId())).toList();
            int current = page == null || page < 1 ? 1 : page;
            int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
            long offset = (long) (current - 1) * size;
            List<InternshipEvaluation> rows = offset >= scoped.size() ? List.of()
                    : scoped.subList((int) offset, (int) Math.min(offset + size, scoped.size()));
            int pages = (scoped.size() + size - 1) / size;
            pageResult = PageResult.build((long) scoped.size(), rows, pages, current, size);
        }
        return Result.success(pageResult);
    }

    private List<InternshipEvaluation> scopedEvaluations() {
        List<InternshipEvaluation> all = internshipEvaluationService.findAll();
        if ("ROLE_ADMIN".equals(CurrentHolder.getUserRole())) return all;
        Set<Long> allowedIds = new HashSet<>(teacherScopeMapper.studentIds(requireTeacher()));
        return all.stream().filter(item -> allowedIds.contains(item.getStudentId())).toList();
    }

    private void requirePublishedForStudent(InternshipEvaluation evaluation) {
        if ("ROLE_STUDENT".equals(CurrentHolder.getUserRole())
                && !Integer.valueOf(1).equals(evaluation.getGradePublished())) {
            throw new AccessDeniedException("成绩尚未发布");
        }
    }

    private void requireStudentScope(Long studentId, boolean allowStudentSelf) {
        if (studentId == null || studentId <= 0) throw new AccessDeniedException("学生对象不可用");
        String role = CurrentHolder.getUserRole();
        if ("ROLE_ADMIN".equals(role)) return;
        if (allowStudentSelf && "ROLE_STUDENT".equals(role)
                && studentId.equals(CurrentHolder.getUserId())) return;
        if (teacherScopeMapper.contains(requireTeacher(), studentId) <= 0) {
            throw new AccessDeniedException("无权访问该学生的评分记录");
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
