package com.gdmu.service;

import com.gdmu.entity.ApplicationWithdrawalRecord;
import com.gdmu.entity.PageResult;

import java.util.List;

/**
 * 撤回申请记录服务接口
 */
public interface ApplicationWithdrawalRecordService {

    /**
     * 插入撤回申请记录
     */
    int insert(ApplicationWithdrawalRecord record);

    /**
     * 根据 ID 查询撤回申请记录
     */
    ApplicationWithdrawalRecord findById(Long id);

    /**
     * 根据状态 ID 查询撤回申请记录
     */
    ApplicationWithdrawalRecord findByStatusId(Long statusId);

    /**
     * 查询所有撤回申请记录
     */
    List<ApplicationWithdrawalRecord> findAll();

    /**
     * 动态条件查询撤回申请记录
     */
    List<ApplicationWithdrawalRecord> list(Long statusId, Long applicantId, String applicantRole);

    /**
     * 根据 ID 列表查询撤回申请记录
     */
    List<ApplicationWithdrawalRecord> findByIds(List<Long> ids);

    /**
     * 根据 ID 删除撤回申请记录
     */
    int delete(Long id);

    /**
     * 批量删除撤回申请记录
     */
    int batchDelete(List<Long> ids);

    /**
     * 统计撤回申请记录总数
     */
    Long count();

    /**
     * 分页查询撤回申请记录
     */
    PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize);

    /**
     * 分页查询撤回申请记录（带条件）
     */
    PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize, Long statusId, Long applicantId, String applicantRole);

    /**
     * 分页查询撤回申请记录（带条件，包含申请人姓名）
     */
    PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize, Long statusId, Long applicantId, String applicantRole, String applicantName);

    /**
     * 分页查询撤回申请记录（带条件，包含申请人姓名和时间范围）
     */
    PageResult<ApplicationWithdrawalRecord> findPage(Integer page, Integer pageSize, Long statusId, Long applicantId, String applicantRole, String applicantName, String startTime, String endTime);

    /**
     * 根据状态 ID 删除撤回申请记录
     */
    int deleteByStatusId(Long statusId);

    /**
     * 清空所有撤回申请记录
     */
    int deleteAll();
}
