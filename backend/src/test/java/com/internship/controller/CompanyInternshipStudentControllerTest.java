package com.internship.controller;

import com.internship.entity.User;
import com.internship.entity.dto.CompanyInternshipStudent;
import com.internship.exception.GlobalExceptionHandler;
import com.internship.mapper.CompanyInternshipStudentMapper;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompanyInternshipStudentControllerTest {
    private final CompanyInternshipStudentMapper mapper = mock(CompanyInternshipStudentMapper.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new CompanyInternshipStudentController(mapper))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        User actor = new User();
        actor.setId(100L);
        actor.setRole("ROLE_COMPANY");
        CurrentHolder.setUser(actor);
    }

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
    }

    @Test
    void rosterAlwaysUsesAuthenticatedCompanyEvenWhenAnotherIdIsSupplied() throws Exception {
        CompanyInternshipStudent row = new CompanyInternshipStudent();
        row.setStudentId(7L);
        row.setStudentName("测试学生");
        row.setStudentNumber("TEST0007");
        when(mapper.countCurrentStudents(100L, "测试")).thenReturn(21L);
        when(mapper.findCurrentStudents(100L, "测试", 20L, 20)).thenReturn(List.of(row));

        mvc.perform(get("/api/company/internship-students").param("companyId", "200")
                        .param("keyword", " 测试 ").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(21))
                .andExpect(jsonPath("$.data.rows[0].studentNumber").value("TEST0007"))
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..username").doesNotExist());
        verify(mapper).countCurrentStudents(100L, "测试");
        verify(mapper).findCurrentStudents(100L, "测试", 20L, 20);
        verify(mapper, never()).countCurrentStudents(eq(200L), any());
    }

    @Test
    void missingOrNonCompanyIdentityCannotReadTheRoster() throws Exception {
        CurrentHolder.remove();
        mvc.perform(get("/api/company/internship-students")).andExpect(status().isForbidden());
        User student = new User();
        student.setId(100L);
        student.setRole("ROLE_STUDENT");
        CurrentHolder.setUser(student);
        mvc.perform(get("/api/company/internship-students")).andExpect(status().isForbidden());
        verifyNoInteractions(mapper);
    }

    @Test
    void paginationIsBoundedAndEmptyResultsDoNotFetchStudentRows() throws Exception {
        when(mapper.countCurrentStudents(100L, null)).thenReturn(0L);
        mvc.perform(get("/api/company/internship-students").param("page", "0").param("pageSize", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(100))
                .andExpect(jsonPath("$.data.rows").isEmpty());
        verify(mapper, never()).findCurrentStudents(anyLong(), any(), anyLong(), anyInt());
    }
}
