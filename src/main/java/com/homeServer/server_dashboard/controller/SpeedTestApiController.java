package com.homeServer.server_dashboard.controller;

import com.homeServer.server_dashboard.service.SpeedTestService;
import com.homeServer.server_dashboard.service.SpeedTestService.SpeedTestStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/speedtest")
public class SpeedTestApiController {

    private final SpeedTestService speedTestService;

    public SpeedTestApiController(SpeedTestService speedTestService) {
        this.speedTestService = speedTestService;
    }

    @GetMapping
    public ResponseEntity<SpeedTestStatus> getStatus() {
        return ResponseEntity.ok(speedTestService.getStatus());
    }

    /**
     * Um teste consome centenas de MB e satura o link por alguns segundos — e' acao sobre a rede,
     * nao leitura, entao fica com ADMIN (aqui e na matriz de rotas do SecurityConfig).
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/run")
    public ResponseEntity<SpeedTestStatus> run() {
        boolean started = speedTestService.startAsync("manual");
        return ResponseEntity.status(started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT)
                .body(speedTestService.getStatus());
    }
}
