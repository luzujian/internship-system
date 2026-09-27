package com.internship.controller;

import com.internship.entity.StudentApplication;
import com.internship.entity.User;
import com.internship.service.StudentApplicationService;
import com.internship.service.UserService;
import com.internship.utils.AliyunOSSOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class StudentApplicationMaterialAccessTest {
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void ownApplicationCanReadRecordMaterialButOtherStudentCannotReachOss() throws Exception {
        StudentApplicationController controller = new StudentApplicationController();
        StudentApplicationService applications = mock(StudentApplicationService.class);
        UserService users = mock(UserService.class);
        AliyunOSSOperator oss = mock(AliyunOSSOperator.class);
        ReflectionTestUtils.setField(controller, "studentApplicationService", applications);
        ReflectionTestUtils.setField(controller, "userService", users);
        ReflectionTestUtils.setField(controller, "aliyunOSSOperator", oss);

        User actor = new User();
        actor.setId(51L);
        actor.setUsername("S0051");
        when(users.findByUsername("S0051")).thenReturn(actor);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                org.springframework.security.core.userdetails.User.withUsername("S0051")
                        .password("unused").roles("STUDENT").build(), null));

        StudentApplication item = new StudentApplication();
        item.setStudentId(52L);
        String legacyUrl = "https://school-files.oss-cn-beijing.aliyuncs.com/resources/pdf/2026/09/"
                + "123e4567-e89b-12d3-a456-426614174000.pdf";
        item.setMaterials(Map.of("delayPlan", legacyUrl));
        when(applications.findById(71L)).thenReturn(item);
        assertThrows(AccessDeniedException.class, () -> controller.getApplicationById(71L));
        assertEquals(403, controller.downloadMyApplicationMaterial(71L, "delayPlan").getStatusCode().value());
        verifyNoInteractions(oss);

        item.setStudentId(51L);
        assertEquals("uploaded.pdf", ((StudentApplication) controller.getApplicationById(71L).getData())
                .getMaterials().get("delayPlan"));
        when(oss.downloadRecordAttachment(legacyUrl, 51L)).thenReturn(new byte[] {1, 2});
        assertEquals(200, controller.downloadMyApplicationMaterial(71L, "delayPlan").getStatusCode().value());
        verify(oss).downloadRecordAttachment(legacyUrl, 51L);
        assertEquals(404, controller.downloadMyApplicationMaterial(71L, "unlisted").getStatusCode().value());
    }
}
