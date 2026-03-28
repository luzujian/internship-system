package com.gdmu.mapper;

import com.gdmu.entity.CompanyQualification;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 企业资质审核Mapper接口
 */
@Mapper
public interface CompanyQualificationMapper {
    /**
     * 插入企业资质审核
     * @param qualification 企业资质审核
     * @return 插入的记录数
     */
    int insert(CompanyQualification qualification);
    
    /**
     * 根据ID查询企业资质审核
     * @param id 资质ID
     * @return 企业资质审核
     */
    CompanyQualification findById(Long id);
    
    /**
     * 根据企业ID查询资质审核
     * @param companyId 企业ID
     * @return 企业资质审核
     */
    CompanyQualification findByCompanyId(Long companyId);
    
    /**
     * 更新企业资质审核
     * @param qualification 企业资质审核
     * @return 更新的记录数
     */
    int update(CompanyQualification qualification);
    
    /**
     * 删除企业资质审核
     * @param id 资质ID
     * @return 删除的记录数
     */
    int deleteById(Long id);
    
    /**
     * 查询所有企业资质审核
     * @return 企业资质审核列表
     */
    List<CompanyQualification> findAll();
    
    /**
     * 动态条件查询企业资质审核
     * @param status 审核状态
     * @param companyName 企业名称
     * @return 企业资质审核列表
     */
    List<CompanyQualification> list(@Param("status") String status,
                                    @Param("companyName") String companyName);
    
    /**
     * 根据ID列表查询企业资质审核
     * @param ids 资质ID列表
     * @return 企业资质审核列表
     */
    List<CompanyQualification> selectByIds(List<Long> ids);
    
    /**
     * 批量删除企业资质审核
     * @param ids 资质ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 查询企业资质审核总数
     * @return 企业资质审核总数
     */
    Long count();
    
    /**
     * 分页查询企业资质审核
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 企业资质审核列表
     */
    List<CompanyQualification> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 根据状态查询资质审核数量
     * @param status 审核状态
     * @return 资质审核数量
     */
    int countByStatus(@Param("status") String status);
}
