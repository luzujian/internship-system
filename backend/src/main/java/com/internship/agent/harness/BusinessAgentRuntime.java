package com.internship.agent.harness;

import com.internship.agent.business.AgentReadTools.Spec;
import com.internship.agent.business.AgentTypes.Session;
import com.internship.agent.business.AgentTypes.Task;
import java.util.List;

/** The application owns authorization and receipts; runtimes only orchestrate guarded tools. */
public interface BusinessAgentRuntime {
    record Result(String answer, boolean waiting, long modelCalls, long inputTokens, long outputTokens) {}
    String engine();
    boolean enabled();
    Result run(Session session, Task task, String contextKey, String system, List<Spec> specs,
               HarnessToolBridge.Executor tools, Runnable checkpoint, Runnable persist) throws Exception;
    void cancel(Session session, Task task);
    void deleteConversation(Session session);
}
