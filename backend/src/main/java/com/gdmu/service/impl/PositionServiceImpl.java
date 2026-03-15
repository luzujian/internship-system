package com.gdmu.service.impl;

import com.gdmu.entity.PageResult;
import com.gdmu.entity.Position;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.PositionMapper;
import com.gdmu.service.PositionService;
import com.gdmu.service.StudentInternshipStatusService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 岗位服务实现类
 */
@Slf4j
@Service
public class PositionServiceImpl implements PositionService {
    
    private final PositionMapper positionMapper;
    private final StudentInternshipStatusService studentInternshipStatusService;

    @Autowired
    public PositionServiceImpl(PositionMapper positionMapper,
                               @Lazy StudentInternshipStatusService studentInternshipStatusService) {
        this.positionMapper = positionMapper;
        this.studentInternshipStatusService = studentInternshipStatusService;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(Position position) {
        log.debug("插入岗位信息: {}", position.getPositionName());
        
        // 参数校验
        validatePositionInfo(position);
        
        // 设置创建和更新时间
        Date now = new Date();
        position.setCreateTime(now);
        position.setUpdateTime(now);
        
        int result = positionMapper.insert(position);
        log.info("岗位信息插入成功，岗位名称: {}", position.getPositionName());
        return result;
    }
    
    @Override
    public Position findById(Long id) {
        log.debug("根据ID查询岗位信息，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("岗位ID无效");
        }
        
        return positionMapper.findById(id);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(Position position) {
        log.debug("更新岗位信息，ID: {}", position.getId());
        
        // 参数校验
        if (position.getId() == null || position.getId() <= 0) {
            throw new BusinessException("岗位ID无效");
        }
        
        // 检查岗位是否存在
        Position existingPosition = positionMapper.findById(position.getId());
        if (existingPosition == null) {
            throw new BusinessException("岗位不存在");
        }
        
        // 设置更新时间
        position.setUpdateTime(new Date());
        
        int result = positionMapper.update(position);
        log.info("岗位信息更新成功，岗位名称: {}", position.getPositionName());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除岗位信息，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("岗位ID无效");
        }
        
        // 检查岗位是否存在
        Position position = positionMapper.findById(id);
        if (position == null) {
            throw new BusinessException("岗位不存在");
        }
        
        // 先删除该岗位下的所有实习确认表
        try {
            int deletedCount = studentInternshipStatusService.deleteByPositionId(id);
            log.info("已删除岗位关联的实习确认表，岗位ID: {}, 删除记录数: {}", id, deletedCount);
        } catch (Exception e) {
            log.error("删除岗位关联的实习确认表失败，岗位ID: {}, 错误: {}", id, e.getMessage());
            throw new BusinessException("删除岗位关联的实习确认表失败: " + e.getMessage());
        }
        
        // 删除岗位
        int result = positionMapper.deleteById(id);
        log.info("岗位信息删除成功，岗位名称: {}", position.getPositionName());
        return result;
    }
    
    @Override
    public List<Position> findAll() {
        log.debug("查询所有岗位信息");
        return positionMapper.findAll();
    }
    
    @Override
    public List<Position> findByCompanyId(Long companyId) {
        log.debug("根据企业ID查询岗位信息，企业ID: {}", companyId);
        
        // 参数校验
        if (companyId == null || companyId <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        return positionMapper.findByCompanyId(companyId);
    }

    @Override
    public List<Position> findByCompanyIdWithConditions(Long companyId, String positionName, String department, String status) {
        log.debug("根据企业ID和条件查询岗位信息，企业ID: {}, 岗位名称: {}, 部门: {}, 状态: {}", companyId, positionName, department, status);
        
        // 参数校验
        if (companyId == null || companyId <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        return positionMapper.findByCompanyIdWithConditions(companyId, positionName, department, status);
    }
    
    @Override
    public List<Position> list(Long companyId, String positionName) {
        log.debug("动态条件查询岗位信息，企业ID: {}, 岗位名称: {}", companyId, positionName);
        return positionMapper.list(companyId, positionName);
    }
    
    @Override
    public List<Position> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询岗位信息，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return positionMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除岗位信息，ID列表: {}", ids);
        
        // 参数校验
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("岗位ID列表不能为空");
        }
        
        int result = positionMapper.batchDeleteByIds(ids);
        log.info("批量删除岗位信息成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询岗位总数");
        return positionMapper.count();
    }
    
    @Override
    public PageResult<Position> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询岗位信息，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, (Long) null, (String) null);
    }
    
    @Override
    public PageResult<Position> findPage(Integer page, Integer pageSize, Long companyId, String positionName) {
        log.debug("分页查询岗位信息，页码: {}, 每页大小: {}, 企业ID: {}, 岗位名称: {}", 
                page, pageSize, companyId, positionName);
        
        // 使用PageHelper进行分页查询
        PageHelper.startPage(page, pageSize);
        List<Position> positions = positionMapper.list(companyId, positionName);
        
        // 构建分页结果
        PageInfo<Position> pageInfo = new PageInfo<>(positions);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<Map<String, Object>> findPage(Integer page, Integer pageSize, String companyName, String positionName) {
        log.debug("分页查询岗位信息（带企业名称），页码: {}, 每页大小: {}, 企业名称: {}, 岗位名称: {}",
                page, pageSize, companyName, positionName);
        
        PageHelper.startPage(page, pageSize);
        List<Map<String, Object>> positions = positionMapper.findPageWithCompany(companyName, positionName);
        
        PageInfo<Map<String, Object>> pageInfo = new PageInfo<>(positions);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public Map<String, Object> getStatistics() {
        log.debug("获取岗位统计数据");
        return positionMapper.getStatistics();
    }

    @Override
    public List<Map<String, Object>> getRecruitedStudents(Long positionId) {
        log.debug("获取岗位已招学生，岗位ID: {}", positionId);
        if (positionId == null || positionId <= 0) {
            return Collections.emptyList();
        }
        return positionMapper.getRecruitedStudents(positionId);
    }

    @Override
    public PageResult<Position> findPageByCompanyId(Long companyId, Integer page, Integer pageSize) {
        log.debug("根据企业ID分页查询岗位，企业ID: {}, 页码: {}, 每页大小: {}", companyId, page, pageSize);
        
        if (companyId == null || companyId <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        PageHelper.startPage(page, pageSize);
        List<Position> positions = positionMapper.findByCompanyId(companyId);
        
        PageInfo<Position> pageInfo = new PageInfo<>(positions);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public Long countByCompanyId(Long companyId) {
        log.debug("根据企业ID统计岗位数量，企业ID: {}", companyId);
        
        if (companyId == null || companyId <= 0) {
            throw new BusinessException("企业ID无效");
        }
        
        List<Position> positions = positionMapper.findByCompanyId(companyId);
        return (long) positions.size();
    }
    
    /**
     * 验证岗位信息
     */
    private void validatePositionInfo(Position position) {
        if (position == null) {
            throw new BusinessException("岗位信息不能为空");
        }
        
        if (StringUtils.isBlank(position.getPositionName())) {
            throw new BusinessException("岗位名称不能为空");
        }
        
        if (position.getPlannedRecruit() != null && position.getPlannedRecruit() < 0) {
            throw new BusinessException("计划招聘人数不能为负数");
        }
        
        if (position.getRecruitedCount() != null && position.getRecruitedCount() < 0) {
            throw new BusinessException("已招人数不能为负数");
        }
    }

    @Override
    public int updateRecruitedCount(Long positionId) {
        log.debug("更新岗位已招人数，岗位ID: {}", positionId);
        
        if (positionId == null || positionId <= 0) {
            throw new BusinessException("岗位ID无效");
        }
        
        int result = positionMapper.updateRecruitedCount(positionId);
        log.info("岗位已招人数更新成功，岗位ID: {}, 影响行数: {}", positionId, result);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearAll() {
        log.info("开始清除所有岗位数据");

        try {
            int count = positionMapper.clearAll();
            log.info("清除所有岗位数据成功，共清除{}条记录", count);
            return count;
        } catch (Exception e) {
            log.error("清除所有岗位数据失败: {}", e.getMessage(), e);
            throw new BusinessException("清除岗位数据失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int pausePosition(Long positionId) {
        log.info("暂停岗位招聘，岗位 ID: {}", positionId);

        if (positionId == null || positionId <= 0) {
            throw new BusinessException("岗位 ID 无效");
        }

        Position position = positionMapper.findById(positionId);
        if (position == null) {
            throw new BusinessException("岗位不存在");
        }

        position.setStatus("paused");
        position.setUpdateTime(new Date());

        int result = positionMapper.update(position);
        log.info("岗位招聘已暂停，岗位 ID: {}, 岗位名称：{}", positionId, position.getPositionName());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resumePosition(Long positionId) {
        log.info("恢复岗位招聘，岗位 ID: {}", positionId);

        if (positionId == null || positionId <= 0) {
            throw new BusinessException("岗位 ID 无效");
        }

        Position position = positionMapper.findById(positionId);
        if (position == null) {
            throw new BusinessException("岗位不存在");
        }

        position.setStatus("active");
        position.setUpdateTime(new Date());

        int result = positionMapper.update(position);
        log.info("岗位招聘已恢复，岗位 ID: {}, 岗位名称：{}", positionId, position.getPositionName());
        return result;
    }
}