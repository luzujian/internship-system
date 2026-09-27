package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.Result;
import com.internship.entity.StudentUser;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.service.CompanyTagService;
import com.internship.service.CompanyUserService;
import com.internship.service.StudentUserService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InitialCredentialProvisioningTest {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @AfterEach
    void clearIdentity() {
        CurrentHolder.remove();
    }

    @Test
    void studentCreationIgnoresClientPasswordAndReturnsFixedDefaultCredential() {
        StudentUserService service = mock(StudentUserService.class);
        StudentUserController controller = new StudentUserController();
        ReflectionTestUtils.setField(controller, "studentUserService", service);
        StudentUser student = new StudentUser();
        student.setStudentId("S101");
        student.setPassword("client-supplied-secret");
        when(service.register(any())).thenAnswer(invocation -> {
            StudentUser input = invocation.getArgument(0);
            input.setPassword(encoder.encode(input.getPassword()));
            return 1;
        });

        Result result = controller.addStudentUser(student);

        assertEquals(200, result.getCode());
        assertCredential(result, "S101", student.getPassword());
        verify(service).register(student);
    }

    @Test
    void teacherCreationIgnoresClientPasswordAndReturnsFixedDefaultCredential() {
        TeacherUserService service = mock(TeacherUserService.class);
        TeacherUserController controller = new TeacherUserController();
        ReflectionTestUtils.setField(controller, "teacherUserService", service);
        TeacherUser teacher = new TeacherUser();
        teacher.setTeacherUserId("T101");
        teacher.setTeacherType("COLLEGE");
        teacher.setPassword("client-supplied-secret");
        when(service.register(any())).thenAnswer(invocation -> {
            TeacherUser input = invocation.getArgument(0);
            input.setPassword(encoder.encode(input.getPassword()));
            return 1;
        });

        Result result = controller.addTeacherUser(teacher);

        assertEquals(200, result.getCode());
        assertCredential(result, "T101", teacher.getPassword());
        verify(service).register(teacher);
    }

    @Test
    void companyCreationIgnoresClientPasswordAndReturnsFixedDefaultCredential() {
        CompanyUserService service = mock(CompanyUserService.class);
        CompanyUserController controller = new CompanyUserController();
        ReflectionTestUtils.setField(controller, "companyUserService", service);
        CompanyUser company = new CompanyUser();
        company.setCompanyName("Test Company");
        company.setPassword("client-supplied-secret");
        when(service.insert(any())).thenAnswer(invocation -> {
            CompanyUser input = invocation.getArgument(0);
            input.setPassword(encoder.encode(input.getPassword()));
            return 1;
        });

        Result result = controller.addCompany(company);

        assertEquals(200, result.getCode());
        assertCredential(result, "Test Company", company.getPassword());
        verify(service).insert(company);
    }

    @Test
    void adminApprovalKeepsRegistrationSecretForEmailVerifiedReset() {
        CompanyUserService service = mock(CompanyUserService.class);
        CompanyUserController controller = new CompanyUserController();
        ReflectionTestUtils.setField(controller, "companyUserService", service);
        ReflectionTestUtils.setField(controller, "companyTagService", mock(CompanyTagService.class));
        CompanyUser company = new CompanyUser();
        company.setId(42L);
        company.setCompanyName("Test Company");
        company.setPassword("stored-hash");
        when(service.findById(42L)).thenReturn(company);
        User reviewer = new User();
        reviewer.setId(7L);
        reviewer.setRole("ROLE_ADMIN");
        CurrentHolder.setUser(reviewer);

        Result result = controller.auditCompany(42L, Map.of("auditStatus", 1));

        assertEquals(200, result.getCode());
        assertNull(company.getPassword(), "service update must leave the existing hash untouched");
        verify(service).update(company);
    }

    @Test
    void teacherApprovalKeepsRegistrationSecretForEmailVerifiedReset() {
        CompanyUserService service = mock(CompanyUserService.class);
        TeacherController controller = new TeacherController();
        ReflectionTestUtils.setField(controller, "companyUserService", service);
        CompanyUser company = new CompanyUser();
        company.setId(42L);
        company.setCompanyName("Test Company");
        company.setPassword("stored-hash");
        when(service.findById(42L)).thenReturn(company);
        User reviewer = new User();
        reviewer.setId(7L);
        reviewer.setRole("ROLE_TEACHER_COLLEGE");
        CurrentHolder.setUser(reviewer);

        Result result = controller.auditCompany(42L, Map.of("auditStatus", 1));

        assertEquals(200, result.getCode());
        assertNull(company.getPassword(), "service update must leave the existing hash untouched");
        verify(service).update(company);
    }

    private void assertCredential(Result result, String username, String storedHash) {
        Map<?, ?> data = (Map<?, ?>) result.getData();
        assertNotNull(data);
        List<?> credentials = (List<?>) data.get("initialCredentials");
        assertEquals(1, credentials.size());
        Map<?, ?> credential = (Map<?, ?>) credentials.getFirst();
        assertEquals(username, credential.get("username"));
        String password = (String) credential.get("password");
        assertEquals("123456", password);
        assertNotEquals(password, storedHash);
        assertTrue(encoder.matches(password, storedHash));
    }
}
