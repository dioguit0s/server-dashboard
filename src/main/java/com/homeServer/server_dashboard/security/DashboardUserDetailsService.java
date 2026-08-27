package com.homeServer.server_dashboard.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.homeServer.server_dashboard.repository.DashboardUserRepository;

/**
 * Carrega os usuarios do banco, no lugar do {@code InMemoryUserDetailsManager} de usuario unico que
 * existia antes.
 */
@Service
public class DashboardUserDetailsService implements UserDetailsService {

    private final DashboardUserRepository userRepository;

    public DashboardUserDetailsService(DashboardUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByUsernameIgnoreCase(username)
                .map(DashboardUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario nao encontrado: " + username));
    }
}
