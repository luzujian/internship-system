package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.internship.agent.business.AgentTypes.*;

class AgentAnswerScopeTest {
    private final ObjectMapper json=new ObjectMapper();
    private final Actor company=new Actor("company",1L,"ROLE_COMPANY","企业",null,null,null,false,Set.of());

    @Test void fieldQuestionsUseNarrowVerifiedGuidesWithoutRelatedRules() throws Exception {
        AgentKnowledge knowledge=new AgentKnowledge(json);
        for(String q:List.of("发布岗位的时候可以选择招多少学生吗","发布岗位时能设置招聘人数吗？","招聘人数在哪里填")) {
            assertEquals("plannedRecruit",AgentAnswerScope.fieldKey(company,q));
            ToolResult result=knowledge.lookup(company,q);
            assertEquals(1,result.items().size());assertTrue(result.facts().containsKey("conciseAnswer"));
            assertFalse(json.writeValueAsString(result).contains("剩余名额"));
        }
        assertEquals("contactId",AgentAnswerScope.fieldKey(company,"发布岗位时可以选择不同联系人吗"));
        assertEquals("salaryUnit",AgentAnswerScope.fieldKey(company,"发布岗位时可以选择薪资单位吗"));
    }

    @Test void realCountsConstraintsComparisonsFollowupsAndMultipleQuestionsNeverTakeFieldShortcut() {
        for(String q:List.of("计划招聘人数与剩余名额有什么区别","发布岗位可以招200人吗","我发布的岗位招了多少人", "这个人数可以修改吗",
                "发布岗位时可以设置招聘人数吗，最多多少人", "发布岗位时可以设置招聘人数吗，联系人呢", "帮我把招聘人数设置为5", "为什么不能修改招聘人数"))
            assertNull(AgentAnswerScope.fieldKey(company,q),q);
        Actor student=new Actor("student",1L,"ROLE_STUDENT","学生",null,null,null,false,Set.of());
        assertNull(AgentAnswerScope.fieldKey(student,"发布岗位时可以选择招多少学生吗"));
    }

    @Test void reviewCanOnlySelectOriginalPartsAndRejectsInvalidOrInventedOutput() {
        String draft="可以。在招聘管理填写计划招聘人数，它与确认后的剩余名额是两回事。";
        var parts=AgentAnswerScope.parts(draft);
        assertEquals("可以。在招聘管理填写计划招聘人数。",AgentAnswerScope.select(json,parts,"{\"keep\":[0,1]}"));
        assertEquals(draft,AgentAnswerScope.select(json,parts,"{\"keep\":[0,1,2]}"));
        for(String invalid:List.of("{\"keep\":[]}","{\"keep\":[-1]}","{\"keep\":[2,1]}","{\"keep\":[0,0]}","{\"keep\":[50]}","{\"keep\":[0.5]}","{\"keep\":[0],\"answer\":\"已发布\"}","已发布"))
            assertThrows(AgentException.class,()->AgentAnswerScope.select(json,parts,invalid));
    }

    @Test void fullSelectionPreservesConditionsListsAndCodeVerbatim() throws Exception {
        for(String draft:List.of("尚未提交，只有点击确认后才会发送。", "目前只查到第一页，合计仍未核实。", "1. 打开页面。\n2. 填写字段。", "示例：`a，b`。", "```text\na，b\nc\n```")) {
            var parts=AgentAnswerScope.parts(draft);
            assertEquals(draft,AgentAnswerScope.select(json,parts,json.writeValueAsString(Map.of("keep",java.util.stream.IntStream.range(0,parts.size()).boxed().toList()))));
        }
    }

    @Test void relevanceRejectionIsDistinctFromRetryableProtocolFailure() {
        var parts=AgentAnswerScope.parts("只有学生确认后才能接收，目前尚未确认。");
        var rejected=assertThrows(AgentAnswerScope.ReviewFailure.class,()->AgentAnswerScope.select(json,parts,"{\"keep\":[]}"));
        assertFalse(rejected.retryable);assertEquals("NO_RELEVANT_CONTENT",rejected.reason);
        for(String raw:List.of("", "{\"keep\":", "{\"keep\":[-1]}", "{\"keep\":[1,0]}", "{\"keep\":[0,0]}", "{\"keep\":[0]} trailing")) {
            var invalid=assertThrows(AgentAnswerScope.ReviewFailure.class,()->AgentAnswerScope.select(json,parts,raw));
            assertTrue(invalid.retryable);assertEquals("ANSWER_CHECK_FAILED",invalid.getCode());
        }
    }
}
