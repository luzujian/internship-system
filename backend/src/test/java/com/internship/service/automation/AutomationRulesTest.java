package com.internship.service.automation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.internship.service.automation.AutomationRules.*;

class AutomationRulesTest {
    final ObjectMapper json=new ObjectMapper();
    @Test void deadlineWindowIsExactlyThreeDaysAcrossMonthsAndYears(){
        var rule=leaf(json,"appraisal.deadline","DAYS_UNTIL",List.of(1,3));validate(rule);
        for(LocalDate deadline:List.of(LocalDate.of(2027,1,1),LocalDate.of(2028,3,1),LocalDate.of(2026,9,30))) {
            for(int days=-1;days<=5;days++)assertEquals(days>=1&&days<=3?Truth.TRUE:Truth.FALSE,evaluate(rule,Map.of("appraisal.deadline",deadline),deadline.minusDays(days)));
        }
        assertEquals(Truth.FALSE,evaluate(rule,Map.of("appraisal.deadline",LocalDate.of(2027,1,10)),LocalDate.of(2026,12,30)));
    }
    @Test void unknownFactsAreNeverInvertedIntoMatching(){
        var rule=json.createObjectNode().set("not",leaf(json,"appraisal.required","EQ",true));
        assertEquals(Truth.UNKNOWN,evaluate(rule,Map.of(),LocalDate.now()));
        assertThrows(RuntimeException.class,()->validate(leaf(json,"student.returnedToWork","EQ",false)));
        assertThrows(RuntimeException.class,()->validate(leaf(json,"appraisal.required","EQ","true")));
        assertThrows(RuntimeException.class,()->validate(leaf(json,"appraisal.deadline","DAYS_UNTIL",List.of("one",3))));
        assertThrows(RuntimeException.class,()->validate(leaf(json,"internship.status","EQ","NOT_RETURNED")));
    }
    @Test void recordConditionsCannotJoinDifferentPeriods()throws Exception{
        var condition=json.readTree("""
          {"exists":"reflections","where":{"all":[{"field":"periodNumber","op":"EQ","value":2},{"field":"required","op":"EQ","value":true}]}}
          """);validate(condition);
        var facts=Map.<String,Object>of("reflections",List.of(Map.of("periodNumber",1,"required",true),Map.of("periodNumber",2,"required",false)));
        assertEquals(Truth.FALSE,evaluate(condition,facts,LocalDate.now()));
    }
    @Test void activationWaitsForNextFixedTimeAndDoesNotBackfill()throws Exception{
        var daily=json.readTree("{\"mode\":\"DAILY\",\"time\":\"09:00\"}");
        assertEquals(LocalDateTime.of(2026,10,1,9,0),AutomationTiming.next(daily,LocalDateTime.of(2026,9,30,9,0,1)));
        assertEquals(LocalDateTime.of(2026,9,30,9,0),AutomationTiming.next(daily,LocalDateTime.of(2026,9,30,8,59)));
        var monthly=json.readTree("{\"mode\":\"MONTHLY\",\"time\":\"09:00\",\"monthdays\":[31]}");
        assertEquals(LocalDateTime.of(2027,3,31,9,0),AutomationTiming.next(monthly,LocalDateTime.of(2027,2,1,9,0)));
        var once=json.readTree("{\"mode\":\"ONCE\",\"time\":\"09:00\",\"date\":\"2026-09-21\"}");
        assertNull(AutomationTiming.next(once,LocalDateTime.of(2026,9,21,9,1)));
    }
    @Test void templatesAreDeterministicAndPreserveEditedBody(){
        var definitions=new AutomationDefinitions(json,null);
        var template=definitions.templates().getFirst();String body="自定义正文，包含标点与\n换行。";
        var result=definitions.parse(template.get("prefix")+body,null);
        assertEquals(body,result.path("notification").path("body").asText());assertTrue(definitions.issues(result).isEmpty());
        assertEquals("DAILY",result.path("trigger").path("mode").asText());assertEquals("09:00",result.path("trigger").path("time").asText());
    }
    @Test void dateRangesCompareCalendarDaysAndSourceFieldsAreChecked() throws Exception {
        var dateRule=leaf(json,"date","BETWEEN",List.of("2026-09-01","2026-09-21"));
        var rule=json.createObjectNode().put("exists","applications").set("where",dateRule);validate(rule);
        assertEquals(Truth.TRUE,evaluate(rule,Map.of("applications",List.of(Map.of("date","2026-09-21T23:59:59"))),LocalDate.of(2026,9,21)));
        var invalid=json.createObjectNode().put("exists","applications").set("where",leaf(json,"periodNumber","EQ",1));
        assertThrows(RuntimeException.class,()->validate(invalid));
    }
    @Test void periodFactsKeepMissingHistorySeparateFromSubmittedPeriods(){
        var calendar=new com.internship.entity.dto.InternshipCalendar();calendar.setStartDate("2026-09-01");calendar.setEndDate("2026-09-30");calendar.setReportCycle(7);
        var f=new LinkedHashMap<String,Object>();f.put("appraisal.status","RETURNED");f.put("confirmations",List.of(Map.of("status",1,"recallStatus",0)));
        var records=new ArrayList<Map<String,Object>>();records.add(Map.of("periodNumber",1,"status","SUBMITTED","required",false));f.put("reflections",records);
        AutomationFacts.addRequiredPeriods(f,List.of(Map.of("status","CONFIRMED","company_id",1,"internship_start_time","2026-09-01")),calendar,LocalDate.of(2026,9,21));
        assertEquals(3,records.size());assertEquals(false,records.getFirst().get("required"));assertEquals(true,records.get(1).get("required"));assertEquals(2,records.get(1).get("periodNumber"));
        assertEquals(LocalDate.of(2026,9,14),records.get(1).get("dueDate"));assertEquals(true,f.get("appraisal.required"));
    }
    @Test void describeProducesReadableConditions(){
        var rule=leaf(json,"appraisal.deadline","DAYS_UNTIL",List.of(1,3));
        assertEquals("距离鉴定表截止日期还剩 1 ~ 3 天",describe(rule));
        var singleDay=leaf(json,"dueDate","DAYS_UNTIL",List.of(1,1));
        assertEquals("距离截止日期还剩 1 天",describe(singleDay));
        var overdue=leaf(json,"appraisal.deadline","DAYS_OVERDUE",List.of(1,5));
        assertEquals("距离鉴定表截止日期已逾期 1 ~ 5 天",describe(overdue));
        var between=leaf(json,"periodNumber","BETWEEN",List.of(1,3));
        assertEquals("期次范围为 1 ~ 3",describe(between));
    }
}
