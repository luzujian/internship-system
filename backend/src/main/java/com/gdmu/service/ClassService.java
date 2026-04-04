package com.gdmu.service;

import com.gdmu.entity.Class;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface ClassService {
    // 根据ID查询班级
    Class findById(Long id);
    
    // 查询所有班级
    List<Class> findAll();
    
    /**
     * 查询所有班级并包含学生数量
     * @return 班级列表
     */
    List<Class> findAllWithStudentCount();
    
    // 根据专业ID查询班级
    List<Class> findByMajorId(Long majorId);
    
    // 新增班级
    int save(Class classEntity);
    
    // 更新班级
    int update(Class classEntity);
    
    // 删除班级
    int delete(Long id);
    
    // 从Excel导入班级数据
    Map<String, Object> importExcel(MultipartFile file);
    
    // 从JSON导入班级数据
    Map<String, Object> importFromJson(String jsonData);
    
    // 获取所有班级数据（用于导出）
    List<Class> getAllClassData();
    
    // 批量删除班级
    int batchDelete(List<Long> ids);
}