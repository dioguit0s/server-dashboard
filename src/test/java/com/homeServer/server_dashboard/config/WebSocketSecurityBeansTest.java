package com.homeServer.server_dashboard.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

/**
 * Depois de fechar o dashboard atras de login, nenhum topico STOMP pode continuar aberto a
 * anonimo — nem o antigo {@code /topic/public}, que so' publicava metricas resumidas mas ainda
 * assim expunha dados do servidor sem autenticacao.
 */
class WebSocketSecurityBeansTest {

    private final AuthorizationManager<Message<?>> authorizationManager =
            new WebSocketSecurityBeans().websocketMessageAuthorizationManager();

    @Test
    void anonymousCannotSubscribeToAnyTopic() {
        assertThat(decide(null, "/topic/public").isGranted()).isFalse();
        assertThat(decide(null, "/topic/admin").isGranted()).isFalse();
        assertThat(decide(null, "/topic/docker").isGranted()).isFalse();
    }

    @Test
    void authenticatedViewerCanSubscribeToEveryTopic() {
        Authentication viewer = new UsernamePasswordAuthenticationToken(
                "observador", null, AuthorityUtils.createAuthorityList("ROLE_VIEWER"));

        assertThat(decide(viewer, "/topic/public").isGranted()).isTrue();
        assertThat(decide(viewer, "/topic/admin").isGranted()).isTrue();
        assertThat(decide(viewer, "/topic/docker").isGranted()).isTrue();
    }

    private org.springframework.security.authorization.AuthorizationResult decide(Authentication authentication, String destination) {
        SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.SUBSCRIBE);
        accessor.setDestination(destination);
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Authentication resolved = authentication == null
                // Anonimo de verdade, como o AnonymousAuthenticationFilter da HttpSecurity produziria
                // para uma requisicao sem sessao — nao tem VIEWER/ADMIN, so' ROLE_ANONYMOUS.
                ? new AnonymousAuthenticationToken("key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS"))
                : authentication;
        return authorizationManager.authorize(() -> resolved, message);
    }
}
