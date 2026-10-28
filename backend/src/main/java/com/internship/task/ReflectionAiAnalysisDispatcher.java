package com.internship.task;

import com.internship.agent.business.AgentFeatureGate;
import com.internship.agent.business.AgentException;
import com.internship.entity.InternshipReflection;
import com.internship.entity.TeacherUser;
import com.internship.mapper.InternshipReflectionMapper;
import com.internship.mapper.TeacherUserMapper;
import com.internship.service.ClassCounselorRelationService;
import com.internship.service.CounselorAISettingsService;
import com.internship.service.StudentReflectionAIAnalysisService;
import com.internship.websocket.AnnouncementWebSocketHandler;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Existing submitted rows are the durable backlog; the bounded executor is only a wake-up mechanism. */
@Slf4j
@Component
public class ReflectionAiAnalysisDispatcher {
    @Autowired
    private com.internship.service.ReflectionFeatureService reflectionFeature;
    public static final int MAX_ATTEMPTS = 3;
    public static final String FAILURE_MESSAGE = "心得已提交，AI分析暂未完成，可稍后重试或教师人工批阅，无需重复提交";
    private final InternshipReflectionMapper reflections;
    private final StudentReflectionAIAnalysisService analysis;
    private final ClassCounselorRelationService assignments;
    private final TeacherUserMapper teachers;
    private final CounselorAISettingsService settings;
    private final AgentFeatureGate gate;
    private final AnnouncementWebSocketHandler notifications;
    private final ThreadPoolExecutor executor;
    private final Set<Long> queued = ConcurrentHashMap.newKeySet();
    private final int retryDelaySeconds;

    public ReflectionAiAnalysisDispatcher(InternshipReflectionMapper reflections,
            StudentReflectionAIAnalysisService analysis, ClassCounselorRelationService assignments,
            CounselorAISettingsService settings, TeacherUserMapper teachers,
            AgentFeatureGate gate, AnnouncementWebSocketHandler notifications,
            @Value("${ai.reflection.workers:2}") int workers,
            @Value("${ai.reflection.queue-capacity:64}") int capacity,
            @Value("${ai.reflection.retry-delay-seconds:60}") int retryDelaySeconds) {
        this.reflections = reflections;
        this.analysis = analysis;
        this.assignments = assignments;
        this.settings = settings;
        this.teachers = teachers;
        this.gate = gate;
        this.notifications = notifications;
        this.retryDelaySeconds = Math.max(60, retryDelaySeconds);
        int threads = Math.max(1, Math.min(workers, 16));
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(Math.max(1, Math.min(capacity, 1024))), task -> {
                    Thread thread = new Thread(task, "reflection-ai-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public void dispatchAfterCommit(Long reflectionId) {
        if (!reflectionFeature.enabled()) return;
        if (reflectionId == null) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCommit() { enqueue(reflectionId); }
                });
            }
            // Without synchronization support, durable recovery is safer than starting before commit.
            return;
        }
        enqueue(reflectionId);
    }

    boolean enqueue(Long reflectionId) {
        if (!reflectionFeature.enabled()) return false;
        if (reflectionId == null || executor.isShutdown() || !queued.add(reflectionId)) return false;
        try {
            executor.execute(() -> {
                try { analyzePending(reflectionId); }
                finally { queued.remove(reflectionId); }
            });
            return true;
        } catch (RejectedExecutionException rejected) {
            queued.remove(reflectionId);
            // No failure is recorded: the submitted row will be found by the next recovery scan.
            return false;
        }
    }

    @Scheduled(initialDelayString = "${ai.reflection.recovery-initial-delay-ms:10000}",
            fixedDelayString = "${ai.reflection.recovery-delay-ms:30000}")
    public void recoverPending() {
        if (!reflectionFeature.enabled()) return;
        if (executor.isShutdown() || !gate.enabled()) return;
        try {
            int capacity = executor.getQueue().remainingCapacity();
            if (capacity == 0) return;
            List<Long> pending = reflections.findPendingAiAnalysis(Math.min(capacity, 128), MAX_ATTEMPTS, retryDelaySeconds);
            for (Long id : pending) enqueue(id);
        } catch (Exception failure) {
            log.warn("实习心得AI待处理任务暂时无法恢复：{}", failure.getClass().getSimpleName());
        }
    }

    private void analyzePending(Long reflectionId) {
        if (!reflectionFeature.enabled()) return;
        InternshipReflection snapshot = null;
        Long version = null;
        boolean analysisStarted = false;
        try {
            if (!gate.enabled()) return;
            snapshot = reflections.findById(reflectionId);
            if (!pending(snapshot) || reflections.hasManualEvaluation(reflectionId)
                    || analysis.findByReflectionId(reflectionId) != null) return;
            version = reflections.latestSubmissionEventId(reflectionId, snapshot.getStudentId());
            if (version == null) version = 0L;
            if (reflections.countAiFailures(reflectionId, snapshot.getStudentId(), version) >= MAX_ATTEMPTS) return;
            // Resolve current assignment at execution time, never trust the counselor captured by submission.
            Long counselor = assignments.findCounselorIdByStudentId(snapshot.getStudentId());
            if (counselor == null) return;
            TeacherUser teacher = teachers.findById(counselor);
            if (teacher == null || !Integer.valueOf(1).equals(teacher.getStatus())
                    || !"COUNSELOR".equals(teacher.getTeacherType()) || !settings.isAiScoringEnabled(counselor)) return;
            analysisStarted = true;
            Map<String, Object> result = analysis.analyzeReflectionContent(snapshot.getContent(), counselor,
                    snapshot.getStudentId(), reflectionId);
            if (Thread.currentThread().isInterrupted()) return;
            if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                try { notifications.sendTeacherTodoUpdate(counselor, 1L); }
                catch (Exception ignored) { log.debug("实习心得AI通知暂时不可用，reflectionId={}", reflectionId); }
            } else if (result == null || (!Boolean.TRUE.equals(result.get("stale"))
                    && !Boolean.TRUE.equals(result.get("isNotReflection"))
                    && !Boolean.TRUE.equals(result.get("deferred"))
                    && Boolean.TRUE.equals(result.get("retryable")))) {
                recordFailure(snapshot, version);
            }
        } catch (AgentException deferred) {
            // Admission control is not a failed model attempt; recovery will retry the durable row.
            log.debug("实习心得AI等待可用容量：reflectionId={}", reflectionId);
        } catch (Exception failure) {
            log.warn("实习心得AI后台任务失败：reflectionId={}, type={}", reflectionId, failure.getClass().getSimpleName());
            if (analysisStarted && snapshot != null && version != null && !Thread.currentThread().isInterrupted()) recordFailure(snapshot, version);
        }
    }

    private void recordFailure(InternshipReflection snapshot, Long version) {
        try {
            // The conditional insert refuses stale/withdrawn/reviewed/resubmitted content.
            if (reflections.recordAiFailure(snapshot, version, new Date()) > 0) {
                notifications.sendAIAnalysisResultToUser(snapshot.getStudentId(), snapshot.getId(), false, false, FAILURE_MESSAGE);
            }
        } catch (Exception failure) {
            log.warn("实习心得AI失败状态暂时无法保存：reflectionId={}, type={}", snapshot.getId(), failure.getClass().getSimpleName());
        }
    }

    private static boolean pending(InternshipReflection reflection) {
        return reflection != null && Integer.valueOf(0).equals(reflection.getDeleted())
                && Integer.valueOf(0).equals(reflection.getStatus()) && reflection.getRemark() == null
                && reflection.getSubmitTime() != null && reflection.getContent() != null && !reflection.getContent().isBlank();
    }

    public int failureCount(Long reflectionId, Long studentId) {
        Long version = reflections.latestSubmissionEventId(reflectionId, studentId);
        return reflections.countAiFailures(reflectionId, studentId, version == null ? 0L : version);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        queued.clear();
    }
}
