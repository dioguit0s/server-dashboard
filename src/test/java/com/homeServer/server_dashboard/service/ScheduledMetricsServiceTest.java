package com.homeServer.server_dashboard.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * O ciclo periodico so' pode coletar o que algum topico com assinante consome — e' disso que vem a
 * economia de CPU (e de temperatura) no servidor.
 */
@ExtendWith(MockitoExtension.class)
class ScheduledMetricsServiceTest {

    @Mock
    private MonitorService monitorService;

    @Mock
    private MonitoredServicesService monitoredServicesService;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private TopicSubscriptionRegistry topicSubscriptionRegistry;

    @InjectMocks
    private ScheduledMetricsService scheduledMetricsService;

    @Test
    void semAssinantesNaoColetaNemPublica() {
        when(topicSubscriptionRegistry.hasSubscribers(any())).thenReturn(false);

        scheduledMetricsService.sendMetrics();

        verifyNoInteractions(monitorService);
        verifyNoInteractions(monitoredServicesService);
        verifyNoInteractions(messagingTemplate);
    }

    @Test
    void comAssinanteApenasNoPublicoNaoVarreProcessosNemServicos() {
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledMetricsService.PUBLIC_TOPIC)).thenReturn(true);
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledMetricsService.ADMIN_TOPIC)).thenReturn(false);
        stubPublicMetrics();

        scheduledMetricsService.sendMetrics();

        verify(messagingTemplate).convertAndSend(eq(ScheduledMetricsService.PUBLIC_TOPIC), any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(eq(ScheduledMetricsService.ADMIN_TOPIC), any(Object.class));
        verify(monitorService, never()).getTopProcesses(anyInt());
        verify(monitorService, never()).isServiceUp(anyInt());
        verifyNoInteractions(monitoredServicesService);
    }

    @Test
    void comAssinanteNoAdminVarreProcessosUmaUnicaVez() {
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledMetricsService.PUBLIC_TOPIC)).thenReturn(true);
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledMetricsService.ADMIN_TOPIC)).thenReturn(true);
        stubPublicMetrics();
        when(monitoredServicesService.getAll()).thenReturn(List.of());
        when(monitorService.getTopProcesses(anyInt()))
                .thenReturn(new MonitorService.TopProcesses(List.of(), List.of()));

        scheduledMetricsService.sendMetrics();

        // Uma varredura de /proc por ciclo, nao duas: as duas ordenacoes saem da mesma coleta.
        verify(monitorService, times(1)).getTopProcesses(anyInt());
        verify(messagingTemplate).convertAndSend(eq(ScheduledMetricsService.PUBLIC_TOPIC), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq(ScheduledMetricsService.ADMIN_TOPIC), any(Object.class));
    }

    @Test
    void assinanteApenasNoAdminNaoPublicaNoPublico() {
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledMetricsService.PUBLIC_TOPIC)).thenReturn(false);
        when(topicSubscriptionRegistry.hasSubscribers(ScheduledMetricsService.ADMIN_TOPIC)).thenReturn(true);
        when(monitoredServicesService.getAll()).thenReturn(List.of());
        when(monitorService.getTopProcesses(anyInt()))
                .thenReturn(new MonitorService.TopProcesses(List.of(), List.of()));

        scheduledMetricsService.sendMetrics();

        verify(messagingTemplate, never()).convertAndSend(eq(ScheduledMetricsService.PUBLIC_TOPIC), any(Object.class));
        verify(messagingTemplate).convertAndSend(eq(ScheduledMetricsService.ADMIN_TOPIC), any(Object.class));
    }

    private void stubPublicMetrics() {
        when(monitorService.getAdvancedDiskMetrics()).thenReturn(new MonitorService.DiskMetrics(
                new MonitorService.DiskInfo("100.00 GB", "50.00 GB", "50.00 GB", 50d),
                List.of(), List.of(), new MonitorService.DiskIoInfo("0 B/s", "0 B/s")));
        when(monitorService.getNetworkMetrics()).thenReturn(new MonitorService.NetworkInfo("0 B/s", "0 B/s"));
        when(monitorService.getSystemUptime()).thenReturn("0 dias, 00:00:00");
        when(monitorService.formatMemory(anyLong())).thenReturn("0.00 GB");
    }
}
