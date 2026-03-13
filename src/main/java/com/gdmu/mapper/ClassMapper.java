package com.gdmu.mapper;

import com.gdmu.entity.Class;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ClassMapper {
    // 根据ID查询班级
    Class findById(Long id);
    
    // 查询所有班级
    List<Class> findAll();
    
    // 根据专业ID查询班级
    List<Class> findByMajorId(Long majorId);
    
    // 新增班级
    int insert(Class classEntity);
    
    // 更新班级
    int update(Class classEntity);
    
    // 删除班级
    int delete(Long id);
}