package com.gdmu.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 服务熔断器
 * 用于处理 AI 服务不可用时的降级，保证系统整体可用性
 *
 * 工作原理：
 * 1. 连续失败达到阈值后，自动熔断（关闭 AI 服务调用）
 * 2. 熔断期间直接返回降级结果，不再调用 AI 服务
 * 3. 熔断时间过后，自动尝试恢复（半开状态）
 * 4. 恢复成功后重置失败计数，恢复正常调用
 */
@Slf4j
@Component
public class AiServiceCircuitBreaker {

    /** 是否启用 AI 服务（手动开关） */
    private static final AtomicBoolean AI_ENABLED = new AtomicBoolean(true);

    /** 失败计数器 */
    private final AtomicInteger failureCount = new AtomicInteger(0);

    /** 熔断时间戳（null 表示未熔断） */
    private volatile Long circuitBreakTime = null;

    /** 连续失败阈值 */
    private static final int FAILURE_THRESHOLD = 5;

    /** 熔断时长（毫秒）- 5 分钟 */
    private static final long CIRCUIT_BREAK_DURATION = 5 * 60 * 1000;

    /** 记录每个辅导员的熔断状态 */
    private final Map<Long, CircuitBreakState> counselorStates = new ConcurrentHashMap<>();

    /**
     * 熔断状态类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class CircuitBreakState {
        private AtomicInteger failureCount;
        private volatile Long breakTime;
    }

    /**
     * 检查是否允许调用 AI 服务
     * @param counselorId 辅导员ID，null表示全局检查
     * @return true-允许调用，false-熔断中
     */
    public boolean allowRequest(Long counselorId) {
        // 检查手动开关
        if (!AI_ENABLED.get()) {
            log.warn("AI 服务已被手动关闭");
            return false;
        }

        // 检查全局熔断状态
        if (circuitBreakTime != null) {
            long elapsed = System.currentTimeMillis() - circuitBreakTime;
            if (elapsed > CIRCUIT_BREAK_DURATION) {
                // 熔断时间已过，尝试恢复
                log.info("AI 全局服务熔断时间已过，尝试恢复");
                resetCircuitBreaker(null);
                return true;
            }
            log.warn("AI 全局服务处于熔断状态，剩余 {} 秒",
                (CIRCUIT_BREAK_DURATION - elapsed) / 1000);
            return false;
        }

        // 检查辅导员独立熔断状态
        if (counselorId != null) {
            CircuitBreakState state = counselorStates.get(counselorId);
            if (state != null && state.getBreakTime() != null) {
                long elapsed = System.currentTimeMillis() - state.getBreakTime();
                if (elapsed > CIRCUIT_BREAK_DURATION) {
                    // 熔断时间已过，移除状态
                    counselorStates.remove(counselorId);
                    log.info("辅导员 {} 的 AI 服务熔断时间已过，已恢复", counselorId);
                    return true;
                }
                log.warn("辅导员 {} 的 AI 服务处于熔断状态，剩余 {} 秒",
                    counselorId, (CIRCUIT_BREAK_DURATION - elapsed) / 1000);
                return false;
            }
        }

        return true;
    }

    /**
     * 记录调用成功
     * @param counselorId 辅导员ID，null表示全局调用
     */
    public void recordSuccess(Long counselorId) {
        if (counselorId == null) {
            failureCount.set(0);
        } else {
            CircuitBreakState state = counselorStates.get(counselorId);
            if (state != null) {
                state.getFailureCount().set(0);
            }
        }
    }

    /**
     * 记录调用失败
     * @param counselorId 辅导员ID，null表示全局调用
     */
    public void recordFailure(Long counselorId) {
        CircuitBreakState state = getOrCreateState(counselorId);
        int count = state.getFailureCount().incrementAndGet();
        log.error("AI 服务调用失败，辅导员ID: {}，连续失败次数：{}", counselorId, count);

        if (count >= FAILURE_THRESHOLD) {
            state.setBreakTime(System.currentTimeMillis());
            log.error("AI 服务连续失败 {} 次，已触发熔断，辅导员ID: {}，熔断时长 {} 分钟",
                count, counselorId, CIRCUIT_BREAK_DURATION / 60000);
        }
    }

    /**
     * 重置熔断器
     * @param counselorId 辅导员ID，null表示重置全局熔断器
     */
    public void resetCircuitBreaker(Long counselorId) {
        if (counselorId == null) {
            failureCount.set(0);
            circuitBreakTime = null;
            log.info("AI 全局服务熔断器已重置");
        } else {
            counselorStates.remove(counselorId);
            log.info("辅导员 {} 的 AI 服务熔断器已重置", counselorId);
        }
    }

    /**
     * 获取或创建辅导员熔断状态
     */
    private CircuitBreakState getOrCreateState(Long counselorId) {
        return counselorStates.computeIfAbsent(counselorId,
            k -> new CircuitBreakState(new AtomicInteger(0), null));
    }

    /**
     * 手动关闭 AI 服务
     */
    public void disableAiService() {
        AI_ENABLED.set(false);
        log.warn("AI 服务已被手动关闭");
    }

    /**
     * 手动开启 AI 服务
     */
    public void enableAiService() {
        AI_ENABLED.set(true);
        resetCircuitBreaker(null);
        log.info("AI 服务已被手动开启");
    }

    /**
     * 获取熔断器状态信息
     */
    public CircuitBreakerStatus getStatus() {
        boolean isBreakOpen = circuitBreakTime != null;
        long remainingTime = 0;

        if (isBreakOpen) {
            long elapsed = System.currentTimeMillis() - circuitBreakTime;
            remainingTime = Math.max(0, CIRCUIT_BREAK_DURATION - elapsed);
        }

        return new CircuitBreakerStatus(
            AI_ENABLED.get(),
            isBreakOpen,
            failureCount.get(),
            remainingTime / 1000,
            FAILURE_THRESHOLD
        );
    }

    /**
     * 熔断器状态类
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class CircuitBreakerStatus {
        private final boolean enabled;
        private final boolean circuitBreakOpen;
        private final int failureCount;
        private final long remainingBreakTimeSeconds;
        private final int failureThreshold;
    }
}
