package com.gdmu.service.impl;

import com.gdmu.entity.Role;
import com.gdmu.entity.Permission;
import com.gdmu.mapper.RoleMapper;
import com.gdmu.mapper.PermissionMapper;
import com.gdmu.mapper.RolePermissionMapper;
import com.gdmu.service.AdminPermissionService;
import com.gdmu.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class AdminPermissionServiceImpl implements AdminPermissionService {

    @Autowired
    private RoleMapper roleMapper;

    @Autowired
    private PermissionMapper permissionMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;
    
    @Autowired
    private UserService userService;

    @Override
    public List<Role> getAllRoles() {
        List<Role> allRoles = roleMapper.findAll();
        return allRoles.stream()
                .filter(role -> "ROLE_ADMIN".equals(role.getRoleCode()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createRole(Role role) {
        role.setStatus(1);
        roleMapper.insert(role);
        log.info("角色创建成功: {}", role.getRoleName());
    }

    @Override
    @Transactional
    public void updateRole(Role role) {
        roleMapper.update(role);
        log.info("角色更新成功: {}", role.getRoleName());
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = roleMapper.findById(id);
        if (role != null) {
            rolePermissionMapper.deleteByRoleCode(role.getRoleCode());
        }
        roleMapper.deleteById(id);
        log.info("角色删除成功: {}", id);
    }

    @Override
    public List<Permission> getAllPermissions() {
        return permissionMapper.findAll();
    }

    @Override
    public List<Permission> getRolePermissions(Long roleId) {
        return rolePermissionMapper.findPermissionsByRoleId(roleId);
    }

    @Override
    public List<Permission> getRolePermissionsByCode(String roleCode) {
        return rolePermissionMapper.findPermissionsByRoleCode(roleCode);
    }
    
    @Override
    @Transactional
    public void assignPermissions(Long roleId, List<Long> permissionIds) {
        log.info("开始为角色分配权限，角色ID: {}, 权限ID列表: {}", roleId, permissionIds);
        
        Role role = roleMapper.findById(roleId);
        if (role == null) {
            log.error("角色不存在，角色ID: {}", roleId);
            throw new RuntimeException("角色不存在");
        }
        
        List<Long> finalPermissionIds = new ArrayList<>();
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (Object permissionId : permissionIds) {
                if (permissionId instanceof Integer) {
                    finalPermissionIds.add(((Integer) permissionId).longValue());
                } else if (permissionId instanceof Long) {
                    finalPermissionIds.add((Long) permissionId);
                } else if (permissionId instanceof String) {
                    try {
                        finalPermissionIds.add(Long.parseLong((String) permissionId));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析权限ID: {}", permissionId);
                    }
                }
            }
        }
        
        log.info("清理角色 {} 的旧权限", role.getRoleCode());
        rolePermissionMapper.deleteByRoleCode(role.getRoleCode());
        
        if (!finalPermissionIds.isEmpty()) {
            log.info("为角色 {} 分配 {} 个权限", role.getRoleCode(), finalPermissionIds.size());
            for (Long permissionId : finalPermissionIds) {
                Permission permission = permissionMapper.findById(permissionId);
                if (permission != null) {
                    rolePermissionMapper.insert(role.getRoleCode(), permission.getPermissionCode());
                    log.debug("分配权限: {} -> {}", role.getRoleCode(), permission.getPermissionCode());
                } else {
                    log.warn("权限ID {} 不存在", permissionId);
                }
            }
        } else {
            log.info("权限ID列表为空，角色 {} 将没有任何权限", role.getRoleCode());
        }
        
        log.info("为角色 {} 分配权限成功，权限数量: {}", roleId, finalPermissionIds.size());
        
        // 清除缓存，确保下次查询时获取最新权限
        clearPermissionCache();
    }
    
    private void clearPermissionCache() {
        try {
            userService.clearAllUserDetailsCache();
        } catch (Exception e) {
            log.warn("清除权限缓存失败: {}", e.getMessage());
        }
    }

    @Override
    public List<Map<String, Object>> getPermissionTree() {
        List<Permission> allPermissions = permissionMapper.findAll();
        
        Map<String, List<Permission>> mainModuleMap = new LinkedHashMap<>();
        
        for (Permission permission : allPermissions) {
            String module = permission.getModule();
            String mainModule = module;
            String subModule = null;
            
            if (module.contains("-")) {
                int dashIndex = module.indexOf("-");
                mainModule = module.substring(0, dashIndex);
                subModule = module.substring(dashIndex + 1);
            }
            
            if (!mainModuleMap.containsKey(mainModule)) {
                mainModuleMap.put(mainModule, new ArrayList<>());
            }
            mainModuleMap.get(mainModule).add(permission);
        }
        
        List<String> moduleOrder = Arrays.asList(
            "用户管理",
            "撤回申请审核",
            "基础数据管理",
            "公告管理",
            "资源文档管理",
            "问题反馈管理",
            "数据统计",
            "AI配置",
            "系统管理"
        );

        List<Map<String, Object>> tree = new ArrayList<>();
        for (String mainModuleName : moduleOrder) {
            List<Permission> mainModulePermissions = mainModuleMap.get(mainModuleName);
            if (mainModulePermissions == null || mainModulePermissions.isEmpty()) {
                continue;
            }
            
            Map<String, Object> mainModuleNode = new HashMap<>();
            mainModuleNode.put("id", "main_" + mainModuleName);
            mainModuleNode.put("module", mainModuleName);
            
            Map<String, List<Permission>> subModuleMap = new LinkedHashMap<>();
            for (Permission permission : mainModulePermissions) {
                String module = permission.getModule();
                String subModule = module;
                
                if (module.contains("-")) {
                    int dashIndex = module.indexOf("-");
                    subModule = module.substring(dashIndex + 1);
                }
                
                if (!subModuleMap.containsKey(subModule)) {
                    subModuleMap.put(subModule, new ArrayList<>());
                }
                subModuleMap.get(subModule).add(permission);
            }
            
            List<Map<String, Object>> subModules = new ArrayList<>();
            for (Map.Entry<String, List<Permission>> entry : subModuleMap.entrySet()) {
                String subModuleName = entry.getKey();
                List<Permission> subModulePermissions = entry.getValue();
                
                Map<String, Object> subModuleNode = new HashMap<>();
                subModuleNode.put("id", "sub_" + mainModuleName + "_" + subModuleName);
                subModuleNode.put("module", subModuleName);
                
                List<Map<String, Object>> permissionList = new ArrayList<>();
                for (Permission permission : subModulePermissions) {
                    Map<String, Object> permissionNode = new HashMap<>();
                    permissionNode.put("id", permission.getId());
                    permissionNode.put("permissionCode", permission.getPermissionCode());
                    permissionNode.put("permissionName", permission.getPermissionName());
                    permissionList.add(permissionNode);
                }
                
                subModuleNode.put("children", permissionList);
                subModules.add(subModuleNode);
            }
            
            mainModuleNode.put("subModules", subModules);
            tree.add(mainModuleNode);
        }
        return tree;
    }
}
