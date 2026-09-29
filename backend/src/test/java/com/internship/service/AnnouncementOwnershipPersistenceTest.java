package com.internship.service;
import com.internship.entity.*;
import com.internship.mapper.AnnouncementMapper;
import com.internship.service.impl.AnnouncementServiceImpl;
import com.internship.utils.CurrentHolder;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AnnouncementOwnershipPersistenceTest {
    @AfterEach void clear(){CurrentHolder.remove();}
    private Announcement row(){var a=new Announcement();a.setId(1L);a.setTitle("公告测试");a.setContent("内容");a.setPublisher("显示姓名");a.setStatus("DRAFT");a.setTargetType("STUDENT");return a;}
    @Test void serviceBindsCreatorAndUpdatePreservesExistingOwner() {
        var actor=new User();actor.setId(9L);actor.setRole("ROLE_TEACHER_COLLEGE");CurrentHolder.setUser(actor);
        var mapper=mock(AnnouncementMapper.class);var service=new AnnouncementServiceImpl(mapper);var a=row();a.setPublisherUserId(666L);a.setPublisherUserType("ADMIN");
        service.insert(a);assertEquals(9L,a.getPublisherUserId());assertEquals("TEACHER",a.getPublisherUserType());
        when(mapper.findById(1L)).thenReturn(a);var edit=row();edit.setPublisherUserId(10L);edit.setPublisherUserType("ADMIN");
        service.update(edit);assertEquals(9L,edit.getPublisherUserId());assertEquals("TEACHER",edit.getPublisherUserType());
    }
    @Test void mapperBindsOwnerOnInsertButHasNoOwnershipUpdateAssignment() throws Exception {
        var configuration=new org.apache.ibatis.session.Configuration();
        try(var input=getClass().getClassLoader().getResourceAsStream("com/internship/mapper/AnnouncementMapper.xml")) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input,configuration,"AnnouncementMapper.xml",configuration.getSqlFragments()).parse();
        }
        var a=row();a.setPublisherUserId(9L);a.setPublisherUserType("TEACHER");
        var insert=configuration.getMappedStatement("com.internship.mapper.AnnouncementMapper.insert").getBoundSql(a);
        assertTrue(insert.getSql().contains("publisher_user_id"));assertTrue(insert.getParameterMappings().stream().anyMatch(p->p.getProperty().equals("publisherUserType")));
        var update=configuration.getMappedStatement("com.internship.mapper.AnnouncementMapper.update").getBoundSql(a);
        assertFalse(update.getSql().contains("publisher_user_id"));assertFalse(update.getSql().contains("publisher_user_type"));
    }
}
