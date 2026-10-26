package com.internship.service;

import com.internship.config.DynamicChatClientFactory;
import com.internship.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TeacherAutoTaskParseRecoveryTest {
    private static final String REQUEST = "现在通过站内通知全部负责的学生，明天参加实习会议，只发一次。";
    private static final String TRUNCATED = """
            {"name":"实习会议站内通知","triggerCondition":"SCHEDULED_REMINDER","triggerDays":null,"targetMode":"SCOPE","targetQueries":
            """;
    private static final String VALID = """
            {"name":"实习会议站内通知","triggerCondition":"SCHEDULED_REMINDER","triggerDays":null,
             "targetMode":"SCOPE","targetQueries":[],"action":"STATION_REMINDER","frequencyMinutes":1440,
             "endAt":null,"schedule":{"mode":"MANUAL"},"messageTemplate":"请明天参加实习会议。","questions":[]}
            """;
    private ChatModel model;
    private TeacherAutoTaskParseService service;

    @BeforeEach void setup() {
        model = mock(ChatModel.class);
        var factory = mock(DynamicChatClientFactory.class);
        ChatClient client = ChatClient.create(model);
        when(factory.createPreviewChatClient(anyString(), anyString())).thenReturn(client);
        service = new TeacherAutoTaskParseService(factory);
        ReflectionTestUtils.setField(service, "maxOutputTokens", 800);
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5)).build());
    }

    @Test void truncatedMeetingDraftIsRegeneratedAndStillRequiresConfirmation() {
        when(model.call(any(Prompt.class))).thenReturn(response(TRUNCATED), response(VALID));
        Map<String, Object> draft = service.parse(REQUEST);
        assertEquals("SCHEDULED_REMINDER", draft.get("triggerCondition"));
        assertEquals("STATION_REMINDER", draft.get("action"));
        assertEquals("{\"mode\":\"MANUAL\"}", draft.get("scheduleConfig"));
        assertEquals(List.of(), draft.get("questions"));
        assertEquals(true, draft.get("needsConfirm"));
        assertEquals("请明天参加实习会议。", draft.get("messageTemplate"));
        var calls = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(calls.capture());
        assertEquals(800, calls.getAllValues().get(0).getOptions().getMaxTokens());
        assertEquals(1600, calls.getAllValues().get(1).getOptions().getMaxTokens());
        assertEquals(30L, ((Map<?, ?>) draft.get("usage")).get("totalTokens"));
    }

    @Test void twoInvalidDraftsFailWithoutAnUnboundedRetry() {
        when(model.call(any(Prompt.class))).thenReturn(response(TRUNCATED));
        assertThrows(BusinessException.class, () -> service.parse(REQUEST));
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test void validDraftUsesOnlyOneModelCall() {
        when(model.call(any(Prompt.class))).thenReturn(response(VALID));
        assertEquals(true, service.parse(REQUEST).get("needsConfirm"));
        verify(model).call(any(Prompt.class));
    }

    @Test void networkFailureDoesNotTriggerDraftRegeneration() {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("provider unavailable"));
        assertThrows(BusinessException.class, () -> service.parse(REQUEST));
        verify(model).call(any(Prompt.class));
    }

    @Test void regeneratedJsonCannotBypassBusinessWhitelist() {
        when(model.call(any(Prompt.class))).thenReturn(response(TRUNCATED),
                response(VALID.replace("SCHEDULED_REMINDER", "UNKNOWN_STUDENT_STATE")));
        Map<String, Object> draft = service.parse(REQUEST);
        assertEquals("UNSUPPORTED", draft.get("triggerCondition"));
        assertEquals(false, draft.get("needsConfirm"));
        assertFalse(((List<?>) draft.get("questions")).isEmpty());
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test void nullJsonIsRegeneratedInsteadOfCausingAnUnhandledFailure() {
        when(model.call(any(Prompt.class))).thenReturn(response("null"), response(VALID));
        assertEquals(true, service.parse(REQUEST).get("needsConfirm"));
        verify(model, times(2)).call(any(Prompt.class));
    }

    @Test void emptyDraftIsRegeneratedUsingALargerBudget() {
        when(model.call(any(Prompt.class))).thenReturn(response(""), response(VALID));
        Map<String, Object> draft = service.parse(REQUEST);
        assertEquals("{\"mode\":\"MANUAL\"}", draft.get("scheduleConfig"));
        assertEquals(true, draft.get("needsConfirm"));
        var calls = ArgumentCaptor.forClass(Prompt.class);
        verify(model, times(2)).call(calls.capture());
        assertEquals(1600, calls.getAllValues().get(1).getOptions().getMaxTokens());
    }

    @Test void twoEmptyDraftsShowTheExistingNoResultError() {
        when(model.call(any(Prompt.class))).thenReturn(response(""));
        BusinessException error = assertThrows(BusinessException.class, () -> service.parse(REQUEST));
        assertEquals("AI未返回任务解析结果，请稍后重试或使用手动配置。", error.getMessage());
        verify(model, times(2)).call(any(Prompt.class));
    }
}
