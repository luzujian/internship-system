package com.gdmu.controller;

import com.gdmu.anno.Log;
import com.gdmu.entity.Class;
import com.gdmu.entity.ClassCounselorRelation;
import com.gdmu.entity.Result;
import com.gdmu.service.ClassCounselorRelationService;
import com.gdmu.service.ClassService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/teacher/classes")
public class TeacherClassController {

    @Autowired
    private ClassService classService;

    @Autowired
    private ClassCounselorRelationService classCounselorRelationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('TEACHER_COUNSELOR', 'TEACHER_COLLEGE', 'TEACHER_DEPARTMENT')")
    @Log(module = "TEACHER_CLASS", operationType = "SELECT", description = "获取教师关联的班级列表")
    public Result getTeacherClasses(@RequestParam(required = false) Long counselorId) {
        log.info("获取教师关联的班级列表，辅导员ID: {}", counselorId);
        try {
            if (counselorId == null) {
                return Result.error("辅导员ID不能为空");
            }
            List<Class> classes = classCounselorRelationService.findClassesByCounselorId(counselorId);
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
        try {
            List<Class> classes = classService.findAllWithStudentCount();
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
        try {
            Class classEntity = classService.findById(classId);
            if (classEntity == null) {
                return Result.error("班级不存在");
            }
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
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            List<?> rawClassIds = (List<?>) request.get("classIds");
            List<Long> classIds = rawClassIds.stream()
                    .map(obj -> Long.parseLong(obj.toString()))
                    .collect(java.util.stream.Collectors.toList());
            
            if (classIds == null || classIds.isEmpty()) {
                return Result.error("班级ID列表不能为空");
            }
            
            int result = classCounselorRelationService.assignClassesToCounselor(counselorId, classIds);
            return Result.success("分配成功", result);
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
        try {
            Long counselorId = Long.parseLong(request.get("counselorId").toString());
            Long classId = Long.parseLong(request.get("classId").toString());
            
            int result = classCounselorRelationService.unassignClassFromCounselor(counselorId, classId);
            return Result.success("取消关联成功", result);
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
        try {
            if (counselorId == null) {
                return Result.error("辅导员ID不能为空");
            }
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
        try {
            List<ClassCounselorRelation> relations = classCounselorRelationService.findByCounselorId(counselorId);
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
        try {
            Map<String, Object> statistics = classCounselorRelationService.getCounselorStatistics(counselorId);
            return Result.success(statistics);
        } catch (Exception e) {
            log.error("获取统计数据失败: {}", e.getMessage());
            return Result.error("获取统计数据失败: " + e.getMessage());
        }
    }
}
