package com.homeServer.server_dashboard.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicSubscriptionRegistryTest {

    private static final String PUBLIC_TOPIC = "/topic/public";
    private static final String ADMIN_TOPIC = "/topic/admin";

    private TopicSubscriptionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TopicSubscriptionRegistry();
    }

    @Test
    void registraInscricaoEIgnoraOutrosDestinos() {
        registry.onSubscribe(subscribe("s1", "sub-0", PUBLIC_TOPIC));

        assertTrue(registry.hasSubscribers(PUBLIC_TOPIC));
        assertFalse(registry.hasSubscribers(ADMIN_TOPIC));
    }

    @Test
    void semInscricaoNenhumTopicoTemAssinante() {
        assertFalse(registry.hasSubscribers(PUBLIC_TOPIC));
        assertEquals(0, registry.countSubscribers(PUBLIC_TOPIC));
    }

    @Test
    void unsubscribeRemoveApenasAInscricaoCorrespondente() {
        registry.onSubscribe(subscribe("s1", "sub-0", PUBLIC_TOPIC));
        registry.onSubscribe(subscribe("s1", "sub-1", ADMIN_TOPIC));

        registry.onUnsubscribe(unsubscribe("s1", "sub-0"));

        assertFalse(registry.hasSubscribers(PUBLIC_TOPIC));
        assertTrue(registry.hasSubscribers(ADMIN_TOPIC));
    }

    @Test
    void disconnectRemoveTodasAsInscricoesDaSessao() {
        registry.onSubscribe(subscribe("s1", "sub-0", PUBLIC_TOPIC));
        registry.onSubscribe(subscribe("s1", "sub-1", ADMIN_TOPIC));
        registry.onSubscribe(subscribe("s2", "sub-0", PUBLIC_TOPIC));

        registry.onDisconnect(disconnect("s1"));

        assertTrue(registry.hasSubscribers(PUBLIC_TOPIC), "a sessao s2 continua inscrita");
        assertFalse(registry.hasSubscribers(ADMIN_TOPIC));
        assertEquals(1, registry.countSubscribers(PUBLIC_TOPIC));
    }

    @Test
    void contaInscricoesDeSessoesDiferentes() {
        registry.onSubscribe(subscribe("s1", "sub-0", PUBLIC_TOPIC));
        registry.onSubscribe(subscribe("s2", "sub-0", PUBLIC_TOPIC));

        assertEquals(2, registry.countSubscribers(PUBLIC_TOPIC));
    }

    @Test
    void mensagemSemDestinoOuSessaoEIgnorada() {
        registry.onSubscribe(subscribe(null, "sub-0", PUBLIC_TOPIC));
        registry.onSubscribe(subscribe("s1", "sub-0", null));
        registry.onUnsubscribe(unsubscribe("sessao-desconhecida", "sub-0"));

        assertFalse(registry.hasSubscribers(PUBLIC_TOPIC));
    }

    private SessionSubscribeEvent subscribe(String sessionId, String subscriptionId, String destination) {
        return new SessionSubscribeEvent(this, message(StompCommand.SUBSCRIBE, sessionId, subscriptionId, destination));
    }

    private SessionUnsubscribeEvent unsubscribe(String sessionId, String subscriptionId) {
        return new SessionUnsubscribeEvent(this, message(StompCommand.UNSUBSCRIBE, sessionId, subscriptionId, null));
    }

    private SessionDisconnectEvent disconnect(String sessionId) {
        return new SessionDisconnectEvent(this,
                message(StompCommand.DISCONNECT, sessionId, null, null), sessionId, CloseStatus.NORMAL);
    }

    private Message<byte[]> message(StompCommand command, String sessionId, String subscriptionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (subscriptionId != null) {
            accessor.setSubscriptionId(subscriptionId);
        }
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }
}
