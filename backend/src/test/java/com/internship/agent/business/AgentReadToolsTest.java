package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.entity.*;
import com.internship.entity.dto.*;
import com.internship.mapper.*;
import com.internship.service.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static com.internship.agent.business.AgentTypes.*;

class AgentReadToolsTest {
    StudentUserMapper students=mock(StudentUserMapper.class);
    CompanyUserMapper companies=mock(CompanyUserMapper.class);
    PositionService positions=mock(PositionService.class);
    StudentJobApplicationService applications=mock(StudentJobApplicationService.class);
    StudentApplicationService ordinary=mock(StudentApplicationService.class);
    InterviewInvitationService interviews=mock(InterviewInvitationService.class);
    InternshipConfirmationRecordService confirmations=mock(InternshipConfirmationRecordService.class);
    InternshipStageAssignmentService placements=mock(InternshipStageAssignmentService.class);
    StudentHomeTodoService todos=mock(StudentHomeTodoService.class);
    InternshipCalendarService calendar=mock(InternshipCalendarService.class);
    AgentReadMapper mapper=mock(AgentReadMapper.class);
    HomeService homeService=mock(HomeService.class);
    CompanyUserService companyUsers=mock(CompanyUserService.class);
    InternshipReflectionMapper reflections=mock(InternshipReflectionMapper.class);
    ClassCounselorRelationMapper counselorRelations=mock(ClassCounselorRelationMapper.class);
    TeacherUserMapper teacherUsers=mock(TeacherUserMapper.class);
    TeacherInternshipAppraisalService appraisals=mock(TeacherInternshipAppraisalService.class);
    StudentPlanService plans=mock(StudentPlanService.class);
    AgentCapabilityService capabilities=mock(AgentCapabilityService.class);
    TeacherInternshipSummaryService summaries=mock(TeacherInternshipSummaryService.class);
    JobSearchStatusService jobSearchStatuses=mock(JobSearchStatusService.class);
    InternshipProgressRecordService progressRecords=mock(InternshipProgressRecordService.class);
    TeacherReminderExecuteService reminderDrafts=mock(TeacherReminderExecuteService.class);
    AgentReadTools tools;
    Actor student=new Actor("s1",1L,"ROLE_STUDENT","学生",null,null,null,false,Set.of());
    Actor teacher=new Actor("t1",2L,"ROLE_TEACHER_COLLEGE","老师","COLLEGE",5L,null,false,Set.of("view:student_tracking"));
    @BeforeEach void setup()throws Exception{when(capabilities.has(any(),eq("agent_chat"))).thenReturn(true);ObjectMapper json=new ObjectMapper();tools=new AgentReadTools(json,students,companies,positions,applications,ordinary,interviews,confirmations,placements,todos,calendar,mapper,new AgentKnowledge(json),homeService,companyUsers,reflections,counselorRelations,teacherUsers,appraisals,plans,capabilities,summaries,jobSearchStatuses,progressRecords,mock(com.internship.service.SysDictService.class));tools.setReminderDrafts(reminderDrafts);com.internship.support.ReflectionFeatureTestSupport.enable(tools);}
    @Test void retiredReflectionIsNotAdvertisedOrExecutableDespiteRetainedPermissions(){
        org.springframework.test.util.ReflectionTestUtils.setField(tools, "reflectionFeature", mock(ReflectionFeatureService.class));
        assertTrue(tools.specs(student).stream().noneMatch(spec -> spec.name().equals("student_reflections")));
        assertTrue(tools.specs(teacher).stream().noneMatch(spec -> spec.name().equals("teacher_reflections")));
        assertTrue(tools.specs(student).stream().anyMatch(spec -> spec.name().equals("student_appraisal")));
        assertTrue(tools.specs(student).stream().anyMatch(spec -> spec.name().equals("internship_calendar")));
        assertThrows(AgentException.class, () -> tools.execute(student, "student_reflections", "{}"));
        verifyNoInteractions(reflections);
    }
    @Test void failedSnapshotMustNotClaimSaved(){
        when(capabilities.has(student,"internship_plan")).thenReturn(true);
        when(plans.refresh(student)).thenReturn(new StudentPlanService.PlanView(
                "SEARCH","寻找实习",List.of(),"投递岗位","去投递",0,false));
        when(plans.factsOf(any())).thenCallRealMethod();
        ToolResult result=tools.execute(student,"student_plan","{}");
        assertFalse(result.warnings().stream().anyMatch(w -> w.contains("快照已保存")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.contains("保存失败")));
    }

    @Test void roleAndParameterValidationPrecedeBusinessReads(){
        assertThrows(AgentException.class,()->tools.execute(student,"teacher_reflections","{}"));
        assertThrows(AgentException.class,()->tools.execute(student,"student_profile","{\"studentId\":\"2\"}"));
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_details","{\"ids\":[\"1\",\"2\",\"3\",\"4\"]}"));
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_details","{\"ids\":[1]}"));
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_search","{\"page\":-1}"));
        assertThrows(AgentException.class,()->tools.execute(student,"delete_student","{}"));
        verifyNoInteractions(students,positions,mapper);
    }
    @Test void otherStudentsApplicationIsIndistinguishableFromMissingObject(){
        StudentJobApplication other=new StudentJobApplication();other.setStudentId(99L);
        when(applications.findById(10L)).thenReturn(other);
        assertEquals("OBJECT_UNAVAILABLE",assertThrows(AgentException.class,()->tools.validateObject(student,new BusinessObject("job_application","10"))).getCode());
        assertEquals("OBJECT_UNAVAILABLE",assertThrows(AgentException.class,()->tools.validateObject(student,new BusinessObject("job_application","11"))).getCode());
    }
    @Test void disabledCompanyOrInactivePositionCannotBeQueried(){
        Position position=new Position();position.setId(10L);position.setCompanyId(7L);position.setStatus("active");
        CompanyUser company=new CompanyUser();company.setStatus(0);company.setAuditStatus(1);
        when(positions.findById(10L)).thenReturn(position);when(companies.findById(7L)).thenReturn(company);
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_details","{\"ids\":[\"10\"]}"));
        position.setStatus("closed");company.setStatus(1);
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_details","{\"ids\":[\"10\"]}"));
    }
    @Test void jobComparisonUsesActualIdsAndKeepsMissingProfileDataExplicit(){
        CompanyUser company=new CompanyUser();company.setStatus(1);company.setAuditStatus(1);company.setCompanyName("示例企业");company.setIndustry("互联网");company.setScale("100-500人");
        when(companies.findById(7L)).thenReturn(company);
        for(long id:List.of(10L,20L)){
            Position position=new Position();position.setId(id);position.setCompanyId(7L);position.setStatus("active");position.setPositionName("岗位"+id);position.setRequirements("Java开发");position.setSalaryUnit("元/天");position.setProvidesAccommodation(1);position.setInterviewMethod("线上视频");
            when(positions.findById(id)).thenReturn(position);
        }
        ToolResult details=tools.execute(student,"jobs_details","{\"ids\":[\"20\",\"10\"]}");
        assertEquals(List.of("20","10"),details.items().stream().map(row->row.get("id")).toList());assertEquals(List.of("查看岗位：岗位20（示例企业）","查看岗位：岗位10（示例企业）"),details.links().stream().map(AgentTypes.Link::label).toList());assertEquals("元/天",details.items().getFirst().get("salaryUnit"));assertEquals(1,details.items().getFirst().get("providesAccommodation"));assertEquals("线上视频",details.items().getFirst().get("interviewMethod"));assertEquals("互联网",details.items().getFirst().get("companyIndustry"));assertEquals("100-500人",details.items().getFirst().get("companyScale"));
        when(mapper.studentAdvisorProfile(1L)).thenReturn(Map.of("title","学生"));
        ToolResult incomplete=tools.execute(student,"student_profile","{}");assertEquals("",incomplete.items().getFirst().get("major"));assertFalse(incomplete.warnings().isEmpty());assertFalse(incomplete.items().getFirst().containsKey("password"));
        assertTrue(((List<?>)incomplete.facts().get("unverifiedFields")).contains("学历层次"));
    }
    @Test void missingCalendarIsAnErrorNotZeroSubmissions(){
        assertEquals("CALENDAR_UNAVAILABLE",assertThrows(AgentException.class,()->tools.execute(teacher,"teacher_reflections","{}")).getCode());
        verifyNoInteractions(mapper);
    }
    @Test void unifiedCalendarIsReadableAcrossRolesAndReflectsLatestTeacherSettings(){
        InternshipCalendar first = new InternshipCalendar();
        first.setStartDate("2026-06-30");first.setEndDate("2026-12-31");
        first.setAppraisalDeadline("2027-01-31");first.setReportCycle(7);
        when(calendar.getCalendar()).thenReturn(first);
        var company = new Actor("c1",7L,"ROLE_COMPANY","企业",null,null,null,false,Set.of());
        var admin = new Actor("a1",9L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        for (Actor actor : List.of(student, teacher, company, admin)) {
            ToolResult actual = tools.execute(actor,"internship_calendar","{}");
            assertEquals("2026-06-30",actual.facts().get("startDate"));
            assertEquals("2026-12-31",actual.facts().get("endDate"));
            assertEquals("2027-01-31",actual.facts().get("appraisalDeadline"));
            assertEquals(7,actual.facts().get("reportCycleDays"));
            assertEquals(List.of(),actual.facts().get("missingFields"));
            assertEquals("全校统一安排",actual.scope());
        }
        InternshipCalendar changed = new InternshipCalendar();
        changed.setStartDate("2026-07-01");changed.setEndDate("2027-02-28");
        when(calendar.getCalendar()).thenReturn(changed);
        assertEquals("2027-02-28",tools.execute(student,"internship_calendar","{}").facts().get("endDate"));
        verify(calendar,times(5)).getCalendar();
        verifyNoInteractions(placements,students,applications);
    }
    @Test void missingCalendarFieldsRemainExplicitWithoutInventingPersonalDates(){
        when(calendar.getCalendar()).thenReturn(new InternshipCalendar());
        ToolResult missing = tools.execute(student,"internship_calendar","{}");
        assertEquals("",missing.facts().get("endDate"));
        assertTrue(((List<?>)missing.facts().get("missingFields")).contains("endDate"));
        InternshipCalendar partial = new InternshipCalendar();partial.setEndDate("2026-12-31");
        when(calendar.getCalendar()).thenReturn(partial);
        ToolResult result = tools.execute(student,"internship_calendar","{}");
        assertEquals("2026-12-31",result.facts().get("endDate"));
        assertFalse(((List<?>)result.facts().get("missingFields")).contains("endDate"));
        assertThrows(AgentException.class,()->tools.execute(student,"internship_calendar","{\"studentId\":\"2\"}"));
        verifyNoInteractions(placements,students);
    }
    @Test void invalidCalendarAndReadFailureAreNotReportedAsUnconfigured(){
        InternshipCalendar invalid = new InternshipCalendar();invalid.setEndDate("not-a-date");
        when(calendar.getCalendar()).thenReturn(invalid);
        assertEquals("CALENDAR_INVALID",assertThrows(AgentException.class,()->tools.execute(student,"internship_calendar","{}")).getCode());
        invalid.setStartDate("2026-12-31");invalid.setEndDate("2026-06-30");
        assertEquals("CALENDAR_INVALID",assertThrows(AgentException.class,()->tools.execute(student,"internship_calendar","{}")).getCode());
        when(calendar.getCalendar()).thenThrow(new IllegalStateException("database unavailable"));
        assertEquals("CALENDAR_UNAVAILABLE",assertThrows(AgentException.class,()->tools.execute(student,"internship_calendar","{}")).getCode());
    }
    @Test void municipalAddressAndInvalidSalaryRemainExplicit(){
        CompanyUser company=new CompanyUser();company.setStatus(1);company.setAuditStatus(1);company.setCompanyName("示例企业");
        Position job=new Position();job.setId(10L);job.setCompanyId(7L);job.setStatus("active");job.setPositionName("开发岗位");
        job.setProvince("北京市");job.setCity("市辖区");job.setDistrict("东城区");job.setSalaryUnit("K/鏈�");
        job.setSalaryMin(java.math.BigDecimal.ZERO);job.setSalaryMax(java.math.BigDecimal.ZERO);
        when(companies.findById(7L)).thenReturn(company);when(positions.findById(10L)).thenReturn(job);
        Map<String,Object> result=tools.execute(student,"jobs_details","{\"ids\":[\"10\"]}").items().getFirst();
        assertEquals("北京市 · 市辖区 · 东城区",result.get("location"));
        assertEquals("K/鏈�",result.get("salaryUnit"));assertEquals("NEEDS_CONFIRMATION",result.get("salaryStatus"));
        assertTrue(result.get("salaryNote").toString().contains("不能据此认定为无薪"));assertEquals("",result.get("requirements"));
        Map<String,Object> daily=Map.of("id","11","province","广东省","city","深圳市","salaryMin",180,"salaryMax",220,"salaryUnit","元/天");
        when(mapper.searchJobs(null,"深圳",0,10)).thenReturn(List.of(daily));when(mapper.countJobs(null,"深圳")).thenReturn(1L);
        Map<String,Object> found=tools.execute(student,"jobs_search","{\"city\":\"深圳\"}").items().getFirst();
        assertEquals("RECORDED",found.get("salaryStatus"));assertEquals(180,found.get("salaryMin"));assertEquals("元/天",found.get("salaryUnit"));
        assertEquals("广东省 · 深圳市",found.get("location"));assertFalse(daily.containsKey("salaryStatus"));
    }
    @Test void missingTeacherScopeCannotExpandToAllStudents(){
        Actor unassigned=new Actor("t2",3L,"ROLE_TEACHER_DEPARTMENT","老师","DEPARTMENT",null,null,false,Set.of("view:student_tracking"));
        assertEquals("SCOPE_UNAVAILABLE",assertThrows(AgentException.class,()->tools.execute(unassigned,"teacher_reflections","{}")).getCode());verifyNoInteractions(mapper);
    }
    @Test void waitingForCompanyIsNotAnotherStudentSubmission(){
        when(todos.getActionableTodos(1L)).thenReturn(List.of(new StudentTodoItemDTO("confirmation:9","CONFIRMATION_PENDING","等待企业确认","", "normal",null,null,null)));
        ToolResult result=tools.execute(student,"student_todos","{}");
        assertEquals("0",result.facts().get("actionableCount"));assertEquals("1",result.facts().get("waitingCount"));assertEquals("WAITING_OTHER",result.items().getFirst().get("handling"));
    }
    @Test void noRowsIsSuccessfulEmptyEvidence(){
        when(mapper.searchJobs("不存在的岗位",null,0,10)).thenReturn(List.of());
        ToolResult result=tools.execute(student,"jobs_search","{\"keyword\":\"不存在的岗位\"}");
        assertEquals("OK",result.status());assertEquals(0,result.total());assertFalse(result.hasMore());
    }
    @Test void serviceFailuresPropagateRatherThanBecomingNoData(){
        when(todos.getActionableTodos(1L)).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class,()->tools.execute(student,"student_todos","{}"));
    }
    @Test void teacherReminderDraftToolIsRegisteredAndDelegates(){
        assertTrue(tools.specs(teacher).stream().anyMatch(s->"teacher_reminder_draft".equals(s.name())));
        assertThrows(AgentException.class,()->tools.execute(student,"teacher_reminder_draft","{\"studentQuery\":\"s001\"}"));
        when(reminderDrafts.draft(eq(teacher),eq("s001"),eq("记得早点休息"),eq("STATION_REMINDER"))).thenReturn(
                new ToolResult("teacher_reminder_draft","学生提醒确认","OK","2026-09-22T10:00:00+08:00","当前教师负责范围",
                        List.of(),1,false,Map.of(),List.of(),List.of()));
        assertEquals("OK",tools.execute(teacher,"teacher_reminder_draft","{\"studentQuery\":\"s001\",\"content\":\"记得早点休息\",\"channel\":\"STATION_REMINDER\"}").status());
        verify(reminderDrafts).draft(teacher,"s001","记得早点休息","STATION_REMINDER");
        assertThrows(AgentException.class,()->tools.execute(teacher,"teacher_reminder_draft","{\"unknown\":\"x\"}"));
    }
    @Test void reminderDraftSchemaStaysMachineReadable(){
        String schema=tools.specs(teacher).stream().filter(s->"teacher_reminder_draft".equals(s.name())).findFirst().orElseThrow().schema();
        assertTrue(schema.contains("\"maxLength\":2000"));
        assertDoesNotThrow(()->new ObjectMapper().readTree(schema));
    }
    @Test void companyAndAdminExposeOnlyTheirOwnReadTools(){
        Actor company=new Actor("ROLE_COMPANY",10L,"ROLE_COMPANY","测试",null,null,null,false,Set.of());
        Actor admin=new Actor("ROLE_ADMIN",10L,"ROLE_ADMIN","测试",null,null,null,false,Set.of());
        assertEquals(Set.of("knowledge_lookup","internship_calendar","ask_user","my_permissions","company_profile","company_jobs","company_applications","company_confirmations"),
                new HashSet<>(tools.specs(company).stream().map(AgentReadTools.Spec::name).toList()));
        assertEquals(Set.of("knowledge_lookup","internship_calendar","ask_user","my_permissions","admin_review_queue","admin_overview"),
                new HashSet<>(tools.specs(admin).stream().map(AgentReadTools.Spec::name).toList()));
        assertThrows(AgentException.class,()->tools.execute(company,"student_progress","{}"));
        assertThrows(AgentException.class,()->tools.execute(company,"admin_overview","{}"));
        assertThrows(AgentException.class,()->tools.execute(admin,"company_jobs","{}"));
        assertThrows(AgentException.class,()->tools.execute(student,"company_jobs","{}"));
        assertThrows(AgentException.class,()->tools.execute(student,"admin_review_queue","{}"));
        assertThrows(AgentException.class,()->tools.execute(teacher,"admin_overview","{}"));
    }
    @Test void cursorPaginationPreservesMoreEvenWithoutTotalCount(){
        ToolResult result=AgentReadTools.result("activity","动态",student,List.of(Map.of("id","1")),1,
                Map.of("page",2,"pageSize",1,"hasMore",true),List.of(),List.of());
        assertTrue(result.hasMore());assertEquals(3,result.facts().get("nextPage"));
        assertEquals(1,result.facts().get("returnedCount"));
        assertEquals(false,result.facts().get("totalCountKnown"));
    }
    @Test void requiredToolArgumentsAreRejectedBeforeReads(){
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_details","{}"));
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_details","{\"ids\":null}"));
        assertThrows(AgentException.class,()->tools.execute(student,"jobs_details","{\"ids\":\"1\"}"));
        verifyNoInteractions(positions);
    }
    @Test void catalogReflectsActualRoleTools(){
        when(capabilities.capabilitiesFor(student)).thenReturn(Map.of("agent_chat",true));
        var facts=tools.execute(student,"my_permissions","{}").facts();
        String catalog=tools.serialize(facts.get("availableQueries"));
        assertTrue(catalog.contains("jobs_search"));assertTrue(catalog.contains("parameters"));
        assertFalse(catalog.contains("company_applications"));
        assertThrows(AgentException.class,()->tools.requireAvailable(student,"company_applications"));
        assertFalse(catalog.contains("student_plan"));
        assertThrows(AgentException.class,()->tools.requireAvailable(student,"student_plan"));
        when(capabilities.has(student,"internship_plan")).thenReturn(true);
        assertTrue(tools.specs(student).stream().anyMatch(spec -> spec.name().equals("student_plan")));
    }
    @Test void companyPermissionsDoNotExposeOtherRolesCapabilitiesOrDisabledFeatureLists(){
        Actor company=new Actor("company-7",7L,"ROLE_COMPANY","测试企业",null,null,null,false,Set.of());
        when(capabilities.capabilitiesFor(company)).thenReturn(Map.of(
                "agent_chat",true,"voice_input",false,"form_fill",true,
                "internship_plan",false,"job_apply",false,"teacher_internship_summary",false,
                "auto_task",false,"auto_task_notify",false));
        ToolResult result=tools.execute(company,"my_permissions","{}");
        assertEquals(Map.of("agent_chat",true,"voice_input",false,"form_fill",true),result.facts().get("aiCapabilities"));
        assertEquals(List.of("对话问答","辅助填表"),result.facts().get("enabledFeatures"));
        assertFalse(result.facts().containsKey("disabledFeatures"));
        String serialized=tools.serialize(result);
        for(String unrelated:List.of("teacher_internship_summary","auto_task","job_apply","internship_plan","教师简报","自动任务"))
            assertFalse(serialized.contains(unrelated),unrelated);
        assertTrue(result.warnings().stream().noneMatch(value->value.contains("未开通")));
    }
    @Test void companyProfileUsesAuthenticatedCompanyAndRejectsForeignId(){
        Actor company=new Actor("ROLE_COMPANY",10L,"ROLE_COMPANY","测试",null,null,null,false,Set.of());
        CompanyInfoKnowledgeService source=mock(CompanyInfoKnowledgeService.class);
        tools.setCompanyInfoKnowledge(source);when(source.read(10L)).thenReturn(Map.of("id",10L));
        assertEquals(1,tools.execute(company,"company_profile","{}").total());
        verify(source).read(10L);
        assertThrows(AgentException.class,()->tools.execute(company,"company_profile","{\"companyId\":\"20\"}"));
        verify(source,never()).read(20L);
    }

    @Test void teacherSummaryRequiresTeacherRoleAndCapabilityGate(){
        when(capabilities.has(teacher,"teacher_internship_summary")).thenReturn(false);
        assertEquals("CAPABILITY_DISABLED",assertThrows(AgentException.class,()->tools.execute(teacher,"teacher_internship_summary","{\"scopeType\":\"COLLEGE\",\"view\":\"summary\"}")).getCode());
        when(capabilities.has(teacher,"teacher_internship_summary")).thenReturn(true);
        ToolResult expected=new ToolResult("teacher_internship_summary","学院实习情况简报","OK","now","当前教师授权范围",List.of(),0,false,Map.of(),List.of(),List.of());
        when(summaries.query(eq(teacher),any(),isNull())).thenReturn(expected);
        assertEquals(expected,tools.execute(teacher,"teacher_internship_summary","{\"scopeType\":\"COLLEGE\",\"view\":\"summary\"}"));
        assertThrows(AgentException.class,()->tools.execute(student,"teacher_internship_summary","{\"scopeType\":\"COLLEGE\"}"));
    }

    @Test void teacherTodosUsesOnlyStrictScopedCountersAndDoesNotInventZeroOnFailure(){
        when(mapper.teacherTodoCounts(teacher)).thenReturn(Map.of("responsible",12L,"appraisalSubmitted",10L,"appraisalNotSubmitted",2L,"selfPracticePending",0L,"delayPending",0L,"pendingReflection",0L));
        ToolResult result=tools.execute(teacher,"teacher_todos","{}");
        assertEquals(2L,result.facts().get("appraisalNotSubmitted"));
        assertFalse(result.facts().containsKey("companyQualificationPending"));
        verifyNoInteractions(homeService,ordinary,companyUsers,appraisals);
        when(mapper.teacherTodoCounts(teacher)).thenReturn(null);
        assertEquals("TOOL_UNAVAILABLE",assertThrows(AgentException.class,()->tools.execute(teacher,"teacher_todos","{}")).getCode());
    }

    @Test void foreignCompanyFilterIsRejectedBeforeQueryingApplications(){
        Actor company=new Actor("c1",7L,"ROLE_COMPANY","企业",null,null,null,false,Set.of());
        when(mapper.positionOwnerCompany(20L)).thenReturn(9L);
        assertEquals("OBJECT_UNAVAILABLE",assertThrows(AgentException.class,()->tools.execute(company,"company_applications","{\"positionId\":\"20\"}")).getCode());
        verify(mapper,never()).companyApplications(any(),any(),any(),anyInt(),anyInt());
    }

    @Test void responsibleTeacherComesFromOwnClassAndCurrentAccountWithoutPrivateFields(){
        StudentUser profile=new StudentUser();profile.setId(1L);profile.setClassId(8L);
        when(students.findById(1L)).thenReturn(profile);
        ClassCounselorRelation relation=new ClassCounselorRelation();relation.setCounselorId(7L);relation.setCounselorName("旧名字");
        when(counselorRelations.findByClassId(8L)).thenReturn(List.of(relation,relation));
        TeacherUser teacher=new TeacherUser();teacher.setId(7L);teacher.setName("王老师");teacher.setTeacherType("COUNSELOR");teacher.setStatus(1);teacher.setPhone("private");teacher.setPassword("secret");
        when(teacherUsers.findById(7L)).thenReturn(teacher);
        ToolResult result=tools.execute(student,"student_teachers","{}");
        assertEquals(1L,result.total());assertEquals(Map.of("name","王老师","role","负责辅导员"),result.items().getFirst());
        teacher.setStatus(0);
        assertEquals("NO_ACTIVE_COUNSELOR",tools.execute(student,"student_teachers","{}").facts().get("bindingStatus"));
        profile.setClassId(null);
        assertEquals("NO_CLASS",tools.execute(student,"student_teachers","{}").facts().get("bindingStatus"));
        assertThrows(AgentException.class,()->tools.execute(student,"student_teachers","{\"studentId\":\"2\"}"));
    }

    @Test void expandedProfileProjectsOnlyOwnedBasicFactsAndKeepsMissingFieldsExplicit(){
        when(mapper.studentAdvisorProfile(1L)).thenReturn(Map.of("title","学生一","studentNumber","s001","college","学院","className","一班","password","secret","phone","private"));
        ToolResult result=tools.execute(student,"student_profile","{}");
        assertEquals("s001",result.items().getFirst().get("studentNumber"));
        assertEquals("学院",result.items().getFirst().get("college"));
        assertFalse(result.items().getFirst().containsKey("password"));assertFalse(result.items().getFirst().containsKey("phone"));
        assertTrue(((List<?>)result.facts().get("missingFields")).contains("major"));
        when(mapper.studentAdvisorProfile(1L)).thenReturn(Map.of("title","学生一","className","二班"));
        assertEquals("二班",tools.execute(student,"student_profile","{}").items().getFirst().get("className"));
    }

    @Test void advisorInternshipSeparatesCurrentPlacementFromHistoryAndDoesNotInferDates(){
        when(jobSearchStatuses.resolveForStudentIds(List.of(1L))).thenReturn(Map.of(1L,new JobSearchStatusDTO("IN_PROGRESS","实习中")));
        InternshipStageAssignment old=new InternshipStageAssignment();old.setCompanyName("旧公司");old.setCurrentFlag(0);old.setStatus("INTERRUPTED");
        InternshipStageAssignment current=new InternshipStageAssignment();current.setCompanyName("当前公司");current.setCurrentFlag(1);current.setSourceType("SELF_PRACTICE");
        InternshipStageAssignment pending=new InternshipStageAssignment();pending.setStatus("PENDING_CONFIRMATION");pending.setCurrentFlag(0);pending.setCompanyName("未开始公司");
        when(placements.findByStudentId(1L)).thenReturn(List.of(old,current,pending));
        ToolResult result=tools.execute(student,"student_internship","{}");
        assertEquals(1L,result.total());assertEquals("当前公司",result.items().getFirst().get("company"));
        assertEquals("实习中",result.facts().get("jobSearchStatusText"));
        assertFalse(result.items().getFirst().containsKey("internshipEndTime"));
        assertEquals(1L,tools.execute(student,"student_internship","{\"view\":\"HISTORY\"}").total());
        assertEquals("旧公司",tools.execute(student,"student_internship","{\"view\":\"HISTORY\"}").items().getFirst().get("company"));
        assertThrows(AgentException.class,()->tools.execute(student,"student_internship","{\"studentId\":\"2\"}"));
        when(placements.findByStudentId(1L)).thenReturn(List.of(old));
        assertEquals(0L,tools.execute(student,"student_internship","{}").total());
    }

    @Test void comprehensiveAdvisorContextKeepsOnlyOwnVerifiedSections(){
        when(mapper.studentAdvisorProfile(1L)).thenReturn(Map.of("title","本人","major","软件工程"));
        StudentUser user=new StudentUser();user.setId(1L);when(students.findById(1L)).thenReturn(user);
        when(jobSearchStatuses.resolveForStudentIds(List.of(1L))).thenReturn(Map.of(1L,new JobSearchStatusDTO("UNKNOWN","待核实")));
        when(placements.findByStudentId(1L)).thenReturn(List.of());when(todos.getActionableTodos(1L)).thenReturn(List.of());
        var result=tools.execute(student,"student_advisor_context","{}");
        assertTrue(result.facts().keySet().containsAll(List.of("profile","teachers","internship","todos","calendar")));
        assertThrows(AgentException.class,()->tools.execute(teacher,"student_advisor_context","{}"));
        when(mapper.studentAdvisorProfile(1L)).thenThrow(new IllegalStateException("unavailable"));
        assertThrows(IllegalStateException.class,()->tools.execute(student,"student_advisor_context","{}"));
    }

    @Test void revokedChatBlocksEveryReadBeforeDataAccess(){
        when(capabilities.has(student,"agent_chat")).thenReturn(false);
        assertEquals("CAPABILITY_DISABLED",assertThrows(AgentException.class,()->tools.execute(student,"student_appraisal","{}")).getCode());
        verifyNoInteractions(mapper);
    }

    @Test void studentMaterialQueriesUseOnlyAuthenticatedIdentityAndDistinguishAbsence(){
        when(mapper.studentReflections(1L,0,20)).thenReturn(List.of(Map.of("id","17","submissionStatus","NOT_SUBMITTED")));
        when(mapper.countStudentReflections(1L)).thenReturn(1L);
        assertEquals(1L,tools.execute(student,"student_reflections","{}").total());
        when(mapper.studentAppraisal(1L)).thenReturn(null);
        assertEquals(false,tools.execute(student,"student_appraisal","{}").facts().get("recordFound"));
        when(mapper.studentAppraisal(1L)).thenReturn(Map.of("id","3","status","DRAFT","fileCount",2));
        assertEquals("DRAFT",tools.execute(student,"student_appraisal","{}").items().getFirst().get("status"));
        assertEquals("INVALID_TOOL_INPUT",assertThrows(AgentException.class,()->tools.execute(student,"student_appraisal","{\"studentId\":\"2\"}")).getCode());
        assertThrows(AgentException.class,()->tools.execute(teacher,"student_appraisal","{}"));
        verify(mapper,never()).studentAppraisal(2L);
    }

    @Test void applicationCountCannotIncludeOffersOrOrdinaryApplications(){
        StudentJobApplication entry=new StudentJobApplication();entry.setId(7L);entry.setStudentId(1L);
        when(applications.findByStudentId(1L)).thenReturn(List.of(entry));
        assertEquals(1L,tools.execute(student,"student_applications","{}").total());
        verifyNoInteractions(ordinary,interviews,confirmations);
    }

    @Test void companyAndAdminObjectOwnershipIsVerified(){
        Actor company=new Actor("ROLE_COMPANY",1L,"ROLE_COMPANY","企业1",null,null,null,false,Set.of());
        Actor admin=new Actor("ROLE_ADMIN",10L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
        when(mapper.positionOwnerCompany(24L)).thenReturn(1L);
        when(mapper.positionOwnerCompany(6L)).thenReturn(2L);
        assertDoesNotThrow(()->tools.validateObject(company,new BusinessObject("company_job","24")));
        assertEquals("OBJECT_UNAVAILABLE",assertThrows(AgentException.class,()->tools.validateObject(company,new BusinessObject("company_job","6"))).getCode());
        when(mapper.applicationOwnerCompany(11L)).thenReturn(2L);
        assertEquals("OBJECT_UNAVAILABLE",assertThrows(AgentException.class,()->tools.validateObject(company,new BusinessObject("company_application","11"))).getCode());
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->tools.validateObject(company,new BusinessObject("reflection","3"))).getCode());
        CompanyUser target=new CompanyUser();target.setId(5L);
        when(companies.findById(5L)).thenReturn(target);
        assertDoesNotThrow(()->tools.validateObject(admin,new BusinessObject("admin_company","5")));
        assertEquals("FORBIDDEN",assertThrows(AgentException.class,()->tools.validateObject(company,new BusinessObject("admin_company","5"))).getCode());
    }

    @Test void confirmationObjectIsScopedToTheRequestingSide(){
        Actor company=new Actor("ROLE_COMPANY",1L,"ROLE_COMPANY","企业1",null,null,null,false,Set.of());
        InternshipConfirmationRecord record=new InternshipConfirmationRecord();
        record.setId(12L);record.setCompanyId(1L);record.setStudentId(8L);
        when(confirmations.findById(12L)).thenReturn(record);
        assertDoesNotThrow(()->tools.validateObject(company,new BusinessObject("confirmation","12")));
        record.setCompanyId(2L);
        assertEquals("OBJECT_UNAVAILABLE",assertThrows(AgentException.class,()->tools.validateObject(company,new BusinessObject("confirmation","12"))).getCode());
    }

    @Test void companyQueriesUseVerifiedCompanyIdAndValidatePaging(){
        Actor company=new Actor("ROLE_COMPANY",1L,"ROLE_COMPANY","企业1",null,null,null,false,Set.of());
        when(mapper.companyJobs(1L,"active",0,10)).thenReturn(List.of());
        when(mapper.countCompanyJobs(1L,"active")).thenReturn(7L);
        ToolResult jobs=tools.execute(company,"company_jobs","{\"status\":\"active\"}");
        assertEquals(7,jobs.total());
        verify(mapper).companyJobs(1L,"active",0,10);
        assertThrows(AgentException.class,()->tools.execute(company,"company_jobs","{\"page\":0}"));
        assertThrows(AgentException.class,()->tools.execute(company,"company_jobs","{\"companyId\":\"2\"}"));
        when(mapper.companyApplications(1L,null,null,10,10)).thenReturn(List.of());
        assertEquals(0,tools.execute(company,"company_applications","{\"page\":2}").total());
        verify(mapper).companyApplications(1L,null,null,10,10);
    }

    @Test void studentPlanRequiresCapabilityAndReturnsSnapshot(){
        when(capabilities.has(student,"internship_plan")).thenReturn(false);
        assertEquals("CAPABILITY_DISABLED",assertThrows(AgentException.class,()->tools.execute(student,"student_plan","{}")).getCode());
        when(capabilities.has(student,"internship_plan")).thenReturn(true);
        when(plans.refresh(student)).thenReturn(new StudentPlanService.PlanView(
                "SEARCH","寻找实习",
                List.of(new StudentPlanService.PlanItem("apply","投递岗位","TODO","去投递","OPEN_JOBS")),
                "投递岗位","去投递",2,true));
        when(plans.factsOf(any())).thenReturn(Map.of("stage","SEARCH"));
        ToolResult result=tools.execute(student,"student_plan","{}");
        assertEquals("student_plan",result.tool());
        assertEquals(1,result.total());
        assertEquals("TODO",result.items().getFirst().get("state"));
    }
}
