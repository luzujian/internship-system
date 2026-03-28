package com.gdmu.service.impl;

import com.gdmu.entity.TeacherRolePermission;
import com.gdmu.mapper.TeacherRolePermissionMapper;
import com.gdmu.service.TeacherRolePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TeacherRolePermissionServiceImpl implements TeacherRolePermissionService {
    
    @Autowired
    private TeacherRolePermissionMapper teacherRolePermissionMapper;
    
    @Override
    public List<TeacherRolePermission> findAll() {
        return teacherRolePermissionMapper.findAll();
    }
    
    @Override
    public TeacherRolePermission findById(Long id) {
        return teacherRolePermissionMapper.findById(id);
    }
    
    @Override
    public List<TeacherRolePermission> findByRoleId(Long roleId) {
        return teacherRolePermissionMapper.findByRoleId(roleId);
    }
    
    @Override
    public List<TeacherRolePermission> findByPermissionId(Long permissionId) {
        return teacherRolePermissionMapper.findByPermissionId(permissionId);
    }
    
    @Override
    public void insert(TeacherRolePermission teacherRolePermission) {
        teacherRolePermissionMapper.insert(teacherRolePermission);
        log.info("插入角色权限关联成功: roleId={}, permissionId={}", 
                teacherRolePermission.getRoleId(), teacherRolePermission.getPermissionId());
    }
    
    @Override
    public void update(TeacherRolePermission teacherRolePermission) {
        teacherRolePermissionMapper.update(teacherRolePermission);
        log.info("更新角色权限关联成功: id={}", teacherRolePermission.getId());
    }
    
    @Override
    public void deleteById(Long id) {
        teacherRolePermissionMapper.deleteById(id);
        log.info("删除角色权限关联成功: id={}", id);
    }
    
    @Override
    public void deleteByRoleId(Long roleId) {
        teacherRolePermissionMapper.deleteByRoleId(roleId);
        log.info("删除角色的所有权限关联成功: roleId={}", roleId);
    }
}
