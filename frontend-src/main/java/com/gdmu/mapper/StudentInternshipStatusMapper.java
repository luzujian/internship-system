package com.gdmu.mapper;

import com.gdmu.entity.StudentInternshipStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 学生实习状态Mapper接口
 */
@Mapper
public interface StudentInternshipStatusMapper {
    /**
     * 插入学生实习状态
     * @param status 学生实习状态
     * @return 插入的记录数
     */
    int insert(StudentInternshipStatus status);
    
    /**
     * 根据ID查询学生实习状态
     * @param id 状态ID
     * @return 学生实习状态
     */
    StudentInternshipStatus findById(Long id);
    
    /**
     * 根据学生ID查询实习状态
     * @param studentId 学生ID
     * @return 学生实习状态
     */
    StudentInternshipStatus findByStudentId(Long studentId);
    
    /**
     * 更新学生实习状态
     * @param status 学生实习状态
     * @return 更新的记录数
     */
    int update(StudentInternshipStatus status);
    
    /**
     * 删除学生实习状态
     * @param id 状态ID
     * @return 删除的记录数
     */
    int deleteById(Long id);
    
    /**
     * 查询所有学生实习状态
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> findAll();
    
    /**
     * 动态条件查询学生实习状态
     * @param studentId 学生ID
     * @param name 学生姓名
     * @param gender 性别
     * @param status 实习状态
     * @param companyId 企业ID
     * @param companyName 企业名称
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> list(@Param("studentId") Long studentId,
                                       @Param("name") String name,
                                       @Param("gender") Integer gender,
                                       @Param("status") Integer status,
                                       @Param("companyId") Long companyId,
                                       @Param("companyName") String companyName);
    
    /**
     * 根据ID列表查询学生实习状态
     * @param ids 状态ID列表
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> selectByIds(List<Long> ids);
    
    /**
     * 批量删除学生实习状态
     * @param ids 状态ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 查询学生实习状态总数
     * @return 学生实习状态总数
     */
    Long count();
    
    /**
     * 分页查询学生实习状态
     * @param offset 偏移量
     * @param pageSize 每页大小
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> findPage(@Param("offset") Integer offset, @Param("pageSize") Integer pageSize);
    
    /**
     * 根据状态查询学生实习状态数量
     * @param status 实习状态
     * @return 学生实习状态数量
     */
    int countByStatus(@Param("status") Integer status);
    
    /**
     * 根据企业ID查询学生实习状态数量
     * @param companyId 企业ID
     * @return 学生实习状态数量
     */
    int countByCompanyId(@Param("companyId") Long companyId);
    
    /**
     * 查询所有学生实习状态
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> selectAll();
    
    /**
     * 根据专业ID和状态查询学生实习状态数量
     * @param majorId 专业ID
     * @param status 实习状态
     * @return 学生实习状态数量
     */
    int countByMajorIdAndStatus(@Param("majorId") Long majorId, @Param("status") Integer status);

    /**
     * 查询所有学生实习状态详情（用于导出）
     * @param status 状态筛选
     * @return 学生实习状态详情列表
     */
    List<Map<String, Object>> findAllWithDetails(@Param("status") Integer status);

    /**
     * 获取各专业实习进度（用于导出）
     * @return 专业进度列表
     */
    List<Map<String, Object>> getMajorProgressForExport();

    /**
     * 获取班级实习统计（用于导出）
     * @return 班级统计列表
     */
    List<Map<String, Object>> getClassStatisticsForExport();

    /**
     * 查询待审核撤回申请（分页）
     * @param studentId 学生ID
     * @param name 学生姓名
     * @param gender 性别
     * @param companyId 企业ID
     * @param companyName 企业名称
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> findPendingRecallAuditList(@Param("studentId") Long studentId,
                                                               @Param("name") String name,
                                                               @Param("gender") Integer gender,
                                                               @Param("companyId") Long companyId,
                                                               @Param("companyName") String companyName);

    /**
     * 根据撤回状态统计数量
     * @param recallStatus 撤回状态
     * @return 数量
     */
    int countByRecallStatus(@Param("recallStatus") Integer recallStatus);

    /**
     * 学生提交撤回申请
     * @param id 实习状态ID
     * @param recallReason 撤回原因
     * @return 更新的记录数
     */
    int submitRecallApplication(@Param("id") Long id, @Param("recallReason") String recallReason);

    /**
     * 审核撤回申请
     * @param id 实习状态ID
     * @param recallStatus 撤回状态
     * @param recallAuditRemark 审核备注
     * @param reviewerId 审核人ID
     * @return 更新的记录数
     */
    int auditRecallApplication(@Param("id") Long id, 
                               @Param("recallStatus") Integer recallStatus,
                               @Param("recallAuditRemark") String recallAuditRemark,
                               @Param("reviewerId") Long reviewerId);

    int clearRecallData();
}