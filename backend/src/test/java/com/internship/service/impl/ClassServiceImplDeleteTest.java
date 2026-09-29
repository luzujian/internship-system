package com.internship.service.impl;

import com.internship.entity.Class;
import com.internship.mapper.ClassCounselorRelationMapper;
import com.internship.mapper.ClassMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.MajorService;
import com.internship.service.StudentUserService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ClassServiceImplDeleteTest {

    @Test
    void deletingClassRemovesCounselorRelationsBeforeClassRow() {
        ClassMapper classes = mock(ClassMapper.class);
        ClassCounselorRelationMapper relations = mock(ClassCounselorRelationMapper.class);
        StudentUserService students = mock(StudentUserService.class);
        ClassServiceImpl service = service(classes, relations, students);

        Class existing = new Class();
        existing.setId(153L);
        when(classes.findByIdForUpdate(153L)).thenReturn(existing);
        when(relations.deleteByClassId(153L)).thenReturn(1);
        when(students.deleteByClassId(153L)).thenReturn(0);
        when(classes.delete(153L)).thenReturn(1);

        service.delete(153L);

        var ordered = inOrder(relations, students, classes);
        ordered.verify(relations).deleteByClassId(153L);
        ordered.verify(students).deleteByClassId(153L);
        ordered.verify(classes).delete(153L);
    }

    @Test
    void relationCleanupFailurePreventsClassDeletion() {
        ClassMapper classes = mock(ClassMapper.class);
        ClassCounselorRelationMapper relations = mock(ClassCounselorRelationMapper.class);
        StudentUserService students = mock(StudentUserService.class);
        ClassServiceImpl service = service(classes, relations, students);

        Class existing = new Class();
        existing.setId(153L);
        when(classes.findByIdForUpdate(153L)).thenReturn(existing);
        when(relations.deleteByClassId(153L)).thenThrow(new RuntimeException("relation cleanup failed"));

        assertThrows(RuntimeException.class, () -> service.delete(153L));
        verify(classes, never()).delete(153L);
        verifyNoInteractions(students);
    }

    private ClassServiceImpl service(ClassMapper classes,
                                     ClassCounselorRelationMapper relations,
                                     StudentUserService students) {
        ClassServiceImpl service = new ClassServiceImpl();
        ReflectionTestUtils.setField(service, "classMapper", classes);
        ReflectionTestUtils.setField(service, "classCounselorRelationMapper", relations);
        ReflectionTestUtils.setField(service, "studentUserService", students);
        ReflectionTestUtils.setField(service, "majorService", mock(MajorService.class));
        ReflectionTestUtils.setField(service, "teacherUserMapper", mock(TeacherUserMapper.class));
        return service;
    }
}
