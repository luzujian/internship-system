package com.internship.agent.business;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class LegacyAiRequestLimiter {
    private final Semaphore slots = new Semaphore(8);
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    public AutoCloseable acquire(String owner) {
        if (!active.add(owner)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "请等待当前 AI 请求完成");
        if (!slots.tryAcquire()) {
            active.remove(owner);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "AI 服务繁忙");
        }
        AtomicBoolean closed = new AtomicBoolean();
        return () -> { if (closed.compareAndSet(false, true)) { active.remove(owner); slots.release(); } };
    }
    public static void release(AutoCloseable lease) {
        try { lease.close(); } catch (Exception ignored) { }
    }
}
