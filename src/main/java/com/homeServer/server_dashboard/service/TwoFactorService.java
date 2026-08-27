package com.homeServer.server_dashboard.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.repository.DashboardUserRepository;
import com.homeServer.server_dashboard.security.totp.QrCodeRenderer;
import com.homeServer.server_dashboard.security.totp.TotpService;
import com.homeServer.server_dashboard.security.totp.TotpService.SecondFactorResult;

/**
 * Orquestra o 2FA sobre o banco: enrollment, confirmacao, desligamento e verificacao no login.
 *
 * <p>A criptografia em si mora em {@link TotpService}; aqui ficam as transacoes e as regras de
 * quando cada operacao e' permitida.
 */
@Service
public class TwoFactorService {

    private static final Logger log = LoggerFactory.getLogger(TwoFactorService.class);

    private final DashboardUserRepository userRepository;
    private final TotpService totpService;
    private final QrCodeRenderer qrCodeRenderer;
    private final PasswordEncoder passwordEncoder;

    public TwoFactorService(DashboardUserRepository userRepository, TotpService totpService,
                            QrCodeRenderer qrCodeRenderer, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.totpService = totpService;
        this.qrCodeRenderer = qrCodeRenderer;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Comeca (ou recomeca) o enrollment: gera um segredo novo e o guarda com {@code totpEnabled}
     * ainda em false. So' a confirmacao com um codigo valido liga o 2FA — assim ninguem fica
     * trancado para fora por ter fechado a pagina no meio do processo.
     */
    @Transactional
    public EnrollmentChallenge beginEnrollment(String username) {
        DashboardUser user = require(username);
        if (user.isTotpEnabled()) {
            throw new IllegalArgumentException("O 2FA ja esta ativo nesta conta");
        }

        String secret = totpService.generateSecret();
        user.setTotpSecret(secret);
        user.setLastUsedTimeStep(null);
        userRepository.save(user);

        String otpAuthUri = totpService.buildOtpAuthUri(user.getUsername(), secret);
        return new EnrollmentChallenge(secret, otpAuthUri, qrCodeRenderer.renderAsDataUri(otpAuthUri));
    }

    /**
     * Confirma o enrollment com um codigo do autenticador e devolve os codigos de recuperacao em
     * texto — a unica vez em que eles sao visiveis.
     */
    @Transactional
    public List<String> confirmEnrollment(String username, String submittedCode) {
        DashboardUser user = require(username);
        if (user.isTotpEnabled()) {
            throw new IllegalArgumentException("O 2FA ja esta ativo nesta conta");
        }
        if (user.getTotpSecret() == null) {
            throw new IllegalArgumentException("Nenhum enrollment em andamento — gere um QR code primeiro");
        }
        if (!totpService.isValidForSecret(user.getTotpSecret(), submittedCode)) {
            throw new IllegalArgumentException("Codigo invalido — confira o relogio do dispositivo e tente de novo");
        }

        user.setTotpEnabled(true);
        List<String> recoveryCodes = totpService.regenerateRecoveryCodes(user);
        userRepository.save(user);
        log.info("[ServerDash] 2FA ativado para o usuario '{}'", user.getUsername());
        return recoveryCodes;
    }

    /**
     * Desliga o proprio 2FA. Exige a senha atual: sem isso, uma sessao sequestrada removeria o
     * segundo fator, que e' justamente o que protege a conta nesse cenario.
     */
    @Transactional
    public void disableOwn(String username, String currentPassword) {
        DashboardUser user = require(username);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }
        user.clearTwoFactor();
        userRepository.save(user);
        log.warn("[ServerDash] 2FA desativado pelo proprio usuario '{}'", user.getUsername());
    }

    /**
     * Emite um lote novo de codigos de recuperacao, invalidando os anteriores. Exige a senha atual
     * pelo mesmo motivo de {@link #disableOwn}.
     */
    @Transactional
    public List<String> regenerateRecoveryCodes(String username, String currentPassword) {
        DashboardUser user = require(username);
        if (!user.isTotpEnabled()) {
            throw new IllegalArgumentException("Ative o 2FA antes de gerar codigos de recuperacao");
        }
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }
        List<String> recoveryCodes = totpService.regenerateRecoveryCodes(user);
        userRepository.save(user);
        log.info("[ServerDash] codigos de recuperacao regerados para o usuario '{}'", user.getUsername());
        return recoveryCodes;
    }

    /**
     * Segundo passo do login. Grava o resultado (anti-replay do TOTP ou consumo do codigo de
     * recuperacao) antes de responder, para que uma repeticao imediata nao passe.
     */
    @Transactional
    public SecondFactorResult verifySecondFactor(String username, String submittedCode) {
        DashboardUser user = userRepository.findByUsernameIgnoreCase(DashboardUserService.normalizeUsername(username))
                .orElse(null);
        if (user == null || !user.isEnabled()) {
            return SecondFactorResult.REJECTED;
        }

        SecondFactorResult result = totpService.verifySecondFactor(user, submittedCode);
        if (result.isAccepted()) {
            userRepository.save(user);
        }
        if (result == SecondFactorResult.RECOVERY_CODE) {
            log.warn("[ServerDash] login do usuario '{}' concluido com codigo de recuperacao — restam {}",
                    user.getUsername(), user.unusedRecoveryCodeCount());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long unusedRecoveryCodeCount(String username) {
        return require(username).unusedRecoveryCodeCount();
    }

    private DashboardUser require(String username) {
        return userRepository.findByUsernameIgnoreCase(DashboardUserService.normalizeUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));
    }

    /**
     * O que a tela de enrollment precisa mostrar: o QR, o segredo para digitacao manual e a URI
     * {@code otpauth://} por tras dele.
     */
    public record EnrollmentChallenge(String secret, String otpAuthUri, String qrCodeDataUri) {
    }
}
