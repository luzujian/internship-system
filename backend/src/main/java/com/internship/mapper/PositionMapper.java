package com.internship.mapper;

import com.internship.entity.Position;
import org.apache.ibatis.annotations.Delete;
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
     * 根据ID查询岗位信息并加行锁（FOR UPDATE，防止并发超发）
     * @param id 岗位ID
     * @return 岗位信息
     */
    default Position findByIdForUpdate(Long id) {
        Position position = lockPositionRow(id);
        if (position == null) return null;
        int recruited = countCurrentOccupancyForUpdate(id);
        position.setRecruitedCount(recruited);
        position.setRemainingQuota((position.getPlannedRecruit() == null ? 0 : position.getPlannedRecruit()) - recruited);
        return position;
    }

    Position lockPositionRow(@Param("id") Long id);
    int countCurrentOccupancyForUpdate(@Param("id") Long id);

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

    Long countByCompanyId(@Param("companyId") Long companyId);

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
    /** 到期安全阀:暂停实习期已过的在招岗位,返回暂停行数 */
    int pauseExpiredActivePositions();

    int updateRecruitedCount(@Param("positionId") Long positionId);

    /**
     * 浏览次数+1
     * @param positionId 岗位ID
     */
    void incrementViewCount(@Param("positionId") Long positionId);

    /**
     * 清除所有岗位数据
     * @return 清除的记录数
     */
    int clearAll();

    /**
     * 获取所有不重复的行业类别ID
     * @return 行业类别ID列表
     */
    List<Long> findDistinctCategoryIds();

    /**
     * 获取所有不重复的公司ID
     * @return 公司ID列表
     */
    List<Long> findDistinctCompanyIds();

    /**
     * 获取所有不重复的省份
     * @return 省份列表
     */
    List<String> findDistinctProvinces();

    // ========== 删除岗位级联清理：按 position_id 删除所有引用该岗位的业务表记录（每条都在 Service 层独立 try-catch，表不存在时跳过） ==========
    @Delete("delete from student_internship_status where position_id = #{positionId}")
    int deleteInternshipStatusByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from student_job_application where position_id = #{positionId}")
    int deleteJobApplicationByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from internship_application where position_id = #{positionId}")
    int deleteInternshipApplicationByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from internship_confirmation_record where position_id = #{positionId}")
    int deleteConfirmationRecordByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from internship_stage_assignment where position_id = #{positionId}")
    int deleteStageAssignmentByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from interview_invitation where position_id = #{positionId}")
    int deleteInterviewInvitationByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from position_favorite where position_id = #{positionId}")
    int deletePositionFavoriteByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from position_view_record where position_id = #{positionId}")
    int deletePositionViewRecordByPositionId(@Param("positionId") Long positionId);

    @Delete("delete from company_notification where position_id = #{positionId}")
    int deleteCompanyNotificationByPositionId(@Param("positionId") Long positionId);
}
