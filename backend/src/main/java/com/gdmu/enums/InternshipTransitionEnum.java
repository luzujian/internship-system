package com.gdmu.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实习申请状态统一枚举
 * 用于规范实习申请业务的状态管理
 */
@Getter
public enum InternshipTransitionEnum {

    PENDING(0, "待处理", "实习申请已提交，待企业处理"),
    ACCEPTED(1, "已接受", "企业已接受申请，待学生确认"),
    REJECTED(2, "已拒绝", "申请被拒绝"),
    CONFIRMED(3, "已确认", "学生已确认，实习生效"),
    WITHDRAWN(4, "已撤回", "申请已撤回");

    private final Integer code;
    private final String description;
    private final String detail;

    InternshipTransitionEnum(Integer code, String description, String detail) {
        this.code = code;
        this.description = description;
        this.detail = detail;
    }

    /**
     * 根据状态码获取枚举
     */
    public static InternshipTransitionEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的申请状态码：" + code));
    }

    /**
     * 获取所有状态码列表
     */
    public static List<Integer> codes() {
        return Arrays.stream(values())
                .map(InternshipTransitionEnum::getCode)
                .collect(Collectors.toList());
    }
}
