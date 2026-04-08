package com.homeServer.server_dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import static org.springframework.messaging.simp.SimpMessageType.CONNECT;
import static org.springframework.messaging.simp.SimpMessageType.DISCONNECT;
import static org.springframework.messaging.simp.SimpMessageType.HEARTBEAT;
import static org.springframework.messaging.simp.SimpMessageType.MESSAGE;
import static org.springframework.messaging.simp.SimpMessageType.UNSUBSCRIBE;

@Configuration
public class WebSocketSecurityBeans {

    @Bean(name = "websocketMessageAuthorizationManager")
    AuthorizationManager<Message<?>> websocketMessageAuthorizationManager() {
        MessageMatcherDelegatingAuthorizationManager.Builder messages =
                MessageMatcherDelegatingAuthorizationManager.builder();
        return messages
                .simpTypeMatchers(CONNECT, UNSUBSCRIBE, DISCONNECT, HEARTBEAT).permitAll()
                .simpSubscribeDestMatchers("/topic/public").permitAll()
                .simpSubscribeDestMatchers("/topic/admin", "/topic/docker").hasRole("ADMIN")
                .simpTypeMatchers(MESSAGE).denyAll()
                .anyMessage().denyAll()
                .build();
    }
}
