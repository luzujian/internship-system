package com.internship.agent.business;

import com.internship.service.AIModelService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

// P4：模型故障上限回归。Agent 对话/填表链路单次调用上限 = 连接10秒 + 读取40秒，
// maxAttempts=1 不重试；教师/管理员 preview 为连接2秒/读取10秒。锁定时长，
// 防止回到“近20分钟才失败”。只打本地回环桩，不连外网、不碰业务数据。
class DeepSeekAgentModelTimeoutTest {
    private ServerSocket server;
    private Thread serverThread;

    @AfterEach
    void tearDown() throws IOException {
        if (server != null && !server.isClosed()) server.close();
        if (serverThread != null) serverThread.interrupt();
    }

    private DeepSeekAgentModel modelOnPort(int port) {
        AIModelService models=mock(AIModelService.class);
        var row=new com.internship.entity.AIModel();row.setModelCode("deepseek-flash");row.setStatus(1);
        org.mockito.Mockito.when(models.findDefaultModel()).thenReturn(row);
        return new DeepSeekAgentModel("test-key", "http://127.0.0.1:" + port,
                "deepseek-flash", models);
    }

    @Test
    void refusedConnectionFailsFastWithoutRetry() throws Exception {
        int port;
        try (ServerSocket probe = new ServerSocket(0)) {
            port = probe.getLocalPort();
        }
        DeepSeekAgentModel model = modelOnPort(port);
        long started = System.currentTimeMillis();
        assertThrows(Exception.class, () -> model.call(List.of(new UserMessage("hi")), List.of()));
        assertTrue(System.currentTimeMillis() - started < 15000, "连接拒绝应快速失败");
    }

    @Test
    void silentConnectionFailsWithinReadCeiling() throws Exception {
        server = new ServerSocket(0);
        int port = server.getLocalPort();
        ServerSocket acceptSock = server;
        serverThread = new Thread(() -> {
            try (Socket s = acceptSock.accept()) {
                Thread.sleep(180000);
            } catch (Exception ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        DeepSeekAgentModel model = modelOnPort(port);
        long started = System.currentTimeMillis();
        assertThrows(Exception.class, () -> model.call(List.of(new UserMessage("hi")), List.of()));
        long elapsed = System.currentTimeMillis() - started;
        assertTrue(elapsed >= 30000, "应等到读取超时而非立即失败，实际 " + elapsed + "ms");
        assertTrue(elapsed < 90000, "必须有上界，不得长期卡死，实际 " + elapsed + "ms");
    }

    @Test
    void serverErrorDoesNotTriggerRetry() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = new ServerSocket(0);
        int port = server.getLocalPort();
        ServerSocket acceptSock = server;
        serverThread = new Thread(() -> {
            while (!acceptSock.isClosed()) {
                try (Socket s = acceptSock.accept()) {
                    InputStream in = s.getInputStream();
                    StringBuilder head = new StringBuilder();
                    int b;
                    while ((b = in.read()) != -1) {
                        head.append((char) b);
                        if (head.length() > 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
                        if (head.length() > 65536) break;
                    }
                    hits.incrementAndGet();
                    byte[] body = "{\"error\":{\"message\":\"boom\"}}".getBytes(StandardCharsets.UTF_8);
                    String resp = "HTTP/1.1 500 X\r\nContent-Type: application/json\r\nContent-Length: "
                            + body.length + "\r\nConnection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(resp.getBytes(StandardCharsets.UTF_8));
                    out.write(body);
                    out.flush();
                } catch (Exception ignored) {
                    break;
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        DeepSeekAgentModel model = modelOnPort(port);
        assertThrows(Exception.class, () -> model.call(List.of(new UserMessage("hi")), List.of()));
        assertEquals(1, hits.get(), "maxAttempts=1，不得重试");
    }

    @Test
    void parallelCallsDoNotInterfere() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = new ServerSocket(0);
        int port = server.getLocalPort();
        ServerSocket acceptSock = server;
        Thread acceptor = new Thread(() -> {
            while (!acceptSock.isClosed()) {
                try (Socket s = acceptSock.accept()) {
                    InputStream in = s.getInputStream();
                    StringBuilder head = new StringBuilder();
                    int b;
                    while ((b = in.read()) != -1) {
                        head.append((char) b);
                        if (head.length() > 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
                        if (head.length() > 65536) break;
                    }
                    hits.incrementAndGet();
                    byte[] body = "{\"error\":{\"message\":\"busy\"}}".getBytes(StandardCharsets.UTF_8);
                    String resp = "HTTP/1.1 500 X\r\nContent-Type: application/json\r\nContent-Length: "
                            + body.length + "\r\nConnection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(resp.getBytes(StandardCharsets.UTF_8));
                    out.write(body);
                    out.flush();
                } catch (Exception ignored) {
                    break;
                }
            }
        });
        acceptor.setDaemon(true);
        acceptor.start();
        DeepSeekAgentModel model = modelOnPort(port);
        long started = System.currentTimeMillis();
        List<Thread> workers = new java.util.ArrayList<>();
        AtomicInteger failures = new AtomicInteger();
        for (int i = 0; i < 8; i++) {
            Thread w = new Thread(() -> {
                try {
                    model.call(List.of(new UserMessage("hi")), List.of());
                } catch (Exception expected) {
                    failures.incrementAndGet();
                }
            });
            workers.add(w);
            w.start();
        }
        for (Thread w : workers) w.join(60000);
        assertEquals(8, failures.get(), "8 路并行应各自独立失败，无串扰");
        assertEquals(8, hits.get(), "每路恰好一次请求，不多不少");
        assertTrue(System.currentTimeMillis() - started < 60000, "并行失败应快速收敛");
    }
    @Test
    void slowDripBodyStillHitsReadCeiling() throws Exception {
        server = new ServerSocket(0);
        int port = server.getLocalPort();
        ServerSocket acceptSock = server;
        serverThread = new Thread(() -> {
            try (Socket s = acceptSock.accept()) {
                InputStream in = s.getInputStream();
                StringBuilder head = new StringBuilder();
                int b;
                while ((b = in.read()) != -1) {
                    head.append((char) b);
                    if (head.length() > 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
                    if (head.length() > 65536) break;
                }
                OutputStream out = s.getOutputStream();
                String headers = "HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\n"
                        + "Transfer-Encoding: chunked\r\nConnection: keep-alive\r\n\r\n";
                out.write(headers.getBytes(StandardCharsets.UTF_8));
                out.write("1e\r\ndata: {\"choices\":[{\"delta\":{\r\n".getBytes(StandardCharsets.UTF_8));
                out.flush();
                Thread.sleep(180000);
            } catch (Exception ignored) {
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        DeepSeekAgentModel model = modelOnPort(port);
        long started = System.currentTimeMillis();
        assertThrows(Exception.class, () -> model.call(List.of(new UserMessage("hi")), List.of()));
        long elapsed = System.currentTimeMillis() - started;
        assertTrue(elapsed >= 30000, "响应体中途停顿也应等到读取超时，实际 " + elapsed + "ms");
        assertTrue(elapsed < 90000, "必须有上界，不得长期卡死，实际 " + elapsed + "ms");
    }

    @Test
    void malformedSuccessBodyFailsWithoutRetry() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        server = new ServerSocket(0);
        int port = server.getLocalPort();
        ServerSocket acceptSock = server;
        serverThread = new Thread(() -> {
            while (!acceptSock.isClosed()) {
                try (Socket s = acceptSock.accept()) {
                    InputStream in = s.getInputStream();
                    StringBuilder head = new StringBuilder();
                    int b;
                    while ((b = in.read()) != -1) {
                        head.append((char) b);
                        if (head.length() > 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) break;
                        if (head.length() > 65536) break;
                    }
                    hits.incrementAndGet();
                    byte[] body = "this is not json{{{".getBytes(StandardCharsets.UTF_8);
                    String resp = "HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                            + body.length + "\r\nConnection: close\r\n\r\n";
                    OutputStream out = s.getOutputStream();
                    out.write(resp.getBytes(StandardCharsets.UTF_8));
                    out.write(body);
                    out.flush();
                } catch (Exception ignored) {
                    break;
                }
            }
        });
        serverThread.setDaemon(true);
        serverThread.start();
        DeepSeekAgentModel model = modelOnPort(port);
        long started = System.currentTimeMillis();
        assertThrows(Exception.class, () -> model.call(List.of(new UserMessage("hi")), List.of()));
        assertEquals(1, hits.get(), "畸形 200 不得重试");
        assertTrue(System.currentTimeMillis() - started < 60000, "畸形 200 应快速失败，不等待超时");
    }
}
