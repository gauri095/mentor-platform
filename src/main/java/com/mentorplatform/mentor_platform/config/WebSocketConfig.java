package com.mentorplatform.mentor_platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket + STOMP configuration for the Mentor–Student Platform.
 *
 * Connection flow:
 *   1. Client connects to ws://localhost:8080/ws  (SockJS fallback available)
 *   2. Client subscribes to /topic/session/{id}   (code + chat broadcast)
 *   3. Client sends to /app/code or /app/chat
 *   4. Server broadcasts to all subscribers on /topic/session/{id}
 *
 * Frontend usage with @stomp/stompjs:
 *
 *   import { Client } from '@stomp/stompjs';
 *   import SockJS from 'sockjs-client';
 *
 *   const client = new Client({
 *     webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
 *   });
 *
 *   client.onConnect = () => {
 *     client.subscribe(`/topic/session/${sessionId}`, msg => {
 *       const payload = JSON.parse(msg.body);
 *     });
 *     client.publish({
 *       destination: '/app/code',
 *       body: JSON.stringify({ sessionId, code }),
 *     });
 *   };
 *
 *   client.activate();
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    /**
     * Register the STOMP endpoint that the frontend connects to.
     *
     * /ws              — the WebSocket handshake URL
     * .withSockJS()    — adds SockJS fallback for environments that block
     *                    raw WebSocket (some corporate proxies / older browsers)
     *
     * Restrict allowedOriginPatterns to your Vercel URL in production:
     *   .setAllowedOriginPatterns("https://mentor-app.vercel.app")
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")   // tighten in production
                .withSockJS();
    }

    /**
     * Configure the message broker routing.
     *
     * /topic  — server-to-client broadcast prefix (pub/sub)
     *           Clients subscribe to e.g. /topic/session/abc-123
     *
     * /app    — client-to-server message prefix
     *           Routes to @MessageMapping methods in controllers:
     *             /app/code  →  @MessageMapping("/code")
     *             /app/chat  →  @MessageMapping("/chat")
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}