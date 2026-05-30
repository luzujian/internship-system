package com.internship.mapper;

import com.internship.entity.TeacherRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TeacherRoleMapper {
    
    List<TeacherRole> findAll();
    
    TeacherRole findById(Long id);
    
    TeacherRole findByRoleCode(String roleCode);
    
    void insert(TeacherRole teacherRole);
    
    void update(TeacherRole teacherRole);
    
    void deleteById(Long id);
}
