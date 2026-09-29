package com.internship.controller;

import com.internship.entity.*;
import com.internship.service.*;
import com.internship.utils.CurrentHolder;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AnnouncementImmutableOwnershipTest {
    private AnnouncementController controller;
    private AnnouncementService service;
    @BeforeEach void setup() {
        controller=new AnnouncementController();service=mock(AnnouncementService.class);
        ReflectionTestUtils.setField(controller,"announcementService",service);
        ReflectionTestUtils.setField(controller,"webSocketHandler",mock(AnnouncementWebSocketHandler.class));
        var actor=new User();actor.setId(9L);actor.setName("同名教师");actor.setRole("ROLE_TEACHER_COLLEGE");CurrentHolder.setUser(actor);
    }
    @AfterEach void clear(){CurrentHolder.remove();}
    private Announcement row(long id,Long owner,String type) {
        var a=new Announcement();a.setId(id);a.setPublisher("同名教师");a.setPublisherRole("COLLEGE");
        a.setPublisherUserId(owner);a.setPublisherUserType(type);a.setStatus("DRAFT");a.setTargetType("STUDENT");return a;
    }
    @Test void sameNameLegacyAndCrossTableIdCannotGrantOwnership() {
        for(var a:List.of(row(1L,10L,"TEACHER"),row(2L,null,null),row(3L,9L,"ADMIN"))) {
            when(service.findById(a.getId())).thenReturn(a);
            assertThrows(AccessDeniedException.class,()->controller.updateAnnouncement(a.getId(),row(a.getId(),9L,"TEACHER")));
            assertThrows(AccessDeniedException.class,()->controller.updateAnnouncementWithAttachments(a.getId(),Map.of("title","forged")));
            assertThrows(AccessDeniedException.class,()->controller.deleteAnnouncement(a.getId()));
            assertThrows(AccessDeniedException.class,()->controller.batchDeleteAnnouncement(Map.of("ids",List.of(a.getId()))));
            assertThrows(AccessDeniedException.class,()->controller.getAnnouncementById(a.getId()));
            assertThrows(AccessDeniedException.class,()->controller.downloadAttachment(a.getId().toString(),"file.pdf"));
        }
        verify(service,never()).update(any());verify(service,never()).delete(any());verify(service,never()).batchDelete(any());
    }
    @Test void renamedOwnerCanEditAndMineFiltersBeforePagination() {
        var own=row(1L,9L,"TEACHER");own.setPublisher("旧姓名");
        when(service.findById(1L)).thenReturn(own);when(service.update(any())).thenReturn(1);
        assertEquals(200,controller.updateAnnouncement(1L,row(1L,999L,"ADMIN")).getCode());
        when(service.listForViewer(null,null,null,null,"9","TEACHER")).thenReturn(List.of(row(2L,10L,"TEACHER"),own,row(3L,9L,"TEACHER")));
        var page=(PageResult<Announcement>)controller.getAnnouncementsByPage(1,1,null,null,null,null,true).getData();
        assertEquals(2L,page.getTotal());assertEquals(List.of(1L),page.getRows().stream().map(Announcement::getId).toList());
    }
    @Test void createIgnoresForgedOwnerAndLegacyRowsRemainAdminManageable() {
        controller.addAnnouncement(Map.of("title","新公告","content","内容","publisherUserId",999L,"publisherUserType","ADMIN","targetType","STUDENT"));
        verify(service).insert(argThat(a->Long.valueOf(9).equals(a.getPublisherUserId())&&"TEACHER".equals(a.getPublisherUserType())));
        var actor=CurrentHolder.getUser();actor.setRole("ROLE_ADMIN");
        when(service.findById(2L)).thenReturn(row(2L,null,null));when(service.delete(2L)).thenReturn(1);
        assertEquals(200,controller.deleteAnnouncement(2L).getCode());
    }
}
