package com.gdmu.service;

import com.gdmu.entity.StudentInternshipStatus;
import com.gdmu.entity.PageResult;

import java.util.List;

/**
 * 学生实习状态服务接口
 */
public interface StudentInternshipStatusService {
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
    int delete(Long id);

    int deleteByPositionId(Long positionId);
    
    /**
     * 查询所有学生实习状态
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> findAll();
    
    /**
     * 动态条件查询学生实习状态（扩展版，支持班级过滤）
     * @param studentId 学生ID
     * @param name 学生姓名
     * @param gender 性别
     * @param status 实习状态
     * @param companyId 企业ID
     * @param companyName 企业名称
     * @param grade 年级
     * @param major 专业
     * @param className 班级名称
     * @param studentUserId 学号
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> list(Long studentId, String name, Integer gender, Integer status, Long companyId, String companyName, String grade, String major, String className, String studentUserId);
    
    /**
     * 根据ID列表查询学生实习状态
     * @param ids 状态ID列表
     * @return 学生实习状态列表
     */
    List<StudentInternshipStatus> findByIds(List<Long> ids);
    
    /**
     * 批量删除学生实习状态
     * @param ids 状态ID列表
     * @return 删除的记录数
     */
    int batchDelete(List<Long> ids);
    
    /**
     * 查询学生实习状态总数
     * @return 学生实习状态总数
     */
    Long count();

    /**
     * 根据状态统计学生数量
     * @param status 实习状态
     * @return 相应状态的学生数量
     */
    Integer countByStatus(Integer status);

    /**
     * 分页查询学生实习状态
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<StudentInternshipStatus> findPage(Integer page, Integer pageSize);
    
    /**
     * 分页查询学生实习状态
     * @param page 页码
     * @param pageSize 每页大小
     * @param studentId 学生ID
     * @param name 学生姓名
     * @param gender 性别
     * @param status 实习状态
     * @param companyId 企业ID
     * @param companyName 企业名称
     * @return 分页结果
     */
    PageResult<StudentInternshipStatus> findPage(Integer page, Integer pageSize, Long studentId, String name, Integer gender, Integer status, Long companyId, String companyName, String grade, String major, String className);

    PageResult<StudentInternshipStatus> findPendingRecallAuditPage(Integer page, Integer pageSize, Long studentId, String name, Integer gender, Long companyId, String companyName);

    List<StudentInternshipStatus> findPendingRecallAuditList(Long studentId, String name, Integer gender, Long companyId, String companyName);

    Integer countByRecallStatus(Integer recallStatus);

    int auditRecallApplication(Long id, Integer recallStatus, String recallAuditRemark, Long reviewerId);

    int submitRecallApplication(Long id, String recallReason);

    /**
     * 提交撤回申请（自动撤回模式）- 同时创建撤回记录
     * @param id 实习状态 ID
     * @param recallReason 撤回原因
     * @param applicantId 申请人 ID
     * @param applicantRole 申请人身份
     * @return 更新的记录数
     */
    int submitRecallApplicationWithRecord(Long id, String recallReason, Long applicantId, String applicantRole);

    int clearRecallData();
}