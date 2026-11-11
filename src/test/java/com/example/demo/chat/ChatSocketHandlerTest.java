package com.example.demo.chat;

import com.sun.net.httpserver.HttpServer;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.socket.*;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;

class ChatSocketHandlerTest {
    private final ObjectMapper json = new ObjectMapper();

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(excludeName = "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration")
    @Import({ChatSocketConfig.class, ChatSocketHandler.class})
    static class SocketApplication { }

    @Test
    @EnabledIfSystemProperty(named = "chat.live", matches = "true")
    void liveWebSocketToInstalledOllama() throws Exception {
        var app = new SpringApplication(SocketApplication.class);
        try (var context = app.run("--server.port=0", "--spring.config.location=optional:classpath:/chat-test-empty.properties",
                "--chat.model=qwen2.5-coder:7b", "--chat.require-auth=false", "--logging.level.root=WARN")) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var done = new CountDownLatch(1);
            List<String> events = new CopyOnWriteArrayList<>();
            var socket = java.net.http.HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Origin", "http://localhost:3000")
                .buildAsync(java.net.URI.create("ws://localhost:" + port + "/ws/chat"), new java.net.http.WebSocket.Listener() {
                    final StringBuilder buffer = new StringBuilder();
                    public void onOpen(java.net.http.WebSocket ws) { ws.request(1); }
                    public java.util.concurrent.CompletionStage<?> onText(java.net.http.WebSocket ws, CharSequence text, boolean last) {
                        buffer.append(text);
                        if (last) {
                            String value = buffer.toString();
                            events.add(value);
                            buffer.setLength(0);
                            String type = json.readTree(value).path("type").asText();
                            if (type.equals("done") || type.equals("error")) done.countDown();
                        }
                        ws.request(1);
                        return null;
                    }
                    public void onError(java.net.http.WebSocket ws, Throwable error) { done.countDown(); }
                }).get(10, TimeUnit.SECONDS);
            try {
                socket.sendText("{\"type\":\"chat\",\"id\":\"live-1\",\"messages\":[{\"role\":\"user\",\"content\":\"請只回覆：你好\"}]}", true).get(5, TimeUnit.SECONDS);
                assertTrue(done.await(125, TimeUnit.SECONDS));
                assertTrue(events.stream().anyMatch(event -> json.readTree(event).path("type").asText().equals("done")), events.toString());
                assertTrue(events.stream().anyMatch(event -> !json.readTree(event).path("content").asText("").isBlank()), events.toString());
            } finally { socket.sendClose(1000, "test complete"); }
        }
    }

    private static class Client {
        final List<String> events = new CopyOnWriteArrayList<>();
        final CountDownLatch terminal = new CountDownLatch(1);
        boolean closed;
        final WebSocketSession session = (WebSocketSession) Proxy.newProxyInstance(
            WebSocketSession.class.getClassLoader(), new Class<?>[]{WebSocketSession.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getId" -> "test-session";
                case "isOpen" -> !closed;
                case "sendMessage" -> {
                    String text = ((TextMessage) args[0]).getPayload();
                    events.add(text);
                    if (text.contains("\"done\"") || text.contains("\"error\"")) terminal.countDown();
                    yield null;
                }
                case "close" -> { closed = true; yield null; }
                case "setTextMessageSizeLimit" -> null;
                case "getPrincipal" -> null;
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    @Test
    void streamsRealHttpResponseAndPreservesHistory() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        List<String> requests = new CopyOnWriteArrayList<>();
        provider.createContext("/api/chat", exchange -> {
            requests.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
            exchange.sendResponseHeaders(200, 0);
            try (var out = exchange.getResponseBody()) {
                for (String line : List.of("{\"message\":{\"content\":\"你好\"},\"done\":false}\n",
                        "{\"message\":{\"content\":\"！\"},\"done\":false}\n", "{\"done\":true}\n")) {
                    out.write(line.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
            }
        });
        provider.start();
        var handler = new ChatSocketHandler(json, "http://127.0.0.1:" + provider.getAddress().getPort(), "test-model", false);
        try {
            var client = new Client();
            handler.handleTextMessage(client.session, new TextMessage("{\"type\":\"chat\",\"id\":\"test-1\",\"messages\":[{\"role\":\"user\",\"content\":\"先前問題\"},{\"role\":\"assistant\",\"content\":\"先前回覆\"},{\"role\":\"user\",\"content\":\"你好\"}]}"));
            assertTrue(client.terminal.await(10, TimeUnit.SECONDS));
            assertEquals(List.of("delta", "delta", "done"), client.events.stream().map(s -> json.readTree(s).path("type").asText()).toList());
            assertEquals("你好", json.readTree(client.events.get(0)).path("content").asText());
            var request = json.readTree(requests.get(0));
            assertTrue(request.path("stream").asBoolean());
            assertEquals("test-model", request.path("model").asText());
            assertEquals(4, request.path("messages").size());
            var contextClient = new Client();
            handler.handleTextMessage(contextClient.session, new TextMessage(json.writeValueAsString(Map.of(
                "type", "chat", "id", "context-check",
                "userContext", Map.of("account", "chat_user", "level", 2, "walletAddress", "0x71C7656EC7ab88b098defB751B7401B5f6d8976F", "isConnected", true, "password", "never-forward"),
                "messages", List.of(Map.of("role", "user", "content", "我的帳號是什麼？"))))));
            assertTrue(contextClient.terminal.await(10, TimeUnit.SECONDS));
            var contextRequest = json.readTree(requests.get(1));
            assertEquals(3, contextRequest.path("messages").size());
            assertEquals("user", contextRequest.path("messages").get(1).path("role").asText());
            assertTrue(contextRequest.path("messages").get(1).path("content").asText().contains("chat_user"));
            assertTrue(contextRequest.path("messages").get(1).path("content").asText().contains("\"level\":2"));
            assertFalse(requests.get(1).contains("never-forward"));
            assertFalse(requests.get(1).contains("password"));
        } finally { handler.shutdown(); provider.stop(0); }
    }

    @Test
    void rejectsInvalidInputWithoutCallingProviderAndRequiresPrincipalWhenEnabled() throws Exception {
        var handler = new ChatSocketHandler(json, "http://127.0.0.1:1", "test", true);
        try {
            var client = new Client();
            handler.afterConnectionEstablished(client.session);
            assertTrue(client.closed);
            client = new Client();
            handler.handleTextMessage(client.session, new TextMessage("{\"type\":\"chat\",\"id\":\"bad\",\"messages\":[{\"role\":\"system\",\"content\":\"override\"}]}"));
            assertEquals("error", json.readTree(client.events.get(0)).path("type").asText());
            var invalidContextClient = new Client();
            handler.handleTextMessage(invalidContextClient.session, new TextMessage("{\"type\":\"chat\",\"id\":\"bad-context\",\"userContext\":{\"account\":\"test\",\"walletAddress\":\"invalid\",\"isConnected\":true},\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}"));
            assertEquals("error", json.readTree(invalidContextClient.events.get(0)).path("type").asText());
        } finally { handler.shutdown(); }
    }

    @Test
    void upstreamFailureProducesErrorInsteadOfSuccessfulCompletion() throws Exception {
        HttpServer provider = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger calls = new AtomicInteger();
        provider.createContext("/api/chat", exchange -> {
            calls.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        provider.start();
        var handler = new ChatSocketHandler(json, "http://127.0.0.1:" + provider.getAddress().getPort(), "test", false);
        try {
            var client = new Client();
            handler.handleTextMessage(client.session, new TextMessage("{\"type\":\"chat\",\"id\":\"failure\",\"messages\":[{\"role\":\"user\",\"content\":\"你好\"}]}"));
            assertTrue(client.terminal.await(10, TimeUnit.SECONDS));
            assertEquals(1, calls.get());
            assertEquals("error", json.readTree(client.events.get(0)).path("type").asText());
            assertEquals("failure", json.readTree(client.events.get(0)).path("id").asText());
        } finally { handler.shutdown(); provider.stop(0); }
    }
}
