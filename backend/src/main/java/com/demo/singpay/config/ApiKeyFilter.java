package com.demo.singpay.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filtre d'authentification par clé API (header X-API-Key).
 *
 * Protège tous les endpoints /api/** sauf :
 *   - /api/webhooks/** (Stripe vérifie sa propre signature)
 *
 * En production : générer une clé forte (ex: openssl rand -hex 32)
 * et la stocker dans APP_API_KEY dans le fichier .env du serveur.
 *
 * Note architecture : dans un vrai système marchand, cette clé est
 * utilisée par le serveur du marchand, pas directement par le frontend.
 * Pour ce démo standalone Angular + Spring Boot, elle est dans le frontend
 * comme une clé de session.
 *
 * @deprecated Remplacé par JwtAuthFilter + Spring Security. Conservé pour référence.
 */
// @Component — désactivé : Spring Security (JwtAuthFilter) gère désormais l'auth sur /api/**
public class ApiKeyFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";

    private final String configuredApiKey;

    public ApiKeyFilter(@Value("${app.api-key:}") String configuredApiKey) {
        this.configuredApiKey = configuredApiKey;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();

        // Passe-droit : requêtes CORS preflight (OPTIONS) — elles n'ont pas de X-API-Key
        if ("OPTIONS".equals(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Passe-droit : webhooks ont leur propre mécanisme d'auth
        if (path.startsWith("/api/webhooks/") || path.startsWith("/webhook/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Passe-droit : si aucune clé configurée (dev sans .env), on laisse passer
        if (configuredApiKey == null || configuredApiKey.isBlank()
                || configuredApiKey.equals("dev-key-change-in-production")) {
            filterChain.doFilter(request, response);
            return;
        }

        String providedKey = request.getHeader(API_KEY_HEADER);
        if (providedKey == null || !configuredApiKey.equals(providedKey)) {
            log.warn("Requête rejetée — clé API invalide ou absente — IP: {}, path: {}",
                     request.getRemoteAddr(), path);
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Clé API invalide ou manquante.\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        // Exclure les routes auth et webhooks — même si ce filtre était réactivé
        return !path.startsWith("/api/")
            || path.startsWith("/api/auth/")
            || path.startsWith("/api/webhooks/");
    }
}
