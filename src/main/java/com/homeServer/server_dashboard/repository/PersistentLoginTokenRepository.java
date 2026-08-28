package com.homeServer.server_dashboard.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.homeServer.server_dashboard.model.PersistentLoginToken;

public interface PersistentLoginTokenRepository extends JpaRepository<PersistentLoginToken, String> {

    void deleteByUsername(String username);
}
