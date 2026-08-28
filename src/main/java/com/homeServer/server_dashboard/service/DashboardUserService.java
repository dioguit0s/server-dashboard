package com.homeServer.server_dashboard.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.repository.DashboardUserRepository;
import com.homeServer.server_dashboard.repository.PersistentLoginTokenRepository;

/**
 * Regras de negocio da gestao de usuarios.
 *
 * <p>A parte mais importante daqui nao e' o CRUD, e sim as travas que impedem o dashboard de ficar
 * sem administrador: ninguem remove, desabilita ou rebaixa o ultimo {@code ADMIN} ativo, e ninguem
 * se remove ou se desabilita a si mesmo (o caminho mais facil de se trancar para fora).
 *
 * <p>Erros de uso viram {@link IllegalArgumentException} com mensagem para o usuario final; os
 * controllers as traduzem em 400/409.
 */
@Service
public class DashboardUserService {

    private static final Logger log = LoggerFactory.getLogger(DashboardUserService.class);

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{3,64}$");
    private static final int MINIMUM_PASSWORD_LENGTH = 8;
    private static final int MAXIMUM_PASSWORD_LENGTH = 200;

    private final DashboardUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PersistentLoginTokenRepository persistentLoginTokenRepository;

    public DashboardUserService(DashboardUserRepository userRepository, PasswordEncoder passwordEncoder,
                                PersistentLoginTokenRepository persistentLoginTokenRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.persistentLoginTokenRepository = persistentLoginTokenRepository;
    }

    @Transactional(readOnly = true)
    public List<DashboardUser> listAll() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    @Transactional(readOnly = true)
    public DashboardUser requireByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(normalizeUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));
    }

    @Transactional(readOnly = true)
    public DashboardUser requireById(Long identifier) {
        return userRepository.findById(identifier)
                .orElseThrow(() -> new IllegalArgumentException("Usuario nao encontrado"));
    }

    @Transactional
    public DashboardUser create(String username, String rawPassword, Set<DashboardRole> roles) {
        String normalized = normalizeUsername(username);
        validateUsername(normalized);
        validatePassword(rawPassword);
        validateRoles(roles);

        if (userRepository.existsByUsernameIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Ja existe um usuario com esse nome");
        }

        DashboardUser user = new DashboardUser(normalized, passwordEncoder.encode(rawPassword), roles);
        DashboardUser saved = userRepository.save(user);
        log.info("[ServerDash] usuario '{}' criado com os papeis {}", normalized, roles);
        return saved;
    }

    @Transactional
    public DashboardUser updateRoles(Long identifier, Set<DashboardRole> roles, String actingUsername) {
        validateRoles(roles);
        DashboardUser user = requireById(identifier);

        if (user.hasRole(DashboardRole.ADMIN) && !roles.contains(DashboardRole.ADMIN)) {
            ensureNotTheLastActiveAdmin(user, "rebaixar");
        }

        user.setRoles(roles);
        log.info("[ServerDash] papeis do usuario '{}' alterados para {} por '{}'",
                user.getUsername(), roles, actingUsername);
        return userRepository.save(user);
    }

    @Transactional
    public DashboardUser setEnabled(Long identifier, boolean enabled, String actingUsername) {
        DashboardUser user = requireById(identifier);
        ensureNotSelf(user, actingUsername, "Nao e' possivel desabilitar a propria conta");

        if (!enabled) {
            ensureNotTheLastActiveAdmin(user, "desabilitar");
            persistentLoginTokenRepository.deleteByUsername(user.getUsername());
        }

        user.setEnabled(enabled);
        log.info("[ServerDash] usuario '{}' {} por '{}'",
                user.getUsername(), enabled ? "habilitado" : "desabilitado", actingUsername);
        return userRepository.save(user);
    }

    /**
     * Redefinicao feita por um admin: nao exige a senha atual, e deixa o 2FA do usuario intacto —
     * trocar a senha de alguem nao deveria remover um fator de autenticacao. Para destravar quem
     * perdeu o autenticador existe {@link #disableTwoFactor}.
     */
    @Transactional
    public DashboardUser resetPassword(Long identifier, String newRawPassword, String actingUsername) {
        validatePassword(newRawPassword);
        DashboardUser user = requireById(identifier);
        user.setPasswordHash(passwordEncoder.encode(newRawPassword));
        persistentLoginTokenRepository.deleteByUsername(user.getUsername());
        log.info("[ServerDash] senha do usuario '{}' redefinida por '{}'", user.getUsername(), actingUsername);
        return userRepository.save(user);
    }

    /**
     * Troca da propria senha: exige a senha atual, para que uma sessao sequestrada nao consiga
     * trocar a credencial e tomar a conta em definitivo.
     */
    @Transactional
    public void changeOwnPassword(String username, String currentPassword, String newPassword) {
        DashboardUser user = requireByUsername(username);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Senha atual incorreta");
        }
        validatePassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        persistentLoginTokenRepository.deleteByUsername(user.getUsername());
        userRepository.save(user);
        log.info("[ServerDash] usuario '{}' trocou a propria senha", user.getUsername());
    }

    /**
     * Destrava quem perdeu o autenticador. Como remove um fator de autenticacao, so' um admin
     * chega aqui, e o evento fica registrado em WARN para auditoria.
     */
    @Transactional
    public DashboardUser disableTwoFactor(Long identifier, String actingUsername) {
        DashboardUser user = requireById(identifier);
        user.clearTwoFactor();
        log.warn("[ServerDash] 2FA do usuario '{}' desligado por '{}'", user.getUsername(), actingUsername);
        return userRepository.save(user);
    }

    @Transactional
    public void delete(Long identifier, String actingUsername) {
        DashboardUser user = requireById(identifier);
        ensureNotSelf(user, actingUsername, "Nao e' possivel remover a propria conta");
        ensureNotTheLastActiveAdmin(user, "remover");
        persistentLoginTokenRepository.deleteByUsername(user.getUsername());
        userRepository.delete(user);
        log.info("[ServerDash] usuario '{}' removido por '{}'", user.getUsername(), actingUsername);
    }

    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private void ensureNotSelf(DashboardUser user, String actingUsername, String message) {
        if (user.getUsername().equalsIgnoreCase(normalizeUsername(actingUsername))) {
            throw new IllegalArgumentException(message);
        }
    }

    private void ensureNotTheLastActiveAdmin(DashboardUser user, String action) {
        if (!user.hasRole(DashboardRole.ADMIN) || !user.isEnabled()) {
            return;
        }
        if (userRepository.countByRolesContainingAndEnabledTrue(DashboardRole.ADMIN) <= 1) {
            throw new IllegalArgumentException(
                    "Nao e' possivel " + action + " o unico administrador ativo — crie outro admin antes");
        }
    }

    private static void validateUsername(String username) {
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "Usuario deve ter de 3 a 64 caracteres, usando apenas letras, numeros, ponto, hifen ou underscore");
        }
    }

    private static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("A senha deve ter pelo menos " + MINIMUM_PASSWORD_LENGTH + " caracteres");
        }
        if (rawPassword.length() > MAXIMUM_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("A senha deve ter no maximo " + MAXIMUM_PASSWORD_LENGTH + " caracteres");
        }
    }

    private static void validateRoles(Set<DashboardRole> roles) {
        if (roles == null || roles.isEmpty()) {
            throw new IllegalArgumentException("Informe pelo menos um papel (VIEWER ou ADMIN)");
        }
        if (!EnumSet.allOf(DashboardRole.class).containsAll(roles)) {
            throw new IllegalArgumentException("Papel desconhecido");
        }
    }
}
