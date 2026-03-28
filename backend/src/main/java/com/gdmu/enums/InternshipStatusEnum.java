package com.gdmu.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实习状态统一枚举
 * 用于规范实习业务中的状态管理，避免魔法数字
 */
@Getter
public enum InternshipStatusEnum {

    NOT_FOUND(0, "待就业", "未找到实习岗位"),
    HAS_OFFER(1, "待确认", "已获得实习 Offer 但未确认"),
    CONFIRMED(2, "已确定", "实习已确认，待开始"),
    IN_PROGRESS(3, "实习中", "实习进行中"),
    ENDED(4, "已结束", "实习已结束"),
    INTERRUPTED(5, "已中断", "实习中断"),
    DELAYED(6, "延期", "实习延期（包含准备考研）");

    private final Integer code;
    private final String description;
    private final String detail;

    InternshipStatusEnum(Integer code, String description, String detail) {
        this.code = code;
        this.description = description;
        this.detail = detail;
    }

    /**
     * 根据状态码获取枚举
     */
    public static InternshipStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的实习状态码：" + code));
    }

    /**
     * 获取所有状态码列表
     */
    public static List<Integer> codes() {
        return Arrays.stream(values())
                .map(InternshipStatusEnum::getCode)
                .collect(Collectors.toList());
    }
}
