package com.internship.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.agent.business.AgentException;
import com.internship.mapper.TeacherAutoTaskRunMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;
import java.util.UUID;

/** A committed occurrence claim survives process restarts. Notifications never run inside its transaction. */
@Service
public class TeacherAutoTaskRunService {
    public static final String UNCERTAIN_REASON = "有一次执行结果待核实，自动跟进已暂停；请打开任务“记录”核对运行与发送明细，核实前不能重新启用。";
    public static final String PENDING_REASON = "上一次执行仍在进行，请通过任务“记录”核验；暂时不能再次执行或启用。";
    private record ClaimResult(RunView run, boolean blocked) {}
    public record RunView(String runId, Long taskId, String occurrenceKey, long taskVersion,
                          String status, long startedAt, long updatedAt, Map<String, Object> receipt,
                          String errorMessage, boolean claimed) {}
    private final TeacherAutoTaskRunMapper mapper;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;

    public TeacherAutoTaskRunService(TeacherAutoTaskRunMapper mapper, ObjectMapper json, PlatformTransactionManager tx) {
        this.mapper = mapper; this.json = json;
        transaction = new TransactionTemplate(tx);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public RunView claim(Long taskId, String key, long version) {
        ClaimResult result = transaction.execute(ignored -> {
            if (mapper.lockTask(taskId) == null) throw new AgentException("TASK_NOT_FOUND", "任务不存在。", 404);
            var existing = mapper.find(taskId, key);
            if (existing != null) {
                RunView previous = view(existing, false);
                if ("UNKNOWN".equals(previous.status())) mapper.pauseUncertain(taskId, UNCERTAIN_REASON);
                return new ClaimResult(previous, false);
            }
            var pending = mapper.unsettled(taskId);
            if (pending != null) {
                RunView previous = view(pending, false);
                if ("UNKNOWN".equals(previous.status())) mapper.pauseUncertain(taskId, UNCERTAIN_REASON);
                return new ClaimResult(previous, true);
            }
            long now = System.currentTimeMillis();
            var row = new TeacherAutoTaskRunMapper.Row(UUID.randomUUID().toString(), taskId, key, version,
                    "RUNNING", now, now, null, null);
            mapper.insert(row);
            return new ClaimResult(view(row, true), false);
        });
        // Throw only after the pause commits; a failed new occurrence must not roll it back.
        if (result.blocked()) throw new AgentException("RUN_PENDING", reason(result.run()), 409);
        return result.run();
    }

    public RunView pending(Long taskId) {
        var row = mapper.unsettled(taskId);
        return row == null ? null : view(row, false);
    }

    /** Read-only: safe to call from an action transaction that already locks the task. */
    public void requireResolved(Long taskId) {
        RunView pending = pending(taskId);
        if (pending != null) throw new AgentException("RUN_PENDING", reason(pending), 409);
    }

    /** Reconciles legacy/interrupted runs before displaying their status; never releases a claim. */
    public RunView reconcilePending(Long taskId) {
        return transaction.execute(ignored -> {
            RunView pending = pending(taskId);
            if (pending != null && "UNKNOWN".equals(pending.status())) mapper.pauseUncertain(taskId, UNCERTAIN_REASON);
            return pending;
        });
    }

    private static String reason(RunView run) {
        return "UNKNOWN".equals(run.status()) ? UNCERTAIN_REASON : PENDING_REASON;
    }

    public RunView find(Long taskId, String key) {
        var row = mapper.find(taskId, key);
        return row == null ? new RunView(null, taskId, key, 0, "NOT_FOUND", 0, 0, null, null, false) : view(row, false);
    }

    public void heartbeat(String runId) {
        if (mapper.heartbeat(runId, System.currentTimeMillis()) != 1) {
            throw new AgentException("RUN_PENDING", "运行状态已变化，停止后续发送，请核对任务记录。", 409);
        }
    }

    public void complete(String id, String status, Map<String, Object> receipt, String error) {
        if (!java.util.Set.of("SUCCEEDED", "PARTIAL", "FAILED", "UNKNOWN", "CANCELLED").contains(status)) {
            throw new IllegalArgumentException("Unsupported run result");
        }
        transaction.executeWithoutResult(ignored -> {
            try {
                int changed = mapper.finish(id, status, receipt == null ? null : json.writeValueAsString(receipt), error, System.currentTimeMillis());
                if (changed > 0 && "UNKNOWN".equals(status)) {
                    var run = mapper.findById(id);
                    if (run == null) throw new IllegalStateException("Run disappeared while recording its result");
                    mapper.pauseUncertain(run.taskId(), UNCERTAIN_REASON);
                }
            }
            catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException(e); }
        });
    }

    private RunView view(TeacherAutoTaskRunMapper.Row row, boolean claimed) {
        Map<String, Object> receipt = null;
        try { if (row.receipt() != null) receipt = json.readValue(row.receipt(), new TypeReference<>() {}); }
        catch (Exception e) { throw new AgentException("RUN_RECEIPT_UNAVAILABLE", "运行回执暂时无法读取，请稍后核验，不要重新发送。", 503); }
        // A process may disappear after a send. This view does not release its claim or authorize retries.
        String status = row.status();
        if ("RUNNING".equals(status) && System.currentTimeMillis() - row.updatedAt() > 15 * 60_000L) status = "UNKNOWN";
        return new RunView(row.id(), row.taskId(), row.occurrenceKey(), row.taskVersion(), status,
                row.startedAt(), row.updatedAt(), receipt, row.errorMessage(), claimed);
    }

    public static String manualKey(String requestId) {
        if (requestId == null || !requestId.matches("[a-zA-Z0-9_-]{16,100}")) {
            throw new AgentException("REQUEST_ID_REQUIRED", "请携带本次操作的稳定请求编号，避免重复执行。", 400);
        }
        return "manual:" + requestId;
    }
}
