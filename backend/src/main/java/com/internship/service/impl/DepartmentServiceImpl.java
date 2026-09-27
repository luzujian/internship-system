package com.internship.service.impl;

import com.internship.mapper.DepartmentMapper;
import com.internship.entity.Department;
import com.internship.entity.Division;
import com.internship.entity.Major;
import com.internship.service.DepartmentService;
import com.internship.service.DivisionService;
import com.internship.service.MajorService;
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

    @Autowired
    @Lazy
    private DivisionService divisionService;

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

        // ===== 先做直接反向引用清理（teacher_users.department_id 等在 Service 循环前解绑，避免外键炸） =====
        safeCleanupDepartmentRelations(id);

        // ===== 级联删除：院系 → 系 → 专业 → 班级 → 学生 =====
        log.info("开始删除院系 {} 下的所有系", id);
        List<Division> divisions = divisionService.findByDepartmentId(id);
        if (divisions != null && !divisions.isEmpty()) {
            for (Division division : divisions) {
                try {
                    log.info("删除系: {}", division.getName());
                    divisionService.deleteDivision(division.getId());
                } catch (Exception e) {
                    // 单个系删除失败不阻塞整个院系删除，跳过只打 WARN
                    log.warn("删除院系下的系时跳过（divisionId={}, name={}）: {}",
                            division.getId(), division.getName(), trimErr(e));
                }
            }
        }
        log.info("成功处理院系 {} 下的 {} 个系（含跳过失败项）", id, divisions != null ? divisions.size() : 0);

        // ===== 最后删院系本身 =====
        int result = departmentMapper.deleteById(id);
        log.info("院系删除成功，院系ID: {}", id);
        return result;
    }

    /**
     * 安全清理院系自身的反向引用（department→外键表：teacher_users.department_id）。
     * 每条独立 try-catch，表不存在时跳过，保证后续级联能执行。
     */
    private void safeCleanupDepartmentRelations(Long departmentId) {
        // 教师用户 teacher_users.department_id 置 NULL（不删老师本人，只解绑），否则删院系会因 teacher 外键失败
        try {
            departmentMapper.clearDepartmentIdOnTeachers(departmentId);
        } catch (Exception e) { log.warn("跳过清理 department→teacher_users.department_id: {}", trimErr(e)); }

        // division.department_id 直接删（正常情况下 DepartmentServiceImpl 本身会循环 divisionService.deleteDivision，
        // 但这里多做一层保险——如果前面 division 级联里有任何异常跳过，最后还能靠物理 DELETE 收尾）
        try {
            departmentMapper.deleteDivisionsByDepartmentId(departmentId);
        } catch (Exception e) { log.warn("跳过清理 department→division: {}", trimErr(e)); }

        log.debug("院系直接关联外键清理完成（已跳过不存在的表），院系ID: {}", departmentId);
    }

    /** 提取精简错误信息，避免超长 SQL 堆栈刷屏 */
    private String trimErr(Throwable e) {
        Throwable c = e;
        String lastMsg = (e.getMessage() == null ? "" : e.getMessage().split(";")[0].trim());
        int depth = 0;
        while (c.getCause() != null && depth < 5) {
            c = c.getCause();
            if (c.getMessage() != null) {
                String m = c.getMessage().split(";")[0].trim();
                if (m.length() < 160) lastMsg = m;
            }
            depth++;
        }
        return lastMsg.length() > 160 ? lastMsg.substring(0, 160) : lastMsg;
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