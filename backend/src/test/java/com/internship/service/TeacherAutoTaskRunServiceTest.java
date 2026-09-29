package com.internship.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.mapper.TeacherAutoTaskRunMapper;
import org.junit.jupiter.api.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** No database or delivery connection; verifies durable ledger decisions against persisted rows. */
class TeacherAutoTaskRunServiceTest {
    TeacherAutoTaskRunMapper mapper;
    TeacherAutoTaskRunService service;
    Map<String, TeacherAutoTaskRunMapper.Row> rows;
    @BeforeEach void setup() {
        mapper = mock(TeacherAutoTaskRunMapper.class);
        rows = new HashMap<>();
        var tx = mock(PlatformTransactionManager.class);
        when(tx.getTransaction(any())).thenAnswer(inv -> new SimpleTransactionStatus());
        when(mapper.lockTask(1L)).thenReturn(1L);
        when(mapper.find(eq(1L), anyString())).thenAnswer(inv -> rows.get(inv.getArgument(1)));
        when(mapper.unsettled(1L)).thenAnswer(inv -> rows.values().stream().filter(r -> Set.of("RUNNING", "UNKNOWN").contains(r.status())).findFirst().orElse(null));
        when(mapper.findById(anyString())).thenAnswer(inv -> rows.values().stream().filter(r -> r.id().equals(inv.getArgument(0))).findFirst().orElse(null));
        when(mapper.heartbeat(anyString(),anyLong())).thenAnswer(inv -> {
            var old=rows.values().stream().filter(r->r.id().equals(inv.getArgument(0))).findFirst().orElse(null);
            if(old==null||!"RUNNING".equals(old.status()))return 0;
            rows.put(old.occurrenceKey(),new TeacherAutoTaskRunMapper.Row(old.id(),old.taskId(),old.occurrenceKey(),old.taskVersion(),old.status(),old.startedAt(),inv.getArgument(1),old.receipt(),old.errorMessage()));
            return 1;
        });
        when(mapper.insert(any())).thenAnswer(inv -> { var row = inv.<TeacherAutoTaskRunMapper.Row>getArgument(0); rows.put(row.occurrenceKey(), row); return 1; });
        when(mapper.finish(anyString(), anyString(), nullable(String.class), nullable(String.class), anyLong())).thenAnswer(inv -> {
            var old = rows.values().stream().filter(r -> r.id().equals(inv.getArgument(0))).findFirst().orElseThrow();
            if (!"RUNNING".equals(old.status())) return 0;
            rows.put(old.occurrenceKey(), new TeacherAutoTaskRunMapper.Row(old.id(), old.taskId(), old.occurrenceKey(), old.taskVersion(), inv.getArgument(1), old.startedAt(), inv.getArgument(4), inv.getArgument(2), inv.getArgument(3)));
            return 1;
        });
        service = new TeacherAutoTaskRunService(mapper, new ObjectMapper(), tx);
    }
    @Test void sameOccurrenceAfterRestartReturnsReceiptWithoutClaimingAgain() {
        var run = service.claim(1L, "scheduled:2026-09-20T10:00:00Z", 3);
        assertTrue(run.claimed());
        service.complete(run.runId(), "SUCCEEDED", Map.of("successCount", 2), null);
        var replay = service.claim(1L, run.occurrenceKey(), 4);
        assertFalse(replay.claimed()); assertEquals(run.runId(), replay.runId());
        assertEquals(2, replay.receipt().get("successCount"));
        verify(mapper, times(1)).insert(any());
    }
    @Test void unknownAndRunningCannotBeBypassedWithNewRequestIds() {
        var run = service.claim(1L, "manual:request-first-0001", 3);
        assertEquals("RUN_PENDING", assertThrows(AgentException.class, () -> service.claim(1L, "manual:request-second-0002", 3)).getCode());
        service.complete(run.runId(), "UNKNOWN", null, "待核验");
        assertEquals("RUN_PENDING", assertThrows(AgentException.class, () -> service.claim(1L, "manual:request-third-0003", 3)).getCode());
        assertFalse(service.claim(1L, run.occurrenceKey(), 3).claimed());
    }
    @Test void staleRunningIsShownAsUnknownWithoutUnlocking() {
        rows.put("old", new TeacherAutoTaskRunMapper.Row("r", 1L, "old", 1, "RUNNING", 1, 1, null, null));
        assertEquals("UNKNOWN", service.find(1L, "old").status());
        assertEquals("RUNNING", rows.get("old").status());
    }
    @Test void manualRunRequiresStableBoundedRequestIdentity() {
        assertThrows(AgentException.class, () -> TeacherAutoTaskRunService.manualKey(null));
        assertThrows(AgentException.class, () -> TeacherAutoTaskRunService.manualKey("short"));
        assertEquals("manual:request-stable-0001", TeacherAutoTaskRunService.manualKey("request-stable-0001"));
    }
    @Test void uncertainReceiptPausesTaskWithoutReleasingClaim() {
        var run=service.claim(1L,"manual:request-first-0001",3);
        service.complete(run.runId(),"UNKNOWN",Map.of("unknownCount",1),"等待核实");
        verify(mapper).pauseUncertain(1L,TeacherAutoTaskRunService.UNCERTAIN_REASON);
        assertEquals("UNKNOWN",service.pending(1L).status());
        assertEquals("RUN_PENDING",assertThrows(AgentException.class,()->service.requireResolved(1L)).getCode());
        assertEquals(1,rows.size());
    }
    @Test void normalRunningRequestBlocksDuplicateWithoutPausingOriginalRun() {
        var run=service.claim(1L,"manual:request-first-0001",3);
        assertThrows(AgentException.class,()->service.claim(1L,"manual:request-second-0002",3));
        assertFalse(service.claim(1L,run.occurrenceKey(),3).claimed());
        service.reconcilePending(1L);
        verify(mapper,never()).pauseUncertain(anyLong(),anyString());
        assertEquals("RUNNING",service.pending(1L).status());
    }
    @Test void interruptedRunPausesOnBlockedClaimButKeepsOriginalLedgerRow() {
        rows.put("old",new TeacherAutoTaskRunMapper.Row("r",1L,"old",1,"RUNNING",1,1,null,null));
        assertEquals("RUN_PENDING",assertThrows(AgentException.class,()->service.claim(1L,"new-occurrence",2)).getCode());
        verify(mapper).pauseUncertain(1L,TeacherAutoTaskRunService.UNCERTAIN_REASON);
        assertEquals("RUNNING",rows.get("old").status());
        assertEquals(1,rows.size());
        verify(mapper,never()).insert(any());
    }
    @Test void heartbeatKeepsLongRunningTaskCurrentWithoutReleasingClaim() {
        rows.put("old",new TeacherAutoTaskRunMapper.Row("r",1L,"old",1,"RUNNING",1,1,null,null));
        service.heartbeat("r");
        assertEquals("RUNNING",service.pending(1L).status());
        assertThrows(AgentException.class,()->service.claim(1L,"another-occurrence",2));
        verify(mapper,never()).pauseUncertain(anyLong(),anyString());
        assertEquals(1,rows.size());
    }
}
