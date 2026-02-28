package com.homeServer.server_dashboard.controller;

import com.homeServer.server_dashboard.model.HistoricalMetric;
import com.homeServer.server_dashboard.repository.HistoricalMetricRepository;
import com.homeServer.server_dashboard.service.HistoricalMetricsService;
import com.homeServer.server_dashboard.service.ScheduledMetricsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsApiController {

    private static final Logger log = LoggerFactory.getLogger(MetricsApiController.class);

    @Autowired
    private ScheduledMetricsService scheduledMetricsService;

    @Autowired
    private HistoricalMetricsService historicalMetricsService;

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> getPublicMetrics() {
        return ResponseEntity.ok(scheduledMetricsService.getPublicMetrics());
    }

    @GetMapping("/history")
    public ResponseEntity<List<HistoricalMetric>> getHistorycalMetrics(@RequestParam(defaultValue = "1") int hoursToRetrieve) {
        log.info("[API /history] Requisicao recebida: hoursToRetrieve={}", hoursToRetrieve);
        List<HistoricalMetric> list = historicalMetricsService.getMetricsSince(hoursToRetrieve);
        log.info("[API /history] Respondendo com {} registros", list.size());
        return ResponseEntity.ok(list);
    }
}
