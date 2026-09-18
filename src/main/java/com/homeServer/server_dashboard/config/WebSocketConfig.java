package com.homeServer.server_dashboard.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
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

import com.homeServer.server_dashboard.security.WebSocketAllowedOrigins;

import java.util.Arrays;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final WebSocketAllowedOrigins allowedOrigins;

    public WebSocketConfig(
            @Value("${dashboard.websocket.allowed-origin-patterns:*}") String allowedOriginPatterns,
            @Value("${dashboard.websocket.allow-wildcard-origins:false}") boolean allowWildcardOrigins,
            Environment environment) {
        // No construtor para que um curinga em producao derrube a inicializacao do contexto, e nao
        // apenas registre um aviso que ninguem le.
        this.allowedOrigins = new WebSocketAllowedOrigins(
                allowedOriginPatterns, Arrays.asList(environment.getActiveProfiles()), allowWildcardOrigins);
    }

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

    /**
     * Pool exclusivo dos heartbeats STOMP.
     *
     * <p>Ele era {@code @Primary} e, por isso, virava tambem o scheduler de todas as
     * {@code @Scheduled} da aplicacao — cinco tarefas disputando as mesmas duas threads que
     * mantinham a conexao viva. Com a coleta de metricas ocupando uma delas em tempo integral, o
     * heartbeat podia atrasar e derrubar o WebSocket do cliente. Agora sao pools separados.
     */
    @Bean
    public TaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    /**
     * Pool das tarefas {@code @Scheduled} (metricas, docker, historico, limpeza de tentativas de
     * login). O nome {@code taskScheduler} e' o que o Spring procura por convencao, e o
     * {@code @Primary} garante a resolucao por tipo mesmo havendo mais de um TaskScheduler.
     */
    @Bean(name = "taskScheduler")
    @Primary
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2);
        scheduler.setThreadNamePrefix("dashboard-sched-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins.patterns())
                .addInterceptors(new HttpSessionHandshakeInterceptor())
                .withSockJS();
    }
}
