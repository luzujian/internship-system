package com.gdmu.service;

import com.gdmu.entity.Major;

import java.util.List;

public interface MajorService {
    // 根据ID查询专业
    Major findById(Long id);
    
    // 查询所有专业
    List<Major> findAll();
    
    // 根据名称搜索专业
    List<Major> findByName(String name);
    
    // 根据院系ID查询专业
    List<Major> findByDepartmentId(Long departmentId);
    
    // 新增专业
    int save(Major major);
    
    // 更新专业
    int update(Major major);
    
    // 删除专业
    int delete(Long id);
    
    // 获取所有带有人数信息的专业
    List<Major> findAllWithUserCount();
    
    // 获取指定专业的教师人数
    Integer getTeacherCountByMajorId(Long majorId);
    
    // 获取指定专业的学生人数
    Integer getStudentCountByMajorId(Long majorId);
    
    // 获取指定专业的已确定实习学生数
    Integer getConfirmedCountByMajorId(Long majorId);
    
    // 获取指定专业的未找到实习学生数
    Integer getNotFoundCountByMajorId(Long majorId);
    
    // 获取指定专业的有Offer未确定学生数
    Integer getHasOfferCountByMajorId(Long majorId);
}