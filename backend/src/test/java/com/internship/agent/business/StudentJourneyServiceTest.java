package com.internship.agent.business;

import com.internship.mapper.StudentJourneyPreferenceMapper;
import com.internship.service.InternshipCalendarService;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class StudentJourneyServiceTest {
    private final LocalDate today = LocalDate.of(2026, 9, 21);
    private StudentPlanService.JourneyBasis basis(boolean history, boolean established, boolean self, boolean selfPending,
                                                 boolean delayPending, boolean confirming, boolean passed, boolean submitted) {
        return new StudentPlanService.JourneyBasis(true, history, true, established, self, selfPending,
                delayPending, confirming, passed, submitted, false, false);
    }
    private StudentPlanService.PlanView plan(String stage, StudentPlanService.JourneyBasis b) {
        return new StudentPlanService.PlanView(stage, stage, List.of(), "", "", 0, true,
                StudentPlanService.SnapshotStatus.SAVED, Map.of(), b);
    }
    private StudentJourneyService.Journey project(String stage, StudentPlanService.JourneyBasis b, String choice) {
        return StudentJourneyService.project(plan(stage, b), choice == null ? null : Map.of("routeChoice", choice, "seen", true, "version", 2),
                false, "2026-12-31", "2027-01-31", true, today);
    }
    @Test void freshStudentGetsChoiceNotFalseUnknown() {
        var p = project("UNKNOWN", basis(false,false,false,false,false,false,false,false), null);
        assertTrue(p.autoOpen()); assertTrue(p.needsChoice()); assertEquals(0,p.currentIndex());
    }
    @Test void dismissalPreventsRepeatedAutoOpenButKeepsChoice() {
        var p = StudentJourneyService.project(plan("SEARCH", basis(false,false,false,false,false,false,false,false)),
                Map.of("seen", true), false, null,null,true,today);
        assertFalse(p.autoOpen()); assertTrue(p.needsChoice());
    }
    @Test void normalRouteHasSixStages() {
        var p=project("SEARCH",basis(false,false,false,false,false,false,false,false),"NORMAL");
        assertEquals(6,p.total()); assertEquals(2,p.currentIndex()); assertEquals("寻找岗位与面试",p.title());
        assertTrue(p.canChoose());
    }
    @Test void interviewPassedGoesStraightToConfirmation() {
        var p=project("CONFIRM",basis(true,false,false,false,false,false,true,false),"NORMAL");
        assertEquals(3,p.currentIndex()); assertEquals("确认实习",p.title()); assertFalse(p.canChoose());
    }
    @Test void confirmedPendingCompanyIsWaiting() {
        var p=project("CONFIRM",basis(true,false,false,false,false,true,true,false),"NORMAL");
        assertEquals("WAITING",p.steps().get(2).state());
    }
    @Test void activeInternshipIgnoresAllReplacementRequests() {
        var p=project("IN_PROGRESS",basis(true,true,false,true,true,true,true,false),"SELF");
        assertEquals("NORMAL",p.route()); assertEquals("正式实习",p.title()); assertEquals(4,p.currentIndex());
        assertFalse(p.canChoose()); assertEquals(6,p.steps().size());
        assertTrue(p.steps().stream().noneMatch(s->s.title().contains("新岗位")));
    }
    @Test void selfApprovedSkipsInterviewAndEnterpriseConfirmation() {
        var p=project("IN_PROGRESS",basis(true,true,true,false,false,true,true,false),"NORMAL");
        assertEquals("SELF",p.route()); assertEquals(3,p.currentIndex()); assertEquals(5,p.total());
        assertTrue(p.steps().stream().noneMatch(s->s.key().equals("confirmation")));
    }
    @Test void selfPendingLocksChoiceAndWaitsForReview() {
        var p=project("SEARCH",basis(true,false,false,true,false,false,false,false),null);
        assertEquals("SELF",p.route()); assertEquals("WAITING",p.steps().get(1).state()); assertFalse(p.canChoose());
    }
    @Test void cooperativeSelfPracticeWaitsForCompanyWithoutAdvancingToFormalInternship() {
        var b = new StudentPlanService.JourneyBasis(true, true, true, false, false, true,
                false, true, false, false, false, false, "SELF", "pending", "", "COMPANY");
        var p = project("CONFIRM", b, null);
        assertEquals("SELF", p.route());
        assertEquals(2, p.currentIndex());
        assertEquals("等待企业确认", p.title());
        assertTrue(p.steps().get(1).detail().contains("等待企业确认"));
        assertEquals("PENDING", p.steps().get(2).state());
    }
    @Test void rejectedSelfPracticeShowsTeacherReasonAndResubmission() {
        var b = new StudentPlanService.JourneyBasis(true, true, true, false, false, false,
                false, false, false, false, false, false, "SELF", "rejected", "材料不完整");
        var p = project("SEARCH", b, null);
        var step = p.steps().get(1);
        assertEquals("SELF", p.route());
        assertEquals("自主实习申请已驳回", p.title());
        assertEquals("REJECTED", step.state());
        assertTrue(step.detail().contains("材料不完整"));
        assertEquals("OPEN_SELF_PRACTICE", step.action());
    }

    @Test void establishedSelfInternshipTitleIgnoresOldApplicationOutcomes() {
        for (String applicationStatus : List.of("rejected", "withdrawn", "pending")) {
            var b = new StudentPlanService.JourneyBasis(true, true, true, true, true, "pending".equals(applicationStatus),
                    false, false, false, false, false, false, "SELF", applicationStatus, "历史申请理由");
            var active = project("IN_PROGRESS", b, null);
            assertEquals("正式实习", active.title(), applicationStatus);
            assertEquals(3, active.currentIndex());
            assertEquals("DONE", active.steps().get(1).state());
            assertEquals("正式实习 · 待开始", project("ONBOARD", b, null).title());
            assertEquals("提交实习鉴定表", project("FINISHED", b, null).title());
        }
    }

    @Test void withdrawnSelfPracticeShowsResubmission() {
        var b = new StudentPlanService.JourneyBasis(true, true, true, false, false, false,
                false, false, false, false, false, false, "SELF", "withdrawn", "");
        var p = project("SEARCH", b, null);
        var step = p.steps().get(1);
        assertEquals("自主实习申请已撤回", p.title());
        assertEquals("WITHDRAWN", step.state());
        assertEquals("OPEN_SELF_PRACTICE", step.action());
    }
    @Test void delayIsAnArrangementNotACompletedRoute() {
        var p=project("DELAYED",basis(true,false,false,false,false,false,false,false),"NORMAL");
        assertEquals("延期中",p.title()); assertEquals(0,p.total()); assertFalse(p.canChoose());
    }
    @Test void delayPreferenceCanBeChangedBeforeSubmission() {
        var p=project("SEARCH",basis(false,false,false,false,false,false,false,false),"DELAY");
        assertTrue(p.canChoose()); assertEquals("OPEN_DELAY",p.steps().get(0).action());
    }
    @Test void endedButMissingAppraisalDoesNotComplete() {
        var p=project("FINISHED",basis(true,true,true,false,false,false,false,false),null);
        assertEquals(4,p.currentIndex()); assertEquals("提交实习鉴定表",p.title());
        assertEquals("PENDING",p.steps().get(4).state());
        assertFalse(p.steps().get(4).detail().contains("已正式提交"));
    }
    @Test void endedAndSubmittedCompletesAllStages() {
        var p=project("FINISHED",basis(true,true,true,false,false,false,false,true),null);
        assertEquals("实习流程已完成",p.title()); assertTrue(p.steps().stream().allMatch(s->s.state().equals("DONE")));
    }
    @Test void hiddenReflectionsNeverAppear() {
        var p=project("IN_PROGRESS",basis(true,true,false,false,false,false,false,false),null);
        assertTrue(p.steps().stream().noneMatch(s->s.title().contains("心得") || s.detail().contains("心得") || s.meaning().contains("心得") || s.nextAction().contains("心得") || s.completionCriteria().contains("心得")));
    }
    @Test void enabledReflectionIsInsideFormalStageOnly() {
        var b=new StudentPlanService.JourneyBasis(true,true,true,true,false,false,false,false,false,false,true,true);
        var p=project("IN_PROGRESS",b,null);
        assertEquals(6,p.total()); assertEquals("OPEN_REFLECTIONS",p.steps().get(3).action());
    }
    @Test void calendarWindowPreventsEarlyOrLateSubmissionLink() {
        var b=basis(true,true,false,false,false,false,false,false);
        var early=project("FINISHED",b,null);
        assertEquals("",early.steps().get(4).action());
        var late=StudentJourneyService.project(plan("FINISHED",b),null,false,"2026-01-01","2026-02-01",true,today);
        assertEquals("",late.steps().get(4).action()); assertTrue(late.steps().get(4).detail().contains("截止"));
    }
    @Test void dataFailureCannotUnlockOrComplete() {
        var b=new StudentPlanService.JourneyBasis(false,true,true,true,false,false,false,false,false,true,false,false);
        var p=project("FINISHED",b,null);
        assertFalse(p.reliable()); assertFalse(p.canChoose()); assertEquals(0,p.currentIndex());
    }
    @Test void interruptedDoesNotResetToApplicationStage() {
        var p=project("INTERRUPTED",basis(true,false,false,false,false,false,false,false),null);
        assertEquals("实习已中断",p.title()); assertEquals(4,p.currentIndex()); assertFalse(p.canChoose());
    }
    @Test void authorizationAndOptimisticVersionAreServerEnforced() {
        var plans=mock(StudentPlanService.class);var prefs=mock(StudentJourneyPreferenceMapper.class);
        var caps=mock(AgentCapabilityService.class);var gate=mock(AgentFeatureGate.class);
        var svc=new StudentJourneyService(plans,prefs,caps,gate,mock(InternshipCalendarService.class));
        var student=new AgentTypes.Actor("student:8",8L,"ROLE_STUDENT","",null,null,null,false,Set.of());
        assertEquals(403,assertThrows(AgentException.class,()->svc.current(student)).getHttpStatus());
        verifyNoInteractions(plans,prefs);
        when(caps.has(eq(student),anyString())).thenReturn(true);
        when(prefs.lock(8L)).thenReturn(Map.of("version",3));
        assertEquals(409,assertThrows(AgentException.class,()->svc.save(student,new StudentJourneyService.PreferenceRequest("SELF",2))).getHttpStatus());
        verify(prefs,never()).update(anyLong(),any(),anyLong());
    }
    @Test void businessChangeBlocksStaleRouteChoiceEvenWithCurrentPreferenceVersion() {
        var plans=mock(StudentPlanService.class);var prefs=mock(StudentJourneyPreferenceMapper.class);var caps=mock(AgentCapabilityService.class);
        var svc=new StudentJourneyService(plans,prefs,caps,mock(AgentFeatureGate.class),mock(InternshipCalendarService.class));
        var student=new AgentTypes.Actor("student:8",8L,"ROLE_STUDENT","",null,null,null,false,Set.of());
        when(caps.has(eq(student),anyString())).thenReturn(true);when(prefs.lock(8L)).thenReturn(Map.of("version",0));
        when(plans.refresh(student)).thenReturn(plan("IN_PROGRESS",basis(true,true,false,false,false,false,false,false)));
        assertEquals("JOURNEY_LOCKED",assertThrows(AgentException.class,()->svc.save(student,new StudentJourneyService.PreferenceRequest("SELF",0))).getCode());
        verify(prefs,never()).update(anyLong(),any(),anyLong());
    }
    @Test void phaseQuestionsRequireFreshPlanEvenWhenAskingHow() {
        for(String q:List.of("我到哪个阶段了", "这一步为什么没完成", "完善个人资料是什么意思", "下一步怎么办", "实习流程有哪些阶段"))
            assertTrue(AgentTaskService.isJourneyQuestion(q),q);
        assertFalse(AgentTaskService.isJourneyQuestion("谁是我的辅导员"));
    }
    @Test void explanationsDistinguishWaitingFromStudentAction() {
        var p=project("CONFIRM",basis(true,false,false,false,false,true,true,false),"NORMAL");
        var step=p.steps().stream().filter(s->s.key().equals("confirmation")).findFirst().orElseThrow();
        assertEquals("WAITING_OTHER",step.handling());
        assertTrue(step.completionCriteria().contains("企业确认"));
        assertTrue(step.remainingReasons().getFirst().contains("等待企业"));
        assertFalse(step.meaning().isBlank());
    }
    @Test void missingProfileNamesAreSharedWithSidebarAndNeverMarkedDone() {
        var b=new StudentPlanService.JourneyBasis(true,true,false,true,false,false,false,false,false,true,false,false);
        var base=plan("FINISHED",b);
        var enriched=new StudentPlanService.PlanView(base.stage(),base.stageLabel(),List.of(new StudentPlanService.PlanItem("profile","完善个人资料","TODO","请到个人中心补齐：邮箱。","OPEN_PROFILE")),"","",0,true,StudentPlanService.SnapshotStatus.SAVED,Map.of(),b);
        var p=StudentJourneyService.project(enriched,null,false,"2026-01-01","2026-02-01",true,today);
        var profile=p.steps().getFirst();
        assertEquals("TODO",profile.state());assertTrue(profile.detail().contains("邮箱"));
        assertEquals(profile.detail(),profile.nextAction());
        var appraisal=p.steps().stream().filter(s->s.key().equals("appraisal")).findFirst().orElseThrow();
        assertEquals("DONE",appraisal.state());assertTrue(appraisal.remainingReasons().isEmpty());
    }
}
