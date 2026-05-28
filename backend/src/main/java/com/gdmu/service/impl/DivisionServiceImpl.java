package com.gdmu.service.impl;

import com.gdmu.entity.Class;
import com.gdmu.entity.Division;
import com.gdmu.entity.Major;
import com.gdmu.mapper.DivisionMapper;
import com.gdmu.service.ClassService;
import com.gdmu.service.DivisionService;
import com.gdmu.service.MajorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class DivisionServiceImpl implements DivisionService {

    @Autowired
    private DivisionMapper divisionMapper;

    @Autowired
    @Lazy
    private MajorService majorService;

    @Autowired
    @Lazy
    private ClassService classService;

    @Override
    public Division findById(Long id) {
        return divisionMapper.findById(id);
    }

    @Override
    public List<Division> findAll() {
        return divisionMapper.findAll();
    }

    @Override
    public List<Division> findByDepartmentId(Long departmentId) {
        return divisionMapper.findByDepartmentId(departmentId);
    }

    @Override
    public List<Division> findAllWithUserCount() {
        List<Division> divisions = divisionMapper.findAll();

        if (divisions != null && !divisions.isEmpty()) {
            for (Division division : divisions) {
                // 查询学生人数
                Integer studentCount = getStudentCountByDivisionId(division.getId());
                studentCount = studentCount != null ? studentCount : 0;
                division.setStudentCount(studentCount);

                // 查询教师人数
                Integer teacherCount = getTeacherCountByDivisionId(division.getId());
                teacherCount = teacherCount != null ? teacherCount : 0;
                division.setTeacherCount(teacherCount);

                // 查询实习状态统计
                Integer confirmedCount = getConfirmedCountByDivisionId(division.getId());
                division.setConfirmedCount(confirmedCount != null ? confirmedCount : 0);

                Integer notFoundCount = getNotFoundCountByDivisionId(division.getId());
                division.setNotFoundCount(notFoundCount != null ? notFoundCount : 0);

                Integer hasOfferCount = getHasOfferCountByDivisionId(division.getId());
                division.setHasOfferCount(hasOfferCount != null ? hasOfferCount : 0);

                // 更新数据库中的人数信息
                divisionMapper.updateDivisionCount(division.getId(), teacherCount, studentCount);

                // 更新数据库中的实习状态统计信息
                divisionMapper.updateDivisionInternshipCount(
                    division.getId(),
                    division.getConfirmedCount(),
                    division.getNotFoundCount(),
                    division.getHasOfferCount()
                );
            }
        }

        return divisions != null ? divisions : java.util.Collections.emptyList();
    }

    @Override
    public Integer getTeacherCountByDivisionId(Long divisionId) {
        if (divisionId == null || divisionId <= 0) {
            throw new RuntimeException("系ID无效");
        }
        return divisionMapper.getTeacherCountByDivisionId(divisionId);
    }

    @Override
    public Integer getStudentCountByDivisionId(Long divisionId) {
        if (divisionId == null || divisionId <= 0) {
            throw new RuntimeException("系ID无效");
        }
        return divisionMapper.getStudentCountByDivisionId(divisionId);
    }

    @Override
    public Integer getConfirmedCountByDivisionId(Long divisionId) {
        if (divisionId == null || divisionId <= 0) {
            throw new RuntimeException("系ID无效");
        }
        return divisionMapper.getConfirmedCountByDivisionId(divisionId);
    }

    @Override
    public Integer getNotFoundCountByDivisionId(Long divisionId) {
        if (divisionId == null || divisionId <= 0) {
            throw new RuntimeException("系ID无效");
        }
        return divisionMapper.getNotFoundCountByDivisionId(divisionId);
    }

    @Override
    public Integer getHasOfferCountByDivisionId(Long divisionId) {
        if (divisionId == null || divisionId <= 0) {
            throw new RuntimeException("系ID无效");
        }
        return divisionMapper.getHasOfferCountByDivisionId(divisionId);
    }

    @Override
    public int addDivision(Division division) {
        // 检查系名称是否已存在
        List<Division> existingDivisions = findByDepartmentId(division.getDepartmentId());
        if (existingDivisions != null) {
            for (Division existing : existingDivisions) {
                if (existing.getName().equals(division.getName())) {
                    throw new RuntimeException("该学院下已存在同名的系: " + division.getName());
                }
            }
        }

        division.setCreateTime(new Date());
        division.setUpdateTime(new Date());
        return divisionMapper.insert(division);
    }

    @Override
    public int updateDivision(Division division) {
        division.setUpdateTime(new Date());
        return divisionMapper.update(division);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDivision(Long id) {
        log.info("删除系，ID: {}", id);

        if (id == null || id <= 0) {
            throw new RuntimeException("系ID无效");
        }

        Division division = divisionMapper.findById(id);
        if (division == null) {
            throw new RuntimeException("系不存在");
        }

        // 级联删除：系 → 专业 → 班级
        log.info("开始删除系 {} 下的所有专业", id);
        List<Major> majors = majorService.findByDivisionId(id);
        if (majors != null && !majors.isEmpty()) {
            for (Major major : majors) {
                log.info("删除专业: {}", major.getName());
                majorService.delete(major.getId());
            }
        }
        log.info("成功删除系 {} 下的 {} 个专业", id, majors != null ? majors.size() : 0);

        int result = divisionMapper.delete(id);
        log.info("系删除成功，系ID: {}", id);
        return result;
    }
}
