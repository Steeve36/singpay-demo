package com.demo.singpay.controller;

import com.demo.singpay.dto.auth.AuthResponse;
import com.demo.singpay.dto.auth.LoginRequest;
import com.demo.singpay.dto.auth.RegisterRequest;
import com.demo.singpay.model.User;
import com.demo.singpay.service.JwtService;
import com.demo.singpay.service.RefreshTokenService;
import com.demo.singpay.service.UserService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;

/**
 * Endpoints d'authentification — tous publics (/api/auth/**).
 *
 * POST /api/auth/register  → créer un compte
 * POST /api/auth/login     → obtenir access token + refresh cookie
 * POST /api/auth/refresh   → renouveler l'access token via refresh cookie
 * POST /api/auth/logout    → révoquer tous les refresh tokens du compte
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String REFRESH_COOKIE = "refresh_token";

    private final AuthenticationManager authManager;
    private final UserService           userService;
    private final JwtService            jwtService;
    private final RefreshTokenService   refreshTokenService;
    private final boolean               secureCookie;
    private final long                  refreshExpiryDays;

    public AuthController(
            AuthenticationManager authManager,
            UserService userService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            @Value("${app.jwt.cookie-secure:true}")    boolean secureCookie,
            @Value("${app.jwt.refresh-expiry-days:7}") long    refreshExpiryDays) {
        this.authManager         = authManager;
        this.userService         = userService;
        this.jwtService          = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.secureCookie        = secureCookie;
        this.refreshExpiryDays   = refreshExpiryDays;
    }

    // ── Register ─────────────────────────────────────────────────────────────

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            userService.register(req.getFirstName(), req.getLastName(),
                                 req.getEmail(), req.getPassword());
        } catch (IllegalArgumentException e) {
            // Email déjà utilisé — ne pas révéler l'existence du compte (anti-énumération)
            log.debug("Tentative d'inscription avec email existant: {}", req.getEmail());
        }
        // Toujours retourner 201 avec le même message pour empêcher l'énumération de comptes
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(Map.of("message",
                "Si cet email n'est pas encore enregistré, votre compte a été créé. Vous pouvez vous connecter."));
    }

    // ── Login ─────────────────────────────────────────────────────────────────

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req,
                                   HttpServletResponse response) {
        try {
            authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    req.getEmail().toLowerCase().trim(), req.getPassword()));
        } catch (BadCredentialsException e) {
            log.warn("Échec de connexion pour : {}", req.getEmail());
            // Message générique — ne pas indiquer si c'est l'email ou le mot de passe
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Email ou mot de passe incorrect."));
        }

        User user = (User) userService.loadUserByUsername(
            req.getEmail().toLowerCase().trim());

        String accessToken  = jwtService.generateAccessToken(user, user.getId(), user.getRole().name());
        String rawRefresh   = refreshTokenService.createRefreshToken(user);

        setRefreshCookie(response, rawRefresh);
        log.info("Connexion réussie — userId: {}", user.getId());

        return ResponseEntity.ok(buildAuthResponse(user, accessToken));
    }

    // ── Refresh ───────────────────────────────────────────────────────────────

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractRefreshCookie(request);
        if (rawToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Session expirée. Veuillez vous reconnecter."));
        }

        Optional<User> userOpt = refreshTokenService.validateAndRotate(rawToken);
        if (userOpt.isEmpty()) {
            clearRefreshCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("message", "Session invalide ou expirée. Veuillez vous reconnecter."));
        }

        User user          = userOpt.get();
        String accessToken = jwtService.generateAccessToken(user, user.getId(), user.getRole().name());
        String newRefresh  = refreshTokenService.createRefreshToken(user);

        setRefreshCookie(response, newRefresh);
        return ResponseEntity.ok(buildAuthResponse(user, accessToken));
    }

    // ── Logout ────────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        String rawToken = extractRefreshCookie(request);
        if (rawToken != null) {
            // Révoquer TOUS les tokens du compte pour invalider toutes les sessions actives
            try {
                refreshTokenService.revokeByRawToken(rawToken);
            } catch (Exception ignored) {}
        }
        clearRefreshCookie(response);
        return ResponseEntity.ok(Map.of("message", "Déconnexion réussie."));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user, String accessToken) {
        return new AuthResponse(
            accessToken,
            jwtService.getAccessTokenExpiryMs() / 1000,
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRole().name()
        );
    }

    private void setRefreshCookie(HttpServletResponse response, String rawToken) {
        Cookie cookie = new Cookie(REFRESH_COOKIE, rawToken);
        cookie.setHttpOnly(true);
        cookie.setSecure(secureCookie);         // false en dev HTTP, true en prod HTTPS
        cookie.setPath("/api/auth/refresh");    // scope minimal : uniquement l'endpoint refresh
        cookie.setMaxAge((int) (refreshExpiryDays * 86400));
        // SameSite=Strict via header — l'API Cookie de Servlet ne l'expose pas directement
        response.addHeader("Set-Cookie",
            buildSetCookieHeader(REFRESH_COOKIE, rawToken, cookie.getMaxAge()));
    }

    private String buildSetCookieHeader(String name, String value, int maxAge) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value).append("; ");
        sb.append("Path=/api/auth/refresh; ");
        sb.append("Max-Age=").append(maxAge).append("; ");
        sb.append("HttpOnly; ");
        if (secureCookie) sb.append("Secure; ");
        sb.append("SameSite=Strict");
        return sb.toString();
    }

    private void clearRefreshCookie(HttpServletResponse response) {
        response.addHeader("Set-Cookie",
            REFRESH_COOKIE + "=; Path=/api/auth/refresh; Max-Age=0; HttpOnly; SameSite=Strict"
                + (secureCookie ? "; Secure" : ""));
    }

    private String extractRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
            .filter(c -> REFRESH_COOKIE.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }
}
