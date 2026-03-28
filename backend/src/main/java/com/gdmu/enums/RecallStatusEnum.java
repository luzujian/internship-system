package com.gdmu.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 撤回状态统一枚举
 * 用于规范实习撤回业务的状态管理
 */
@Getter
public enum RecallStatusEnum {

    NOT_RECALL(0, "未撤回", "正常实习状态"),
    PENDING(1, "待审核", "撤回申请已提交，待审核"),
    APPROVED(2, "已批准", "撤回申请已批准"),
    REJECTED(3, "已拒绝", "撤回申请被拒绝");

    private final Integer code;
    private final String description;
    private final String detail;

    RecallStatusEnum(Integer code, String description, String detail) {
        this.code = code;
        this.description = description;
        this.detail = detail;
    }

    /**
     * 根据状态码获取枚举
     */
    public static RecallStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code.equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("无效的撤回状态码：" + code));
    }

    /**
     * 获取所有状态码列表
     */
    public static List<Integer> codes() {
        return Arrays.stream(values())
                .map(RecallStatusEnum::getCode)
                .collect(Collectors.toList());
    }
}
