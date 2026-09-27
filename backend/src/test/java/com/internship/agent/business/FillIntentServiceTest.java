package com.internship.agent.business;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.internship.agent.business.AgentTypes.*;
import static com.internship.agent.business.FillIntentService.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FillIntentServiceTest {
    private AgentTaskService tasks;
    private AgentCapabilityService capabilities;
    private AgentModel model;
    private FillIntentService service;
    private final Actor student = new Actor("ROLE_STUDENT:8:false:", 8L, "ROLE_STUDENT", "s001", null, null, null, false, Set.of());

    @BeforeEach void setup() {
        tasks = mock(AgentTaskService.class);
        capabilities = mock(AgentCapabilityService.class);
        model = mock(AgentModel.class);
        when(tasks.enabled()).thenReturn(true);
        when(capabilities.has(student, "form_fill")).thenReturn(true);
        service = new FillIntentService(tasks, capabilities, model, new ObjectMapper());
    }

    private void modelReturns(String text) {
        AssistantMessage output = new AssistantMessage(text);
        ChatResponse response = new ChatResponse(List.of(new Generation(output)));
        when(model.call(anyList(), anyList())).thenReturn(response);
    }

    private FillRequest request(FieldDescriptor... fields) {
        return new FillRequest("home:self-practice", "自主实习申请", List.of(fields), "公司是杭州云启科技");
    }

    private final FieldDescriptor company = new FieldDescriptor("company", "实习单位", "input", null, true, null, null);
    private final FieldDescriptor city = new FieldDescriptor("companyCity", "单位所在城市", "input", null, true, null, null);
    private final FieldDescriptor status = new FieldDescriptor("status", "当前状态", "radio",
            List.of(Map.of("value", "found", "label", "已找到实习"), Map.of("value", "postgraduate", "label", "准备考研")), true, null, null);
    private final FieldDescriptor letter = new FieldDescriptor("applicationLetterUrl", "个人申请书", "upload", null, true, null, null);

    @Test void eachFillReceivesFreshTrustedServerTime() {
        modelReturns("{\"intent\":\"chat\",\"assignments\":[]}");
        try (var clock = org.mockito.Mockito.mockStatic(AgentClock.class)) {
            clock.when(AgentClock::nowLine).thenReturn(
                    "现在是2026年09月21日23:59星期一（Asia/Shanghai）。",
                    "现在是2026年09月22日00:00星期二（Asia/Shanghai）。");
            service.fill(student, request(company));
            service.fill(student, request(company));
            var prompts = org.mockito.ArgumentCaptor.forClass(List.class);
            org.mockito.Mockito.verify(model, org.mockito.Mockito.times(2)).call(prompts.capture(), anyList());
            var first = (org.springframework.ai.chat.messages.Message) prompts.getAllValues().get(0).get(0);
            var second = (org.springframework.ai.chat.messages.Message) prompts.getAllValues().get(1).get(0);
            assertTrue(first.getText().startsWith("现在是2026年09月21日23:59"));
            assertTrue(second.getText().startsWith("现在是2026年09月22日00:00"));
        }
    }

    @Test void capabilityDisabledYieldsForbidden() {
        when(capabilities.has(student, "form_fill")).thenReturn(false);
        AgentException error = assertThrows(AgentException.class,
                () -> service.fill(student, request(company)));
        assertEquals(403, error.getHttpStatus());
        assertEquals("CAPABILITY_DISABLED", error.getCode());
    }

    @Test void uploadFieldsAreExcludedAndExplained() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"company\",\"value\":\"杭州云启科技\",\"confidence\":0.9}]}");
        Map<String, Object> result = service.fill(student, request(company, letter));
        List<Map<String, Object>> skipped = (List<Map<String, Object>>) result.get("skippedFields");
        assertTrue(skipped.stream().anyMatch(item -> "applicationLetterUrl".equals(item.get("prop"))));
        assertEquals(1, ((List<?>) result.get("assignments")).size());
    }

    @Test void assignmentsAreValidatedAndNormalised() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":["
                + "{\"prop\":\"company\",\"value\":\"杭州云启科技\",\"confidence\":0.95},"
                + "{\"prop\":\"not_a_field\",\"value\":\"编造\",\"confidence\":0.9},"
                + "{\"prop\":\"status\",\"value\":\"已找到实习\",\"confidence\":0.9}]}");
        Map<String, Object> result = service.fill(student, request(company, status, letter));
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");
        assertEquals(2, assignments.size());
        assertEquals("杭州云启科技", assignments.get(0).get("value"));
        // 选项字段按 label 归一为选项 value；编造字段被丢弃
        assertEquals("found", assignments.get(1).get("value"));
        assertTrue(assignments.stream().noneMatch(item -> "not_a_field".equals(item.get("prop"))));
    }

    @Test void keepSalaryInstructionWinsEvenWhenModelSuggestsChangingIt(){
        FieldDescriptor count=new FieldDescriptor("plannedRecruit","招聘人数","number",null,true,null,"number");
        FieldDescriptor salary=new FieldDescriptor("salaryMin","最低薪资","number",null,true,null,"number");
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"plannedRecruit\",\"value\":\"5\"},{\"prop\":\"salaryMin\",\"value\":\"3000\"}]}");
        Map<String,Object> result=service.fill(student,new FillRequest("company:position","岗位",List.of(count,salary),"人数改成五个，薪资不变，其他内容不要动"));
        var assignments=(List<Map<String,Object>>)result.get("assignments");
        assertEquals(1,assignments.size());assertEquals("plannedRecruit",assignments.getFirst().get("prop"));assertEquals(true,assignments.getFirst().get("overwriteAllowed"));
        assertTrue(((List<Map<String,Object>>)result.get("skippedFields")).stream().anyMatch(field->"salaryMin".equals(field.get("prop"))));
    }
    @Test void onlyExplicitClearProducesAnEmptyAssignment(){
        FieldDescriptor remark=new FieldDescriptor("remark","备注","textarea",null,false,null,null);
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"remark\",\"value\":\"\",\"operation\":\"CLEAR\"}]}");
        var cleared=(List<Map<String,Object>>)service.fill(student,new FillRequest("example","表单",List.of(remark),"清空备注")).get("assignments");
        assertEquals(1,cleared.size());assertEquals("",cleared.getFirst().get("value"));assertEquals("CLEAR",cleared.getFirst().get("operation"));
        for(String expression:List.of("不要清空备注","不要把备注清空","别清掉备注","备注先别清空"))assertTrue(((List<?>)service.fill(student,new FillRequest("example","表单",List.of(remark),expression)).get("assignments")).isEmpty(),expression);
        var unrequested=service.fill(student,new FillRequest("example","表单",List.of(remark),"这个怎么填"));
        assertTrue(((List<?>)unrequested.get("assignments")).isEmpty());assertFalse(((List<?>)unrequested.get("questions")).isEmpty());
    }
    @Test void naturalOptionExpressionAuthorizesOverwrite(){
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"status\",\"value\":\"草稿\",\"confidence\":0.95}]}");
        FieldDescriptor status = new FieldDescriptor("status", "状态", "select",
                List.of(Map.of("value", "DRAFT", "label", "草稿"), Map.of("value", "PUBLISHED", "label", "发布")), true, null, null);
        var assignments=(List<Map<String,Object>>)service.fill(student,new FillRequest("admin:resource","发布文档",List.of(status),"状态就用草稿")).get("assignments");
        assertEquals(1,assignments.size());
        assertEquals("DRAFT",assignments.getFirst().get("value"));
        assertEquals(true,assignments.getFirst().get("overwriteAllowed"));
    }

    @Test void unmentionedExistingFieldCannotReceiveOverwritePermission(){
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"companyCity\",\"value\":\"杭州\"}]}");
        var assignments=(List<Map<String,Object>>)service.fill(student,request(city)).get("assignments");
        assertEquals(false,assignments.getFirst().get("overwriteAllowed"));
    }
    @Test void negativeCountChangeMustNeverOverwrite(){
        FieldDescriptor count=new FieldDescriptor("plannedRecruit","招聘人数","number",null,true,null,"number");
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"plannedRecruit\",\"value\":\"5\"}]}");
        for(String expression:List.of("不要把人数改成五个","人数不能改成五个","别修改人数","人数不改为5"))assertTrue(((List<?>)service.fill(student,new FillRequest("example","表单",List.of(count),expression)).get("assignments")).isEmpty(),expression);
    }

    @Test void unmatchedOptionBecomesQuestionInsteadOfGuess() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"status\",\"value\":\"找不到的选项\",\"confidence\":0.9}]}");
        Map<String, Object> result = service.fill(student, request(status));
        assertTrue(((List<?>) result.get("assignments")).isEmpty());
        List<String> questions = (List<String>) result.get("questions");
        assertEquals(1, questions.size());
        assertTrue(questions.get(0).contains("当前状态"));
    }

    @Test void permissionRevokedWhileModelRunsDiscardsAssignments() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"company\",\"value\":\"杭州云启科技\",\"confidence\":0.9}]}");
        when(capabilities.has(student, "form_fill")).thenReturn(true, false);
        AgentException error = assertThrows(AgentException.class, () -> service.fill(student, request(company)));
        assertEquals("CAPABILITY_DISABLED", error.getCode());
    }

    @Test void featureDisabledWhileModelRunsDiscardsAssignments() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[]}");
        when(tasks.enabled()).thenReturn(true, false);
        AgentException error = assertThrows(AgentException.class, () -> service.fill(student, request(company)));
        assertEquals("AI_DISABLED", error.getCode());
    }

    @Test void multiSelectNormalizesEverySpokenOption() {
        FieldDescriptor majors = new FieldDescriptor("requiredMajors", "招聘专业", "select",
                List.of(
                        Map.of("value", "101", "label", "生物医学工程"),
                        Map.of("value", "102", "label", "计算机科学与技术")),
                true, null, "array");
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":["
                + "{\"prop\":\"requiredMajors\",\"value\":\"生物医学工程、计算机科学与技术\",\"confidence\":0.96}]}");
        Map<String, Object> result = service.fill(student, request(majors));
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");
        assertEquals("101、102", assignments.get(0).get("value"));
    }

    @Test void multiSelectRejectsUnknownOptionWithoutPartialWrite() {
        FieldDescriptor majors = new FieldDescriptor("requiredMajors", "招聘专业", "select",
                List.of(
                        Map.of("value", "101", "label", "生物医学工程"),
                        Map.of("value", "102", "label", "计算机科学与技术")),
                true, null, "array");
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":["
                + "{\"prop\":\"requiredMajors\",\"value\":\"生物医学工程、不存在的专业\",\"confidence\":0.96}]}");
        Map<String, Object> result = service.fill(student, request(majors));
        assertTrue(((List<?>) result.get("assignments")).isEmpty());
        List<String> questions = (List<String>) result.get("questions");
        assertTrue(questions.get(0).contains("招聘专业"));
    }

    @Test void chatIntentPassesThroughForFollowUpRouting() {
        modelReturns("{\"intent\":\"chat\",\"assignments\":[],\"questions\":[]}");
        Map<String, Object> result = service.fill(student, request(company));
        assertEquals("chat", result.get("intent"));
        assertTrue(((List<?>) result.get("assignments")).isEmpty());
    }

    @Test void invalidInputIsRejected() {
        assertThrows(AgentException.class, () -> service.fill(student,
                new FillRequest("f", "表单", List.of(company), "  ")));
        assertThrows(AgentException.class, () -> service.fill(student,
                new FillRequest(null, "表单", List.of(company), "填内容")));
        assertThrows(AgentException.class, () -> service.fill(student,
                new FillRequest("f", "表单", List.of(), "填内容")));
        assertThrows(AgentException.class, () -> service.fill(student,
                new FillRequest("f", "表单", java.util.stream.IntStream.range(0, 41)
                        .mapToObj(i -> new FieldDescriptor("p" + i, "字段" + i, "input", null, null, null, null)).toList(), "填内容")));
    }

    @Test void nonJsonModelOutputFailsExplicitly() {
        modelReturns("这不是JSON，我只是想聊聊。");
        AgentException error = assertThrows(AgentException.class,
                () -> service.fill(student, request(company)));
        assertEquals("MODEL_UNAVAILABLE", error.getCode());
    }

    @Test void markdownFencedJsonIsAccepted() {
        modelReturns("```json\n{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"company\",\"value\":\"云启\",\"confidence\":0.9}]}\n```");
        Map<String, Object> result = service.fill(student, request(company));
        assertEquals(1, ((List<?>) result.get("assignments")).size());
    }

    @Test void providerFailureBecomesExplicitModelUnavailable() {
        when(model.call(anyList(), anyList())).thenThrow(new RuntimeException("402 Insufficient Balance"));
        AgentException error = assertThrows(AgentException.class,
                () -> service.fill(student, request(company)));
        assertEquals("MODEL_UNAVAILABLE", error.getCode());
        assertEquals(502, error.getHttpStatus());
    }

    @Test void duplicateFieldAssignmentsRequireClarification() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"company\",\"value\":\"A\"},{\"prop\":\"company\",\"value\":\"B\"}]}");
        Map<String, Object> result = service.fill(student, request(company));
        assertTrue(((List<?>) result.get("assignments")).isEmpty());
        assertEquals(1, ((List<?>) result.get("questions")).size());
    }

    @Test void allowCustomIsExplicitAndEmptyOptionsFailClosed() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"choice\",\"value\":\"新岗位\"}]}");
        FieldDescriptor fixed = new FieldDescriptor("choice", "岗位", "select", List.of(), false, null, null);
        assertTrue(((List<?>) service.fill(student, request(fixed)).get("assignments")).isEmpty());
        FieldDescriptor custom = new FieldDescriptor("choice", "岗位", "select", List.of(), false, null, null, true);
        assertEquals(1, ((List<?>) service.fill(student, request(custom)).get("assignments")).size());
    }

    @Test void lowConfidenceValueIsClarifiedInsteadOfApplied() {
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"company\",\"value\":\"不确定公司\",\"confidence\":0.3}]}");
        Map<String, Object> result = service.fill(student, request(company));
        assertTrue(((List<?>) result.get("assignments")).isEmpty());
        assertFalse(((List<?>) result.get("questions")).isEmpty());
    }

    @Test void datetimeAndLongTextAreNotSilentlyTruncated() {
        String description = "完整描述".repeat(160);
        modelReturns("{\"intent\":\"form_fill\",\"assignments\":[{\"prop\":\"description\",\"value\":\"" + description + "\"},{\"prop\":\"time\",\"value\":\"2026-09-25 14:30:00\"}]}");
        Map<String, Object> result = service.fill(student, request(
                new FieldDescriptor("description", "岗位描述", "textarea", null, false, null, null),
                new FieldDescriptor("time", "面试时间", "datetime", null, false, null, "string")));
        List<Map<String, Object>> assignments = (List<Map<String, Object>>) result.get("assignments");
        assertEquals(description, assignments.get(0).get("value"));
        assertEquals("2026-09-25 14:30:00", assignments.get(1).get("value"));
    }
}
