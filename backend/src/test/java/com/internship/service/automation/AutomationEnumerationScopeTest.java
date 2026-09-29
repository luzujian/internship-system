package com.internship.service.automation;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.TeacherUser;
import com.internship.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class AutomationEnumerationScopeTest {
    @Test void removedStudentIsExcludedBeforeFactsAreLoaded() throws Exception {
        var service=mock(AutomationService.class);var db=mock(JdbcTemplate.class);
        var teachers=mock(TeacherUserService.class);var scope=mock(TeacherAutomationScopeService.class);var facts=mock(AutomationFacts.class);
        ReflectionTestUtils.setField(service,"db",db);ReflectionTestUtils.setField(service,"teachers",teachers);
        ReflectionTestUtils.setField(service,"scope",scope);ReflectionTestUtils.setField(service,"facts",facts);
        ReflectionTestUtils.setField(service,"json",new ObjectMapper());ReflectionTestUtils.setField(service,"tx",mock(TransactionTemplate.class));
        when(service.decode(any())).thenCallRealMethod();
        var teacher=new TeacherUser();when(teachers.findById(1L)).thenReturn(teacher);
        when(scope.contains(teacher,10L)).thenReturn(true);
        var task=Map.<String,Object>of("id",1L,"teacher_id",1L,"status","ACTIVE","version",1L);
        when(db.update(anyString(),anyString(),eq("run"))).thenReturn(1);
        when(db.queryForMap(anyString(),eq(1L))).thenReturn(task);
        var dispatch=Map.<String,Object>of("run_id","run","task_id",1L,"task_version",1L,"cursor_id",0L,"valid_until",AutomationService.now().plusHours(1),"definition","{}","audience","[10,20]");
        var worker=new AutomationWorker(service,mock(ReminderService.class),false);
        try {
            worker.enumerate(dispatch);
            verify(facts).load(eq(List.of(10L)),any());
            verify(facts,never()).load(eq(List.of(10L,20L)),any());
        } finally {worker.close();}
    }
}
