package com.gdmu.service.impl;

import com.gdmu.entity.ClassCounselorRelation;
import com.gdmu.entity.Department;
import com.gdmu.entity.Division;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.dto.TeacherDashboardStatsDTO;
import com.gdmu.mapper.ClassCounselorRelationMapper;
import com.gdmu.mapper.DepartmentMapper;
import com.gdmu.mapper.DivisionMapper;
import com.gdmu.mapper.StudentInternshipStatusMapper;
import com.gdmu.mapper.TeacherUserMapper;
import com.gdmu.service.TeacherDashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TeacherDashboardServiceImpl implements TeacherDashboardService {

    @Autowired
    private StudentInternshipStatusMapper studentInternshipStatusMapper;

    @Autowired
    private ClassCounselorRelationMapper classCounselorRelationMapper;

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private DivisionMapper divisionMapper;

    @Autowired
    private DepartmentMapper departmentMapper;

    @Override
    public TeacherDashboardStatsDTO getDashboardStats(Long userId, String teacherType, String startDate, String endDate) {
        log.info("获取教师端看板统计数据，userId: {}, teacherType: {}, 时间范围: {} - {}",
                userId, teacherType, startDate, endDate);

        try {
            // 辅导员：按负责班级过滤
            if ("COUNSELOR".equals(teacherType) && userId != null && userId > 0) {
                return buildCounselorStats(userId, startDate, endDate);
            }

            // 系室教师：按所属系室过滤
            if ("DEPARTMENT".equals(teacherType) && userId != null && userId > 0) {
                return buildDepartmentStats(userId, startDate, endDate);
            }

            // 学院教师：按所属学院下所有系室过滤
            if ("COLLEGE".equals(teacherType) && userId != null && userId > 0) {
                return buildCollegeStats(userId, startDate, endDate);
            }

            // 管理员或其他：全量统计
            return buildGlobalStats(startDate, endDate);

        } catch (Exception e) {
            log.error("获取教师端看板统计数据失败", e);
            return buildEmptyResult();
        }
    }

    private TeacherDashboardStatsDTO buildCounselorStats(Long userId, String startDate, String endDate) {
        TeacherDashboardStatsDTO result = new TeacherDashboardStatsDTO();

        List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(userId);
        if (relations == null || relations.isEmpty()) {
            log.warn("辅导员 {} 没有关联的班级", userId);
            return buildEmptyResult();
        }

        List<Long> classIds = relations.stream()
                .map(ClassCounselorRelation::getClassId)
                .collect(Collectors.toList());
        log.debug("辅导员负责班级数量：{}", classIds.size());

        Map<String, Object> dashboardStats = studentInternshipStatusMapper.getDashboardStatsByClassIds(classIds, startDate, endDate);
        if (dashboardStats == null) {
            dashboardStats = defaultTotalsMap();
        }

        result.setTotalStudents(getIntValue(dashboardStats.get("totalStudents")));
        result.setConfirmed(getIntValue(dashboardStats.get("confirmed")) + getIntValue(dashboardStats.get("interning")));
        result.setOffer(getIntValue(dashboardStats.get("offer")));
        result.setNoOffer(getIntValue(dashboardStats.get("noOffer")));
        result.setDelay(getIntValue(dashboardStats.get("delay")));

        result.setScopeName("负责班级");

        result.setGradeData(convertGradeData(studentInternshipStatusMapper.getStatsByGradeByClassIds(classIds, startDate, endDate)));
        result.setMajorData(convertMajorData(studentInternshipStatusMapper.getStatsByMajorByClassIds(classIds, startDate, endDate)));
        result.setClassData(convertClassData(studentInternshipStatusMapper.getStatsByClassIds(classIds, startDate, endDate)));

        return result;
    }

    private TeacherDashboardStatsDTO buildDepartmentStats(Long userId, String startDate, String endDate) {
        TeacherUser teacher = teacherUserMapper.findById(userId);
        Long divisionId = null;
        if (teacher != null && teacher.getDivisionId() != null) {
            divisionId = Long.valueOf(teacher.getDivisionId());
        }

        if (divisionId == null) {
            log.warn("系室教师 {} 没有关联的系室", userId);
            return buildEmptyResult();
        }

        List<Long> divisionIds = Collections.singletonList(divisionId);
        TeacherDashboardStatsDTO result = buildDivisionStats(divisionIds, startDate, endDate);

        Division division = divisionMapper.findById(divisionId);
        if (division != null) {
            result.setScopeName(division.getName());
        }

        return result;
    }

    private TeacherDashboardStatsDTO buildCollegeStats(Long userId, String startDate, String endDate) {
        TeacherUser teacher = teacherUserMapper.findById(userId);
        Long deptId = null;
        if (teacher != null && teacher.getDepartmentId() != null) {
            deptId = Long.valueOf(teacher.getDepartmentId());
        }

        if (deptId == null) {
            log.warn("学院教师 {} 没有关联的学院", userId);
            return buildEmptyResult();
        }

        List<Division> divisions = divisionMapper.findByDepartmentId(deptId);
        if (divisions == null || divisions.isEmpty()) {
            log.warn("学院 {} 下无系室", deptId);
            return buildEmptyResult();
        }

        List<Long> divisionIds = divisions.stream()
                .map(Division::getId)
                .collect(Collectors.toList());
        log.debug("学院教师管辖 {} 个系室", divisionIds.size());

        TeacherDashboardStatsDTO result = buildDivisionStats(divisionIds, startDate, endDate);

        Department department = departmentMapper.findById(deptId);
        if (department != null) {
            result.setScopeName(department.getName());
        }

        return result;
    }

    private TeacherDashboardStatsDTO buildDivisionStats(List<Long> divisionIds, String startDate, String endDate) {
        TeacherDashboardStatsDTO result = new TeacherDashboardStatsDTO();

        Map<String, Object> dashboardStats = studentInternshipStatusMapper.getDashboardStatsByDivisionIds(divisionIds, startDate, endDate);
        if (dashboardStats == null) {
            dashboardStats = defaultTotalsMap();
        }

        result.setTotalStudents(getIntValue(dashboardStats.get("totalStudents")));
        result.setConfirmed(getIntValue(dashboardStats.get("confirmed")) + getIntValue(dashboardStats.get("interning")));
        result.setOffer(getIntValue(dashboardStats.get("offer")));
        result.setNoOffer(getIntValue(dashboardStats.get("noOffer")));
        result.setDelay(getIntValue(dashboardStats.get("delay")));

        result.setGradeData(convertGradeData(studentInternshipStatusMapper.getStatsByGradeByDivisionIds(divisionIds, startDate, endDate)));
        result.setMajorData(convertMajorData(studentInternshipStatusMapper.getStatsByMajorByDivisionIds(divisionIds, startDate, endDate)));
        result.setClassData(convertClassData(studentInternshipStatusMapper.getStatsByClassByDivisionIds(divisionIds, startDate, endDate)));
        result.setDivisionData(convertDivisionData(studentInternshipStatusMapper.getStatsByDivisionByDivisionIds(divisionIds, startDate, endDate)));

        return result;
    }

    private TeacherDashboardStatsDTO buildGlobalStats(String startDate, String endDate) {
        TeacherDashboardStatsDTO result = new TeacherDashboardStatsDTO();

        Map<String, Object> dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
        if (dashboardStats == null) {
            dashboardStats = defaultTotalsMap();
        }

        result.setTotalStudents(getIntValue(dashboardStats.get("totalStudents")));
        result.setConfirmed(getIntValue(dashboardStats.get("confirmed")) + getIntValue(dashboardStats.get("interning")));
        result.setOffer(getIntValue(dashboardStats.get("offer")));
        result.setNoOffer(getIntValue(dashboardStats.get("noOffer")));
        result.setDelay(getIntValue(dashboardStats.get("delay")));

        result.setGradeData(convertGradeData(studentInternshipStatusMapper.getStatsByGrade(startDate, endDate)));
        result.setMajorData(convertMajorData(studentInternshipStatusMapper.getStatsByMajor(startDate, endDate)));
        result.setClassData(convertClassData(studentInternshipStatusMapper.getStatsByClass(startDate, endDate)));

        return result;
    }

    private Map<String, Object> defaultTotalsMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("totalStudents", 0);
        map.put("confirmed", 0);
        map.put("offer", 0);
        map.put("noOffer", 0);
        map.put("interning", 0);
        map.put("delay", 0);
        return map;
    }

    private TeacherDashboardStatsDTO buildEmptyResult() {
        TeacherDashboardStatsDTO result = new TeacherDashboardStatsDTO();
        result.setTotalStudents(0);
        result.setConfirmed(0);
        result.setOffer(0);
        result.setNoOffer(0);
        result.setDelay(0);
        result.setGradeData(new ArrayList<>());
        result.setMajorData(new ArrayList<>());
        result.setClassData(new ArrayList<>());
        return result;
    }

    private Integer getIntValue(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private List<TeacherDashboardStatsDTO.GradeStatsDTO> convertGradeData(List<Map<String, Object>> gradeDataList) {
        List<TeacherDashboardStatsDTO.GradeStatsDTO> result = new ArrayList<>();
        if (gradeDataList == null) {
            return result;
        }
        
        for (Map<String, Object> item : gradeDataList) {
            TeacherDashboardStatsDTO.GradeStatsDTO dto = new TeacherDashboardStatsDTO.GradeStatsDTO();
            dto.setGradeName(getStringValue(item.get("gradeName")));
            dto.setTotal(getIntValue(item.get("total")));
            dto.setConfirmed(getIntValue(item.get("confirmed")));
            dto.setOffer(getIntValue(item.get("offer")));
            dto.setNoOffer(getIntValue(item.get("noOffer")));
            dto.setDelay(getIntValue(item.get("delay")));
            result.add(dto);
        }
        return result;
    }

    private List<TeacherDashboardStatsDTO.MajorStatsDTO> convertMajorData(List<Map<String, Object>> majorDataList) {
        List<TeacherDashboardStatsDTO.MajorStatsDTO> result = new ArrayList<>();
        if (majorDataList == null) {
            return result;
        }
        
        for (Map<String, Object> item : majorDataList) {
            TeacherDashboardStatsDTO.MajorStatsDTO dto = new TeacherDashboardStatsDTO.MajorStatsDTO();
            dto.setMajorName(getStringValue(item.get("majorName")));
            dto.setTotal(getIntValue(item.get("total")));
            dto.setConfirmed(getIntValue(item.get("confirmed")));
            dto.setOffer(getIntValue(item.get("offer")));
            dto.setNoOffer(getIntValue(item.get("noOffer")));
            dto.setDelay(getIntValue(item.get("delay")));
            result.add(dto);
        }
        return result;
    }

    private List<TeacherDashboardStatsDTO.ClassStatsDTO> convertClassData(List<Map<String, Object>> classDataList) {
        List<TeacherDashboardStatsDTO.ClassStatsDTO> result = new ArrayList<>();
        if (classDataList == null) {
            return result;
        }

        for (Map<String, Object> item : classDataList) {
            TeacherDashboardStatsDTO.ClassStatsDTO dto = new TeacherDashboardStatsDTO.ClassStatsDTO();
            dto.setClassName(getStringValue(item.get("className")));
            dto.setTotal(getIntValue(item.get("total")));
            dto.setConfirmed(getIntValue(item.get("confirmed")));
            dto.setOffer(getIntValue(item.get("offer")));
            dto.setNoOffer(getIntValue(item.get("noOffer")));
            dto.setDelay(getIntValue(item.get("delay")));
            result.add(dto);
        }
        return result;
    }

    private List<TeacherDashboardStatsDTO.DivisionStatsDTO> convertDivisionData(List<Map<String, Object>> divisionDataList) {
        List<TeacherDashboardStatsDTO.DivisionStatsDTO> result = new ArrayList<>();
        if (divisionDataList == null) {
            return result;
        }

        for (Map<String, Object> item : divisionDataList) {
            TeacherDashboardStatsDTO.DivisionStatsDTO dto = new TeacherDashboardStatsDTO.DivisionStatsDTO();
            dto.setDivisionName(getStringValue(item.get("divisionName")));
            dto.setTotal(getIntValue(item.get("total")));
            dto.setConfirmed(getIntValue(item.get("confirmed")));
            dto.setOffer(getIntValue(item.get("offer")));
            dto.setNoOffer(getIntValue(item.get("noOffer")));
            dto.setDelay(getIntValue(item.get("delay")));
            result.add(dto);
        }
        return result;
    }

    private String getStringValue(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    @Override
    public TeacherDashboardStatsDTO getCounselorDashboardStats(Long counselorId, String startDate, String endDate) {
        log.info("获取辅导员看板统计数据，辅导员 ID: {}, 时间范围：{} - {}", counselorId, startDate, endDate);
        
        try {
            TeacherDashboardStatsDTO result = new TeacherDashboardStatsDTO();
            result.setScopeName("负责班级");

            List<ClassCounselorRelation> relations = classCounselorRelationMapper.findByCounselorId(counselorId);
            if (relations != null && !relations.isEmpty()) {
                List<Long> classIds = relations.stream()
                        .map(ClassCounselorRelation::getClassId)
                        .collect(Collectors.toList());
                
                log.debug("辅导员负责班级数量：{}", classIds.size());
                
                Map<String, Object> dashboardStats = studentInternshipStatusMapper.getDashboardStatsByClassIds(classIds, startDate, endDate);
                if (dashboardStats == null) {
                    log.warn("辅导员看板统计数据为空，使用默认值");
                    dashboardStats = new HashMap<>();
                    dashboardStats.put("totalStudents", 0);
                    dashboardStats.put("confirmed", 0);
                    dashboardStats.put("offer", 0);
                    dashboardStats.put("noOffer", 0);
                    dashboardStats.put("interning", 0);
                    dashboardStats.put("delay", 0);
                }
                
                result.setTotalStudents(getIntValue(dashboardStats.get("totalStudents")));
                result.setConfirmed(getIntValue(dashboardStats.get("confirmed")) + getIntValue(dashboardStats.get("interning")));
                result.setOffer(getIntValue(dashboardStats.get("offer")));
                result.setNoOffer(getIntValue(dashboardStats.get("noOffer")));
                result.setDelay(getIntValue(dashboardStats.get("delay")));
                
                List<Map<String, Object>> gradeDataList = studentInternshipStatusMapper.getStatsByGradeByClassIds(classIds, startDate, endDate);
                result.setGradeData(convertGradeData(gradeDataList));
                
                List<Map<String, Object>> majorDataList = studentInternshipStatusMapper.getStatsByMajorByClassIds(classIds, startDate, endDate);
                result.setMajorData(convertMajorData(majorDataList));
                
                List<Map<String, Object>> classDataList = studentInternshipStatusMapper.getStatsByClassIds(classIds, startDate, endDate);
                result.setClassData(convertClassData(classDataList));
            } else {
                log.warn("辅导员 {} 没有关联的班级", counselorId);
                result.setTotalStudents(0);
                result.setConfirmed(0);
                result.setOffer(0);
                result.setNoOffer(0);
                result.setDelay(0);
                result.setGradeData(new ArrayList<>());
                result.setMajorData(new ArrayList<>());
                result.setClassData(new ArrayList<>());
            }
            
            return result;
        } catch (Exception e) {
            log.error("获取辅导员看板统计数据失败，辅导员 ID: {}", counselorId, e);
            TeacherDashboardStatsDTO errorResult = new TeacherDashboardStatsDTO();
            errorResult.setTotalStudents(0);
            errorResult.setConfirmed(0);
            errorResult.setOffer(0);
            errorResult.setNoOffer(0);
            errorResult.setDelay(0);
            errorResult.setGradeData(new ArrayList<>());
            errorResult.setMajorData(new ArrayList<>());
            errorResult.setClassData(new ArrayList<>());
            return errorResult;
        }
    }
}
