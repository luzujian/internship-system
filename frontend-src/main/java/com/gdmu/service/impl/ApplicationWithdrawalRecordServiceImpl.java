package com.gdmu.service.impl;

import com.gdmu.entity.ApplicationWithdrawalRecord;
import com.gdmu.entity.PageResult;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.ApplicationWithdrawalRecordMapper;
import com.gdmu.service.ApplicationWithdrawalRecordService;
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
 * 撤回申请记录服务实现类
 */
@Slf4j
@Service
public class ApplicationWithdrawalRecordServiceImpl implements ApplicationWithdrawalRecordService {

    @Autowired
    private ApplicationWithdrawalRecordMapper applicationWithdrawalRecordMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int insert(ApplicationWithdrawalRecord record) {
        log.debug("插入撤回申请记录，状态 ID: {}, 申请人 ID: {}", record.getStatusId(), record.getApplicantId());

        if (record == null) {
            throw new BusinessException("撤回申请记录不能为空");
        }

        if (record.getStatusId() == null || record.getStatusId() <= 0) {
            throw new BusinessException("状态 ID 无效");
        }

        if (record.getApplicantId() == null || record.getApplicantId() <= 0) {
            throw new BusinessException("申请人 ID 无效");
        }

        if (record.getApplicantRole() == null || record.getApplicantRole().isEmpty()) {
            throw new BusinessException("申请人身份不能为空");
        }

        if (record.getWithdrawalReason() == null || record.getWithdrawalReason().isEmpty()) {
            throw new BusinessException("撤回原因不能为空");
        }

        if (record.getWithdrawalTime() == null) {
            record.setWithdrawalTime(new Date());
        }

        int result = applicationWithdrawalRecordMapper.insert(record);
        log.info("撤回申请记录插入成功，状态 ID: {}", record.getStatusId());

        return result;
    }

    @Override
    public ApplicationWithdrawalRecord findById(Long id) {
        log.debug("根据 ID 查询撤回申请记录，ID: {}", id);

        if (id == null || id <= 0) {
            throw new BusinessException("记录 ID 无效");
        }

        return applicationWithdrawalRecordMapper.findById(id);
    }

    @Override
    public ApplicationWithdrawalRecord findByStatusId(Long statusId) {
        log.debug("根据状态 ID 查询撤回申请记录，状态 ID: {}", statusId);

        if (statusId == null || statusId <= 0) {
            throw new BusinessException("状态 ID 无效");
        }

        return applicationWithdrawalRecordMapper.findByStatusId(statusId);
    }

    @Override
    public List<ApplicationWithdrawalRecord> findAll() {
        log.debug("查询所有撤回申请记录");
        return applicationWithdrawalRecordMapper.findAll();
    }

    @Override
    public List<ApplicationWithdrawalRecord> list(Long statusId, Long applicantId, String applicantRole) {
        log.debug("动态条件查询撤回申请记录，状态 ID: {}, 申请人 ID: {}, 申请人身份：{}", statusId, applicantId, applicantRole);
        return applicationWithdrawalRecordMapper.list(statusId, applicantId, applicantRole);
    }

    @Override
    public List<ApplicationWithdrawalRecord> findByIds(List<Long> ids) {
        log.debug("根据 ID 列表查询撤回申请记录，ID 列表：{}", ids);

        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        return applicationWithdrawalRecordMapper.selectByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        log.debug("删除撤回申请记录，ID: {}", id);

        if (id == null || id <= 0) {
            throw new BusinessException("记录 ID 无效");
        }

        ApplicationWithdrawalRecord record = applicationWithdrawalRecordMapper.findById(id);
        if (record == null) {
            throw new BusinessException("撤回申请记录不存在");
        }

        int result = applicationWithdrawalRecordMapper.deleteById(id);
        log.info("撤回申请记录删除成功，ID: {}", id);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除撤回申请记录，ID 列表：{}", ids);

        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("记录 ID 列表不能为空");
        }

        int result = applicationWithdrawalRecordMapper.batchDeleteByIds(ids);
        log.info("批量删除撤回申请记录成功，删除数量：{}", result);

        return result;
    }

    @Override
    public Long count() {
        log.debug("统计撤回申请记录总数");
        return applicationWithdrawalRecordMapper.count();
    }

    @Override
    public PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询撤回申请记录，页码：{}, 每页大小：{}", page, pageSize);
        return findPage(page, pageSize, null, null, null);
    }

    @Override
    public PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize, Long statusId, Long applicantId, String applicantRole) {
        return findPage(page, pageSize, statusId, applicantId, applicantRole, null);
    }

    @Override
    public PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize, Long statusId, Long applicantId, String applicantRole, String applicantName) {
        return findPage(page, pageSize, statusId, applicantId, applicantRole, applicantName, null, null);
    }

    @Override
    public PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize, Long statusId, Long applicantId, String applicantRole, String applicantName, String startTime, String endTime) {
        log.debug("分页查询撤回申请记录，页码：{}, 每页大小：{}, 状态 ID: {}, 申请人 ID: {}, 申请人身份：{}, 申请人姓名：{}, 开始时间：{}, 结束时间：{}",
                page, pageSize, statusId, applicantId, applicantRole, applicantName, startTime, endTime);

        PageHelper.startPage(page, pageSize);
        List<ApplicationWithdrawalRecord> records = applicationWithdrawalRecordMapper.listWithApplicantName(statusId, applicantId, applicantRole, applicantName, startTime, endTime);

        PageInfo<ApplicationWithdrawalRecord> pageInfo = new PageInfo<>(records);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByStatusId(Long statusId) {
        log.debug("根据状态 ID 删除撤回申请记录，状态 ID: {}", statusId);

        if (statusId == null || statusId <= 0) {
            throw new BusinessException("状态 ID 无效");
        }

        int result = applicationWithdrawalRecordMapper.deleteByStatusId(statusId);
        log.info("根据状态 ID 删除撤回申请记录成功，状态 ID: {}", statusId);

        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteAll() {
        log.debug("清空所有撤回申请记录");
        int result = applicationWithdrawalRecordMapper.deleteAll();
        log.info("清空所有撤回申请记录成功，删除记录数: {}", result);
        return result;
    }
}
