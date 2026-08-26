package com.homeServer.server_dashboard.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.thymeleaf.extras.springsecurity6.dialect.SpringSecurityDialect;

import com.homeServer.server_dashboard.security.AdminCredentials;
import com.homeServer.server_dashboard.security.LoginAttemptFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${dashboard.admin.username}")
    private String adminUsername;

    @Value("${dashboard.admin.password:}")
    private String adminPassword;

    @Value("${dashboard.admin.password-hash:}")
    private String adminPasswordHash;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, LoginAttemptFilter loginAttemptFilter) throws Exception {
        http
            .addFilterBefore(loginAttemptFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/charts", "/cpu-details", "/disk-details", "/ram-details", "/login", "/home/**", "/css/**", "/js/**", "/ws/**", "/api/metrics/public", "/api/metrics/history", "/favicon.ico", "/error", "/manifest.json", "/sw.js", "/icons/**").permitAll()
                .requestMatchers("/processes", "/services", "/containers", "/logs", "/api/**").authenticated()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .defaultSuccessUrl("/", true)
            )
            .logout(logout -> logout
                .logoutSuccessUrl("/")
            )
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers("/ws/**")
            )
            .headers(headers -> headers
                .frameOptions(frameOptions -> frameOptions.sameOrigin())
            );
        return http.build();
    }

    /**
     * O {@link LoginAttemptFilter} deve rodar apenas dentro da cadeia do Spring Security
     * (depois do CsrfFilter); esta registration desativa o auto-registro de beans Filter feito
     * pelo Boot, que o colocaria tambem na cadeia global do servlet container.
     */
    @Bean
    public FilterRegistrationBean<LoginAttemptFilter> loginAttemptFilterRegistration(LoginAttemptFilter loginAttemptFilter) {
        FilterRegistrationBean<LoginAttemptFilter> registration = new FilterRegistrationBean<>(loginAttemptFilter);
        registration.setEnabled(false);
        return registration;
    }

    /**
     * {@link DelegatingPasswordEncoder} para que o hash armazenado carregue o proprio algoritmo
     * ({@code {bcrypt}$2a$...}) e possa ser trocado no futuro sem mudar codigo. O encoder padrao
     * para {@code matches()} e' BCrypt, cobrindo hashes gerados por ferramentas externas, que vem
     * sem o prefixo.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        DelegatingPasswordEncoder passwordEncoder =
            (DelegatingPasswordEncoder) PasswordEncoderFactories.createDelegatingPasswordEncoder();
        passwordEncoder.setDefaultPasswordEncoderForMatches(new BCryptPasswordEncoder());
        return passwordEncoder;
    }

    @Bean
    public AdminCredentials adminCredentials(PasswordEncoder passwordEncoder) {
        return new AdminCredentials(adminUsername, adminPassword, adminPasswordHash, passwordEncoder);
    }

    @Bean
    public UserDetailsService userDetailsService(AdminCredentials adminCredentials) {
        return new InMemoryUserDetailsManager(
            User.builder()
                .username(adminCredentials.username())
                .password(adminCredentials.encodedPassword())
                .roles("ADMIN")
                .build()
        );
    }

    @Bean
    public SpringSecurityDialect springSecurityDialect() {
        return new SpringSecurityDialect();
    }
}
