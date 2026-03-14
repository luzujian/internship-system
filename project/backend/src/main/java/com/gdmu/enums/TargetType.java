package com.gdmu.enums;

public enum TargetType {
    COMPANY("COMPANY", "公司"),
    STUDENT("STUDENT", "学生");

    private final String code;
    private final String desc;

    TargetType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static TargetType fromCode(String code) {
        for (TargetType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown target type code: " + code);
    }
}
