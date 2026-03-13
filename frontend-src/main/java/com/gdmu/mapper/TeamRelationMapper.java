package com.gdmu.mapper;

import com.gdmu.entity.TeamRelation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TeamRelationMapper {
    
    // 根据小组ID查询成员关系
    List<TeamRelation> selectByGroupId(@Param("groupId") Long groupId);
    
    // 根据学生ID查询加入的小组
    List<TeamRelation> selectByStudentId(@Param("studentId") Long studentId);
    
    // 根据小组ID和学生ID查询关系
    TeamRelation selectByGroupAndStudent(@Param("groupId") Long groupId, @Param("studentId") Long studentId);
    
    // 插入小组关系
    int insert(TeamRelation teamRelation);
    
    // 更新小组关系
    int update(TeamRelation teamRelation);
    
    // 删除小组关系
    int deleteById(@Param("id") Long id);
    
    // 根据小组ID删除所有关系
    int deleteByGroupId(@Param("groupId") Long groupId);
    
    // 根据学生ID删除关系
    int deleteByStudentId(@Param("studentId") Long studentId);
    
    // 统计小组成员数量
    int countByGroupId(@Param("groupId") Long groupId);
    
    // 查询小组组长
    TeamRelation selectLeaderByGroupId(@Param("groupId") Long groupId);
    
    // 根据ID查询小组关系
    TeamRelation selectById(@Param("id") Long id);
}