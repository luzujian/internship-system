package com.internship.service.impl;

import com.internship.entity.Class;
import com.internship.entity.Major;
import com.internship.mapper.MajorMapper;
import com.internship.service.ClassService;
import com.internship.service.MajorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Slf4j
@Service
public class MajorServiceImpl implements MajorService {
    
    @Autowired
    private MajorMapper majorMapper;
    
    @Autowired
    @Lazy
    private ClassService classService;
    
    @Override
    public Major findById(Long id) {
        return majorMapper.findById(id);
    }
    
    @Override
    @Cacheable(value = "majors", key = "'all'", unless = "#result == null")
    public List<Major> findAll() {
        return majorMapper.findAll();
    }
    
    @Override
    public List<Major> findAllWithUserCount() {
        List<Major> majors = majorMapper.findAll();
        
        if (majors != null && !majors.isEmpty()) {
            for (Major major : majors) {
                // 查询学生人数
                Integer studentCount = getStudentCountByMajorId(major.getId());
                studentCount = studentCount != null ? studentCount : 0;
                major.setStudentCount(studentCount);
                
                // 查询实习状态统计
                Integer confirmedCount = getConfirmedCountByMajorId(major.getId());
                major.setConfirmedCount(confirmedCount != null ? confirmedCount : 0);
                
                Integer notFoundCount = getNotFoundCountByMajorId(major.getId());
                major.setNotFoundCount(notFoundCount != null ? notFoundCount : 0);
                
                Integer hasOfferCount = getHasOfferCountByMajorId(major.getId());
                major.setHasOfferCount(hasOfferCount != null ? hasOfferCount : 0);
                
                // 更新数据库中的人数信息（不更新教师人数）
                majorMapper.updateMajorCount(major.getId(), null, studentCount);
                
                // 更新数据库中的实习状态统计信息
                majorMapper.updateMajorInternshipCount(
                    major.getId(), 
                    major.getConfirmedCount(), 
                    major.getNotFoundCount(), 
                    major.getHasOfferCount()
                );
            }
        }
        
        return majors != null ? majors : java.util.Collections.emptyList();
    }
    
    @Override
    public Integer getTeacherCountByMajorId(Long majorId) {
        if (majorId == null || majorId <= 0) {
            throw new RuntimeException("专业ID无效");
        }
        return majorMapper.getTeacherCountByMajorId(majorId);
    }
    
    @Override
    public Integer getStudentCountByMajorId(Long majorId) {
        if (majorId == null || majorId <= 0) {
            throw new RuntimeException("专业ID无效");
        }
        return majorMapper.getStudentCountByMajorId(majorId);
    }
    
    @Override
    public Integer getConfirmedCountByMajorId(Long majorId) {
        if (majorId == null || majorId <= 0) {
            throw new RuntimeException("专业ID无效");
        }
        return majorMapper.getConfirmedCountByMajorId(majorId);
    }
    
    @Override
    public Integer getNotFoundCountByMajorId(Long majorId) {
        if (majorId == null || majorId <= 0) {
            throw new RuntimeException("专业ID无效");
        }
        return majorMapper.getNotFoundCountByMajorId(majorId);
    }
    
    @Override
    public Integer getHasOfferCountByMajorId(Long majorId) {
        if (majorId == null || majorId <= 0) {
            throw new RuntimeException("专业ID无效");
        }
        return majorMapper.getHasOfferCountByMajorId(majorId);
    }
    
    @Override
    public List<Major> findByName(String name) {
        return majorMapper.findByName(name);
    }
    
    @Override
    public List<Major> findByDepartmentId(Long departmentId) {
        return majorMapper.findByDepartmentId(departmentId);
    }

    @Override
    public List<Major> findByDivisionId(Long divisionId) {
        return majorMapper.findByDivisionId(divisionId);
    }

    @Override
    @CacheEvict(value = "majors", key = "'all'")
    public int save(Major major) {
        // 检查专业名称是否已存在
        List<Major> existingMajors = findByName(major.getName());
        if (existingMajors != null && !existingMajors.isEmpty()) {
            // 检查是否在同一院系下
            for (Major existing : existingMajors) {
                if (existing.getDepartmentId().equals(major.getDepartmentId())) {
                    throw new RuntimeException("专业名称已存在: " + major.getName());
                }
            }
        }
        
        major.setCreateTime(new Date());
        major.setUpdateTime(new Date());
        return majorMapper.insert(major);
    }
    
    @Override
    @CacheEvict(value = "majors", key = "'all'")
    public int update(Major major) {
        major.setUpdateTime(new Date());
        return majorMapper.update(major);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "majors", key = "'all'")
    public int delete(Long id) {
        log.info("删除专业，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new RuntimeException("专业ID无效");
        }
        
        Major major = majorMapper.findById(id);
        if (major == null) {
            throw new RuntimeException("专业不存在");
        }

        // 先解除学生对专业的直接引用；班级必须统一交给 ClassService 删除，
        // 这样班级-辅导员关联也会按同一套事务规则清理。
        safeCleanupMajorRelations(id);

        // ===== 级联删除：专业 → 班级。任一班级删除失败都中止整个事务，避免留下半套关联数据。 =====
        log.info("开始删除专业 {} 下的所有班级", id);
        List<Class> classes = classService.findByMajorId(id);
        if (classes != null && !classes.isEmpty()) {
            for (Class classEntity : classes) {
                log.info("删除班级: {}", classEntity.getName());
                classService.delete(classEntity.getId());
            }
        }
        log.info("成功处理专业 {} 下的 {} 个班级", id, classes != null ? classes.size() : 0);
        
        int result = majorMapper.delete(id);
        log.info("专业删除成功，专业ID: {}", id);
        return result;
    }

    /** 清理专业的直接外键引用。班级删除由上面的 ClassService 统一负责。 */
    private void safeCleanupMajorRelations(Long majorId) {
        try {
            majorMapper.clearMajorIdOnStudents(majorId);
        } catch (Exception e) { log.warn("跳过清理 major→student_users.major_id: {}", trimErr(e)); }
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
}
