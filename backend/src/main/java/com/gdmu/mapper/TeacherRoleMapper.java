package com.gdmu.mapper;

import com.gdmu.entity.TeacherRole;
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
