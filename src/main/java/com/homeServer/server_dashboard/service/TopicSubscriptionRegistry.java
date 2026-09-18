package com.homeServer.server_dashboard.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Quem esta inscrito em cada topico STOMP.
 *
 * <p>Existe para que as coletas periodicas so' rodem quando ha alguem para receber o resultado.
 * Antes, varrer todos os processos, abrir socket em cada servico monitorado e chamar o CLI do
 * Docker acontecia o tempo todo, mesmo sem nenhuma aba aberta — num servidor pequeno isso e' calor
 * e CPU gastos para publicar mensagem que ninguem le.
 *
 * <p>As inscricoes de uma sessao so' saem do mapa quando ela desconecta; um UNSUBSCRIBE remove
 * apenas a propria inscricao. Nao remover o mapa vazio da sessao evita corrida com um SUBSCRIBE
 * simultaneo da mesma sessao.
 */
@Component
public class TopicSubscriptionRegistry {

    private static final Logger log = LoggerFactory.getLogger(TopicSubscriptionRegistry.class);

    /** Sessao STOMP -> (id da inscricao -> destino assinado). */
    private final Map<String, Map<String, String>> destinationsBySession = new ConcurrentHashMap<>();

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        String destination = accessor.getDestination();
        if (sessionId == null || subscriptionId == null || destination == null) {
            return;
        }
        destinationsBySession
                .computeIfAbsent(sessionId, ignored -> new ConcurrentHashMap<>())
                .put(subscriptionId, destination);
        log.debug("[ServerDash] Inscricao registrada: session={} dest={}", sessionId, destination);
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        if (sessionId == null || subscriptionId == null) {
            return;
        }
        Map<String, String> subscriptions = destinationsBySession.get(sessionId);
        if (subscriptions != null) {
            subscriptions.remove(subscriptionId);
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        destinationsBySession.remove(event.getSessionId());
    }

    /** {@code true} se ao menos uma sessao esta inscrita no destino. */
    public boolean hasSubscribers(String destination) {
        for (Map<String, String> subscriptions : destinationsBySession.values()) {
            if (subscriptions.containsValue(destination)) {
                return true;
            }
        }
        return false;
    }

    /** Quantas inscricoes existem no destino (uma sessao pode ter mais de uma). */
    public int countSubscribers(String destination) {
        int total = 0;
        for (Map<String, String> subscriptions : destinationsBySession.values()) {
            for (String subscribedDestination : subscriptions.values()) {
                if (subscribedDestination.equals(destination)) {
                    total++;
                }
            }
        }
        return total;
    }
}
