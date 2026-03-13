package com.gdmu.service;

import com.gdmu.entity.AdminUser;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.User;
import com.gdmu.entity.PageResult;
import java.util.List;
import java.util.Map;

public interface UserService {
    User findByUsername(String username);
    int register(User user);
    User findById(Long id);
    List<User> findByIds(List<Long> ids);
    int update(User user);
    Long count();
    PageResult<User> findPage(Integer page, Integer pageSize);
    int delete(Long id);
    List<User> findByRole(String role);
    int updatePassword(String username, String newPassword);
    int updateUsername(String oldUsername, String newUsername);
    
    // 根据课程ID列表查询学生信息
    List<Map<String, Object>> findStudentsByCourseIds(List<Long> courseIds, String searchName, Long classId);
    
    // 获取所有管理员用户
    List<AdminUser> getAllAdminUsers();
    
    // 根据教师类型获取教师列表
    List<TeacherUser> getTeachersByType(String teacherType);
    
    // 清除所有UserDetails缓存
    void clearAllUserDetailsCache();
}