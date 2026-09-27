package com.internship.service;

import com.internship.entity.CompanyAlumniMember;
import com.internship.entity.CompanyUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyAlumniMemberMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CompanyAlumniServiceTest {
    @Mock
    private CompanyAlumniMemberMapper alumniMemberMapper;

    @Test
    void alumniUnitRequiresAtLeastOneMember() {
        CompanyAlumniService service = new CompanyAlumniService(alumniMemberMapper);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.normalize(1, List.of()));

        assertEquals("校友单位至少需要填写一名校友", error.getMessage());
    }

    @Test
    void nonAlumniUnitAlwaysClearsMembersAndLegacyRepresentative() {
        CompanyAlumniService service = new CompanyAlumniService(alumniMemberMapper);
        CompanyAlumniMember ignored = member("张三", "13800000000", "经理");
        CompanyUser company = new CompanyUser();
        company.setIsAlumniUnit(0);
        company.setAlumniRepresentativeName("旧校友");

        List<CompanyAlumniMember> normalized = service.normalize(0, List.of(ignored));
        service.syncLegacyRepresentative(company, normalized);
        service.replace(10L, normalized);

        assertTrue(normalized.isEmpty());
        assertEquals(0, company.getIsAlumniUnit());
        assertEquals(null, company.getAlumniRepresentativeName());
        verify(alumniMemberMapper).deleteByCompanyId(10L);
        verify(alumniMemberMapper, never()).batchInsert(org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void validMembersAreTrimmedAndFirstMemberMirrorsLegacyFields() {
        CompanyAlumniService service = new CompanyAlumniService(alumniMemberMapper);
        CompanyUser company = new CompanyUser();
        company.setIsAlumniUnit(1);

        List<CompanyAlumniMember> normalized = service.normalize(1,
                List.of(member(" 张三 ", " 13800000000 ", " 技术经理 ")));
        service.syncLegacyRepresentative(company, normalized);
        service.replace(10L, normalized);

        assertEquals("张三", company.getAlumniRepresentativeName());
        assertEquals("13800000000", company.getAlumniRepresentativePhone());
        assertEquals("技术经理", company.getAlumniRepresentativePosition());
        ArgumentCaptor<List<CompanyAlumniMember>> captor = ArgumentCaptor.forClass(List.class);
        verify(alumniMemberMapper).batchInsert(captor.capture());
        assertEquals(10L, captor.getValue().get(0).getCompanyId());
    }

    private CompanyAlumniMember member(String name, String phone, String position) {
        CompanyAlumniMember member = new CompanyAlumniMember();
        member.setName(name);
        member.setPhone(phone);
        member.setPosition(position);
        return member;
    }
}
