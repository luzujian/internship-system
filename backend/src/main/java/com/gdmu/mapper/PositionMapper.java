package com.gdmu.mapper;

import com.gdmu.entity.Position;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 岗位Mapper接口
 */
@Mapper
public interface PositionMapper {
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
    int deleteById(Long id);
    
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
    List<Position> findByCompanyIdWithConditions(@Param("companyId") Long companyId,
                                                  @Param("positionName") String positionName,
                                                  @Param("department") String department,
                                                  @Param("status") String status);
    
    /**
     * 动态条件查询岗位信息
     * @param companyId 企业ID
     * @param positionName 岗位名称
     * @return 岗位信息列表
     */
    List<Position> list(@Param("companyId") Long companyId, 
                       @Param("positionName") String positionName);
    
    /**
     * 根据ID列表查询岗位信息
     * @param ids 岗位ID列表
     * @return 岗位信息列表
     */
    List<Position> selectByIds(List<Long> ids);
    
    /**
     * 批量删除岗位信息
     * @param ids 岗位ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 查询岗位总数
     * @return 岗位总数
     */
    Long count();
    
    /**
     * 分页查询岗位信息
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 岗位信息列表
     */
    List<Position> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 查询所有岗位信息
     * @return 岗位信息列表
     */
    List<Position> selectAll();

    /**
     * 分页查询岗位信息（带企业名称）
     * @param companyName 企业名称
     * @param positionName 岗位名称
     * @return 岗位信息列表
     */
    List<Map<String, Object>> findPageWithCompany(@Param("companyName") String companyName,
                                                   @Param("positionName") String positionName);

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
    List<Map<String, Object>> getRecruitedStudents(@Param("positionId") Long positionId);

    /**
     * 根据类别ID查询岗位信息
     * @param categoryId 类别ID
     * @return 岗位信息列表
     */
    List<Position> findByCategoryId(@Param("categoryId") Long categoryId);

    /**
     * 更新岗位已招人数
     * @param positionId 岗位ID
     * @return 更新的记录数
     */
    int updateRecruitedCount(@Param("positionId") Long positionId);

    /**
     * 清除所有岗位数据
     * @return 清除的记录数
     */
    int clearAll();
}