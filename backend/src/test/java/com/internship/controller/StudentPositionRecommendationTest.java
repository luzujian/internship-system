package com.internship.controller;

import com.internship.entity.Result;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.dto.CompanyRatingSummary;
import com.internship.entity.dto.PositionVO;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.service.CompanyRecommendationRatingService;
import com.internship.service.PositionCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StudentPositionRecommendationTest {

    @Test
    void appliedMarkerUsesOnlyLatestRecordPerPosition() {
        List<StudentJobApplication> newestFirst = List.of(
                application(5L, 1L, "interview_failed"),
                application(4L, 1L, "interview_passed"),
                application(3L, 2L, "pending_interview"),
                application(2L, 3L, null));

        assertEquals(Set.of(2L, 3L),
                StudentPositionController.latestBlockingPositionIds(newestFirst));
    }

    @Test
    void recommendedEndpointReturnsEveryActivePositionWithoutTopTenLimit() {
        SecurityContextHolder.clearContext();
        StudentPositionController controller = new StudentPositionController();
        PositionCacheService cacheService = mock(PositionCacheService.class);
        StudentJobApplicationMapper applicationMapper = mock(StudentJobApplicationMapper.class);
        CompanyRecommendationRatingService ratingService = mock(CompanyRecommendationRatingService.class);
        List<PositionVO> cached = new ArrayList<>(IntStream.rangeClosed(1, 12)
                .mapToObj(index -> activePosition((long) index))
                .toList());
        PositionVO paused = activePosition(99L);
        paused.setStatus("paused");
        cached.add(paused);
        when(cacheService.getCachedPositions()).thenReturn(cached);
        when(applicationMapper.countByPositionIds(anyList())).thenReturn(List.of());
        when(ratingService.findSubmittedSummaries(any())).thenReturn(Map.of());
        ReflectionTestUtils.setField(controller, "positionCacheService", cacheService);
        ReflectionTestUtils.setField(controller, "studentJobApplicationMapper", applicationMapper);
        ReflectionTestUtils.setField(controller, "companyRecommendationRatingService", ratingService);

        Result result = controller.getRecommended();

        assertEquals(200, result.getCode());
        List<PositionVO> returned = (List<PositionVO>) result.getData();
        assertEquals(12, returned.size());
        assertEquals(false, returned.stream().anyMatch(position -> position.getId().equals(99L)));
    }

    @Test
    void recommendedOrderUsesRealAverageThenCountTimeAndIdAndPlacesUnratedLast() {
        PositionVO lowerAverage = position(102L, 2L, 3_000L);
        PositionVO higherAverageFewVotes = position(101L, 1L, 1_000L);
        PositionVO higherAverageMoreVotes = position(103L, 3L, 500L);
        PositionVO unratedLowerId = position(104L, 4L, 2_000L);
        PositionVO unratedHigherId = position(105L, 5L, 2_000L);
        List<PositionVO> positions = new ArrayList<>(List.of(
                unratedLowerId,
                lowerAverage,
                higherAverageFewVotes,
                unratedHigherId,
                higherAverageMoreVotes));
        Map<Long, CompanyRatingSummary> summaries = Map.of(
                1L, summary(1L, "4.84", 1),
                2L, summary(2L, "4.76", 100),
                3L, summary(3L, "4.84", 10));

        positions.sort(StudentPositionController.recommendedComparator(summaries));

        assertEquals(List.of(103L, 101L, 102L, 105L, 104L),
                positions.stream().map(PositionVO::getId).toList());
    }

    private PositionVO position(Long id, Long companyId, long publishTime) {
        PositionVO position = new PositionVO();
        position.setId(id);
        position.setCompanyId(companyId);
        position.setPublishTime(new Date(publishTime));
        return position;
    }

    private PositionVO activePosition(Long id) {
        PositionVO position = position(id, id, id * 1_000L);
        position.setStatus("active");
        return position;
    }

    private CompanyRatingSummary summary(Long companyId, String average, int count) {
        CompanyRatingSummary summary = new CompanyRatingSummary();
        summary.setCompanyId(companyId);
        summary.setAverageStars(new BigDecimal(average));
        summary.setRatingCount(count);
        return summary;
    }

    private StudentJobApplication application(Long id, Long positionId, String status) {
        StudentJobApplication application = new StudentJobApplication();
        application.setId(id);
        application.setPositionId(positionId);
        application.setStatus(status);
        return application;
    }
}
