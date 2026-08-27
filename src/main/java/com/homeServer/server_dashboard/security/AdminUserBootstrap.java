package com.homeServer.server_dashboard.security;

import java.util.EnumSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.repository.DashboardUserRepository;
import com.homeServer.server_dashboard.service.DashboardUserService;

/**
 * Provisiona o admin inicial a partir de {@code DASHBOARD_ADMIN_USERNAME} e da senha configurada,
 * mantendo o contrato de deploy que existia antes de os usuarios irem para o banco.
 *
 * <p>Cria apenas se o usuario ainda nao existir. Depois do primeiro boot o banco e' a verdade: uma
 * senha trocada pela tela de conta nao e' sobrescrita por um restart, e um admin removido de
 * proposito nao volta sozinho — o que voltaria a ser um usuario fixo em memoria com outro nome.
 */
@Component
public class AdminUserBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserBootstrap.class);

    private final DashboardUserRepository userRepository;
    private final AdminCredentials adminCredentials;

    public AdminUserBootstrap(DashboardUserRepository userRepository, AdminCredentials adminCredentials) {
        this.userRepository = userRepository;
        this.adminCredentials = adminCredentials;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = DashboardUserService.normalizeUsername(adminCredentials.username());

        if (userRepository.existsByUsernameIgnoreCase(username)) {
            log.info("[ServerDash] admin '{}' ja existe no banco — credenciais do ambiente ignoradas neste boot",
                    username);
            return;
        }

        DashboardUser admin = new DashboardUser(
                username,
                adminCredentials.encodedPassword(),
                EnumSet.of(DashboardRole.ADMIN));
        userRepository.save(admin);
        log.info("[ServerDash] admin inicial '{}' criado a partir das variaveis de ambiente", username);
    }
}
