package com.internship.agent.business;

import java.util.*;
import org.junit.jupiter.api.Test;
import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentFillAuthorizationTest {
    final AgentIdentityService identity=mock(AgentIdentityService.class);
    final FillIntentService fills=mock(FillIntentService.class);
    final Actor actor=new Actor("ROLE_STUDENT:1:false:",1L,"ROLE_STUDENT","local",null,null,null,false,Set.of());
    final AgentController controller=new AgentController(identity,mock(AgentTaskService.class),mock(AgentStore.class),fills,mock(AgentUiIntentService.class),mock(JobApplyExecuteService.class),mock(TeacherReminderExecuteService.class),mock(AgentActionService.class),mock(com.internship.service.SysDictService.class));
    @Test void accountDisabledDuringModelCallCannotReturnAssignments(){
        when(identity.resolve("Bearer fixture")).thenReturn(actor).thenThrow(new AgentException("AUTH_REQUIRED","disabled",401));
        when(fills.fill(actor,null)).thenReturn(Map.of("assignments",List.of(Map.of("prop","remark","value","late"))));
        assertEquals("AUTH_REQUIRED",assertThrows(AgentException.class,()->controller.fillIntent("Bearer fixture",null)).getCode());
        verify(fills).fill(actor,null);
    }
    @Test void roleChangedDuringModelCallCannotReturnAssignments(){
        Actor changed=new Actor("ROLE_TEACHER:1:false:",1L,"ROLE_TEACHER","local","COUNSELOR",null,null,false,Set.of());
        when(identity.resolve("Bearer fixture")).thenReturn(actor,changed);
        when(fills.fill(actor,null)).thenReturn(Map.of("assignments",List.of(Map.of("prop","remark","value","late"))));
        assertEquals("SCOPE_CHANGED",assertThrows(AgentException.class,()->controller.fillIntent("Bearer fixture",null)).getCode());
    }
}
