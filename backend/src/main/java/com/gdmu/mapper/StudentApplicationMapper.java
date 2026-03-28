package com.gdmu.mapper;

import com.gdmu.entity.StudentApplication;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 学生申请Mapper接口
 */
@Mapper
public interface StudentApplicationMapper {
    /**
     * 插入学生申请
     * @param application 学生申请
     * @return 插入的记录数
     */
    int insert(StudentApplication application);
    
    /**
     * 根据ID查询学生申请
     * @param id 申请ID
     * @return 学生申请
     */
    StudentApplication findById(Long id);
    
    /**
     * 根据学生ID查询申请列表
     * @param studentId 学生ID
     * @return 学生申请列表
     */
    List<StudentApplication> findByStudentId(Long studentId);
    
    /**
     * 更新学生申请
     * @param application 学生申请
     * @return 更新的记录数
     */
    int update(StudentApplication application);
    
    /**
     * 删除学生申请
     * @param id 申请ID
     * @return 删除的记录数
     */
    int deleteById(Long id);
    
    /**
     * 查询所有学生申请
     * @return 学生申请列表
     */
    List<StudentApplication> findAll();
    
    /**
     * 动态条件查询学生申请
     * @param applicationType 申请类型
     * @param status 申请状态
     * @param studentName 学生姓名
     * @param studentUserId 学号
     * @return 学生申请列表
     */
    List<StudentApplication> list(@Param("applicationType") String applicationType,
                                   @Param("status") String status,
                                   @Param("studentName") String studentName,
                                   @Param("studentUserId") String studentUserId);
    
    /**
     * 根据ID列表查询学生申请
     * @param ids 申请ID列表
     * @return 学生申请列表
     */
    List<StudentApplication> selectByIds(List<Long> ids);
    
    /**
     * 批量删除学生申请
     * @param ids 申请ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 查询学生申请总数
     * @return 学生申请总数
     */
    Long count();
    
    /**
     * 分页查询学生申请
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 学生申请列表
     */
    List<StudentApplication> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 根据申请类型和状态查询申请数量
     * @param applicationType 申请类型
     * @param status 申请状态
     * @return 申请数量
     */
    int countByTypeAndStatus(@Param("applicationType") String applicationType, @Param("status") String status);
    
    /**
     * 根据状态查询申请数量
     * @param status 申请状态
     * @return 申请数量
     */
    int countByStatus(@Param("status") String status);

    /**
     * 获取待审核申请数量（用于首页统计）
     * @return 待审核申请数量
     */
    int countPendingApproval();

    /**
     * 根据时间范围统计申请数量
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 申请数量
     */
    int countByTimeRange(@Param("startDate") Date startDate, @Param("endDate") Date endDate);
}
