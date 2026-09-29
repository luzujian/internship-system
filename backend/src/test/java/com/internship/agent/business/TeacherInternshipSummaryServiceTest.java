package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.dto.JobSearchStatusDTO;
import com.internship.mapper.AgentReadMapper;
import com.internship.service.JobSearchStatusService;
import org.junit.jupiter.api.*;
import java.util.*;
import static com.internship.agent.business.AgentTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherInternshipSummaryServiceTest {
    final ObjectMapper json=new ObjectMapper();
    final AgentReadMapper reads=mock(AgentReadMapper.class);
    final JobSearchStatusService jobSearchStatuses=mock(JobSearchStatusService.class);
    final TeacherInternshipSummaryService service=new TeacherInternshipSummaryService(reads,jobSearchStatuses);
    final Actor college=new Actor("t47",47L,"ROLE_TEACHER_COLLEGE","学院教师","COLLEGE",24L,null,false,Set.of("view:student_tracking"));
    final Actor department=new Actor("t48",48L,"ROLE_TEACHER_DEPARTMENT","系室教师","DEPARTMENT",24L,15L,false,Set.of("view:student_tracking"));
    final Actor counselor=new Actor("t194",194L,"ROLE_TEACHER_COUNSELOR","辅导员","COUNSELOR",null,null,false,Set.of("view:student_tracking"));

    @Test void collegeSummaryUsesBackendStatisticsAndPersistsScope()throws Exception{
        when(reads.teacherSummaryScopes(college,"COLLEGE",null,null)).thenReturn(List.of(Map.of("id","24","name","生物医学工程学院")));
        when(reads.teacherSummaryStudentRows(college,"COLLEGE",24L)).thenReturn(rows());
        derived("IN_PROGRESS","NOT_FOUND","NO_OFFER");
        Session session=new Session();
        ToolResult result=service.query(college,json.readTree("{\"scopeType\":\"COLLEGE\",\"view\":\"summary\"}"),session);
        assertEquals(3L,result.facts().get("total"));
        assertEquals(2L,result.facts().get("withoutOffer"));
        assertEquals(2L,result.facts().get("focusCount"));
        assertEquals(1L,result.facts().get("placed"));
        assertEquals(33.3,result.facts().get("placedRate"));
        assertEquals("COLLEGE",session.getLastTeacherSummaryQuery().scopeType());
        assertEquals(24L,session.getLastTeacherSummaryQuery().scopeId());
    }

    @Test void departmentTeacherCannotQueryAnotherDivisionEvenIfResolverReturnsIt()throws Exception{
        when(reads.teacherSummaryScopes(department,"DIVISION","信工系",null)).thenReturn(List.of(Map.of("id","16","name","信工系")));
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.query(department,json.readTree("{\"scopeType\":\"DIVISION\",\"scopeName\":\"信工系\"}"),new Session())).getCode());
        verify(reads,never()).teacherSummaryStudentRows(any(),anyString(),any());
    }

    @Test void classScopeIsResolvedWithinTeacherAuthorization()throws Exception{
        when(reads.teacherSummaryScopes(department,"CLASS","23信管1班",null)).thenReturn(List.of(Map.of("id","173","name","23信管1班")));
        when(reads.teacherSummaryStudentRows(department,"CLASS",173L)).thenReturn(rows());
        derived("IN_PROGRESS","NOT_FOUND","NO_OFFER");
        ToolResult result=service.query(department,json.readTree("{\"scopeType\":\"CLASS\",\"scopeName\":\"23信管1班\",\"view\":\"summary\"}"),new Session());
        assertEquals("23信管1班",result.facts().get("scopeName"));
        assertEquals(3L,result.facts().get("total"));
    }

    @Test void counselorUsesOnlyAssignedClassesAndCannotQueryCollegeOrDivision()throws Exception{
        when(reads.teacherSummaryStudentRows(counselor,"COUNSELOR",null)).thenReturn(rows());
        derived("IN_PROGRESS","NOT_FOUND","NO_OFFER");
        ToolResult result=service.query(counselor,json.readTree("{\"scopeType\":\"COUNSELOR\",\"view\":\"summary\"}"),new Session());
        assertEquals(3L,result.facts().get("total"));
        when(reads.teacherSummaryScopes(counselor,"CLASS","23级生工7班",null)).thenReturn(List.of(Map.of("id","179","name","23级生工7班")));
        when(reads.teacherSummaryStudentRows(counselor,"CLASS",179L)).thenReturn(rows());
        assertEquals(3L,service.query(counselor,json.readTree("{\"scopeType\":\"CLASS\",\"scopeName\":\"23级生工7班\",\"view\":\"summary\"}"),new Session()).facts().get("total"));
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.query(counselor,json.readTree("{\"scopeType\":\"COLLEGE\"}"),new Session())).getCode());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.query(counselor,json.readTree("{\"scopeType\":\"DIVISION\",\"scopeName\":\"信工系\"}"),new Session())).getCode());
        when(reads.teacherSummaryScopes(counselor,"CLASS","23信管1班",null)).thenReturn(List.of());
        assertEquals("SCOPE_NOT_FOUND",assertThrows(AgentException.class,()->service.query(counselor,json.readTree("{\"scopeType\":\"CLASS\",\"scopeName\":\"23信管1班\"}"),new Session())).getCode());
    }

    @Test void lastScopeIsReusedOnlyAfterASuccessfulVerifiedQuery()throws Exception{
        assertEquals("NO_LAST_SCOPE",assertThrows(AgentException.class,()->service.query(college,json.readTree("{\"scopeType\":\"LAST\",\"view\":\"students\"}"),new Session())).getCode());
        when(reads.teacherSummaryScopes(college,"COLLEGE",null,null)).thenReturn(List.of(Map.of("id","24","name","生物医学工程学院")));
        when(reads.teacherSummaryStudentRows(college,"COLLEGE",24L)).thenReturn(rows());
        derived("IN_PROGRESS","NOT_FOUND","NO_OFFER");
        Session session=new Session();
        service.query(college,json.readTree("{\"scopeType\":\"COLLEGE\",\"view\":\"summary\"}"),session);
        when(reads.teacherSummaryScopes(college,"COLLEGE",null,24L)).thenReturn(List.of(Map.of("id","24","name","学院")));
        ToolResult result=service.query(college,json.readTree("{\"scopeType\":\"LAST\",\"view\":\"students\"}"),session);
        assertEquals(3L,result.total());
        verify(reads,times(2)).teacherSummaryStudentRows(college,"COLLEGE",24L);
    }

    @Test void ambiguousMissingAndUnauthorizedScopesFailClosed()throws Exception{
        when(reads.teacherSummaryScopes(college,"DIVISION","系",null)).thenReturn(List.of(Map.of("id","15","name","生工系"),Map.of("id","16","name","信工系")));
        AgentException ambiguous=assertThrows(AgentException.class,()->service.query(college,json.readTree("{\"scopeType\":\"DIVISION\",\"scopeName\":\"系\"}"),new Session()));
        assertEquals("AMBIGUOUS_SCOPE",ambiguous.getCode());
        assertTrue(ambiguous.getMessage().contains("生工系、信工系"));
        when(reads.teacherSummaryScopes(college,"DIVISION","不存在的系",null)).thenReturn(List.of());
        assertEquals("SCOPE_NOT_FOUND",assertThrows(AgentException.class,()->service.query(college,json.readTree("{\"scopeType\":\"DIVISION\",\"scopeName\":\"不存在的系\"}"),new Session())).getCode());
        Actor noPermission=new Actor("t1",1L,"ROLE_TEACHER_COLLEGE","无权限","COLLEGE",24L,null,false,Set.of());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->service.query(noPermission,json.readTree("{\"scopeType\":\"COLLEGE\"}"),new Session())).getCode());
    }

    @Test void statusFilterUsesUnifiedDisplayStatusAndFocusView()throws Exception{
        when(reads.teacherSummaryScopes(college,"COLLEGE",null,null)).thenReturn(List.of(Map.of("id","24","name","生物医学工程学院")));
        when(reads.teacherSummaryStudentRows(college,"COLLEGE",24L)).thenReturn(rows());
        derived("IN_PROGRESS","NOT_FOUND","NO_OFFER");
        ToolResult result=service.query(college,json.readTree("{\"scopeType\":\"COLLEGE\",\"view\":\"focus\",\"status\":\"NO_OFFER\",\"page\":1}"),new Session());
        assertEquals(1L,result.total());
        assertEquals("NO_OFFER",result.items().getFirst().get("status"));
        assertEquals("NO_OFFER",result.facts().get("status"));
        assertEquals("INVALID_TOOL_INPUT",assertThrows(AgentException.class,()->service.query(college,json.readTree("{\"scopeType\":\"COLLEGE\",\"status\":\"MADE_UP\"}"),new Session())).getCode());
    }

    private List<Map<String,Object>> rows(){
        List<Map<String,Object>> result=new ArrayList<>();
        result.add(student(1L,"张一","20230001","23信管1班","信息管理与信息系统","信工系","示例公司","开发实习"));
        result.add(student(2L,"张二","20230002","23信管1班","信息管理与信息系统","信工系","",""));
        result.add(student(3L,"张三","20230003","23信管1班","信息管理与信息系统","信工系","",""));
        return result;
    }
    private Map<String,Object> student(Long id,String name,String no,String className,String major,String division,String company,String position){
        Map<String,Object> row=new LinkedHashMap<>();
        row.put("id",id);row.put("title",name);row.put("studentNo",no);row.put("className",className);
        row.put("major",major);row.put("division",division);row.put("company",company);row.put("position",position);
        return row;
    }
    private void derived(String... statuses){
        Map<Long,JobSearchStatusDTO> map=new LinkedHashMap<>();
        for(int i=0;i<statuses.length;i++) map.put((long)(i+1),new JobSearchStatusDTO(statuses[i],statuses[i]));
        when(jobSearchStatuses.resolveForStudentIds(List.of(1L,2L,3L))).thenReturn(map);
    }
}
