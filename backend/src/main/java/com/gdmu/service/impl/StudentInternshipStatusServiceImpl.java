package com.gdmu.service.impl;

import com.gdmu.entity.ApplicationWithdrawalRecord;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.StudentInternshipStatusMapper;
import com.gdmu.service.ApplicationWithdrawalRecordService;
import com.gdmu.service.PositionService;
import com.gdmu.service.StudentInternshipStatusService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 学生实习状态服务实现类
 */
@Slf4j
@Service
public class StudentInternshipStatusServiceImpl implements StudentInternshipStatusService {
    
    private final StudentInternshipStatusMapper studentInternshipStatusMapper;
    private final PositionService positionService;
    private final ApplicationWithdrawalRecordService applicationWithdrawalRecordService;

    @Autowired
    public StudentInternshipStatusServiceImpl(StudentInternshipStatusMapper studentInternshipStatusMapper,
                                           PositionService positionService,
                                           ApplicationWithdrawalRecordService applicationWithdrawalRecordService) {
        this.studentInternshipStatusMapper = studentInternshipStatusMapper;
        this.positionService = positionService;
        this.applicationWithdrawalRecordService = applicationWithdrawalRecordService;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(StudentInternshipStatus status) {
        log.debug("插入学生实习状态，学生ID: {}", status.getStudentId());
        
        // 参数校验
        validateStatusInfo(status);
        
        // 设置创建和更新时间
        Date now = new Date();
        status.setCreateTime(now);
        status.setUpdateTime(now);
        
        int result = studentInternshipStatusMapper.insert(status);
        log.info("学生实习状态插入成功，学生ID: {}", status.getStudentId());
        
        // 更新岗位已招人数
        if (status.getPositionId() != null && (status.getStatus() == 1 || status.getStatus() == 2)) {
            try {
                positionService.updateRecruitedCount(status.getPositionId());
                log.debug("已更新岗位已招人数，岗位ID: {}", status.getPositionId());
            } catch (Exception e) {
                log.warn("更新岗位已招人数失败，岗位ID: {}, 错误: {}", status.getPositionId(), e.getMessage());
            }
        }
        
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByPositionId(Long positionId) {
        log.debug("根据岗位ID删除学生实习状态，岗位ID: {}", positionId);

        if (positionId == null || positionId <= 0) {
            throw new BusinessException("岗位ID无效");
        }

        int result = studentInternshipStatusMapper.deleteByPositionId(positionId);
        log.info("根据岗位ID删除学生实习状态成功，岗位ID: {}, 删除记录数: {}", positionId, result);
        return result;
    }
    
    @Override
    public StudentInternshipStatus findById(Long id) {
        log.debug("根据ID查询学生实习状态，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        return studentInternshipStatusMapper.findById(id);
    }
    
    @Override
    public StudentInternshipStatus findByStudentId(Long studentId) {
        log.debug("根据学生ID查询实习状态，学生ID: {}", studentId);
        
        // 参数校验
        if (studentId == null || studentId <= 0) {
            throw new BusinessException("学生ID无效");
        }
        
        return studentInternshipStatusMapper.findByStudentId(studentId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(StudentInternshipStatus status) {
        log.debug("更新学生实习状态，ID: {}", status.getId());
        
        // 参数校验
        if (status.getId() == null || status.getId() <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        // 检查状态是否存在
        StudentInternshipStatus existingStatus = studentInternshipStatusMapper.findById(status.getId());
        if (existingStatus == null) {
            throw new BusinessException("学生实习状态不存在");
        }
        
        // 设置更新时间
        status.setUpdateTime(new Date());
        
        int result = studentInternshipStatusMapper.update(status);
        log.info("学生实习状态更新成功，学生ID: {}", status.getStudentId());
        
        // 更新岗位已招人数
        updatePositionRecruitedCount(existingStatus, status);
        
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除学生实习状态，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        // 检查状态是否存在
        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("学生实习状态不存在");
        }
        
        Long positionId = status.getPositionId();
        Integer oldStatus = status.getStatus();
        
        int result = studentInternshipStatusMapper.deleteById(id);
        log.info("学生实习状态删除成功，学生ID: {}", status.getStudentId());
        
        // 更新岗位已招人数（如果之前的状态是1或2）
        if (positionId != null && (oldStatus == 1 || oldStatus == 2)) {
            try {
                positionService.updateRecruitedCount(positionId);
                log.debug("已更新岗位已招人数，岗位ID: {}", positionId);
            } catch (Exception e) {
                log.warn("更新岗位已招人数失败，岗位ID: {}, 错误: {}", positionId, e.getMessage());
            }
        }
        
        return result;
    }
    
    @Override
    public List<StudentInternshipStatus> findAll() {
        log.debug("查询所有学生实习状态");
        return studentInternshipStatusMapper.findAll();
    }
    
    @Override
    public List<StudentInternshipStatus> list(Long studentId, String name, Integer gender, Integer status, Long companyId, String companyName) {
        log.debug("动态条件查询学生实习状态，学生ID: {}, 学生姓名: {}, 性别: {}, 状态: {}, 企业ID: {}, 企业名称: {}", 
                studentId, name, gender, status, companyId, companyName);
        return studentInternshipStatusMapper.list(studentId, name, gender, status, companyId, companyName);
    }
    
    @Override
    public List<StudentInternshipStatus> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询学生实习状态，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return studentInternshipStatusMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除学生实习状态，ID列表: {}", ids);
        
        // 参数校验
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("状态ID列表不能为空");
        }
        
        int result = studentInternshipStatusMapper.batchDeleteByIds(ids);
        log.info("批量删除学生实习状态成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询学生实习状态总数");
        return studentInternshipStatusMapper.count();
    }
    
    @Override
    public PageResult<StudentInternshipStatus> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询学生实习状态，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null, null, null, null, null);
    }
    
    @Override
    public PageResult<StudentInternshipStatus> findPage(Integer page, Integer pageSize, Long studentId, String name, Integer gender, Integer status, Long companyId, String companyName) {
        log.debug("分页查询学生实习状态，页码: {}, 每页大小: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 状态: {}, 企业ID: {}, 企业名称: {}", 
                page, pageSize, studentId, name, gender, status, companyId, companyName);
        
        // 使用PageHelper进行分页查询
        PageHelper.startPage(page, pageSize);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.list(studentId, name, gender, status, companyId, companyName);
        
        // 构建分页结果
        PageInfo<StudentInternshipStatus> pageInfo = new PageInfo<>(statuses);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    /**
     * 验证学生实习状态信息
     */
    private void validateStatusInfo(StudentInternshipStatus status) {
        if (status == null) {
            throw new BusinessException("学生实习状态信息不能为空");
        }
        
        if (status.getStudentId() == null || status.getStudentId() <= 0) {
            throw new BusinessException("学生ID不能为空");
        }
        
        if (status.getStatus() == null || status.getStatus() < 0 || status.getStatus() > 2) {
            throw new BusinessException("实习状态值无效");
        }
    }

    @Override
    public PageResult<StudentInternshipStatus> findPendingRecallAuditPage(Integer page, Integer pageSize, Long studentId, String name, Integer gender, Long companyId, String companyName) {
        log.debug("分页查询待审核撤回申请，页码: {}, 每页大小: {}, 学生ID: {}, 学生姓名: {}, 性别: {}, 企业ID: {}, 企业名称: {}", 
                page, pageSize, studentId, name, gender, companyId, companyName);
        
        PageHelper.startPage(page, pageSize);
        List<StudentInternshipStatus> statuses = studentInternshipStatusMapper.findPendingRecallAuditList(studentId, name, gender, companyId, companyName);
        
        PageInfo<StudentInternshipStatus> pageInfo = new PageInfo<>(statuses);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public Integer countByRecallStatus(Integer recallStatus) {
        log.debug("根据撤回状态统计数量，撤回状态: {}", recallStatus);
        return studentInternshipStatusMapper.countByRecallStatus(recallStatus);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int auditRecallApplication(Long id, Integer recallStatus, String recallAuditRemark, Long reviewerId) {
        log.debug("审核撤回申请，ID: {}, 撤回状态: {}, 审核备注: {}, 审核人ID: {}", id, recallStatus, recallAuditRemark, reviewerId);
        
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        if (recallStatus == null || (recallStatus != 2 && recallStatus != 3)) {
            throw new BusinessException("撤回审核状态无效");
        }
        
        if (reviewerId == null || reviewerId <= 0) {
            throw new BusinessException("审核人ID无效");
        }
        
        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("实习状态不存在");
        }
        
        if (status.getRecallStatus() == null || status.getRecallStatus() != 1) {
            throw new BusinessException("该记录不在待审核撤回状态");
        }
        
        int result = studentInternshipStatusMapper.auditRecallApplication(id, recallStatus, recallAuditRemark, reviewerId);
        
        if (result > 0 && recallStatus == 2) {
            StudentInternshipStatus updateStatus = new StudentInternshipStatus();
            updateStatus.setId(id);
            updateStatus.setStatus(1);
            updateStatus.setRecallStatus(2);
            studentInternshipStatusMapper.update(updateStatus);
            log.info("撤回申请已批准，实习状态已退回到有Offer状态，ID: {}", id);
        }
        
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitRecallApplication(Long id, String recallReason) {
        log.debug("提交撤回申请，ID: {}, 撤回原因: {}", id, recallReason);
        
        if (id == null || id <= 0) {
            throw new BusinessException("状态ID无效");
        }
        
        if (recallReason == null || recallReason.trim().isEmpty()) {
            throw new BusinessException("撤回原因不能为空");
        }
        
        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("实习状态不存在");
        }
        
        if (status.getStatus() == null || status.getStatus() != 2) {
            throw new BusinessException("只有已确定的实习才能申请撤回");
        }
        
        if (status.getRecallStatus() != null && status.getRecallStatus() != 0) {
            throw new BusinessException("该记录已有撤回申请");
        }
        
        int result = studentInternshipStatusMapper.submitRecallApplication(id, recallReason);
        log.info("撤回申请提交成功，ID: {}", id);
        return result;
    }

    /**
     * 更新岗位已招人数
     * 当学生实习状态变化时，更新相关岗位的已招人数
     */
    private void updatePositionRecruitedCount(StudentInternshipStatus oldStatus, StudentInternshipStatus newStatus) {
        Long oldPositionId = oldStatus != null ? oldStatus.getPositionId() : null;
        Long newPositionId = newStatus != null ? newStatus.getPositionId() : null;
        
        // 如果岗位ID发生变化，需要更新两个岗位
        if (oldPositionId != null && !oldPositionId.equals(newPositionId)) {
            if (oldPositionId != null) {
                try {
                    positionService.updateRecruitedCount(oldPositionId);
                    log.debug("已更新旧岗位已招人数，岗位ID: {}", oldPositionId);
                } catch (Exception e) {
                    log.warn("更新旧岗位已招人数失败，岗位ID: {}, 错误: {}", oldPositionId, e.getMessage());
                }
            }
            if (newPositionId != null) {
                try {
                    positionService.updateRecruitedCount(newPositionId);
                    log.debug("已更新新岗位已招人数，岗位ID: {}", newPositionId);
                } catch (Exception e) {
                    log.warn("更新新岗位已招人数失败，岗位ID: {}, 错误: {}", newPositionId, e.getMessage());
                }
            }
        } else if (oldPositionId != null) {
            // 如果岗位ID没有变化，但状态可能变化了，也需要更新
            try {
                positionService.updateRecruitedCount(oldPositionId);
                log.debug("已更新岗位已招人数，岗位ID: {}", oldPositionId);
            } catch (Exception e) {
                log.warn("更新岗位已招人数失败，岗位ID: {}, 错误: {}", oldPositionId, e.getMessage());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int clearRecallData() {
        log.info("开始清除学生实习撤回申请数据");

        try {
            int count = studentInternshipStatusMapper.clearRecallData();
            log.info("清除学生实习撤回申请数据成功，共清除{}条记录", count);
            return count;
        } catch (Exception e) {
            log.error("清除学生实习撤回申请数据失败: {}", e.getMessage(), e);
            throw new BusinessException("清除撤回申请数据失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int submitRecallApplicationWithRecord(Long id, String recallReason, Long applicantId, String applicantRole) {
        log.debug("提交撤回申请（自动撤回），ID: {}, 撤回原因：{}, 申请人 ID: {}, 申请人身份：{}", id, recallReason, applicantId, applicantRole);

        if (id == null || id <= 0) {
            throw new BusinessException("状态 ID 无效");
        }

        if (recallReason == null || recallReason.trim().isEmpty()) {
            throw new BusinessException("撤回原因不能为空");
        }

        if (applicantId == null || applicantId <= 0) {
            throw new BusinessException("申请人 ID 无效");
        }

        if (applicantRole == null || applicantRole.isEmpty()) {
            throw new BusinessException("申请人身份不能为空");
        }

        StudentInternshipStatus status = studentInternshipStatusMapper.findById(id);
        if (status == null) {
            throw new BusinessException("实习状态不存在");
        }

        if (status.getStatus() == null || status.getStatus() != 2) {
            throw new BusinessException("只有已确定的实习才能申请撤回");
        }

        if (status.getRecallStatus() != null && status.getRecallStatus() != 0) {
            throw new BusinessException("该记录已有撤回申请");
        }

        // 1. 更新实习状态为已撤回
        int result = studentInternshipStatusMapper.submitRecallApplication(id, recallReason);

        // 2. 创建撤回记录
        ApplicationWithdrawalRecord record = new ApplicationWithdrawalRecord();
        record.setStatusId(id);
        record.setApplicantId(applicantId);
        record.setApplicantRole(applicantRole);
        record.setWithdrawalReason(recallReason);
        record.setWithdrawalTime(new Date());
        applicationWithdrawalRecordService.insert(record);

        log.info("自动撤回申请成功，ID: {}, 已创建撤回记录", id);
        return result;
    }
}