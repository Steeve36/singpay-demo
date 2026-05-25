package com.demo.singpay.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate limiting par IP — algorithme Token Bucket via Bucket4j.
 *
 * Limites (requêtes / minute) :
 *   - /api/payment/ussd, /api/payments/initiate : 5  (argent réel, strict)
 *   - /api/payment/order/**                     : 60 (polling frontend)
 *   - Tout le reste /api/**                     : 30
 *
 * En production multi-instances, remplacer le ConcurrentHashMap par
 * un backend distribué (Redis via bucket4j-redis).
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    // Un bucket par IP × type de limite
    private final ConcurrentHashMap<String, Bucket> paymentBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> pollingBuckets = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> defaultBuckets = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler) throws Exception {

        String ip   = resolveClientIp(request);
        String path = request.getRequestURI();

        Bucket bucket = selectBucket(ip, path);
        if (bucket.tryConsume(1)) {
            return true;
        }

        log.warn("Rate limit atteint — IP: {}, path: {}", ip, path);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"Trop de requêtes. Veuillez patienter.\"}");
        return false;
    }

    private Bucket selectBucket(String ip, String path) {
        if (isPaymentInitPath(path)) {
            return paymentBuckets.computeIfAbsent(ip, k -> buildBucket(5, Duration.ofMinutes(1)));
        }
        if (path.startsWith("/api/payment/order/")) {
            return pollingBuckets.computeIfAbsent(ip, k -> buildBucket(60, Duration.ofMinutes(1)));
        }
        return defaultBuckets.computeIfAbsent(ip, k -> buildBucket(30, Duration.ofMinutes(1)));
    }

    private boolean isPaymentInitPath(String path) {
        return path.equals("/api/payment/ussd")
            || path.equals("/api/payments/initiate")
            || path.equals("/api/payment/create-link");
    }

    private Bucket buildBucket(int capacity, Duration refillDuration) {
        Bandwidth limit = Bandwidth.builder()
            .capacity(capacity)
            .refillIntervally(capacity, refillDuration)
            .build();
        return Bucket.builder().addLimit(limit).build();
    }

    /**
     * Résout l'IP réelle derrière un reverse proxy (Nginx, Cloudflare…).
     * En production, s'assurer que seul le proxy de confiance peut forger X-Forwarded-For.
     */
    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp;
        }
        return request.getRemoteAddr();
    }
}
