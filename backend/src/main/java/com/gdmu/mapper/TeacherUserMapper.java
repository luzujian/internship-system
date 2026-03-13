package com.gdmu.mapper;

import com.gdmu.entity.TeacherUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface TeacherUserMapper {
    TeacherUser findByTeacherUserId(@Param("teacherUserId") String teacherUserId);
    int insert(TeacherUser teacherUser);
    TeacherUser findById(Long id);
    int update(TeacherUser teacherUser);
    
    // 查询所有教师用户，配合PageHelper使用
    List<TeacherUser> findAll();
    
    // 根据ID列表查询教师用户
    // 该方法在XML文件中实现
    List<TeacherUser> selectByIds(List<Long> ids);
    
    // 根据条件查询教师用户
    List<TeacherUser> list(@Param("teacherUserId") String teacherUserId,
                           @Param("name") String name,
                           @Param("departmentId") String departmentId,
                           @Param("status") Integer status,
                           @Param("gender") Integer gender,
                           @Param("teacherType") String teacherType);


    // 查询总记录数
    @Select("select count(1) from teacher_users")
    Long count();
    
    // 分页查询教师用户
    @Select("select * from teacher_users limit #{offset}, #{pageSize}")
    List<TeacherUser> findPage(Integer offset, Integer pageSize);
    
    // 删除教师用户
    @Delete("delete from teacher_users where id = #{id}")
    int deleteById(Long id);
    
    /**
     * 查询所有满足条件的教师用户
     * @param teacherId 工号筛选条件
     * @param name 姓名筛选条件
     * @param departmentId 院系ID筛选条件
     * @return 教师用户列表
     */
    List<TeacherUser> findAll(@Param("teacherUserId") String teacherUserId, @Param("name") String name, @Param("departmentId") String departmentId);
    
    /**
     * 根据工号列表查询教师用户
     * @param teacherIds 工号列表
     * @return 教师用户列表
     */
    List<TeacherUser> findByTeacherIds(@Param("teacherIds") List<String> teacherIds);
    
    /**
     * 批量删除教师用户
     * @param ids 教师用户ID列表
     * @return 删除的记录数
     */
    int batchDeleteByIds(@Param("ids") List<Long> ids);
    
    /**
     * 批量插入教师用户
     * @param teacherUsers 教师用户列表
     * @return 插入的记录数
     */
    int batchInsert(@Param("teacherUsers") List<TeacherUser> teacherUsers);
    
    /**
     * 根据教师类型查询教师列表
     * @param teacherType 教师类型：COLLEGE-学院，DEPARTMENT-系室，COUNSELOR-辅导员
     * @return 教师用户列表
     */
    List<TeacherUser> findByTeacherType(@Param("teacherType") String teacherType);
}