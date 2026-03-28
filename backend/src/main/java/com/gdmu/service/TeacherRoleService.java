package com.gdmu.service;

import com.gdmu.entity.TeacherRole;
import java.util.List;

public interface TeacherRoleService {
    List<TeacherRole> findAll();
    TeacherRole findById(Long id);
    TeacherRole findByRoleCode(String roleCode);
    void insert(TeacherRole teacherRole);
    void update(TeacherRole teacherRole);
    void deleteById(Long id);
}
