package com.internship.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.reactive.ClientHttpRequestDecorator;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ClientRequest;
import reactor.core.publisher.Mono;

/** Qwen defaults to extensive reasoning; business UI calls need bounded, direct answers. */
public final class QwenRequestSupport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private QwenRequestSupport() {}

    static byte[] normalize(byte[] body) throws IOException {
        var value = JSON.readTree(body);
        if (value instanceof ObjectNode object && "qwen3.8-omni-flash".equals(object.path("model").asText())) {
            if (!object.has("reasoning_effort") && !object.has("thinking_budget")) object.put("reasoning_effort", "none");
            return JSON.writeValueAsBytes(object);
        }
        return body;
    }
    public static ClientHttpRequestInterceptor interceptor() {
        return (request, body, execution) -> {
            byte[] normalized = normalize(body);
            request.getHeaders().setContentLength(normalized.length);
            return execution.execute(request, normalized);
        };
    }
    public static ExchangeFilterFunction filter() {
        return (request, next) -> next.exchange(ClientRequest.from(request).body((output, context) ->
            request.body().insert(new ClientHttpRequestDecorator(output) {
                @Override public Mono<Void> writeWith(org.reactivestreams.Publisher<? extends org.springframework.core.io.buffer.DataBuffer> body) {
                    return DataBufferUtils.join(body).flatMap(buffer -> {
                        byte[] bytes = new byte[buffer.readableByteCount()];
                        buffer.read(bytes); DataBufferUtils.release(buffer);
                        try {
                            byte[] normalized = normalize(bytes);
                            getHeaders().setContentLength(normalized.length);
                            return super.writeWith(Mono.just(bufferFactory().wrap(normalized)));
                        } catch (IOException e) { return Mono.error(e); }
                    });
                }
            }, context)).build());
    }
}
