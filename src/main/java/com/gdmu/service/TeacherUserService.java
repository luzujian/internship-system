package com.gdmu.service;

import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.PageResult;
import java.util.List;
import java.util.Map;

public interface TeacherUserService {
    TeacherUser findByTeacherUserId(String teacherUserId);
    int register(TeacherUser teacherUser);
    TeacherUser findById(Long id);
    List<TeacherUser> findByIds(List<Long> ids);
    int update(TeacherUser teacherUser);
    Long count();
    PageResult<TeacherUser> findPage(Integer page, Integer pageSize);
    PageResult<TeacherUser> findPageByCondition(Integer page, Integer pageSize, Map<String, Object> params);
    int delete(Long id);
    
    /**
     * 批量删除教师用户
     * @param ids 教师用户ID列表
     * @return 删除的记录数
     */
    int batchDelete(List<Long> ids);
    
    /**
     * 从Excel导入教师数据
     * @param teacherDataList Excel中解析的教师数据列表
     * @return 导入结果，包含成功和失败信息
     */
    Map<String, Object> importFromExcel(List<Map<String, Object>> teacherDataList);
    
    /**
     * 查询所有满足条件的教师用户
     * @param teacherUserId 工号筛选条件
     * @param name 姓名筛选条件
     * @param departmentId 院系ID筛选条件
     * @return 教师用户列表
     */
    List<TeacherUser> findAll(String teacherUserId, String name, String departmentId);
    
    /**
     * 根据教师类型查询教师列表
     * @param teacherType 教师类型：COLLEGE-学院，DEPARTMENT-系室，COUNSELOR-辅导员
     * @return 教师用户列表
     */
    List<TeacherUser> findByTeacherType(String teacherType);
}