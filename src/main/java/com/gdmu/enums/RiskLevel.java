package com.gdmu.enums;

public enum RiskLevel {
    LOW(1, "低风险"),
    MEDIUM(2, "中风险"),
    HIGH(3, "高风险");

    private final int code;
    private final String desc;

    RiskLevel(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static RiskLevel fromCode(int code) {
        for (RiskLevel level : values()) {
            if (level.code == code) {
                return level;
            }
        }
        throw new IllegalArgumentException("Unknown risk level code: " + code);
    }

    public static RiskLevel fromName(String name) {
        if (name == null) {
            return null;
        }
        for (RiskLevel level : values()) {
            if (level.name().equals(name)) {
                return level;
            }
        }
        return MEDIUM;
    }
}
