package com.internship.agent.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * 平台统一时钟：发给模型的“现在”与服务端定时/校验共用同一时钟
 * （Asia/Shanghai 服务器时间），避免模型凭训练记忆虚构日期。
 */
public final class AgentClock {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd");
    private static final DateTimeFormatter DATE_TIME_CN =
            DateTimeFormatter.ofPattern("uuuu年MM月dd日HH:mm");

    private AgentClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    /**
     * 拼到各模型 SYSTEM 首行，例如：
     * 现在是2026年09月18日14:30星期五（Asia/Shanghai）。涉及今天/明天/几天后等相对时间时以此为准换算成绝对日期，不得虚构日期。
     */
    public static String nowLine() {
        LocalDateTime t = now();
        String week = t.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.CHINA);
        return "现在是" + t.format(DATE_TIME_CN) + week
                + "（Asia/Shanghai）。涉及今天/明天/几天后等相对时间时以此为准换算成绝对日期，不得虚构日期。";
    }

    public static String todayText() {
        return today().format(DATE);
    }
}
