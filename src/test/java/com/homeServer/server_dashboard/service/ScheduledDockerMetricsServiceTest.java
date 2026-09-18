package com.homeServer.server_dashboard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cada ciclo do broadcast de containers executa o CLI do Docker duas vezes; sem ninguem na pagina
 * de containers, ele nao deve rodar.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledDockerMetricsServiceTest {

    @Mock
    private DockerService dockerService;

    @Mock
    private SimpMessagingTemplate simpleMessagingTemplate;

    @Mock
    private TopicSubscriptionRegistry topicSubscriptionRegistry;

    @InjectMocks
    private ScheduledDockerMetricsService scheduledDockerMetricsService;

    @Test
    void semAssinantesNaoChamaOCliDoDocker() {
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledDockerMetricsService.DOCKER_TOPIC)).thenReturn(false);

        scheduledDockerMetricsService.broadcastDockerMetrics();

        verifyNoInteractions(dockerService);
        verifyNoInteractions(simpleMessagingTemplate);
    }

    @Test
    void comAssinantePublicaOEstadoDosContainers() {
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledDockerMetricsService.DOCKER_TOPIC)).thenReturn(true);
        when(dockerService.retrieveAllContainers()).thenReturn(List.of());

        scheduledDockerMetricsService.broadcastDockerMetrics();

        verify(dockerService).retrieveAllContainers();
        verify(simpleMessagingTemplate).convertAndSend(eq(ScheduledDockerMetricsService.DOCKER_TOPIC), any(Object.class));
    }
}
