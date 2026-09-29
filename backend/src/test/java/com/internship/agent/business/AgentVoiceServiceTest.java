package com.internship.agent.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.internship.config.AgentVoiceWebSocketConfig;
import org.junit.jupiter.api.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistration;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.OriginHandshakeInterceptor;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentVoiceServiceTest {
    final ObjectMapper json = new ObjectMapper();
    final AgentIdentityService identity = mock(AgentIdentityService.class);
    final AgentCapabilityService capabilities = mock(AgentCapabilityService.class);
    final AgentFeatureGate gate = mock(AgentFeatureGate.class);
    final HttpClient client = mock(HttpClient.class);
    final WebSocket.Builder builder = mock(WebSocket.Builder.class);
    final WebSocket upstream = mock(WebSocket.class);
    final ScheduledExecutorService clock = mock(ScheduledExecutorService.class);
    final AtomicLong now = new AtomicLong(100_000);
    final List<WebSocket.Listener> listeners = new CopyOnWriteArrayList<>();
    final List<Runnable> deadlines = new ArrayList<>();
    Runnable check;
    AgentVoiceService service;

    @BeforeEach void setup() {
        when(identity.resolve(anyString())).thenAnswer(call -> actor(Long.parseLong(call.<String>getArgument(0).substring(7)), false));
        when(capabilities.has(any(), eq("voice_input"))).thenReturn(true);
        when(client.newWebSocketBuilder()).thenReturn(builder);
        when(builder.connectTimeout(any())).thenReturn(builder);
        when(builder.header(anyString(), anyString())).thenReturn(builder);
        when(builder.buildAsync(any(), any())).thenAnswer(call -> { listeners.add(call.getArgument(1)); return new CompletableFuture<WebSocket>(); });
        when(upstream.sendText(anyString(), eq(true))).thenReturn(CompletableFuture.completedFuture(upstream));
        when(clock.scheduleAtFixedRate(any(Runnable.class), eq(1L), eq(1L), eq(TimeUnit.SECONDS))).thenAnswer(call -> { check=call.getArgument(0); return mock(ScheduledFuture.class); });
        when(clock.schedule(any(Runnable.class), eq(5L), eq(TimeUnit.SECONDS))).thenAnswer(call -> { deadlines.add(call.getArgument(0)); return mock(ScheduledFuture.class); });
        service = new AgentVoiceService(identity, capabilities, gate, json, "test-key", "wss://voice.test/realtime", "test-model", client, clock, now::get);
    }
    @AfterEach void close() { service.closeAll(); }

    private AgentTypes.Actor actor(long id, boolean readOnly) { return new AgentTypes.Actor("ROLE_COMPANY:"+id, id, "ROLE_COMPANY", "企业", null, null, null, readOnly, Set.of()); }
    private String ticket(long id) { return service.create("Bearer "+id).get("url").toString(); }
    private WebSocketSession session(String url) {
        var session=mock(WebSocketSession.class);
        when(session.getId()).thenReturn(UUID.randomUUID().toString());
        when(session.getUri()).thenReturn(URI.create("wss://app.test"+url));
        when(session.isOpen()).thenReturn(true);
        return session;
    }
    private WebSocketSession connect() throws Exception {
        var session=session(ticket(7)); service.afterConnectionEstablished(session); return session;
    }
    private void ready() { listeners.getLast().onOpen(upstream); event("{\"type\":\"session.updated\"}"); }
    private void event(String event) { listeners.getLast().onText(upstream,event,true); }
    private List<JsonNode> sent(WebSocketSession session) throws Exception {
        var capture=org.mockito.ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(session, atLeastOnce()).sendMessage(capture.capture());
        List<JsonNode> result=new ArrayList<>();
        for(var message:capture.getAllValues())result.add(json.readTree(message.getPayload().toString()));
        return result;
    }
    private void finish(WebSocketSession session) throws Exception { service.handleTextMessage(session,new TextMessage("{\"type\":\"finish\"}")); }

    private void dictation() {
        var settings=mock(AgentVoiceSettings.class);
        when(settings.resolve()).thenReturn(new AgentVoiceSettings.Connection("qwen3.8-omni-flash","https://provider.test/v1","secret"));
        org.springframework.test.util.ReflectionTestUtils.setField(service,"voiceSettings",settings);
    }
    private void audio(WebSocketSession session) throws Exception {
        byte[] pcm=new byte[6400];Arrays.fill(pcm,(byte)12);
        service.handleTextMessage(session,new TextMessage(json.writeValueAsString(Map.of("type","audio","audio",Base64.getEncoder().encodeToString(pcm)))));
    }
    @Test void dictationWaitsForFinishAndReturnsEditableTranscriptOnce() throws Exception {
        dictation();
        var pending=new CompletableFuture<java.net.http.HttpResponse<String>>();
        when(client.sendAsync(any(java.net.http.HttpRequest.class),any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn((CompletableFuture)pending);
        var session=connect();audio(session);
        verify(client,never()).sendAsync(any(),any(java.net.http.HttpResponse.BodyHandler.class));
        assertEquals("dictation",sent(session).getFirst().path("mode").asText());
        finish(session);finish(session);
        verify(client,times(1)).sendAsync(any(),any(java.net.http.HttpResponse.BodyHandler.class));
        var response=mock(java.net.http.HttpResponse.class);when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"choices\":[{\"finish_reason\":\"stop\",\"message\":{\"content\":\"人数改成五个，薪资不变\"}}]}");
        pending.complete(response);
        assertEquals(1,sent(session).stream().filter(e->e.path("type").asText().equals("final")).count());
        assertEquals("finished",sent(session).getLast().path("type").asText());
        verify(client,never()).newWebSocketBuilder();
    }
    @Test void closeCancelsPendingDictationAndLateOutputCannotWrite() throws Exception {
        dictation();var pending=new CompletableFuture<java.net.http.HttpResponse<String>>();
        when(client.sendAsync(any(java.net.http.HttpRequest.class),any(java.net.http.HttpResponse.BodyHandler.class))).thenReturn((CompletableFuture)pending);
        var session=connect();audio(session);finish(session);service.afterConnectionClosed(session,CloseStatus.NORMAL);
        assertTrue(pending.isCancelled());
        assertTrue(sent(session).stream().noneMatch(e->e.path("type").asText().equals("final")));
    }
    @Test void revokedPermissionBeforeFinishDoesNotSubmitAudio() throws Exception {
        dictation();var session=connect();audio(session);when(capabilities.has(any(),eq("voice_input"))).thenReturn(false);finish(session);
        verify(client,never()).sendAsync(any(),any(java.net.http.HttpResponse.BodyHandler.class));
        assertEquals("error",sent(session).getLast().path("type").asText());
    }
    @Test void wavHasBrowserPcmFormatAndCorrectLength() {
        var wav=java.nio.ByteBuffer.wrap(AgentVoiceService.wav(new byte[6400])).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(6444,wav.capacity());assertEquals(16000,wav.getInt(24));assertEquals(6400,wav.getInt(40));
    }

    @Test void explicitOriginsAreRegisteredAndForeignBrowserOriginIsRejected() throws Exception {
        var registry=mock(WebSocketHandlerRegistry.class);
        var registration=mock(WebSocketHandlerRegistration.class);
        when(registry.addHandler(service,"/ws/agent-voice")).thenReturn(registration);
        new AgentVoiceWebSocketConfig(service,"http://localhost:5173,http://127.0.0.1:5173").registerWebSocketHandlers(registry);
        verify(registration).setAllowedOrigins("http://localhost:5173","http://127.0.0.1:5173");
        var origin=new OriginHandshakeInterceptor(List.of("http://localhost:5173","http://127.0.0.1:5173"));
        var request=mock(ServerHttpRequest.class);var response=mock(ServerHttpResponse.class);
        HttpHeaders headers=new HttpHeaders();headers.setOrigin("https://untrusted.test");
        when(request.getHeaders()).thenReturn(headers);when(request.getURI()).thenReturn(URI.create("http://localhost:8080/ws/agent-voice"));
        assertFalse(origin.beforeHandshake(request,response,service,new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.FORBIDDEN);
        headers.setOrigin("http://localhost:5173");
        assertTrue(origin.beforeHandshake(request,response,service,new HashMap<>()));
    }

    @Test void ticketIsSingleUseAndNeverExposesProviderKey() throws Exception {
        String url=ticket(7);assertFalse(url.contains("test-key"));
        service.afterConnectionEstablished(session(url));
        var duplicate=session(url);service.afterConnectionEstablished(duplicate);
        verify(duplicate).close(CloseStatus.POLICY_VIOLATION);
        verify(builder,times(1)).buildAsync(any(),any());
        verify(builder).header("Authorization","Bearer test-key");
    }

    @Test void concurrentTicketConsumptionOpensExactlyOneRelay() throws Exception {
        String url=ticket(7);
        try(var pool=Executors.newFixedThreadPool(8)) {
            CountDownLatch start=new CountDownLatch(1);List<Future<?>> work=new ArrayList<>();
            for(int i=0;i<20;i++)work.add(pool.submit(()->{start.await();service.afterConnectionEstablished(session(url));return null;}));
            start.countDown();for(var item:work)item.get(5,TimeUnit.SECONDS);
        }
        verify(builder,times(1)).buildAsync(any(),any());
    }

    @Test void concurrentTicketCreationRespectsGlobalBound() throws Exception {
        try(var pool=Executors.newFixedThreadPool(8)) {
            CountDownLatch start=new CountDownLatch(1);List<Future<Boolean>> work=new ArrayList<>();
            for(int i=1;i<=80;i++){int id=i;work.add(pool.submit(()->{start.await();try{ticket(id);return true;}catch(AgentException e){assertEquals(429,e.getHttpStatus());return false;}}));}
            start.countDown();int accepted=0;for(var item:work)if(item.get(5,TimeUnit.SECONDS))accepted++;
            assertEquals(64,accepted);
        }
    }

    @Test void newTicketRevokesPreviousAndExactExpiryFailsClosed() throws Exception {
        String previous=ticket(7), latest=ticket(7);
        var old=session(previous);service.afterConnectionEstablished(old);verify(old).close(CloseStatus.POLICY_VIOLATION);
        now.addAndGet(30_000);
        var expired=session(latest);service.afterConnectionEstablished(expired);verify(expired).close(CloseStatus.POLICY_VIOLATION);
        verifyNoInteractions(builder);
    }

    @Test void ownerCannotOpenTwoActiveRelays() throws Exception {
        connect();var second=session(ticket(7));service.afterConnectionEstablished(second);
        verify(second).close(CloseStatus.POLICY_VIOLATION);
        verify(builder,times(1)).buildAsync(any(),any());
    }

    @Test void revokedOrReadOnlyIdentityCannotIssueTicketOrRedeemOldTicket() throws Exception {
        String ticket=ticket(7);
        when(capabilities.has(any(),eq("voice_input"))).thenReturn(false);
        assertThrows(AgentException.class,()->ticket(8));
        var revoked=session(ticket);service.afterConnectionEstablished(revoked);verify(revoked).close(CloseStatus.POLICY_VIOLATION);
        when(capabilities.has(any(),eq("voice_input"))).thenReturn(true);
        when(identity.resolve("Bearer 9")).thenReturn(actor(9,true));
        assertThrows(AgentException.class,()->ticket(9));
        verifyNoInteractions(builder);
    }

    @Test void permissionRevocationClosesActiveCaptureAndReportsReason() throws Exception {
        var session=connect();ready();
        when(capabilities.has(any(),eq("voice_input"))).thenReturn(false);
        now.addAndGet(5_001);check.run();
        assertTrue(sent(session).stream().anyMatch(row->"error".equals(row.path("type").asText())&&row.path("message").asText().contains("权限")));
        verify(upstream).abort();verify(session).close();
    }

    @Test void finishBeforeReadyIsAnErrorWithoutUpstreamFinish() throws Exception {
        var session=connect();finish(session);
        assertEquals("error",sent(session).getLast().path("type").asText());
        assertTrue(deadlines.isEmpty());verifyNoInteractions(upstream);
    }

    @Test void finalizationTimeoutNeverFabricatesFinished() throws Exception {
        var session=connect();ready();finish(session);finish(session);
        assertEquals(1,deadlines.size());deadlines.getFirst().run();
        var events=sent(session);
        assertEquals("error",events.getLast().path("type").asText());
        assertTrue(events.getLast().path("message").asText().contains("超时"));
        assertTrue(events.stream().noneMatch(row->"finished".equals(row.path("type").asText())));
        verify(upstream,times(1)).sendText(contains("session.finish"),eq(true));
    }

    @Test void finalTailPrecedesRealFinishedAndTimeoutCannotOverwriteIt() throws Exception {
        var session=connect();ready();finish(session);
        event("{\"type\":\"conversation.item.input_audio_transcription.completed\",\"item_id\":\"last\",\"transcript\":\"五个人\"}");
        event("{\"type\":\"session.finished\"}");deadlines.getFirst().run();
        var events=sent(session);
        assertEquals(List.of("ready","final","finished"),events.stream().map(row->row.path("type").asText()).toList());
        assertEquals("五个人",events.get(1).path("text").asText());
    }

    @Test void upstreamErrorOrTransportFailureClosesWithoutLeakingProviderDetails() throws Exception {
        var session=connect();ready();
        event("{\"type\":\"error\",\"message\":\"provider-secret-test-key\"}");
        var events=sent(session);assertEquals("error",events.getLast().path("type").asText());
        assertFalse(events.toString().contains("provider-secret"));verify(upstream).abort();
    }

    @Test void synchronousConnectionFailureDoesNotLeakActiveRelaySlot() throws Exception {
        when(builder.buildAsync(any(),any())).thenThrow(new IllegalArgumentException("test-key"));
        var session=connect();assertEquals("error",sent(session).getLast().path("type").asText());
        var second=connect();assertEquals("error",sent(second).getLast().path("type").asText());
        verify(builder,times(2)).buildAsync(any(),any());
    }

    @Test void sessionDurationLimitClosesCapture() throws Exception {
        var session=connect();ready();now.addAndGet(125_001);check.run();
        assertEquals("error",sent(session).getLast().path("type").asText());verify(upstream).abort();
    }

    @Test void malformedOrUnboundedAudioIsRejectedBeforeUpstream() throws Exception {
        var session=connect();ready();
        service.handleTextMessage(session,new TextMessage("{\"type\":\"audio\",\"audio\":\"AQ==\"}"));
        assertEquals("error",sent(session).getLast().path("type").asText());
        verify(upstream,never()).sendText(contains("input_audio_buffer.append"),eq(true));
    }
}
