package com.homeServer.server_dashboard.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ScheduledDockerMetricsService {

    @Autowired
    private DockerService dockerService;

    @Autowired
    private SimpMessagingTemplate simpleMessagingTemplate;

    @Scheduled(fixedDelay = 3000)
    public void broadcastDockerMetrics() {
        List<DockerService.DockerContainerInformation> containerList = dockerService.retrieveAllContainers();
        simpleMessagingTemplate.convertAndSend("/topic/docker", containerList);
    }
}