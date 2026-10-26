package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CompanySmartImportCreateOnlyTest {
    @Test void companyAppearingAfterPreviewCannotHaveProfileOrContactsOverwritten() {
        var mapper = mock(CompanyUserMapper.class); var contacts = mock(CompanyContactService.class);
        var service = new CompanyUserServiceImpl(mapper,mock(PasswordEncoder.class),mock(CompanyAlumniService.class));
        ReflectionTestUtils.setField(service,"companyContactService",contacts);
        CompanyUser original = new CompanyUser(); original.setId(77L); original.setCompanyName("测试企业"); original.setAddress("原地址");
        when(mapper.findImportMatches("测试企业")).thenReturn(List.of(original));
        var result = service.importFromExcel(List.of(new LinkedHashMap<>(Map.of("企业名称","测试企业","企业地址","新地址","_createOnly",true))));
        assertEquals(1,result.get("failCount")); assertEquals(0,result.get("successCount")); assertEquals("原地址",original.getAddress());
        verify(mapper,never()).updateImportedProfile(any()); verify(mapper,never()).insert(any()); verify(contacts,never()).mergeImported(anyLong(),any(),anyBoolean());
    }
}
