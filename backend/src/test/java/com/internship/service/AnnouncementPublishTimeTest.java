package com.internship.service;

import com.internship.entity.Announcement;
import com.internship.exception.BusinessException;
import com.internship.mapper.AnnouncementMapper;
import com.internship.service.impl.AnnouncementServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnnouncementPublishTimeTest {
    @Mock private AnnouncementMapper mapper;
    private AnnouncementServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AnnouncementServiceImpl(mapper);
    }

    @Test
    void publishedAnnouncementUsesServerTimeAndNoExpiry() {
        Announcement announcement = announcement("PUBLISHED");
        announcement.setPublishTime(new Date(0));
        announcement.setValidFrom(new Date(0));
        announcement.setPriority("important");
        when(mapper.insert(announcement)).thenReturn(1);

        long before = System.currentTimeMillis();
        assertEquals(1, service.insert(announcement));
        long after = System.currentTimeMillis();

        assertEquals("normal", announcement.getPriority());
        assertNull(announcement.getValidTo());
        assertNotNull(announcement.getPublishTime());
        assertEquals(announcement.getPublishTime(), announcement.getValidFrom());
        assertTrue(announcement.getPublishTime().getTime() >= before);
        assertTrue(announcement.getPublishTime().getTime() <= after);
    }

    @Test
    void draftHasNoPublishTimeUntilItIsPublished() {
        Announcement announcement = announcement("DRAFT");
        announcement.setPublishTime(new Date(0));
        announcement.setPriority("important");
        when(mapper.insert(announcement)).thenReturn(1);

        service.insert(announcement);

        assertNull(announcement.getPublishTime());
        assertNull(announcement.getValidTo());
        assertEquals("normal", announcement.getPriority());
    }

    @Test
    void draftToPublishedTransitionStampsPublishTimeAtUpdate() {
        Announcement existing = announcement("DRAFT");
        existing.setPublishTime(null);
        existing.setPriority("normal");
        Announcement changed = announcement("PUBLISHED");
        changed.setPublishTime(new Date(0));
        changed.setValidFrom(new Date(0));
        changed.setPriority("important");
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.update(changed)).thenReturn(1);

        long before = System.currentTimeMillis();
        assertEquals(1, service.update(changed));
        long after = System.currentTimeMillis();

        assertEquals("normal", changed.getPriority());
        assertNull(changed.getValidTo());
        assertNotNull(changed.getPublishTime());
        assertEquals(changed.getPublishTime(), changed.getValidFrom());
        assertTrue(changed.getPublishTime().getTime() >= before);
        assertTrue(changed.getPublishTime().getTime() <= after);
    }

    @Test
    void editingPublishedAnnouncementDoesNotAcceptClientPublishTime() {
        Announcement existing = announcement("PUBLISHED");
        existing.setPublishTime(new Date(1_000L));
        existing.setValidFrom(new Date(1_000L));
        existing.setPriority("important");
        Announcement changed = announcement("PUBLISHED");
        changed.setPublishTime(new Date(0));
        changed.setValidFrom(new Date(0));
        changed.setPriority("important");
        when(mapper.findById(1L)).thenReturn(existing);
        when(mapper.update(changed)).thenReturn(1);

        service.update(changed);

        assertEquals(existing.getPublishTime(), changed.getPublishTime());
        assertEquals(existing.getValidFrom(), changed.getValidFrom());
        assertEquals("normal", changed.getPriority());
        assertNull(changed.getValidTo());
    }

    private Announcement announcement(String status) {
        Announcement announcement = new Announcement();
        announcement.setId(1L);
        announcement.setTitle("考试安排公告");
        announcement.setContent("请按安排参加考试。");
        announcement.setPublisher("管理员");
        announcement.setPublisherRole("ADMIN");
        announcement.setStatus(status);
        announcement.setTargetType("[\"STUDENT\"]");
        announcement.setValidTo(new Date(System.currentTimeMillis() + 86_400_000L));
        return announcement;
    }
}
