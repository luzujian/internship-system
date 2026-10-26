package com.internship.agent.business;

import com.internship.entity.*;
import com.internship.mapper.*;
import com.internship.service.TeacherPermissionService;
import com.internship.utils.JwtUtils;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentIdentityServiceTest {
    JwtUtils jwt=mock(JwtUtils.class);
    StudentUserMapper students=mock(StudentUserMapper.class);
    TeacherUserMapper teachers=mock(TeacherUserMapper.class);
    CompanyUserMapper companies=mock(CompanyUserMapper.class);
    AdminUserMapper admins=mock(AdminUserMapper.class);
    TeacherPermissionService permissions=mock(TeacherPermissionService.class);
    AgentIdentityService identity=new AgentIdentityService(jwt,students,teachers,companies,admins,permissions);
    @Test void expiredSessionCannotStartAgentTools() {
        claims("ROLE_STUDENT", "access", false);
        when(jwt.isSessionActive(any())).thenReturn(false);
        assertThrows(AgentException.class, () -> identity.resolve("Bearer signed"));
    }

    void claims(String role,String type,boolean readonly){
        when(jwt.isSessionActive(any())).thenReturn(true);
        when(jwt.validateToken("signed")).thenReturn(true);
        when(jwt.parseToken("signed")).thenReturn(Jwts.claims().add("username","same-username").add("role",role).add("typ",type).add("isReadOnly",readonly).add("originalAdminUsername",readonly?"admin":"").issuedAt(new Date()).build());
    }
    @Test void signedRoleChoosesTypedAccountEvenWhenUsernamesOverlap(){
        claims("ROLE_STUDENT","access",false);
        StudentUser student=new StudentUser();student.setId(3L);student.setName("学生");student.setStatus(1);when(students.findByStudentId("same-username")).thenReturn(student);
        assertEquals(3L,identity.resolve("Bearer signed").id());verifyNoInteractions(admins,teachers,companies);
    }
    @Test void refreshTokenRevokedTokenAndDisabledAccountsAreRejected(){
        claims("ROLE_STUDENT","refresh",false);assertThrows(AgentException.class,()->identity.resolve("Bearer signed"));
        claims("ROLE_STUDENT","access",false);
        StudentUser student=new StudentUser();student.setId(3L);student.setStatus(0);when(students.findByStudentId("same-username")).thenReturn(student);
        assertThrows(AgentException.class,()->identity.resolve("Bearer signed"));
        when(jwt.validateToken("signed")).thenReturn(false);assertThrows(AgentException.class,()->identity.resolve("Bearer signed"));
    }
    @Test void readonlyViewAsHasASeparateSessionOwner(){
        claims("ROLE_STUDENT","access",false);
        StudentUser student=new StudentUser();student.setId(3L);student.setStatus(1);when(students.findByStudentId("same-username")).thenReturn(student);
        String owner=identity.resolve("Bearer signed").ownerKey();claims("ROLE_STUDENT","access",true);
        var readonly=identity.resolve("Bearer signed");assertTrue(readonly.readOnly());assertNotEquals(owner,readonly.ownerKey());
    }
    @Test void staleTeacherTypeCannotKeepFormerPrivileges(){
        claims("ROLE_TEACHER_COLLEGE","access",false);
        TeacherUser teacher=new TeacherUser();teacher.setId(4L);teacher.setStatus(1);teacher.setTeacherType("COUNSELOR");when(teachers.findByTeacherUserId("same-username")).thenReturn(teacher);
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->identity.resolve("Bearer signed")).getCode());verifyNoInteractions(permissions);
    }
}
