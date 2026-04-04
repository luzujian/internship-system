package com.gdmu.service.impl;

import com.gdmu.entity.InternshipTimeSettings;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.StudentApplication;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.StudentApplicationMapper;
import com.gdmu.service.InternshipTimeSettingsService;
import com.gdmu.service.StudentApplicationService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 学生申请服务实现类
 */
@Slf4j
@Service
public class StudentApplicationServiceImpl implements StudentApplicationService {

    private final StudentApplicationMapper studentApplicationMapper;

    @Autowired
    private InternshipTimeSettingsService internshipTimeSettingsService;

    @Autowired
    private com.gdmu.mapper.StudentInternshipStatusMapper internshipStatusMapper;

    @Autowired
    public StudentApplicationServiceImpl(StudentApplicationMapper studentApplicationMapper) {
        this.studentApplicationMapper = studentApplicationMapper;
    }

    /**
     * 校验延迟实习申请时间是否在截止日期前
     */
    private void validateDelayApplicationDeadline() {
        InternshipTimeSettings settings = internshipTimeSettingsService.findLatest();
        if (settings == null) {
            throw new BusinessException("系统未设置时间节点，请联系管理员");
        }

        LocalDate today = LocalDate.now();

        // 校验延迟申请截止日期
        if (settings.getDelayApplicationDeadline() != null && !settings.getDelayApplicationDeadline().isEmpty()) {
            LocalDate deadline = LocalDate.parse(settings.getDelayApplicationDeadline(), DateTimeFormatter.ISO_LOCAL_DATE);
            if (today.isAfter(deadline)) {
                throw new BusinessException("延迟实习申请已超过截止日期，截止日期为：" + settings.getDelayApplicationDeadline());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(StudentApplication application) {
        log.debug("插入学生申请: {}", application.getStudentName());

        validateApplicationInfo(application);

        // 如果是延迟实习申请，需要校验截止日期
        if ("delay".equals(application.getApplicationType())) {
            validateDelayApplicationDeadline();
        }

        int result = studentApplicationMapper.insert(application);
        log.info("学生申请插入成功，学生: {}", application.getStudentName());
        return result;
    }
    
    @Override
    public StudentApplication findById(Long id) {
        log.debug("根据ID查询学生申请，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        return studentApplicationMapper.findById(id);
    }
    
    @Override
    public List<StudentApplication> findByStudentId(Long studentId) {
        log.debug("根据学生ID查询申请列表，学生ID: {}", studentId);
        return studentApplicationMapper.findByStudentId(studentId);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int update(StudentApplication application) {
        log.debug("更新学生申请，ID: {}", application.getId());
        
        if (application.getId() == null || application.getId() <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        StudentApplication existingApplication = studentApplicationMapper.findById(application.getId());
        if (existingApplication == null) {
            throw new BusinessException("申请不存在");
        }
        
        validateApplicationInfo(application);
        
        int result = studentApplicationMapper.update(application);
        log.info("学生申请更新成功，ID: {}", application.getId());
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除学生申请，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        StudentApplication application = studentApplicationMapper.findById(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }
        
        int result = studentApplicationMapper.deleteById(id);
        log.info("学生申请删除成功，ID: {}", id);
        return result;
    }
    
    @Override
    public List<StudentApplication> findAll() {
        log.debug("查询所有学生申请");
        return studentApplicationMapper.findAll();
    }
    
    @Override
    public List<StudentApplication> list(String applicationType, String status, String studentName, String studentUserId) {
        log.debug("动态条件查询学生申请，类型: {}, 状态: {}, 姓名: {}, 学号: {}", 
                applicationType, status, studentName, studentUserId);
        return studentApplicationMapper.list(applicationType, status, studentName, studentUserId);
    }
    
    @Override
    public List<StudentApplication> findByIds(List<Long> ids) {
        log.debug("根据ID列表查询学生申请，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        
        return studentApplicationMapper.selectByIds(ids);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除学生申请，ID列表: {}", ids);
        
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("申请ID列表不能为空");
        }
        
        int result = studentApplicationMapper.batchDeleteByIds(ids);
        log.info("批量删除学生申请成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Long count() {
        log.debug("查询学生申请总数");
        return studentApplicationMapper.count();
    }
    
    @Override
    public PageResult<StudentApplication> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询学生申请，页码: {}, 每页大小: {}", page, pageSize);
        return findPage(page, pageSize, null, null, null, null);
    }
    
    @Override
    public PageResult<StudentApplication> findPage(Integer page, Integer pageSize, String applicationType, String status, String studentName, String studentUserId) {
        log.debug("分页查询学生申请，页码: {}, 每页大小: {}, 类型: {}, 状态: {}, 姓名: {}, 学号: {}", 
                page, pageSize, applicationType, status, studentName, studentUserId);
        
        PageHelper.startPage(page, pageSize);
        List<StudentApplication> applications = studentApplicationMapper.list(applicationType, status, studentName, studentUserId);
        
        PageInfo<StudentApplication> pageInfo = new PageInfo<>(applications);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public int countByTypeAndStatus(String applicationType, String status) {
        log.debug("根据申请类型和状态查询申请数量，类型: {}, 状态: {}", applicationType, status);
        return studentApplicationMapper.countByTypeAndStatus(applicationType, status);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int approve(Long id, Long reviewerId) {
        log.debug("批准学生申请，ID: {}, 审核人ID: {}", id, reviewerId);

        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }

        StudentApplication application = studentApplicationMapper.findById(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"pending".equals(application.getStatus())) {
            throw new BusinessException("该申请已审核，无法重复操作");
        }

        // 校验审核时限
        validateApprovalTimeLimit(application);

        application.setStatus("approved");
        application.setReviewerId(reviewerId);
        application.setReviewTime(new Date());

        int result = studentApplicationMapper.update(application);

        // 如果是单位变更申请，需要处理学生实习状态
        if ("unitChange".equals(application.getApplicationType())) {
            handleUnitChangeApproval(application);
        }

        log.info("学生申请批准成功，ID: {}", id);
        return result;
    }

    /**
     * 处理单位变更申请批准后的学生状态变更
     */
    private void handleUnitChangeApproval(StudentApplication application) {
        log.debug("处理单位变更申请批准，学生ID: {}", application.getStudentId());

        // 将学生实习状态改为5（已中断），清空公司相关信息
        internshipStatusMapper.updateStatusToInterrupted(application.getStudentId());

        log.info("单位变更申请批准处理完成，学生ID: {}, 状态已更新为已中断", application.getStudentId());
    }

    /**
     * 校验审核时限
     * 如果审核时间超过设置的时限，记录警告日志但仍允许审核
     */
    private void validateApprovalTimeLimit(StudentApplication application) {
        InternshipTimeSettings settings = internshipTimeSettingsService.findLatest();
        if (settings == null || settings.getApprovalTimeLimit() == null) {
            return; // 没有设置时限，不做校验
        }

        Integer limit = settings.getApprovalTimeLimit();
        if (limit <= 0) {
            return; // 无效的时限设置
        }

        Date createTime = application.getCreateTime();
        if (createTime == null) {
            return; // 没有创建时间，无法校验
        }

        // 计算工作日天数（简化计算：直接按自然日计算，实际应扣除周末）
        long daysBetween = (new Date().getTime() - createTime.getTime()) / (1000 * 60 * 60 * 24);

        if (daysBetween > limit) {
            log.warn("审核超过时限！申请ID: {}, 创建时间: {}, 已超过 {} 天（时限 {} 天）",
                    application.getId(), createTime, daysBetween, limit);
            // 注意：这里只记录警告，不阻止审核操作，因为实际业务中可能需要特事特办
        }
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int reject(Long id, Long reviewerId, String rejectReason) {
        log.debug("驳回学生申请，ID: {}, 审核人ID: {}, 驳回理由: {}", id, reviewerId, rejectReason);
        
        if (id == null || id <= 0) {
            throw new BusinessException("申请ID无效");
        }
        
        if (StringUtils.isBlank(rejectReason)) {
            throw new BusinessException("驳回理由不能为空");
        }
        
        StudentApplication application = studentApplicationMapper.findById(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }

        if (!"pending".equals(application.getStatus())) {
            throw new BusinessException("该申请已审核，无法重复操作");
        }

        // 校验审核时限
        validateApprovalTimeLimit(application);

        application.setStatus("rejected");
        application.setReviewerId(reviewerId);
        application.setReviewTime(new Date());
        application.setRejectReason(rejectReason);
        
        int result = studentApplicationMapper.update(application);
        log.info("学生申请驳回成功，ID: {}", id);
        return result;
    }
    
    /**
     * 验证申请信息
     */
    private void validateApplicationInfo(StudentApplication application) {
        if (application == null) {
            throw new BusinessException("申请信息不能为空");
        }
        
        if (application.getStudentId() == null) {
            throw new BusinessException("学生ID不能为空");
        }
        
        if (StringUtils.isBlank(application.getStudentName())) {
            throw new BusinessException("学生姓名不能为空");
        }
        
        if (StringUtils.isBlank(application.getStudentUserId())) {
            throw new BusinessException("学号不能为空");
        }
        
        if (StringUtils.isBlank(application.getApplicationType())) {
            throw new BusinessException("申请类型不能为空");
        }
        
        if (StringUtils.isBlank(application.getReason())) {
            throw new BusinessException("申请理由不能为空");
        }
        
        if (!"selfPractice".equals(application.getApplicationType()) &&
            !"unitChange".equals(application.getApplicationType()) &&
            !"delay".equals(application.getApplicationType())) {
            throw new BusinessException("申请类型无效");
        }
    }

    @Override
    public StudentApplication findLatestUnitChangeByStudentId(Long studentId) {
        log.debug("查询学生最新单位变更申请，学生ID: {}", studentId);
        List<StudentApplication> apps = studentApplicationMapper.findByStudentId(studentId);
        if (apps == null || apps.isEmpty()) {
            return null;
        }
        // 返回最新的unitChange申请
        return apps.stream()
            .filter(a -> "unitChange".equals(a.getApplicationType()))
            .max(Comparator.comparing(StudentApplication::getCreateTime))
            .orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int resubmitUnitChange(Long id, Long studentId, String newCompany, String reason, Map<String, String> materials) {
        log.debug("重新提交单位变更申请，ID: {}, 学生ID: {}", id, studentId);

        StudentApplication application = studentApplicationMapper.findById(id);
        if (application == null) {
            throw new BusinessException("申请不存在");
        }
        if (!studentId.equals(application.getStudentId())) {
            throw new BusinessException("无权操作此申请");
        }
        if (!"rejected".equals(application.getStatus())) {
            throw new BusinessException("只能重新提交被驳回的申请");
        }

        // 更新申请信息
        application.setNewCompany(newCompany);
        application.setReason(reason);
        application.setMaterials(materials);
        application.setStatus("pending");
        application.setApplyTime(new Date());
        application.setRejectReason(null);  // 清空驳回原因
        application.setReviewerId(null);
        application.setReviewTime(null);

        return studentApplicationMapper.update(application);
    }
}
