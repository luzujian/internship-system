package com.internship.service.impl;

import com.internship.entity.Class;
import com.internship.entity.ClassCounselorRelation;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.ClassMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ClassCounselorRelationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ClassCounselorRelationServiceImpl implements ClassCounselorRelationService {

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int assignClassToCounselor(Long classId, Long counselorId, String className, String counselorName) {
        List<ClassCounselorRelation> existing = classCounselorRelationMapper.findByClassId(classId);
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
        for (Long classId : classIds) {
            List<ClassCounselorRelation> existing = classCounselorRelationMapper.findByClassId(classId);
            boolean alreadyAssigned = existing.stream()
                    .anyMatch(r -> r.getCounselorId().equals(counselorId));

            if (!alreadyAssigned) {
                Class classEntity = classMapper.findById(classId);
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
    public int unassignClassFromCounselor(Long counselorId, Long classId) {
        return classCounselorRelationMapper.deleteByClassAndCounselor(classId, counselorId);
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
        // 先通过 counselorId 获取教师的 teacherUserId
        var teacher = teacherUserMapper.findById(counselorId);
        if (teacher == null || teacher.getTeacherUserId() == null) {
            return new ArrayList<>();
        }
        // 直接通过 class.teacher_user_id 查询班级
        return classMapper.findByTeacherUserId(teacher.getTeacherUserId());
    }

    @Override
    public List<Map<String, Object>> findStudentsByClassId(Long classId) {
        return studentUserMapper.findByClassIdAsMap(classId);
    }

    @Override
    public List<Map<String, Object>> findStudentsByClassIdWithCounselor(Long classId, Long counselorId) {
        List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(counselorId);
        boolean hasAccess = relations.stream().anyMatch(r -> r.getClassId().equals(classId));
        
        if (!hasAccess) {
            log.warn("辅导员 {} 无权访问班级 {} 的学生信息", counselorId, classId);
            return new ArrayList<>();
        }
        
        return studentUserMapper.findByClassIdAsMap(classId);
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

        return studentUserMapper.findByClassIdsAndSearch(classIds, searchName, major, grade, status, companyName);
    }

    @Override
    public Map<String, Object> getCounselorStatistics(Long counselorId) {
        Map<String, Object> statistics = new HashMap<>();

        List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(counselorId);
        statistics.put("classCount", relations.size());

        if (relations.isEmpty()) {
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

        List<Long> classIds = relations.stream()
                .map(ClassCounselorRelation::getClassId)
                .collect(Collectors.toList());

        // 使用 StudentInternshipStatusMapper 获取统计数据
        Map<String, Object> dashboardStats = studentInternshipStatusMapper.getDashboardStatsByClassIds(classIds, null, null);

        int totalStudents = dashboardStats != null && dashboardStats.get("totalStudents") != null
                ? ((Number) dashboardStats.get("totalStudents")).intValue() : 0;
        int confirmed = dashboardStats != null && dashboardStats.get("confirmed") != null
                ? ((Number) dashboardStats.get("confirmed")).intValue() : 0;
        int offer = dashboardStats != null && dashboardStats.get("offer") != null
                ? ((Number) dashboardStats.get("offer")).intValue() : 0;
        int noOffer = dashboardStats != null && dashboardStats.get("noOffer") != null
                ? ((Number) dashboardStats.get("noOffer")).intValue() : 0;
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
