package com.internship.mapper;

import com.internship.entity.Class;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ClassMapper {
    @Select("SELECT c.id,c.name,c.teacher_user_id AS teacherId,m.department_id AS departmentId "
            + "FROM class c LEFT JOIN major m ON m.id=c.major_id WHERE c.id=#{id} FOR UPDATE")
    Class findByIdForUpdate(Long id);

    @Select("SELECT c.id,c.name,c.grade,c.major_id AS majorId,m.name AS majorName,m.department_id AS departmentId "
            + "FROM class c JOIN major m ON m.id=c.major_id WHERE m.department_id=#{departmentId} "
            + "AND (c.teacher_user_id IS NULL OR TRIM(c.teacher_user_id)='') "
            + "AND NOT EXISTS (SELECT 1 FROM class_counselor_relation r WHERE r.class_id=c.id) ORDER BY c.id")
    List<Class> findClaimableByDepartment(Long departmentId);

    @Update("UPDATE class SET teacher_user_id=#{teacherId},update_time=NOW() WHERE id=#{id}")
    int updateResponsibleTeacher(@Param("id") Long id, @Param("teacherId") String teacherId);
    // 根据ID查询班级
    Class findById(Long id);

    // 根据教师工号查询班级
    List<Class> findByTeacherUserId(String teacherUserId);

    // 查询所有班级
    List<Class> findAll();
    
    // 根据专业ID查询班级
    List<Class> findByMajorId(Long majorId);
    
    // 新增班级
    int insert(Class classEntity);
    
    // 更新班级
    int update(Class classEntity);
    
    // 删除班级
    int delete(Long id);

}
