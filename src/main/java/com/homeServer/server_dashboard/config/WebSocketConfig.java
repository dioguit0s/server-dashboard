package com.homeServer.server_dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    @Value("${dashboard.websocket.allowed-origin-patterns:*}")
    private String allowedOriginPatterns;

    @Override
    public void configureClientInboundChannel(org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(@Nullable Message<?> message, MessageChannel channel) {
                if (message == null) return message;
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && accessor.getCommand() != null) {
                    StompCommand cmd = accessor.getCommand();
                    String sessionId = accessor.getSessionId();
                    if (cmd == StompCommand.CONNECT) {
                        log.info("[ServerDash] STOMP CONNECT session={}", sessionId);
                    } else if (cmd == StompCommand.DISCONNECT) {
                        log.info("[ServerDash] STOMP DISCONNECT session={}", sessionId);
                    } else if (cmd == StompCommand.SUBSCRIBE) {
                        String dest = accessor.getDestination();
                        log.debug("[ServerDash] STOMP SUBSCRIBE session={} dest={}", sessionId, dest);
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
        config.setApplicationDestinationPrefixes("/app");
    }

    @Bean
    @Primary
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] patterns = Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        if (patterns.length == 0) {
            patterns = new String[]{"*"};
        }
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(patterns)
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .withSockJS();
    }
}
