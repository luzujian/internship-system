package com.internship.controller;
import com.internship.entity.*;
import com.internship.service.*;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.Map;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class PasswordSessionBoundaryTest {
    private final JwtRedisService revocations=mock(JwtRedisService.class);
    private final PasswordEncoder encoder=mock(PasswordEncoder.class);
    private final UserCacheService cache=mock(UserCacheService.class);
    private final Map<String,String> body=Map.of("oldPassword","old","newPassword","StrongNew#927","confirmPassword","StrongNew#927");
    @BeforeEach void setup() { when(encoder.matches("old","hash")).thenReturn(true); }
    @AfterEach void clear() { CurrentHolder.remove(); org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    private void inject(Object controller) {
        ReflectionTestUtils.setField(controller,"jwtRedisService",revocations);
        ReflectionTestUtils.setField(controller,"passwordEncoder",encoder);
        ReflectionTestUtils.setField(controller,"userCacheService",cache);
    }
    @Test void teacherRevokesSignedEmployeeIdBeforePasswordWrite() {
        var user=new User();user.setId(1L);user.setRole("ROLE_TEACHER_COUNSELOR");CurrentHolder.setUser(user);
        var teacher=new TeacherUser();teacher.setTeacherUserId("employee1");teacher.setPassword("hash");teacher.setEmail("x@example.test");
        var service=mock(TeacherUserService.class);when(service.findById(1L)).thenReturn(teacher);
        var controller=new AccountSettingsController();inject(controller);ReflectionTestUtils.setField(controller,"teacherUserService",service);
        assertEquals(200,controller.changePassword(body,new MockHttpServletRequest()).getCode());
        var order=inOrder(revocations,service);order.verify(revocations).incrementUserTokenVersion("employee1");order.verify(service).update(teacher);
    }
    @Test void companyRevokesBeforePasswordWriteAndFailurePreventsWrite() {
        var user=new User();user.setId(1L);user.setRole("ROLE_COMPANY");CurrentHolder.setUser(user);
        var company=new CompanyUser();company.setUsername("company1");company.setPassword("hash");company.setEmail("x@example.test");
        var service=mock(CompanyUserService.class);when(service.findById(1L)).thenReturn(company);when(service.update(company)).thenReturn(1);
        var controller=new CompanyController();inject(controller);ReflectionTestUtils.setField(controller,"companyUserService",service);
        assertEquals(200,controller.changePassword(body).getCode());
        var order=inOrder(revocations,service);order.verify(revocations).incrementUserTokenVersion("company1");order.verify(service).update(company);
        company.setPassword("hash"); clearInvocations(service);
        when(revocations.incrementUserTokenVersion("company1")).thenThrow(new IllegalStateException("offline"));
        assertNotEquals(200,controller.changePassword(body).getCode()); verify(service,never()).update(any());
    }
    @Test void studentRevokesBeforePasswordWrite() {
        var principal=org.springframework.security.core.userdetails.User.withUsername("student1").password("hash").roles("STUDENT").build();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,"",principal.getAuthorities()));
        var user=new User();user.setId(1L);
        var users=mock(UserService.class);when(users.findByUsername("student1")).thenReturn(user);
        var student=new StudentUser();student.setPassword("hash");student.setEmail("x@example.test");
        var service=mock(StudentUserService.class);when(service.findById(1L)).thenReturn(student);
        var controller=new StudentController();inject(controller);ReflectionTestUtils.setField(controller,"studentUserService",service);ReflectionTestUtils.setField(controller,"userService",users);
        assertEquals(200,controller.changePassword(body).getCode());
        var order=inOrder(revocations,service);order.verify(revocations).incrementUserTokenVersion("student1");order.verify(service).update(student);
    }
    @Test void teacherLogoutRevokesSessionFamily() {
        var jwt=mock(com.internship.utils.JwtUtils.class);var controller=new AccountSettingsController();
        ReflectionTestUtils.setField(controller,"jwtUtils",jwt);
        var request=new MockHttpServletRequest(); request.addHeader("Authorization","Bearer fixture");
        assertEquals(200,controller.logout(request).getCode());verify(jwt).revokeSession("fixture");
    }
}
