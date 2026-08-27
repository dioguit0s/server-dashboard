package com.homeServer.server_dashboard.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.homeServer.server_dashboard.model.DashboardRole;
import com.homeServer.server_dashboard.model.DashboardUser;

public interface DashboardUserRepository extends JpaRepository<DashboardUser, Long> {

    Optional<DashboardUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<DashboardUser> findAllByOrderByUsernameAsc();

    /**
     * Admins ativos — usado para impedir que o ultimo deles seja removido, desabilitado ou
     * rebaixado, o que deixaria o dashboard sem ninguem capaz de administrar.
     */
    long countByRolesContainingAndEnabledTrue(DashboardRole role);
}
