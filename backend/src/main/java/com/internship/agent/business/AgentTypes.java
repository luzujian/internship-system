package com.internship.agent.business;

import java.util.*;
import lombok.Data;

public final class AgentTypes {
    private AgentTypes() {}

    public record Actor(String ownerKey, Long id, String role, String name, String teacherType,
                        Long departmentId, Long divisionId, boolean readOnly, Set<String> permissions) {}
    public record BusinessObject(String type, String id) {}
    public record PageContext(String pageKey, String route, String title, String view,
                              BusinessObject object, List<BusinessObject> selectedObjects,
                              Map<String, Object> filters, long revision) {}
    public record CreateTask(String sessionId, String clientRequestId, String message, PageContext pageContext) {}
    public record UiFeedback(String clientRequestId, String actionId, PageContext pageContext) {}
    public record ResendTask(String clientRequestId, String message, String operation) {
        public ResendTask(String clientRequestId,String message){this(clientRequestId,message,null);}
    }
    public record DraftRequest(String draftKey,String text,long expectedRevision) {}
    public record TimelineEvent(String id,String kind,long createdAt,String message,Map<String,Object> payload) {}
    public record Turn(String question, String answer, PageContext context) {}
    public record Step(String tool, String title, String status, long durationMs, String errorCode) {}
    public record Link(String label, String action, String objectId, Map<String, String> parameters) {}
    public record ToolResult(String tool, String title, String status, String observedAt, String scope,
                             List<Map<String, Object>> items, long total, boolean hasMore,
                             Map<String, Object> facts, List<Link> links, List<String> warnings) {}
    public record TeacherSummaryQuery(String scopeType, Long scopeId, String scopeName,
                                      String view, String status) {}

    @Data
    public static class Session {
        private String id;
        private String ownerKey;
        private String scopeFingerprint;
        private List<Turn> turns = new ArrayList<>();
        private List<String> taskIds = new ArrayList<>();
        private TeacherSummaryQuery lastTeacherSummaryQuery;
        /** Server-owned attachment and harness continuation, stored in the existing session snapshot. */
        private String attachmentContextId;
        private com.internship.agent.harness.OpenCodeHarness.Dialogue harness;
        /** Framework-independent continuation. Credentials and raw graph state never belong here. */
        private Map<String,Object> runtime;
        /** 会话标题：默认取首条用户消息，可被用户重命名。 */
        private String title;
        /** 最近更新时间（毫秒），用于历史列表排序；不影响业务数据。 */
        private long updatedAt;
        /** 归档标记：归档只影响历史列表展示，不删除消息、业务记录或审计。 */
        private boolean archived;
    }

    @Data
    public static class Task {
        private String id;
        private String sessionId;
        private String ownerKey;
        private Actor actor;
        private String scopeFingerprint;
        private PageContext pageContext;
        private String message;
        private String attachmentContextId;
        private String harness;
        private Map<String,Object> runtime;
        /** Validated UI proposals only; the browser performs its own page/form-instance checks. */
        private List<Map<String,Object>> uiActions = new ArrayList<>();
        private Map<String,Object> importReceipt;
        private String status = "QUEUED";
        private String answer = "";
        private String errorCode;
        private String errorMessage;
        private List<Step> steps = new ArrayList<>();
        private List<ToolResult> cards = new ArrayList<>();
        private int modelCalls;
        private int toolCalls;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private long modelDurationMs;
        private boolean usageAvailable;
        private long version;
        private String editOf;
        private int editRound;
        private Integer turnIndex;
        private long createdAt;
        /** Execution budget starts after admission to a worker; persisted inside the existing JSON snapshot. */
        private long startedAt;
        private long updatedAt;
        private String runtimeId;
        private String clientRequestId;
        /** Automatic UI continuation inherits the original request and can never execute business writes. */
        private String parentTaskId;
        private int uiContinuationDepth;
        /** Invocation-local acknowledgement; never exposed or trusted as a browser input. */
        @com.fasterxml.jackson.annotation.JsonIgnore
        private String reconciledCancellationRunId;
        private String rootMessageId;
        private int revisionNumber = 1;
        private String responseKind = "ANSWER";
        private List<String> allowedOperations = List.of("EDIT","REGENERATE");

        public boolean terminal() {
            return Set.of("SUCCEEDED", "FAILED", "CANCELLED", "WAITING_INPUT").contains(status);
        }

        public void addUsage(AgentUsage usage) {
            if (usage == null) return;
            promptTokens += usage.promptTokens();
            completionTokens += usage.completionTokens();
            totalTokens += usage.totalTokens();
            modelDurationMs += usage.durationMs();
            usageAvailable = usageAvailable || usage.usageAvailable();
        }
    }
}
