package com.internship.agent.business;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentTypes.Actor;
import com.internship.service.SysDictService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.lang.reflect.Field;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentKnowledgeTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test void requiredCoreFlowsAreRegisteredAndStructured() throws Exception {
        List<Map<String,Object>> cards;
        try (var stream = new ClassPathResource("agent/knowledge.json").getInputStream()) {
            cards = json.readValue(stream, new TypeReference<>() {});
        }
        Set<String> ids = new HashSet<>();
        for (Map<String,Object> card : cards) {
            assertTrue(ids.add(String.valueOf(card.get("id"))), "知识卡 id 重复: " + card.get("id"));
            assertFalse(String.valueOf(card.get("title")).isBlank());
            assertFalse(((List<?>) card.get("roles")).isEmpty());
            String level = String.valueOf(card.get("verifyLevel"));
            assertTrue(Set.of("CODE_TRACED", "RUNTIME_VERIFIED", "UNVERIFIED").contains(level), "未知核验级别: " + level);
        }
        assertTrue(ids.containsAll(Set.of(
                "application", "offer", "reflection", "teachertodo", "capabilities",
                "voice-form-fill", "student-plan-howto", "teacher-auto-task-howto",
                "teacher-internship-summary", "ai-capability-control")));
    }

    @Test void lookupDoesNotInventUnknownTopicAndFiltersByRole() {
        AgentKnowledge knowledge = assertDoesNotThrow(() -> new AgentKnowledge(json));
        Actor student = new Actor("ROLE_STUDENT:1:false:", 1L, "ROLE_STUDENT", "学生", null,
                null, null, false, Set.of());
        Actor teacher = new Actor("ROLE_TEACHER_COUNSELOR:9:false:", 9L, "ROLE_TEACHER_COUNSELOR", "教师", "COUNSELOR",
                null, null, false, Set.of());

        assertEquals(0, knowledge.lookup(student, "完全不存在的系统能力XYZ").total());
        assertTrue(knowledge.lookup(student, "实习计划").total() > 0);
        assertTrue(knowledge.lookup(student, "自主实习怎么申请").items().stream()
                .anyMatch(item -> "self-practice-entry".equals(item.get("id"))));
        assertTrue(knowledge.lookup(teacher, "教师自动化任务").total() > 0);
        assertTrue(knowledge.lookup(student, "完全不存在的系统能力XYZ").items().stream()
                .noneMatch(item -> "teacher-auto-task-howto".equals(item.get("id"))));
    }

    @Test void forgotPasswordCardDescribesSelfServiceForAllThreeRoles() {
        AgentKnowledge knowledge = assertDoesNotThrow(() -> new AgentKnowledge(json));
        Actor student = new Actor("ROLE_STUDENT:1:false:", 1L, "ROLE_STUDENT", "学生", null,
                null, null, false, Set.of());
        var items = knowledge.lookup(student, "忘记密码").items();
        var card = items.stream().filter(i -> "faq-account-locked".equals(i.get("id"))).findFirst()
                .orElseThrow(() -> new AssertionError("missing"));
        String text = String.valueOf(card.get("rules"));
        assertTrue(text.contains("自助") && text.contains("验证码"));
        assertFalse(text.contains("学生与教师联系管理员重置"));
        assertTrue(text.contains("管理员账号暂不支持自助"));
    }
    @Test void calendarQuestionsUseUnifiedSettingsWithoutBypassingMixedObjectQuestions() {
        var knowledge = assertDoesNotThrow(() -> new AgentKnowledge(json));
        var student = new Actor("s1",1L,"ROLE_STUDENT","学生",null,null,null,false,Set.of());
        for (String question : List.of("实习什么时候结束？", "我的实习什么时候开始", "请问实习结束日期是什么", "实习起止时间", "鉴定表截止日期是什么"))
            assertTrue(AgentKnowledge.isCalendarQuestion(question),question);
        for (String question : List.of("实习什么时候结束，我的岗位怎么填写", "这个申请为什么被拒绝", "我的实习结束了吗"))
            assertFalse(AgentKnowledge.isCalendarQuestion(question),question);
        assertTrue(knowledge.lookup(student,"实习什么时候结束").items().stream()
                .anyMatch(card -> "unified-internship-calendar".equals(card.get("id"))));
    }

    @Test void coreWorkflowMatchesNaturalQuestionsWithoutClaimingPersonalStatus() throws Exception {
        var knowledge=new AgentKnowledge(json);
        var actor=new Actor("ROLE_COMPANY:1:false:",1L,"ROLE_COMPANY","企业",null,null,null,false,Set.of());
        for(String question:List.of("该系统的核心业务逻辑是什么", "该系统的核心业务流程是什么？", "请介绍一下系统的主要功能", "请绘制该系统的核心流程图，以文本的形式", "请画出系统的核心业务流程图，用文字表示")) {
            assertTrue(AgentKnowledge.isSystemOverviewQuestion(question));
            assertTrue(knowledge.lookup(actor,question).items().stream().anyMatch(card->"system-business-overview".equals(card.get("id"))));
        }
        for(String question:List.of("系统核心业务流程是什么，我的岗位为什么不能提交", "请绘制系统核心流程图，并查询当前岗位", "这个岗位还缺几个人", "我的实习确认到哪一步了"))
            assertFalse(AgentKnowledge.isSystemOverviewQuestion(question));
    }

    @Test void emptyStatusDictionaryDoesNotMatchEveryQuestion() throws Exception {
        var knowledge = new AgentKnowledge(json);
        var dict = mock(SysDictService.class);
        when(dict.codesByType("internship_status")).thenReturn(List.of());
        Field field = AgentKnowledge.class.getDeclaredField("dictService");
        field.setAccessible(true);
        field.set(knowledge, dict);

        knowledge.load();

        assertTrue(AgentKnowledge.isStatusQuestion("我的实习状态是什么"));
        assertFalse(AgentKnowledge.isStatusQuestion("今天的天气怎么样"));
    }

}
