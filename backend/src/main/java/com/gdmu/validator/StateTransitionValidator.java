package com.gdmu.validator;

import com.gdmu.enums.InternshipStatusEnum;
import com.gdmu.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 状态流转验证器
 * 定义并验证实习状态流转的合法性，防止非法状态变更
 */
@Slf4j
@Component
public class StateTransitionValidator {

    /**
     * 定义合法的状态流转关系
     * key: 当前状态，value: 允许流转到的目标状态列表
     * 状态码对应关系（参考 InternshipStatusEnum）：
     * 0-未找到, 1-待确认, 2-已确定, 3-实习中, 4-已结束, 5-已中断, 6-延期
     */
    private static final Map<Integer, List<Integer>> ALLOWED_TRANSITIONS = Map.ofEntries(
        // 0-未找到 → 1-待确认
        Map.entry(0, List.of(1)),
        // 1-待确认 → 2-已确定(接受) 或 0-未找到(拒绝offer)
        Map.entry(1, List.of(2, 0)),
        // 2-已确定 → 3-实习中(开始实习) 或 5-已中断 或 6-延期
        Map.entry(2, List.of(3, 5, 6)),
        // 3-实习中 → 4-已结束(完成) 或 5-已中断
        Map.entry(3, List.of(4, 5)),
        // 4-已结束 → (终态，不可流转)
        Map.entry(4, List.of()),
        // 5-已中断 → 2-已确定(恢复实习) 或 4-已结束(终止) 或 1-待确认(重新申请offer)
        Map.entry(5, List.of(2, 4, 1)),
        // 6-延期 → 2-已确定(确认延期后开始) 或 3-实习中(边延期边实习) 或 4-已结束
        Map.entry(6, List.of(2, 3, 4))
    );

    /**
     * 验证状态流转是否合法
     * @param currentCode 当前状态码
     * @param targetCode 目标状态码
     * @throws BusinessException 如果状态流转不合法
     */
    public void validate(Integer currentCode, Integer targetCode) {
        if (currentCode == null || targetCode == null) {
            throw new BusinessException("状态码不能为空");
        }

        // 验证状态码是否有效
        InternshipStatusEnum.of(currentCode);
        InternshipStatusEnum.of(targetCode);

        List<Integer> allowedTargets = ALLOWED_TRANSITIONS.get(currentCode);
        if (allowedTargets == null || !allowedTargets.contains(targetCode)) {
            String currentDesc = InternshipStatusEnum.of(currentCode).getDescription();
            String targetDesc = InternshipStatusEnum.of(targetCode).getDescription();
            String errorMsg = String.format("状态流转不合法：从 [%s] 不能流转到 [%s]", currentDesc, targetDesc);
            log.warn(errorMsg);
            throw new BusinessException(errorMsg);
        }

        log.debug("状态流转验证通过：{} -> {}", currentDesc(currentCode), targetDesc(targetCode));
    }

    /**
     * 批量验证状态流转
     */
    public void validateBatch(List<Map.Entry<Integer, Integer>> transitions) {
        for (Map.Entry<Integer, Integer> transition : transitions) {
            validate(transition.getKey(), transition.getValue());
        }
    }

    private String targetDesc(Integer code) {
        return InternshipStatusEnum.of(code).getDescription();
    }

    private String currentDesc(Integer code) {
        return InternshipStatusEnum.of(code).getDescription();
    }
}
