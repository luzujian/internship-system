package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TeacherAutoTaskScheduleTest {
    private final ObjectMapper json = new ObjectMapper();
    private final ZoneId zone = ZoneId.of("Asia/Shanghai");

    @Test void nullConfigKeepsWindowCompatibility() {
        TeacherAutoTaskSchedule schedule = TeacherAutoTaskSchedule.parse(null, json);
        assertEquals(TeacherAutoTaskSchedule.Mode.WINDOW, schedule.mode());
        OffsetDateTime now = OffsetDateTime.of(2026, 9, 17, 9, 30, 0, 0, ZoneOffset.ofHours(8));
        assertEquals(TeacherAutoTaskSchedule.DueState.DUE, schedule.evaluate(now, null, Duration.ofMinutes(60)).state());
    }

    @Test void onceUsesGraceWindowAndPersistedLastRun() {
        TeacherAutoTaskSchedule schedule = TeacherAutoTaskSchedule.parse(
                "{\"mode\":\"ONCE\",\"date\":\"2026-09-17\",\"time\":\"09:30\"}", json);
        OffsetDateTime due = OffsetDateTime.of(2026, 9, 17, 9, 35, 0, 0, ZoneOffset.ofHours(8));
        TeacherAutoTaskSchedule.DueEvaluation first = schedule.evaluate(due, null, Duration.ofMinutes(60));
        assertEquals(TeacherAutoTaskSchedule.DueState.DUE, first.state());
        TeacherAutoTaskSchedule.DueEvaluation repeated = schedule.evaluate(due, first.occurrence(), Duration.ofMinutes(60));
        assertEquals(TeacherAutoTaskSchedule.DueState.ALREADY_DONE, repeated.state());
        OffsetDateTime late = OffsetDateTime.of(2026, 9, 17, 10, 31, 0, 0, ZoneOffset.ofHours(8));
        assertEquals(TeacherAutoTaskSchedule.DueState.MISSED,
                schedule.evaluate(late, null, Duration.ofMinutes(60)).state());
    }

    @Test void dailyAndWeeklyResolveLatestScheduledOccurrence() {
        TeacherAutoTaskSchedule weekly = TeacherAutoTaskSchedule.parse(
                "{\"mode\":\"WEEKLY\",\"time\":\"17:00\",\"weekdays\":[5]}", json);
        OffsetDateTime friday = ZonedDateTime.of(2026, 9, 18, 17, 10, 0, 0, zone).toOffsetDateTime();
        TeacherAutoTaskSchedule.DueEvaluation due = weekly.evaluate(friday, null, Duration.ofMinutes(60));
        assertEquals(TeacherAutoTaskSchedule.DueState.DUE, due.state());
        assertEquals(DayOfWeek.FRIDAY, due.occurrence().getDayOfWeek());

        TeacherAutoTaskSchedule daily = TeacherAutoTaskSchedule.parse(
                "{\"mode\":\"DAILY\",\"time\":\"09:30\"}", json);
        OffsetDateTime nextMorning = ZonedDateTime.of(2026, 9, 19, 9, 35, 0, 0, zone).toOffsetDateTime();
        assertEquals(2026, daily.evaluate(nextMorning, null, Duration.ofMinutes(60)).occurrence().getYear());
        assertEquals(9, daily.evaluate(nextMorning, null, Duration.ofMinutes(60)).occurrence().getHour());
    }

    @Test void invalidFixedScheduleFailsClosed() {
        assertThrows(BusinessException.class, () -> TeacherAutoTaskSchedule.parse(
                "{\"mode\":\"WEEKLY\",\"time\":\"17:00\",\"weekdays\":[]}", json));
        assertThrows(BusinessException.class, () -> TeacherAutoTaskSchedule.parse(
                "{\"mode\":\"ONCE\",\"date\":\"2026-09-17\"}", json));
        assertThrows(BusinessException.class, () -> TeacherAutoTaskSchedule.parse(
                "{\"mode\":\"DAILY\",\"time\":\"25:00\"}", json));
    }

    @Test void manualOnceCanNeverBePickedUpByBackgroundScheduler() {
        var schedule = TeacherAutoTaskSchedule.parse("{\"mode\":\"MANUAL\"}", json);
        assertEquals(TeacherAutoTaskSchedule.Mode.MANUAL, schedule.mode());
        assertEquals(TeacherAutoTaskSchedule.DueState.NOT_DUE,
                schedule.evaluate(OffsetDateTime.now(zone), null, Duration.ofHours(24)).state());
    }
}
