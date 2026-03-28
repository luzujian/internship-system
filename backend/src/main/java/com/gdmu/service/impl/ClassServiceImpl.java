package com.gdmu.service.impl;

import com.gdmu.mapper.ClassCounselorRelationMapper;
import com.gdmu.mapper.ClassMapper;
import com.gdmu.mapper.TeacherUserMapper;
import com.gdmu.entity.Class;
import com.gdmu.entity.ClassCounselorRelation;
import com.gdmu.entity.Major;
import com.gdmu.entity.TeacherUser;
import com.gdmu.service.ClassService;
import com.gdmu.service.MajorService;
import com.gdmu.service.StudentUserService;
import com.gdmu.utils.ExcelUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
public class ClassServiceImpl implements ClassService {
    
    @Autowired
    private ClassMapper classMapper;
    
    @Autowired
    @Lazy
    private StudentUserService studentUserService;
    
    @Autowired
    private MajorService majorService;

    @Autowired
    private ClassCounselorRelationMapper classCounselorRelationMapper;

    @Autowired
    private TeacherUserMapper teacherUserMapper;
    
    @Override
    public Class findById(Long id) {
        return classMapper.findById(id);
    }
    
    @Override
    @Cacheable(value = "classes", key = "'all'", unless = "#result == null")
    public List<Class> findAll() {
        return classMapper.findAll();
    }
    
    @Override
    public List<Class> findAllWithStudentCount() {
        return classMapper.findAll();
    }
    
    @Override
    public List<Class> findByMajorId(Long majorId) {
        return classMapper.findByMajorId(majorId);
    }
    
    @Override
    @CacheEvict(value = "classes", key = "'all'")
    @Transactional(rollbackFor = Exception.class)
    public int save(Class classEntity) {
        classEntity.setCreateTime(new Date());
        classEntity.setUpdateTime(new Date());
        int result = classMapper.insert(classEntity);

        // 创建辅导员关联关系
        String teacherUserId = classEntity.getTeacherId();
        if (teacherUserId != null && !teacherUserId.isEmpty()) {
            TeacherUser teacher = teacherUserMapper.findByTeacherUserId(teacherUserId);
            if (teacher != null) {
                ClassCounselorRelation relation = new ClassCounselorRelation();
                relation.setClassId(classEntity.getId());
                relation.setClassName(classEntity.getName());
                relation.setCounselorId(teacher.getId());
                relation.setCounselorName(teacher.getName());
                relation.setCreateTime(new Date());
                relation.setUpdateTime(new Date());
                classCounselorRelationMapper.insert(relation);
                log.info("新建班级 {} 并创建辅导员关联: counselorId={}, counselorName={}",
                    classEntity.getId(), teacher.getId(), teacher.getName());
            } else {
                log.warn("未找到教师 userId={}，无法创建辅导员关联关系", teacherUserId);
            }
        }

        return result;
    }
    
    @Override
    @CacheEvict(value = "classes", key = "'all'")
    @Transactional(rollbackFor = Exception.class)
    public int update(Class classEntity) {
        // 获取旧的班级信息，用于比较教师是否变更
        Class oldClass = classMapper.findById(classEntity.getId());
        String oldTeacherId = oldClass != null ? oldClass.getTeacherId() : null;

        classEntity.setUpdateTime(new Date());
        int result = classMapper.update(classEntity);

        String newTeacherId = classEntity.getTeacherId();
        boolean teacherChanged = (newTeacherId == null && oldTeacherId != null)
                || (newTeacherId != null && !newTeacherId.equals(oldTeacherId));

        // 删除该班级的所有旧辅导员关联（无论是否变更，都重新建立关联）
        List<ClassCounselorRelation> oldRelations = classCounselorRelationMapper.findByClassId(classEntity.getId());
        for (ClassCounselorRelation oldRelation : oldRelations) {
            classCounselorRelationMapper.deleteById(oldRelation.getId());
            log.info("删除旧的辅导员关联: 班级ID={}, 原辅导员ID={}", classEntity.getId(), oldRelation.getCounselorId());
        }

        // 如果新的教师ID不为空，创建新的辅导员关联
        if (newTeacherId != null && !newTeacherId.isEmpty()) {
            TeacherUser teacher = teacherUserMapper.findByTeacherUserId(newTeacherId);
            if (teacher != null) {
                ClassCounselorRelation newRelation = new ClassCounselorRelation();
                newRelation.setClassId(classEntity.getId());
                newRelation.setClassName(classEntity.getName());
                newRelation.setCounselorId(teacher.getId());
                newRelation.setCounselorName(teacher.getName());
                newRelation.setCreateTime(new Date());
                newRelation.setUpdateTime(new Date());
                classCounselorRelationMapper.insert(newRelation);
                log.info("{} 辅导员关联: 班级ID={}, 辅导员ID={}, 姓名={}",
                    teacherChanged ? "变更并创建" : "重新创建",
                    classEntity.getId(), teacher.getId(), teacher.getName());
            } else {
                log.warn("未找到教师 userId={}，无法创建辅导员关联关系", newTeacherId);
            }
        }

        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    @CacheEvict(value = "classes", key = "'all'")
    public int delete(Long id) {
        log.info("删除班级，ID: {}", id);
        
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("班级ID无效");
        }
        
        Class classEntity = classMapper.findById(id);
        if (classEntity == null) {
            throw new IllegalArgumentException("班级不存在");
        }
        
        log.info("开始删除班级 {} 下的所有学生", id);
        int deletedStudentCount = studentUserService.deleteByClassId(id);
        log.info("成功删除班级 {} 下的 {} 名学生", id, deletedStudentCount);
        
        int result = classMapper.delete(id);
        log.info("班级删除成功，班级ID: {}", id);
        return result;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int batchDelete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("班级ID列表不能为空");
        }
        
        int result = 0;
        for (Long id : ids) {
            result += delete(id);
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> importExcel(MultipartFile file) {
        log.info("开始导入班级Excel数据");
        try {
            List<Map<String, Object>> dataList = ExcelUtils.readExcel(file);
            
            int successCount = 0;
            int failCount = 0;
            List<Map<String, Object>> failList = new ArrayList<>();
            
            if (dataList == null || dataList.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("successCount", successCount);
                result.put("failCount", failCount);
                result.put("failList", failList);
                return result;
            }
            
            for (int i = 0; i < dataList.size(); i++) {
                Map<String, Object> rowData = dataList.get(i);
                Map<String, Object> failInfo = new HashMap<>();
                failInfo.put("rowNum", i + 2);
                failInfo.putAll(rowData);
                
                try {
                    Class classEntity = convertToClass(rowData);
                    validateClassData(classEntity);
                    
                    boolean exists = false;
                    List<Class> allClasses = findAll();
                    for (Class existing : allClasses) {
                        if (existing.getName().equals(classEntity.getName())) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (exists) {
                        failInfo.put("errorMsg", "班级已存在: " + classEntity.getName());
                        failList.add(failInfo);
                        failCount++;
                        continue;
                    }
                    
                    save(classEntity);
                    successCount++;
                } catch (Exception e) {
                    log.error("导入班级数据失败，行号: {}", i + 2, e);
                    failInfo.put("errorMsg", convertToChineseErrorMessage(e.getMessage()));
                    failList.add(failInfo);
                    failCount++;
                }
            }
            
            log.info("班级Excel数据导入完成，成功: {}, 失败: {}", successCount, failCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failList", failList);
            
            return result;
        } catch (IOException e) {
            log.error("读取Excel文件失败: {}", e.getMessage());
            throw new RuntimeException("读取Excel文件失败: " + e.getMessage());
        } catch (Exception e) {
            log.error("导入班级数据失败: {}", e.getMessage());
            throw new RuntimeException("导入班级数据失败: " + e.getMessage());
        }
    }
    
    @Override
    public Map<String, Object> importFromJson(String jsonData) {
        log.info("开始从JSON导入班级数据");
        log.info("接收到的JSON数据: {}", jsonData);
        try {
            com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> dataList = objectMapper.readValue(jsonData, 
                objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            
            log.info("解析后的数据列表大小: {}", dataList != null ? dataList.size() : 0);
            if (dataList != null && !dataList.isEmpty()) {
                log.info("第一条数据示例: {}", dataList.get(0));
                log.info("第一条数据的所有字段: {}", dataList.get(0).keySet());
            }
            
            int successCount = 0;
            int failCount = 0;
            List<Map<String, Object>> failList = new ArrayList<>();
            
            if (dataList == null || dataList.isEmpty()) {
                Map<String, Object> result = new HashMap<>();
                result.put("successCount", successCount);
                result.put("failCount", failCount);
                result.put("failList", failList);
                return result;
            }
            
            for (int i = 0; i < dataList.size(); i++) {
                Map<String, Object> rowData = dataList.get(i);
                Map<String, Object> failInfo = new HashMap<>();
                failInfo.put("rowNum", i + 1);
                failInfo.putAll(rowData);
                
                try {
                    Class classEntity = convertFromJsonData(rowData);
                    validateClassData(classEntity);
                    
                    boolean exists = false;
                    List<Class> allClasses = findAll();
                    for (Class existing : allClasses) {
                        if (existing.getName().equals(classEntity.getName())) {
                            exists = true;
                            break;
                        }
                    }
                    
                    if (exists) {
                        failInfo.put("errorMsg", "班级已存在: " + classEntity.getName());
                        failList.add(failInfo);
                        failCount++;
                        continue;
                    }
                    
                    save(classEntity);
                    successCount++;
                } catch (Exception e) {
                    log.error("导入班级数据失败，行号: {}", i + 1, e);
                    failInfo.put("errorMsg", convertToChineseErrorMessage(e.getMessage()));
                    failList.add(failInfo);
                    failCount++;
                }
            }
            
            log.info("班级JSON数据导入完成，成功: {}, 失败: {}", successCount, failCount);
            
            Map<String, Object> result = new HashMap<>();
            result.put("successCount", successCount);
            result.put("failCount", failCount);
            result.put("failList", failList);
            
            return result;
        } catch (Exception e) {
            log.error("导入班级JSON数据失败: {}", e.getMessage(), e);
            throw new RuntimeException("导入班级JSON数据失败: " + e.getMessage());
        }
    }
    
    @Override
    public List<Class> getAllClassData() {
        return findAllWithStudentCount();
    }
    
    private Class convertToClass(Map<String, Object> rowData) {
        log.debug("处理Excel行数据: {}", rowData);
        Class classEntity = new Class();
        
        String name = getStringValue(rowData, "班级名称");
        String majorName = getStringValue(rowData, "专业名称");
        String majorIdStr = getStringValue(rowData, "专业ID");
        String gradeStr = getStringValue(rowData, "年级");
        String teacherIdStr = getStringValue(rowData, "负责教师");
        
        if (teacherIdStr == null || teacherIdStr.isEmpty()) {
            teacherIdStr = getStringValue(rowData, "负责老师");
        }
        if (teacherIdStr == null || teacherIdStr.isEmpty()) {
            teacherIdStr = getStringValue(rowData, "teacherId");
        }
        
        log.debug("解析到的字段值 - 班级名称: {}, 专业名称: {}, 专业ID: {}, 年级: {}, 负责教师ID: {}", name, majorName, majorIdStr, gradeStr, teacherIdStr);
        
        classEntity.setName(name);
        
        if (gradeStr != null && !gradeStr.isEmpty()) {
            try {
                classEntity.setGrade(Integer.parseInt(gradeStr));
            } catch (NumberFormatException e) {
                throw new RuntimeException("年级格式不正确: " + gradeStr);
            }
        }
        
        if (majorIdStr != null && !majorIdStr.isEmpty()) {
            try {
                Long majorId = Long.parseLong(majorIdStr);
                classEntity.setMajorId(majorId);
                log.debug("直接从Excel中获取专业ID: {}", majorId);
            } catch (NumberFormatException e) {
                log.debug("专业ID格式不正确，尝试通过专业名称查找: {}", majorIdStr);
            }
        }
        
        if (classEntity.getMajorId() == null) {
            if (majorName == null || majorName.isEmpty()) {
                majorName = getStringValue(rowData, "专业");
            }
            
            if (majorName != null && !majorName.isEmpty()) {
                log.debug("尝试通过专业名称查找: {}", majorName);
                List<Major> majors = majorService.findByName(majorName);
                if (majors == null || majors.isEmpty()) {
                    List<Major> allMajors = majorService.findAll();
                    log.debug("未找到专业: {}, 系统中可用的专业有: {}", majorName, allMajors);
                    throw new RuntimeException("专业名称不存在: " + majorName);
                }
                Long majorId = majors.get(0).getId();
                classEntity.setMajorId(majorId);
                log.debug("通过专业名称获取专业ID: {}, 名称: {}", majorId, majorName);
            } else {
                log.error("专业ID和专业名称都为空，rowData中的所有key: {}", rowData.keySet());
                throw new RuntimeException("专业ID不能为空，请在Excel中提供专业ID或专业名称");
            }
        }
        
        if (teacherIdStr != null && !teacherIdStr.isEmpty()) {
            classEntity.setTeacherId(teacherIdStr);
            log.debug("从Excel中获取负责教师ID: {}", teacherIdStr);
        } else {
            log.debug("负责教师ID为空，不设置负责教师");
        }
        
        log.debug("最终班级实体 - ID: {}, 名称: {}, 专业ID: {}, 年级: {}, 负责教师ID: {}", 
            classEntity.getId(), classEntity.getName(), classEntity.getMajorId(), 
            classEntity.getGrade(), classEntity.getTeacherId());
        
        return classEntity;
    }
    
    private Class convertFromJsonData(Map<String, Object> rowData) {
        log.debug("处理JSON行数据: {}", rowData);
        Class classEntity = new Class();
        
        Object nameObj = rowData.get("name");
        Object gradeObj = rowData.get("grade");
        Object majorIdObj = rowData.get("majorId");
        Object teacherIdObj = rowData.get("teacherId");
        Object studentCountObj = rowData.get("studentCount");
        
        String name = nameObj != null ? nameObj.toString() : null;
        String gradeStr = gradeObj != null ? gradeObj.toString() : null;
        String majorIdStr = majorIdObj != null ? majorIdObj.toString() : null;
        String teacherIdStr = teacherIdObj != null ? teacherIdObj.toString() : null;
        
        log.debug("解析到的字段值 - 班级名称: {}, 专业ID: {}, 年级: {}, 负责教师ID: {}, 班级人数: {}", name, majorIdStr, gradeStr, teacherIdStr, studentCountObj);
        log.debug("studentCountObj类型: {}, 值: {}", studentCountObj != null ? studentCountObj.getClass().getName() : "null", studentCountObj);
        
        if (name == null || name.isEmpty()) {
            throw new RuntimeException("班级名称不能为空");
        }
        classEntity.setName(name);
        
        if (gradeStr != null && !gradeStr.isEmpty()) {
            try {
                classEntity.setGrade(Integer.parseInt(gradeStr));
            } catch (NumberFormatException e) {
                throw new RuntimeException("年级格式不正确: " + gradeStr);
            }
        }
        
        if (majorIdStr != null && !majorIdStr.isEmpty()) {
            try {
                Long majorId = Long.parseLong(majorIdStr);
                classEntity.setMajorId(majorId);
                log.debug("从JSON中获取专业ID: {}", majorId);
            } catch (NumberFormatException e) {
                throw new RuntimeException("专业ID格式不正确: " + majorIdStr);
            }
        } else {
            throw new RuntimeException("专业ID不能为空");
        }
        
        if (teacherIdStr != null && !teacherIdStr.isEmpty()) {
            classEntity.setTeacherId(teacherIdStr);
            log.debug("从JSON中获取负责教师ID: {}", teacherIdStr);
        }
        
        if (studentCountObj != null) {
            try {
                Integer studentCount = Integer.parseInt(studentCountObj.toString());
                if (studentCount >= 0) {
                    classEntity.setStudentCount(studentCount);
                    log.debug("从JSON中获取班级人数: {}, 设置成功", studentCount);
                } else {
                    log.debug("班级人数不能为负数，设置为0");
                    classEntity.setStudentCount(0);
                }
            } catch (NumberFormatException e) {
                log.debug("班级人数格式不正确，设置为0，错误: {}", e.getMessage());
                classEntity.setStudentCount(0);
            }
        } else {
            log.debug("studentCountObj为null，不设置班级人数");
        }
        
        log.debug("最终班级实体 - ID: {}, 名称: {}, 专业ID: {}, 年级: {}, 负责教师ID: {}, 班级人数: {}", 
            classEntity.getId(), classEntity.getName(), classEntity.getMajorId(), 
            classEntity.getGrade(), classEntity.getTeacherId(), classEntity.getStudentCount());
        
        return classEntity;
    }
    
    private void validateClassData(Class classEntity) {
        if (classEntity.getName() == null || classEntity.getName().isEmpty()) {
            throw new RuntimeException("班级名称不能为空");
        }
        if (classEntity.getMajorId() == null) {
            throw new RuntimeException("专业ID不能为空");
        }
        if (classEntity.getGrade() == null) {
            throw new RuntimeException("年级不能为空");
        }
    }
    
    private String getStringValue(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    // 将英文错误消息转换为中文
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