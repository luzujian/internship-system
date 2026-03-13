package com.gdmu.mapper;

import com.gdmu.entity.StudentUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;
import java.util.Map;

@Mapper
public interface StudentUserMapper {
    StudentUser findByStudentId(@Param("studentId") String studentId);
    int insert(StudentUser studentUser);
    StudentUser findById(Long id);
    int update(StudentUser studentUser);
    
    // 查询所有学生用户，配合PageHelper使用
    List<StudentUser> findAll();

    // 动态条件查询
    List<StudentUser> list(@Param("studentId") String studentId, 
                          @Param("name") String name, 
                          @Param("grade") Integer grade, 
                          @Param("majorId") Integer majorId, 
                          @Param("classId") String classId);
    
    // 动态条件查询（包含状态参数）
    List<StudentUser> listWithStatus(@Param("studentId") String studentId, 
                          @Param("name") String name, 
                          @Param("grade") Integer grade, 
                          @Param("majorId") Integer majorId, 
                          @Param("classId") String classId, 
                          @Param("status") Integer status,
                          @Param("gender") Integer gender);
    
    // 根据ID列表查询学生用户
    // 该方法在XML文件中实现
    List<StudentUser> selectByIds(List<Long> ids);
    
    // 查询总记录数
    @Select("select count(1) from student_users")
    Long count();
    
    // 分页查询学生用户
    @Select("select * from student_users limit #{offset}, #{pageSize}")
    List<StudentUser> findPage(Integer offset, Integer pageSize);
    
    // 删除学生用户
    @Delete("delete from student_users where id = #{id}")
    int deleteById(Long id);
    
    /**
     * 批量查询学生用户，根据学号列表
     * @param studentIds 学号列表
     * @return 学生用户列表
     */
    List<StudentUser> findByStudentIds(@Param("studentIds") List<String> studentIds);
    
    /**
     * 批量插入学生用户
     * @param studentUsers 学生用户列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("studentUsers") List<StudentUser> studentUsers);
    
    /**
     * 批量删除学生用户，根据ID列表
     * @param ids 学生用户ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 根据班级ID查询学生数量
     * @param classId 班级ID
     * @return 学生数量
     */
    Integer countByClassId(@Param("classId") Long classId);
    
    /**
     * 根据班级ID查询所有学生
     * @param classId 班级ID
     * @return 学生列表
     */
    List<StudentUser> findByClassId(@Param("classId") Long classId);
    
    /**
     * 更新 group_table 表中的 leader_student_id 为 NULL
     * @param ids 学生ID列表
     * @return 更新的记录数
     */
    int updateGroupLeaderToNull(@Param("ids") List<Long> ids);
    
    /**
     * 根据课程ID列表查询学生信息
     * @param courseIds 课程ID列表
     * @param searchName 学生姓名搜索关键字
     * @param classId 班级ID
     * @return 学生信息列表
     */
    List<Map<String, Object>> findStudentsByCourseIds(@Param("courseIds") List<Long> courseIds, 
                                                      @Param("searchName") String searchName, 
                                                      @Param("classId") Long classId);

    List<StudentUser> list(@Param("studentId") String studentId, 
                          @Param("name") String name, 
                          @Param("grade") Integer grade, 
                          @Param("majorId") Integer majorIdInt, 
                          @Param("classId") Long classId, 
                          @Param("status") Integer status);
    
    /**
     * 删除contribution表中与特定学生相关的记录
     * @param studentId 学生ID
     * @return 删除的记录数
     */
    @Delete("delete from contribution where student_user_id = #{studentId}")
    int deleteContributionByStudentId(Long studentId);
    
    /**
     * 直接更新特定小组的所有组长相关字段为null
     * 这是解决小组组长外键约束的最终手段
     * @param groupId 小组ID
     * @return 更新的记录数
     */
    int updateSpecificGroupLeadersToNull(@Param("groupId") Long groupId);
    
    /**
     * 根据专业ID查询学生数量
     * @param majorId 专业ID
     * @return 学生数量
     */
    int countByMajorId(@Param("majorId") Long majorId);
    
    /**
     * 查询所有学生
     * @return 学生列表
     */
    List<StudentUser> selectAll();
}