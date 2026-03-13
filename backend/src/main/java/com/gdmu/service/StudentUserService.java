package com.gdmu.service;

import com.gdmu.entity.StudentUser;
import com.gdmu.entity.PageResult;
import com.gdmu.vo.StudentUserVO;

import java.util.List;
import java.util.Map;

public interface StudentUserService {
    StudentUser findByStudentId(String studentId);
    int register(StudentUser studentUser);
    StudentUser findById(Long id);
    List<StudentUser> findByIds(List<Long> ids);
    int update(StudentUser studentUser);
    Long count();
    PageResult<StudentUser> findPage(Integer page, Integer pageSize);
    PageResult<StudentUser> findPage(Integer page, Integer pageSize, String keyword);
    PageResult<StudentUser> findPage(Integer page, Integer pageSize, String studentId, String name, Integer grade, String majorId, String classId);
    PageResult<StudentUser> findPage(Integer page, Integer pageSize, String studentId, String name, Integer grade, String majorId, String classId, Integer status);
    PageResult<StudentUser> findPage(Integer page, Integer pageSize, String studentId, String name, Integer grade, String majorId, String classId, Integer status, Integer gender);
    int delete(Long id);
    
    /**
     * 从Excel导入学生数据
     * @param studentDataList Excel中解析的学生数据列表
     * @return 导入结果，包含成功和失败信息
     */
    Map<String, Object> importFromExcel(List<Map<String, Object>> studentDataList);
    
    /**
     * 批量删除学生用户
     * @param ids 学生用户ID列表
     * @return 删除的记录数
     */
    int batchDeleteStudentUsers(List<Long> ids);
    
    /**
     * 查询所有满足条件的学生用户
     * @param studentId 学号筛选条件
     * @param name 姓名筛选条件
     * @param grade 年级筛选条件
     * @param majorId 专业ID筛选条件
     * @param classId 班级ID筛选条件
     * @return 学生用户列表
     */
    List<StudentUser> findAll(String studentId, String name, Integer grade, String majorId, String classId);
    List<StudentUser> findAll(String studentId, String name, Integer grade, String majorId, String classId, Integer status);
    
    /**
     * 根据班级ID获取学生数量
     * @param classId 班级ID
     * @return 学生数量
     */
    Integer getStudentCountByClassId(Long classId);
    
    /**
     * AI查询学生用户信息
     * @param queryParams 查询参数
     * @return 学生用户视图对象列表
     */
    List<StudentUserVO> queryStudentsByParams(Map<String, Object> queryParams);
    
    /**
     * 根据课程ID列表查询学生信息
     * @param courseIds 课程ID列表
     * @param searchName 学生姓名搜索关键字
     * @param classId 班级ID
     * @return 学生信息列表
     */
    List<Map<String, Object>> findStudentsByCourseIds(List<Long> courseIds, String searchName, Long classId);
    
    /**
     * 根据班级ID删除所有学生
     * @param classId 班级ID
     * @return 删除的记录数
     */
    int deleteByClassId(Long classId);
    
    // 新增学生用户
    int save(StudentUser studentUser);
}
