package com.internship.service.impl;

import com.internship.entity.Position;
import com.internship.mapper.PositionMapper;
import com.internship.service.StudentInternshipStatusService;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PositionCompanyAuthorizationTest {
    private final PositionMapper mapper = mock(PositionMapper.class);
    private final PositionServiceImpl service = new PositionServiceImpl(mapper, mock(StudentInternshipStatusService.class));

    @Test
    void anotherCompanyCannotUpdateOrDeleteAPosition() {
        Position existing = new Position();
        existing.setId(10L);
        existing.setCompanyId(20L);
        when(mapper.findByIdForUpdate(10L)).thenReturn(existing);

        Position request = new Position();
        request.setId(10L);
        request.setCompanyId(30L);
        assertThrows(AccessDeniedException.class, () -> service.updateForCompany(request, 30L));
        assertThrows(AccessDeniedException.class, () -> service.deleteForCompany(10L, 30L));

        verify(mapper, never()).update(any());
        verify(mapper, never()).deleteById(10L);
    }

    @Test
    void ownerCanUpdateButCannotTransferOwnershipThroughThePayload() {
        Position existing = new Position();
        existing.setId(10L);
        existing.setCompanyId(20L);
        when(mapper.findByIdForUpdate(10L)).thenReturn(existing);
        when(mapper.update(any())).thenReturn(1);

        Position request = new Position();
        request.setId(10L);
        request.setCompanyId(30L);
        request.setPositionName("test role");
        assertEquals(1, service.updateForCompany(request, 20L));
        assertNull(request.getCompanyId());
        verify(mapper).update(request);
    }
}
