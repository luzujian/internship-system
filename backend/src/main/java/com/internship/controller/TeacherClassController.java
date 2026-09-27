package com.internship.controller;

import com.internship.anno.Log;
import com.internship.entity.Class;
import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.Result;
import com.internship.entity.TeacherUser;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.ClassService;
import com.internship.utils.CurrentHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

@Slf4j
@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherClassController {

    @Autowired
    private ClassService classService;

    @Autowired
    private ClassCounselorRelationService classCounselorRelationService;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取教师关联的班级列表")
    public Result getTeacherClasses(@RequestParam(required = false) Long counselorId) {
        log.info("获取教师关联的班级列表，辅导员ID: {}", counselorId);
        TeacherScope scope = currentScope();
        requireCounselorTarget(scope, counselorId, false);
        try {
            List<Class> classes = classCounselorRelationService.findClassesByCounselorId(counselorId)
                    .stream().filter(scope::canView).toList();
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取教师关联班级列表失败: {}", e.getMessage());
            return Result.error("获取班级列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取所有班级列表(用于分配)")
    public Result getAllClasses() {
        log.info("获取所有班级列表");
        TeacherScope scope = currentScope();
        try {
            List<Class> classes = classService.findAllWithStudentCount().stream()
                    .filter(scope::canView).toList();
            return Result.success(classes);
        } catch (Exception e) {
            log.error("获取所有班级列表失败: {}", e.getMessage());
            return Result.error("获取班级列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/{classId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取班级详情")
    public Result getClassDetail(@PathVariable Long classId) {
        log.info("获取班级详情，班级ID: {}", classId);
        TeacherScope scope = currentScope();
        Class classEntity = requireClassScope(scope, classId);
        try {
            return Result.success(classEntity);
        } catch (Exception e) {
            log.error("获取班级详情失败: {}", e.getMessage());
            return Result.error("获取班级详情失败: " + e.getMessage());
        }
    }

    @PostMapping("/assign")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "INSERT", description = "分配班级给辅导员")
    public Result assignClassToCounselor(@RequestBody Map<String, Object> request) {
        log.info("分配班级给辅导员");
        TeacherScope scope = currentScope();
        if ("COUNSELOR".equals(scope.teacher().getTeacherType())) {
            throw new AccessDeniedException("辅导员不能自行扩大负责班级范围");
        }
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            List<?> rawClassIds = (List<?>) request.get("classIds");
            List<Long> classIds = rawClassIds.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(java.util.stream.Collectors.toList());
            
            if (classIds == null || classIds.isEmpty()) {
                return Result.error("班级ID列表不能为空");
            }
            requireCounselorTarget(scope, counselorId, false);
            TeacherUser targetCounselor = teacherUserMapper.findById(counselorId);
            if (targetCounselor == null || !"COUNSELOR".equals(targetCounselor.getTeacherType())) {
                throw new AccessDeniedException("只能为辅导员分配班级");
            }
            classIds.forEach(classId -> requireClassScope(scope, classId));
            int result = classCounselorRelationService.assignClassesToCounselor(counselorId, classIds);
            return Result.success("分配成功", result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("分配班级失败: {}", e.getMessage());
            return Result.error("分配失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/unassign")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "DELETE", description = "取消班级与辅导员的关联")
    public Result unassignClassFromCounselor(@RequestBody Map<String, Object> request) {
        log.info("取消班级与辅导员的关联");
        TeacherScope scope = currentScope();
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            Long classId = Long.parseLong(request.get("classId").toString());
            requireCounselorTarget(scope, counselorId, false);
            requireClassScope(scope, classId);
            int result = classCounselorRelationService.unassignClassFromCounselor(counselorId, classId);
            return Result.success("取消关联成功", result);
        } catch (AccessDeniedException e) {
            throw e;
        } catch (Exception e) {
            log.error("取消关联失败: {}", e.getMessage());
            return Result.error("取消关联失败: " + e.getMessage());
        }
    }

    @GetMapping("/students/{classId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取班级学生列表")
    public Result getClassStudents(@PathVariable Long classId, @RequestParam(required = false) Long counselorId) {
        log.info("获取班级学生列表，班级ID: {}, 辅导员ID: {}", classId, counselorId);
        TeacherScope scope = currentScope();
        requireCounselorTarget(scope, counselorId, false);
        requireClassScope(scope, classId);
        try {
            List<Map<String, Object>> students = classCounselorRelationService.findStudentsByClassIdWithCounselor(classId, counselorId);
            return Result.success(students);
        } catch (Exception e) {
            log.error("获取班级学生列表失败: {}", e.getMessage());
            return Result.error("获取学生列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/counselor-students/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取辅导员关联的所有学生")
    public Result getCounselorStudents(
            @PathVariable Long counselorId,
            @RequestParam(required = false) String searchName,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) String major,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String companyName) {
        log.info("获取辅导员关联的所有学生，辅导员ID: {}", counselorId);
        TeacherScope scope = currentScope();
        requireCounselorTarget(scope, counselorId, true);
        if (classId != null) requireClassScope(scope, classId);
        try {
            List<Map<String, Object>> students = classCounselorRelationService.findStudentsByCounselorId(counselorId, searchName, classId, major, grade, status, companyName);
            return Result.success(students);
        } catch (Exception e) {
            log.error("获取辅导员学生列表失败: {}", e.getMessage());
            return Result.error("获取学生列表失败: " + e.getMessage());
        }
    }

    @GetMapping("/relations/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取辅导员的班级关联关系")
    public Result getCounselorRelations(@PathVariable Long counselorId) {
        log.info("获取辅导员的班级关联关系，辅导员ID: {}", counselorId);
        TeacherScope scope = currentScope();
        requireCounselorTarget(scope, counselorId, false);
        try {
            List<ClassCounselorRelation> relations = classCounselorRelationService.findByCounselorId(counselorId)
                    .stream().filter(relation -> {
                        Class item = classService.findById(relation.getClassId());
                        return item != null && scope.canView(item);
                    }).toList();
            return Result.success(relations);
        } catch (Exception e) {
            log.error("获取班级关联关系失败: {}", e.getMessage());
            return Result.error("获取关联关系失败: " + e.getMessage());
        }
    }

    @GetMapping("/statistics/{counselorId}")
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取辅导员班级统计数据")
    public Result getCounselorStatistics(@PathVariable Long counselorId) {
        log.info("获取辅导员班级统计数据，辅导员ID: {}", counselorId);
        TeacherScope scope = currentScope();
        requireCounselorTarget(scope, counselorId, true);
        try {
            Map<String, Object> statistics = classCounselorRelationService.getCounselorStatistics(counselorId);
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage());
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }

    private TeacherScope currentScope() {
        Long userId = CurrentHolder.getUserId();
        TeacherUser teacher = userId == null ? null : teacherUserMapper.findById(userId);
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())) {
            throw new AccessDeniedException("教师身份不可用");
        }
        String type = teacher.getTeacherType();
        if ("COUNSELOR".equals(type)) {
            Set<Long> classIds = new HashSet<>();
            for (ClassCounselorRelation relation : classCounselorRelationService.findByCounselorId(userId)) {
                if (relation.getClassId() != null) classIds.add(relation.getClassId());
            }
            return new TeacherScope(teacher, null, null, classIds);
        }
        Long departmentId = parsePositiveId(teacher.getDepartmentId());
        Long divisionId = parsePositiveId(teacher.getDivisionId());
        if ("COLLEGE".equals(type) && departmentId != null) {
            return new TeacherScope(teacher, departmentId, null, Set.of());
        }
        if ("DEPARTMENT".equals(type) && divisionId != null) {
            return new TeacherScope(teacher, null, divisionId, Set.of());
        }
        throw new AccessDeniedException("教师负责范围未配置");
    }

    private static Long parsePositiveId(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Class requireClassScope(TeacherScope scope, Long classId) {
        Class item = classId == null ? null : classService.findById(classId);
        if (!scope.canView(item)) {
            throw new AccessDeniedException("无权访问该班级");
        }
        return item;
    }

    private void requireCounselorTarget(TeacherScope scope, Long targetId, boolean requireWholeScope) {
        if (targetId == null || targetId <= 0) {
            throw new AccessDeniedException("辅导员身份不可用");
        }
        TeacherUser actor = scope.teacher();
        if ("COUNSELOR".equals(actor.getTeacherType()) && !actor.getId().equals(targetId)) {
            throw new AccessDeniedException("无权查看其他辅导员数据");
        }
        if (!actor.getId().equals(targetId)) {
            TeacherUser target = teacherUserMapper.findById(targetId);
            if (target == null || !Integer.valueOf(1).equals(target.getStatus())
                    || !"COUNSELOR".equals(target.getTeacherType())
                    || ("COLLEGE".equals(actor.getTeacherType())
                        && !scope.departmentId().equals(parsePositiveId(target.getDepartmentId())))
                    || ("DEPARTMENT".equals(actor.getTeacherType())
                        && !scope.divisionId().equals(parsePositiveId(target.getDivisionId())))) {
                throw new AccessDeniedException("无权查看该辅导员数据");
            }
        }
        if (requireWholeScope) {
            for (ClassCounselorRelation relation : classCounselorRelationService.findByCounselorId(targetId)) {
                Class item = classService.findById(relation.getClassId());
                // 关系表可能保留已删除班级的历史关联。失效关系不能扩大可见范围，
                // 但也不应让辅导员的全部列表和统计接口误报为越权。
                if (item == null) {
                    log.warn("跳过已失效的辅导员班级关联: counselorId={}, classId={}",
                            targetId, relation.getClassId());
                    continue;
                }
                if (!scope.canView(item)) {
                    throw new AccessDeniedException("该辅导员负责范围超出当前教师管辖范围");
                }
            }
        }
    }

    private record TeacherScope(TeacherUser teacher, Long departmentId, Long divisionId, Set<Long> classIds) {
        private boolean canView(Class item) {
            if (item == null) return false;
            return switch (teacher.getTeacherType()) {
                case "COUNSELOR" -> classIds.contains(item.getId());
                case "DEPARTMENT" -> divisionId.equals(item.getDivisionId());
                case "COLLEGE" -> departmentId.equals(item.getDepartmentId());
                default -> false;
            };
        }
    }
}
