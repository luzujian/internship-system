package com.internship.controller;

import com.internship.entity.Position;
import com.internship.entity.Result;
import com.internship.entity.dto.PositionVO;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.service.CompanyRecommendationRatingService;
import com.internship.service.PositionCacheService;
import com.internship.service.PositionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StudentPositionFiltersTest {
    private final StudentPositionController controller = new StudentPositionController();
    private final PositionCacheService cache = mock(PositionCacheService.class);
    private final PositionService positions = mock(PositionService.class);

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        StudentJobApplicationMapper applications = mock(StudentJobApplicationMapper.class);
        CompanyRecommendationRatingService ratings = mock(CompanyRecommendationRatingService.class);
        when(applications.countByPositionIds(anyList())).thenReturn(List.of());
        when(ratings.findSubmittedSummaries(any())).thenReturn(Map.of());
        when(cache.getCachedRegions()).thenReturn(null);
        ReflectionTestUtils.setField(controller, "positionCacheService", cache);
        ReflectionTestUtils.setField(controller, "positionService", positions);
        ReflectionTestUtils.setField(controller, "studentJobApplicationMapper", applications);
        ReflectionTestUtils.setField(controller, "companyRecommendationRatingService", ratings);
    }

    @ParameterizedTest
    @ValueSource(strings = {"all", "latest", "hot", "recommended"})
    void everyModeIncludesAllRecruitingPositionsAndKeepsTheEndDate(String mode) {
        List<PositionVO> data = new ArrayList<>(IntStream.rangeClosed(1, 12)
                .mapToObj(this::position).toList());
        PositionVO paused = position(90);
        paused.setStatus("paused");
        PositionVO expired = position(91);
        expired.setInternshipEndDate(Date.valueOf(LocalDate.now().minusDays(1)));
        data.add(paused);
        data.add(expired);
        data.getFirst().setInternshipEndDate(Date.valueOf(LocalDate.now()));
        when(cache.getCachedPositions()).thenReturn(data);

        Result result = switch (mode) {
            case "latest" -> controller.getLatest();
            case "hot" -> controller.getHot();
            case "recommended" -> controller.getRecommended();
            default -> controller.getAll();
        };

        assertEquals(200, result.getCode());
        List<PositionVO> returned = (List<PositionVO>) result.getData();
        assertEquals(12, returned.size());
        assertTrue(returned.stream().anyMatch(p -> p.getId().equals(1L)));
        assertFalse(returned.stream().anyMatch(p -> p.getId() >= 90));
    }

    @Test
    void latestAndHotHaveStableOrderingWithMissingAndTiedValues() {
        PositionVO first = position(1);
        PositionVO second = position(2);
        PositionVO third = position(3);
        PositionVO missing = position(4);
        second.setPublishTime(first.getPublishTime());
        third.setViewCount(100);
        missing.setPublishTime(null);
        missing.setViewCount(null);
        when(cache.getCachedPositions()).thenReturn(new ArrayList<>(List.of(first, missing, second, third)));

        assertEquals(List.of(3L, 2L, 1L, 4L), ids(controller.getLatest()));
        assertEquals(List.of(3L, 2L, 1L, 4L), ids(controller.getHot()));
    }

    @Test
    void regionsContainProvinceCityDistrictAndOnlyRecruitingLocations() {
        when(positions.findAll()).thenReturn(List.of(
                location("广东省", "广州市", "天河区", "active"),
                location("广东省", "广州市", "黄埔区", "active"),
                location("广东省", "深圳市", null, "active"),
                location("广东省", "广州市", "天河区", "active"),
                location("北京市", null, null, "active"),
                location("浙江省", "杭州市", "西湖区", "paused")));

        Result result = controller.getRegionOptions();

        assertEquals(200, result.getCode());
        List<Map<String, Object>> regions = (List<Map<String, Object>>) result.getData();
        assertEquals(2, regions.size());
        Map<String, Object> guangdong = regions.getFirst();
        List<Map<String, Object>> cities = (List<Map<String, Object>>) guangdong.get("children");
        assertEquals(List.of("广州市", "深圳市"), cities.stream().map(c -> c.get("value")).toList());
        List<Map<String, Object>> districts = (List<Map<String, Object>>) cities.getFirst().get("children");
        assertEquals(List.of("天河区", "黄埔区"), districts.stream().map(c -> c.get("value")).toList());
        assertFalse(cities.get(1).containsKey("children"));
        assertFalse(regions.get(1).containsKey("children"));
    }

    private List<Long> ids(Result result) {
        assertEquals(200, result.getCode());
        return ((List<PositionVO>) result.getData()).stream().map(PositionVO::getId).toList();
    }

    private PositionVO position(int id) {
        PositionVO position = new PositionVO();
        position.setId((long) id);
        position.setCompanyId((long) id);
        position.setStatus("active");
        position.setPublishTime(new java.util.Date(id * 1_000L));
        position.setViewCount(1);
        return position;
    }

    private Position location(String province, String city, String district, String status) {
        Position position = new Position();
        position.setProvince(province);
        position.setCity(city);
        position.setDistrict(district);
        position.setStatus(status);
        return position;
    }
}
