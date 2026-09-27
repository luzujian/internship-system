package com.internship.service;

import com.internship.entity.StudentReminder;
import com.internship.entity.User;
import com.internship.mapper.StudentReminderMapper;
import com.internship.service.impl.ReminderServiceImpl;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReminderConfirmationAuthorizationTest {
    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
    }

    @Test
    void anotherStudentsReminderCannotBeConfirmed() {
        StudentReminderMapper mapper = mock(StudentReminderMapper.class);
        ReminderServiceImpl service = service(mapper);
        identity("ROLE_STUDENT", 20L);
        StudentReminder reminder = reminder(50L, 21L);
        when(mapper.selectById(50L)).thenReturn(reminder);

        assertThrows(AccessDeniedException.class, () -> service.confirmReminder(50L));
        verify(mapper, never()).updateById(any());
    }

    @Test
    void owningStudentCanConfirmReminder() {
        StudentReminderMapper mapper = mock(StudentReminderMapper.class);
        ReminderServiceImpl service = service(mapper);
        identity("ROLE_STUDENT", 20L);
        StudentReminder reminder = reminder(50L, 20L);
        when(mapper.selectById(50L)).thenReturn(reminder);

        service.confirmReminder(50L);

        assertEquals(1, reminder.getIsConfirmed());
        verify(mapper).updateById(reminder);
    }

    @Test
    void otherRoleWithSameNumericIdCannotConfirmReminder() {
        StudentReminderMapper mapper = mock(StudentReminderMapper.class);
        ReminderServiceImpl service = service(mapper);
        identity("ROLE_COMPANY", 20L);

        assertThrows(AccessDeniedException.class, () -> service.confirmReminder(50L));
        verifyNoInteractions(mapper);
    }

    private ReminderServiceImpl service(StudentReminderMapper mapper) {
        ReminderServiceImpl service = new ReminderServiceImpl();
        ReflectionTestUtils.setField(service, "reminderMapper", mapper);
        return service;
    }

    private StudentReminder reminder(Long id, Long studentId) {
        StudentReminder reminder = new StudentReminder();
        reminder.setId(id);
        reminder.setStudentId(studentId);
        reminder.setIsConfirmed(0);
        return reminder;
    }

    private void identity(String role, Long id) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        CurrentHolder.setUser(user);
    }
}
