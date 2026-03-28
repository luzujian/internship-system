package com.gdmu.service.impl;

import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.AdminUserMapper;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.AdminUser;
import com.gdmu.service.AdminUserService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

/**
 * 管理员用户服务实现类
 */
@Slf4j
@Service
public class AdminUserServiceImpl implements AdminUserService {

    @Autowired
    private AdminUserMapper adminUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public AdminUser findByUsername(String username) {
        log.debug("查找管理员用户，用户名: {}", username);
        if (StringUtils.isBlank(username)) {
            throw new BusinessException("用户名不能为空");
        }
        return adminUserMapper.findByUsername(username);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int register(AdminUser adminUser) {
        log.debug("注册管理员用户: {}", adminUser.getUsername());
        // 参数校验
        validateAdminRegistration(adminUser);
        
        // 检查用户名是否已存在
        if (adminUserMapper.findByUsername(adminUser.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        
        // 使用 BCrypt 加密密码
        adminUser.setPassword(passwordEncoder.encode(adminUser.getPassword()));
        adminUser.setCreateTime(new Date());
        adminUser.setUpdateTime(new Date());
        
        // 默认管理员级别为1
        if (adminUser.getAdminLevel() == null) {
            adminUser.setAdminLevel(1);
        }
        
        int result = adminUserMapper.insert(adminUser);
        log.info("管理员用户注册成功，用户名: {}", adminUser.getUsername());
        return result;
    }

    @Override
    public AdminUser findById(Long id) {
        log.debug("查找管理员用户，ID: {}", id);
        if (id == null || id <= 0) {
            throw new BusinessException("用户ID无效");
        }
        return adminUserMapper.findById(id);
    }

    @Override
    public List<AdminUser> findByIds(List<Long> ids) {
        log.debug("批量查找管理员用户，用户ID列表: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return adminUserMapper.selectByIds(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int update(AdminUser adminUser) {
        log.debug("更新管理员用户信息，用户ID: {}", adminUser.getId());
        
        // 参数校验
        if (adminUser.getId() == null || adminUser.getId() <= 0) {
            throw new BusinessException("用户ID无效");
        }
        
        // 校验用户名不能为空
        if (StringUtils.isBlank(adminUser.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        
        // 检查用户是否存在
        AdminUser existingUser = adminUserMapper.findById(adminUser.getId());
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 检查用户名是否被其他用户使用
        if (!existingUser.getUsername().equals(adminUser.getUsername())) {
            AdminUser userWithSameUsername = adminUserMapper.findByUsername(adminUser.getUsername());
            if (userWithSameUsername != null) {
                throw new BusinessException("用户名已存在，请使用其他用户名");
            }
        }
        
        // 如果提供了新密码，则进行加密
        if (StringUtils.isNotBlank(adminUser.getPassword())) {
            adminUser.setPassword(passwordEncoder.encode(adminUser.getPassword()));
        } else {
            // 否则保持原密码
            adminUser.setPassword(existingUser.getPassword());
        }
        
        // 安全检查：不允许修改超级管理员的管理员级别
        if ("admin".equals(existingUser.getUsername())) {
            adminUser.setAdminLevel(existingUser.getAdminLevel());
        }
        
        adminUser.setUpdateTime(new Date());
        
        int result = adminUserMapper.update(adminUser);
        log.info("管理员用户信息更新成功，用户ID: {}", adminUser.getId());
        return result;
    }

    @Override
    public Long count() {
        log.debug("查询管理员用户总数");
        return adminUserMapper.count();
    }

    @Override
    public PageResult<AdminUser> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询管理员用户，页码: {}, 每页条数: {}", page, pageSize);
        // 设置分页参数
        PageHelper.startPage(page, pageSize);
        List<AdminUser> adminUsers = adminUserMapper.findAll();
        // 构建分页结果
        PageInfo<AdminUser> pageInfo = new PageInfo<>(adminUsers);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int delete(Long id) {
        log.debug("删除管理员用户，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("用户ID无效");
        }
        
        // 检查用户是否存在
        AdminUser adminUser = adminUserMapper.findById(id);
        if (adminUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 安全检查：不允许删除超级管理员
        if ("admin".equals(adminUser.getUsername())) {
            throw new BusinessException("无法删除超级管理员");
        }
        
        int result = adminUserMapper.deleteById(id);
        log.info("管理员用户删除成功，用户ID: {}", id);
        return result;
    }

    @Override
    public List<AdminUser> findAll() {
        log.debug("查询所有管理员用户");
        return adminUserMapper.findAll();
    }
    
    // 验证管理员用户注册信息
    private void validateAdminRegistration(AdminUser adminUser) {
        if (adminUser == null) {
            throw new BusinessException("用户信息不能为空");
        }
        
        if (StringUtils.isBlank(adminUser.getUsername())) {
            throw new BusinessException("用户名不能为空");
        }
        
        if (StringUtils.isBlank(adminUser.getPassword())) {
            throw new BusinessException("密码不能为空");
        }
        
        if (StringUtils.isBlank(adminUser.getPhone())) {
            throw new BusinessException("手机号不能为空");
        }
        
        // 部门字段不再是必填项
        // if (StringUtils.isBlank(adminUser.getDepartment())) {
        //     throw new BusinessException("所属部门不能为空");
        // }
        
        // 管理员级别必须在1-10之间
        if (adminUser.getAdminLevel() != null && (adminUser.getAdminLevel() < 1 || adminUser.getAdminLevel() > 10)) {
            throw new BusinessException("管理员级别必须在1-10之间");
        }
    }
}