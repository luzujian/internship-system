package com.gdmu.service;

import com.gdmu.entity.Position;
import com.gdmu.entity.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 岗位服务接口
 */
public interface PositionService {
    /**
     * 插入岗位信息
     * @param position 岗位信息
     * @return 插入的记录数
     */
    int insert(Position position);
    
    /**
     * 根据ID查询岗位信息
     * @param id 岗位ID
     * @return 岗位信息
     */
    Position findById(Long id);
    
    /**
     * 更新岗位信息
     * @param position 岗位信息
     * @return 更新的记录数
     */
    int update(Position position);
    
    /**
     * 删除岗位信息
     * @param id 岗位ID
     * @return 删除的记录数
     */
    int delete(Long id);
    
    /**
     * 查询所有岗位信息
     * @return 岗位信息列表
     */
    List<Position> findAll();
    
    /**
     * 根据企业ID查询岗位信息
     * @param companyId 企业ID
     * @return 岗位信息列表
     */
    List<Position> findByCompanyId(Long companyId);

    /**
     * 根据企业ID和条件查询岗位信息
     * @param companyId 企业ID
     * @param positionName 岗位名称
     * @param department 部门
     * @param status 状态
     * @return 岗位信息列表
     */
    List<Position> findByCompanyIdWithConditions(Long companyId, String positionName, String department, String status);
    
    /**
     * 动态条件查询岗位信息
     * @param companyId 企业ID
     * @param positionName 岗位名称
     * @return 岗位信息列表
     */
    List<Position> list(Long companyId, String positionName);
    
    /**
     * 根据ID列表查询岗位信息
     * @param ids 岗位ID列表
     * @return 岗位信息列表
     */
    List<Position> findByIds(List<Long> ids);
    
    /**
     * 批量删除岗位信息
     * @param ids 岗位ID列表
     * @return 删除的记录数
     */
    int batchDelete(List<Long> ids);
    
    /**
     * 查询岗位总数
     * @return 岗位总数
     */
    Long count();
    
    /**
     * 分页查询岗位信息
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<Position> findPage(Integer page, Integer pageSize);
    
    /**
     * 分页查询岗位信息
     * @param page 页码
     * @param pageSize 每页大小
     * @param companyId 企业ID
     * @param positionName 岗位名称
     * @return 分页结果
     */
    PageResult<Position> findPage(Integer page, Integer pageSize, Long companyId, String positionName);

    /**
     * 分页查询岗位信息（带企业名称）
     * @param page 页码
     * @param pageSize 每页大小
     * @param companyName 企业名称
     * @param positionName 岗位名称
     * @return 分页结果
     */
    PageResult<Map<String, Object>> findPage(Integer page, Integer pageSize, String companyName, String positionName);

    /**
     * 获取岗位统计数据
     * @return 统计数据
     */
    Map<String, Object> getStatistics();

    /**
     * 获取岗位已招学生列表
     * @param positionId 岗位ID
     * @return 学生列表
     */
    List<Map<String, Object>> getRecruitedStudents(Long positionId);

    /**
     * 根据企业ID分页查询岗位
     * @param companyId 企业ID
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<Position> findPageByCompanyId(Long companyId, Integer page, Integer pageSize);

    /**
     * 根据企业ID统计岗位数量
     * @param companyId 企业ID
     * @return 岗位数量
     */
    Long countByCompanyId(Long companyId);

    /**
     * 更新岗位已招人数
     * @param positionId 岗位ID
     * @return 更新的记录数
     */
    int updateRecruitedCount(Long positionId);

    /**
     * 清除所有岗位数据
     * @return 清除的记录数
     */
    int clearAll();

    /**
     * 暂停岗位招聘
     * @param positionId 岗位 ID
     * @return 更新的记录数
     */
    int pausePosition(Long positionId);

    /**
     * 恢复岗位招聘
     * @param positionId 岗位 ID
     * @return 更新的记录数
     */
    int resumePosition(Long positionId);
}