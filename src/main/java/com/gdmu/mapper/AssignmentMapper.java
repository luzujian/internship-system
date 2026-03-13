package com.gdmu.mapper;

import com.gdmu.entity.Assignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

@Mapper
public interface AssignmentMapper {
    // 根据ID查询作业
    @Select("select * from assignment where id = #{id}")
    Assignment selectById(Long id);

    // 根据教师ID查询作业 - 注意这里使用的是teacher_user_id而不是teacher_id
    @Select("select * from assignment where teacher_user_id = #{teacherId}")
    List<Assignment> selectByTeacherId(Long teacherId);
    
    // 根据教师ID和课程ID查询作业
    @Select("select * from assignment where teacher_user_id = #{teacherId} and course_id = #{courseId}")
    List<Assignment> selectByTeacherIdAndCourseId(Long teacherId, Long courseId);

    // 查询所有作业
    @Select("select * from assignment")
    List<Assignment> selectAll();

    // 新增作业 - 注意这里使用的是teacher_user_id而不是teacher_id
    @Insert("insert into assignment(title, description, teacher_user_id, create_time, deadline, course_id, status) values(#{title}, #{description}, #{teacherId}, #{createTime}, #{deadline}, #{courseId}, #{status})")
    int insert(Assignment assignment);

    // 更新作业
    @Update("update assignment set title = #{title}, description = #{description}, deadline = #{deadline}, course_id = #{courseId}, status = #{status} where id = #{id}")
    int update(Assignment assignment);

    // 删除作业
    @Delete("delete from assignment where id = #{id}")
    int deleteById(Long id);
    
    // 根据ID列表查询作业
    // 该方法在XML文件中实现
    List<Assignment> findByIds(List<Long> ids);
    
    // 查询所有作业并关联课程和教师信息
    List<Assignment> findAllWithCourseAndTeacher();
    
    // 根据学生ID查询作业并关联课程和教师信息
    List<Assignment> findByStudentIdWithCourseAndTeacher(Long studentId);
}