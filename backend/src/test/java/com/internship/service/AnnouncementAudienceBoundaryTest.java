package com.internship.service;
import com.internship.entity.*;
import com.internship.mapper.StudentUserMapper;
import com.internship.controller.AnnouncementReadRecordController;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AnnouncementAudienceBoundaryTest {
    @Test void restrictedDraftAndFutureCannotCreateReadRecord() {
        var policy=new AnnouncementAudiencePolicy(mock(StudentUserMapper.class));
        var service=mock(AnnouncementService.class); var records=mock(AnnouncementReadRecordService.class);
        var controller=new AnnouncementReadRecordController();
        ReflectionTestUtils.setField(controller,"announcementService",service);
        ReflectionTestUtils.setField(controller,"announcementAudiencePolicy",policy);
        ReflectionTestUtils.setField(controller,"announcementReadRecordService",records);
        var user=new User(); user.setId(1L); user.setRole("ROLE_STUDENT"); CurrentHolder.setUser(user);
        var a=new Announcement(); a.setStatus("PUBLISHED"); a.setTargetType("[\"TEACHER\"]");
        var record=new AnnouncementReadRecord(); record.setAnnouncementId(1L);
        when(service.findById(1L)).thenReturn(a);
        try {
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.createReadRecord(record));
            a.setTargetType("ALL"); a.setStatus("DRAFT");
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.createReadRecord(record));
            a.setStatus("PUBLISHED"); a.setPublishTime(new java.util.Date(System.currentTimeMillis()+60000));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->controller.createReadRecord(record));
            verifyNoInteractions(records);
            a.setPublishTime(null);
            assertEquals(200,controller.createReadRecord(record).getCode());
            assertEquals("1",record.getUserId()); verify(records).insert(record);
        } finally { CurrentHolder.remove(); }
    }
    @Test void teacherTypeRequiresExactAudienceMatch() {
        var policy=new AnnouncementAudiencePolicy(mock(StudentUserMapper.class));
        var a=new Announcement(); a.setStatus("PUBLISHED"); a.setTargetType("TEACHER_TYPE");
        a.setTargetValue("{\"teacherTypes\":[\"COUNSELOR\"]}");
        var user=new User(); user.setRole("ROLE_TEACHER_COLLEGE");
        assertFalse(policy.canReadPublished(a,user));
        user.setRole("ROLE_TEACHER_COUNSELOR"); assertTrue(policy.canReadPublished(a,user));
    }
}
