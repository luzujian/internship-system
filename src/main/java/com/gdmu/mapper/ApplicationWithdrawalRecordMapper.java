package com.gdmu.mapper;

import com.gdmu.entity.ApplicationWithdrawalRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 撤回申请记录 Mapper 接口
 */
public interface ApplicationWithdrawalRecordMapper {

    /**
     * 插入撤回申请记录
     */
    int insert(ApplicationWithdrawalRecord record);

    /**
     * 根据 ID 查询撤回申请记录
     */
    ApplicationWithdrawalRecord findById(@Param("id") Long id);

    /**
     * 根据状态 ID 查询撤回申请记录
     */
    ApplicationWithdrawalRecord findByStatusId(@Param("statusId") Long statusId);

    /**
     * 查询所有撤回申请记录
     */
    List<ApplicationWithdrawalRecord> findAll();

    /**
     * 动态条件查询撤回申请记录
     */
    List<ApplicationWithdrawalRecord> list(
        @Param("statusId") Long statusId,
        @Param("applicantId") Long applicantId,
        @Param("applicantRole") String applicantRole
    );

    /**
     * 动态条件查询撤回申请记录（包含申请人姓名筛选）
     */
    List<ApplicationWithdrawalRecord> listWithApplicantName(
        @Param("statusId") Long statusId,
        @Param("applicantId") Long applicantId,
        @Param("applicantRole") String applicantRole,
        @Param("applicantName") String applicantName,
        @Param("startTime") String startTime,
        @Param("endTime") String endTime
    );

    /**
     * 根据 ID 列表查询撤回申请记录
     */
    List<ApplicationWithdrawalRecord> selectByIds(@Param("ids") List<Long> ids);

    /**
     * 根据 ID 删除撤回申请记录
     */
    int deleteById(@Param("id") Long id);

    /**
     * 批量删除撤回申请记录
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);

    /**
     * 统计撤回申请记录总数
     */
    Long count();

    /**
     * 根据状态 ID 删除撤回申请记录
     */
    int deleteByStatusId(@Param("statusId") Long statusId);

    /**
     * 清空所有撤回申请记录
     */
    int deleteAll();
}
