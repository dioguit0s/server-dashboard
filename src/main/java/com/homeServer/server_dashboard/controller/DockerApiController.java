package com.homeServer.server_dashboard.controller;

import com.homeServer.server_dashboard.service.DockerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/docker")
public class DockerApiController {

    @Autowired
    private DockerService dockerService;

    @PostMapping("/{actionName}/{containerIdentifier}")
    public ResponseEntity<?> handleContainerAction(@PathVariable String actionName, @PathVariable String containerIdentifier) {
        if (!actionName.equals("start") && !actionName.equals("stop") && !actionName.equals("restart")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ação inválida solicitada"));
        }

        boolean actionResult = dockerService.executeActionOnContainer(actionName, containerIdentifier);
        if (actionResult) {
            return ResponseEntity.ok(Map.of("message", "Ação executada com sucesso"));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("error", "Falha ao executar a ação no container"));
        }
    }
}