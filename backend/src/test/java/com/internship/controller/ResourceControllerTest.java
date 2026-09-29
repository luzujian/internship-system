package com.internship.controller;

import com.internship.entity.LearningResource;
import com.internship.entity.User;
import com.internship.service.ResourceService;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ResourceControllerTest {
    @Mock
    private ResourceService resourceService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ResourceController controller = new ResourceController();
        org.springframework.test.util.ReflectionTestUtils
                .setField(controller, "resourceService", resourceService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void clearUser() {
        CurrentHolder.remove();
    }

    private void user(long id, String role) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        CurrentHolder.setUser(user);
    }

    @Test
    void searchResourcesUsesCurrentPostContract() throws Exception {
        when(resourceService.semanticSearch("Java Spring Boot"))
                .thenReturn(List.of(new LearningResource()));

        mockMvc.perform(post("/api/resources/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Java Spring Boot\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void aiSearchResourcesUsesCurrentPostContract() throws Exception {
        when(resourceService.aiSmartSearch("数据库", "PDF", 1, 5))
                .thenReturn(Map.of("total", 1, "list", List.of(Map.of("id", 2))));

        mockMvc.perform(post("/api/resources/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"数据库\",\"fileType\":\"PDF\",\"page\":1,\"pageSize\":5}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.list").isArray())
                .andExpect(jsonPath("$.list.length()").value(1));
    }

    @Test
    void emptyQueryStillUsesSearchService() throws Exception {
        when(resourceService.semanticSearch("")).thenReturn(List.of());

        mockMvc.perform(post("/api/resources/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void aiSearchRequestDefaultsAreAccepted() throws Exception {
        when(resourceService.aiSmartSearch("Spring", null, 1, 10)).thenReturn(Map.of());

        mockMvc.perform(post("/api/resources/ai/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Spring\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void malformedSearchBodyIsRejected() throws Exception {
        mockMvc.perform(post("/api/resources/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(resourceService);
    }

    @Test
    void uploadUsesAuthenticatedIdentityInsteadOfSubmittedUploader() throws Exception {
        user(41L, "ROLE_STUDENT");
        when(resourceService.uploadResource(any(), any(), eq(41L), eq("STUDENT"))).thenReturn(77L);

        mockMvc.perform(multipart("/api/resources/upload")
                        .param("title", "测试资源")
                        .param("uploaderId", "999")
                        .param("uploaderRole", "ADMIN"))
                .andExpect(status().isOk());

        verify(resourceService).uploadResource(any(), any(), eq(41L), eq("STUDENT"));
    }

    @Test
    void uploadWithoutTrustedIdentityCannotPublishAsAdmin() throws Exception {
        mockMvc.perform(multipart("/api/resources/upload")
                        .param("title", "伪造上传")
                        .param("uploaderId", "1")
                        .param("uploaderRole", "ADMIN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(resourceService);
    }

    @Test
    void collegeTeacherUploadKeepsTeacherWorkflow() throws Exception {
        user(52L, "ROLE_TEACHER_COLLEGE");
        when(resourceService.uploadResource(any(), any(), eq(52L), eq("TEACHER"))).thenReturn(78L);

        mockMvc.perform(multipart("/api/resources/upload").param("title", "教师资料"))
                .andExpect(status().isOk());
        verify(resourceService).uploadResource(any(), any(), eq(52L), eq("TEACHER"));
    }

    @Test
    void reviewUsesAuthenticatedAdminIdAndRejectsOtherRoles() throws Exception {
        user(7L, "ROLE_ADMIN");
        mockMvc.perform(post("/api/resources/15/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"reviewerId\":999}"))
                .andExpect(status().isOk());
        verify(resourceService).reviewResource(15L, true, 7L);

        user(8L, "ROLE_STUDENT");
        mockMvc.perform(post("/api/resources/15/review")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true,\"reviewerId\":7}"))
                .andExpect(status().isForbidden());
        verify(resourceService).reviewResource(15L, true, 7L);
    }

    @Test
    void unpublishedDetailIsVisibleOnlyToUploaderOrAdmin() throws Exception {
        LearningResource pending = new LearningResource();
        pending.setId(15L);
        pending.setStatus("PENDING");
        pending.setUploaderId(41L);
        pending.setUploaderRole("STUDENT");
        when(resourceService.getResourceById(15L)).thenReturn(pending);

        user(42L, "ROLE_STUDENT");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/resources/15"))
                .andExpect(status().isForbidden());
        user(41L, "ROLE_COMPANY");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/resources/15"))
                .andExpect(status().isForbidden());
        user(41L, "ROLE_STUDENT");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/resources/15"))
                .andExpect(status().isOk());
        user(7L, "ROLE_ADMIN");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/resources/15"))
                .andExpect(status().isOk());
    }
}
