package com.internship.agent.business;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class AgentClockTest {
    @Test void nowLineCarriesServerDateTimeAndWeekday() {
        String line = AgentClock.nowLine();
        assertTrue(line.startsWith("现在是"), "时间行应以“现在是”开头: " + line);
        assertTrue(line.matches("现在是\\d{4}年\\d{2}月\\d{2}日\\d{2}:\\d{2}星期[一二三四五六日天]（Asia/Shanghai）。.*"),
                "时间行格式异常: " + line);
        assertTrue(line.contains("不得虚构日期"));
        String expectedDate = LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(DateTimeFormatter.ofPattern("uuuu年MM月dd日"));
        assertTrue(line.contains(expectedDate), "时间行日期应与服务器时钟一致: " + line);
    }

    @Test void todayMatchesServerClock() {
        assertEquals(LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("uuuu-MM-dd")),
                AgentClock.todayText());
    }
}
