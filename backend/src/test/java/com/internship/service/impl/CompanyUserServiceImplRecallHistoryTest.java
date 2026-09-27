package com.internship.service.impl;

import com.internship.entity.CompanyUser;
import com.internship.mapper.CompanyUserMapper;
import com.internship.service.CompanyAlumniService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompanyUserServiceImplRecallHistoryTest {
    @Test
    void resubmissionKeepsCompletedRecallHistoryWhileResettingCurrentState() {
        CompanyUserMapper mapper = mock(CompanyUserMapper.class);
        CompanyUser existing = new CompanyUser();
        existing.setId(7L);
        existing.setAuditStatus(3);
        existing.setRecallStatus(2);
        existing.setRecallReason("联系人姓名填写错误");
        Date applyTime = new Date(1700000000000L);
        Date auditTime = new Date(1700000060000L);
        existing.setRecallApplyTime(applyTime);
        existing.setRecallAuditTime(auditTime);
        existing.setRecallAuditRemark("自动允许撤回修改");

        CompanyUser update = new CompanyUser();
        update.setId(7L);
        update.setAuditStatus(0);
        update.setRecallStatus(0);

        when(mapper.findById(7L)).thenReturn(existing);
        when(mapper.update(existing)).thenReturn(1);

        CompanyUserServiceImpl service = new CompanyUserServiceImpl(
                mapper, mock(PasswordEncoder.class), mock(CompanyAlumniService.class));
        assertEquals(1, service.update(update));

        assertEquals(0, existing.getRecallStatus());
        assertEquals("联系人姓名填写错误", existing.getRecallReason());
        assertSame(applyTime, existing.getRecallApplyTime());
        assertSame(auditTime, existing.getRecallAuditTime());
        assertEquals("自动允许撤回修改", existing.getRecallAuditRemark());
        verify(mapper).update(existing);
    }
}
