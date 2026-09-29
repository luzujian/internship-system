package com.internship.service;

import com.internship.entity.CompanySchoolLiaison;
import com.internship.entity.CompanyUser;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanySchoolLiaisonMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.TeacherUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CompanySchoolLiaisonService {
    private final CompanySchoolLiaisonMapper liaisonMapper;
    private final CompanyUserMapper companyUserMapper;
    private final TeacherUserMapper teacherUserMapper;

    public CompanySchoolLiaisonService(CompanySchoolLiaisonMapper liaisonMapper,
                                       CompanyUserMapper companyUserMapper,
                                       TeacherUserMapper teacherUserMapper) {
        this.liaisonMapper = liaisonMapper;
        this.companyUserMapper = companyUserMapper;
        this.teacherUserMapper = teacherUserMapper;
    }

    public List<CompanySchoolLiaison> findByCompanyId(Long companyId) {
        if (companyId == null) {
            return Collections.emptyList();
        }
        return liaisonMapper.findByCompanyId(companyId);
    }

    @Transactional(rollbackFor = Exception.class)
    public CompanySchoolLiaison add(Long companyId, Long teacherId, Long currentTeacherId) {
        validateCompany(companyId);
        if (teacherId == null) {
            throw new BusinessException("对接人不能为空");
        }
        validateTeacherIds(List.of(teacherId));
        if (liaisonMapper.exists(companyId, teacherId) > 0) {
            throw new BusinessException("该教师已是企业校内对接人");
        }

        CompanySchoolLiaison liaison = new CompanySchoolLiaison();
        liaison.setCompanyId(companyId);
        liaison.setTeacherId(teacherId);
        liaison.setCreatorTeacherId(currentTeacherId);
        liaison.setCreateTime(LocalDateTime.now());
        liaisonMapper.insert(liaison);
        return liaisonMapper.findById(liaison.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void delete(Long liaisonId, Long currentTeacherId) {
        CompanySchoolLiaison liaison = liaisonMapper.findById(liaisonId);
        if (liaison == null) {
            throw new BusinessException("校内对接关系不存在");
        }
        if (liaison.getCreatorTeacherId() == null
                || !liaison.getCreatorTeacherId().equals(currentTeacherId)) {
            throw new BusinessException("只能删除本人添加的校内对接人");
        }
        liaisonMapper.deleteById(liaisonId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void replace(Long companyId, List<Long> teacherIds, Long currentTeacherId) {
        TeacherUser current = teacherUserMapper.findById(currentTeacherId);
        if (current == null || !"DEPARTMENT".equals(current.getTeacherType())) {
            throw new BusinessException("只有系主任可以维护校内对接人");
        }
        validateCompany(companyId);
        List<Long> ids = validateTeacherIds(teacherIds);
        liaisonMapper.deleteByCompanyId(companyId);
        if (!ids.isEmpty()) {
            List<CompanySchoolLiaison> liaisons = ids.stream().map(id -> {
                CompanySchoolLiaison liaison = new CompanySchoolLiaison();
                liaison.setCompanyId(companyId);
                liaison.setTeacherId(id);
                liaison.setCreatorTeacherId(currentTeacherId);
                liaison.setCreateTime(LocalDateTime.now());
                return liaison;
            }).collect(Collectors.toList());
            liaisonMapper.batchInsert(liaisons);
        }
    }

    private void validateCompany(Long companyId) {
        CompanyUser company = companyUserMapper.findById(companyId);
        if (company == null) {
            throw new BusinessException("企业不存在");
        }
    }

    private List<Long> validateTeacherIds(List<Long> teacherIds) {
        if (teacherIds == null) {
            throw new BusinessException("对接人列表不能为空");
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long teacherId : teacherIds) {
            if (teacherId == null) {
                throw new BusinessException("对接人不能为空");
            }
            distinctIds.add(teacherId);
        }
        if (distinctIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> ids = new ArrayList<>(distinctIds);
        List<TeacherUser> teachers = teacherUserMapper.selectByIds(ids);
        Map<Long, TeacherUser> teachersById = teachers.stream()
                .collect(Collectors.toMap(TeacherUser::getId, Function.identity()));
        boolean invalid = ids.stream().anyMatch(id -> {
            TeacherUser teacher = teachersById.get(id);
            return teacher == null || !Integer.valueOf(1).equals(teacher.getStatus());
        });
        if (invalid) {
            throw new BusinessException("对接教师不存在或已停用");
        }
        return ids;
    }
}
