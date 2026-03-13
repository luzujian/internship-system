package com.gdmu.mapper;

import com.gdmu.entity.GroupTable;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Options;

import java.util.List;

@Mapper
public interface GroupMapper {
    // 根据ID查询小组
    @Select("select * from group_table where id = #{id}")
    GroupTable selectById(Long id);

    // 根据组长ID查询小组
    @Select("select * from group_table where leader_id = #{leaderId}")
    GroupTable selectByLeaderId(Long leaderId);

    // 根据学生组长ID查询小组
    @Select("select * from group_table where leader_student_id = #{leaderStudentId}")
    List<GroupTable> selectByLeaderStudentId(Long leaderStudentId);

    // 查询所有小组
    @Select("select * from group_table")
    List<GroupTable> selectAll();

    // 根据名称模糊查询小组
    // 该方法在XML文件中实现
    List<GroupTable> selectByNameLike(String keyword);

    // 新增小组 - 显式字段映射确保正确保存所有字段
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert({
        "INSERT INTO group_table",
        "(name, leader_id, leader_student_id, course_id, assignment_id, create_time, update_time, status, member_count, current_members, class_id, major_id, teacher_id, description)",
        "VALUES",
        "(#{name}, #{leaderId}, #{leaderStudentId}, #{courseId}, #{assignmentId}, #{createTime}, #{updateTime}, #{status}, #{memberCount}, #{currentMembers}, #{classId}, #{majorId}, #{teacherId}, #{description})"
    })
    int insert(GroupTable group);

    // 更新小组
    @Update("update group_table set name = #{name}, leader_id = #{leaderId}, class_id = #{classId}, major_id = #{majorId}, course_id = #{courseId}, assignment_id = #{assignmentId}, status = #{status}, teacher_id = #{teacherId}, member_count = #{memberCount}, update_time = #{updateTime} where id = #{id}")
    int update(GroupTable group);

    // 删除小组
    @Delete("delete from group_table where id = #{id}")
    int deleteById(Long id);
    
    // 根据ID列表查询小组
    // 该方法在XML文件中实现
    List<GroupTable> findByIds(List<Long> ids);
    
    // 查询小组总数
    @Select("select count(*) from group_table")
    int countAll();}