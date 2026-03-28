package com.gdmu.mapper;

import com.gdmu.entity.GroupMember;
import org.apache.ibatis.annotations.*;
import org.apache.ibatis.annotations.Options;

import java.util.List;
import java.util.Map;

@Mapper
public interface GroupMemberMapper {
    // 根据小组ID查询所有成员
    @Select("select id, group_id, student_user_id as studentId, join_time as joinTime from group_member where group_id = #{groupId}")
    List<GroupMember> findByGroupId(Long groupId);

    // 根据学生ID查询所属小组
    @Select("select id, group_id, student_user_id as studentId, join_time as joinTime from group_member where student_user_id = #{studentId}")
    List<GroupMember> findByStudentId(Long studentId);

    // 根据小组ID和学生ID查询成员信息
    GroupMember findByGroupIdAndStudentId(Long groupId, Long studentId);

    // 检查学生是否在小组中
    @Select("select count(1) from group_member where group_id = #{groupId} and student_user_id = #{studentId}")
    int countByGroupIdAndStudentId(Long groupId, Long studentId);

    // 新增小组成员
    @Options(useGeneratedKeys = true, keyProperty = "id")
    @Insert("insert into group_member(group_id, student_user_id, join_time) values(#{groupId}, #{studentId}, #{joinTime})")
    int insert(GroupMember groupMember);

    // 删除小组成员
    @Delete("delete from group_member where group_id = #{groupId} and student_user_id = #{studentId}")
    int deleteByGroupIdAndStudentId(Long groupId, Long studentId);

    // 根据小组ID删除所有成员
    @Delete("delete from group_member where group_id = #{groupId}")
    int deleteByGroupId(Long groupId);

    // 根据学生ID删除所有小组成员记录
    @Delete("delete from group_member where student_user_id = #{studentId}")
    int deleteByStudentId(Long studentId);

    // 根据ID删除成员
    int deleteById(Long id);

    // 查询多个小组的成员数量
    // 该方法在XML文件中实现
    List<Map<String, Object>> countMemberByGroupIds(@Param("groupIds") List<Long> groupIds);
    
    // 查询所有小组成员关系
    @Select("select id, group_id, student_user_id as studentId, join_time as joinTime from group_member")
    List<GroupMember> findAll();

    // 更新小组成员信息
    @Update("update group_member set group_id = #{groupId}, student_user_id = #{studentId}, join_time = #{joinTime} where id = #{id}")
    int update(GroupMember groupMember);
}