package com.homeServer.server_dashboard.controller;

import com.homeServer.server_dashboard.service.LogViewerService;
import com.homeServer.server_dashboard.service.LogViewerService.LogFetchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogsApiController {

    private final LogViewerService logViewerService;

    public LogsApiController(LogViewerService logViewerService) {
        this.logViewerService = logViewerService;
    }

    @GetMapping("/docker")
    public ResponseEntity<?> fetchDockerLogs(
            @RequestParam String container,
            @RequestParam(defaultValue = "100") int tail) {
        int boundedTail = logViewerService.clampTail(tail);
        LogFetchResult result = logViewerService.fetchDockerLogs(container, boundedTail);
        return toResponse(result, Map.of(
                "container", container.trim(),
                "tail", boundedTail
        ));
    }

    @GetMapping("/journal")
    public ResponseEntity<?> fetchJournalLogs(
            @RequestParam String unit,
            @RequestParam(defaultValue = "100") int tail) {
        int boundedTail = logViewerService.clampTail(tail);
        LogFetchResult result = logViewerService.fetchJournalLogs(unit, boundedTail);
        return toResponse(result, Map.of(
                "unit", unit.trim(),
                "tail", boundedTail
        ));
    }

    @GetMapping("/sources")
    public ResponseEntity<Map<String, Object>> listSources() {
        return ResponseEntity.ok(logViewerService.listSources());
    }

    private ResponseEntity<?> toResponse(LogFetchResult result, Map<String, Object> successFields) {
        if (!result.success()) {
            if (result.exitCode() == -1 && result.error() != null
                    && (result.error().contains("inválid") || result.error().contains("invalid"))) {
                return ResponseEntity.badRequest().body(Map.of("error", result.error()));
            }
            return ResponseEntity.status(503).body(Map.of("error",
                    result.error() != null ? result.error() : "Falha ao obter logs"));
        }
        Map<String, Object> body = new java.util.HashMap<>(successFields);
        body.put("logs", result.logs() != null ? result.logs() : "");
        return ResponseEntity.ok(body);
    }
}
