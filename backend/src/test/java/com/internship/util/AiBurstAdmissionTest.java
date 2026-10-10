package com.internship.util;

import com.internship.agent.business.AgentException;
import com.internship.config.AiRuntimeProperties;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AiBurstAdmissionTest {
    @Test void sixtyFourSimultaneousUsersStayWithinEightProviderCallsAndRecoverAllSlots() throws Exception {
        AiCallGuard guard = new AiCallGuard(new AiRuntimeProperties());
        CountDownLatch start = new CountDownLatch(1), attempted = new CountDownLatch(64), release = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger(), peak = new AtomicInteger(), accepted = new AtomicInteger(), rejected = new AtomicInteger();
        try (ExecutorService users = Executors.newFixedThreadPool(64)) {
            List<Future<?>> requests = new ArrayList<>();
            for (int user = 0; user < 64; user++) requests.add(users.submit(() -> {
                try {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    guard.call(() -> {
                        accepted.incrementAndGet();
                        peak.accumulateAndGet(active.incrementAndGet(), Math::max);
                        attempted.countDown();
                        try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
                        finally { active.decrementAndGet(); }
                        return true;
                    });
                } catch (AgentException limited) {
                    assertEquals(429, limited.getHttpStatus());
                    rejected.incrementAndGet(); attempted.countDown();
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            start.countDown();
            try {
                assertTrue(attempted.await(4, TimeUnit.SECONDS));
                assertEquals(8, peak.get()); assertEquals(8, accepted.get()); assertEquals(56, rejected.get());
            } finally { release.countDown(); }
            for (Future<?> request : requests) request.get(5, TimeUnit.SECONDS);
        }
        assertEquals(0, active.get());
        List<AiCallGuard.Lease> recovered = new ArrayList<>();
        try {
            for (int slot = 0; slot < 8; slot++) recovered.add(guard.acquire());
            assertThrows(AgentException.class, guard::acquire);
        } finally { recovered.forEach(AiCallGuard.Lease::close); }
    }
}
