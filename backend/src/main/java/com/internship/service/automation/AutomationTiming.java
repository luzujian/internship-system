package com.internship.service.automation;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.*;
import static com.internship.service.automation.AutomationRules.fail;

/** Calendar schedules stay relative; activation never backfills a time before activation. */
public final class AutomationTiming {
    private AutomationTiming() {}
    public static final Set<String> MODES = Set.of("ONCE", "DAILY", "WEEKLY", "MONTHLY", "INTERVAL", "CONDITION");
    public static void validate(JsonNode trigger) {
        String mode = trigger.path("mode").asText();
        if (Set.of("IMMEDIATE","MANUAL").contains(mode)) fail("「立即一次」已停用，请重新选择执行时间。");
        if (!MODES.contains(mode)) fail("请选择支持的执行时间。");
        try {
            if (Set.of("ONCE", "DAILY", "WEEKLY", "MONTHLY").contains(mode)) times(trigger);
            if (mode.equals("ONCE")) LocalDate.parse(trigger.path("date").asText());
            if (mode.equals("WEEKLY")) numbers(trigger.path("weekdays"), 1, 7);
            if (mode.equals("MONTHLY")) numbers(trigger.path("monthdays"), 1, 31);
            if (mode.equals("INTERVAL") && (trigger.path("minutes").asInt() < 1 || trigger.path("minutes").asInt() > 525600)) fail("执行间隔需要为1至525600分钟。");
        } catch (RuntimeException e) { fail("执行日期、星期或钟点不完整，请核对时间。"); }
    }
    private static List<LocalTime> times(JsonNode n) {
        List<LocalTime> result = new ArrayList<>();
        if (n.path("times").isArray()) n.path("times").forEach(v -> result.add(LocalTime.parse(v.asText())));
        else result.add(LocalTime.parse(n.path("time").asText()));
        if (result.isEmpty() || result.size() > 24 || result.stream().anyMatch(t -> t.getSecond() != 0 || t.getNano() != 0)) fail("执行时间按分钟设置，每天最多24个时刻。");
        return result.stream().distinct().sorted().toList();
    }
    private static Set<Integer> numbers(JsonNode n, int min, int max) {
        if (!n.isArray() || n.isEmpty()) fail("请选择执行日期。");
        Set<Integer> result = new HashSet<>(); n.forEach(v -> { if (!v.isIntegralNumber() || v.asInt() < min || v.asInt() > max) fail("执行日期不正确。"); result.add(v.asInt()); }); return result;
    }
    public static LocalDateTime next(JsonNode trigger, LocalDateTime after) {
        String mode = trigger.path("mode").asText();
        if (mode.equals("CONDITION")) return after.plusMinutes(1).withSecond(0).withNano(0);
        if (mode.equals("INTERVAL")) return after.plusMinutes(trigger.path("minutes").asInt());
        List<LocalTime> times = times(trigger);
        if (mode.equals("ONCE")) {
            LocalDateTime at = LocalDate.parse(trigger.path("date").asText()).atTime(times.getFirst()); return at.isAfter(after) ? at : null;
        }
        Set<Integer> weekdays = mode.equals("WEEKLY") ? numbers(trigger.path("weekdays"), 1, 7) : Set.of();
        Set<Integer> monthdays = mode.equals("MONTHLY") ? numbers(trigger.path("monthdays"), 1, 31) : Set.of();
        for (int offset = 0; offset <= 370; offset++) {
            LocalDate date = after.toLocalDate().plusDays(offset);
            if (!weekdays.isEmpty() && !weekdays.contains(date.getDayOfWeek().getValue())) continue;
            if (!monthdays.isEmpty() && !monthdays.contains(date.getDayOfMonth())) continue;
            for (LocalTime t : times) { LocalDateTime at = date.atTime(t); if (at.isAfter(after)) return at; }
        }
        return null;
    }
    public static String describe(JsonNode t) {
        String mode = t.path("mode").asText();
        return switch (mode) {
            case "CONDITION" -> "条件满足时执行";
            case "INTERVAL" -> "每隔" + t.path("minutes").asInt() + "分钟";
            case "ONCE" -> t.path("date").asText() + " " + times(t).getFirst();
            case "DAILY" -> "每天 " + String.join("、",times(t).stream().map(LocalTime::toString).toList());
            case "WEEKLY" -> "每周" + t.path("weekdays") + " " + times(t);
            case "MONTHLY" -> "每月" + t.path("monthdays") + "日 " + times(t);
            default -> "时间待完善";
        };
    }
}
