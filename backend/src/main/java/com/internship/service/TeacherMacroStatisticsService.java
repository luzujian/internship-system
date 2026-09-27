package com.internship.service;

import com.internship.entity.Division;
import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherMacroStatisticsDTO;
import com.internship.exception.BusinessException;
import com.internship.mapper.DivisionMapper;
import com.internship.mapper.TeacherMacroStatisticsMapper;
import com.internship.mapper.TeacherUserMapper;
import org.springframework.stereotype.Service;

@Service
public class TeacherMacroStatisticsService {
    private final JobSearchStatusService jobSearchStatusService;
    private final TeacherMacroStatisticsMapper teacherMacroStatisticsMapper;
    private final TeacherUserMapper teacherUserMapper;
    private final DivisionMapper divisionMapper;

    public TeacherMacroStatisticsService(JobSearchStatusService jobSearchStatusService,
                                         TeacherMacroStatisticsMapper teacherMacroStatisticsMapper,
                                         TeacherUserMapper teacherUserMapper,
                                         DivisionMapper divisionMapper) {
        this.jobSearchStatusService = jobSearchStatusService;
        this.teacherMacroStatisticsMapper = teacherMacroStatisticsMapper;
        this.teacherUserMapper = teacherUserMapper;
        this.divisionMapper = divisionMapper;
    }

    public TeacherMacroStatisticsDTO getStatistics(Long teacherUserId) {
        Long departmentId = resolveDepartmentId(teacherUserId);
        long withoutOfferStudentCount =
                jobSearchStatusService.countWithoutOfferByDepartmentId(departmentId);
        long totalRemainingQuota = teacherMacroStatisticsMapper.sumActivePositionRemainingQuota();
        return new TeacherMacroStatisticsDTO(withoutOfferStudentCount, totalRemainingQuota);
    }

    private Long resolveDepartmentId(Long teacherUserId) {
        if (teacherUserId == null || teacherUserId <= 0) {
            throw new BusinessException("无法识别当前教师");
        }
        TeacherUser teacher = teacherUserMapper.findById(teacherUserId);
        if (teacher == null) {
            throw new BusinessException("当前教师档案不存在");
        }

        Long directDepartmentId = parseOptionalId(teacher.getDepartmentId(), "教师所属学院");
        if ("DEPARTMENT".equalsIgnoreCase(teacher.getTeacherType())) {
            Long divisionId = parseOptionalId(teacher.getDivisionId(), "系室");
            if (divisionId == null) {
                if (directDepartmentId == null) {
                    throw new BusinessException("当前系主任未关联学院或系室，请联系管理员修正");
                }
                return directDepartmentId;
            }
            Division division = divisionMapper.findById(divisionId);
            if (division == null || division.getDepartmentId() == null
                    || division.getDepartmentId() <= 0) {
                throw new BusinessException("当前系室未关联有效学院");
            }
            if (directDepartmentId != null
                    && !directDepartmentId.equals(division.getDepartmentId())) {
                throw new BusinessException("教师学院与系室所属学院不一致，请联系管理员修正");
            }
            return division.getDepartmentId();
        }

        if (directDepartmentId == null) {
            throw new BusinessException("当前教师未关联学院，请联系管理员修正");
        }
        return directDepartmentId;
    }

    private Long parseOptionalId(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            long id = Long.parseLong(value.trim());
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException e) {
            throw new BusinessException(fieldName + "配置无效，请联系管理员修正");
        }
    }
}
