package com.internship.controller;

import com.internship.entity.TeacherUser;
import com.internship.entity.dto.TeacherAppraisalDetail;
import com.internship.exception.GlobalExceptionHandler;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.TeacherInternshipAppraisalService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

// P3：教师端 Long 内部主键（如 studentId / classId）收到学号等非数字字符串时，
// 必须明确 400（参数名 + 应为数字），不得落入兜底 500；合法 Long 与学号 String
// 通道（name / studentUserId）行为不变。本测试为 standalone MockMvc，不连库、
// 不碰业务数据、不经过安全过滤器，只验证参数绑定契约。
class TeacherStudentIdContractTest {

    private MockMvc mvc;
    private TeacherInternshipAppraisalService appraisalService;
    private TeacherUser teacher;

    @BeforeEach
    void setUp() {
        appraisalService = mock(TeacherInternshipAppraisalService.class);
        TeacherUserMapper teacherUserMapper = mock(TeacherUserMapper.class);
        teacher = mock(TeacherUser.class);
        when(teacherUserMapper.findByTeacherUserId("t001")).thenReturn(teacher);
        TeacherInternshipAppraisalController controller =
                new TeacherInternshipAppraisalController(appraisalService, teacherUserMapper);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("t001", null, Collections.emptyList()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void illegalQueryParamReturns400Not500() throws Exception {
        mvc.perform(get("/api/teacher/internship-appraisals").param("classId", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("classId")))
                .andExpect(jsonPath("$.message", containsString("数字")))
                .andExpect(jsonPath("$.message", not(containsString("at "))));
    }

    @Test
    void illegalPathVariableReturns400Not500() throws Exception {
        mvc.perform(get("/api/teacher/internship-appraisals/students/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message", containsString("studentId")))
                .andExpect(jsonPath("$.message", containsString("数字")))
                .andExpect(jsonPath("$.message", not(containsString("at "))));
    }

    @Test
    void legalLongQueryParamStillWorks() throws Exception {
        mvc.perform(get("/api/teacher/internship-appraisals").param("classId", "7"))
                .andExpect(status().isOk());
    }

    @Test
    void legalLongPathVariableStillWorks() throws Exception {
        TeacherAppraisalDetail detail = new TeacherAppraisalDetail();
        detail.setStudentId(9L);
        when(appraisalService.findSubmittedDetail(any(TeacherUser.class), eq(9L))).thenReturn(detail);
        mvc.perform(get("/api/teacher/internship-appraisals/students/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.studentId").value(9));
    }
}
