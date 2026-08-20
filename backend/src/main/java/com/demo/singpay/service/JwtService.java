package com.demo.singpay.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;

/**
 * Service JWT — génération et validation des access tokens.
 *
 * Algorithme : HS256 (HMAC-SHA256) avec clé ≥ 256 bits (32 octets).
 * La clé doit être générée via : openssl rand -hex 32
 * et stockée dans JWT_SECRET dans le fichier .env.
 *
 * Access token : 15 minutes
 * Refresh token : géré séparément par RefreshTokenService (cookie HttpOnly)
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final String  jwtSecret;
    private final long    accessTokenExpiryMs;

    public JwtService(
            @Value("${app.jwt.secret}")         String jwtSecret,
            @Value("${app.jwt.expiry-ms:900000}") long accessTokenExpiryMs) {
        this.jwtSecret           = jwtSecret;
        this.accessTokenExpiryMs = accessTokenExpiryMs;
    }

    // ── Génération ───────────────────────────────────────────────────────────

    public String generateAccessToken(UserDetails userDetails, Long userId, String role) {
        return buildToken(Map.of("uid", userId, "role", role), userDetails);
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
            .claims(extraClaims)
            .subject(userDetails.getUsername())
            .issuedAt(new Date(now))
            .expiration(new Date(now + accessTokenExpiryMs))
            .signWith(signingKey())
            .compact();
    }

    // ── Validation ───────────────────────────────────────────────────────────

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String subject = extractSubject(token);
            return subject.equals(userDetails.getUsername()) && !isExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT invalide : {}", e.getMessage());
            return false;
        }
    }

    public String extractSubject(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Long extractUserId(String token) {
        Object uid = extractAllClaims(token).get("uid");
        if (uid instanceof Integer) return ((Integer) uid).longValue();
        if (uid instanceof Long)    return (Long) uid;
        return null;
    }

    public String extractRole(String token) {
        return (String) extractAllClaims(token).get("role");
    }

    public long getAccessTokenExpiryMs() { return accessTokenExpiryMs; }

    // ── Internals ────────────────────────────────────────────────────────────

    private boolean isExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(extractAllClaims(token));
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
