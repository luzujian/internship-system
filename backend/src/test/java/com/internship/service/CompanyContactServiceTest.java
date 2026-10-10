package com.internship.service;

import com.internship.entity.CompanyContact;
import com.internship.entity.Position;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyContactMapper;
import com.internship.mapper.PositionMapper;
import com.internship.service.impl.PositionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanyContactServiceTest {
    private final CompanyContactMapper mapper = mock(CompanyContactMapper.class);
    private final PositionCacheService cache = mock(PositionCacheService.class);
    private final CompanyContactService service = new CompanyContactService(mapper, cache);
    @BeforeEach void setup() { when(mapper.lockCompany(100L)).thenReturn(100L); }
    private CompanyContact contact(String name, String phone, String email) {
        CompanyContact contact = new CompanyContact(); contact.setName(name); contact.setPhone(phone); contact.setEmail(email); return contact;
    }
    @Test void preventsForeignContactsAndDeletionOfUsedContacts() {
        assertThrows(BusinessException.class, () -> service.save(100L, 99L, contact("张老师", "13800000000", "")));
        verify(mapper, never()).update(any());
        when(mapper.findOwned(10L, 100L)).thenReturn(contact("张老师", "13800000000", ""));
        when(mapper.countPositions(10L)).thenReturn(2L);
        assertThrows(BusinessException.class, () -> service.delete(100L, 10L));
        verify(mapper, never()).delete(any(), any());
        when(mapper.countPositions(10L)).thenReturn(0L);
        service.delete(100L, 10L);
        verify(mapper).delete(10L, 100L);
    }
    @Test void normalizesOptionalEmailAndDeduplicatesImportWithoutErasingExistingEmail() {
        CompanyContact existing = contact("张老师", "13800000000", "one@example.test"); existing.setId(10L);
        when(mapper.findByCompanyId(100L)).thenReturn(List.of(existing));
        CompanyContact incoming = contact(" 张老师 ", "138 0000 0000", "");
        CompanyContact merged = service.mergeImported(100L, incoming);
        assertEquals(10L, merged.getId()); assertEquals("13800000000", merged.getPhone()); assertEquals("one@example.test", merged.getEmail());
        verify(mapper, never()).insert(any()); verify(mapper).update(incoming);
        assertThrows(BusinessException.class, () -> service.save(100L, null, incoming));
        assertThrows(BusinessException.class, () -> service.save(100L, null, contact("王老师", "x", "bad")));
        CompanyContact spacedEmail = contact("王老师", "13800000001", "hr @example.test");
        service.validateImported(spacedEmail);
        assertEquals("hr@example.test", spacedEmail.getEmail());
    }
    @Test void positionBindingValidatesOwnershipAndAllowsExplicitLegacyFallback() {
        PositionMapper positions = mock(PositionMapper.class);
        PositionServiceImpl positionService = new PositionServiceImpl(positions, mock(StudentInternshipStatusService.class));
        ReflectionTestUtils.setField(positionService, "companyContactService", service);
        Position existing = new Position(); existing.setId(1L); existing.setCompanyId(100L); existing.setContactId(10L);
        when(positions.findByIdForUpdate(1L)).thenReturn(existing);
        Position request = new Position(); request.setId(1L); request.setContactId(99L);
        assertThrows(BusinessException.class, () -> positionService.updateForCompany(request, 100L));
        verify(positions, never()).update(any());
        request.setContactId(null);
        positionService.updateForCompany(request, 100L);
        assertTrue(request.isContactSpecified()); assertNull(request.getContactId());
        verify(positions).update(request);
    }
}
