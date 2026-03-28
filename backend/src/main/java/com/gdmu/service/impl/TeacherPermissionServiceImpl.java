package com.gdmu.service.impl;

import com.gdmu.entity.TeacherPermission;
import com.gdmu.entity.TeacherRole;
import com.gdmu.entity.TeacherRolePermission;
import com.gdmu.mapper.TeacherPermissionMapper;
import com.gdmu.mapper.TeacherRoleMapper;
import com.gdmu.mapper.TeacherRolePermissionMapper;
import com.gdmu.service.TeacherPermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TeacherPermissionServiceImpl implements TeacherPermissionService {
    
    @Autowired
    private TeacherPermissionMapper teacherPermissionMapper;
    
    @Autowired
    private TeacherRolePermissionMapper teacherRolePermissionMapper;
    
    @Autowired
    private TeacherRoleMapper teacherRoleMapper;
    
    @Override
    public List<TeacherPermission> findAll() {
        return teacherPermissionMapper.findAll();
    }
    
    @Override
    public TeacherPermission findById(Long id) {
        return teacherPermissionMapper.findById(id);
    }
    
    @Override
    public TeacherPermission findByPermissionCode(String permissionCode) {
        return teacherPermissionMapper.findByPermissionCode(permissionCode);
    }
    
    @Override
    public List<TeacherPermission> findByParentId(Long parentId) {
        return teacherPermissionMapper.findByParentId(parentId);
    }
    
    @Override
    public void insert(TeacherPermission teacherPermission) {
        teacherPermissionMapper.insert(teacherPermission);
        log.info("插入教师权限成功: {}", teacherPermission.getPermissionName());
    }
    
    @Override
    public void update(TeacherPermission teacherPermission) {
        teacherPermissionMapper.update(teacherPermission);
        log.info("更新教师权限成功: {}", teacherPermission.getPermissionName());
    }
    
    @Override
    public void deleteById(Long id) {
        teacherPermissionMapper.deleteById(id);
        log.info("删除教师权限成功: id={}", id);
    }
    
    @Override
    public List<TeacherPermission> getPermissionsByRoleId(Long roleId) {
        List<TeacherRolePermission> rolePermissions = teacherRolePermissionMapper.findByRoleId(roleId);
        List<TeacherPermission> permissions = new ArrayList<>();
        
        for (TeacherRolePermission rolePermission : rolePermissions) {
            TeacherPermission permission = teacherPermissionMapper.findById(rolePermission.getPermissionId());
            if (permission != null) {
                permission.setSortOrder(rolePermission.getSortOrder());
                permissions.add(permission);
            }
        }
        
        permissions.sort((a, b) -> {
            int orderA = a.getSortOrder() != null ? a.getSortOrder() : 0;
            int orderB = b.getSortOrder() != null ? b.getSortOrder() : 0;
            return orderA - orderB;
        });
        
        return permissions;
    }
    
    @Override
    public List<String> getPermissionCodesByRoleId(Long roleId) {
        List<TeacherPermission> permissions = getPermissionsByRoleId(roleId);
        return permissions.stream()
                .map(TeacherPermission::getPermissionCode)
                .collect(Collectors.toList());
    }
    
    @Override
    public List<String> getPermissionCodesByRoleCode(String roleCode) {
        TeacherRole role = teacherRoleMapper.findByRoleCode(roleCode);
        if (role == null) {
            log.warn("角色不存在: {}", roleCode);
            return Collections.emptyList();
        }
        return getPermissionCodesByRoleId(role.getId());
    }
    
    @Override
    public List<TeacherPermission> getPermissionsByRoleCode(String roleCode) {
        TeacherRole role = teacherRoleMapper.findByRoleCode(roleCode);
        if (role == null) {
            log.warn("角色不存在: {}", roleCode);
            return Collections.emptyList();
        }
        return getPermissionsByRoleId(role.getId());
    }
    
    @Override
    public List<TeacherRole> getAllRoles() {
        return teacherRoleMapper.findAll();
    }
    
    @Override
    public TeacherRole getRoleByRoleCode(String roleCode) {
        return teacherRoleMapper.findByRoleCode(roleCode);
    }
    
    @Override
    public List<Map<String, Object>> getPermissionTree() {
        List<TeacherPermission> allPermissions = teacherPermissionMapper.findAll();
        
        List<Map<String, Object>> tree = new ArrayList<>();
        for (TeacherPermission permission : allPermissions) {
            Map<String, Object> node = new HashMap<>();
            node.put("id", permission.getId());
            node.put("permissionName", permission.getPermissionName());
            node.put("permissionCode", permission.getPermissionCode());
            node.put("path", permission.getPath());
            node.put("icon", permission.getIcon());
            node.put("sortOrder", permission.getSortOrder());
            tree.add(node);
        }
        return tree;
    }
    
    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds, Map<Long, Integer> sortOrderMap) {
        log.info("开始为教师角色分配权限，角色ID: {}, 权限ID列表: {}, 排序: {}", roleId, permissionIds, sortOrderMap);
        
        TeacherRole role = teacherRoleMapper.findById(roleId);
        if (role == null) {
            log.error("教师角色不存在，角色ID: {}", roleId);
            throw new RuntimeException("教师角色不存在");
        }
        
        teacherRolePermissionMapper.deleteByRoleId(roleId);
        log.info("已清除角色 {} 的旧权限", role.getRoleCode());
        
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Long permissionId : permissionIds) {
                TeacherPermission permission = teacherPermissionMapper.findById(permissionId);
                if (permission != null) {
                    TeacherRolePermission rolePermission = new TeacherRolePermission();
                    rolePermission.setRoleId(roleId);
                    rolePermission.setPermissionId(permissionId);
                    rolePermission.setSortOrder(sortOrderMap != null ? sortOrderMap.getOrDefault(permissionId, 0) : 0);
                    rolePermission.setCreateTime(new Date());
                    rolePermission.setDeleted(0);
                    teacherRolePermissionMapper.insert(rolePermission);
                    log.debug("分配权限: {} -> {}, 排序: {}", role.getRoleCode(), permission.getPermissionCode(), rolePermission.getSortOrder());
                } else {
                    log.warn("权限ID {} 不存在", permissionId);
                }
            }
        }
        
        log.info("为教师角色 {} 分配权限成功，权限数量: {}", role.getRoleCode(), permissionIds != null ? permissionIds.size() : 0);
    }
}
