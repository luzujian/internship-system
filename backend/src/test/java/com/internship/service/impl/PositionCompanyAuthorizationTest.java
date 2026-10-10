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
    @org.junit.jupiter.api.BeforeEach
    void contactDependency() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "companyContactService", mock(com.internship.service.CompanyContactService.class));
    }
    private final PositionMapper mapper = mock(PositionMapper.class);
    private final PositionServiceImpl service = new PositionServiceImpl(mapper, mock(StudentInternshipStatusService.class));

    @Test
    void mixedCompanyBatchIsRejectedBeforeAnyDeletion() {
        Position own = new Position(); own.setCompanyId(20L);
        Position foreign = new Position(); foreign.setCompanyId(30L);
        when(mapper.findByIdForUpdate(10L)).thenReturn(own);
        when(mapper.findByIdForUpdate(11L)).thenReturn(foreign);
        assertThrows(AccessDeniedException.class, () -> service.batchDeleteForCompany(java.util.List.of(10L, 11L), 20L));
        verify(mapper, never()).batchDeleteByIds(any());
        verify(mapper, never()).deleteById(any());
    }

    @Test
    void ownBatchAllowsDuplicateIdsAndRejectsMissingIdentityOrRows() {
        Position own = new Position(); own.setCompanyId(20L);
        when(mapper.findByIdForUpdate(10L)).thenReturn(own);
        when(mapper.batchDeleteByIds(java.util.List.of(10L))).thenReturn(1);
        assertEquals(1, service.batchDeleteForCompany(java.util.List.of(10L, 10L), 20L));
        assertThrows(AccessDeniedException.class, () -> service.batchDeleteForCompany(java.util.List.of(10L), null));
        assertThrows(AccessDeniedException.class, () -> service.batchDeleteForCompany(java.util.List.of(12L), 20L));
        assertThrows(AccessDeniedException.class, () -> service.batchDeleteForCompany(java.util.Arrays.asList(10L, null), 20L));
        assertThrows(AccessDeniedException.class, () -> service.batchDeleteForCompany(java.util.List.of(), 20L));
        verify(mapper).batchDeleteByIds(java.util.List.of(10L));
    }

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
