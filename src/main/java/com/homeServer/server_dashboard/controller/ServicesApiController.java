package com.homeServer.server_dashboard.controller;

import com.homeServer.server_dashboard.service.MonitoredServicesService;
import com.homeServer.server_dashboard.service.MonitoredServicesService.MonitoredService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/services")
public class ServicesApiController {

    @Autowired
    private MonitoredServicesService monitoredServicesService;

    @GetMapping
    public List<MonitoredService> list() {
        return monitoredServicesService.getAll();
    }

    @PostMapping
    public ResponseEntity<?> add(@RequestBody Map<String, Object> body) {
        String name = body != null && body.get("name") != null ? body.get("name").toString().trim() : "";
        Object portObj = body != null ? body.get("port") : null;
        if (name.isEmpty() || portObj == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Nome e porta são obrigatórios"));
        }
        int port;
        try {
            port = portObj instanceof Number n ? n.intValue() : Integer.parseInt(portObj.toString());
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Porta deve ser um número válido (1-65535)"));
        }
        try {
            MonitoredService added = monitoredServicesService.add(name, port);
            return ResponseEntity.ok(added);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{port}")
    public ResponseEntity<?> remove(@PathVariable int port) {
        if (port < 1 || port > 65535) {
            return ResponseEntity.badRequest().body(Map.of("error", "Porta inválida"));
        }
        boolean removed = monitoredServicesService.remove(port);
        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
