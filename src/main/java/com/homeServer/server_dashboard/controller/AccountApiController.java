package com.homeServer.server_dashboard.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.homeServer.server_dashboard.service.DashboardUserService;
import com.homeServer.server_dashboard.service.TwoFactorService;
import com.homeServer.server_dashboard.service.TwoFactorService.EnrollmentChallenge;

/**
 * Operacoes que o usuario logado faz sobre a propria conta: trocar a senha e gerenciar o 2FA.
 *
 * <p>Tudo aqui age sobre o principal autenticado — nenhum endpoint aceita um nome de usuario como
 * parametro, para que nao exista caminho de um usuario mexer na conta de outro.
 */
@RestController
@RequestMapping("/api/account")
public class AccountApiController {

    private final DashboardUserService userService;
    private final TwoFactorService twoFactorService;

    public AccountApiController(DashboardUserService userService, TwoFactorService twoFactorService) {
        this.userService = userService;
        this.twoFactorService = twoFactorService;
    }

    @PutMapping("/password")
    public ResponseEntity<?> changePassword(@RequestBody(required = false) Map<String, Object> body,
                                            @AuthenticationPrincipal UserDetails user) {
        return handle(() -> {
            userService.changeOwnPassword(user.getUsername(),
                    stringValue(body, "currentPassword"),
                    stringValue(body, "newPassword"));
            return ResponseEntity.ok(Map.of("message", "Senha alterada"));
        });
    }

    /**
     * Passo 1 do enrollment: gera o segredo e devolve o QR. O 2FA so' liga no passo 2.
     */
    @PostMapping("/2fa/enrollment")
    public ResponseEntity<?> beginEnrollment(@AuthenticationPrincipal UserDetails user) {
        return handle(() -> {
            EnrollmentChallenge challenge = twoFactorService.beginEnrollment(user.getUsername());
            return ResponseEntity.ok(Map.of(
                    "secret", challenge.secret(),
                    "qrCode", challenge.qrCodeDataUri()));
        });
    }

    /**
     * Passo 2: confirma com um codigo do autenticador. Os codigos de recuperacao voltam aqui e
     * nunca mais — a partir daqui so' existem em hash.
     */
    @PostMapping("/2fa")
    public ResponseEntity<?> confirmEnrollment(@RequestBody(required = false) Map<String, Object> body,
                                               @AuthenticationPrincipal UserDetails user) {
        return handle(() -> {
            List<String> recoveryCodes = twoFactorService.confirmEnrollment(user.getUsername(), stringValue(body, "code"));
            return ResponseEntity.ok(Map.of("recoveryCodes", recoveryCodes));
        });
    }

    @DeleteMapping("/2fa")
    public ResponseEntity<?> disableTwoFactor(@RequestBody(required = false) Map<String, Object> body,
                                              @AuthenticationPrincipal UserDetails user) {
        return handle(() -> {
            twoFactorService.disableOwn(user.getUsername(), stringValue(body, "currentPassword"));
            return ResponseEntity.ok(Map.of("message", "2FA desativado"));
        });
    }

    @PostMapping("/2fa/recovery-codes")
    public ResponseEntity<?> regenerateRecoveryCodes(@RequestBody(required = false) Map<String, Object> body,
                                                     @AuthenticationPrincipal UserDetails user) {
        return handle(() -> {
            List<String> recoveryCodes = twoFactorService.regenerateRecoveryCodes(
                    user.getUsername(), stringValue(body, "currentPassword"));
            return ResponseEntity.ok(Map.of("recoveryCodes", recoveryCodes));
        });
    }

    private static ResponseEntity<?> handle(ResponseSupplier action) {
        try {
            return action.get();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static String stringValue(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        return value == null ? "" : value.toString();
    }

    @FunctionalInterface
    private interface ResponseSupplier {
        ResponseEntity<?> get();
    }
}
