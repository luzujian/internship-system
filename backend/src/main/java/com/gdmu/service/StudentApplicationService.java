package com.gdmu.service;

import com.gdmu.entity.StudentApplication;
import com.gdmu.entity.PageResult;

import java.util.List;
import java.util.Map;

/**
 * 学生申请服务接口
 */
public interface StudentApplicationService {
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
    int delete(Long id);
    
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
    List<StudentApplication> list(String applicationType, String status, String studentName, String studentUserId);
    
    /**
     * 根据ID列表查询学生申请
     * @param ids 申请ID列表
     * @return 学生申请列表
     */
    List<StudentApplication> findByIds(List<Long> ids);
    
    /**
     * 批量删除学生申请
     * @param ids 申请ID列表
     * @return 删除的记录数
     */
    int batchDelete(List<Long> ids);
    
    /**
     * 查询学生申请总数
     * @return 学生申请总数
     */
    Long count();
    
    /**
     * 分页查询学生申请
     * @param page 页码
     * @param pageSize 每页大小
     * @return 分页结果
     */
    PageResult<StudentApplication> findPage(Integer page, Integer pageSize);
    
    /**
     * 分页查询学生申请
     * @param page 页码
     * @param pageSize 每页大小
     * @param applicationType 申请类型
     * @param status 申请状态
     * @param studentName 学生姓名
     * @param studentUserId 学号
     * @return 分页结果
     */
    PageResult<StudentApplication> findPage(Integer page, Integer pageSize, String applicationType, String status, String studentName, String studentUserId);
    
    /**
     * 根据申请类型和状态查询申请数量
     * @param applicationType 申请类型
     * @param status 申请状态
     * @return 申请数量
     */
    int countByTypeAndStatus(String applicationType, String status);
    
    /**
     * 批准申请
     * @param id 申请ID
     * @param reviewerId 审核人ID
     * @return 更新的记录数
     */
    int approve(Long id, Long reviewerId);
    
    /**
     * 驳回申请
     * @param id 申请ID
     * @param reviewerId 审核人ID
     * @param rejectReason 驳回理由
     * @return 更新的记录数
     */
    int reject(Long id, Long reviewerId, String rejectReason);

    /**
     * 获取学生的最新单位变更申请
     * @param studentId 学生ID
     * @return 最新单位变更申请
     */
    StudentApplication findLatestUnitChangeByStudentId(Long studentId);

    /**
     * 更新单位变更申请状态为pending（用于再次申请）
     * @param id 申请ID
     * @param studentId 学生ID
     * @param newCompany 新单位名称
     * @param reason 申请理由
     * @param materials 申请材料
     * @return 更新的记录数
     */
    int resubmitUnitChange(Long id, Long studentId, String newCompany, String reason, Map<String, String> materials);
}
