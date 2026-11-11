package com.example.demo.chat;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ChatSocketConfig implements WebSocketConfigurer {
    private final ChatSocketHandler handler;
    private final String[] origins;

    public ChatSocketConfig(ChatSocketHandler handler,
            @Value("${chat.allowed-origins:http://localhost:3000,http://127.0.0.1:3000}") String[] origins) {
        this.handler = handler;
        this.origins = origins;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/chat").setAllowedOrigins(origins);
    }
}
