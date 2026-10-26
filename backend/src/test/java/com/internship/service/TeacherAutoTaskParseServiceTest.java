package com.internship.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TeacherAutoTaskParseService 离线单测：聚焦应用侧白名单校验、默认值与追问（不调用模型）。
 * 覆盖：非法触发条件/动作回落、临期天数边界、OFFER 忽略天数、频率三档、结束日期、短信追问、文案兜底。
 */
class TeacherAutoTaskParseServiceTest {

    private TeacherAutoTaskParseService service;

    @BeforeEach void setup() {
        service = new TeacherAutoTaskParseService(null);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> invokeValidate(Map<String, Object> result, String original) throws Exception {
        Method m = TeacherAutoTaskParseService.class.getDeclaredMethod("validate", Map.class, String.class);
        m.setAccessible(true);
        return (Map<String, Object>) m.invoke(service, result, original);
    }

    private Map<String, Object> raw(String trigger, Object days, String action, Object freq) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("triggerCondition", trigger);
        r.put("triggerDays", days);
        r.put("action", action);
        r.put("frequencyMinutes", freq);
        return r;
    }

    @Test void invalidTriggerAndActionNeverFallBackToDifferentBusiness() throws Exception {
        Map<String, Object> draft = invokeValidate(raw("NOT_A_TRIGGER", 2, "NOT_AN_ACTION", 1440),
                "提醒鉴定没交的同学发邮件");
        assertEquals("UNSUPPORTED", draft.get("triggerCondition"));
        assertEquals("UNSUPPORTED", draft.get("action"));
        assertNull(draft.get("triggerDays"));
        assertEquals(false, draft.get("needsConfirm"));
        assertFalse(((List<?>) draft.get("questions")).isEmpty());
    }

    @Test void scheduledReminderKeepsTheSpecifiedStudentAndHasNoBusinessDeadline() throws Exception {
        var model = raw("SCHEDULED_REMINDER", 2, "EMAIL", 1440);
        model.put("schedule", Map.of("mode", "DAILY", "time", "14:42"));
        model.put("targetMode", "SELECTED");
        model.put("messageTemplate", "请回到实习岗位。");
        var draft = invokeValidate(model, "我想要创建一个自动化任务。\n任务内容是：通过邮件提醒，通知学生s001请回到实习岗位。\n执行条件是：每天下午两点42分准时提醒。");
        assertEquals("SCHEDULED_REMINDER", draft.get("triggerCondition"));
        assertNull(draft.get("triggerDays"));
        assertEquals(List.of("s001"), draft.get("targetQueries"));
        assertEquals(true, draft.get("needsConfirm"));
        assertEquals(List.of(), draft.get("questions"));
        assertTrue(draft.get("scheduleConfig").toString().contains("14:42"));
    }

    @Test void scheduledReminderRequiresTimeAndAudience() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "EMAIL", 1440);
        model.put("messageTemplate", "请返岗");
        var draft = invokeValidate(model, "帮我发提醒");
        assertEquals(false, draft.get("needsConfirm"));
        assertTrue(draft.get("questions").toString().contains("执行时间"));
        assertTrue(draft.get("questions").toString().contains("哪些学生"));
    }

    @Test void immediateRequestUsesManualConfirmationWithoutInventingAClockTime() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "EMAIL", 1440);
        model.put("messageTemplate", "请返岗"); model.put("targetMode", "SCOPE");
        var draft = invokeValidate(model, "通过邮件提醒我负责范围内所有学生，请返岗，执行时间是：立即发送一次");
        assertTrue(draft.get("scheduleConfig").toString().contains("MANUAL"));
        assertEquals(true, draft.get("needsConfirm"));
    }

    @Test void immediateMeetingNoticeDoesNotAskForTheMeetingDaySendTime() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "STATION_REMINDER", 1440);
        model.put("schedule", Map.of("mode", "WINDOW"));
        model.put("targetMode", "SCOPE");
        model.put("messageTemplate", "请明天参加实习会议。");
        model.put("questions", List.of("明天希望在哪个具体时间发送这条站内通知？"));
        var draft = invokeValidate(model, "现在通过站内通知全部负责的学生，明天参加实习会议，只发一次。");
        assertEquals("{\"mode\":\"MANUAL\"}", draft.get("scheduleConfig"));
        assertEquals(List.of(), draft.get("questions"));
        assertEquals(true, draft.get("needsConfirm"));
        assertEquals("请明天参加实习会议。", draft.get("messageTemplate"));
    }

    @Test void explicitImmediateSendOverridesAnInventedFutureSendTime() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "STATION_REMINDER", 1440);
        model.put("schedule", Map.of("mode", "ONCE", "date", "2099-10-11", "time", "09:00"));
        model.put("targetMode", "SCOPE");
        model.put("messageTemplate", "请明天参加会议。");
        var draft = invokeValidate(model, "请现在通知全部负责的学生，明天参加会议。");
        assertEquals("{\"mode\":\"MANUAL\"}", draft.get("scheduleConfig"));
        assertEquals(true, draft.get("needsConfirm"));
    }

    @Test void immediateSendDoesNotRemoveAnAudienceQuestion() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "EMAIL", 1440);
        model.put("schedule", Map.of("mode", "WINDOW"));
        model.put("messageTemplate", "请参加会议。");
        model.put("questions", List.of("请补充发送时间和提醒对象。"));
        var draft = invokeValidate(model, "现在发送会议提醒。");
        assertEquals(false, draft.get("needsConfirm"));
        assertTrue(draft.get("questions").toString().contains("提醒对象"));
    }

    @Test void laterCorrectionCanReplaceImmediateSendWithADailySchedule() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "EMAIL", 1440);
        model.put("schedule", Map.of("mode", "DAILY", "time", "09:00"));
        model.put("targetMode", "SCOPE");
        model.put("messageTemplate", "请参加会议。");
        var draft = invokeValidate(model, "现在通知全部学生参加会议。\n教师补充或修正，冲突处以本次为准：改成每天09:00提醒。");
        assertEquals("{\"mode\":\"DAILY\",\"time\":\"09:00\"}", draft.get("scheduleConfig"));
    }

    @Test void negatedImmediateSendKeepsTheSpecifiedFutureSchedule() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "EMAIL", 1440);
        model.put("schedule", Map.of("mode", "ONCE", "date", "2099-10-11", "time", "09:00"));
        model.put("targetMode", "SCOPE");
        model.put("messageTemplate", "请参加会议。");
        var draft = invokeValidate(model, "不要现在发送，2099-10-11 09:00通知全部负责的学生。");
        assertEquals("{\"mode\":\"ONCE\",\"date\":\"2099-10-11\",\"time\":\"09:00\"}", draft.get("scheduleConfig"));
    }

    @Test void recipientCorrectionDoesNotRetainTheOldAccount() throws Exception {
        var model = raw("SCHEDULED_REMINDER", null, "EMAIL", 1440);
        model.put("messageTemplate", "请返岗");
        model.put("schedule", Map.of("mode", "DAILY", "time", "14:42"));
        model.put("targetQueries", List.of("s001", "s002"));
        var draft = invokeValidate(model, "每天14:42通知学生s001返岗\n教师补充或修正，冲突处以本次为准：只提醒学生s002");
        assertEquals(List.of("s002"), draft.get("targetQueries"));
    }

    @Test void triggerDaysBoundaryAndDefault() throws Exception {
        assertEquals(2, invokeValidate(raw("APPRAISAL_MISSING", null, "STATION_REMINDER", 1440), "").get("triggerDays"),
                "未指定默认 2 天");
        assertEquals(7, invokeValidate(raw("APPRAISAL_MISSING", 7, "STATION_REMINDER", 1440), "").get("triggerDays"));
        assertEquals(2, invokeValidate(raw("APPRAISAL_MISSING", 99, "STATION_REMINDER", 1440), "").get("triggerDays"),
                "超范围（>30）回落默认 2 天");
        assertEquals(2, invokeValidate(raw("APPRAISAL_MISSING", 0, "STATION_REMINDER", 1440), "").get("triggerDays"),
                "非正数回落默认 2 天");
    }

    @Test void offerTriggerAlwaysIgnoresTriggerDays() throws Exception {
        assertNull(invokeValidate(raw("OFFER_PENDING", 5, "STATION_REMINDER", 1440), "").get("triggerDays"),
                "Offer 待确认条件不使用临期天数");
    }

    @Test void zeroTriggerDaysDoesNotThrowAndFallsBack() throws Exception {
        // 回归：模型可能返回 triggerDays=0（意为“当天截止”），
        // 旧实现的三元表达式在 fallback=null 时自动拆箱导致 NPE，解析整体失败。
        assertEquals(2, invokeValidate(raw("APPRAISAL_MISSING", 0, "STATION_REMINDER", 1440), "").get("triggerDays"),
                "triggerDays=0 应安全回落默认值而非抛 NPE");
        assertEquals(2, invokeValidate(raw("APPRAISAL_MISSING", "0", "STATION_REMINDER", 1440), "").get("triggerDays"),
                "字符串 \"0\" 同样安全回落");
        assertNull(invokeValidate(raw("OFFER_PENDING", 0, "STATION_REMINDER", 1440), "").get("triggerDays"),
                "Offer 场景 triggerDays=0 应安全置空");
    }

    @Test void frequencyOnlyAcceptsThreeAllowedValues() throws Exception {
        assertEquals(60, invokeValidate(raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 60), "").get("frequencyMinutes"));
        assertEquals(10080, invokeValidate(raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 10080), "").get("frequencyMinutes"));
        assertEquals(1440, invokeValidate(raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 30), "").get("frequencyMinutes"),
                "非法频率回落每天");
        assertEquals(1440, invokeValidate(raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", null), "").get("frequencyMinutes"),
                "缺省频率为每天");
    }

    @Test void pastEndDateIsDiscarded() throws Exception {
        Map<String, Object> r = raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 1440);
        r.put("endAt", "2020-01-01");
        assertNull(invokeValidate(r, "").get("endAt"), "早于当天的结束日期被丢弃");
        r.put("endAt", "not-a-date");
        assertNull(invokeValidate(r, "").get("endAt"), "非法日期格式被丢弃");
    }

    @Test void smsRequestAddsFollowUpQuestion() throws Exception {
        Map<String, Object> draft = invokeValidate(raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 1440),
                "给没交鉴定的同学发短信");
        List<?> questions = (List<?>) draft.get("questions");
        assertFalse(questions.isEmpty(), "提到短信时应生成追问");
        assertTrue(String.valueOf(questions.get(0)).contains("短信"), "追问应说明短信通道未接入");
        assertEquals(false, draft.get("needsConfirm"), "存在追问时 needsConfirm 为 false");
    }

    @Test void completeRequestNeedsNoConfirmation() throws Exception {
        Map<String, Object> draft = invokeValidate(raw("APPRAISAL_MISSING", 2, "EMAIL", 1440),
                "距离鉴定截止还有两天时发邮件提醒");
        assertEquals(true, draft.get("needsConfirm"));
        assertFalse(String.valueOf(draft.get("messageTemplate")).isBlank(), "文案为空时使用默认文案");
        assertFalse(String.valueOf(draft.get("name")).isBlank(), "任务名为空时自动生成");
    }

    @Test void messageTemplateIsTruncatedTo200Chars() throws Exception {
        Map<String, Object> r = raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 1440);
        r.put("messageTemplate", "提".repeat(300));
        assertEquals(200, String.valueOf(invokeValidate(r, "").get("messageTemplate")).length());
    }

    @Test void fixedClockSchedulesAreNormalizedAndInferred() throws Exception {
        Map<String, Object> weekly = raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 1440);
        weekly.put("schedule", Map.of("mode", "WEEKLY", "time", "17:00", "weekdays", List.of(5)));
        String weeklyJson = String.valueOf(invokeValidate(weekly, "每周五17:00提醒").get("scheduleConfig"));
        assertTrue(weeklyJson.contains("\"mode\":\"WEEKLY\""));
        assertTrue(weeklyJson.contains("\"weekdays\":[5]"));

        String dailyJson = String.valueOf(invokeValidate(
                raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 1440), "每天9:30提醒鉴定")
                .get("scheduleConfig"));
        assertTrue(dailyJson.contains("\"mode\":\"DAILY\""));
        assertTrue(dailyJson.contains("\"time\":\"09:30\""));

        String onceJson = String.valueOf(invokeValidate(
                raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 1440), "2026-09-20 09:30提醒鉴定")
                .get("scheduleConfig"));
        assertTrue(onceJson.contains("\"mode\":\"ONCE\""));
        assertTrue(onceJson.contains("\"date\":\"2026-09-20\""));
    }

    @Test void relativeDeadlineRemainsWindowMode() throws Exception {
        String value = String.valueOf(invokeValidate(
                raw("APPRAISAL_MISSING", 2, "STATION_REMINDER", 1440), "截止前两天提醒")
                .get("scheduleConfig"));
        assertTrue(value.contains("\"mode\":\"WINDOW\""));
    }
}
