package com.gdmu.service.impl;

import com.gdmu.entity.TeacherRole;
import com.gdmu.mapper.TeacherRoleMapper;
import com.gdmu.service.TeacherRoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
public class TeacherRoleServiceImpl implements TeacherRoleService {
    
    @Autowired
    private TeacherRoleMapper teacherRoleMapper;
    
    @Override
    public List<TeacherRole> findAll() {
        return teacherRoleMapper.findAll();
    }
    
    @Override
    public TeacherRole findById(Long id) {
        return teacherRoleMapper.findById(id);
    }
    
    @Override
    public TeacherRole findByRoleCode(String roleCode) {
        return teacherRoleMapper.findByRoleCode(roleCode);
    }
    
    @Override
    public void insert(TeacherRole teacherRole) {
        teacherRoleMapper.insert(teacherRole);
        log.info("插入教师角色成功: {}", teacherRole.getRoleName());
    }
    
    @Override
    public void update(TeacherRole teacherRole) {
        teacherRoleMapper.update(teacherRole);
        log.info("更新教师角色成功: {}", teacherRole.getRoleName());
    }
    
    @Override
    public void deleteById(Long id) {
        teacherRoleMapper.deleteById(id);
        log.info("删除教师角色成功: id={}", id);
    }
}
