package com.internship.service;

import com.internship.agent.business.AgentException;
import com.internship.config.DynamicChatClientFactory;
import com.internship.entity.Position;
import com.internship.entity.StudentUser;
import com.internship.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LegacyAiGenerationTest {
    private final DynamicChatClientFactory clients = mock(DynamicChatClientFactory.class);
    private final ChatClient chat = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final PositionService positions = mock(PositionService.class);
    private final StudentUserService students = mock(StudentUserService.class);
    private JobRecommendationAgentService recommendations;
    private JobDescriptionGeneratorService descriptions;
    private EnhancedDeepSeekService enhanced;

    @BeforeEach
    void setUp() {
        when(clients.createChatClient(anyString(), anyString())).thenReturn(chat);
        StudentUser student = new StudentUser();
        student.setId(11L);
        student.setMajor("计算机");
        student.setName("测试学生");
        student.setGrade(2026);
        when(students.findByStudentId("s011")).thenReturn(student);
        recommendations = new JobRecommendationAgentService(positions, students,
                mock(PositionFavoriteService.class), mock(StudentJobApplicationService.class),
                mock(CompanyUserService.class), clients);
        descriptions = new JobDescriptionGeneratorService(clients);
        enhanced = new EnhancedDeepSeekService();
        ResourceService resources = mock(ResourceService.class);
        when(resources.semanticSearch(anyString())).thenReturn(List.of());
        ReflectionTestUtils.setField(enhanced, "resourceService", resources);
        ReflectionTestUtils.setField(enhanced, "chatClientFactory", clients);
        when(positions.findAll()).thenReturn(List.of(position(1, "Java开发")));
    }

    @Test
    void largeCatalogAndLongFieldsProduceBoundedRelevantCandidatePrompt() {
        List<Position> catalog = new ArrayList<>();
        for (int i = 1; i <= 99; i++) {
            Position position = position(i, "销售" + "名".repeat(1000));
            position.setRequirements("求".repeat(3000));
            position.setDescription("述".repeat(3000));
            catalog.add(position);
        }
        catalog.add(position(100, "Java开发"));
        when(positions.findAll()).thenReturn(catalog);
        when(positions.findByIds(List.of(100L))).thenReturn(List.of(catalog.get(99)));
        when(chat.prompt(any(Prompt.class)).call().content()).thenReturn("position_ids: 100");

        clearInvocations(chat);
        Map<String, Object> result = recommendations.processJobRecommendation("Java开发 " + "求".repeat(9000), "s011", null);

        assertEquals(true, result.get("success"));
        assertEquals(1, result.get("total"));
        assertEquals(30, result.get("candidateCount"));
        assertEquals(true, result.get("candidateLimitReached"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chat).prompt(prompt.capture());
        String content = prompt.getValue().getInstructions().get(1).getText();
        assertEquals(30, content.lines().filter(line -> line.startsWith("ID:")).count());
        assertTrue(content.contains("ID:100|名称:Java开发"));
        assertTrue(content.length() < 20000, "provider input must have a hard bound independent of catalog size");
        assertFalse(content.contains("述".repeat(241)));
    }

    @Test
    void providerFailureIsNotSuccessfulZeroMatches() {
        when(chat.prompt(any(Prompt.class)).call().content()).thenThrow(new IllegalStateException("provider unavailable"));
        Map<String, Object> result = recommendations.processJobRecommendation("推荐", "s011", null);
        assertEquals(false, result.get("success"));
        assertEquals(true, result.get("error"));
        assertFalse(result.get("message").toString().contains("找到0个"));
        verify(positions, never()).findByIds(anyList());
    }

    @Test
    void malformedAndUnknownRecommendationIdsFailExplicitly() {
        for (String response : new String[]{"", "服务繁忙", "position_ids: ", "position_ids: 1,broken", "position_ids: 999"}) {
            when(chat.prompt(any(Prompt.class)).call().content()).thenReturn(response);
            assertEquals(false, recommendations.processJobRecommendation("推荐", "s011", null).get("success"), response);
        }
    }

    @Test
    void explicitNoMatchAndEmptyCatalogRemainSuccessful() {
        when(chat.prompt(any(Prompt.class)).call().content()).thenReturn("position_ids: \"\"");
        Map<String, Object> result = recommendations.processJobRecommendation("推荐", "s011", null);
        assertEquals(true, result.get("success"));
        assertEquals(0, result.get("total"));
        reset(clients);
        when(positions.findAll()).thenReturn(List.of());
        assertEquals(true, recommendations.processJobRecommendation("推荐", "s011", null).get("success"));
        verifyNoInteractions(clients);
    }

    @Test
    void duplicateMatchesAreDeduplicatedAndKeepProviderOrder() {
        when(positions.findAll()).thenReturn(List.of(position(1, "Java开发"), position(2, "开发")));
        when(positions.findByIds(List.of(2L, 1L))).thenReturn(List.of(position(1, "Java开发"), position(2, "开发")));
        when(chat.prompt(any(Prompt.class)).call().content()).thenReturn("position_ids: 2,1,2");
        Map<String, Object> result = recommendations.processJobRecommendation("推荐", "s011", null);
        assertEquals(true, result.get("success"));
        List<?> jobs = (List<?>) result.get("positions");
        assertEquals(2L, ((Map<?, ?>) jobs.get(0)).get("id"));
        assertEquals(1L, ((Map<?, ?>) jobs.get(1)).get("id"));
    }

    @Test
    void descriptionProviderFailureThrowsInsteadOfReturningErrorText() {
        when(chat.prompt().messages(anyList()).call().content()).thenThrow(new IllegalStateException("provider unavailable"));
        assertThrows(BusinessException.class, () -> descriptions.generateJobDescription("开发", "研发", "实习", "deepseek-flash"));
    }

    @Test
    void blankAndHeaderOnlyDescriptionsFail() {
        for (String response : new String[]{null, "", " \t\r\n", "【岗位描述】", "【岗位描述】\n \t\r\n"}) {
            when(chat.prompt().messages(anyList()).call().content()).thenReturn(response);
            assertThrows(BusinessException.class,
                    () -> descriptions.generateJobDescription("开发", "研发", "实习", "deepseek-flash"),
                    "response: " + response);
        }
    }

    @Test
    void formattedAndPlainChineseDescriptionsKeepTheirContent() {
        String content = "负责排查无法登录等系统问题，并参与开发与测试。";
        for (String response : new String[]{"【岗位描述】\n" + content, "  " + content + "\n"}) {
            when(chat.prompt().messages(anyList()).call().content()).thenReturn(response);
            assertEquals(content,
                    descriptions.generateJobDescription("开发", "研发", "实习", "deepseek-flash").get("description"));
        }
    }

    @Test
    void resourceGenerationFailsExplicitlyForBlankAndProviderFailure() {
        when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(" ");
        assertThrows(BusinessException.class, () -> enhanced.chatWithResources("评分描述", false));
        when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenThrow(new IllegalStateException("provider unavailable"));
        assertThrows(BusinessException.class, () -> enhanced.chatWithResources("评分描述", false));
    }

    @Test
    void successfulResourceGenerationKeepsProviderText() {
        when(chat.prompt().system(anyString()).user(anyString()).call().content()).thenReturn("完成工作，主动协作。");
        assertEquals("完成工作，主动协作。", enhanced.chatWithResources("评分描述", false));
    }

    @Test
    void modelBusyExceptionKeepsIts429ContractAcrossLegacyCallers() {
        AgentException busy = new AgentException("AI_BUSY", "AI繁忙，请稍后重试", 429);
        when(clients.createChatClient(anyString(), anyString())).thenThrow(busy);
        assertSame(busy, assertThrows(AgentException.class, () -> recommendations.processJobRecommendation("推荐", "s011", null)));
        assertSame(busy, assertThrows(AgentException.class, () -> descriptions.generateJobDescription("开发", "研发", "实习", "deepseek-flash")));
        assertSame(busy, assertThrows(AgentException.class, () -> enhanced.chatWithResources("评分描述", false)));
    }

    private Position position(long id, String name) {
        Position position = new Position();
        position.setId(id);
        position.setPositionName(name);
        position.setStatus("active");
        return position;
    }
}
