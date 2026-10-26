package com.internship.importing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.*;
import com.internship.util.AiCallGuard;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SmartImportRecognitionTest {
    final AgentTypes.Actor actor = new AgentTypes.Actor("admin",2L,"ROLE_ADMIN","管理员",null,null,null,false,Set.of());
    final List<SmartWorkbookReader.SourceSheet> sheets = List.of(new SmartWorkbookReader.SourceSheet("0:0","名单.xlsx","学生",List.of(
            new SmartWorkbookReader.SourceRow(1,List.of("序列代码","真实姓名","所属教学班")),new SmartWorkbookReader.SourceRow(2,List.of("00123","测试学生","23级1班")))));
    SmartImportRecognition service(AgentModel model) {
        var tasks = mock(AgentTaskService.class); when(tasks.enabled()).thenReturn(true);
        return new SmartImportRecognition(model,tasks,mock(AiCallGuard.class),new ObjectMapper());
    }
    static ChatResponse response(String text) { return new ChatResponse(List.of(new Generation(new AssistantMessage(text)))); }
    @Test void unfamiliarHeadersAfterATitleAreIncludedInTheModelContext() {
        var source = List.of(new SmartWorkbookReader.SourceSheet("0:0","名单.xlsx","学生",List.of(
                new SmartWorkbookReader.SourceRow(1,List.of("本地验收学生名册")),
                new SmartWorkbookReader.SourceRow(3,List.of("学籍标识码","同学称呼","所在行政分组")),
                new SmartWorkbookReader.SourceRow(4,List.of("00123","测试学生","23级1班")))));
        var model = mock(AgentModel.class);
        when(model.call(anyList(),anyList())).thenAnswer(call -> {
            List<org.springframework.ai.chat.messages.Message> messages = call.getArgument(0);
            assertTrue(messages.get(1).getText().contains("学籍标识码"));
            assertTrue(messages.get(1).getText().contains("所在行政分组"));
            return response("{\"type\":\"student\",\"sheets\":[{\"sheetId\":\"0:0\",\"headerRow\":3,\"columns\":{\"学号\":1,\"姓名\":2,\"班级\":3}}]}");
        });
        var result = service(model).recognize(actor,source,"student","帮我导入",null);
        assertEquals(3,result.layouts().getFirst().headerRow()); assertEquals(3,result.layouts().getFirst().columns().size());
    }
    @Test void semanticHeadersAreMappedButRecordsAreNotProducedByTheModel() {
        var model = mock(AgentModel.class);
        when(model.call(anyList(),anyList())).thenReturn(response("{\"type\":\"student\",\"sheets\":[{\"sheetId\":\"0:0\",\"headerRow\":1,\"columns\":{\"学号\":1,\"姓名\":2,\"班级\":3}}],\"defaults\":{\"年级\":\"2023\"}}"));
        var result = service(model).recognize(actor,sheets,"student","这些学生的年级是2023",null);
        assertEquals(1,result.layouts().getFirst().columns().get("学号")); assertEquals("2023",result.defaults().get("年级"));
        verify(model).call(anyList(),eq(List.of()));
    }
    @Test void inventedDefaultsAndUnknownFieldsAreNotTrusted() {
        var model = mock(AgentModel.class);
        when(model.call(anyList(),anyList())).thenReturn(response("{\"type\":\"student\",\"sheets\":[{\"sheetId\":\"0:0\",\"headerRow\":1,\"columns\":{\"学号\":1}}],\"defaults\":{\"年级\":\"2024\"}}"));
        assertTrue(service(model).recognize(actor,sheets,"student","帮我导入",null).defaults().isEmpty());
        when(model.call(anyList(),anyList())).thenReturn(response("{\"type\":\"student\",\"sheets\":[{\"sheetId\":\"0:0\",\"headerRow\":1,\"columns\":{\"SQL\":1}}]}"));
        var result = service(model).recognize(actor,sheets,"student","帮我导入",null);
        assertFalse(result.layouts().getFirst().columns().containsKey("SQL")); assertFalse(result.warnings().isEmpty());
    }
    @Test void providerFailureRetainsManualColumnSelectionAndNeverDropsTheWorkbook() {
        var model = mock(AgentModel.class); when(model.call(anyList(),anyList())).thenThrow(new IllegalStateException("provider unavailable"));
        var result = service(model).recognize(actor,sheets,"student","帮我导入",null);
        assertEquals("0:0",result.layouts().getFirst().sheetId()); assertFalse(result.warnings().isEmpty());
    }
    @Test void modelCannotChangeThePageSpecifiedImportType() {
        var model = mock(AgentModel.class); when(model.call(anyList(),anyList())).thenReturn(response("{\"type\":\"company\",\"sheets\":[]}"));
        assertEquals(SmartImportType.STUDENT,service(model).recognize(actor,sheets,"student","帮我导入",null).type());
    }
}
