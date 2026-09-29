package com.internship.controller;

import com.internship.entity.ResourceDocument;
import com.internship.entity.User;
import com.internship.mapper.ResourceViewRecordMapper;
import com.internship.service.ResourceDocumentService;
import com.internship.utils.AliyunOSSOperator;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ResourceDocumentStudentVisibilityTest {
    @Test
    void teacherCannotReadOtherTeachersDraftButCanReadOwn() {
        User teacher = new User(); teacher.setId(10L); teacher.setRole("ROLE_TEACHER_COUNSELOR");
        CurrentHolder.setUser(teacher);
        var own = document(10L, "DRAFT", "ALL", null); own.setPublisherId(10L); own.setPublisherRole("COUNSELOR");
        var foreign = document(11L, "DRAFT", "ALL", null); foreign.setPublisherId(11L);
        when(service.findById(10L)).thenReturn(own);
        when(service.findById(11L)).thenReturn(foreign);
        assertEquals(own, controller.getResourceDocumentById(10L).getData());
        assertThrows(AccessDeniedException.class, () -> controller.getResourceDocumentById(11L));
        assertThrows(AccessDeniedException.class, () -> controller.downloadResourceDocument(11L));
        foreign.setPublisherId(10L); foreign.setPublisherRole("ADMIN");
        assertThrows(AccessDeniedException.class, () -> controller.getResourceDocumentById(11L));
        verifyNoInteractions(oss);
    }
    private ResourceDocumentController controller;
    private ResourceDocumentService service;
    private ResourceViewRecordMapper records;
    private AliyunOSSOperator oss;

    @BeforeEach
    void setUp() {
        controller = new ResourceDocumentController();
        service = mock(ResourceDocumentService.class);
        records = mock(ResourceViewRecordMapper.class);
        oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "resourceDocumentService", service);
        ReflectionTestUtils.setField(controller, "resourceViewRecordMapper", records);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);
        User student = new User();
        student.setId(1L);
        student.setRole("ROLE_STUDENT");
        CurrentHolder.setUser(student);
    }

    @AfterEach
    void tearDown() {
        CurrentHolder.remove();
    }

    @Test
    void studentListsAndSearchesOnlyPublishedUntargetedStudentDocuments() {
        ResourceDocument all = document(1L, "PUBLISHED", "ALL", null);
        ResourceDocument students = document(2L, "PUBLISHED", "STUDENT", "");
        List<ResourceDocument> returned = List.of(all, students,
                document(3L, "DRAFT", "ALL", null),
                document(4L, "PUBLISHED", "TEACHER", null),
                document(5L, "PUBLISHED", "STUDENT", "other-group"));
        when(service.findPublished()).thenReturn(returned);
        when(service.list("keyword", "PUBLISHED", null)).thenReturn(returned);

        assertEquals(List.of(all, students), controller.getAllResourceDocuments().getData());
        assertEquals(List.of(all, students), controller.getPublishedResourceDocuments().getData());
        assertEquals(List.of(all, students), controller.searchResourceDocuments("keyword", "DRAFT", null).getData());
        verify(service, never()).findAll();
        verify(service).list("keyword", "PUBLISHED", null);
    }

    @Test
    void hiddenDocumentDetailAndDownloadHaveNoSideEffects() {
        for (ResourceDocument hidden : List.of(
                document(3L, "DRAFT", "ALL", null),
                document(4L, "PUBLISHED", "TEACHER", null),
                document(5L, "PUBLISHED", "STUDENT", "other-group"))) {
            when(service.findById(hidden.getId())).thenReturn(hidden);
            assertThrows(AccessDeniedException.class,
                    () -> controller.getResourceDocumentById(hidden.getId()));
            assertThrows(AccessDeniedException.class,
                    () -> controller.downloadResourceDocument(hidden.getId()));
        }
        verifyNoInteractions(oss, records);
        verify(service, never()).incrementViewCount(anyLong());
        verify(service, never()).incrementDownloadCount(anyLong());
    }

    @Test
    void publishedStudentDocumentCanBeReadAndDownloaded() throws Exception {
        ResourceDocument allowed = document(1L, "PUBLISHED", "STUDENT", null);
        when(service.findById(1L)).thenReturn(allowed);
        when(oss.downloadResourceDocumentFile("oss-object")).thenReturn(new byte[]{1, 2});

        assertEquals(allowed, controller.getResourceDocumentById(1L).getData());
        assertNotNull(controller.downloadResourceDocument(1L).getBody());
        verify(service).incrementViewCount(1L);
        verify(service).incrementDownloadCount(1L);
        verify(oss).downloadResourceDocumentFile("oss-object");
    }

    @Test
    void studentTeacherAndAdminUseTheSameAuthorizedHttpDownload() throws Exception {
        ResourceDocument allowed = document(1L, "PUBLISHED", "STUDENT", null);
        when(service.findById(1L)).thenReturn(allowed);
        when(oss.downloadResourceDocumentFile("oss-object")).thenReturn(new byte[]{1, 2});
        var mvc = MockMvcBuilders.standaloneSetup(controller).build();

        for (String role : List.of("ROLE_STUDENT", "ROLE_TEACHER_COLLEGE", "ROLE_ADMIN")) {
            User reader = new User();
            reader.setId(1L);
            reader.setRole(role);
            CurrentHolder.setUser(reader);
            mvc.perform(get("/api/resource-documents/download/1"))
                    .andExpect(status().isOk())
                    .andExpect(content().bytes(new byte[]{1, 2}));
        }
        verify(oss, times(3)).downloadResourceDocumentFile("oss-object");
    }

    @Test
    void missingIdentityFailsClosedBeforeQuery() {
        CurrentHolder.remove();
        assertThrows(AccessDeniedException.class, controller::getAllResourceDocuments);
        assertThrows(AccessDeniedException.class, () -> controller.downloadResourceDocument(1L));
        verifyNoInteractions(service, oss);
    }

    private ResourceDocument document(Long id, String status, String targetType, String targetValue) {
        ResourceDocument document = new ResourceDocument();
        document.setId(id);
        document.setStatus(status);
        document.setTargetType(targetType);
        document.setTargetValue(targetValue);
        document.setFileUrl("oss-object");
        document.setFileName("file.pdf");
        return document;
    }
}
