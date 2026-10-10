package com.internship.service;

import com.internship.entity.InternshipApplicationEntity;
import com.internship.entity.StudentJobApplication;
import com.internship.exception.BusinessException;
import com.internship.mapper.InternshipApplicationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyApplicationActivityServiceTest {
    private final InternshipApplicationMapper mapper = mock(InternshipApplicationMapper.class);
    private final StudentJobApplicationService jobs = mock(StudentJobApplicationService.class);
    private final CompanyApplicationActivityService service = new CompanyApplicationActivityService(mapper, jobs);

    @Test void pageUsesGlobalUnreadCountAndBusinessUpdateTime() {
        InternshipApplicationEntity row = snapshot(31, 1000, "pending");
        row.setViewed(true); row.setUpdateTime(new Date(2000)); row.setViewTime(new Date(9000));
        when(mapper.findActivityPage(7L, false, 0, 10)).thenReturn(List.of(row));
        when(mapper.countActivities(7L, false)).thenReturn(12L);
        when(mapper.countActivities(7L, true)).thenReturn(2L);
        var feed = service.list(7L, 0, 10, false);
        assertEquals(2, feed.unreadCount()); assertEquals(12, feed.total()); assertTrue(feed.hasMore());
        assertEquals(new Date(2000), feed.items().getFirst().activityTime());
    }

    @Test void unreadFilterAndPaginationStayInCompanyScope() {
        when(mapper.findActivityPage(7L, true, 10, 10)).thenReturn(List.of(snapshot(31, 1000, "withdrawn")));
        when(mapper.countActivities(7L, true)).thenReturn(11L);
        var feed = service.list(7L, 10, 10, true);
        assertEquals(11, feed.total()); assertFalse(feed.hasMore());
        verify(mapper, never()).countActivities(7L, false);
        assertThrows(BusinessException.class, () -> service.list(7L, -1, 10, false));
        assertThrows(BusinessException.class, () -> service.list(7L, 0, 51, false));
    }

    @Test void cannotReadOrMarkAnotherCompanyRecord() {
        var row = snapshot(31, 1000, "pending"); row.setCompanyId(8L);
        when(mapper.findById(31L)).thenReturn(row);
        assertThrows(AccessDeniedException.class, () -> service.detail(7L, 31L));
        assertThrows(AccessDeniedException.class, () -> service.read(7L, 31L, "pending", 1000L));
        verify(mapper, never()).readActivityVersion(any(), any(), any(), any());
        verifyNoInteractions(jobs);
    }

    @Test void cannotResolveAnotherCompanyJobApplication() {
        var job = job(91, 1000, "pending"); job.setCompanyId(8L);
        when(jobs.findById(91L)).thenReturn(job);
        assertThrows(AccessDeniedException.class, () -> service.forApplication(7L, 91L));
        verifyNoInteractions(mapper);
    }

    @Test void staleStatusOrVersionNeverClearsNewUnreadUpdate() {
        var row = snapshot(31, 1000, "withdrawn");
        when(mapper.findById(31L)).thenReturn(row);
        assertThrows(BusinessException.class, () -> service.read(7L, 31L, "pending", 1000L));
        assertThrows(BusinessException.class, () -> service.read(7L, 31L, "withdrawn", 999L));
        verify(mapper, never()).readActivityVersion(any(), any(), any(), any());
    }

    @Test void readIsConditionalAndConcurrentStateChangeIsReported() {
        var row = snapshot(31, 1000, "pending");
        when(mapper.findById(31L)).thenReturn(row);
        assertThrows(BusinessException.class, () -> service.read(7L, 31L, "pending", 1000L));
        when(mapper.readActivityVersion(31L, 7L, "pending", new Date(1000))).thenReturn(1);
        assertDoesNotThrow(() -> service.read(7L, 31L, "pending", 1000L));
        row.setViewed(true);
        assertDoesNotThrow(() -> service.read(7L, 31L, "pending", 1000L));
        verify(mapper, times(2)).readActivityVersion(31L, 7L, "pending", new Date(1000));
    }

    @Test void distinctTableIdsAndRepeatedApplicationsResolveTheExactAttempt() {
        var first = snapshot(31, 1000, "rejected"); var second = snapshot(32, 9000, "pending");
        var oldJob = job(91, 1000, "rejected"); var newJob = job(92, 9000, "pending");
        when(mapper.findById(31L)).thenReturn(first);
        when(mapper.findActivityAttempts(7L, 5L, 3L)).thenReturn(List.of(second, first));
        when(jobs.findByCompanyId(7L)).thenReturn(List.of(newJob, oldJob));
        when(jobs.findById(92L)).thenReturn(newJob);
        assertEquals(91L, service.detail(7L, 31L).application().getId());
        assertEquals(32L, service.forApplication(7L, 92L).id());
    }

    @Test void withdrawnSnapshotDoesNotOpenNewApplicationEvenInTheSameSecond() {
        var withdrawn = snapshot(31, 1000, "withdrawn"); var retry = snapshot(32, 1000, "pending");
        var newJob = job(92, 1000, "pending");
        when(mapper.findById(31L)).thenReturn(withdrawn);
        when(mapper.findActivityAttempts(7L, 5L, 3L)).thenReturn(List.of(retry, withdrawn));
        when(jobs.findByCompanyId(7L)).thenReturn(List.of(newJob));
        assertNull(service.detail(7L, 31L).application());
        assertEquals("withdrawn", service.detail(7L, 31L).activity().status());
    }

    @Test void ambiguousHistoricalTimestampsNeverMarkAnUnrelatedAttempt() {
        var first = snapshot(31, 1000, "rejected"); var second = snapshot(32, 1000, "rejected");
        var firstJob = job(91, 1000, "rejected"); var secondJob = job(92, 1000, "rejected");
        when(mapper.findById(31L)).thenReturn(first);
        when(mapper.findActivityAttempts(7L, 5L, 3L)).thenReturn(List.of(second, first));
        when(jobs.findByCompanyId(7L)).thenReturn(List.of(firstJob, secondJob));
        when(jobs.findById(91L)).thenReturn(firstJob);
        assertNull(service.detail(7L, 31L).application());
        assertNull(service.forApplication(7L, 91L));
    }

    @Test void oldSingleAttemptCanBeLinkedWithoutGuessingAcrossRetries() {
        var legacy = snapshot(31, 2000, "pending"); var job = job(91, 1000, "pending");
        when(mapper.findActivityAttempts(7L, 5L, 3L)).thenReturn(List.of(legacy));
        when(jobs.findByCompanyId(7L)).thenReturn(List.of(job));
        when(jobs.findById(91L)).thenReturn(job);
        assertEquals(31L, service.forApplication(7L, 91L).id());
    }

    @Test void dateOnlyApplyDateDoesNotConfuseSameDayRetryAttempts() {
        var first = snapshot(31, 36_000_000, "rejected");
        var second = snapshot(32, 54_000_000, "pending");
        var oldJob = job(91, 0, "rejected"); oldJob.setCreateTime(new Date(36_000_000));
        var newJob = job(92, 0, "pending"); newJob.setCreateTime(new Date(54_000_000));
        when(mapper.findActivityAttempts(7L, 5L, 3L)).thenReturn(List.of(second, first));
        when(jobs.findByCompanyId(7L)).thenReturn(List.of(newJob, oldJob));
        when(jobs.findById(92L)).thenReturn(newJob);
        when(mapper.findById(31L)).thenReturn(first);
        assertEquals(32L, service.forApplication(7L, 92L).id());
        assertEquals(91L, service.detail(7L, 31L).application().getId());
    }

    private InternshipApplicationEntity snapshot(long id, long time, String status) {
        var row = new InternshipApplicationEntity();
        row.setId(id); row.setCompanyId(7L); row.setStudentId(5L); row.setPositionId(3L);
        row.setApplyTime(new Date(time)); row.setUpdateTime(new Date(time)); row.setStatus(status); row.setViewed(false);
        return row;
    }
    private StudentJobApplication job(long id, long time, String status) {
        var row = new StudentJobApplication();
        row.setId(id); row.setCompanyId(7L); row.setStudentId(5L); row.setPositionId(3L);
        row.setApplyDate(new Date(time)); row.setStatus(status);
        return row;
    }
}
