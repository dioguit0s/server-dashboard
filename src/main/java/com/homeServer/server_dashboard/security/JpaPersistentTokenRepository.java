package com.homeServer.server_dashboard.security;

import java.util.Date;

import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.homeServer.server_dashboard.model.PersistentLoginToken;
import com.homeServer.server_dashboard.repository.PersistentLoginTokenRepository;

/**
 * Ponte entre o {@code PersistentTokenRepository} que o Spring Security espera para o "lembrar de
 * mim" e o armazenamento real, feito em JPA/H2 como o resto do dashboard (em vez do
 * {@code JdbcTokenRepositoryImpl} padrao, que exigiria uma tabela fora do controle do Hibernate).
 */
@Component
public class JpaPersistentTokenRepository implements PersistentTokenRepository {

    private final PersistentLoginTokenRepository repository;

    public JpaPersistentTokenRepository(PersistentLoginTokenRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void createNewToken(PersistentRememberMeToken token) {
        repository.save(new PersistentLoginToken(
                token.getSeries(), token.getUsername(), token.getTokenValue(), token.getDate().toInstant()));
    }

    @Override
    @Transactional
    public void updateToken(String series, String tokenValue, Date lastUsed) {
        repository.findById(series).ifPresent(entity -> {
            entity.setTokenValue(tokenValue);
            entity.setLastUsed(lastUsed.toInstant());
        });
    }

    @Override
    @Transactional(readOnly = true)
    public PersistentRememberMeToken getTokenForSeries(String seriesId) {
        return repository.findById(seriesId)
                .map(entity -> new PersistentRememberMeToken(
                        entity.getUsername(), entity.getSeries(), entity.getTokenValue(), Date.from(entity.getLastUsed())))
                .orElse(null);
    }

    @Override
    @Transactional
    public void removeUserTokens(String username) {
        repository.deleteByUsername(username);
    }
}
