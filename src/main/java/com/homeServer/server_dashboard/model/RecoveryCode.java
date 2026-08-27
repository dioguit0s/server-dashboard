package com.homeServer.server_dashboard.model;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Codigo de recuperacao de 2FA, guardado apenas como hash: quem le o banco nao consegue usar o
 * codigo, do mesmo jeito que acontece com a senha.
 *
 * <p>Uso unico — {@link #usedAt} e' preenchido no primeiro resgate e o codigo nunca mais vale.
 */
@Embeddable
public class RecoveryCode {

    @Column(name = "code_hash", nullable = false, length = 100)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    protected RecoveryCode() {
    }

    public RecoveryCode(String codeHash) {
        this.codeHash = codeHash;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public void setCodeHash(String codeHash) {
        this.codeHash = codeHash;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(Instant usedAt) {
        this.usedAt = usedAt;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * Igualdade pelo hash: e' o que identifica o codigo dentro da colecao do usuario.
     */
    @Override
    public boolean equals(Object other) {
        return other instanceof RecoveryCode code && Objects.equals(codeHash, code.codeHash);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(codeHash);
    }
}
