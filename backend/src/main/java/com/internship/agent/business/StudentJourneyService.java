package com.internship.agent.business;

import com.internship.mapper.StudentJourneyPreferenceMapper;
import com.internship.service.InternshipCalendarService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import static com.internship.agent.business.AgentTypes.Actor;

/** A deterministic, student-owned view of the whole internship, independent of chat sessions. */
@Service
@RequiredArgsConstructor
public class StudentJourneyService {
    private final StudentPlanService plans;
    private final StudentJourneyPreferenceMapper preferences;
    private final AgentCapabilityService capabilities;
    private final AgentFeatureGate featureGate;
    private final InternshipCalendarService calendars;

    public record Step(String key, String title, String state, String detail, String action,
                       String meaning, String completionCriteria, List<String> remainingReasons,
                       String nextAction, String handling) {
        public Step(String key,String title,String state,String detail,String action) {
            this(key,title,state,detail,action,meaningOf(key,title),criteriaOf(key,title),
                    "DONE".equals(state)?List.of():List.of("PENDING".equals(state)?"前置阶段尚未完成。":detail),
                    "DONE".equals(state)?"本阶段已完成，无需重复办理。":"PENDING".equals(state)?"先完成当前阶段，再办理本阶段。":detail,
                    "DONE".equals(state)?"DONE":"PENDING".equals(state)?"WAITING_PREVIOUS":
                    "WAITING".equals(state)?"WAITING_OTHER":action.isBlank()?"WAITING_TIME_OR_ARRANGEMENT":"ACTION_REQUIRED");
        }
    }
    private static String meaningOf(String key,String title) {
        return switch(key) {
            case "profile" -> "补齐联系和身份资料，便于实习办理与教师联系。";
            case "placement" -> title.contains("自主")?"提交已经自行落实的实习单位，由合作企业确认或辅导员审核。":"申请系统岗位并参加企业面试，取得可确认的实习机会。";
            case "confirmation" -> "将通过面试或直接录取的岗位办理为正式实习。";
            case "internship" -> "按教师统一安排开展实习。";
            case "appraisal" -> "上传实习鉴定材料，完成页面要求后正式提交。";
            case "complete" -> "核对本次实习流程的最终收尾结果。";
            case "delay" -> "申请考研延期，按审批结果安排后续实习。";
            default -> "按当前实习安排办理。";
        };
    }
    private static String criteriaOf(String key,String title) {
        return switch(key) {
            case "profile" -> "姓名、学号、电话和邮箱均已填写。";
            case "placement" -> title.contains("自主")?"自主实习申请通过并落实实习经历。":"存在当前有效的面试通过或直接录取结果。仅投递或收到面试邀请不算完成。";
            case "confirmation" -> "学生提交实习确认表，企业确认通过。";
            case "internship" -> "已达到学院本次实习统一结束时间，且系统状态为“实习已结束”。";
            case "appraisal" -> "实习鉴定表已正式提交。仅上传附件或保存草稿不算完成。";
            case "complete" -> "学院本次实习已统一结束，且鉴定表已正式提交。";
            case "delay" -> "延期审批和恢复安排以真实业务记录为准，获准延期不代表整个实习已完成。";
            default -> "以最新业务记录为准。";
        };
    }
    public record Journey(String route, String title, int currentIndex, int total, List<Step> steps,
                          boolean canChoose, boolean needsChoice, boolean autoOpen, boolean reliable,
                          long preferenceVersion, long observedAt) {}
    public record PreferenceRequest(String choice, long expectedVersion) {}

    private void authorize(Actor actor, boolean write) {
        featureGate.requireEnabled();
        if (!"ROLE_STUDENT".equals(actor.role()) || !capabilities.has(actor, "internship_plan")
                || !capabilities.has(actor, "agent_chat"))
            throw new AgentException("CAPABILITY_DISABLED", "当前身份未开通实习规划能力。", 403);
        if (write && actor.readOnly()) throw new AgentException("READ_ONLY", "当前为只读身份。", 403);
    }

    public Journey current(Actor actor) {
        authorize(actor, false);
        return render(plans.refresh(actor), preferences.find(actor.id()), actor.readOnly());
    }

    public Journey forPlan(Actor actor, StudentPlanService.PlanView plan) {
        authorize(actor, false);
        return render(plan, preferences.find(actor.id()), actor.readOnly());
    }

    @Transactional
    public Journey save(Actor actor, PreferenceRequest request) {
        authorize(actor, true);
        if (request == null || request.expectedVersion() < 0 || (request.choice() != null
                && !Set.of("NORMAL", "SELF", "DELAY").contains(request.choice())))
            throw new AgentException("INVALID_CHOICE", "请选择有效的办理方式。", 400);
        preferences.ensure(actor.id());
        Map<String, Object> pref = preferences.lock(actor.id());
        long version = number(pref, "version");
        if (version != request.expectedVersion())
            throw new AgentException("JOURNEY_CONFLICT", "办理指引已更新，请核对最新进度。", 409);
        var plan = plans.refresh(actor);
        Journey before = render(plan, pref, false);
        if (!before.reliable()) throw new AgentException("JOURNEY_UNAVAILABLE", "暂时无法核实实习进度，请稍后重试。", 503);
        String choice = request.choice() == null ? Objects.toString(pref.get("routeChoice"), null) : request.choice();
        if (request.choice() != null && !before.canChoose())
            throw new AgentException("JOURNEY_LOCKED", "已有办理中的申请或实习，请按当前业务流程调整。", 409);
        if (preferences.update(actor.id(), choice, version) != 1)
            throw new AgentException("JOURNEY_CONFLICT", "办理指引已更新，请核对最新进度。", 409);
        return render(plan, preferences.find(actor.id()), false);
    }

    private Journey render(StudentPlanService.PlanView plan, Map<String, Object> pref, boolean readOnly) {
        String end = null, deadline = null;
        boolean calendarReliable = true;
        try {
            var calendar = calendars.getCalendar();
            if (calendar != null) { end = calendar.getEndDate(); deadline = calendar.getAppraisalDeadline(); }
        } catch (RuntimeException e) { calendarReliable = false; }
        return project(plan, pref, readOnly, end, deadline, calendarReliable, LocalDate.now(ZoneId.of("Asia/Shanghai")));
    }

    static Journey project(StudentPlanService.PlanView plan, Map<String, Object> pref, boolean readOnly,
                           String end, String deadline, boolean calendarReliable, LocalDate today) {
        var b = plan.journeyBasis();
        String choice = pref == null ? "" : Objects.toString(pref.get("routeChoice"), "");
        long version = number(pref, "version");
        boolean seen = pref != null && (Boolean.TRUE.equals(pref.get("seen")) || number(pref, "seen") == 1);
        if (b == null || !b.reliable() || !calendarReliable)
            return new Journey("UNKNOWN", "进度待核实", 0, 0, List.of(), false, false, false, false, version, System.currentTimeMillis());
        String stage = plan.stage();
        boolean delayed = "DELAYED".equals(stage), interrupted = "INTERRUPTED".equals(stage);
        boolean locked = b.established() || b.selfPending() || b.delayPending() || b.confirming() || b.passed() || delayed || interrupted;
        boolean canChoose = !locked && !readOnly;
        String observedRoute = Objects.toString(b.observedRoute(), "").trim();
        boolean needsChoice = observedRoute.isEmpty() && choice.isEmpty() && canChoose;
        boolean autoOpen = needsChoice && !seen;
        if (choice.isEmpty()) choice = observedRoute;
        // Actual placements override pending replacement applications and guidance preferences.
        boolean self = b.established() || interrupted ? b.selfPractice() : b.selfPending() || (!b.passed() && !b.confirming() && "SELF".equals(choice));
        boolean delay = delayed || (!b.established() && (b.delayPending() || "DELAY".equals(choice)));
        int total = self ? 5 : 6;
        if (needsChoice) return new Journey("UNSELECTED", "选择实习安排", 0, 0, List.of(), canChoose, true, autoOpen, true, version, System.currentTimeMillis());
        if (delay) {
            String title = delayed ? "延期中" : b.delayPending() ? "延期申请审核中" : "准备延期申请";
            return new Journey("DELAY", title, 0, 0, List.of(new Step("delay", "考研延期", delayed || b.delayPending() ? "WAITING" : "CURRENT",
                    delayed ? "按已批准的安排办理后续实习。" : b.delayPending() ? "申请已提交，等待审核。" : "提交申请和所需材料。",
                    delayed || b.delayPending() ? "OPEN_APPLICATION" : "OPEN_DELAY")), canChoose, false, false, true, version, System.currentTimeMillis());
        }
        int formal = self ? 3 : 4, appraisal = total - 1;
        boolean ended = "FINISHED".equals(stage), complete = ended && b.appraisalSubmitted();
        int index = complete ? total : ended ? appraisal : b.established() || interrupted ? formal
                : b.selfPending() ? 2 : b.passed() || b.confirming() ? 3 : !b.profileDone() ? 1 : 2;
        if ("UNKNOWN".equals(stage) && b.hasHistory())
            return new Journey(self ? "SELF" : "NORMAL", "进度待核实", 0, total, List.of(), false, false, false, false, version, System.currentTimeMillis());
        List<Step> steps = new ArrayList<>();
        String profileDetail = plan.items().stream().filter(item -> "profile".equals(item.key())).map(StudentPlanService.PlanItem::detail).findFirst().orElse(b.profileDone()?"必要资料已完善。":"补齐姓名、学号、电话和邮箱。");
        steps.add(new Step("profile", "完善资料", b.profileDone()?"DONE":index==1?"CURRENT":"TODO", profileDetail, "OPEN_PROFILE"));
        if (!b.profileDone() && index > 1) steps.set(0, new Step("profile", "完善资料", "TODO", profileDetail, "OPEN_PROFILE"));
        String selfApplicationStatus = Objects.toString(b.selfApplicationStatus(), "").trim();
        String selfRejectReason = Objects.toString(b.selfRejectReason(), "").trim();
        boolean companyReview = "COMPANY".equals(b.selfReviewTarget());
        String selfHandler = companyReview ? "企业确认" : "辅导员审核";
        steps.add(step("placement", self ? "自主实习申请" : "寻找岗位与面试", 2, index, complete,
                index > 2 ? self ? "自主实习已落实。" : "已通过面试或直接录取。"
                : self ? b.selfPending() ? "申请已提交，等待" + selfHandler + "。" : "提交自主实习申请和所需材料。"
                        : "申请岗位、参加面试；面试通过或直接录取后办理实习确认。",
                self ? b.selfPending() ? "OPEN_APPLICATION" : "OPEN_SELF_PRACTICE" : "OPEN_JOBS"));
        if (self && index == 2 && (b.selfPending() || Set.of("pending", "rejected", "withdrawn").contains(selfApplicationStatus))) {
            String state = "rejected".equals(selfApplicationStatus) ? "REJECTED"
                    : "withdrawn".equals(selfApplicationStatus) ? "WITHDRAWN" : "WAITING";
            String detail = "rejected".equals(selfApplicationStatus)
                    ? "自主实习申请已驳回。" + (companyReview ? "企业" : "辅导员") + "填写的理由：" + (selfRejectReason.isBlank() ? "未填写" : selfRejectReason) + "。可修改后重新提交。"
                    : "withdrawn".equals(selfApplicationStatus)
                    ? "你已撤回自主实习申请，可重新填写并提交。"
                    : "申请已提交，等待" + selfHandler + "；可撤回后重新提交。";
            String action = b.selfPending() || "pending".equals(selfApplicationStatus) ? "OPEN_APPLICATION" : "OPEN_SELF_PRACTICE";
            steps.set(1, new Step("placement", "自主实习申请", state, detail, action));
        }
        if (!self) steps.add(step("confirmation", "确认实习", 3, index, complete,
                index > 3 ? "实习已确认。" : b.confirming() ? "确认表已提交，等待企业确认。" : "面试通过或直接录取后，提交实习确认表。", "OPEN_CONFIRMATION"));
        if (!self && b.confirming() && index == 3) steps.set(2, new Step("confirmation", "确认实习", "WAITING", "确认表已提交，等待企业确认。", "OPEN_CONFIRMATION"));
        String formalDetail = interrupted ? "实习已中断，请联系负责老师核对后续安排。"
                : "ONBOARD".equals(stage) ? "实习已确认，等待开始。"
                : end == null || end.isBlank() ? "实习结束时间待教师设置。" : "统一结束时间：" + end + "。";
        if (b.reflectionEnabled()) formalDetail += b.reflectionDue() ? "有实习心得待提交。" : "按要求提交实习心得。";
        Step formalStep=step("internship", "正式实习", formal, index, complete, formalDetail,
                b.reflectionEnabled() && b.reflectionDue() && !interrupted ? "OPEN_REFLECTIONS" : "");
        if ("CURRENT".equals(formalStep.state())) {
            String reason=interrupted?"实习已中断，后续安排尚需核对。":"ONBOARD".equals(stage)?"实习已确认，但尚未进入正式开始状态。":"当前实习业务状态仍未结束。";
            String next=interrupted?"联系负责老师核对恢复或后续实习安排。":"ONBOARD".equals(stage)?"按教师统一开始安排准备实习。":"继续按教师统一安排实习，结束后核对系统状态。";
            if(b.reflectionEnabled() && b.reflectionDue()) next += "前往实习心得页面完成当前待提交事项。";
            formalStep=new Step(formalStep.key(),formalStep.title(),formalStep.state(),formalStep.detail(),formalStep.action(),
                    formalStep.meaning(),formalStep.completionCriteria(),List.of(reason,formalDetail),next,
                    interrupted?"CONTACT_TEACHER":b.reflectionEnabled()&&b.reflectionDue()?"ACTION_REQUIRED":"WAITING_TIME_OR_ARRANGEMENT");
        }
        steps.add(formalStep);
        String appraisalDetail = b.appraisalSubmitted() ? "鉴定表已正式提交。" : "实习结束后上传鉴定表并正式提交。";
        String appraisalAction = "OPEN_APPRAISAL";
        try {
            if (end == null || end.isBlank() || deadline == null || deadline.isBlank()) {
                appraisalDetail = "鉴定提交时间尚未完整设置，请联系负责老师。"; appraisalAction = "";
            } else if (today.isBefore(LocalDate.parse(end))) {
                appraisalDetail = end + "起可提交，截止" + deadline + "。"; appraisalAction = "";
            } else if (today.isAfter(LocalDate.parse(deadline)) && !b.appraisalSubmitted()) {
                appraisalDetail = "提交已截止，请联系负责老师。"; appraisalAction = "";
            } else if (!b.appraisalSubmitted()) appraisalDetail += "截止" + deadline + "。";
        } catch (RuntimeException e) { appraisalDetail = "提交时间待核实，请联系负责老师。"; appraisalAction = ""; }
        steps.add(new Step("appraisal", "提交实习鉴定表", b.appraisalSubmitted()?"DONE":index==appraisal?"CURRENT":"PENDING", b.appraisalSubmitted()?"鉴定表已正式提交。":appraisalDetail, appraisalAction));
        steps.add(step("complete", "完成", total, index, complete, complete ? "实习已结束，鉴定表已正式提交。" : "实习结束且鉴定表正式提交后完成。", ""));
        String title = complete ? "实习流程已完成" : interrupted ? "实习已中断"
                : self && index == 2 && "rejected".equals(selfApplicationStatus) ? "自主实习申请已驳回"
                : self && index == 2 && "withdrawn".equals(selfApplicationStatus) ? "自主实习申请已撤回"
                : self && index == 2 && (b.selfPending() || "pending".equals(selfApplicationStatus)) ? "等待" + selfHandler
                : "ONBOARD".equals(stage) ? "正式实习 · 待开始" : steps.get(index - 1).title();
        return new Journey(self ? "SELF" : "NORMAL", title, index, total, List.copyOf(steps), canChoose, false, false, true, version, System.currentTimeMillis());
    }

    private static Step step(String key, String title, int index, int current, boolean complete, String detail, String action) {
        return new Step(key, title, complete || index < current ? "DONE" : index == current ? "CURRENT" : "PENDING", detail, action);
    }
    private static long number(Map<String, Object> map, String key) {
        return map != null && map.get(key) instanceof Number n ? n.longValue() : 0;
    }
}
