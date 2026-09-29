package com.internship.service.impl;

import com.internship.entity.Class;
import com.internship.entity.ClassCounselorRelation;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.ClassMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CounselorClassListTest {
    private final ClassCounselorRelationServiceImpl service = new ClassCounselorRelationServiceImpl();
    private final ClassCounselorRelationMapper relations = mock(ClassCounselorRelationMapper.class);
    private final ClassMapper classes = mock(ClassMapper.class);

    CounselorClassListTest() {
        ReflectionTestUtils.setField(service, "classCounselorRelationMapper", relations);
        ReflectionTestUtils.setField(service, "classMapper", classes);
    }

    @Test
    void separatelyAssignedClassAppearsEvenWhenPrimaryTeacherIsDifferent() {
        Class row = new Class();
        row.setId(11L);
        row.setTeacherId("another-teacher");
        row.setStudentCount(35);
        when(relations.findByCounselorId(49L)).thenReturn(List.of(relation(11L)));
        when(classes.findById(11L)).thenReturn(row);

        assertEquals(List.of(row), service.findClassesByCounselorId(49L));
        verify(classes, never()).findByTeacherUserId(anyString());
    }

    @Test
    void duplicateAndDeletedRelationsDoNotDuplicateOrHideValidClasses() {
        Class row = new Class();
        row.setId(11L);
        when(relations.findByCounselorId(49L)).thenReturn(
                List.of(relation(11L), relation(11L), relation(12L), relation(null)));
        when(classes.findById(11L)).thenReturn(row);

        assertEquals(List.of(row), service.findClassesByCounselorId(49L));
        verify(classes, times(1)).findById(11L);
    }

    @Test
    void unassignedCounselorDoesNotReceiveOtherClasses() {
        when(relations.findByCounselorId(49L)).thenReturn(List.of());
        assertEquals(List.of(), service.findClassesByCounselorId(49L));
        verifyNoInteractions(classes);
    }

    private ClassCounselorRelation relation(Long classId) {
        ClassCounselorRelation row = new ClassCounselorRelation();
        row.setClassId(classId);
        row.setCounselorId(49L);
        return row;
    }
}
