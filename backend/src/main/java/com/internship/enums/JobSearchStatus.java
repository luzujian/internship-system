package com.internship.enums;

/**
 * 学生端和教师端统一使用的求职进度展示状态。
 * 数据库中的 numeric internshipStatus 保持不变，本枚举只用于接口展示。
 */
public enum JobSearchStatus {
    NOT_FOUND("未投递"),
    NO_OFFER("无 Offer"),
    PENDING_CONFIRMATION("待确认"),
    CONFIRMED("已确定"),
    IN_PROGRESS("实习中"),
    ENDED("已结束"),
    INTERRUPTED("已中断"),
    DELAYED("延期"),
    UNKNOWN("未知");

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
