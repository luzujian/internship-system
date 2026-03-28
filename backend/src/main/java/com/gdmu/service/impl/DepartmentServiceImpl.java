package com.gdmu.service.impl;

import com.gdmu.mapper.DepartmentMapper;
import com.gdmu.entity.Department;
import com.gdmu.entity.Major;
import com.gdmu.service.DepartmentService;
import com.gdmu.service.MajorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 院系服务实现类
 */
@Slf4j
@Service
public class DepartmentServiceImpl implements DepartmentService {

    @Autowired
    private DepartmentMapper departmentMapper;
    
    @Autowired
    @Lazy
    private MajorService majorService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int addDepartment(Department department) {
        log.debug("添加院系: {}", department.getName());
        // 参数校验
        validateDepartment(department);
        
        // 检查院系名称是否已存在
        List<Department> existingDepartments = findByName(department.getName());
        if (existingDepartments != null && !existingDepartments.isEmpty()) {
            throw new RuntimeException("院系名称已存在: " + department.getName());
        }
        
        department.setCreateTime(new Date());
        department.setUpdateTime(new Date());
        
        int result = departmentMapper.insert(department);
        log.info("院系添加成功，名称: {}", department.getName());
        return result;
    }
    
    @Override
    public List<Department> findAllWithUserCount() {
        log.debug("查询所有带有人数信息的院系");
        List<Department> departments = departmentMapper.findAll();
        
        if (departments != null && !departments.isEmpty()) {
            for (Department department : departments) {
                // 查询教师人数
                Integer teacherCount = getTeacherCountByDepartmentId(department.getId());
                teacherCount = teacherCount != null ? teacherCount : 0;
                department.setTeacherCount(teacherCount);
                
                // 查询学生人数
                Integer studentCount = getStudentCountByDepartmentId(department.getId());
                studentCount = studentCount != null ? studentCount : 0;
                department.setStudentCount(studentCount);
                
                // 查询实习状态统计
                Integer confirmedCount = getConfirmedCountByDepartmentId(department.getId());
                department.setConfirmedCount(confirmedCount != null ? confirmedCount : 0);
                
                Integer notFoundCount = getNotFoundCountByDepartmentId(department.getId());
                department.setNotFoundCount(notFoundCount != null ? notFoundCount : 0);
                
                Integer hasOfferCount = getHasOfferCountByDepartmentId(department.getId());
                department.setHasOfferCount(hasOfferCount != null ? hasOfferCount : 0);
                
                // 更新数据库中的人数信息
                departmentMapper.updateDepartmentCount(department.getId(), teacherCount, studentCount);
                
                // 更新数据库中的实习状态统计信息
                departmentMapper.updateDepartmentInternshipCount(
                    department.getId(), 
                    department.getConfirmedCount(), 
                    department.getNotFoundCount(), 
                    department.getHasOfferCount()
                );
                
                log.debug("院系ID: {}, 名称: {}, 教师人数: {}, 学生人数: {}, 已确定实习: {}, 未找到实习: {}, 有Offer未确定: {}", 
                         department.getId(), department.getName(), 
                         department.getTeacherCount(), department.getStudentCount(),
                         department.getConfirmedCount(), department.getNotFoundCount(), department.getHasOfferCount());
            }
        }
        
        return departments != null ? departments : Collections.emptyList();
    }
    
    @Override
    public Integer getTeacherCountByDepartmentId(Long departmentId) {
        log.debug("获取院系ID: {} 的教师人数", departmentId);
        if (departmentId == null || departmentId <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        return departmentMapper.getTeacherCountByDepartmentId(departmentId);
    }
    
    @Override
    public Integer getStudentCountByDepartmentId(Long departmentId) {
        log.debug("获取院系ID: {} 的学生人数", departmentId);
        if (departmentId == null || departmentId <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        return departmentMapper.getStudentCountByDepartmentId(departmentId);
    }
    
    @Override
    public Integer getConfirmedCountByDepartmentId(Long departmentId) {
        log.debug("获取院系ID: {} 的已确定实习学生数", departmentId);
        if (departmentId == null || departmentId <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        return departmentMapper.getConfirmedCountByDepartmentId(departmentId);
    }
    
    @Override
    public Integer getNotFoundCountByDepartmentId(Long departmentId) {
        log.debug("获取院系ID: {} 的未找到实习学生数", departmentId);
        if (departmentId == null || departmentId <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        return departmentMapper.getNotFoundCountByDepartmentId(departmentId);
    }
    
    @Override
    public Integer getHasOfferCountByDepartmentId(Long departmentId) {
        log.debug("获取院系ID: {} 的有Offer未确定学生数", departmentId);
        if (departmentId == null || departmentId <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        return departmentMapper.getHasOfferCountByDepartmentId(departmentId);
    }

    @Override
    public Department findById(Long id) {
        log.debug("查找院系，ID: {}", id);
        if (id == null || id <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        return departmentMapper.findById(id);
    }

    @Override
    public List<Department> findAll() {
        log.debug("查询所有院系");
        List<Department> departments = departmentMapper.findAll();
        return departments != null ? departments : Collections.emptyList();
    }
    
    @Override
    public List<Department> findByName(String name) {
        log.debug("按名称模糊查询院系，名称: {}", name);
        Map<String, Object> params = new HashMap<>();
        params.put("name", name);
        List<Department> departments = departmentMapper.list(params);
        return departments != null ? departments : Collections.emptyList();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateDepartment(Department department) {
        log.debug("更新院系信息，院系ID: {}", department.getId());
        
        // 参数校验
        if (department.getId() == null || department.getId() <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        
        validateDepartment(department);
        
        // 检查院系是否存在
        Department existingDepartment = departmentMapper.findById(department.getId() != null ? department.getId().longValue() : null);
        if (existingDepartment == null) {
            throw new RuntimeException("院系不存在");
        }
        
        department.setUpdateTime(new Date());
        
        int result = departmentMapper.update(department);
        log.info("院系信息更新成功，院系ID: {}", department.getId());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int deleteDepartment(Long id) {
        log.debug("删除院系，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new RuntimeException("院系ID无效");
        }
        
        // 检查院系是否存在
        Department department = departmentMapper.findById(id);
        if (department == null) {
            throw new RuntimeException("院系不存在");
        }
        
        log.info("开始删除院系 {} 下的所有专业", id);
        List<Major> majors = majorService.findByDepartmentId(id);
        if (majors != null && !majors.isEmpty()) {
            for (Major major : majors) {
                log.info("删除专业: {}", major.getName());
                majorService.delete(major.getId());
            }
        }
        log.info("成功删除院系 {} 下的 {} 个专业", id, majors != null ? majors.size() : 0);
        
        int result = departmentMapper.deleteById(id);
        log.info("院系删除成功，院系ID: {}", id);
        return result;
    }

    @Override
    public Long count() {
        log.debug("查询院系总数");
        return departmentMapper.count();
    }
    
    // 验证院系信息
    private void validateDepartment(Department department) {
        if (department == null) {
            throw new RuntimeException("院系信息不能为空");
        }
        
        if (!StringUtils.hasLength(department.getName())) {
            throw new RuntimeException("院系名称不能为空");
        }
        
        // 院系名称长度限制
        if (department.getName().length() > 50) {
            throw new RuntimeException("院系名称不能超过50个字符");
        }
    }
}