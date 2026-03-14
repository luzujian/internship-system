package com.gdmu.enums;

public enum AuditDecision {
    APPROVED("APPROVED", "批准"),
    REJECTED("REJECTED", "拒绝"),
    MANUAL("MANUAL", "转人工审核");

    private final String code;
    private final String desc;

    AuditDecision(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static AuditDecision fromCode(String code) {
        for (AuditDecision decision : values()) {
            if (decision.code.equals(code)) {
                return decision;
            }
        }
        throw new IllegalArgumentException("Unknown audit decision code: " + code);
    }
}
