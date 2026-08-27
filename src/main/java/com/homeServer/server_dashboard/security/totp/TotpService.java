package com.homeServer.server_dashboard.security.totp;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.homeServer.server_dashboard.model.DashboardUser;
import com.homeServer.server_dashboard.model.RecoveryCode;

/**
 * Segundo fator do login: enrollment TOTP e codigos de recuperacao.
 *
 * <p>Duas garantias importantes vivem aqui:
 * <ul>
 *   <li><b>anti-replay</b> — um codigo TOTP vale por ate 90 segundos (o passo atual mais a
 *       tolerancia de relogio). Sem controle, quem visse o codigo na tela poderia reusa-lo nessa
 *       janela; por isso o passo aceito fica gravado no usuario e passos menores ou iguais sao
 *       recusados;</li>
 *   <li><b>uso unico dos codigos de recuperacao</b> — cada codigo e' marcado como usado no resgate
 *       e nunca mais vale.</li>
 * </ul>
 */
@Service
public class TotpService {

    private static final Logger log = LoggerFactory.getLogger(TotpService.class);

    private static final String ISSUER = "ServerDash";
    /** 160 bits, o tamanho recomendado pela RFC 4226 para a chave HMAC-SHA1. */
    private static final int SECRET_LENGTH_IN_BYTES = 20;
    /** Um passo para tras e um para frente: cobre relogio dessincronizado sem alargar demais a janela. */
    private static final int ALLOWED_DRIFT_IN_STEPS = 1;

    private static final int RECOVERY_CODE_COUNT = 10;
    private static final int RECOVERY_CODE_GROUPS = 3;
    private static final int RECOVERY_CODE_GROUP_LENGTH = 4;
    /** Sem I, L, O, U, 0 e 1: reduz erro de leitura quando o codigo e' anotado no papel. */
    private static final String RECOVERY_CODE_ALPHABET = "ABCDEFGHJKMNPQRSTVWXYZ23456789";

    private static final Pattern TOTP_CODE_SHAPE = Pattern.compile("^[0-9]{" + TotpGenerator.DIGITS + "}$");

    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Clock clock;

    @Autowired
    public TotpService(PasswordEncoder passwordEncoder) {
        this(passwordEncoder, Clock.systemUTC());
    }

    TotpService(PasswordEncoder passwordEncoder, Clock clock) {
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    /**
     * Novo segredo em Base32, para o QR code e para digitacao manual.
     */
    public String generateSecret() {
        byte[] secret = new byte[SECRET_LENGTH_IN_BYTES];
        secureRandom.nextBytes(secret);
        return Base32.encode(secret);
    }

    /**
     * URI {@code otpauth://} lida pelos aplicativos autenticadores — e' o conteudo do QR code.
     */
    public String buildOtpAuthUri(String username, String secret) {
        String label = encode(ISSUER + ":" + username);
        return "otpauth://totp/" + label
                + "?secret=" + encode(secret)
                + "&issuer=" + encode(ISSUER)
                + "&algorithm=SHA1"
                + "&digits=" + TotpGenerator.DIGITS
                + "&period=" + TotpGenerator.TIME_STEP.toSeconds();
    }

    /**
     * Confere um codigo contra um segredo ainda nao salvo — o passo de confirmacao do enrollment,
     * em que o usuario prova que o autenticador leu o QR corretamente.
     */
    public boolean isValidForSecret(String secret, String submittedCode) {
        return matchTotp(secret, submittedCode) != null;
    }

    /**
     * Verifica o segundo fator no login. Aceita um codigo TOTP ou um codigo de recuperacao ainda
     * nao usado, e ja aplica anti-replay / marcacao de uso no {@code user} recebido.
     *
     * <p>O usuario e' modificado mas nao gravado: quem chama decide a transacao.
     *
     * @return o resultado, para que o chamador possa registrar no log que um codigo de recuperacao
     *         foi gasto
     */
    public SecondFactorResult verifySecondFactor(DashboardUser user, String submittedCode) {
        if (!user.isTotpEnabled()) {
            return SecondFactorResult.REJECTED;
        }

        if (looksLikeTotpCode(submittedCode)) {
            Long matchedStep = matchTotp(user.getTotpSecret(), submittedCode);
            if (matchedStep == null) {
                return SecondFactorResult.REJECTED;
            }
            Long lastUsedStep = user.getLastUsedTimeStep();
            if (lastUsedStep != null && matchedStep <= lastUsedStep) {
                log.warn("[ServerDash] codigo TOTP reapresentado para o usuario '{}' — recusado", user.getUsername());
                return SecondFactorResult.REJECTED;
            }
            user.setLastUsedTimeStep(matchedStep);
            return SecondFactorResult.TOTP;
        }

        return consumeRecoveryCode(user, submittedCode) ? SecondFactorResult.RECOVERY_CODE : SecondFactorResult.REJECTED;
    }

    /**
     * Gera um lote novo de codigos de recuperacao, substituindo os anteriores. Os codigos em texto
     * voltam apenas nesta chamada — o usuario guarda o que ficar salvo apenas em hash.
     */
    public List<String> regenerateRecoveryCodes(DashboardUser user) {
        List<String> plainCodes = new ArrayList<>(RECOVERY_CODE_COUNT);
        List<RecoveryCode> hashedCodes = new ArrayList<>(RECOVERY_CODE_COUNT);

        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            String code = randomRecoveryCode();
            plainCodes.add(code);
            // O hash guarda a forma canonica (sem hifens), para que o resgate funcione com ou sem eles.
            hashedCodes.add(new RecoveryCode(passwordEncoder.encode(normalizeRecoveryCode(code))));
        }

        user.getRecoveryCodes().clear();
        user.getRecoveryCodes().addAll(hashedCodes);
        return plainCodes;
    }

    private boolean consumeRecoveryCode(DashboardUser user, String submittedCode) {
        String normalized = normalizeRecoveryCode(submittedCode);
        if (normalized.isEmpty()) {
            return false;
        }
        for (RecoveryCode recoveryCode : user.getRecoveryCodes()) {
            if (!recoveryCode.isUsed() && passwordEncoder.matches(normalized, recoveryCode.getCodeHash())) {
                recoveryCode.setUsedAt(Instant.now(clock));
                return true;
            }
        }
        return false;
    }

    private Long matchTotp(String secret, String submittedCode) {
        if (secret == null || secret.isBlank()) {
            return null;
        }
        long currentStep = TotpGenerator.timeStepAt(clock.instant().getEpochSecond());
        return TotpGenerator.matchingTimeStep(Base32.decode(secret), submittedCode, currentStep, ALLOWED_DRIFT_IN_STEPS);
    }

    /**
     * Separa os dois formatos antes de gastar tempo: so' um codigo com a forma de TOTP e' testado
     * contra o segredo, e so' o que nao tem essa forma passa pelos BCrypt dos codigos de
     * recuperacao.
     */
    private static boolean looksLikeTotpCode(String submittedCode) {
        return submittedCode != null && TOTP_CODE_SHAPE.matcher(submittedCode.replaceAll("[\\s-]", "")).matches();
    }

    private String randomRecoveryCode() {
        StringBuilder code = new StringBuilder();
        for (int group = 0; group < RECOVERY_CODE_GROUPS; group++) {
            if (group > 0) {
                code.append('-');
            }
            for (int position = 0; position < RECOVERY_CODE_GROUP_LENGTH; position++) {
                code.append(RECOVERY_CODE_ALPHABET.charAt(secureRandom.nextInt(RECOVERY_CODE_ALPHABET.length())));
            }
        }
        return code.toString();
    }

    /**
     * Forma canonica de um codigo de recuperacao: maiusculas, sem hifens nem espacos. E' o que vai
     * para o hash e o que o resgate compara, entao o codigo pode ser digitado como for.
     */
    private static String normalizeRecoveryCode(String submittedCode) {
        return submittedCode == null
                ? ""
                : submittedCode.trim().replaceAll("[\\s-]", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /**
     * Como o segundo fator foi satisfeito — o chamador usa para registrar no log quando um codigo
     * de recuperacao e' gasto.
     */
    public enum SecondFactorResult {
        TOTP,
        RECOVERY_CODE,
        REJECTED;

        public boolean isAccepted() {
            return this != REJECTED;
        }
    }
}
