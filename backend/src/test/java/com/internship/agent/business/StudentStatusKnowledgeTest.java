package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.enums.JobSearchStatus;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StudentStatusKnowledgeTest {
    private final Actor student=new Actor("s:1",1L,"ROLE_STUDENT","学生",null,null,null,false,Set.of());
    @Test void statusQuestionsAlwaysFindFullGuide() throws Exception {
        var knowledge=new AgentKnowledge(new ObjectMapper());
        for(String q:List.of("学生有哪些实习状态", "无 Offer是什么意思", "为什么从无Offer变成未投递", "面试通过后是待确认吗", "已中断怎么恢复", "我现在是什么状态")) {
            assertTrue(AgentKnowledge.isStatusQuestion(q),q);
            var result=knowledge.lookup(student,q);
            assertEquals("internship-status-guide",result.items().getFirst().get("id"));
        }
        assertFalse(AgentKnowledge.isStatusQuestion("我的负责老师是谁"));
    }
    @Test void documentedStatesMatchBusinessEnumAndExplainEntryExit() throws Exception {
        var card=new AgentKnowledge(new ObjectMapper()).lookup(student,"实习状态").items().getFirst();
        var states=new ObjectMapper().valueToTree(card.get("states"));
        assertEquals(8,states.size());
        Set<String> expected=new HashSet<>();for(var state:JobSearchStatus.values())if(state!=JobSearchStatus.UNKNOWN)expected.add(state.name());
        Set<String> actual=new HashSet<>();
        for(var state:states) {
            actual.add(state.path("code").asText());
            assertEquals(JobSearchStatus.valueOf(state.path("code").asText()).getText(),state.path("name").asText());
            for(String field:List.of("meaning","entry","exit","next"))assertFalse(state.path(field).asText().isBlank());
        }
        assertEquals(expected,actual);
        String rules=card.get("rules").toString();assertTrue(rules.contains("不能只看最近一条申请"));assertTrue(rules.contains("自主实习审核通过"));assertTrue(rules.contains("数字0"));assertTrue(rules.contains("不需要企业在系统内再次确认"));
    }
}
