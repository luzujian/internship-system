package com.internship.service.impl;

import com.internship.exception.BusinessException;
import com.internship.mapper.CounselorCategoryWeightMapper;
import com.internship.mapper.CounselorScoringRuleMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.entity.CounselorCategoryWeight;
import com.internship.entity.CounselorScoringRule;
import com.internship.entity.PageResult;
import com.internship.entity.Department;
import com.internship.entity.Division;
import com.internship.entity.TeacherUser;
import com.internship.service.DepartmentService;
import com.internship.service.DivisionService;
import com.internship.service.TeacherUserService;
import com.internship.utils.UserCredentialDefaults;
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

    @Autowired
    private DivisionService divisionService;

    @Autowired
    private CounselorScoringRuleMapper counselorScoringRuleMapper;

    @Autowired
    private CounselorCategoryWeightMapper counselorCategoryWeightMapper;

    @Autowired
    private com.internship.mapper.CounselorAISettingsMapper counselorAISettingsMapper;

    @Autowired
    private com.internship.service.automation.AutomationTemplates automationTemplates;

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
        teacherUser.setPassword(UserCredentialDefaults.PASSWORD);
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
        
        // 使用 BCrypt 加密密码
        teacherUser.setPassword(passwordEncoder.encode(teacherUser.getPassword()));
        teacherUser.setCreateTime(new Date());
        teacherUser.setUpdateTime(new Date());
        
        // 如果没有设置性别，默认设置为1（男）
        if (teacherUser.getGender() == null) {
            teacherUser.setGender(1);
        }
        
        String role = roleForTeacherType(teacherUser.getTeacherType());
        teacherUser.setRole(role);
        
        int result = teacherUserMapper.insert(teacherUser);
        automationTemplates.initialize(teacherUser);
        log.info("教师INSERT完成，ID: {}, 教师编号: {}", teacherUser.getId(), teacherUser.getTeacherUserId());

        // 仅辅导员初始化默认评分规则 + AI 开启
        if ("COUNSELOR".equals(teacherUser.getTeacherType())) {
            initDefaultScoringRules(teacherUser.getId());
            if (counselorAISettingsMapper.findByCounselorId(teacherUser.getId()) == null) {
                com.internship.entity.CounselorAISettings aiSettings = new com.internship.entity.CounselorAISettings();
                aiSettings.setCounselorId(teacherUser.getId());
                aiSettings.setEnableAiScoring(1);
                aiSettings.setCreateTime(new Date());
                aiSettings.setUpdateTime(new Date());
                counselorAISettingsMapper.insert(aiSettings);
            }
        }

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

        String effectiveType = StringUtils.isNotBlank(teacherUser.getTeacherType())
                ? teacherUser.getTeacherType() : existingUser.getTeacherType();
        boolean scopeChanged = StringUtils.isNotBlank(teacherUser.getTeacherType())
                || StringUtils.isNotBlank(teacherUser.getDepartmentId())
                || StringUtils.isNotBlank(teacherUser.getDivisionId());
        if (scopeChanged && "DEPARTMENT".equals(effectiveType)) {
            String effectiveDepartment = StringUtils.isNotBlank(teacherUser.getDepartmentId())
                    ? teacherUser.getDepartmentId() : existingUser.getDepartmentId();
            String effectiveDivision = StringUtils.isNotBlank(teacherUser.getDivisionId())
                    ? teacherUser.getDivisionId() : existingUser.getDivisionId();
            validateDepartmentDivision(effectiveDepartment, effectiveDivision);
        }

        // 工号唯一性校验：仅在工号被修改时检查（且不能与其他用户工号重复）
        if (StringUtils.isNotBlank(teacherUser.getTeacherUserId())
                && !teacherUser.getTeacherUserId().equals(existingUser.getTeacherUserId())) {
            TeacherUser duplicate = teacherUserMapper.findByTeacherUserId(teacherUser.getTeacherUserId());
            if (duplicate != null) {
                throw new BusinessException("工号已存在，请修改");
            }
        }
        
        // 如果提供了新密码，则进行加密
        if (StringUtils.isNotBlank(teacherUser.getPassword())) {
            teacherUser.setPassword(passwordEncoder.encode(teacherUser.getPassword()));
        } else {
            // 否则保持原密码
            teacherUser.setPassword(existingUser.getPassword());
        }
        
        // 如果更新了教师类型，需要同步更新角色
        if (StringUtils.isNotBlank(teacherUser.getTeacherType())) {
            teacherUser.setRole(roleForTeacherType(teacherUser.getTeacherType()));
        } else if (StringUtils.isNotBlank(existingUser.getTeacherType())) {
            teacherUser.setRole(roleForTeacherType(existingUser.getTeacherType()));
        } else {
            // Legacy rows without a type cannot authenticate, but must remain disableable.
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
        log.debug("条件分页查询教师用户：page={}, pageSize={}, filterCount={}",
                page, pageSize, params != null ? params.size() : 0);
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
    public int resetAllPasswords() {
        String passwordHash = passwordEncoder.encode(UserCredentialDefaults.PASSWORD);
        return teacherUserMapper.resetAllPasswords(passwordHash);
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
    
    @Transactional(rollbackFor = Exception.class)
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
        Map<String, String> pendingInitialPasswords = new HashMap<>();
        List<Map<String, String>> initialCredentials = new ArrayList<>();
        
        for (int i = 0; i < teacherDataList.size(); i++) {
            Map<String, Object> rowData = teacherDataList.get(i);
            Map<String, Object> failInfo = new HashMap<>();
            failInfo.put("rowNum", i + 2); // Excel行号从1开始，数据从第2行开始
            failInfo.putAll(rowData);
            
            try {
                // 跳过工号为空的空行（如模板自带的填写说明行）
                if (StringUtils.isBlank(getStringValue(rowData, "工号"))) {
                    continue;
                }
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
                teacherUser.setRole(roleForTeacherType(teacherUser.getTeacherType()));
                String initialPassword = UserCredentialDefaults.PASSWORD;
                teacherUser.setPassword(passwordEncoder.encode(initialPassword));

                // 添加到有效列表
                validTeacherList.add(teacherUser);
                pendingInitialPasswords.put(teacherUser.getTeacherUserId(), initialPassword);
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
                if (batchResult != batchList.size()) {
                    throw new BusinessException("教师导入未全部写入，请重试");
                }
                successCount += batchResult;
                for (TeacherUser insertedTeacher : batchList) {
                    initialCredentials.add(Map.of("username", insertedTeacher.getUsername(),
                            "password", pendingInitialPasswords.get(insertedTeacher.getTeacherUserId())));
                }

                // 为导入的辅导员初始化默认评分规则 + AI 开启
                  for (TeacherUser tu : batchList) {
                      TeacherUser saved = tu.getId() == null ? teacherUserMapper.findByTeacherUserId(tu.getTeacherUserId()) : tu;
                      if (saved == null || saved.getId() == null) throw new BusinessException("导入教师信息未能保存，请重试。");
                      automationTemplates.initialize(saved);
                      tu.setId(saved.getId());
                      if ("COUNSELOR".equals(tu.getTeacherType())) {
                        initDefaultScoringRules(tu.getId());
                        if (counselorAISettingsMapper.findByCounselorId(tu.getId()) == null) {
                            com.internship.entity.CounselorAISettings aiSettings = new com.internship.entity.CounselorAISettings();
                            aiSettings.setCounselorId(tu.getId());
                            aiSettings.setEnableAiScoring(1);
                            aiSettings.setCreateTime(new Date());
                            aiSettings.setUpdateTime(new Date());
                            counselorAISettingsMapper.insert(aiSettings);
                        }
                    }
                }

                log.info("导入批次 {} 成功，导入数量: {}", i + 1, batchResult);
            }
        }
        
        log.info("Excel教师数据导入完成，成功: {}, 失败: {}", successCount, failCount);
        
        // 返回导入结果
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failList", failList);
        result.put("initialCredentials", initialCredentials);
        
        return result;
    }
    
    // 将Excel行数据转换为TeacherUser对象
    // 表头兼容：新模板（工号/姓名/院系/教师类型）与旧模板（院系 ID/身份/手机号/邮箱）均可导入
    private TeacherUser convertToTeacherUser(Map<String, Object> rowData) {
        log.debug("处理Excel行数据: {}", rowData);
        TeacherUser teacherUser = new TeacherUser();

        String teacherUserId = getStringValue(rowData, "工号");
        String name = getStringValue(rowData, "姓名");
        String teacherTypeStr = firstNonBlank(
                getStringValue(rowData, "教师类型"),
                getStringValue(rowData, "身份"));
        String departmentValue = firstNonBlank(
                getStringValue(rowData, "学院"),
                getStringValue(rowData, "院系"),
                getStringValue(rowData, "院系 ID"));
        String divisionValue = firstNonBlank(
                getStringValue(rowData, "系"),
                getStringValue(rowData, "系室"),
                getStringValue(rowData, "系室 ID"));
        String phone = getStringValue(rowData, "手机号");
        String email = getStringValue(rowData, "邮箱");

        teacherUser.setUsername(teacherUserId);
        teacherUser.setTeacherUserId(teacherUserId);
        teacherUser.setName(name);
        teacherUser.setCreateTime(new Date());
        teacherUser.setUpdateTime(new Date());
        teacherUser.setPhone(phone);
        teacherUser.setEmail(email);

        if (StringUtils.isBlank(teacherTypeStr)) {
            throw new BusinessException("教师类型不能为空");
        }
        String normalizedType = teacherTypeStr.trim();
        switch (normalizedType) {
            case "学院教师":
            case "COLLEGE":
                teacherUser.setTeacherType("COLLEGE");
                break;
            case "系室教师":
            case "DEPARTMENT":
                teacherUser.setTeacherType("DEPARTMENT");
                break;
            case "辅导员":
            case "COUNSELOR":
                teacherUser.setTeacherType("COUNSELOR");
                break;
            default:
                throw new BusinessException("教师类型填写不正确，应为学院教师/系室教师/辅导员");
        }

        if (StringUtils.isBlank(departmentValue)) {
            throw new BusinessException("院系不能为空");
        }
        String deptText = departmentValue.trim();
        if (deptText.matches("^\\d+$")) {
            // 兼容旧模板填写的数字院系 ID
            teacherUser.setDepartmentId(deptText);
        } else {
            List<Department> departments = departmentService.findByName(deptText);
            if (departments == null || departments.isEmpty()) {
                throw new BusinessException("院系名称不存在: " + deptText);
            }
            teacherUser.setDepartmentId(String.valueOf(departments.get(0).getId()));
        }

        if ("DEPARTMENT".equals(teacherUser.getTeacherType())) {
            if (StringUtils.isBlank(divisionValue)) {
                throw new BusinessException("系室教师必须填写所属系");
            }
            String divisionText = divisionValue.trim();
            if (divisionText.matches("^\\d+$")) {
                teacherUser.setDivisionId(divisionText);
            } else {
                List<Division> divisions = divisionService.findByDepartmentId(Long.parseLong(teacherUser.getDepartmentId()));
                Division division = divisions == null ? null : divisions.stream()
                        .filter(item -> divisionText.equals(item.getName())).findFirst().orElse(null);
                if (division == null) {
                    throw new BusinessException("所属学院内不存在该系: " + divisionText);
                }
                teacherUser.setDivisionId(String.valueOf(division.getId()));
            }
        }

        return teacherUser;
    }

    // 取多个候选值中第一个非空字符串，用于兼容新旧导入模板的不同表头
    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
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
        roleForTeacherType(teacherUser.getTeacherType());
        if ("DEPARTMENT".equals(teacherUser.getTeacherType())) {
            validateDepartmentDivision(teacherUser.getDepartmentId(), teacherUser.getDivisionId());
        }
    }

    private void validateDepartmentDivision(String departmentId, String divisionIdValue) {
        if (StringUtils.isBlank(departmentId) || StringUtils.isBlank(divisionIdValue)) {
            throw new BusinessException("系室教师必须填写所属学院和系");
        }
        Long divisionId;
        try {
            divisionId = Long.parseLong(divisionIdValue);
        } catch (NumberFormatException e) {
            throw new BusinessException("所属系 ID 无效");
        }
        if (divisionId <= 0) {
            throw new BusinessException("所属系 ID 无效");
        }
        Division division = divisionService.findById(divisionId);
        if (division == null || division.getDepartmentId() == null
                || !departmentId.equals(String.valueOf(division.getDepartmentId()))) {
            throw new BusinessException("所属系与学院不匹配");
        }
    }

    private String roleForTeacherType(String teacherType) {
        if (teacherType == null) {
            throw new BusinessException("教师类型必须为学院教师、系室教师或辅导员");
        }
        return switch (teacherType) {
            case "COLLEGE" -> "ROLE_TEACHER_COLLEGE";
            case "DEPARTMENT" -> "ROLE_TEACHER_DEPARTMENT";
            case "COUNSELOR" -> "ROLE_TEACHER_COUNSELOR";
            default -> throw new BusinessException("教师类型必须为学院教师、系室教师或辅导员");
        };
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

    /**
     * 为新教师初始化默认评分规则（3个维度 × 5个等级 = 15条规则）
     * 维度：实习态度(40%)、专业能力(35%)、学习成长(25%)
     */
    private void initDefaultScoringRules(Long counselorId) {
        log.info("开始初始化默认评分规则，教师ID: {}", counselorId);
        if (counselorId == null) {
            log.warn("教师ID为空，跳过评分规则初始化");
            return;
        }
        // 维度定义：code, name, weight
        record Dim(String code, String name, int weight) {}
        List<Dim> dimensions = List.of(
            new Dim("实习态度", "实习态度", 40),
            new Dim("专业能力", "专业能力", 35),
            new Dim("学习成长", "学习成长", 25)
        );

        // 等级定义：name, min, max, desc
        record Level(String name, int min, int max, String desc) {}
        List<Level> levels = List.of(
            new Level("优秀", 90, 100, "表现卓越，远超预期"),
            new Level("良好", 80, 89, "表现良好，达到要求"),
            new Level("中等", 70, 79, "表现一般，基本完成"),
            new Level("及格", 60, 69, "勉强达标，存在不足"),
            new Level("不及格", 0, 59, "未达标，需要改进")
        );

        // 构建评分规则
        List<CounselorScoringRule> rules = new ArrayList<>();
        for (Dim dim : dimensions) {
            for (int i = 0; i < levels.size(); i++) {
                Level lv = levels.get(i);
                CounselorScoringRule rule = new CounselorScoringRule();
                rule.setCounselorId(counselorId);
                rule.setRuleName(lv.name());
                rule.setRuleCode(dim.code() + "_" + lv.name());
                rule.setCategory(dim.code());
                rule.setWeight(20);
                rule.setMinScore(lv.min());
                rule.setMaxScore(lv.max());
                rule.setDescription(dim.name() + lv.name() + "：" + lv.desc());
                rule.setSortOrder(i);
                rule.setStatus(1);
                rule.setDeleted(0);
                rule.setCreateTime(new Date());
                rule.setUpdateTime(new Date());
                rules.add(rule);
            }
        }

        // 构建维度权重
        List<CounselorCategoryWeight> weights = new ArrayList<>();
        for (Dim dim : dimensions) {
            CounselorCategoryWeight w = new CounselorCategoryWeight();
            w.setCounselorId(counselorId);
            w.setCategoryCode(dim.code());
            w.setCategoryName(dim.name());
            w.setWeight(dim.weight());
            w.setStatus(1);
            w.setDeleted(0);
            w.setCreateTime(new Date());
            w.setUpdateTime(new Date());
            weights.add(w);
        }

        try {
            counselorScoringRuleMapper.batchInsert(rules);
            counselorCategoryWeightMapper.batchInsert(weights);

            // AI 评分开关由 CounselorAISettingsService.ensureDefaultRulesExist 统一处理

            log.info("已为教师 {} 初始化默认评分规则，共 {} 条规则，{} 个维度", counselorId, rules.size(), dimensions.size());
        } catch (Exception e) {
            log.warn("初始化默认评分规则失败，教师ID: {}，原因: {}", counselorId, e.getMessage());
        }
    }
}
