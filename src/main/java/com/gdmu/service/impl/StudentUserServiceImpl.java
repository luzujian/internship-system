package com.gdmu.service.impl;

import com.gdmu.entity.*;
import com.gdmu.exception.BusinessException;
import com.gdmu.mapper.GroupMapper;
import com.gdmu.mapper.StudentUserMapper;
import com.gdmu.entity.PageResult;
import com.gdmu.entity.Class;
import com.gdmu.service.ClassService;
import com.gdmu.service.DepartmentService;
import com.gdmu.service.MajorService;
import com.gdmu.service.StudentUserService;
import com.gdmu.vo.StudentUserVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;


/**
 * 学生用户服务实现类
 */
@Slf4j
@Service
public class StudentUserServiceImpl implements StudentUserService {
    private final StudentUserMapper studentUserMapper;
    private final GroupMapper groupMapper;
    private final PasswordEncoder passwordEncoder;
    private final MajorService majorService;
    private final ClassService classService;
    private final DepartmentService departmentService;

    @Autowired
    public StudentUserServiceImpl(StudentUserMapper studentUserMapper,
                                  GroupMapper groupMapper,
                                  PasswordEncoder passwordEncoder,
                                  MajorService majorService,
                                  ClassService classService,
                                  DepartmentService departmentService) {
        this.studentUserMapper = studentUserMapper;
        this.groupMapper = groupMapper;
        this.passwordEncoder = passwordEncoder;
        this.majorService = majorService;
        this.classService = classService;
        this.departmentService = departmentService;
    }

    @Override
    public StudentUser findByStudentId(String studentId) {
        log.debug("查找学生用户，学号: {}", studentId);
        if (StringUtils.isBlank(studentId)) {
            throw new BusinessException("学号不能为空");
        }
        StudentUser studentUser = studentUserMapper.findByStudentId(studentId);
        if (studentUser != null) {
            log.debug("找到学生用户，学号: {}, 姓名: {}, 角色: {}, 密码: {}",
                    studentUser.getStudentId(), studentUser.getName(), studentUser.getRole(),
                    studentUser.getPassword() != null ? "[已设置]" : "[空]");
        } else {
            log.debug("未找到学号为 {} 的学生用户", studentId);
        }
        return studentUser;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int register(StudentUser studentUser) {
        log.debug("注册学生用户: {}", studentUser.getStudentId());
        // 参数校验
        validateStudentRegistration(studentUser);

        // 检查学号是否已存在
        if (studentUserMapper.findByStudentId(studentUser.getStudentId()) != null) {
            throw new BusinessException("学号已存在");
        }

        // 设置username字段，与studentUserId相同
        if (studentUser.getUsername() == null || studentUser.getUsername().isEmpty()) {
            studentUser.setUsername(studentUser.getStudentId());
        }

        // 使用 BCrypt 加密密码
        studentUser.setPassword(passwordEncoder.encode(studentUser.getPassword()));
        studentUser.setCreateTime(new Date());
        studentUser.setUpdateTime(new Date());

        int result = studentUserMapper.insert(studentUser);
        log.info("学生用户注册成功，学号: {}", studentUser.getStudentId());
        return result;
    }

    @Override
    public StudentUser findById(Long id) {
        log.debug("查找学生用户，ID: {}", id);
        if (id == null || id <= 0) {
            throw new BusinessException("用户ID无效");
        }
        return studentUserMapper.findById(id);
    }

    @Override
    public List<StudentUser> findByIds(List<Long> ids) {
        log.debug("批量查找学生用户，用户ID列表: {}", ids);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return studentUserMapper.selectByIds(ids);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int update(StudentUser studentUser) {
        log.debug("更新学生用户信息，用户ID: {}", studentUser.getId());

        // 参数校验
        if (studentUser.getId() == null || studentUser.getId() <= 0) {
            throw new BusinessException("用户ID无效");
        }

        // 检查用户是否存在
        StudentUser existingUser = studentUserMapper.findById(studentUser.getId());
        if (existingUser == null) {
            throw new BusinessException("用户不存在");
        }

        // 如果提供了新密码，则进行加密
        if (StringUtils.isNotBlank(studentUser.getPassword())) {
            studentUser.setPassword(passwordEncoder.encode(studentUser.getPassword()));
        } else {
            // 否则保持原密码
            studentUser.setPassword(existingUser.getPassword());
        }

        studentUser.setUpdateTime(new Date());

        int result = studentUserMapper.update(studentUser);
        log.info("学生用户信息更新成功，用户ID: {}", studentUser.getId());
        return result;
    }

    @Override
    public Long count() {
        log.debug("查询学生用户总数");
        return studentUserMapper.count();
    }

    @Override
    public PageResult<StudentUser> findPage(Integer page, Integer pageSize) {
        log.debug("分页查询学生用户，页码: {}, 每页条数: {}", page, pageSize);
        return findPage(page, pageSize, null);
    }

    @Override
    public PageResult<StudentUser> findPage(Integer page, Integer pageSize, String keyword) {
        log.debug("分页查询学生用户，页码: {}, 每页条数: {}, 搜索关键字: {}", page, pageSize, keyword);
        // 直接使用PageHelper和list方法进行分页查询
        PageHelper.startPage(page, pageSize);
        List<StudentUser> studentUsers;
        if (StringUtils.isNotBlank(keyword)) {
            // 使用list方法进行关键字搜索，将关键字同时用于学号和姓名的搜索
            studentUsers = studentUserMapper.list(keyword, keyword, null, null, null);
        } else {
            // 查询所有
            studentUsers = studentUserMapper.findAll();
        }
        // 构建分页结果
        PageInfo<StudentUser> pageInfo = new PageInfo<>(studentUsers);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<StudentUser> findPage(Integer page, Integer pageSize, String studentId, String name, Integer grade, String majorId, String classId, Integer status, Integer gender) {
        log.debug("分页查询学生用户(含状态和性别), 页码: {}, 每页条数: {}, 学号: {}, 姓名: {}, 年级: {}, 专业ID: {}, 班级ID: {}, 状态: {}, 性别: {}",
                page, pageSize, studentId, name, grade, majorId, classId, status, gender);
        PageHelper.startPage(page, pageSize);
        Integer majorIdInt = null;
        if (StringUtils.isNotBlank(majorId)) {
            try {
                majorIdInt = Integer.parseInt(majorId);
            } catch (NumberFormatException e) {
                log.warn("专业ID格式不正确: {}", majorId);
            }
        }
        Long classIdValue = null;
        if (StringUtils.isNotBlank(classId)) {
            try {
                classIdValue = Long.parseLong(classId);
            } catch (NumberFormatException e) {
                log.warn("班级ID格式不正确: {}", classId);
            }
        }
        List<StudentUser> studentUsers = studentUserMapper.listWithStatus(studentId, name, grade, majorIdInt, classIdValue != null ? classIdValue.toString() : null, status, gender);

        log.debug("查询到的学生用户数量(含状态和性别过滤): {}", studentUsers != null ? studentUsers.size() : 0);

        PageInfo<StudentUser> pageInfo = new PageInfo<>(studentUsers);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<StudentUser> findPage(Integer page, Integer pageSize, String studentId, String name, Integer grade, String majorId, String classId) {
        log.debug("分页查询学生用户，页码: {}, 每页条数: {}, 学号: {}, 姓名: {}, 年级: {}, 专业ID: {}, 班级ID: {}",
                page, pageSize, studentId, name, grade, majorId, classId);
        // 直接使用PageHelper和list方法进行多条件分页查询
        PageHelper.startPage(page, pageSize);
        // 调用StudentUserMapper的list方法进行多条件搜索
        // 将majorId从String转换为Integer
        Integer majorIdInt = null;
        if (StringUtils.isNotBlank(majorId)) {
            try {
                majorIdInt = Integer.parseInt(majorId);
            } catch (NumberFormatException e) {
                log.warn("专业ID格式不正确: {}", majorId);
            }
        }
        // 将classId从String转换为Long
        Long classIdValue = null;
        if (StringUtils.isNotBlank(classId)) {
            try {
                classIdValue = Long.parseLong(classId);
            } catch (NumberFormatException e) {
                log.warn("班级ID格式不正确: {}", classId);
            }
        }
        List<StudentUser> studentUsers = studentUserMapper.list(studentId, name, grade, majorIdInt, classIdValue != null ? classIdValue.toString() : null);

        // 添加日志记录查询结果
        log.debug("查询到的学生用户数量: {}", studentUsers != null ? studentUsers.size() : 0);

        // 构建分页结果
        PageInfo<StudentUser> pageInfo = new PageInfo<>(studentUsers);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Override
    public PageResult<StudentUser> findPage(Integer page, Integer pageSize, String studentId, String name, Integer grade, String majorId, String classId, Integer status) {
        log.debug("分页查询学生用户(含状态), 页码: {}, 每页条数: {}, 学号: {}, 姓名: {}, 年级: {}, 专业ID: {}, 班级ID: {}, 状态: {}",
                page, pageSize, studentId, name, grade, majorId, classId, status);
        // 直接使用PageHelper和listWithStatus方法进行多条件分页查询
        PageHelper.startPage(page, pageSize);
        // 将majorId从String转换为Integer
        Integer majorIdInt = null;
        if (StringUtils.isNotBlank(majorId)) {
            try {
                majorIdInt = Integer.parseInt(majorId);
            } catch (NumberFormatException e) {
                log.warn("专业ID格式不正确: {}", majorId);
            }
        }
        // 将classId从String转换为Long
        Long classIdValue = null;
        if (StringUtils.isNotBlank(classId)) {
            try {
                classIdValue = Long.parseLong(classId);
            } catch (NumberFormatException e) {
                log.warn("班级ID格式不正确: {}", classId);
            }
        }
        List<StudentUser> studentUsers = studentUserMapper.listWithStatus(studentId, name, grade, majorIdInt, classIdValue != null ? classIdValue.toString() : null, status, null);

        // 添加日志记录查询结果
        log.debug("查询到的学生用户数量(含状态过滤): {}", studentUsers != null ? studentUsers.size() : 0);

        // 构建分页结果
        PageInfo<StudentUser> pageInfo = new PageInfo<>(studentUsers);
        return PageResult.build(pageInfo.getTotal(), pageInfo.getList(),
                pageInfo.getPages(), pageInfo.getPageNum(), pageInfo.getPageSize());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int delete(Long id) {
        try {
            log.debug("删除学生用户，ID: {}", id);

            // 参数校验
            if (id == null || id <= 0) {
                throw new BusinessException("用户ID无效");
            }

            // 检查用户是否存在
            StudentUser studentUser = studentUserMapper.findById(id);
            if (studentUser == null) {
                throw new BusinessException("用户不存在");
            }

            // 全面处理外键约束，确保学生能够被成功删除

            // 5. 最后删除学生用户记录
            log.debug("执行删除学生用户操作");
            int result = studentUserMapper.deleteById(id);

            if (result > 0) {
                log.info("学生用户删除成功，用户ID: {}", id);
            } else {
                log.warn("删除学生用户失败，未找到记录或已被删除，用户ID: {}", id);
            }

            return result;
        } catch (BusinessException e) {
            // 直接抛出业务异常
            throw e;
        } catch (Exception e) {
            // 捕获其他所有异常，特别是SQLIntegrityConstraintViolationException
            log.error("删除学生用户时发生异常，用户ID: {}, 错误信息: {}", id, e.getMessage());

            // 详细分析异常原因
            String errorMsg = "删除学生用户失败";
            Throwable cause = e;
            while (cause != null) {
                String message = cause.getMessage();
                if (message != null) {
                    if (message.contains("foreign key constraint") ||
                            message.contains("Cannot delete or update a parent row")) {
                        errorMsg = "该学生用户与其他数据存在关联关系，请检查并处理后再删除";
                        break;
                    } else if (message.contains("timeout")) {
                        errorMsg = "数据库操作超时，请稍后重试";
                        break;
                    }
                }
                cause = cause.getCause();
            }

            throw new BusinessException(errorMsg);
        }
    }

    /**
     * 处理小组组长相关的外键约束
     * 当删除学生用户时，如果该学生是小组组长，需要更新小组的组长信息或直接删除小组
     *
     */
    // 查询该学生是否是某个小组的组长（使用leader_student_id字段）
    // 验证学生用户注册信息
    private void validateStudentRegistration(StudentUser studentUser) {
        if (studentUser == null) {
            throw new BusinessException("用户信息不能为空");
        }

        if (StringUtils.isBlank(studentUser.getPassword())) {
            throw new BusinessException("密码不能为空");
        }

        if (StringUtils.isBlank(studentUser.getName())) {
            throw new BusinessException("姓名不能为空");
        }

        if (StringUtils.isBlank(studentUser.getStudentId())) {
            throw new BusinessException("学号不能为空");
        }

        if (studentUser.getMajorId() == null) {
            throw new BusinessException("专业不能为空");
        }

        if (studentUser.getGrade() == null) {
            throw new BusinessException("年级不能为空");
        }

        if (studentUser.getClassId() == null) {
            throw new BusinessException("班级ID不能为空");
        }

        // 班级名称由后端根据班级ID自动填充，不需要前端提供
        // if (StringUtils.isBlank(studentUser.getClassName())) {
        //     throw new BusinessException("班级名称不能为空");
        // }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public Map<String, Object> importFromExcel(List<Map<String, Object>> studentDataList) {
        log.info("开始从Excel导入学生数据，总数据量: {}", studentDataList != null ? studentDataList.size() : 0);

        // 结果统计
        int successCount = 0;
        int failCount = 0;
        List<Map<String, Object>> failList = new ArrayList<>();

        if (studentDataList == null || studentDataList.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failList", failList);
            return result;
        }

        // 获取所有已存在的学生学号，用于批量检查重复
        List<String> allStudentIds = new ArrayList<>();
        for (Map<String, Object> rowData : studentDataList) {
            String studentId = getStringValue(rowData, "学号");
            if (StringUtils.isNotBlank(studentId)) {
                allStudentIds.add(studentId);
            }
        }

        // 批量查询已存在的学号
        Map<String, StudentUser> existingStudentMap = new HashMap<>();
        if (!allStudentIds.isEmpty()) {
            List<StudentUser> existingStudents = studentUserMapper.findByStudentIds(allStudentIds);
            for (StudentUser student : existingStudents) {
                existingStudentMap.put(student.getStudentId(), student);
            }
        }

        // 遍历Excel数据，转换为StudentUser对象并验证
        List<StudentUser> validStudentList = new ArrayList<>();

        for (int i = 0; i < studentDataList.size(); i++) {
            Map<String, Object> rowData = studentDataList.get(i);
            Map<String, Object> failInfo = new HashMap<>();
            failInfo.put("rowNum", i + 2); // Excel行号从1开始，数据从第2行开始
            failInfo.putAll(rowData);

            try {
                // 转换为StudentUser对象
                StudentUser studentUser = convertToStudentUser(rowData);

                // 检查学号是否已存在
                if (existingStudentMap.containsKey(studentUser.getStudentId())) {
                    failInfo.put("errorMsg", "学号已存在");
                    failList.add(failInfo);
                    failCount++;
                    continue;
                }

                // 验证数据有效性
                validateStudentRegistration(studentUser);

                // 添加到有效列表
                validStudentList.add(studentUser);
                existingStudentMap.put(studentUser.getStudentId(), studentUser); // 防止同一文件内重复

            } catch (Exception e) {
                log.error("导入学生数据失败，行号: {}", i + 2, e);
                failInfo.put("errorMsg", convertToChineseErrorMessage(e.getMessage()));
                failList.add(failInfo);
                failCount++;
            }
        }

        // 批量插入有效数据
        if (!validStudentList.isEmpty()) {
            // 分批插入，每批最多100条
            final int BATCH_SIZE = 100;
            int totalSize = validStudentList.size();
            int batchCount = (totalSize + BATCH_SIZE - 1) / BATCH_SIZE;

            for (int i = 0; i < batchCount; i++) {
                int start = i * BATCH_SIZE;
                int end = Math.min(start + BATCH_SIZE, totalSize);
                List<StudentUser> batchList = validStudentList.subList(start, end);

                int batchResult = studentUserMapper.batchInsert(batchList);
                successCount += batchResult;

                log.info("导入批次 {} 成功，导入数量: {}", i + 1, batchResult);
            }
        }

        log.info("Excel学生数据导入完成，成功: {}, 失败: {}", successCount, failCount);

        // 返回导入结果
        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("failList", failList);

        return result;
    }

    // 将Excel行数据转换为StudentUser对象
    private StudentUser convertToStudentUser(Map<String, Object> rowData) {
        log.debug("处理Excel行数据: {}", rowData);
        StudentUser studentUser = new StudentUser();

        // 设置学生信息
        String studentId = getStringValue(rowData, "学号");
        String name = getStringValue(rowData, "姓名");

        log.debug("解析学生信息 - 学号: {}, 姓名: {}", studentId, name);

        studentUser.setUsername(studentId);
        studentUser.setStudentId(studentId);
        studentUser.setName(name);
        studentUser.setPassword(passwordEncoder.encode("123456")); // 默认密码

        // 设置性别
        String genderStr = getStringValue(rowData, "性别");
        if (StringUtils.isNotBlank(genderStr)) {
            if ("男".equals(genderStr)) {
                studentUser.setGender(1);
            } else if ("女".equals(genderStr)) {
                studentUser.setGender(2);
            }
        }

        // 设置专业ID - 兼容导出的Excel中的"专业"和"专业编号"列名
        String majorIdStr = getStringValue(rowData, "专业ID");
        if (StringUtils.isBlank(majorIdStr)) {
            // 如果"专业ID"为空，尝试使用"专业编号"列的值
            majorIdStr = getStringValue(rowData, "专业编号");
        }
        if (StringUtils.isBlank(majorIdStr)) {
            // 如果"专业编号"也为空，尝试使用"专业"列的值
            majorIdStr = getStringValue(rowData, "专业");
        }
        if (StringUtils.isNotBlank(majorIdStr)) {
            try {
                studentUser.setMajorId(Long.parseLong(majorIdStr));
                log.debug("直接解析专业ID成功: {}", studentUser.getMajorId());
            } catch (NumberFormatException e) {
                // 如果转换失败，尝试通过专业名称查找专业ID
                log.debug("尝试通过专业名称查找专业ID: {}", majorIdStr);
                List<Major> majors = majorService.findByName(majorIdStr);
                if (majors != null && !majors.isEmpty()) {
                    // 取第一个匹配的专业ID
                    studentUser.setMajorId(majors.get(0).getId());
                    log.debug("找到专业: {}, ID: {}", majorIdStr, studentUser.getMajorId());
                } else {
                    // 查询所有专业并打印日志，方便调试
                    List<Major> allMajors = majorService.findAll();
                    log.debug("未找到专业: {}, 系统中可用的专业有: {}", majorIdStr, allMajors);
                    throw new BusinessException("专业ID格式不正确，且未找到对应的专业名称: " + majorIdStr);
                }
            }
        }
        log.debug("最终设置的专业ID: {}", studentUser.getMajorId());

        // 设置年级
        String gradeStr = getStringValue(rowData, "年级");
        if (StringUtils.isNotBlank(gradeStr)) {
            try {
                studentUser.setGrade(Integer.parseInt(gradeStr));
            } catch (NumberFormatException e) {
                throw new BusinessException("年级格式不正确");
            }
        }

        // 设置班级ID - 兼容导出的Excel中的"班级"和"班级编号"列名
        String classIdStr = getStringValue(rowData, "班级ID");
        if (StringUtils.isBlank(classIdStr)) {
            // 如果"班级ID"为空，尝试使用"班级编号"列的值
            classIdStr = getStringValue(rowData, "班级编号");
        }
        if (StringUtils.isBlank(classIdStr)) {
            // 如果"班级编号"也为空，尝试使用"班级"列的值
            classIdStr = getStringValue(rowData, "班级");
        }
        if (StringUtils.isNotBlank(classIdStr)) {
            try {
                studentUser.setClassId(Long.parseLong(classIdStr));
                log.debug("直接解析班级ID成功: {}", studentUser.getClassId());
            } catch (NumberFormatException e) {
                // 如果转换失败，尝试通过班级名称查找班级ID
                log.debug("尝试通过班级名称查找班级ID: {}", classIdStr);
                // 创建final变量供lambda表达式使用
                final String finalClassIdStr = classIdStr;
                List<Class> classes = classService.findAll();
                if (classes != null && !classes.isEmpty()) {
                    // 查找名称匹配的班级
                    Class matchedClass = classes.stream()
                            .filter(classEntity -> classEntity.getName().equals(finalClassIdStr))
                            .findFirst()
                            .orElse(null);
                    if (matchedClass != null) {
                        studentUser.setClassId(matchedClass.getId());
                        log.debug("找到班级: {}, ID: {}", classIdStr, studentUser.getClassId());
                    } else {
                        // 查询所有班级并打印日志，方便调试
                        log.debug("未找到班级: {}, 系统中可用的班级有: {}", classIdStr, classes);
                        throw new BusinessException("班级ID格式不正确，且未找到对应的班级名称: " + classIdStr);
                    }
                } else {
                    throw new BusinessException("班级ID格式不正确，且班级列表为空");
                }
            }
        }
        log.debug("最终设置的班级ID: {}", studentUser.getClassId());

        // 注意：班级名称不会被插入到数据库，已在StudentUserMapper.xml的batchInsert中移除
        // 设置班级名称（仅用于日志和临时处理）
        String className = getStringValue(rowData, "班级名称");
        if (StringUtils.isBlank(className)) {
            // 如果"班级名称"为空，尝试使用"班级"列的值
            className = getStringValue(rowData, "班级");
        }
        log.debug("班级名称(仅用于日志): {}", className);

        // 设置角色为学生
        studentUser.setRole("ROLE_STUDENT");
        log.debug("设置学生角色: {}", studentUser.getRole());

        // 设置创建和更新时间
        Date now = new Date();
        studentUser.setCreateTime(now);
        studentUser.setUpdateTime(now);

        return studentUser;
    }

    // 从Map中获取字符串值，处理空值情况
    private String getStringValue(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) {
            return null;
        }

        Object value = map.get(key);
        if (value == null) {
            return null;
        }

        return value.toString().trim();
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int batchDeleteStudentUsers(List<Long> ids) {
        try {
            log.debug("批量删除学生用户，ID列表: {}", ids);

            if (ids == null || ids.isEmpty()) {
                throw new BusinessException("学生用户ID列表不能为空");
            }

            int result = studentUserMapper.batchDeleteByIds(ids);
            log.info("批量删除学生用户成功，删除数量: {}", result);
            return result;

        } catch (Exception e) {
            log.error("批量删除学生用户时发生异常，错误信息: {}", e.getMessage());
            throw new BusinessException("批量删除学生用户失败: " + e.getMessage());
        }
    }

    @Override
    public List<StudentUser> findAll(String studentId, String name, Integer grade, String majorId, String classId) {
        log.debug("查询所有满足条件的学生用户，学号: {}, 姓名: {}, 年级: {}, 专业ID: {}, 班级ID: {}",
                studentId, name, grade, majorId, classId);

        // 将majorId从String转换为Integer
        Integer majorIdInt = null;
        if (StringUtils.isNotBlank(majorId)) {
            try {
                majorIdInt = Integer.parseInt(majorId);
            } catch (NumberFormatException e) {
                log.warn("专业ID格式不正确: {}", majorId);
            }
        }

        // 将classId从String转换为Long
        Long classIdValue = null;
        if (StringUtils.isNotBlank(classId)) {
            try {
                classIdValue = Long.parseLong(classId);
            } catch (NumberFormatException e) {
                log.warn("班级ID格式不正确: {}", classId);
            }
        }
        // 调用StudentUserMapper的list方法进行多条件搜索
        List<StudentUser> studentUsers = studentUserMapper.list(studentId, name, grade, majorIdInt, classIdValue != null ? classIdValue.toString() : null);

        log.debug("查询到的学生用户总数: {}", studentUsers != null ? studentUsers.size() : 0);

        return studentUsers != null ? studentUsers : Collections.emptyList();
    }

    @Override
    public List<StudentUser> findAll(String studentId, String name, Integer grade, String majorId, String classId, Integer status) {
        log.debug("查询所有满足条件的学生用户(含状态), 学号: {}, 姓名: {}, 年级: {}, 专业ID: {}, 班级ID: {}, 状态: {}",
                studentId, name, grade, majorId, classId, status);

        // 将majorId从String转换为Integer
        Integer majorIdInt = null;
        if (StringUtils.isNotBlank(majorId)) {
            try {
                majorIdInt = Integer.parseInt(majorId);
            } catch (NumberFormatException e) {
                log.warn("专业ID格式不正确: {}", majorId);
            }
        }

        // 将classId从String转换为Long
        Long classIdValue = null;
        if (StringUtils.isNotBlank(classId)) {
            try {
                classIdValue = Long.parseLong(classId);
            } catch (NumberFormatException e) {
                log.warn("班级ID格式不正确: {}", classId);
            }
        }
        // 调用StudentUserMapper的list方法进行多条件搜索（含状态参数）
        List<StudentUser> studentUsers = studentUserMapper.list(studentId, name, grade, majorIdInt, classIdValue, status);

        log.debug("查询到的学生用户总数(含状态过滤): {}", studentUsers != null ? studentUsers.size() : 0);

        return studentUsers != null ? studentUsers : Collections.emptyList();
    }

    @Override
    public Integer getStudentCountByClassId(Long classId) {
        log.debug("根据班级ID查询学生数量，班级ID: {}", classId);
        if (classId == null) {
            throw new BusinessException("班级ID不能为空");
        }
        Integer count = studentUserMapper.countByClassId(classId);
        return count != null ? count : 0;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public int save(StudentUser studentUser) {
        log.debug("保存学生用户信息");
        // 参数校验
        if (studentUser == null) {
            throw new BusinessException("学生用户信息不能为空");
        }

        // 如果是新用户（ID为空），则执行插入操作
        if (studentUser.getId() == null) {
            // 检查学号是否已存在
            if (studentUserMapper.findByStudentId(studentUser.getStudentId()) != null) {
                throw new BusinessException("学号已存在");
            }

            // 密码处理（如果有提供密码）
            if (StringUtils.isNotBlank(studentUser.getPassword())) {
                studentUser.setPassword(passwordEncoder.encode(studentUser.getPassword()));
            }

            studentUser.setCreateTime(new Date());
            studentUser.setUpdateTime(new Date());

            int result = studentUserMapper.insert(studentUser);
            log.info("学生用户保存成功，学号: {}", studentUser.getStudentId());
            return result;
        } else {
            // 如果是已存在用户（ID不为空），则执行更新操作
            return update(studentUser);
        }
    }

    @Override
    public List<Map<String, Object>> findStudentsByCourseIds(List<Long> courseIds, String searchName, Long classId) {
        log.debug("根据课程ID列表查询学生信息: courseIds={}, searchName={}, classId={}", courseIds, searchName, classId);

        // 参数校验
        if (courseIds == null || courseIds.isEmpty()) {
            throw new BusinessException("课程ID列表不能为空");
        }

        // 由于course表不存在，直接返回空列表
        return new ArrayList<>();
    }

    @Override
    public List<StudentUserVO> queryStudentsByParams(Map<String, Object> params) {
        log.debug("开始查询学生，参数: {}", params);

        String studentId = (String) params.get("studentId");
        String name = (String) params.get("name");
        String major = (String) params.get("major");
        String department = (String) params.get("department");
        String className = (String) params.get("className");
        Boolean active = (Boolean) params.get("active");
        Integer grade = null;

        // 处理年级参数
        Object gradeObj = params.get("grade");
        if (gradeObj != null) {
            if (gradeObj instanceof Integer) {
                grade = (Integer) gradeObj;
            } else if (gradeObj instanceof String) {
                try {
                    grade = Integer.parseInt((String) gradeObj);
                } catch (NumberFormatException e) {
                    log.warn("年级参数格式错误: {}", gradeObj);
                }
            }
        }

        // 创建最终的局部变量，用于lambda表达式
        final Integer finalGrade = grade;

        // 声明最终的学生列表
        List<StudentUser> filteredStudents;

        // 这里我们先查询所有学生，然后在Java层面进行更复杂的过滤
        List<StudentUser> students = studentUserMapper.findAll();

        // 使用stream进行过滤，处理各种查询条件
        filteredStudents = students.stream()
                .filter(student -> {
                    boolean match = true;

                    // 学号筛选
                    if (studentId != null && !studentId.isEmpty()) {
                        match = match && student.getStudentId() != null &&
                                student.getStudentId().contains(studentId);
                    }

                    // 姓名筛选
                    if (name != null && !name.isEmpty()) {
                        match = match && student.getName() != null &&
                                student.getName().contains(name);
                    }

                    // 专业筛选
                    if (major != null && !major.isEmpty()) {
                        Major majorInfo = majorService.findById(student.getMajorId());
                        if (majorInfo == null || !majorInfo.getName().contains(major)) {
                            return false;
                        }
                    }

                    // 学院筛选
                    if (department != null && !department.isEmpty()) {
                        Major majorInfo = majorService.findById(student.getMajorId());
                        if (majorInfo == null) {
                            return false;
                        }
                        Department deptInfo = departmentService.findById(majorInfo.getDepartmentId());
                        if (deptInfo == null || !deptInfo.getName().contains(department)) {
                            return false;
                        }
                    }

                    // 班级筛选
                    if (className != null && !className.isEmpty()) {
                        Class classInfo = classService.findById(student.getClassId());
                        if (classInfo == null) {
                            return false;
                        }

                        // 支持精确匹配和模糊匹配
                        boolean matched = false;
                        if (classInfo.getName().contains(className)) {
                            matched = true;
                        } else {
                            // 尝试部分匹配
                            String[] parts = className.split("班|");
                            for (String part : parts) {
                                if (part != null && !part.isEmpty() && classInfo.getName().contains(part)) {
                                    matched = true;
                                    break;
                                }
                            }
                        }

                        if (!matched) {
                            return false;
                        }
                    }

                    // 年级筛选
                    if (finalGrade != null) {
                        // 直接使用学生实体的grade字段进行匹配
                        if (student.getGrade() == null || !student.getGrade().equals(finalGrade)) {
                            return false;
                        }
                    }

                    // 激活状态筛选 - 改进处理逻辑
                    if (active != null) {
                        // 检查学生状态字段是否存在
                        if (student.getStatus() == null) {
                            // 如果状态为空，对于active=true的查询，不匹配；对于active=false的查询，也不匹配
                            return false;
                        }

                        // 根据active参数进行筛选
                        Integer status = active ? 1 : 0;
                        match = match && status.equals(student.getStatus());
                    }

                    return match;
                })
                .collect(java.util.stream.Collectors.toList());

        log.debug("查询到的学生用户数量: {}", filteredStudents.size());
        return convertToStudentUserVO(filteredStudents);
    }

    /**
     * 将StudentUser转换为StudentUserVO
     */
    private List<StudentUserVO> convertToStudentUserVO(List<StudentUser> students) {
        List<StudentUserVO> result = new ArrayList<>();

        for (StudentUser student : students) {
            StudentUserVO vo = new StudentUserVO();
            vo.setId(student.getId());
            vo.setStudentId(student.getStudentId());
            vo.setName(student.getName());
            vo.setRole(student.getRole());
            vo.setCreateTime(student.getCreateTime());
            vo.setLastLoginTime(student.getLastLoginTime());
            vo.setStatus(student.getStatus());
            vo.setGrade(student.getGrade());

            // 设置专业和学院信息
            if (student.getMajorId() != null) {
                try {
                    Major major = majorService.findById(student.getMajorId());
                    if (major != null) {
                        vo.setMajorName(major.getName());
                        // 需要通过departmentService获取学院名称
                        if (major.getDepartmentId() != null) {
                            try {
                                Department department = departmentService.findById(major.getDepartmentId());
                                if (department != null) {
                                    vo.setDepartmentName(department.getName());
                                }
                            } catch (Exception e) {
                                log.warn("获取学院信息失败，专业ID: {}, 学院ID: {}", major.getId(), major.getDepartmentId());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("获取专业信息失败，学生ID: {}, 专业ID: {}", student.getId(), student.getMajorId());
                }
            }

            // 设置班级信息
            if (student.getClassId() != null) {
                try {
                    Class classEntity = classService.findById(student.getClassId());
                    if (classEntity != null) {
                        vo.setClassName(classEntity.getName());
                    }
                } catch (Exception e) {
                    log.warn("获取班级信息失败，学生ID: {}, 班级ID: {}", student.getId(), student.getClassId());
                }
            }
            // 将创建的vo对象添加到结果列表中
            result.add(vo);
        }
        // 将return语句移到for循环外部，确保无论students列表是否为空，方法都会返回值
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByClassId(Long classId) {
        log.info("根据班级ID删除所有学生，班级ID: {}", classId);
        
        if (classId == null || classId <= 0) {
            throw new BusinessException("班级ID无效");
        }
        
        List<StudentUser> students = studentUserMapper.findByClassId(classId);
        if (students == null || students.isEmpty()) {
            log.info("班级下没有学生，无需删除");
            return 0;
        }
        
        List<Long> studentIds = students.stream().map(StudentUser::getId).collect(java.util.stream.Collectors.toList());
        int deletedCount = batchDeleteStudentUsers(studentIds);
        
        log.info("成功删除班级 {} 下的 {} 名学生", classId, deletedCount);
        return deletedCount;
    }

    /**
     * 将英文错误消息转换为中文
     */
    private String convertToChineseErrorMessage(String message) {
        if (message == null) {
            return "未知错误";
        }

        // 常见英文错误消息映射
        if (message.contains("Cannot serialize")) {
            return "数据格式错误，无法序列化";
        }
        if (message.contains("NumberFormatException")) {
            return "数字格式不正确";
        }
        if (message.contains("null")) {
            return "数据不能为空";
        }
        if (message.contains("required")) {
            return "必填项不能为空";
        }
        if (message.contains("format")) {
            return "数据格式不正确";
        }
        if (message.contains("duplicate") || message.contains("Duplicate")) {
            return "重复的数据";
        }
        if (message.contains("foreign key")) {
            return "外键约束错误";
        }
        if (message.contains("constraint")) {
            return "数据约束错误";
        }

        // 如果是 BusinessException 的中文消息，直接返回
        return message;
    }
}