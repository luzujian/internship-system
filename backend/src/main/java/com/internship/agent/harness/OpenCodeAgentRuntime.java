package com.internship.agent.harness;

import com.internship.agent.business.AgentReadTools.Spec;
import com.internship.agent.business.AgentTypes.Session;
import com.internship.agent.business.AgentTypes.Task;
import java.util.List;

/** Compatibility adapter for existing conversations and an explicit deployment rollback. */
public final class OpenCodeAgentRuntime implements BusinessAgentRuntime {
    private final OpenCodeHarness harness;
    public OpenCodeAgentRuntime(OpenCodeHarness harness) { this.harness=harness; }
    public String engine() { return "opencode"; }
    public boolean enabled() { return harness.enabled(); }
    public Result run(Session session, Task task, String contextKey, String system, List<Spec> specs,
                      HarnessToolBridge.Executor tools, Runnable checkpoint, Runnable persist) throws Exception {
        if(session.getHarness()==null || task.getEditOf()!=null) session.setHarness(new OpenCodeHarness.Dialogue());
        var result=harness.run(session.getId(),task.getOwnerKey(),session.getHarness(),contextKey,
                system,task.getMessage(),specs,tools,checkpoint,persist);
        return new Result(result.answer(),result.waiting(),result.modelCalls(),result.inputTokens(),result.outputTokens());
    }
    public void cancel(Session session, Task task) { /* The interrupted worker revokes OpenCode's leases. */ }
    public void deleteConversation(Session session) { harness.deleteConversation(session.getId(),session.getOwnerKey()); }
}
