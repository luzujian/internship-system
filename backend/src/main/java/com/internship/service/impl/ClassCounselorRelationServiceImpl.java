package com.internship.service.impl;

import com.internship.entity.Class;
import com.internship.entity.ClassCounselorRelation;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.ClassMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.JobSearchStatusService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClassCounselorRelationServiceImpl implements ClassCounselorRelationService {
    @Override
    public List<Class> findClaimableClasses(Long counselorId) {
        var teacher = teacherUserMapper.findById(counselorId);
        return classMapper.findClaimableByDepartment(requireCounselorDepartment(teacher));
    }

    private Long requireCounselorDepartment(com.internship.entity.TeacherUser teacher) {
        if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                || !"COUNSELOR".equals(teacher.getTeacherType())) {
            throw new org.springframework.security.access.AccessDeniedException("仅在职辅导员可以认领班级");
        }
        try {
            long departmentId = Long.parseLong(teacher.getDepartmentId());
            if (departmentId > 0) return departmentId;
        } catch (NumberFormatException ignored) { }
        throw new com.internship.exception.BusinessException("未配置所属学院，请联系管理员");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(value = "classes", key = "'all'")
    public int claimClasses(Long counselorId, List<Long> classIds) {
        if (classIds == null || classIds.isEmpty() || classIds.size() > 100
                || classIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new com.internship.exception.BusinessException("请选择 1 至 100 个有效班级");
        }
        var teacher = teacherUserMapper.findByIdForUpdate(counselorId);
        Long departmentId = requireCounselorDepartment(teacher);
        if (teacher.getTeacherUserId() == null || teacher.getTeacherUserId().isBlank()) {
            throw new com.internship.exception.BusinessException("教师工号未配置，请联系管理员");
        }
        // 固定加锁顺序；占用状态使用锁定读取，防止同时认领或与管理员分配发生竞争。
        List<Class> selected = new ArrayList<>();
        for (Long classId : classIds.stream().distinct().sorted().toList()) {
            Class item = classMapper.findByIdForUpdate(classId);
            if (item == null || !departmentId.equals(item.getDepartmentId())) {
                throw new org.springframework.security.access.AccessDeniedException("只能认领本院的班级");
            }
            var relations = classCounselorRelationMapper.findByClassIdForUpdate(classId);
            if ((item.getTeacherId() != null && !item.getTeacherId().isBlank()) || !relations.isEmpty()) {
                throw new com.internship.exception.BusinessException("班级「" + item.getName() + "」已分配，请联系管理员确认新增或转交");
            }
            selected.add(item);
        }
        for (Class item : selected) {
            ClassCounselorRelation relation = new ClassCounselorRelation();
            relation.setClassId(item.getId());
            relation.setClassName(item.getName());
            relation.setCounselorId(counselorId);
            relation.setCounselorName(teacher.getName());
            relation.setCreateTime(new Date());
            relation.setUpdateTime(new Date());
            classCounselorRelationMapper.insert(relation);
            classMapper.updateResponsibleTeacher(item.getId(), teacher.getTeacherUserId());
        }
        return selected.size();
    }

    @Autowired
    private ClassCounselorRelationMapper classCounselorRelationMapper;

    @Autowired
    private ClassMapper classMapper;

    @Autowired
    private StudentUserMapper studentUserMapper;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private StudentInternshipStatusMapper studentInternshipStatusMapper;

    @Autowired
    private JobSearchStatusService jobSearchStatusService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignClassToCounselor(Long classId, Long counselorId, String className, String counselorName) {
        if (classMapper.findByIdForUpdate(classId) == null) throw new com.internship.exception.BusinessException("班级不存在");
        List<ClassCounselorRelation> existing = classCounselorRelationMapper.findByClassIdForUpdate(classId);
        if (!existing.isEmpty()) {
            for (ClassCounselorRelation relation : existing) {
                if (relation.getCounselorId().equals(counselorId)) {
                    log.info("班级 {} 已经分配给辅导员 {}", classId, counselorId);
                    return 0;
                }
            }
        }

        ClassCounselorRelation relation = new ClassCounselorRelation();
        relation.setClassId(classId);
        relation.setClassName(className);
        relation.setCounselorId(counselorId);
        relation.setCounselorName(counselorName);
        relation.setCreateTime(new Date());
        relation.setUpdateTime(new Date());

        return classCounselorRelationMapper.insert(relation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignClassesToCounselor(Long counselorId, List<Long> classIds) {
        String counselorName = null;
        var teacher = teacherUserMapper.findById(counselorId);
        if (teacher != null) {
            counselorName = teacher.getName();
        }

        int count = 0;
        for (Long classId : classIds.stream().distinct().sorted().toList()) {
            Class classEntity = classMapper.findByIdForUpdate(classId);
            List<ClassCounselorRelation> existing = classCounselorRelationMapper.findByClassIdForUpdate(classId);
            boolean alreadyAssigned = existing.stream()
                    .anyMatch(r -> r.getCounselorId().equals(counselorId));

            if (!alreadyAssigned) {
                if (classEntity != null) {
                    ClassCounselorRelation relation = new ClassCounselorRelation();
                    relation.setClassId(classId);
                    relation.setClassName(classEntity.getName());
                    relation.setCounselorId(counselorId);
                    relation.setCounselorName(counselorName != null ? counselorName : "辅导员" + String.valueOf(counselorId));
                    relation.setCreateTime(new Date());
                    relation.setUpdateTime(new Date());
                    classCounselorRelationMapper.insert(relation);
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    @org.springframework.cache.annotation.CacheEvict(value = "classes", key = "'all'")
    public int unassignClassFromCounselor(Long counselorId, Long classId) {
        var teacher = teacherUserMapper.findByIdForUpdate(counselorId);
        Class item = classMapper.findByIdForUpdate(classId);
        int count = classCounselorRelationMapper.deleteByClassAndCounselor(classId, counselorId);
        if (count > 0 && item != null && teacher != null
                && teacher.getTeacherUserId() != null && teacher.getTeacherUserId().equals(item.getTeacherId())) {
            classMapper.updateResponsibleTeacher(classId, null);
        }
        return count;
    }

    @Override
    public List<ClassCounselorRelation> findByCounselorId(Long counselorId) {
        return classCounselorRelationMapper.findByCounselorId(counselorId);
    }

    @Override
    public List<ClassCounselorRelation> findByClassId(Long classId) {
        return classCounselorRelationMapper.findByClassId(classId);
    }

    @Override
    public List<Class> findClassesByCounselorId(Long counselorId) {
        // 与班级权限、学生列表及统计使用同一份负责关系，包含单独分配的班级。
        return classCounselorRelationMapper.findByCounselorId(counselorId).stream()
                .map(ClassCounselorRelation::getClassId)
                .filter(Objects::nonNull)
                .distinct()
                .map(classMapper::findById)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<Map<String, Object>> findStudentsByClassId(Long classId) {
        List<Map<String, Object>> students = studentUserMapper.findByClassIdAsMap(classId);
        jobSearchStatusService.enrichStudentMaps(students);
        return students;
    }

    @Override
    public List<Map<String, Object>> findStudentsByClassIdWithCounselor(Long classId, Long counselorId) {
        List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(counselorId);
        boolean hasAccess = relations.stream().anyMatch(r -> r.getClassId().equals(classId));
        
        if (!hasAccess) {
            log.warn("辅导员 {} 无权访问班级 {} 的学生信息", counselorId, classId);
            return new ArrayList<>();
        }
        
        List<Map<String, Object>> students = studentUserMapper.findByClassIdAsMap(classId);
        jobSearchStatusService.enrichStudentMaps(students);
        return students;
    }

    @Override
    public List<Map<String, Object>> findStudentsByCounselorId(Long counselorId, String searchName, Long classId, String major, String grade, Integer status, String companyName) {
        List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(counselorId);
        List<Long> classIds = new ArrayList<>();

        if (classId != null) {
            boolean hasRelation = relations.stream().anyMatch(r -> r.getClassId().equals(classId));
            if (hasRelation) {
                classIds.add(classId);
            }
        } else {
            for (ClassCounselorRelation relation : relations) {
                classIds.add(relation.getClassId());
            }
        }

        if (classIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> students = studentUserMapper.findByClassIdsAndSearch(
                classIds, searchName, major, grade, status, companyName);
        jobSearchStatusService.enrichStudentMaps(students);
        return students;
    }

    @Override
    public Map<String, Object> getCounselorStatistics(Long counselorId) {
        Map<String, Object> statistics = new HashMap<>();

        List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(counselorId);
        // 过滤掉已被删除的班级（孤儿记录）
        List<ClassCounselorRelation> validRelations = relations.stream()
                .filter(r -> classMapper.findById(r.getClassId()) != null)
                .collect(Collectors.toList());
        statistics.put("classCount", validRelations.size());

        if (validRelations.isEmpty()) {
            statistics.put("studentCount", 0);
            statistics.put("noOfferCount", 0);
            statistics.put("pendingCount", 0);
            statistics.put("confirmedCount", 0);
            statistics.put("interningCount", 0);
            statistics.put("finishedCount", 0);
            statistics.put("interruptedCount", 0);
            statistics.put("delayedCount", 0);
            return statistics;
        }

        List<Long> classIds = validRelations.stream()
                .map(ClassCounselorRelation::getClassId)
                .collect(Collectors.toList());

        // 使用 StudentInternshipStatusMapper 获取统计数据
        Map<String, Object> dashboardStats = studentInternshipStatusMapper.getDashboardStatsByClassIds(classIds, null, null);

        int totalStudents = dashboardStats != null && dashboardStats.get("totalStudents") != null
                ? ((Number) dashboardStats.get("totalStudents")).intValue() : 0;
        int confirmed = dashboardStats != null && dashboardStats.get("confirmed") != null
                ? ((Number) dashboardStats.get("confirmed")).intValue() : 0;
        int offer = Math.toIntExact(jobSearchStatusService.countByDisplayFilter(1, classIds));
        int noOffer = Math.toIntExact(jobSearchStatusService.countByDisplayFilter(0, classIds));
        int interning = dashboardStats != null && dashboardStats.get("interning") != null
                ? ((Number) dashboardStats.get("interning")).intValue() : 0;
        int finished = dashboardStats != null && dashboardStats.get("finished") != null
                ? ((Number) dashboardStats.get("finished")).intValue() : 0;
        int interrupted = dashboardStats != null && dashboardStats.get("interrupted") != null
                ? ((Number) dashboardStats.get("interrupted")).intValue() : 0;
        int delay = dashboardStats != null && dashboardStats.get("delay") != null
                ? ((Number) dashboardStats.get("delay")).intValue() : 0;

        statistics.put("studentCount", totalStudents);
        statistics.put("confirmedCount", confirmed);
        statistics.put("pendingCount", offer);
        statistics.put("noOfferCount", noOffer);
        statistics.put("interningCount", interning);
        statistics.put("finishedCount", finished);
        statistics.put("interruptedCount", interrupted);
        statistics.put("delayedCount", delay);

        return statistics;
    }

    @Override
    public Long findCounselorIdByStudentId(Long studentId) {
        var student = studentUserMapper.findById(studentId);
        if (student == null || student.getClassId() == null) {
            return null;
        }
        
        List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByClassId(student.getClassId());
        if (relations.isEmpty()) {
            return null;
        }
        
        return relations.get(0).getCounselorId();
    }

    @Override
    public ClassCounselorRelation findById(Long id) {
        return classCounselorRelationMapper.findById(id);
    }
}
