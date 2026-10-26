package com.internship.controller;

import com.internship.dto.TeacherCompanyDTO;
import com.internship.dto.TeacherRecruitmentPositionDTO;
import com.internship.entity.PageResult;
import com.internship.entity.TeacherRole;
import com.internship.entity.TeacherUser;
import com.internship.entity.User;
import com.internship.interceptor.TeacherPermissionInterceptor;
import com.internship.service.TeacherPermissionService;
import com.internship.service.TeacherRecruitmentService;
import com.internship.service.TeacherRoleService;
import com.internship.service.TeacherUserService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TeacherRecruitmentControllerTest {
    private AnnotationConfigApplicationContext context;
    private TeacherRecruitmentController controller;
    private TeacherRecruitmentService service;
    private TeacherPermissionService permissions;
    private TeacherPermissionInterceptor interceptor;
    private TeacherUser teacher;

    @Configuration
    @EnableMethodSecurity
    static class SecurityTestConfig {
        @Bean TeacherRecruitmentService service() { return mock(TeacherRecruitmentService.class); }
        @Bean TeacherRecruitmentController controller(TeacherRecruitmentService service) {
            return new TeacherRecruitmentController(service);
        }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigApplicationContext(SecurityTestConfig.class);
        controller = context.getBean(TeacherRecruitmentController.class);
        service = context.getBean(TeacherRecruitmentService.class);
        TeacherUserService teachers = mock(TeacherUserService.class);
        TeacherRoleService roles = mock(TeacherRoleService.class);
        permissions = mock(TeacherPermissionService.class);
        interceptor = new TeacherPermissionInterceptor();
        ReflectionTestUtils.setField(interceptor, "teacherUserService", teachers);
        ReflectionTestUtils.setField(interceptor, "teacherRoleService", roles);
        ReflectionTestUtils.setField(interceptor, "teacherPermissionService", permissions);
        teacher = new TeacherUser();
        teacher.setRole("ROLE_TEACHER");
        teacher.setTeacherType("COLLEGE");
        when(teachers.findByTeacherUserId("teacher")).thenReturn(teacher);
        TeacherRole role = new TeacherRole();
        role.setId(1L);
        when(roles.findByRoleCode(anyString())).thenReturn(role);
        when(permissions.getPermissionCodesByRoleId(1L)).thenReturn(List.of("menu:recruitment"));
        User user = new User();
        user.setUsername("teacher");
        CurrentHolder.setUser(user);
    }

    @AfterEach void close() {
        SecurityContextHolder.clearContext();
        CurrentHolder.remove();
        context.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_TEACHER_COLLEGE", "ROLE_TEACHER_DEPARTMENT", "ROLE_TEACHER_COUNSELOR"})
    void everyTeacherLevelCanReadTheSameCatalogueAndCompanyMaterials(String role) throws Exception {
        authenticate(role, "menu:recruitment");
        teacher.setTeacherType(role.substring("ROLE_TEACHER_".length()));
        teacher.setRole(role);
        TeacherRecruitmentPositionDTO position = new TeacherRecruitmentPositionDTO();
        position.setId(19L);
        position.setCompanyId(7L);
        position.setStatus("paused");
        position.setCompanyPositionCount(3);
        position.setCategoryId(4L);
        position.setCategoryName("技术研发");
        TeacherCompanyDTO company = new TeacherCompanyDTO();
        company.setIntroduction("企业自主上传的简介");
        company.setPhotos("[\"https://example.com/company.jpg\"]");
        when(service.findPage(1, 10, null, null, null, null)).thenReturn(PageResult.build(1L, List.of(position)));
        when(service.findDetail(19L)).thenReturn(new TeacherRecruitmentService.Detail(position, company));

        var mvc = mvc();
        mvc.perform(get("/api/teacher/recruitment/positions"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.rows[0].status").value("paused"))
                .andExpect(jsonPath("$.data.rows[0].categoryName").value("技术研发"));
        mvc.perform(get("/api/teacher/recruitment/positions/19"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.position.companyPositionCount").value(3))
                .andExpect(jsonPath("$.data.position.categoryName").value("技术研发"))
                .andExpect(jsonPath("$.data.company.introduction").value("企业自主上传的简介"))
                .andExpect(jsonPath("$.data.company.photos").isNotEmpty())
                .andExpect(jsonPath("$.data.company.password").doesNotExist())
                .andExpect(jsonPath("$.data.company.businessLicense").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ROLE_ADMIN", "ROLE_STUDENT", "ROLE_COMPANY", "ROLE_TEACHER"})
    void unrelatedRolesAreDeniedEvenWithTheMenuAuthority(String role) {
        authenticate(role, "menu:recruitment");
        assertThrows(AccessDeniedException.class, () -> controller.getPositions(1, 10, null, null, null, null));
        assertThrows(AccessDeniedException.class, () -> controller.getPosition(19L));
        verifyNoInteractions(service);
    }

    @Test void teacherWithoutMenuPermissionCannotRead() throws Exception {
        authenticate("ROLE_TEACHER_COUNSELOR");
        when(permissions.getPermissionCodesByRoleId(1L)).thenReturn(List.of());
        mvc().perform(get("/api/teacher/recruitment/positions")).andExpect(status().isForbidden());
        mvc().perform(get("/api/teacher/recruitment/positions/19")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void categoryFilterIsPassedWithTheOtherSearchConditions() throws Exception {
        authenticate("ROLE_TEACHER_COUNSELOR", "menu:recruitment");
        teacher.setTeacherType("COUNSELOR");
        when(service.findPage(2, 20, "企业", "工程师", "active", 4L))
                .thenReturn(PageResult.build(0L, List.of()));
        mvc().perform(get("/api/teacher/recruitment/positions")
                        .param("page", "2").param("pageSize", "20")
                        .param("companyName", "企业").param("positionName", "工程师")
                        .param("status", "active").param("categoryId", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        verify(service).findPage(2, 20, "企业", "工程师", "active", 4L);
    }

    @Test void permissionRevocationTakesEffectEvenIfTokenStillContainsTheMenu() throws Exception {
        authenticate("ROLE_TEACHER_COLLEGE", "menu:recruitment");
        when(permissions.getPermissionCodesByRoleId(1L)).thenReturn(List.of());
        mvc().perform(get("/api/teacher/recruitment/positions/19")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }

    @Test void publishedCatalogueProvidesNoMutationEndpoints() throws Exception {
        authenticate("ROLE_TEACHER_COLLEGE", "menu:recruitment");
        var mvc = mvc();
        mvc.perform(post("/api/teacher/recruitment/positions")).andExpect(status().isMethodNotAllowed());
        mvc.perform(put("/api/teacher/recruitment/positions/19")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/teacher/recruitment/positions/19")).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(service);
    }

    private void authenticate(String... authorities) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "teacher", "unused", Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList()));
    }

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(controller).addInterceptors(interceptor).build();
    }
}
