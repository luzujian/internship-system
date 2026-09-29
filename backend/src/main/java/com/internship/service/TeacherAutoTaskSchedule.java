package com.internship.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.internship.exception.BusinessException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** 自动化任务调度配置：窗口、一次性、每日或每周固定钟点。 */
public record TeacherAutoTaskSchedule(Mode mode, LocalDate date, LocalTime time,
                                      Set<DayOfWeek> weekdays) {
    public enum Mode { WINDOW, ONCE, DAILY, WEEKLY, MANUAL }
    public enum DueState { NOT_DUE, ALREADY_DONE, DUE, MISSED }
    public record DueEvaluation(DueState state, OffsetDateTime occurrence) {}

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    public static TeacherAutoTaskSchedule window() {
        return new TeacherAutoTaskSchedule(Mode.WINDOW, null, null, Set.of());
    }

    public static TeacherAutoTaskSchedule parse(String value, ObjectMapper mapper) {
        if (value == null || value.isBlank()) return window();
        try {
            JsonNode root = mapper.readTree(value);
            if (!root.isObject()) throw new IllegalArgumentException();
            String modeText = root.path("mode").asText("WINDOW").trim().toUpperCase(Locale.ROOT);
            Mode mode;
            try {
                mode = Mode.valueOf(modeText);
            } catch (IllegalArgumentException e) {
                throw new BusinessException("调度模式仅支持截止窗口、指定时间、每日、每周或确认后执行一次。");
            }
            if (mode == Mode.WINDOW) return window();
            if (mode == Mode.MANUAL) return new TeacherAutoTaskSchedule(mode, null, null, Set.of());

            LocalDate date = null;
            LocalTime time = null;
            Set<DayOfWeek> weekdays = new LinkedHashSet<>();
            if (mode == Mode.ONCE) {
                date = requiredDate(root.path("date").asText(null), "指定日期不能为空。");
                time = requiredTime(root.path("time").asText(null), "指定时间不能为空。");
            } else {
                time = requiredTime(root.path("time").asText(null), "执行钟点不能为空。");
                if (mode == Mode.WEEKLY) {
                    JsonNode values = root.path("weekdays");
                    if (!values.isArray() || values.isEmpty()) throw new BusinessException("每周任务至少选择一个星期。");
                    for (JsonNode item : values) {
                        int day = item.asInt(-1);
                        if (day < 1 || day > 7) throw new BusinessException("星期取值必须在1至7之间。");
                        weekdays.add(DayOfWeek.of(day));
                    }
                }
            }
            return new TeacherAutoTaskSchedule(mode, date, time, Set.copyOf(weekdays));
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("调度配置格式不正确。");
        }
    }

    public static String normalize(String value, ObjectMapper mapper) {
        TeacherAutoTaskSchedule schedule = parse(value, mapper);
        return schedule.toJson(mapper);
    }

    public static String fromDraft(Object raw, ObjectMapper mapper) {
        if (raw == null) return null;
        try {
            return normalize(mapper.writeValueAsString(raw), mapper);
        } catch (Exception e) {
            throw new BusinessException("调度配置格式不正确。");
        }
    }

    public String toJson(ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("mode", mode.name());
        if (date != null) root.put("date", date.format(DATE));
        if (time != null) root.put("time", time.format(TIME));
        if (weekdays != null && !weekdays.isEmpty()) {
            List<Integer> values = weekdays.stream().map(DayOfWeek::getValue).sorted().toList();
            root.set("weekdays", mapper.valueToTree(values));
        }
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new BusinessException("调度配置序列化失败。");
        }
    }

    public boolean isWindow() {
        return mode == Mode.WINDOW;
    }

    /** 判断当前是否应处理某个计划时刻，并给出同一故障恢复窗口内的最近时刻。 */
    public DueEvaluation evaluate(OffsetDateTime now, OffsetDateTime lastRun, Duration grace) {
        if (mode == Mode.MANUAL) return new DueEvaluation(DueState.NOT_DUE, null);
        if (mode == Mode.WINDOW) return new DueEvaluation(DueState.DUE, now.truncatedTo(ChronoUnit.MINUTES));
        OffsetDateTime occurrence = latestOccurrence(now);
        if (occurrence == null || now.isBefore(occurrence)) return new DueEvaluation(DueState.NOT_DUE, occurrence);
        if (lastRun != null && !lastRun.isBefore(occurrence)) return new DueEvaluation(DueState.ALREADY_DONE, occurrence);
        Duration late = Duration.between(occurrence, now);
        if (late.compareTo(grace) <= 0) return new DueEvaluation(DueState.DUE, occurrence);
        return new DueEvaluation(DueState.MISSED, occurrence);
    }

    private OffsetDateTime latestOccurrence(OffsetDateTime now) {
        ZoneId zone = now.getOffset();
        if (mode == Mode.ONCE) {
            return ZonedDateTime.of(date, time, zone).toOffsetDateTime();
        }
        LocalDate start = mode == Mode.WEEKLY ? now.toLocalDate().minusDays(6) : now.toLocalDate();
        for (LocalDate day = now.toLocalDate(); !day.isBefore(start); day = day.minusDays(1)) {
            if (mode == Mode.WEEKLY && !weekdays.contains(day.getDayOfWeek())) continue;
            OffsetDateTime candidate = ZonedDateTime.of(day, time, zone).toOffsetDateTime();
            if (!candidate.isAfter(now)) return candidate;
        }
        return null;
    }

    private static LocalDate requiredDate(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(message);
        try {
            return LocalDate.parse(value.trim(), DATE);
        } catch (DateTimeParseException e) {
            throw new BusinessException("日期格式应为yyyy-MM-dd。");
        }
    }

    private static LocalTime requiredTime(String value, String message) {
        if (value == null || value.isBlank()) throw new BusinessException(message);
        try {
            LocalTime parsed = LocalTime.parse(value.trim(), TIME);
            if (parsed.getSecond() != 0 || parsed.getNano() != 0) throw new DateTimeParseException("seconds", value, 0);
            return parsed;
        } catch (DateTimeParseException e) {
            throw new BusinessException("时间格式应为HH:mm。");
        }
    }
}
