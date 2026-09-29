package com.internship.service;

import com.internship.entity.CompanySchoolLiaison;
import com.internship.entity.CompanyUser;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanySchoolLiaisonMapper;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.TeacherUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanySchoolLiaisonServiceTest {
    @Mock
    private CompanySchoolLiaisonMapper liaisonMapper;
    @Mock
    private CompanyUserMapper companyUserMapper;
    @Mock
    private TeacherUserMapper teacherUserMapper;

    private CompanySchoolLiaisonService service;

    @BeforeEach
    void setUp() {
        service = new CompanySchoolLiaisonService(
                liaisonMapper, companyUserMapper, teacherUserMapper);
    }

    @Test
    void ordinaryTeacherCannotReplaceLiaisons() {
        when(teacherUserMapper.findById(20L)).thenReturn(teacher(20L, "COUNSELOR"));

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.replace(10L, List.of(30L), 20L));

        assertEquals("只有系主任可以维护校内对接人", error.getMessage());
    }

    @Test
    void departmentHeadCanReplaceAndDuplicateTeacherIdsAreCollapsed() {
        when(teacherUserMapper.findById(20L)).thenReturn(teacher(20L, "DEPARTMENT"));
        when(companyUserMapper.findById(10L)).thenReturn(new CompanyUser());
        when(teacherUserMapper.selectByIds(List.of(30L, 31L)))
                .thenReturn(List.of(teacher(30L, "COUNSELOR"), teacher(31L, "COLLEGE")));

        service.replace(10L, List.of(30L, 30L, 31L), 20L);

        verify(liaisonMapper).deleteByCompanyId(10L);
        ArgumentCaptor<List<CompanySchoolLiaison>> captor = ArgumentCaptor.forClass(List.class);
        verify(liaisonMapper).batchInsert(captor.capture());
        assertEquals(List.of(30L, 31L), captor.getValue().stream()
                .map(CompanySchoolLiaison::getTeacherId).toList());
    }

    private TeacherUser teacher(Long id, String type) {
        TeacherUser teacher = new TeacherUser();
        teacher.setId(id);
        teacher.setTeacherType(type);
        teacher.setStatus(1);
        return teacher;
    }
}
