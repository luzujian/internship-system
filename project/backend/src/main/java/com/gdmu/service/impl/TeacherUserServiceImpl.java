package com.gdmu.service.impl;

import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.TeacherUserMapper;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Department;
import com.gdmu.entity.TeacherUser;
import com.gdmu.service.DepartmentService;
import com.gdmu.service.TeacherUserService;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * 教师用户服务实现类
 */
@Slf4j
@Service
public class TeacherUserServiceImpl implements TeacherUserService {

    @Autowired
    private TeacherUserMapper teacherUserMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DepartmentService departmentService;

    @Override
    public TeacherUser findByTeacherUserId(String teacherUserId) {
        log.debug("查找教师用户，教师编号: {}", teacherUserId);
        if (StringUtils.isBlank(teacherUserId)) {
            throw new BusinessException("教师编号不能为空");
        }
        // 直接返回查询结果，不抛出异常，允许调用者继续尝试其他用户类型
        return teacherUserMapper.findByTeacherUserId(teacherUserId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int register(TeacherUser teacherUser) {
        log.debug("注册教师用户: {}", teacherUser.getTeacherUserId());
        // 参数校验
        validateTeacherRegistration(teacherUser);
        
        // 检查教师编号是否已存在
        if (teacherUserMapper.findByTeacherUserId(teacherUser.getTeacherUserId()) != null) {
            throw new BusinessException("教师编号已存在");
        }
        
        // 设置username字段，与teacherUserId相同
        if (teacherUser.getUsername() == null || teacherUser.getUsername().isEmpty()) {
            teacherUser.setUsername(teacherUser.getTeacherUserId());
        }
        
        // 直接使用原始密码，不加密
        teacherUser.setPassword(teacherUser.getPassword());
        teacherUser.setCreateTime(new Date());
        teacherUser.setUpdateTime(new Date());
        
        // 如果没有设置性别，默认设置为1（男）
        if (teacherUser.getGender() == null) {
            teacherUser.setGender(1);
        }
        
        // 如果没有设置教师类型，默认设置为系室教师
        if (teacherUser.getTeacherType() == null || teacherUser.getTeacherType().isEmpty()) {
            teacherUser.setTeacherType("DEPARTMENT");
        }
        
        // 根据教师类型设置角色
        String role = switch (teacherUser.getTeacherType()) {
            case "COLLEGE" -> "ROLE_TEACHER_COLLEGE";
            case "DEPARTMENT" -> "ROLE_TEACHER_DEPARTMENT";
            case "COUNSELOR" -> "ROLE_TEACHER_COUNSELOR";
            default -> "ROLE_TEACHER";
        };
        teacherUser.setRole(role);
        
        int result = teacherUserMapper.insert(teacherUser);
        log.info("教师用户注册成功，教师编号: {}, 类型: {}, 角色: {}", teacherUser.getTeacherUserId(), teacherUser.getTeacherType(), role);
        return result;
    }

    @Override
    public TeacherUser findById(Long id) {
        log.debug("查找教师用户，ID: {}", id);
        if (id == null || id <= 0) {
            throw new BusinessException("用户ID无效");
        }
        return teacherUserMapper.findById(id);
    }

    @Override
    public List<TeacherUser> findByIds(List<Long> ids) {
        log.debug("批量查找教师用户，用户ID列表: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return teacherUserMapper.selectByIds(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int update(TeacherUser teacherUser) {
        log.debug("更新教师用户信息，用户ID: {}", teacherUser.getId());
        
        // 参数校验
        if (teacherUser.getId() == null || teacherUser.getId() <= 0) {
            throw new BusinessException("用户ID无效");
        }
        
        // 检查用户是否存在
        TeacherUser existingUser = teacherUserMapper.findById(teacherUser.getId());
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        // 如果提供了新密码，则直接使用原始密码
        if (StringUtils.isNotBlank(teacherUser.getPassword())) {
            // 直接使用原始密码，不加密
        } else {
            // 否则保持原密码
            teacherUser.setPassword(existingUser.getPassword());
        }
        
        // 如果更新了教师类型，需要同步更新角色
        if (teacherUser.getTeacherType() != null && !teacherUser.getTeacherType().isEmpty()) {
            String role = switch (teacherUser.getTeacherType()) {
                case "COLLEGE" -> "ROLE_TEACHER_COLLEGE";
                case "DEPARTMENT" -> "ROLE_TEACHER_DEPARTMENT";
                case "COUNSELOR" -> "ROLE_TEACHER_COUNSELOR";
                default -> "ROLE_TEACHER";
            };
            teacherUser.setRole(role);
        } else {
            // 如果没有更新教师类型，保持原角色
            teacherUser.setRole(existingUser.getRole());
        }
        
        teacherUser.setUpdateTime(new Date());
        
        int result = teacherUserMapper.update(teacherUser);
        log.info("教师用户信息更新成功，用户ID: {}, 类型: {}, 角色: {}", teacherUser.getId(), teacherUser.getTeacherType(), teacherUser.getRole());
        return result;
    }

    @Override
    public Long count() {
        log.debug("查询教师用户总数");
        return teacherUserMapper.count();
    }

    @Override
    public PageResult<TeacherUser> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询教师用户，页码: {}, 每页条数: {}", page, pageSize);
        // 设置分页参数
        PageHelper.startPage(page, pageSize);
        List<TeacherUser> teacherUsers = teacherUserMapper.findAll();
        // 构建分页结果
        PageInfo<TeacherUser> pageInfo = new PageInfo<>(teacherUsers);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }
    
    @Override
    public PageResult<TeacherUser> findPageByCondition(Integer page, Integer pageSize, Map<String, Object> params) {
        log.debug("条件分页查询教师用户，页码: {}, 每页条数: {}, 查询条件: {}", page, pageSize, params);
        // 设置分页参数
        PageHelper.startPage(page, pageSize);
        
        // 直接从params中获取参数，不进行不必要的类型转换
        String teacherUserId = (params.get("teacherUserId") != null) ? params.get("teacherUserId").toString() : null;
        String name = (params.get("name") != null) ? params.get("name").toString() : null;
        
        Object departmentId = params.get("departmentId");
        if (departmentId == null) {
            departmentId = params.get("department");
        }
        log.debug("获取到的院系ID: {}, 类型: {}", departmentId, departmentId != null ? departmentId.getClass() : "null");
        
        String deptIdStr = null;
        if (departmentId != null) {
            deptIdStr = String.valueOf(departmentId);
            log.debug("转换后的院系ID(String): {}", deptIdStr);
        }
        
        // 获取status参数
        Integer status = null;
        if (params.get("status") != null) {
            try {
                status = Integer.valueOf(params.get("status").toString());
                log.debug("转换后的状态值(Integer): {}", status);
            } catch (NumberFormatException e) {
                log.error("状态值转换为Integer失败: {}", e.getMessage());
            }
        }
        
        // 获取gender参数
        Integer gender = null;
        if (params.get("gender") != null) {
            try {
                gender = Integer.valueOf(params.get("gender").toString());
                log.debug("转换后的性别值(Integer): {}", gender);
            } catch (NumberFormatException e) {
                log.error("性别值转换为Integer失败: {}", e.getMessage());
            }
        }
        
        // 获取teacherType参数
        String teacherType = null;
        if (params.get("teacherType") != null) {
            teacherType = params.get("teacherType").toString();
            log.debug("获取到的教师类型: {}", teacherType);
        }
        
        // 直接将参数传递给mapper
        List<TeacherUser> teacherUsers = teacherUserMapper.list(teacherUserId, name, deptIdStr, status, gender, teacherType);
        
        // 添加日志记录查询结果
        log.debug("查询到的教师用户数量: {}", teacherUsers != null ? teacherUsers.size() : 0);
        
        // 构建分页结果
        PageInfo<TeacherUser> pageInfo = new PageInfo<>(teacherUsers);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int delete(Long id) {
        log.debug("删除教师用户，ID: {}", id);
        
        // 参数校验
        if (id == null || id <= 0) {
            throw new BusinessException("用户ID无效");
        }
        
        // 检查用户是否存在
        TeacherUser teacherUser = teacherUserMapper.findById(id);
        if (teacherUser == null) {
            throw new BusinessException("用户不存在");
        }
        
        int result = teacherUserMapper.deleteById(id);
        log.info("教师用户删除成功，用户ID: {}", id);
        return result;
    }
    
    @Override
    public List<TeacherUser> findAll(String teacherUserId, String name, String departmentId) {
        log.info("查询所有教师数据，筛选条件: 工号={}, 姓名={}, 院系ID={}", teacherUserId, name, departmentId);
        return teacherUserMapper.findAll(teacherUserId, name, departmentId);
    }
    
    @Transactional(rollbackFor = Exception.class)
    @Override
    public int batchDelete(List<Long> ids) {
        log.debug("批量删除教师用户，ID列表: {}", ids);
        
        // 参数校验
        if (ids == null || ids.isEmpty()) {
            throw new BusinessException("ID列表不能为空");
        }
        
        // 检查ID列表中是否有无效ID
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new BusinessException("存在无效的用户ID");
            }
        }
        
        int result = teacherUserMapper.batchDeleteByIds(ids);
        log.info("批量删除教师用户成功，删除数量: {}", result);
        return result;
    }
    
    @Override
    public Map<String, Object> importFromExcel(List<Map<String, Object>> teacherDataList) {
        log.info("开始从Excel导入教师数据，总数据量: {}", teacherDataList != null ? teacherDataList.size() : 0);
        
        // 结果统计
        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> failList = new ArrayList<>();
        
        if (teacherDataList == null || teacherDataList.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failList", failList);
            return result;
        }
        
        // 获取所有已存在的教师工号，用于批量检查重复
        List<String> allTeacherIds = new ArrayList<>();
        for (Map<String, Object> rowData : teacherDataList) {
            String teacherUserId = getStringValue(rowData, "工号");
            if (StringUtils.isNotBlank(teacherUserId)) {
                allTeacherIds.add(teacherUserId);
            }
        }
        
        // 批量查询已存在的工号
        Map<String, TeacherUser> existingTeacherMap = new HashMap<>();
        if (!allTeacherIds.isEmpty()) {
            List<TeacherUser> existingTeachers = teacherUserMapper.findByTeacherIds(allTeacherIds);
            for (TeacherUser teacher : existingTeachers) {
                existingTeacherMap.put(teacher.getTeacherUserId(), teacher);
            }
        }
        
        // 遍历Excel数据，转换为TeacherUser对象并验证
        List<TeacherUser> validTeacherList = new ArrayList<>();
        
        for (int i = 0; i < teacherDataList.size(); i++) {
            Map<String, Object> rowData = teacherDataList.get(i);
            Map<String, Object> failInfo = new HashMap<>();
            failInfo.put("rowNum", i + 2); // Excel行号从1开始，数据从第2行开始
            failInfo.putAll(rowData);
            
            try {
                // 转换为TeacherUser对象
                TeacherUser teacherUser = convertToTeacherUser(rowData);
                
                // 检查工号是否已存在
                if (existingTeacherMap.containsKey(teacherUser.getTeacherUserId())) {
                    failInfo.put("errorMsg", "工号已存在");
                    failList.add(failInfo);
                    failCount++;
                    continue;
                }
                
                // 验证数据有效性
                validateTeacherRegistration(teacherUser);
                
                // 添加到有效列表
                validTeacherList.add(teacherUser);
                existingTeacherMap.put(teacherUser.getTeacherUserId(), teacherUser); // 防止同一文件内重复
                
            } catch (Exception e) {
                log.error("导入教师数据失败，行号: {}", i + 2, e);
                failInfo.put("errorMsg", e.getMessage());
                failList.add(failInfo);
                failCount++;
            }
        }
        
        // 批量插入有效数据
        if (!validTeacherList.isEmpty()) {
            // 分批插入，每批最多100条
            final int BATCH_SIZE = 100;
            int totalSize = validTeacherList.size();
            int batchCount = (totalSize + BATCH_SIZE - 1) / BATCH_SIZE;
            
            for (int i = 0; i < batchCount; i++) {
                int start = i * BATCH_SIZE;
                int end = Math.min(start + BATCH_SIZE, totalSize);
                List<TeacherUser> batchList = validTeacherList.subList(start, end);
                
                int batchResult = teacherUserMapper.batchInsert(batchList);
                successCount += batchResult;
                
                log.info("导入批次 {} 成功，导入数量: {}", i + 1, batchResult);
            }
        }
        
        log.info("Excel教师数据导入完成，成功: {}, 失败: {}", successCount, failCount);
        
        // 返回导入结果
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failList", failList);
        
        return result;
    }
    
    // 将Excel行数据转换为TeacherUser对象
    private TeacherUser convertToTeacherUser(Map<String, Object> rowData) {
        log.debug("处理Excel行数据: {}", rowData);
        TeacherUser teacherUser = new TeacherUser();
        
        String teacherUserId = getStringValue(rowData, "工号");
        String name = getStringValue(rowData, "姓名");
        String genderStr = getStringValue(rowData, "性别");
        String teacherTypeStr = getStringValue(rowData, "身份");
        String departmentName = getStringValue(rowData, "院系");
        
        teacherUser.setUsername(teacherUserId);
        teacherUser.setTeacherUserId(teacherUserId);
        teacherUser.setName(name);
        teacherUser.setPassword("123456");
        teacherUser.setCreateTime(new Date());
        teacherUser.setUpdateTime(new Date());
        
        if (StringUtils.isNotBlank(genderStr)) {
            if ("男".equals(genderStr)) {
                teacherUser.setGender(1);
            } else if ("女".equals(genderStr)) {
                teacherUser.setGender(2);
            }
        }
        
        if (StringUtils.isNotBlank(teacherTypeStr)) {
            switch (teacherTypeStr) {
                case "学院教师":
                    teacherUser.setTeacherType("COLLEGE");
                    break;
                case "系室教师":
                    teacherUser.setTeacherType("DEPARTMENT");
                    break;
                case "辅导员":
                    teacherUser.setTeacherType("COUNSELOR");
                    break;
                default:
                    teacherUser.setTeacherType(teacherTypeStr);
            }
        }
        
        if (StringUtils.isNotBlank(departmentName)) {
            List<Department> departments = departmentService.findByName(departmentName);
            if (departments == null || departments.isEmpty()) {
                throw new BusinessException("院系名称不存在: " + departmentName);
            }
            teacherUser.setDepartmentId(String.valueOf(departments.get(0).getId()));
        }
        
        return teacherUser;
    }
    
    // 验证教师注册信息
    private void validateTeacherRegistration(TeacherUser teacherUser) {
        if (StringUtils.isBlank(teacherUser.getName())) {
            throw new BusinessException("姓名不能为空");
        }
        
        if (StringUtils.isBlank(teacherUser.getTeacherUserId())) {
            throw new BusinessException("工号不能为空");
        }
        
        if (StringUtils.isBlank(teacherUser.getDepartmentId())) {
            throw new BusinessException("院系不能为空");
        }
    }
    
    // 获取字符串值，如果为null则返回空字符串
    private String getStringValue(Map<String, Object> rowData, String key) {
        Object value = rowData.get(key);
        if (value == null) {
            return "";
        }
        return value.toString().trim();
    }
    
    @Override
    public List<TeacherUser> findByTeacherType(String teacherType) {
        log.debug("根据教师类型查询教师列表: teacherType={}", teacherType);
        if (StringUtils.isBlank(teacherType)) {
            throw new BusinessException("教师类型不能为空");
        }
        return teacherUserMapper.findByTeacherType(teacherType);
    }
}