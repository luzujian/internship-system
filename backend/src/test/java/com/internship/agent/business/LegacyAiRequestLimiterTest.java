package com.internship.agent.business;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
class LegacyAiRequestLimiterTest {
    @Test void boundsOwnersAndGlobalConcurrencyAndReleasesOnce() throws Exception {
        var limiter = new LegacyAiRequestLimiter();
        var leases = new ArrayList<AutoCloseable>();
        for (int i=0;i<8;i++) leases.add(limiter.acquire("user"+i));
        assertThrows(ResponseStatusException.class, () -> limiter.acquire("user0"));
        assertThrows(ResponseStatusException.class, () -> limiter.acquire("user8"));
        leases.getFirst().close();
        leases.getFirst().close();
        leases.add(limiter.acquire("user8"));
        assertThrows(ResponseStatusException.class, () -> limiter.acquire("user9"));
        for (var lease : leases) lease.close();
        try (var lease = limiter.acquire("user0")) { assertNotNull(lease); }
    }
}
