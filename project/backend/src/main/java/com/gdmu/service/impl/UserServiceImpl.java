package com.gdmu.service.impl;

import com.gdmu.entity.AdminUser;
import com.gdmu.entity.StudentUser;
import com.gdmu.entity.TeacherUser;
import com.gdmu.entity.CompanyUser;
import com.gdmu.exception.BusinessException;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.User;
import com.gdmu.mapper.CompanyUserMapper;
import com.gdmu.mapper.RolePermissionMapper;
import com.gdmu.service.AdminUserService;
import com.gdmu.service.StudentUserService;
import com.gdmu.service.TeacherUserService;
import com.gdmu.service.UserService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

/**
 * 通用用户服务实现类
 * 处理跨角色的通用用户操作，委托特定角色操作给对应的专门服务
 */
@Slf4j
@Service
public class UserServiceImpl implements UserService, UserDetailsService {

    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Autowired
    private AdminUserService adminUserService;
    
    @Autowired
    private TeacherUserService teacherUserService;
    
    @Autowired
    private StudentUserService studentUserService;
    
    @Autowired
    private CompanyUserMapper companyUserMapper;
    
    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    private static final Map<String, UserDetails> USER_DETAILS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Long> USER_DETAILS_CACHE_TIMESTAMP = new ConcurrentHashMap<>();
    private static final long CACHE_TTL = 5 * 60 * 1000; // 5分钟缓存

    private boolean isUserDetailsCacheExpired(String username) {
        Long timestamp = USER_DETAILS_CACHE_TIMESTAMP.get(username);
        if (timestamp == null) {
            return true;
        }
        return System.currentTimeMillis() - timestamp > CACHE_TTL;
    }

    private UserDetails getCachedUserDetails(String username) {
        if (!isUserDetailsCacheExpired(username)) {
            return USER_DETAILS_CACHE.get(username);
        }
        return null;
    }

    private void cacheUserDetails(String username, UserDetails userDetails) {
        USER_DETAILS_CACHE.put(username, userDetails);
        USER_DETAILS_CACHE_TIMESTAMP.put(username, System.currentTimeMillis());
    }

    private void clearUserDetailsCache(String username) {
        USER_DETAILS_CACHE.remove(username);
        USER_DETAILS_CACHE_TIMESTAMP.remove(username);
    }

    private void clearExpiredUserDetailsCache() {
        long now = System.currentTimeMillis();
        USER_DETAILS_CACHE_TIMESTAMP.entrySet().removeIf(entry -> now - entry.getValue() > CACHE_TTL);
        USER_DETAILS_CACHE.keySet().removeIf(key -> !USER_DETAILS_CACHE_TIMESTAMP.containsKey(key));
    }

    @Override
    public void clearAllUserDetailsCache() {
        USER_DETAILS_CACHE.clear();
        USER_DETAILS_CACHE_TIMESTAMP.clear();
        log.info("已清除所有UserDetails缓存");
    }

    @Override
    public User findByUsername(String username) {
        log.info("根据用户名查找用户: {}", username);
        if (StringUtils.isBlank(username)) {
            throw new BusinessException("用户名不能为空");
        }

        var adminUser = adminUserService.findByUsername(username);
        log.info("查询admin用户结果: adminUser={}", adminUser);
        if (adminUser != null) {
            User adminBaseUser = new User();
            adminBaseUser.setId(adminUser.getId());
            adminBaseUser.setUsername(adminUser.getUsername());
            adminBaseUser.setPassword(adminUser.getPassword());
            adminBaseUser.setRole(adminUser.getRole());
            adminBaseUser.setName(adminUser.getRealName());
            adminBaseUser.setCreateTime(adminUser.getCreateTime());
            adminBaseUser.setUpdateTime(adminUser.getUpdateTime());
            log.info("返回admin用户: {}", adminBaseUser);
            return adminBaseUser;
        }

        var teacherUser = teacherUserService.findByTeacherUserId(username);
        if (teacherUser != null) {
            User teacherBaseUser = new User();
            teacherBaseUser.setId(teacherUser.getId());
            teacherBaseUser.setUsername(teacherUser.getTeacherUserId());
            teacherBaseUser.setPassword(teacherUser.getPassword());
            teacherBaseUser.setRole(teacherUser.getRole());
            teacherBaseUser.setName(teacherUser.getName());
            teacherBaseUser.setCreateTime(teacherUser.getCreateTime());
            teacherBaseUser.setUpdateTime(teacherUser.getUpdateTime());
            return teacherBaseUser;
        }

        var studentUser = studentUserService.findByStudentId(username);
        if (studentUser != null) {
            User studentBaseUser = new User();
            studentBaseUser.setId(studentUser.getId());
            studentBaseUser.setUsername(studentUser.getStudentId());
            studentBaseUser.setPassword(studentUser.getPassword());
            studentBaseUser.setRole(studentUser.getRole());
            studentBaseUser.setName(studentUser.getName());
            studentBaseUser.setCreateTime(studentUser.getCreateTime());
            studentBaseUser.setUpdateTime(studentUser.getUpdateTime());
            return studentBaseUser;
        }

        var company = companyUserMapper.findByUsername(username);
        if (company != null) {
            User companyBaseUser = new User();
            companyBaseUser.setId(company.getId());
            companyBaseUser.setUsername(company.getUsername());
            companyBaseUser.setPassword(company.getPassword());
            companyBaseUser.setRole(company.getRole());
            companyBaseUser.setName(company.getCompanyName());
            companyBaseUser.setCreateTime(company.getCreateTime());
            companyBaseUser.setUpdateTime(company.getUpdateTime());
            return companyBaseUser;
        }

        return null;
    }

    @Override
    public User findById(Long id) {
        log.debug("查找用户，ID: {}", id);
        if (id == null || id <= 0) {
            throw new BusinessException("用户ID无效");
        }
        
        // 优化：根据ID范围判断可能的用户类型，减少不必要的查询
        // 假设ID分配规则：1-1000为管理员，1001-10000为教师，10001以上为学生
        // 如果没有明确的ID分配规则，可以考虑添加用户类型字段或使用缓存
        
        // 检查教师表（教师用户数量较多，优先查询）
        var teacherUser = teacherUserService.findById(id);
        if (teacherUser != null) {
            // 转换为基础User对象
            User teacherBaseUser = new User();
            teacherBaseUser.setId(teacherUser.getId());
            teacherBaseUser.setUsername(teacherUser.getTeacherUserId());
            teacherBaseUser.setPassword(teacherUser.getPassword());
            teacherBaseUser.setRole(teacherUser.getRole());
            teacherBaseUser.setName(teacherUser.getName());
            teacherBaseUser.setCreateTime(teacherUser.getCreateTime());
            teacherBaseUser.setUpdateTime(teacherUser.getUpdateTime());
            return teacherBaseUser;
        }
        
        // 检查学生表（学生用户数量最多，第二优先查询）
        var studentUser = studentUserService.findById(id);
        if (studentUser != null) {
            // 转换为基础User对象
            User studentBaseUser = new User();
            studentBaseUser.setId(studentUser.getId());
            studentBaseUser.setUsername(studentUser.getStudentId());
            studentBaseUser.setPassword(studentUser.getPassword());
            studentBaseUser.setRole(studentUser.getRole());
            studentBaseUser.setName(studentUser.getName());
            studentBaseUser.setCreateTime(studentUser.getCreateTime());
            studentBaseUser.setUpdateTime(studentUser.getUpdateTime());
            return studentBaseUser;
        }
        
        // 检查管理员表（管理员用户数量最少，第三优先查询）
        var adminUser = adminUserService.findById(id);
        if (adminUser != null) {
            // 转换为基础User对象
            User adminBaseUser = new User();
            adminBaseUser.setId(adminUser.getId());
            adminBaseUser.setUsername(adminUser.getUsername());
            adminBaseUser.setPassword(adminUser.getPassword());
            adminBaseUser.setRole(adminUser.getRole());
            adminBaseUser.setName(adminUser.getUsername()); // 使用手机号作为姓名
            adminBaseUser.setCreateTime(adminUser.getCreateTime());
            adminBaseUser.setUpdateTime(adminUser.getUpdateTime());
            return adminBaseUser;
        }
        
        // 检查企业表（企业用户）
        var companyUser = companyUserMapper.findById(id);
        if (companyUser != null) {
            // 转换为基础User对象
            User companyBaseUser = new User();
            companyBaseUser.setId(companyUser.getId());
            companyBaseUser.setUsername(companyUser.getUsername());
            companyBaseUser.setPassword(companyUser.getPassword());
            companyBaseUser.setRole(companyUser.getRole());
            companyBaseUser.setName(companyUser.getCompanyName());
            companyBaseUser.setCreateTime(companyUser.getCreateTime());
            companyBaseUser.setUpdateTime(companyUser.getUpdateTime());
            return companyBaseUser;
        }
        
        return null;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int register(User user) {
        log.debug("注册用户: {}", user.getUsername());

        // 验证用户注册信息
        validateUserRegistration(user);

        // 检查用户名是否已存在
        if (findByUsername(user.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }

        // 设置创建和更新时间
        Date now = new Date();
        user.setCreateTime(now);
        user.setUpdateTime(now);

        // 根据角色选择不同的服务进行注册
        if ("ROLE_ADMIN".equals(user.getRole())) {
            var adminUser = new AdminUser();
            adminUser.setId(null);
            adminUser.setUsername(user.getUsername());
            adminUser.setPassword(user.getPassword());
            adminUser.setRealName(user.getName());
            adminUser.setRole(user.getRole());
            adminUser.setStatus(1);
            adminUser.setCreateTime(user.getCreateTime());
            adminUser.setUpdateTime(user.getUpdateTime());
            return adminUserService.register(adminUser);
        } else if ("ROLE_TEACHER".equals(user.getRole())) {
            var teacherUser = new TeacherUser();
            teacherUser.setId(null); // 数据库自动生成ID
            teacherUser.setUsername(user.getUsername());
            teacherUser.setTeacherUserId(user.getUsername());
            teacherUser.setPassword(user.getPassword());
            teacherUser.setRole(user.getRole());
            teacherUser.setName(user.getName());
            teacherUser.setCreateTime(user.getCreateTime());
            teacherUser.setUpdateTime(user.getUpdateTime());
            return teacherUserService.register(teacherUser);
        } else if ("ROLE_STUDENT".equals(user.getRole())) {
            var studentUser = new StudentUser();
            studentUser.setId(null); // 数据库自动生成ID
            studentUser.setUsername(user.getUsername());
            studentUser.setStudentId(user.getUsername());
            studentUser.setPassword(user.getPassword());
            studentUser.setRole(user.getRole());
            studentUser.setName(user.getName());
            studentUser.setCreateTime(user.getCreateTime());
            studentUser.setUpdateTime(user.getUpdateTime());
            return studentUserService.register(studentUser);
        }

        throw new BusinessException("用户角色无效");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int update(User user) {
        log.debug("更新用户信息，用户ID: {}", user.getId());

        // 参数校验
        if (user.getId() == null || user.getId() <= 0) {
            throw new BusinessException("用户ID无效");
        }

        // 清除缓存
        clearUserDetailsCache(user.getUsername());
        clearExpiredUserDetailsCache();

        // 设置更新时间
        user.setUpdateTime(new Date());

        // 根据角色选择不同的服务进行更新
        if ("ROLE_ADMIN".equals(user.getRole())) {
            var adminUser = new AdminUser();
            adminUser.setId(user.getId());
            adminUser.setUsername(user.getUsername());
            adminUser.setRole(user.getRole());
            adminUser.setUpdateTime(user.getUpdateTime());

            // 直接传递密码，由adminUserService.update方法负责加密
            adminUser.setPassword(user.getPassword());

            return adminUserService.update(adminUser);
        } else if ("ROLE_TEACHER".equals(user.getRole())) {
            var teacherUser = new TeacherUser();
            teacherUser.setId(user.getId());
            teacherUser.setUsername(user.getUsername());
            teacherUser.setTeacherUserId(user.getUsername());
            teacherUser.setRole(user.getRole());
            teacherUser.setName(user.getName());
            teacherUser.setUpdateTime(user.getUpdateTime());

            // 直接传递密码，由teacherUserService.update方法负责加密
            teacherUser.setPassword(user.getPassword());

            return teacherUserService.update(teacherUser);
        } else if ("ROLE_STUDENT".equals(user.getRole())) {
            var studentUser = new StudentUser();
            studentUser.setId(user.getId());
            studentUser.setUsername(user.getUsername());
            studentUser.setStudentId(user.getUsername());
            studentUser.setRole(user.getRole());
            studentUser.setName(user.getName());
            studentUser.setUpdateTime(user.getUpdateTime());

            // 直接传递密码，由studentUserService.update方法负责加密
            studentUser.setPassword(user.getPassword());

            return studentUserService.update(studentUser);
        } else if ("ROLE_COMPANY".equals(user.getRole())) {
            var companyUser = companyUserMapper.findById(user.getId());
            if (companyUser == null) {
                throw new BusinessException("企业用户不存在");
            }
            companyUser.setUsername(user.getUsername());
            companyUser.setRole(user.getRole());
            companyUser.setUpdateTime(user.getUpdateTime());
            
            // 直接传递密码，由mapper负责加密
            companyUser.setPassword(user.getPassword());

            return companyUserMapper.update(companyUser);
        }

        throw new BusinessException("用户角色无效");
    }

    @Override
    public Long count() {
        log.debug("统计用户总数");
        // 统计所有用户表中的用户数量
        Long adminCount = adminUserService.count();
        Long teacherCount = teacherUserService.count();
        Long studentCount = studentUserService.count();
        Long companyCount = companyUserMapper.count();
        return adminCount + teacherCount + studentCount + companyCount;
    }

    @Override
    public PageResult<User> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询用户列表，页码: {}, 每页记录数: {}", page, pageSize);

        // 参数校验
        if (page == null || page <= 0) {
            page = 1;
        }
        if (pageSize == null || pageSize <= 0) {
            pageSize = 10;
        }

        // 实际应用中，需要分别查询各个用户表并合并结果
        // 这里实现了简单的分页查询，将所有用户类型的数据合并后返回
        List<User> allUsers = new ArrayList<>();

        // 查询所有管理员用户
        List<AdminUser> adminUsers = adminUserService.findAll();
        for (AdminUser admin : adminUsers) {
            User user = new User();
            user.setId(admin.getId());
            user.setUsername(admin.getUsername());
            user.setName(admin.getRealName());
            user.setRole("ROLE_ADMIN");
            user.setCreateTime(admin.getCreateTime());
            user.setUpdateTime(admin.getUpdateTime());
            allUsers.add(user);
        }

        // 查询所有教师用户
        List<TeacherUser> teacherUsers = teacherUserService.findAll(null, null, null);
        for (TeacherUser teacher : teacherUsers) {
            User user = new User();
            user.setId(teacher.getId());
            user.setUsername(teacher.getTeacherUserId()); // 使用teacherUserId作为username
            user.setName(teacher.getName());
            user.setRole("ROLE_TEACHER");
            user.setCreateTime(teacher.getCreateTime());
            user.setUpdateTime(teacher.getUpdateTime());
            allUsers.add(user);
        }

        // 查询所有学生用户
        List<StudentUser> studentUsers = studentUserService.findAll(null, null, null, null, null);
        for (StudentUser student : studentUsers) {
            User user = new User();
            user.setId(student.getId());
            user.setUsername(student.getStudentId()); // 使用studentId作为username
            user.setName(student.getName());
            user.setRole("ROLE_STUDENT");
            user.setCreateTime(student.getCreateTime());
            user.setUpdateTime(student.getUpdateTime());
            allUsers.add(user);
        }

        // 查询所有企业用户
        List<CompanyUser> companyUsers = companyUserMapper.findAll();
        for (CompanyUser company : companyUsers) {
            User user = new User();
            user.setId(company.getId());
            user.setUsername(company.getUsername());
            user.setName(company.getCompanyName());
            user.setRole("ROLE_COMPANY");
            user.setCreateTime(company.getCreateTime());
            user.setUpdateTime(company.getUpdateTime());
            allUsers.add(user);
        }

        // 计算总数
        long total = allUsers.size();

        // 实现简单的分页逻辑
        int startIndex = (page - 1) * pageSize;
        int endIndex = Math.min(startIndex + pageSize, allUsers.size());
        List<User> pageData = allUsers.subList(startIndex, endIndex);

        return PageResult.build(total, pageData);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int delete(Long id) {
        log.debug("删除用户，用户ID: {}", id);

        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("用户ID无效");
        }

        // 尝试在各个用户表中删除
        try {
            int result = adminUserService.delete(id);
            if (result > 0) {
                log.info("管理员用户删除成功，用户ID: {}", id);
                return result;
            }
        } catch (Exception e) {
            log.debug("管理员用户删除失败，用户ID: {}，错误: {}", id, e.getMessage());
        }

        try {
            int result = teacherUserService.delete(id);
            if (result > 0) {
                log.info("教师用户删除成功，用户ID: {}", id);
                return result;
            }
        } catch (Exception e) {
            log.debug("教师用户删除失败，用户ID: {}，错误: {}", id, e.getMessage());
        }

        try {
            int result = studentUserService.delete(id);
            if (result > 0) {
                log.info("学生用户删除成功，用户ID: {}", id);
                return result;
            }
        } catch (Exception e) {
            log.debug("学生用户删除失败，用户ID: {}，错误: {}", id, e.getMessage());
        }

        try {
            int result = companyUserMapper.deleteById(id);
            if (result > 0) {
                log.info("企业用户删除成功，用户ID: {}", id);
                return result;
            }
        } catch (Exception e) {
            log.debug("企业用户删除失败，用户ID: {}，错误: {}", id, e.getMessage());
        }

        throw new BusinessException("用户不存在");
    }

    @Override
    public List<User> findByRole(String role) {
        log.debug("按角色查询用户，角色: {}", role);
        if (StringUtils.isBlank(role)) {
            throw new BusinessException("角色不能为空");
        }

        List<User> resultList = new ArrayList<>();

        if ("ROLE_ADMIN".equals(role)) {
            var adminUsers = adminUserService.findAll();
            for (var adminUser : adminUsers) {
                User baseUser = new User();
                baseUser.setId(adminUser.getId());
                baseUser.setUsername(adminUser.getUsername());
                baseUser.setPassword(adminUser.getPassword());
                baseUser.setRole(adminUser.getRole());
                baseUser.setName(adminUser.getUsername());
                baseUser.setCreateTime(adminUser.getCreateTime());
                baseUser.setUpdateTime(adminUser.getUpdateTime());
                resultList.add(baseUser);
            }
        } else if ("ROLE_TEACHER".equals(role) || "ROLE_TEACHER_COLLEGE".equals(role) || "ROLE_TEACHER_DEPARTMENT".equals(role) || "ROLE_TEACHER_COUNSELOR".equals(role)) {
            var teacherUsers = teacherUserService.findAll(null, null, null);
            for (var teacherUser : teacherUsers) {
                // 根据查询的角色过滤用户
                if (!role.equals(teacherUser.getRole())) {
                    continue;
                }
                User baseUser = new User();
                baseUser.setId(teacherUser.getId());
                baseUser.setUsername(teacherUser.getTeacherUserId());
                baseUser.setPassword(teacherUser.getPassword());
                baseUser.setRole(teacherUser.getRole());
                baseUser.setName(teacherUser.getName());
                baseUser.setCreateTime(teacherUser.getCreateTime());
                baseUser.setUpdateTime(teacherUser.getUpdateTime());
                resultList.add(baseUser);
            }
        } else if ("ROLE_STUDENT".equals(role)) {
            var studentUsers = studentUserService.findAll(null, null, null, null, null);
            for (var studentUser : studentUsers) {
                User baseUser = new User();
                baseUser.setId(studentUser.getId());
                baseUser.setUsername(studentUser.getStudentId());
                baseUser.setPassword(studentUser.getPassword());
                baseUser.setRole(studentUser.getRole());
                baseUser.setName(studentUser.getName());
                baseUser.setCreateTime(studentUser.getCreateTime());
                baseUser.setUpdateTime(studentUser.getUpdateTime());
                resultList.add(baseUser);
            }
        } else if ("ROLE_COMPANY".equals(role)) {
            var companyUsers = companyUserMapper.findAll();
            for (var companyUser : companyUsers) {
                User baseUser = new User();
                baseUser.setId(companyUser.getId());
                baseUser.setUsername(companyUser.getUsername());
                baseUser.setPassword(companyUser.getPassword());
                baseUser.setRole(companyUser.getRole());
                baseUser.setName(companyUser.getCompanyName());
                baseUser.setCreateTime(companyUser.getCreateTime());
                baseUser.setUpdateTime(companyUser.getUpdateTime());
                resultList.add(baseUser);
            }
        } else {
            throw new BusinessException("无效的用户角色");
        }

        return resultList;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updatePassword(String username, String newPassword) {
        log.debug("更新用户密码，用户名: {}", username);
        
        if (StringUtils.isBlank(username)) {
            throw new BusinessException("用户名不能为空");
        }
        if (StringUtils.isBlank(newPassword) || newPassword.length() < 6) {
            throw new BusinessException("密码不能为空且长度不能少于6个字符");
        }
        
        // 清除缓存
        clearUserDetailsCache(username);
        clearExpiredUserDetailsCache();
        
        var adminUser = adminUserService.findByUsername(username);
        if (adminUser != null) {
            adminUser.setPassword(newPassword);
            adminUser.setUpdateTime(new Date());
            int result = adminUserService.update(adminUser);
            log.info("管理员用户密码更新成功，用户名: {}", username);
            return result;
        }
        
        var teacherUser = teacherUserService.findByTeacherUserId(username);
        if (teacherUser != null) {
            teacherUser.setPassword(newPassword);
            teacherUser.setUpdateTime(new Date());
            int result = teacherUserService.update(teacherUser);
            log.info("教师用户密码更新成功，用户名: {}", username);
            return result;
        }
        
        var studentUser = studentUserService.findByStudentId(username);
        if (studentUser != null) {
            studentUser.setPassword(newPassword);
            studentUser.setUpdateTime(new Date());
            int result = studentUserService.update(studentUser);
            log.info("学生用户密码更新成功，用户名: {}", username);
            return result;
        }
        
        var companyUser = companyUserMapper.findByUsername(username);
        if (companyUser != null) {
            companyUser.setPassword(newPassword);
            companyUser.setUpdateTime(new Date());
            int result = companyUserMapper.update(companyUser);
            log.info("企业用户密码更新成功，用户名: {}", username);
            return result;
        }
        
        throw new BusinessException("用户不存在");
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int updateUsername(String oldUsername, String newUsername) {
        log.debug("更新用户名，旧用户名: {}, 新用户名: {}", oldUsername, newUsername);
        
        if (StringUtils.isBlank(oldUsername) || StringUtils.isBlank(newUsername)) {
            throw new BusinessException("用户名不能为空");
        }
        
        if (newUsername.length() < 3 || newUsername.length() > 20) {
            throw new BusinessException("用户名长度必须在3-20个字符之间");
        }
        
        if (oldUsername.equals(newUsername)) {
            throw new BusinessException("新用户名不能与旧用户名相同");
        }
        
        // 检查新用户名是否已存在
        if (findByUsername(newUsername) != null) {
            throw new BusinessException("用户名已存在");
        }
        
        // 清除缓存
        clearUserDetailsCache(oldUsername);
        clearExpiredUserDetailsCache();
        
        var adminUser = adminUserService.findByUsername(oldUsername);
        if (adminUser != null) {
            adminUser.setUsername(newUsername);
            adminUser.setUpdateTime(new Date());
            int result = adminUserService.update(adminUser);
            log.info("管理员用户名更新成功，旧用户名: {}, 新用户名: {}", oldUsername, newUsername);
            return result;
        }
        
        var teacherUser = teacherUserService.findByTeacherUserId(oldUsername);
        if (teacherUser != null) {
            teacherUser.setUsername(newUsername);
            teacherUser.setUpdateTime(new Date());
            int result = teacherUserService.update(teacherUser);
            log.info("教师用户名更新成功，旧用户名: {}, 新用户名: {}", oldUsername, newUsername);
            return result;
        }
        
        var studentUser = studentUserService.findByStudentId(oldUsername);
        if (studentUser != null) {
            studentUser.setUsername(newUsername);
            studentUser.setUpdateTime(new Date());
            int result = studentUserService.update(studentUser);
            log.info("学生用户名更新成功，旧用户名: {}, 新用户名: {}", oldUsername, newUsername);
            return result;
        }
        
        var companyUser = companyUserMapper.findByUsername(oldUsername);
        if (companyUser != null) {
            companyUser.setUsername(newUsername);
            companyUser.setUpdateTime(new Date());
            int result = companyUserMapper.update(companyUser);
            log.info("企业用户名更新成功，旧用户名: {}, 新用户名: {}", oldUsername, newUsername);
            return result;
        }
        
        throw new BusinessException("用户不存在");
    }

    @Override
    public List<User> findByIds(List<Long> ids) {
        log.debug("批量查找用户，用户ID列表: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<User> resultList = new ArrayList<>();

        // 分别在各个用户表中查找并合并结果
        for (Long id : ids) {
            try {
                User user = findById(id);
                if (user != null) {
                    resultList.add(user);
                }
            } catch (BusinessException e) {
                // 用户不存在，继续查找其他用户
                log.debug("用户ID {} 不存在: {}", id, e.getMessage());
            }
        }

        return resultList;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        log.debug("Spring Security 身份验证，用户名: {}", username);

        // 先尝试从缓存获取
        UserDetails cachedUserDetails = getCachedUserDetails(username);
        if (cachedUserDetails != null) {
            log.debug("从缓存获取UserDetails: {}", username);
            return cachedUserDetails;
        }

        // 缓存未命中，从数据库查询
        UserDetails userDetails = loadUserDetailsFromDB(username);
        
        // 缓存结果
        if (userDetails != null) {
            cacheUserDetails(username, userDetails);
        }

        return userDetails;
    }

    private UserDetails loadUserDetailsFromDB(String username) throws UsernameNotFoundException {
        var adminUser = adminUserService.findByUsername(username);
        if (adminUser != null) {
            return createAdminUserDetails(adminUser);
        }

        var teacherUser = teacherUserService.findByTeacherUserId(username);
        if (teacherUser != null) {
            return createTeacherUserDetails(teacherUser);
        }

        var studentUser = studentUserService.findByStudentId(username);
        if (studentUser != null) {
            return createStudentUserDetails(studentUser);
        }

        var companyUser = companyUserMapper.findByUsername(username);
        if (companyUser != null) {
            return createCompanyUserDetails(companyUser);
        }

        log.warn("用户不存在: {}", username);
        throw new UsernameNotFoundException("用户不存在: " + username);
    }

    private UserDetails createAdminUserDetails(AdminUser adminUser) {
        String password = adminUser.getPassword();
        if (StringUtils.isBlank(password)) {
            log.error("管理员用户 {} 的密码为空，无法创建UserDetails", adminUser.getUsername());
            password = "defaultpassword";
        }

        String role = adminUser.getRole();
        if (StringUtils.isBlank(role)) {
            log.error("管理员用户 {} 的角色为空，无法创建UserDetails", adminUser.getUsername());
            role = "ROLE_ADMIN";
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        
        try {
            List<com.gdmu.entity.Permission> permissions = rolePermissionMapper.findPermissionsByRoleCode(role);
            if (permissions != null && !permissions.isEmpty()) {
                List<SimpleGrantedAuthority> permissionAuthorities = permissions.stream()
                    .map(p -> new SimpleGrantedAuthority(p.getPermissionCode()))
                    .collect(Collectors.toList());
                authorities.addAll(permissionAuthorities);
                log.debug("管理员用户 {} 加载了 {} 个权限", adminUser.getUsername(), permissions.size());
            }
        } catch (Exception e) {
            log.warn("加载管理员用户 {} 的权限失败: {}", adminUser.getUsername(), e.getMessage());
        }

        return new org.springframework.security.core.userdetails.User(
                adminUser.getUsername(),
                password,
                authorities
        );
    }

    private UserDetails createTeacherUserDetails(TeacherUser teacherUser) {
        String password = teacherUser.getPassword();
        if (StringUtils.isBlank(password)) {
            log.error("教师用户 {} 的密码为空，无法创建UserDetails", teacherUser.getTeacherUserId());
            password = "defaultpassword";
        }

        String role = teacherUser.getRole();
        if (StringUtils.isBlank(role)) {
            log.error("教师用户 {} 的角色为空，无法创建UserDetails", teacherUser.getTeacherUserId());
            role = "ROLE_TEACHER";
        }

        String teacherType = teacherUser.getTeacherType();
        String roleCodeForPermission = role;
        
        if (StringUtils.isNotBlank(teacherType)) {
            roleCodeForPermission = switch (teacherType) {
                case "COLLEGE" -> "ROLE_TEACHER_COLLEGE";
                case "DEPARTMENT" -> "ROLE_TEACHER_DEPARTMENT";
                case "COUNSELOR" -> "ROLE_TEACHER_COUNSELOR";
                default -> "ROLE_TEACHER";
            };
        }

        log.info("创建教师UserDetails，用户: {}, 基础角色: {}, 教师类型: {}, 权限查询角色: {}", 
                teacherUser.getTeacherUserId(), role, teacherType, roleCodeForPermission);

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority(role));
        
        try {
            List<com.gdmu.entity.Permission> permissions = rolePermissionMapper.findPermissionsByRoleCode(roleCodeForPermission);
            log.info("从数据库查询到 {} 个权限，角色: {}", permissions != null ? permissions.size() : 0, roleCodeForPermission);
            if (permissions != null && !permissions.isEmpty()) {
                List<SimpleGrantedAuthority> permissionAuthorities = permissions.stream()
                    .map(p -> new SimpleGrantedAuthority(p.getPermissionCode()))
                    .collect(Collectors.toList());
                authorities.addAll(permissionAuthorities);
                log.info("教师用户 {} 加载了 {} 个权限，权限列表: {}", 
                    teacherUser.getTeacherUserId(), permissions.size(), 
                    permissions.stream().map(com.gdmu.entity.Permission::getPermissionCode).collect(Collectors.toList()));
            }
        } catch (Exception e) {
            log.error("加载教师用户 {} 的权限失败: {}", teacherUser.getTeacherUserId(), e.getMessage(), e);
        }

        log.info("教师用户 {} 最终权限列表: {}", teacherUser.getTeacherUserId(), 
            authorities.stream().map(SimpleGrantedAuthority::getAuthority).collect(Collectors.toList()));

        return new org.springframework.security.core.userdetails.User(
                teacherUser.getTeacherUserId(),
                password,
                authorities
        );
    }

    private UserDetails createStudentUserDetails(StudentUser studentUser) {
        String password = studentUser.getPassword();
        if (StringUtils.isBlank(password)) {
            log.error("学生用户 {} 的密码为空，无法创建UserDetails", studentUser.getStudentId());
            password = "defaultpassword";
        }

        String role = studentUser.getRole();
        if (StringUtils.isBlank(role)) {
            log.error("学生用户 {} 的角色为空，无法创建UserDetails", studentUser.getStudentId());
            role = "ROLE_STUDENT";
        }

        return new org.springframework.security.core.userdetails.User(
                studentUser.getStudentId(),
                password,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }

    private UserDetails createCompanyUserDetails(CompanyUser companyUser) {
        String password = companyUser.getPassword();
        if (StringUtils.isBlank(password)) {
            log.error("企业用户 {} 的密码为空，无法创建UserDetails", companyUser.getUsername());
            password = "defaultpassword";
        }

        String role = companyUser.getRole();
        if (StringUtils.isBlank(role)) {
            log.error("企业用户 {} 的角色为空，无法创建UserDetails", companyUser.getUsername());
            role = "ROLE_COMPANY";
        }

        return new org.springframework.security.core.userdetails.User(
                companyUser.getUsername(),
                password,
                Collections.singletonList(new SimpleGrantedAuthority(role))
        );
    }

    /**
     * 验证用户注册信息
     */
    private void validateUserRegistration(User user) {
        if (user == null) {
            throw new BusinessException("用户信息不能为空");
        }
        if (StringUtils.isBlank(user.getUsername()) || user.getUsername().length() < 3) {
            throw new BusinessException("用户名不能为空且长度不能少于3个字符");
        }
        if (StringUtils.isBlank(user.getPassword()) || user.getPassword().length() < 6) {
            throw new BusinessException("密码不能为空且长度不能少于6个字符");
        }
        if (StringUtils.isBlank(user.getName())) {
            throw new BusinessException("姓名不能为空");
        }
        if (StringUtils.isBlank(user.getRole())) {
            throw new BusinessException("角色不能为空");
        }
    }

    @Override
    public List<Map<String, Object>> findStudentsByCourseIds(List<Long> courseIds, String searchName, Long classId) {
        log.debug("根据课程ID列表查询学生信息: courseIds={}, searchName={}, classId={}", courseIds, searchName, classId);
        
        // 委托给学生用户服务处理
        return studentUserService.findStudentsByCourseIds(courseIds, searchName, classId);
    }
    
    @Override
    public List<AdminUser> getAllAdminUsers() {
        log.debug("获取所有管理员用户");
        return adminUserService.findAll();
    }
    
    @Override
    public List<TeacherUser> getTeachersByType(String teacherType) {
        log.debug("根据教师类型获取教师列表: teacherType={}", teacherType);
        return teacherUserService.findByTeacherType(teacherType);
    }
}