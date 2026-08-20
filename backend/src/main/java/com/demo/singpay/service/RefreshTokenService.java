package com.demo.singpay.service;

import com.demo.singpay.model.RefreshToken;
import com.demo.singpay.model.User;
import com.demo.singpay.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Gestion des refresh tokens.
 *
 * Sécurité :
 *  - Token brut (32 octets aléatoires) transmis une seule fois via cookie HttpOnly Secure
 *  - Seul le hash SHA-256 du token est stocké en base
 *  - Token à usage unique : toute utilisation d'un token révoqué révoque TOUS les tokens du compte
 *    (détection de vol — RFC 6819 Refresh Token Rotation)
 */
@Service
public class RefreshTokenService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final RefreshTokenRepository repo;
    private final long refreshTokenExpiryDays;

    public RefreshTokenService(
            RefreshTokenRepository repo,
            @Value("${app.jwt.refresh-expiry-days:7}") long refreshTokenExpiryDays) {
        this.repo                  = repo;
        this.refreshTokenExpiryDays = refreshTokenExpiryDays;
    }

    /** Génère un token brut, le hash, et persiste le hash. Retourne le token brut (à envoyer en cookie). */
    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateRawToken();
        String hash     = sha256Hex(rawToken);

        RefreshToken rt = new RefreshToken();
        rt.setUser(user);
        rt.setTokenHash(hash);
        rt.setExpiresAt(LocalDateTime.now().plusDays(refreshTokenExpiryDays));
        rt.setRevoked(false);
        repo.save(rt);

        return rawToken;
    }

    /**
     * Valide un token brut reçu depuis le cookie.
     * Si le token est révoqué, révoque TOUS les tokens du compte (détection de vol).
     */
    @Transactional
    public Optional<User> validateAndRotate(String rawToken) {
        String hash = sha256Hex(rawToken);
        Optional<RefreshToken> opt = repo.findByTokenHash(hash);

        if (opt.isEmpty()) return Optional.empty();

        RefreshToken rt = opt.get();

        if (rt.isRevoked() || rt.isExpired()) {
            if (rt.isRevoked()) {
                // Token révoqué utilisé = signe de vol — invalider toute la session
                repo.revokeAllByUserId(rt.getUser().getId());
            }
            return Optional.empty();
        }

        // Rotation : révoquer l'ancien token
        rt.setRevoked(true);
        repo.save(rt);

        return Optional.of(rt.getUser());
    }

    /**
     * Révoque tous les refresh tokens du compte associé au token brut reçu (logout).
     * Fonctionne même si le token est déjà révoqué ou expiré.
     */
    @Transactional
    public void revokeByRawToken(String rawToken) {
        String hash = sha256Hex(rawToken);
        repo.findByTokenHash(hash).ifPresent(rt ->
            repo.revokeAllByUserId(rt.getUser().getId()));
    }

    /** Révoque tous les refresh tokens d'un utilisateur (logout par userId). */
    @Transactional
    public void revokeAll(Long userId) {
        repo.revokeAllByUserId(userId);
    }

    /** Nettoyage des tokens expirés/révoqués (à appeler périodiquement). */
    @Transactional
    public void purgeExpired() {
        repo.deleteExpiredAndRevoked(LocalDateTime.now());
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 non disponible", e);
        }
    }
}
