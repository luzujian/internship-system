package com.gdmu.service.impl;

import com.gdmu.entity.ClassCounselorRelation;
import com.gdmu.entity.dto.TeacherDashboardStatsDTO;
import com.gdmu.mapper.ClassCounselorRelationMapper;
import com.gdmu.mapper.StudentInternshipStatusMapper;
import com.gdmu.service.TeacherDashboardService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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

    @Override
    public TeacherDashboardStatsDTO getDashboardStats(String startDate, String endDate) {
        log.info("获取教师端看板统计数据，时间范围：{} - {}", startDate, endDate);
        
        try {
            TeacherDashboardStatsDTO result = new TeacherDashboardStatsDTO();

            Map<String, Object> dashboardStats = studentInternshipStatusMapper.getDashboardStats(startDate, endDate);
            if (dashboardStats == null) {
                log.warn("看板统计数据为空，使用默认值");
                dashboardStats = new HashMap<>();
                dashboardStats.put("totalStudents", 0);
                dashboardStats.put("confirmed", 0);
                dashboardStats.put("offer", 0);
                dashboardStats.put("noOffer", 0);
                dashboardStats.put("delay", 0);
            }

            result.setTotalStudents(getIntValue(dashboardStats.get("totalStudents")));
            result.setConfirmed(getIntValue(dashboardStats.get("confirmed")));
            result.setOffer(getIntValue(dashboardStats.get("offer")));
            result.setNoOffer(getIntValue(dashboardStats.get("noOffer")));
            result.setDelay(getIntValue(dashboardStats.get("delay")));

            List<Map<String, Object>> gradeDataList = studentInternshipStatusMapper.getStatsByGrade(startDate, endDate);
            result.setGradeData(convertGradeData(gradeDataList));

            List<Map<String, Object>> majorDataList = studentInternshipStatusMapper.getStatsByMajor(startDate, endDate);
            result.setMajorData(convertMajorData(majorDataList));

            List<Map<String, Object>> classDataList = studentInternshipStatusMapper.getStatsByClass(startDate, endDate);
            result.setClassData(convertClassData(classDataList));

            log.debug("教师端看板统计数据获取成功");
            return result;
        } catch (Exception e) {
            log.error("获取教师端看板统计数据失败", e);
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
                    dashboardStats.put("delay", 0);
                }
                
                result.setTotalStudents(getIntValue(dashboardStats.get("totalStudents")));
                result.setConfirmed(getIntValue(dashboardStats.get("confirmed")));
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
