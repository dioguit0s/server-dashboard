package com.homeServer.server_dashboard.controller;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.service.DashboardUserService;

/**
 * Gestao de usuarios. Toda a classe e' restrita a ADMIN — pela matriz de rotas ({@code /api/admin/**})
 * e pela anotacao aqui, que continua valendo se o mapeamento mudar.
 */
@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminApiController {

    private final DashboardUserService userService;

    public UserAdminApiController(DashboardUserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return userService.listAll().stream().map(UserAdminApiController::toView).toList();
    }

    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) Map<String, Object> body) {
        return handle(() -> {
            DashboardUser created = userService.create(
                    stringValue(body, "username"),
                    stringValue(body, "password"),
                    rolesValue(body));
            return ResponseEntity.ok(toView(created));
        });
    }

    @PutMapping("/{identifier}/roles")
    public ResponseEntity<?> updateRoles(@PathVariable Long identifier,
                                         @RequestBody(required = false) Map<String, Object> body,
                                         @AuthenticationPrincipal UserDetails actingUser) {
        return handle(() -> ResponseEntity.ok(
                toView(userService.updateRoles(identifier, rolesValue(body), actingUser.getUsername()))));
    }

    @PutMapping("/{identifier}/enabled")
    public ResponseEntity<?> updateEnabled(@PathVariable Long identifier,
                                           @RequestBody(required = false) Map<String, Object> body,
                                           @AuthenticationPrincipal UserDetails actingUser) {
        return handle(() -> ResponseEntity.ok(
                toView(userService.setEnabled(identifier, booleanValue(body), actingUser.getUsername()))));
    }

    @PutMapping("/{identifier}/password")
    public ResponseEntity<?> resetPassword(@PathVariable Long identifier,
                                           @RequestBody(required = false) Map<String, Object> body,
                                           @AuthenticationPrincipal UserDetails actingUser) {
        return handle(() -> {
            userService.resetPassword(identifier, stringValue(body, "password"), actingUser.getUsername());
            return ResponseEntity.ok(Map.of("message", "Senha redefinida"));
        });
    }

    /**
     * Saida de emergencia para quem perdeu o autenticador — o proximo login volta a exigir so' a
     * senha, e o usuario refaz o enrollment.
     */
    @DeleteMapping("/{identifier}/two-factor")
    public ResponseEntity<?> disableTwoFactor(@PathVariable Long identifier,
                                              @AuthenticationPrincipal UserDetails actingUser) {
        return handle(() -> ResponseEntity.ok(
                toView(userService.disableTwoFactor(identifier, actingUser.getUsername()))));
    }

    @DeleteMapping("/{identifier}")
    public ResponseEntity<?> delete(@PathVariable Long identifier,
                                    @AuthenticationPrincipal UserDetails actingUser) {
        return handle(() -> {
            userService.delete(identifier, actingUser.getUsername());
            return ResponseEntity.noContent().build();
        });
    }

    /**
     * Traduz as recusas de regra de negocio em 400 com a mensagem pronta para a tela, no mesmo
     * formato {@code {"error": ...}} que o resto da API ja usa.
     */
    private static ResponseEntity<?> handle(ResponseSupplier action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Projecao segura do usuario: nunca inclui hash de senha, segredo TOTP ou codigos de recuperacao.
     */
    private static Map<String, Object> toView(DashboardUser user) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("identifier", user.getIdentifier());
        view.put("username", user.getUsername());
        view.put("roles", user.getRoles().stream().map(Enum::name).sorted().toList());
        view.put("enabled", user.isEnabled());
        view.put("totpEnabled", user.isTotpEnabled());
        view.put("unusedRecoveryCodes", user.unusedRecoveryCodeCount());
        view.put("createdAt", user.getCreatedAt() == null ? Instant.EPOCH : user.getCreatedAt());
        return view;
    }

    private static String stringValue(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : value.toString();
    }

    private static boolean booleanValue(Map<String, Object> body) {
        Object value = body == null ? null : body.get("enabled");
        return value instanceof Boolean flag ? flag : Boolean.parseBoolean(String.valueOf(value));
    }

    private static Set<DashboardRole> rolesValue(Map<String, Object> body) {
        Object value = body == null ? null : body.get("roles");
        if (!(value instanceof List<?> rawRoles) || rawRoles.isEmpty()) {
            throw new IllegalArgumentException("Informe pelo menos um papel (VIEWER ou ADMIN)");
        }
        Set<DashboardRole> roles = EnumSet.noneOf(DashboardRole.class);
        for (Object rawRole : rawRoles) {
            try {
                roles.add(DashboardRole.valueOf(String.valueOf(rawRole).trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Papel desconhecido: " + rawRole);
            }
        }
        return roles;
    }

    @FunctionalInterface
    private interface ResponseSupplier {
        ResponseEntity<?> get();
    }
}
