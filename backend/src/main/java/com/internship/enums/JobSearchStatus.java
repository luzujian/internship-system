package com.internship.enums;

/**
 * 学生端和教师端统一使用的求职进度展示状态。
 * 数据库中的 numeric internshipStatus 保持不变，本枚举只用于接口展示。
 */
public enum JobSearchStatus {
    NOT_FOUND("暂无有效投递"),
    NO_OFFER("已投递未录用"),
    PENDING_CONFIRMATION("已录用待确认"),
    CONFIRMED("已定单位待实习"),
    IN_PROGRESS("实习进行中"),
    ENDED("实习已结束"),
    INTERRUPTED("实习已中断"),
    DELAYED("已延期实习"),
    UNKNOWN("状态待核实");

    private final String text;

    JobSearchStatus(String text) {
        this.text = text;
    }

    public String getText() {
        return text;
    }

    public static JobSearchStatus fromEstablishedInternshipStatus(Integer status) {
        if (status == null) {
            return UNKNOWN;
        }
        return switch (status) {
            case 1 -> PENDING_CONFIRMATION;
            case 2 -> CONFIRMED;
            case 3 -> IN_PROGRESS;
            case 4 -> ENDED;
            case 5 -> INTERRUPTED;
            case 6 -> DELAYED;
            default -> UNKNOWN;
        };
    }
}
