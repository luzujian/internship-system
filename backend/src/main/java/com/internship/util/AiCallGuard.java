package com.internship.util;

import com.internship.agent.business.AgentException;
import com.internship.config.AiRuntimeProperties;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Fail-fast, bounded admission shared by foreground, legacy and background model calls. */
@Component
public class AiCallGuard {
    private final AiRuntimeProperties limits;
    private final Semaphore slots;
    private final LongSupplier now;
    private long minute = -1;
    private int requests;
    private final Map<String, UserWindow> users = new HashMap<>();

    @Autowired
    public AiCallGuard(AiRuntimeProperties limits) {
        this(limits, System::currentTimeMillis);
    }

    AiCallGuard(AiRuntimeProperties limits, LongSupplier now) {
        this.limits = limits;
        this.slots = new Semaphore(limits.getMaxConcurrentCalls());
        this.now = now;
    }

    public Lease acquire() {
        if (!slots.tryAcquire()) throw busy();
        try {
            synchronized (this) {
                long current = now.getAsLong() / 60000;
                if (current != minute) { minute = current; requests = 0; }
                if (requests >= limits.getMaxRequestsPerMinute()) throw rateLimited();
                requests++;
            }
            return new Lease(slots::release);
        } catch (RuntimeException error) {
            slots.release();
            throw error;
        }
    }

    /** User admission is separate from provider admission; callers must not acquire the global slot twice. */
    public synchronized Lease acquireUser(String owner) {
        long current = now.getAsLong() / 60000;
        UserWindow window = users.get(owner);
        if (window == null) {
            users.entrySet().removeIf(entry -> !entry.getValue().active && entry.getValue().minute != current);
            if (users.size() >= 10000) throw busy();
            window = new UserWindow();
            users.put(owner, window);
        }
        if (window.active) throw new AgentException("AI_BUSY", "请等待当前 AI 请求完成后再试。", 429);
        if (window.minute != current) { window.minute = current; window.requests = 0; }
        if (window.requests >= limits.getMaxUserRequestsPerMinute()) throw rateLimited();
        window.requests++;
        window.active = true;
        UserWindow claimed = window;
        return new Lease(() -> { synchronized (AiCallGuard.this) { claimed.active = false; } });
    }

    public <T> T call(Supplier<T> action) {
        try (Lease ignored = acquire()) { return action.get(); }
    }

    /** using releases on completion, error, synchronous construction failure, and subscriber cancellation. */
    public <T> Flux<T> stream(Supplier<Flux<T>> action) {
        return Flux.using(this::acquire,
                ignored -> Flux.defer(action)
                        .timeout(Duration.ofMillis(limits.getReadTimeoutMillis()))
                        .takeUntilOther(Mono.delay(Duration.ofMillis(limits.getStreamTimeoutMillis()))
                                .flatMap(tick -> Mono.error(new AgentException("MODEL_TIMEOUT", "模型响应超时，请稍后重试。", 503)))),
                Lease::close);
    }

    public ChatModel wrap(ChatModel delegate) {
        return new ChatModel() {
            @Override public ChatResponse call(Prompt prompt) { return AiCallGuard.this.call(() -> delegate.call(prompt)); }
            @Override public Flux<ChatResponse> stream(Prompt prompt) { return AiCallGuard.this.stream(() -> delegate.stream(prompt)); }
            @Override public ChatOptions getDefaultOptions() { return delegate.getDefaultOptions(); }
        };
    }

    private static AgentException busy() { return new AgentException("AI_BUSY", "AI 服务繁忙，请稍后重试。", 429); }
    private static AgentException rateLimited() { return new AgentException("RATE_LIMITED", "AI 请求较多，请稍后再试。", 429); }
    private static final class UserWindow { long minute = -1; int requests; boolean active; }

    public static final class Lease implements AutoCloseable {
        private final AtomicBoolean closed = new AtomicBoolean();
        private final Runnable release;
        private Lease(Runnable release) { this.release = release; }
        @Override public void close() { if (closed.compareAndSet(false, true)) release.run(); }
    }
}
