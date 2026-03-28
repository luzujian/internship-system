package com.gdmu.mapper;

import com.gdmu.entity.TeacherPermission;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface TeacherPermissionMapper {
    
    List<TeacherPermission> findAll();
    
    TeacherPermission findById(Long id);
    
    TeacherPermission findByPermissionCode(String permissionCode);
    
    List<TeacherPermission> findByParentId(Long parentId);
    
    void insert(TeacherPermission teacherPermission);
    
    void update(TeacherPermission teacherPermission);
    
    void deleteById(Long id);
}
