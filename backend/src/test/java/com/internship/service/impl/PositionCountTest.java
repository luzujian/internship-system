package com.internship.service.impl;

import com.internship.exception.BusinessException;
import com.internship.mapper.PositionMapper;
import com.internship.service.StudentInternshipStatusService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PositionCountTest {
    private final PositionMapper mapper = mock(PositionMapper.class);
    private final PositionServiceImpl service =
            new PositionServiceImpl(mapper, mock(StudentInternshipStatusService.class));

    @Test
    void countsPositionsWithoutLoadingPositionsOrRecruitmentDetails() {
        when(mapper.countByCompanyId(20L)).thenReturn(700L);

        assertEquals(700L, service.countByCompanyId(20L));

        verify(mapper).countByCompanyId(20L);
        verify(mapper, never()).findByCompanyId(any());
    }

    @Test
    void returnsZeroForCompanyWithoutPositions() {
        when(mapper.countByCompanyId(20L)).thenReturn(0L);
        assertEquals(0L, service.countByCompanyId(20L));
    }

    @Test
    void rejectsInvalidCompanyBeforeQuerying() {
        for (Long companyId : new Long[]{null, 0L, -1L}) {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.countByCompanyId(companyId));
            assertEquals("企业ID无效", error.getMessage());
        }
        verifyNoInteractions(mapper);
    }
}
