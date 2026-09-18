package com.homeServer.server_dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduledDockerMetricsService {

    static final String DOCKER_TOPIC = "/topic/docker";

    @Autowired
    private DockerService dockerService;

    @Autowired
    private SimpMessagingTemplate simpleMessagingTemplate;

    @Autowired
    private TopicSubscriptionRegistry topicSubscriptionRegistry;

    /**
     * Publica o estado dos containers enquanto alguém estiver na página de containers.
     *
     * <p>Cada ciclo custa dois processos do CLI do Docker ({@code docker ps -a} e
     * {@code docker stats --no-stream}, ~42 MB de binário cada), e o {@code stats} ainda amostra
     * num intervalo próprio antes de responder. Rodar isso a cada 3s o dia inteiro, sem ninguém
     * olhando, era o item mais caro do dashboard. A página busca o estado inicial por REST
     * ({@code /api/docker/containers}), então nada se perde enquanto o broadcast está parado.
     */
    @Scheduled(fixedDelayString = "${dashboard.docker.broadcast-interval-ms:10000}")
    public void broadcastDockerMetrics() {
        if (!topicSubscriptionRegistry.hasSubscribers(DOCKER_TOPIC)) {
            return;
        }
        List<DockerService.DockerContainerInformation> containerList = dockerService.retrieveAllContainers();
        simpleMessagingTemplate.convertAndSend(DOCKER_TOPIC, containerList);
    }
}
