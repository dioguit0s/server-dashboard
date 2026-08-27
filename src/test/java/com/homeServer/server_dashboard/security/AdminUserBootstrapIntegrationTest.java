package com.homeServer.server_dashboard.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.repository.DashboardUserRepository;

/**
 * O admin definido por variavel de ambiente precisa continuar existindo depois que os usuarios
 * foram para o banco — e' o que mantem os deploys atuais funcionando sem nenhuma migracao manual.
 */
@SpringBootTest
class AdminUserBootstrapIntegrationTest {

    private static final String CONFIGURED_PASSWORD = "test-password";

    @Autowired
    private DashboardUserRepository userRepository;

    @Autowired
    private AdminUserBootstrap adminUserBootstrap;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsTheAdminFromTheEnvironmentOnAnEmptyDatabase() {
        DashboardUser admin = requireAdmin();

        assertTrue(admin.hasRole(DashboardRole.ADMIN));
        assertTrue(admin.isEnabled());
        assertTrue(passwordEncoder.matches(CONFIGURED_PASSWORD, admin.getPasswordHash()));
    }

    @Test
    void startsWithoutTwoFactorSoTheFirstLoginIsNotBlocked() {
        DashboardUser admin = requireAdmin();

        assertEquals(false, admin.isTotpEnabled());
        assertEquals(0, admin.unusedRecoveryCodeCount());
    }

    @Test
    void doesNotDuplicateTheAdminOnASecondBoot() {
        ApplicationArguments noArguments = new DefaultApplicationArguments();
        adminUserBootstrap.run(noArguments);
        adminUserBootstrap.run(noArguments);

        List<DashboardUser> admins = userRepository.findAllByOrderByUsernameAsc().stream()
                .filter(user -> user.getUsername().equals("admin"))
                .toList();
        assertEquals(1, admins.size());
    }

    /**
     * O banco e' a verdade depois do primeiro boot: uma senha trocada na tela de conta nao pode ser
     * desfeita por um restart que ainda carregue a senha antiga no ambiente.
     */
    @Test
    void doesNotOverwriteAPasswordChangedAfterTheFirstBoot() {
        DashboardUser admin = requireAdmin();
        String originalHash = admin.getPasswordHash();
        try {
            admin.setPasswordHash(passwordEncoder.encode("senha-trocada-na-interface"));
            userRepository.save(admin);

            adminUserBootstrap.run(new DefaultApplicationArguments());

            DashboardUser reloaded = requireAdmin();
            assertTrue(passwordEncoder.matches("senha-trocada-na-interface", reloaded.getPasswordHash()));
        } finally {
            // O contexto (e o banco) e' compartilhado pelos cenarios desta classe.
            DashboardUser reloaded = requireAdmin();
            reloaded.setPasswordHash(originalHash);
            userRepository.save(reloaded);
        }
    }

    private DashboardUser requireAdmin() {
        return userRepository.findByUsernameIgnoreCase("admin").orElseThrow();
    }
}
