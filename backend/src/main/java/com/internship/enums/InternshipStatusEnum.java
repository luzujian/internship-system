package com.internship.enums;

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

    NOT_FOUND(0, "尚未获录用", "包含暂无有效投递和已投递未录用，不含已延期实习"),
    HAS_OFFER(1, "已录用待确认", "已获录用，尚未完成实习确认手续"),
    CONFIRMED(2, "已定单位待实习", "实习单位已落实，等待开始实习"),
    IN_PROGRESS(3, "实习进行中", "系统登记当前实习正在进行"),
    ENDED(4, "实习已结束", "当前实习已结束，鉴定材料可能仍需办理"),
    INTERRUPTED(5, "实习已中断", "当前实习已中断，需核对后续安排"),
    DELAYED(6, "已延期实习", "已登记延期实习，包含考研延期");

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
