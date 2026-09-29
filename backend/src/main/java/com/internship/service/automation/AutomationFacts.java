package com.internship.service.automation;

import com.internship.service.*;
import com.internship.entity.dto.InternshipCalendar;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Batches only already-authorized IDs. No credentials, contact details or free-text submissions go to the model. */
@Service
public class AutomationFacts {
    private final NamedParameterJdbcTemplate jdbc;
    private final JobSearchStatusService statuses;
    private final InternshipCalendarService calendars;
    private final StudentHomeTodoService todos;
    public AutomationFacts(NamedParameterJdbcTemplate jdbc, JobSearchStatusService statuses, InternshipCalendarService calendars, StudentHomeTodoService todos) {
        this.jdbc=jdbc; this.statuses=statuses; this.calendars=calendars; this.todos=todos;
    }
    public InternshipCalendar calendar() { return calendars.getCalendar(); }
    public Map<Long, Map<String,Object>> load(List<Long> ids, LocalDateTime now) {
        if (ids.isEmpty()) return Map.of();
        Map<String,Object> args = Map.of("ids", ids);
        Map<Long, Map<String,Object>> result = new LinkedHashMap<>();
        var states = statuses.resolveForStudentIds(ids);
        InternshipCalendar calendar = calendars.getCalendar();
        for (var row : jdbc.queryForList("SELECT s.id,s.username,s.name,s.grade,s.class_id,s.major_id,s.email,c.name AS class_name,m.name AS major_name,d.name AS division_name,dep.name AS department_name FROM student_users s LEFT JOIN class c ON c.id=s.class_id LEFT JOIN major m ON m.id=COALESCE(s.major_id,c.major_id) LEFT JOIN division d ON d.id=m.division_id LEFT JOIN department dep ON dep.id=COALESCE(m.department_id,d.department_id) WHERE s.status=1 AND s.id IN (:ids)",args)) {
            long id = number(row.get("id")); Map<String,Object> f = new LinkedHashMap<>();
            f.put("id",id); f.put("name", row.get("name")); f.put("email",row.get("email"));
            f.put("student.username",row.get("username")); f.put("student.name",row.get("name"));
            f.put("student.grade",row.get("grade"));f.put("student.classId",row.get("class_id"));f.put("student.majorId",row.get("major_id"));
            f.put("student.className",row.get("class_name"));f.put("student.majorName",row.get("major_name"));f.put("student.divisionName",row.get("division_name"));f.put("student.departmentName",row.get("department_name"));
            if (states.containsKey(id)) f.put("internship.status",states.get(id).jobSearchStatus());
            f.put("internship.companyId", null); f.put("internship.positionId", null); f.put("internship.source", null);
            f.put("internship.companyName",null);f.put("internship.positionName",null);
            for (String source : AutomationRules.SOURCES) f.put(source,new ArrayList<Map<String,Object>>());
            f.put("appraisal.status","MISSING"); f.put("appraisal.required",false); f.put("appraisal.canSubmit",false);
            if (calendar != null) {
                f.put("calendar.startDate",calendar.getStartDate()); f.put("calendar.endDate",calendar.getEndDate()); f.put("appraisal.deadline",calendar.getAppraisalDeadline());
            }
            result.put(id,f);
        }
        add(result, "applications", "SELECT student_id,id,status,company_id,position_id,company_name,position_name,apply_date AS date FROM student_job_application WHERE student_id IN (:ids) ORDER BY id",args);
        add(result, "interviews", "SELECT student_id,id,status,company_id,position_id,company_name,position_name,interview_time AS date FROM interview_invitation WHERE student_id IN (:ids) ORDER BY id",args);
        add(result, "confirmations", "SELECT student_id,id,status,recall_status,company_id,position_id,company_name,position_name,create_time AS date FROM internship_confirmation_record WHERE student_id IN (:ids) ORDER BY id",args);
        add(result, "requests", "SELECT student_id,id,status,application_type AS type,company AS company_name,position_name,apply_time AS date FROM student_application WHERE student_id IN (:ids) ORDER BY id",args);
        add(result, "reflections", "SELECT student_id,id,CASE WHEN remark='0' THEN 'DRAFT' WHEN remark='1' THEN 'REVIEWED' ELSE 'SUBMITTED' END AS status,period_number,deadline AS due_date,submit_time AS date FROM internship_reflection WHERE COALESCE(deleted,0)=0 AND student_id IN (:ids) ORDER BY id",args);
        for (var row : jdbc.queryForList("SELECT student_id,id,status FROM internship_appraisal_submission WHERE student_id IN (:ids)",args)) {
            Map<String,Object> f=result.get(number(row.get("student_id"))); if(f!=null) { f.put("appraisal.status",row.get("status")); f.put("appraisal.id",row.get("id")); }
        }
        Map<Long,List<Map<String,Object>>> placements=new HashMap<>();
        for (var row : jdbc.queryForList("SELECT student_id,id,company_id,position_id,company_name,position_name,source_type,status,current_flag,internship_start_time,internship_end_time FROM internship_stage_assignment WHERE student_id IN (:ids) ORDER BY id",args)) {
            Map<String,Object> f=result.get(number(row.get("student_id"))); if(f==null)continue;
            placements.computeIfAbsent(number(row.get("student_id")),key->new ArrayList<>()).add(row);
            if(AutomationService.number(row.get("current_flag"))==1) {
                f.put("internship.companyId",row.get("company_id"));f.put("internship.positionId",row.get("position_id"));f.put("internship.source",row.get("source_type"));
                f.put("internship.companyName",row.get("company_name"));f.put("internship.positionName",row.get("position_name"));
            }
        }
        // Actionable eligibility comes from the same domain service as the student page.
        // These bounded per-student checks include menu availability, placement evidence and calendar validation.
        for (var entry : result.entrySet()) {
            Map<String,Object> f=entry.getValue();
            addRequiredPeriods(f,placements.getOrDefault(entry.getKey(),List.of()),calendar,now.toLocalDate());
            var actionable = todos.getActionableTodos(entry.getKey());
            List<String> matterKeys = new ArrayList<>();
            for (var todo : actionable) {
                if ("APPRAISAL_SUBMISSION_REQUIRED".equals(todo.key())) {
                    f.put("appraisal.required",true); f.put("appraisal.canSubmit",true); matterKeys.add(todo.id());
                }
                if ("REFLECTION_SUBMISSION_REQUIRED".equals(todo.key())) {
                    Object period=todo.action()!=null&&todo.action().params()!=null?todo.action().params().get("periodNumber"):null;
                    // One fact per business period; submitted records must never match a synthetic missing row.
                    records(f,"reflections").removeIf(r->Objects.equals(Objects.toString(r.get("periodNumber"),""),Objects.toString(period,""))&&Boolean.TRUE.equals(r.get("required")));
                    Map<String,Object> record = new LinkedHashMap<>();record.put("status","MISSING");record.put("required",true);record.put("canSubmit",true);
                    record.put("id",todo.id()); record.put("dueDate",todo.dueAt()==null?null:todo.dueAt().toLocalDate().toString());
                    if(todo.action()!=null && todo.action().params()!=null)record.put("periodNumber",todo.action().params().get("periodNumber"));
                    records(f,"reflections").add(record);matterKeys.add(todo.id());
                }
                if ("OFFER_CONFIRMATION_REQUIRED".equals(todo.key()) || "CONFIRMATION_RESUBMISSION_REQUIRED".equals(todo.key())) matterKeys.add(todo.id());
            }
            f.put("matterKeys",matterKeys);
        }
        return result;
    }
    @SuppressWarnings("unchecked") private static List<Map<String,Object>> records(Map<String,Object> f,String key){return (List<Map<String,Object>>)f.get(key);}
    private void add(Map<Long,Map<String,Object>> result,String source,String sql,Map<String,Object> args){
        for(var row:jdbc.queryForList(sql,args)){
            Map<String,Object> f=result.get(number(row.get("student_id")));if(f==null)continue;
            Map<String,Object> record=new LinkedHashMap<>();
            if(source.equals("reflections")){record.put("required","DRAFT".equals(row.get("status")));record.put("canSubmit",false);}
            row.forEach((key,value)->{if(!key.equals("student_id"))record.put(switch(key){case "company_id"->"companyId";case "position_id"->"positionId";case "company_name"->"companyName";case "position_name"->"positionName";case "recall_status"->"recallStatus";case "period_number"->"periodNumber";case "due_date"->"dueDate";default->key;},value instanceof java.sql.Timestamp t?t.toLocalDateTime().toString():value);});
            records(f,source).add(record);
        }
    }
    static void addRequiredPeriods(Map<String,Object> facts,List<Map<String,Object>> placements,InternshipCalendar calendar,LocalDate today) {
        if(calendar==null||calendar.getStartDate()==null||calendar.getEndDate()==null)return;
        LocalDate start=AutomationRules.date(calendar.getStartDate()),end=AutomationRules.date(calendar.getEndDate());
        if(end.isBefore(start))return;
        var real=placements.stream().filter(p->Set.of("CONFIRMED","IN_PROGRESS","REPLACED","INTERRUPTED","ENDED").contains(p.get("status")))
                .filter(p->p.get("internship_start_time")!=null&&p.get("company_id")!=null).toList();
        boolean ratable=real.stream().anyMatch(p->!AutomationRules.date(p.get("internship_start_time")).isAfter(end)&&!AutomationRules.date(p.get("internship_start_time")).isAfter(today));
        if(ratable&&Set.of("MISSING","DRAFT","RETURNED").contains(facts.get("appraisal.status")))facts.put("appraisal.required",true);
        if(calendar.getReportCycle()==null||calendar.getReportCycle()<=0||today.isBefore(start))return;
        if(records(facts,"confirmations").stream().noneMatch(r->"1".equals(String.valueOf(r.get("status")))&&!"2".equals(String.valueOf(r.get("recallStatus")))))return;
        int cycle=calendar.getReportCycle();long periods=java.time.temporal.ChronoUnit.DAYS.between(start,today.isBefore(end)?today:end)/cycle+1;
        if(periods>1000)throw new IllegalStateException("实习日历期次异常，请核对统一时间配置。");
        var reflections=records(facts,"reflections");
        for(int number=1;number<=periods;number++) {
            LocalDate periodStart=start.plusDays((long)(number-1)*cycle),due=start.plusDays((long)number*cycle-1);if(due.isAfter(end))due=end;
            final LocalDate boundary=due;final int period=number;
            boolean expected=real.stream().anyMatch(p->!AutomationRules.date(p.get("internship_start_time")).isAfter(boundary)&&(p.get("internship_end_time")==null||!AutomationRules.date(p.get("internship_end_time")).isBefore(periodStart)));
            if(!expected||reflections.stream().anyMatch(r->Integer.toString(period).equals(String.valueOf(r.get("periodNumber")))))continue;
            var row=new LinkedHashMap<String,Object>();row.put("id","reflection:"+period);row.put("periodNumber",period);row.put("status","MISSING");row.put("required",true);row.put("canSubmit",false);row.put("dueDate",due);row.put("date",null);reflections.add(row);
        }
    }
    private static long number(Object value){return ((Number)value).longValue();}
}
