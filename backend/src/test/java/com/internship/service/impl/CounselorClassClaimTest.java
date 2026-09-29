package com.internship.service.impl;

import com.internship.entity.Class;
import com.internship.entity.ClassCounselorRelation;
import com.internship.entity.TeacherUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CounselorClassClaimTest {
    final ClassMapper classes = mock(ClassMapper.class);
    final ClassCounselorRelationMapper relations = mock(ClassCounselorRelationMapper.class);
    final TeacherUserMapper teachers = mock(TeacherUserMapper.class);
    final ClassCounselorRelationServiceImpl service = new ClassCounselorRelationServiceImpl();
    final TeacherUser actor = new TeacherUser();

    CounselorClassClaimTest() {
        ReflectionTestUtils.setField(service, "classMapper", classes);
        ReflectionTestUtils.setField(service, "classCounselorRelationMapper", relations);
        ReflectionTestUtils.setField(service, "teacherUserMapper", teachers);
        actor.setId(49L); actor.setTeacherUserId("x003"); actor.setName("Teacher");
        actor.setTeacherType("COUNSELOR"); actor.setStatus(1); actor.setDepartmentId("5");
        when(teachers.findByIdForUpdate(49L)).thenReturn(actor);
        when(teachers.findById(49L)).thenReturn(actor);
    }

    Class available(long id) {
        Class item = new Class(); item.setId(id); item.setName("Class "+id); item.setDepartmentId(5L);
        when(classes.findByIdForUpdate(id)).thenReturn(item);
        return item;
    }

    @Test void claimUpdatesBothReferencesAndDeduplicatesInLockOrder() {
        available(11); available(12);
        assertEquals(2, service.claimClasses(49L, List.of(12L,11L,12L)));
        var order = inOrder(classes, relations);
        order.verify(classes).findByIdForUpdate(11L);
        order.verify(relations).findByClassIdForUpdate(11L);
        order.verify(classes).findByIdForUpdate(12L);
        order.verify(relations).findByClassIdForUpdate(12L);
        verify(relations, times(2)).insert(argThat(r -> r.getCounselorId().equals(49L)));
        verify(classes).updateResponsibleTeacher(11L,"x003");
        verify(classes).updateResponsibleTeacher(12L,"x003");
    }

    @Test void crossCollegeClassRejectsWholeBatchBeforeAnyWrite() {
        available(11); available(12).setDepartmentId(6L);
        assertThrows(AccessDeniedException.class, () -> service.claimClasses(49L,List.of(11L,12L)));
        verify(relations, never()).insert(any());
        verify(classes, never()).updateResponsibleTeacher(any(),any());
    }

    @Test void primaryTeacherOccupiesClassEvenWithoutRelation() {
        available(11).setTeacherId("someone");
        assertThrows(BusinessException.class, () -> service.claimClasses(49L,List.of(11L)));
        verify(relations, never()).insert(any());
    }

    @Test void relationshipOccupiesClassEvenWithoutPrimaryTeacher() {
        available(11);
        when(relations.findByClassIdForUpdate(11L)).thenReturn(List.of(new ClassCounselorRelation()));
        assertThrows(BusinessException.class, () -> service.claimClasses(49L,List.of(11L)));
        verify(relations, never()).insert(any());
    }

    @Test void staleCandidateCannotBeClaimedAfterAnotherCounselorTakesIt() {
        available(11).setTeacherId("new-owner");
        assertThrows(BusinessException.class, () -> service.claimClasses(49L,List.of(11L)));
        verify(classes).findByIdForUpdate(11L);
        verify(relations, never()).insert(any());
    }

    @Test void invalidIdentityScopeAndBatchAreRejected() {
        assertThrows(BusinessException.class, () -> service.claimClasses(49L,List.of()));
        assertThrows(BusinessException.class, () -> service.claimClasses(49L,List.of(-1L)));
        actor.setDepartmentId(null);
        assertThrows(BusinessException.class, () -> service.claimClasses(49L,List.of(11L)));
        actor.setDepartmentId("5"); actor.setStatus(0);
        assertThrows(AccessDeniedException.class, () -> service.claimClasses(49L,List.of(11L)));
        actor.setStatus(1); actor.setTeacherType("COLLEGE");
        assertThrows(AccessDeniedException.class, () -> service.claimClasses(49L,List.of(11L)));
        verify(relations, never()).insert(any());
    }

    @Test void candidateListUsesAuthenticatedCounselorCollege() {
        when(classes.findClaimableByDepartment(5L)).thenReturn(List.of());
        assertEquals(List.of(), service.findClaimableClasses(49L));
        verify(classes).findClaimableByDepartment(5L);
    }

    @Test void releaseClearsOnlyOwnPrimaryTeacher() {
        available(11).setTeacherId("x003");
        when(relations.deleteByClassAndCounselor(11L,49L)).thenReturn(1);
        assertEquals(1, service.unassignClassFromCounselor(49L,11L));
        verify(classes).updateResponsibleTeacher(11L,null);
        available(12).setTeacherId("other-teacher");
        when(relations.deleteByClassAndCounselor(12L,49L)).thenReturn(1);
        service.unassignClassFromCounselor(49L,12L);
        verify(classes, never()).updateResponsibleTeacher(eq(12L),any());
    }

    @Test void unrelatedAdminEditPreservesClaimedRelationships() {
        ClassServiceImpl admin = new ClassServiceImpl();
        ReflectionTestUtils.setField(admin,"classMapper",classes);
        ReflectionTestUtils.setField(admin,"classCounselorRelationMapper",relations);
        available(11).setTeacherId("x003");
        Class edit = new Class(); edit.setId(11L); edit.setName("Renamed");
        admin.update(edit);
        verifyNoInteractions(relations);
    }

    @Test void partialWriteFailureRollsBackTheTransaction() throws Exception {
        available(11); available(12);
        when(relations.insert(any())).thenReturn(1).thenThrow(new IllegalStateException("insert failed"));
        var connection = mock(java.sql.Connection.class);
        when(connection.getAutoCommit()).thenReturn(true);
        var source = mock(javax.sql.DataSource.class);
        when(source.getConnection()).thenReturn(connection);
        var transactions = new org.springframework.jdbc.datasource.DataSourceTransactionManager(source);
        var proxy = new org.springframework.aop.framework.ProxyFactory(service);
        proxy.addAdvice(new org.springframework.transaction.interceptor.TransactionInterceptor(transactions,
                new org.springframework.transaction.annotation.AnnotationTransactionAttributeSource()));
        var transactionalService = (com.internship.service.ClassCounselorRelationService) proxy.getProxy();
        assertThrows(IllegalStateException.class, () -> transactionalService.claimClasses(49L,List.of(11L,12L)));
        verify(connection).rollback();
        verify(connection, never()).commit();
    }
}
