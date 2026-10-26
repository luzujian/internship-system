package com.internship.importing;

import com.internship.agent.business.*;
import com.internship.entity.*;
import com.internship.mapper.*;
import com.internship.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmartImportExecutorTest {
    @Test void importedMajorKeepsTheRequiredStoredDivisionRelationship() {
        var majors = mock(MajorService.class);
        var executor = new SmartImportExecutor(mock(StudentUserService.class),mock(TeacherUserService.class),mock(CompanyUserService.class),mock(AnnouncementService.class),
                mock(ClassService.class),mock(DepartmentService.class),majors,mock(DivisionService.class),mock(PositionCategoryService.class),mock(PositionService.class),mock(StudentUserMapper.class),mock(StudentInternshipStatusMapper.class));
        var data = SmartImportValidationTest.students();
        Division division = new Division(); division.setId(3L); division.setDepartmentId(24L); division.setName("检验系");
        var snapshot = new SmartImportCatalog.Snapshot(data.majors(),data.departments(),List.of(division),data.classes(),data.teachers(),data.companies(),data.categories(),data.positions(),data.students(),data.announcements());
        when(majors.save(any())).thenReturn(1);
        var row = SmartImportValidationTest.row(2,Map.of("院系名称","医学技术学院","系名称","检验系","专业名称","新专业"));
        var actor = new AgentTypes.Actor("admin",2L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        executor.execute(actor,SmartImportType.DEPARTMENT_MAJOR,List.of(row),snapshot);
        verify(majors).save(argThat(m -> m.getDepartmentId().equals(24L) && m.getDivisionId().equals(3L)));
    }
    @Test void importedPositionsUseTheExistingPausedStatusContract() {
        var positions = mock(PositionService.class);
        var executor = new SmartImportExecutor(mock(StudentUserService.class),mock(TeacherUserService.class),mock(CompanyUserService.class),mock(AnnouncementService.class),
                mock(ClassService.class),mock(DepartmentService.class),mock(MajorService.class),mock(DivisionService.class),mock(PositionCategoryService.class),positions,mock(StudentUserMapper.class),mock(StudentInternshipStatusMapper.class));
        PositionCategory category = new PositionCategory(); category.setId(11L); category.setName("软件开发");
        var data = SmartImportValidationTest.students();
        var snapshot = new SmartImportCatalog.Snapshot(data.majors(),data.departments(),data.divisions(),data.classes(),data.teachers(),data.companies(),List.of(category),data.positions(),data.students(),data.announcements());
        when(positions.insert(any())).thenReturn(1);
        var row = SmartImportValidationTest.row(2,Map.of("类别名称","软件开发","岗位名称","测试岗位","企业名称","8","岗位要求","测试要求"));
        var actor = new AgentTypes.Actor("admin",2L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        executor.execute(actor,SmartImportType.POSITION_CATEGORY,List.of(row),snapshot);
        verify(positions).insert(argThat(p -> "paused".equals(p.getStatus()) && p.getCompanyId().equals(8L)));
    }
    @Test void missingStudentInitialStatusFailsTheWholeAction() {
        var students = mock(StudentUserService.class); var mapper = mock(StudentUserMapper.class); var statuses = mock(StudentInternshipStatusMapper.class);
        var executor = new SmartImportExecutor(students,mock(TeacherUserService.class),mock(CompanyUserService.class),mock(AnnouncementService.class),
                mock(ClassService.class),mock(DepartmentService.class),mock(MajorService.class),mock(DivisionService.class),mock(PositionCategoryService.class),mock(PositionService.class),mapper,statuses);
        when(students.importFromExcel(anyList())).thenReturn(Map.of("successCount",1,"failCount",0));
        StudentUser saved = new StudentUser(); saved.setId(900L); when(mapper.findByStudentId("test-1")).thenReturn(saved);
        var actor = new AgentTypes.Actor("admin",2L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        var row = SmartImportValidationTest.row(2,Map.of("学号","test-1"));
        assertEquals("IMPORT_INCOMPLETE",assertThrows(AgentException.class,() -> executor.execute(actor,SmartImportType.STUDENT,List.of(row),SmartImportValidationTest.students())).getCode());
    }
}
