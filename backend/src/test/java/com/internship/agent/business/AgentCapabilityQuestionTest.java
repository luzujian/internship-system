package com.internship.agent.business;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AgentCapabilityQuestionTest {
    @Test void recognizesCapabilityQuestionsWithoutSwallowingBusinessRequests() {
        for (String text : new String[]{"问问我能够做什么...", "你能做什么？", "你支持哪些功能", "你现在可以帮我做什么"})
            assertTrue(AgentTaskService.isCapabilityQuestion(text), text);
        for (String text : new String[]{"帮我填写岗位", "我现在可以提交申请吗", "你能做什么？顺便查询学生名单"})
            assertFalse(AgentTaskService.isCapabilityQuestion(text), text);
    }
}
