package com.internship.agent.business;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatResponse;
import java.util.List;
import reactor.core.publisher.Flux;

public interface AgentModel {
    ChatResponse call(List<Message> messages, List<AgentReadTools.Spec> tools);
    default Flux<ChatResponse> stream(List<Message> messages,List<AgentReadTools.Spec> tools){
        return Flux.defer(()->Flux.just(call(messages,tools)));
    }
    /** A separate, tool-free review pass; it shares the configured provider and runtime limits. */
    default Flux<ChatResponse> review(List<Message> messages) { return stream(messages, List.of()); }
}
