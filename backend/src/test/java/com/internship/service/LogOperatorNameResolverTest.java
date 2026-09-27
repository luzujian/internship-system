package com.internship.service;

import com.internship.entity.AdminUser;
import com.internship.entity.CompanyUser;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.mapper.AdminUserMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogOperatorNameResolverTest {
    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private TeacherUserMapper teacherUserMapper;

    @Mock
    private StudentUserMapper studentUserMapper;

    @Mock
    private CompanyUserMapper companyUserMapper;

    @InjectMocks
    private LogOperatorNameResolver resolver;

    @Test
    void resolvesAdminNameByNumericUserId() {
        AdminUser admin = new AdminUser();
        admin.setName("系统管理员");
        when(adminUserMapper.findById(2L)).thenReturn(admin);

        assertEquals("系统管理员", resolver.resolve("ROLE_ADMIN", "2", "admin"));
        verifyNoInteractions(studentUserMapper, teacherUserMapper, companyUserMapper);
    }

    @Test
    void resolvesStudentNameForUnknownRoleByIdentifier() {
        StudentUser student = new StudentUser();
        student.setName("张三");
        when(studentUserMapper.findByStudentId("23209010019")).thenReturn(student);

        assertEquals("张三", resolver.resolve("UNKNOWN", null, "23209010019"));
        verifyNoInteractions(adminUserMapper, teacherUserMapper, companyUserMapper);
    }

    @Test
    void resolvesTeacherNameForTypedRole() {
        TeacherUser teacher = new TeacherUser();
        teacher.setName("李老师");
        when(teacherUserMapper.findById(3L)).thenReturn(teacher);

        assertEquals("李老师", resolver.resolve("ROLE_TEACHER_COUNSELOR", "3", "t003"));
        verifyNoInteractions(adminUserMapper, studentUserMapper, companyUserMapper);
    }

    @Test
    void resolvesCompanyDisplayName() {
        CompanyUser company = new CompanyUser();
        company.setCompanyName("示例科技有限公司");
        when(companyUserMapper.findByUsername("示例科技有限公司")).thenReturn(company);

        assertEquals("示例科技有限公司", resolver.resolve("ROLE_COMPANY", null, "示例科技有限公司"));
        verifyNoInteractions(adminUserMapper, teacherUserMapper, studentUserMapper);
    }
}
