package com.homeServer.server_dashboard.controller;

import com.homeServer.server_dashboard.service.DockerService;
import com.homeServer.server_dashboard.service.DockerService.DockerContainerInformation;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/docker")
public class DockerApiController {

    private static final Pattern CONTAINER_ID_PATTERN =
            Pattern.compile("^([a-f0-9]{12,64}|[a-zA-Z0-9][a-zA-Z0-9_.-]{0,127})$");

    private final DockerService dockerService;

    public DockerApiController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @GetMapping("/containers")
    public ResponseEntity<Map<String, List<DockerContainerInformation>>> listContainers() {
        return ResponseEntity.ok(Map.of("containers", dockerService.retrieveAllContainers()));
    }

    /**
     * Acao de escrita sobre o servidor: parar ou reiniciar um container derruba servico. Fica
     * restrita a ADMIN aqui tambem, e nao so' na matriz de rotas, para que uma mudanca de mapeamento
     * no futuro nao abra o endpoint sem que ninguem perceba.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/{actionName}/{containerIdentifier}")
    public ResponseEntity<?> handleContainerAction(@PathVariable String actionName, @PathVariable String containerIdentifier) {
        if (!actionName.equals("start") && !actionName.equals("stop") && !actionName.equals("restart")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Ação inválida solicitada"));
        }

        if (!isValidContainerIdentifier(containerIdentifier)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Identificador de container inválido"));
        }

        boolean actionResult = dockerService.executeActionOnContainer(actionName, containerIdentifier);
        if (actionResult) {
            return ResponseEntity.ok(Map.of("message", "Ação executada com sucesso"));
        } else {
            return ResponseEntity.internalServerError().body(Map.of("error", "Falha ao executar a ação no container"));
        }
    }

    private boolean isValidContainerIdentifier(String containerIdentifier) {
        return containerIdentifier != null && CONTAINER_ID_PATTERN.matcher(containerIdentifier).matches();
    }
}
