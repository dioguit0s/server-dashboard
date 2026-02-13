package com.homeServer.server_dashboard.controller;

import com.homeServer.server_dashboard.service.ScheduledMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/metrics")
public class MetricsApiController {

    @Autowired
    private ScheduledMetricsService scheduledMetricsService;

    @GetMapping("/public")
    public ResponseEntity<Map<String, Object>> getPublicMetrics() {
        return ResponseEntity.ok(scheduledMetricsService.getPublicMetrics());
    }
}
