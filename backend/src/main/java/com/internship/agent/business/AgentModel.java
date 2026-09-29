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
}
