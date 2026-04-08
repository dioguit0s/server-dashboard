package com.homeServer.server_dashboard.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.messaging.access.intercept.AuthorizationChannelInterceptor;
import org.springframework.security.messaging.context.SecurityContextChannelInterceptor;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WebSocketAuthorizationConfig implements WebSocketMessageBrokerConfigurer {

    private final AuthorizationManager<Message<?>> websocketMessageAuthorizationManager;

    public WebSocketAuthorizationConfig(
            @Qualifier("websocketMessageAuthorizationManager") AuthorizationManager<Message<?>> websocketMessageAuthorizationManager) {
        this.websocketMessageAuthorizationManager = websocketMessageAuthorizationManager;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        AuthorizationChannelInterceptor authz =
                new AuthorizationChannelInterceptor(websocketMessageAuthorizationManager);
        registration.interceptors(new SecurityContextChannelInterceptor(), authz);
    }
}
