package com.internship.service.impl;

import com.internship.entity.StudentUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.GroupMapper;
import com.internship.mapper.StudentInternshipStatusMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.service.ClassService;
import com.internship.service.DepartmentService;
import com.internship.service.MajorService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentRegistrationAtomicityTest {
    @Test
    void failedInternshipStatusCreationFailsWholeAccountRegistration() {
        StudentUserMapper students = mock(StudentUserMapper.class);
        StudentInternshipStatusMapper statuses = mock(StudentInternshipStatusMapper.class);
        when(students.insert(any())).thenAnswer(invocation -> {
            StudentUser student = invocation.getArgument(0);
            student.setId(71L);
            return 1;
        });
        when(statuses.insert(any())).thenReturn(0);
        StudentUserServiceImpl service = new StudentUserServiceImpl(students, mock(GroupMapper.class),
                statuses, new BCryptPasswordEncoder(), mock(MajorService.class),
                mock(ClassService.class), mock(DepartmentService.class));
        StudentUser student = new StudentUser();
        student.setStudentId("S71");
        student.setName("Student");
        student.setPassword("local-test-password");
        student.setMajorId(1L);
        student.setClassId(2L);
        student.setGrade(2024);

        assertThrows(BusinessException.class, () -> service.register(student));
        verify(statuses).insert(argThat(status -> Long.valueOf(71L).equals(status.getStudentId())));
    }
}
