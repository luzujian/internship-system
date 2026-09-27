package com.internship.agent.business;

import com.internship.entity.CompanyUser;
import com.internship.entity.Position;
import com.internship.entity.StudentJobApplication;
import com.internship.entity.StudentUser;
import com.internship.exception.BusinessException;
import com.internship.mapper.CompanyUserMapper;
import com.internship.mapper.StudentUserMapper;
import com.internship.service.PositionService;
import com.internship.service.StudentJobApplicationService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.internship.agent.business.AgentTypes.*;
import static com.internship.agent.business.JobApplyExecuteService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class JobApplyExecuteServiceTest {
    private AgentTaskService tasks;
    private AgentCapabilityService capabilities;
    private AgentActionService actions;
    private StudentPlanService plans;
    private PositionService positions;
    private StudentJobApplicationService applications;
    private JobApplyExecuteService service;
    private final Actor student = new Actor("ROLE_STUDENT:8:false:", 8L, "ROLE_STUDENT", "s001", null, null, null, false, Set.of());

    @BeforeEach void setup() {
        tasks = mock(AgentTaskService.class);
        capabilities = mock(AgentCapabilityService.class);
        actions = mock(AgentActionService.class);
        plans = mock(StudentPlanService.class);
        positions = mock(PositionService.class);
        CompanyUserMapper companies = mock(CompanyUserMapper.class);
        StudentUserMapper students = mock(StudentUserMapper.class);
        applications = mock(StudentJobApplicationService.class);
        AnnouncementWebSocketHandler webSocket = mock(AnnouncementWebSocketHandler.class);
        when(tasks.enabled()).thenReturn(true);
        when(capabilities.has(student, "job_apply")).thenReturn(true);
        Map<String,Map<String,Object>> receipts = new java.util.HashMap<>();
        when(actions.execute(any(),anyString(),anyString(),anyMap(),any())).thenAnswer(call -> {
            String id = call.getArgument(1);
            if(receipts.containsKey(id)) return receipts.get(id);
            java.util.function.Supplier<Map<String,Object>> work=call.getArgument(4);
            Map<String,Object> result=work.get();receipts.put(id,result);return result;
        });
        when(actions.get(any(),anyString())).thenAnswer(call -> new AgentActionService.ActionView(
                call.getArgument(1),"JOB_APPLY","position:5",Map.of("positionId","5"),":active","READY",now(),now()+600000,null,null,null));
        when(plans.refresh(student)).thenReturn(new StudentPlanService.PlanView(
                "SEARCH", "寻找实习", java.util.List.of(), "投递岗位", "去投递", 1, false));
        when(plans.factsOf(any())).thenReturn(Map.of("stage", "SEARCH"));
        Position position = new Position();
        position.setId(5L);
        position.setCompanyId(2L);
        position.setPositionName("后端开发");
        position.setStatus("active");
        when(positions.findById(5L)).thenReturn(position);
        CompanyUser company = new CompanyUser();
        company.setId(2L);
        company.setCompanyName("云启科技");
        company.setStatus(1);
        company.setAuditStatus(1);
        when(companies.findById(2L)).thenReturn(company);
        StudentUser user = new StudentUser();
        user.setId(8L);
        user.setName("s001");
        user.setStudentUserId("S008");
        user.setPhone("13800000000");
        user.setEmail("student@example.test");
        user.setMajor("软件工程");
        user.setGrade(2023);
        user.setStatus(1);
        when(students.findById(8L)).thenReturn(user);
        doAnswer(call -> {
            StudentJobApplication app = call.getArgument(0);
            app.setId(99L);
            return 1;
        }).when(applications).create(any(StudentJobApplication.class));
        service = new JobApplyExecuteService(tasks, capabilities, actions, plans, positions,
                companies, students, applications, webSocket);
    }

    private long now() {
        return System.currentTimeMillis();
    }

    private String requestId() {
        return UUID.randomUUID().toString();
    }

    @Test void capabilityDisabledYieldsForbidden() {
        when(capabilities.has(student, "job_apply")).thenReturn(false);
        AgentException e = assertThrows(AgentException.class,
                () -> service.apply(student, new ApplyRequest("5", requestId(), true, requestId(), now())));
        assertEquals("CAPABILITY_DISABLED", e.getCode());
        verify(applications, never()).create(any());
    }

    @Test void unconfirmedApplyIsRejected() {
        AgentException e = assertThrows(AgentException.class,
                () -> service.apply(student, new ApplyRequest("5", requestId(), false, requestId(), now())));
        assertEquals("NEED_CONFIRM", e.getCode());
        verify(applications, never()).create(any());
    }

    @Test void duplicateRequestIdReturnsFirstReceiptWithoutRecreate() {
        String key = requestId();
        Map<String, Object> first = service.apply(student, new ApplyRequest("5", key, true, key, now()));
        assertEquals("99", String.valueOf(first.get("applicationId")));
        StudentJobApplication existing = new StudentJobApplication();
        existing.setId(99L);
        existing.setStatus("pending");
        existing.setCompanyName("云启科技");
        existing.setPositionName("后端开发");
        when(applications.findById(99L)).thenReturn(existing);
        Map<String, Object> second = service.apply(student, new ApplyRequest("5", key, true, key, now()));
        assertEquals("99", String.valueOf(second.get("applicationId")));
        verify(applications, times(1)).create(any());
    }

    @Test void businessRuleViolationSurfacesAsExecuteFailed() {
        doThrow(new BusinessException("您已申请过该岗位")).when(applications).create(any());
        AgentException e = assertThrows(AgentException.class,
                () -> service.apply(student, new ApplyRequest("5", requestId(), true, requestId(), now())));
        assertEquals("EXECUTE_FAILED", e.getCode());
    }

    @Test void missingPreviewIsRejected() {
        AgentException e = assertThrows(AgentException.class,
                () -> service.apply(student, new ApplyRequest("5", requestId(), true)));
        assertEquals("PREVIEW_REQUIRED", e.getCode());
        verify(applications, never()).create(any());
    }

    @Test void stalePreviewIsRejectedUntilRePreviewed() {
        doThrow(new AgentException("PREVIEW_EXPIRED","确认已过期",409)).when(actions).execute(any(),anyString(),anyString(),anyMap(),any());
        long stale = System.currentTimeMillis() - 11 * 60 * 1000L;
        AgentException e = assertThrows(AgentException.class,
                () -> service.apply(student, new ApplyRequest("5", requestId(), true, requestId(), stale)));
        assertEquals("PREVIEW_EXPIRED", e.getCode());
        verify(applications, never()).create(any());
    }

    @Test void sameActionAcrossTabsReturnsFirstReceipt() {
        String actionId = requestId();
        Map<String, Object> first = service.apply(student, new ApplyRequest("5", requestId(), true, actionId, now()));
        assertEquals("99", String.valueOf(first.get("applicationId")));
        StudentJobApplication existing = new StudentJobApplication();
        existing.setId(99L);
        existing.setStatus("pending");
        existing.setCompanyName("云启科技");
        existing.setPositionName("后端开发");
        when(applications.findById(99L)).thenReturn(existing);
        // 另一个标签页用同一确认动作、不同请求身份再次确认：回到首次回执，不重复创建。
        Map<String, Object> second = service.apply(student, new ApplyRequest("5", requestId(), true, actionId, now()));
        assertEquals("99", String.valueOf(second.get("applicationId")));
        verify(applications, times(1)).create(any());
    }
    @Test void withdrawRequiresOwnership() {
        AgentException e = assertThrows(AgentException.class,
                () -> service.withdraw(student, new WithdrawRequest("7", requestId(), true, requestId(), now())));
        assertEquals("OBJECT_UNAVAILABLE", e.getCode());
    }
}
