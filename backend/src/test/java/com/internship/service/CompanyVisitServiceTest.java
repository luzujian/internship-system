package com.internship.service;

import com.internship.dto.CompanyVisitRecordRequest;
import com.internship.entity.CompanyVisitRecord;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.CompanyVisitMapper;
import com.internship.mapper.TeacherUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyVisitServiceTest {
    @Mock
    private CompanyVisitMapper companyVisitMapper;
    @Mock
    private CompanyUserMapper companyUserMapper;
    @Mock
    private TeacherUserMapper teacherUserMapper;

    private CompanyVisitService service;

    @BeforeEach
    void setUp() {
        service = new CompanyVisitService(companyVisitMapper, companyUserMapper, teacherUserMapper);
    }

    @Test
    void ordinaryTeacherCannotUpdateAnotherTeachersRecord() {
        CompanyVisitRecord existing = visitRecord(1L, 99L);
        when(companyVisitMapper.findById(1L)).thenReturn(existing);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, request(), 20L));

        assertEquals("只能修改或删除本人填写的走访记录", error.getMessage());
    }

    @Test
    void departmentHeadCannotUpdateAnotherTeachersRecord() {
        CompanyVisitRecord existing = visitRecord(1L, 99L);
        when(companyVisitMapper.findById(1L)).thenReturn(existing);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.update(1L, request(), 20L));

        assertEquals("只能修改或删除本人填写的走访记录", error.getMessage());
        verify(companyVisitMapper, never()).updateRecord(existing);
    }

    @Test
    void deleteRemovesParticipantsBeforeRecord() {
        CompanyVisitRecord existing = visitRecord(1L, 20L);
        when(companyVisitMapper.findById(1L)).thenReturn(existing);

        service.delete(1L, 20L);

        InOrder order = inOrder(companyVisitMapper);
        order.verify(companyVisitMapper).deleteParticipantsByRecordId(1L);
        order.verify(companyVisitMapper).deleteRecord(1L);
    }

    private CompanyVisitRecordRequest request() {
        CompanyVisitRecordRequest request = new CompanyVisitRecordRequest();
        request.setCompanyId(10L);
        request.setVisitDate(LocalDate.of(2026, 7, 14));
        request.setParticipantTeacherIds(List.of(30L));
        request.setRemark("走访正常");
        return request;
    }

    private CompanyVisitRecord visitRecord(Long id, Long creatorId) {
        CompanyVisitRecord record = new CompanyVisitRecord();
        record.setId(id);
        record.setCreatorTeacherId(creatorId);
        return record;
    }

}
