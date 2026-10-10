package com.internship.controller;

import com.internship.entity.CompanyUser;
import com.internship.entity.Position;
import com.internship.entity.dto.PositionVO;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentJobApplicationMapper;
import com.internship.service.CompanyRecommendationRatingService;
import com.internship.service.PositionService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

class StudentPositionCooperationModeTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"mutual_choice", "student_contact", "自主", "双向,兜底"})
    void detailUsesSchoolCooperationForEverySystemCompany(String storedMode) {
        SecurityContextHolder.clearContext();
        var controller = new StudentPositionController();
        var positions = mock(PositionService.class);
        var companies = mock(CompanyUserMapper.class);
        var applications = mock(StudentJobApplicationMapper.class);
        var ratings = mock(CompanyRecommendationRatingService.class);
        ReflectionTestUtils.setField(controller, "positionService", positions);
        ReflectionTestUtils.setField(controller, "companyUserMapper", companies);
        ReflectionTestUtils.setField(controller, "studentJobApplicationMapper", applications);
        ReflectionTestUtils.setField(controller, "companyRecommendationRatingService", ratings);

        var position = new Position();
        position.setId(10L); position.setCompanyId(20L); position.setStatus("active");
        var company = new CompanyUser();
        company.setId(20L); company.setCompanyName("合作企业"); company.setCooperationMode(storedMode);
        company.setCompanyTag("学生自主联系,实习基地,接受兜底");
        when(positions.findById(10L)).thenReturn(position);
        when(companies.findById(20L)).thenReturn(company);
        when(applications.countByPositionIds(anyList())).thenReturn(List.of());
        when(ratings.findSubmittedSummaries(any())).thenReturn(Map.of());

        var result = controller.getDetail(10L);
        assertEquals(200, result.getCode());
        var detail = (PositionVO) result.getData();
        assertEquals("mutual_choice", detail.getCooperationMode());
        assertEquals(List.of("实习基地", "接受兜底"), detail.getTags());
        assertEquals(storedMode, company.getCooperationMode());
        verify(companies, never()).update(any());
    }
}
