package com.internship.service;

import com.github.pagehelper.PageHelper;
import com.internship.dto.TeacherCompanyDTO;
import com.internship.dto.TeacherRecruitmentPositionDTO;
import com.internship.exception.BusinessException;
import com.internship.mapper.TeacherRecruitmentMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherRecruitmentServiceTest {
    private final TeacherRecruitmentMapper mapper = mock(TeacherRecruitmentMapper.class);
    private final CompanyRelationsService companies = mock(CompanyRelationsService.class);
    private final TeacherRecruitmentService service = new TeacherRecruitmentService(mapper, companies);

    @AfterEach void clearPage() { PageHelper.clearPage(); }

    @Test void searchKeepsPublishedPausedPositionsAndPassesOnlyCatalogueFilters() {
        TeacherRecruitmentPositionDTO position = new TeacherRecruitmentPositionDTO();
        position.setStatus("paused");
        when(mapper.findPublishedPositions("企业", "研发", "paused", 4L)).thenReturn(List.of(position));
        var result = service.findPage(1, 10, " 企业 ", " 研发 ", " paused ", 4L);
        assertEquals(1, result.getTotal());
        assertEquals("paused", result.getRows().getFirst().getStatus());
        verify(mapper).findPublishedPositions("企业", "研发", "paused", 4L);
        verifyNoInteractions(companies);
    }

    @Test void blankSearchReturnsAllPublishedPositions() {
        when(mapper.findPublishedPositions(null, null, null, null)).thenReturn(List.of());
        assertEquals(0, service.findPage(1, 10, " ", "", null, null).getTotal());
        verify(mapper).findPublishedPositions(null, null, null, null);
    }

    @Test void draftFilterIsRejectedBeforeReadingData() {
        assertThrows(BusinessException.class, () -> service.findPage(1, 10, null, null, "draft", 4L));
        verifyNoInteractions(mapper, companies);
    }

    @Test void detailUsesTheOwningCompanyAndPreservesItsUploadedMaterials() {
        TeacherRecruitmentPositionDTO position = new TeacherRecruitmentPositionDTO();
        position.setCompanyId(7L);
        TeacherCompanyDTO company = new TeacherCompanyDTO();
        company.setPhotos("[\"https://example.com/photo.jpg\"]");
        when(mapper.findPublishedPositionById(19L)).thenReturn(position);
        when(companies.findCompanyDetail(7L)).thenReturn(company);
        var detail = service.findDetail(19L);
        assertSame(position, detail.position());
        assertSame(company, detail.company());
        verify(companies).findCompanyDetail(7L);
    }

    @Test void unpublishedOrMissingPositionDoesNotExposeCompanyData() {
        assertThrows(BusinessException.class, () -> service.findDetail(19L));
        verifyNoInteractions(companies);
    }
}
