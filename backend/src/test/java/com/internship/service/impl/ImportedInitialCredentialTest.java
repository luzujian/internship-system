package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.entity.Division;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.GroupMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ClassService;
import com.internship.service.CompanyAlumniService;
import com.internship.service.DepartmentService;
import com.internship.service.DivisionService;
import com.internship.service.MajorService;
import com.internship.service.automation.AutomationTemplates;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ImportedInitialCredentialTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    void studentImportStoresHashAndReturnsFixedDefaultCredential() {
        StudentUserMapper mapper = mock(StudentUserMapper.class);
        when(mapper.findByStudentIds(anyList())).thenReturn(List.of());
        when(mapper.batchInsert(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        StudentUserServiceImpl service = new StudentUserServiceImpl(mapper, mock(GroupMapper.class),
                mock(StudentInternshipStatusMapper.class), encoder, mock(MajorService.class),
                mock(ClassService.class), mock(DepartmentService.class));

        Map<String, Object> result = service.importFromExcel(List.of(Map.of(
                "学号", "S101", "姓名", "Student", "年级", "2024", "专业ID", "1", "班级ID", "2")));

        assertEquals(1, result.get("successCount"));
        StudentUser inserted = mapperStudent(mapper);
        assertCredential(result, "S101", inserted.getPassword());
    }

    @Test
    void teacherImportStoresHashAndReturnsFixedDefaultCredential() {
        TeacherUserMapper mapper = mock(TeacherUserMapper.class);
        when(mapper.findByTeacherIds(anyList())).thenReturn(List.of());
        when(mapper.batchInsert(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        TeacherUser saved = new TeacherUser();
        saved.setId(10L);
        saved.setTeacherUserId("T101");
        when(mapper.findByTeacherUserId("T101")).thenReturn(saved);
        TeacherUserServiceImpl service = new TeacherUserServiceImpl();
        ReflectionTestUtils.setField(service, "teacherUserMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "automationTemplates", mock(AutomationTemplates.class));

        Map<String, Object> result = service.importFromExcel(List.of(Map.of(
                "工号", "T101", "姓名", "Teacher", "院系 ID", "1", "教师类型", "学院教师")));

        assertEquals(1, result.get("successCount"));
        TeacherUser inserted = mapperTeacher(mapper);
        assertEquals("ROLE_TEACHER_COLLEGE", inserted.getRole());
        assertCredential(result, "T101", inserted.getPassword());
    }

    @Test
    void departmentTeacherImportResolvesDivisionAndPersistsSpecificRole() {
        TeacherUserMapper mapper = mock(TeacherUserMapper.class);
        when(mapper.findByTeacherIds(anyList())).thenReturn(List.of());
        when(mapper.batchInsert(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        TeacherUser saved = new TeacherUser();
        saved.setId(11L);
        saved.setTeacherUserId("T102");
        when(mapper.findByTeacherUserId("T102")).thenReturn(saved);
        DivisionService divisions = mock(DivisionService.class);
        Division division = new Division();
        division.setId(2L);
        division.setDepartmentId(1L);
        division.setName("信息系");
        when(divisions.findByDepartmentId(1L)).thenReturn(List.of(division));
        when(divisions.findById(2L)).thenReturn(division);
        TeacherUserServiceImpl service = new TeacherUserServiceImpl();
        ReflectionTestUtils.setField(service, "teacherUserMapper", mapper);
        ReflectionTestUtils.setField(service, "divisionService", divisions);
        ReflectionTestUtils.setField(service, "passwordEncoder", encoder);
        ReflectionTestUtils.setField(service, "automationTemplates", mock(AutomationTemplates.class));

        Map<String, Object> result = service.importFromExcel(List.of(Map.of(
                "工号", "T102", "姓名", "Teacher", "院系 ID", "1", "系", "信息系", "教师类型", "系室教师")));

        assertEquals(1, result.get("successCount"));
        TeacherUser inserted = mapperTeacher(mapper);
        assertEquals("2", inserted.getDivisionId());
        assertEquals("ROLE_TEACHER_DEPARTMENT", inserted.getRole());
        assertCredential(result, "T102", inserted.getPassword());
    }

    @Test
    void companyImportStoresHashAndReturnsFixedDefaultCredential() {
        CompanyUserMapper mapper = mock(CompanyUserMapper.class);
        when(mapper.batchInsert(anyList())).thenAnswer(invocation -> ((List<?>) invocation.getArgument(0)).size());
        CompanyUserServiceImpl service = new CompanyUserServiceImpl(mapper, encoder, mock(CompanyAlumniService.class));

        Map<String, Object> result = service.importFromExcel(List.of(Map.of(
                "企业名称", "Test Company", "联系人", "Contact", "联系电话", "13800000000", "企业地址", "Test Address")));

        assertEquals(1, result.get("successCount"));
        CompanyUser inserted = mapperCompany(mapper);
        assertCredential(result, "Test Company", inserted.getPassword());
    }

    private StudentUser mapperStudent(StudentUserMapper mapper) {
        org.mockito.ArgumentCaptor<List<StudentUser>> rows = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(mapper).batchInsert(rows.capture());
        return rows.getValue().getFirst();
    }

    private TeacherUser mapperTeacher(TeacherUserMapper mapper) {
        org.mockito.ArgumentCaptor<List<TeacherUser>> rows = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(mapper).batchInsert(rows.capture());
        return rows.getValue().getFirst();
    }

    private CompanyUser mapperCompany(CompanyUserMapper mapper) {
        org.mockito.ArgumentCaptor<List<CompanyUser>> rows = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(mapper).batchInsert(rows.capture());
        return rows.getValue().getFirst();
    }

    private void assertCredential(Map<String, Object> result, String username, String storedHash) {
        List<?> credentials = (List<?>) result.get("initialCredentials");
        assertEquals(1, credentials.size());
        Map<?, ?> credential = (Map<?, ?>) credentials.getFirst();
        assertEquals(username, credential.get("username"));
        String password = (String) credential.get("password");
        assertEquals("123456", password);
        assertNotEquals(password, storedHash);
        assertTrue(encoder.matches(password, storedHash));
    }
}
