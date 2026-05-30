package com.internship.service;

import com.internship.entity.AdminUser;
import com.internship.entity.PageResult;
import java.util.List;

public interface AdminUserService {
    AdminUser findByUsername(String username);
    int register(AdminUser adminUser);
    AdminUser findById(Long id);
    List<AdminUser> findByIds(List<Long> ids);
    int update(AdminUser adminUser);
    Long count();
    PageResult<AdminUser> findPage(Integer page, Integer pageSize);
    int delete(Long id);
    List<AdminUser> findAll();
}