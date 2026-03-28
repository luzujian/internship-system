package com.gdmu.enums;

public enum AuditType {
    RECALL_APPLY("RECALL_APPLY", "撤回申请");

    private final String code;
    private final String desc;

    AuditType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static AuditType fromCode(String code) {
        for (AuditType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown audit type code: " + code);
    }
}
