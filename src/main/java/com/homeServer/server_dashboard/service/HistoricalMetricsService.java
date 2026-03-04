package com.homeServer.server_dashboard.service;

import com.homeServer.server_dashboard.model.HistoricalMetric;
import com.homeServer.server_dashboard.repository.HistoricalMetricRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class HistoricalMetricsService {
    private static final Logger log = LoggerFactory.getLogger(HistoricalMetricsService.class);
    @Autowired
    private HistoricalMetricRepository historicalMetricRepository;

    @Autowired
    private MonitorService monitorService;

    @Scheduled(fixedRate = 60000) //executa a cada 1 minuto (60000 milisegundos)
    public void saveCurrentMetrics(){
        Instant now = Instant.now(); // UTC
        Double cpuTemp = monitorService.getCpuTemperature();
        Double cpuUsage = monitorService.getCpuUsage();
        Double ramUsage = monitorService.getMemoryUsagePercentage();

        log.debug("[Historico] Salvando metrica agendada: recordedAt={} (UTC), cpuTemp={}, cpuUsage={}%, ramUsage={}%", now, cpuTemp, cpuUsage, ramUsage);

        HistoricalMetric historicalMetric = new HistoricalMetric();
        historicalMetric.setRecordedAt(now);
        historicalMetric.setCpuTemperature(cpuTemp);
        historicalMetric.setCpuUsagePercentage(cpuUsage);
        historicalMetric.setRamUsagePercentage(ramUsage);

        historicalMetricRepository.save(historicalMetric);
        log.info("[Historico] Metrica salva com sucesso (id aproximado/inserido a cada 1 min)");
    }

    public List<HistoricalMetric> getMetricsSince(int hoursToRetrieve){
        Instant timeThreshold = Instant.now().minusSeconds(hoursToRetrieve * 3600L); // UTC: "X horas atras"
        log.info("[Historico] Buscando metricas: hoursToRetrieve={}, timeThreshold={} (UTC)", hoursToRetrieve, timeThreshold);

        List<HistoricalMetric> list = historicalMetricRepository.findByRecordedAtAfterOrderByRecordedAtAsc(timeThreshold);
        log.info("[Historico] Retornando {} registros (primeiro: {}, ultimo: {})",
                list.size(),
                list.isEmpty() ? "N/A" : list.get(0).getRecordedAt(),
                list.isEmpty() ? "N/A" : list.get(list.size() - 1).getRecordedAt());
        return list;
    }
}
