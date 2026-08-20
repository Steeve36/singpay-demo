package com.demo.singpay.controller;

import com.demo.singpay.model.SingPayCallback;
import com.demo.singpay.model.SingPayWebhookPayload;
import com.demo.singpay.model.enums.TxnStatus;
import com.demo.singpay.service.PaymentOrchestrator;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Webhooks entrants pour le système multi-passerelles.
 *
 * SingPay  : HMAC-SHA256 sur le corps brut + token secret en fallback.
 * Stripe   : signature vérifiée via Webhook.constructEvent() (HMAC-SHA256).
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookGatewayController {

    private static final Logger log = LoggerFactory.getLogger(WebhookGatewayController.class);

    private final PaymentOrchestrator orchestrator;
    private final String stripeWebhookSecret;
    private final String singpayWebhookToken;    // token simple legacy
    private final String singpayHmacSecret;      // HMAC-SHA256 clé

    public WebhookGatewayController(
            PaymentOrchestrator orchestrator,
            @Value("${stripe.webhook-secret:}")              String stripeWebhookSecret,
            @Value("${singpay.webhook-secret:}")             String singpayWebhookToken,
            @Value("${app.singpay-webhook-hmac-secret:}")    String singpayHmacSecret) {
        this.orchestrator         = orchestrator;
        this.stripeWebhookSecret  = stripeWebhookSecret;
        this.singpayWebhookToken  = singpayWebhookToken;
        this.singpayHmacSecret    = singpayHmacSecret;
    }

    /**
     * POST /api/webhooks/singpay
     *
     * Authentification à deux niveaux (au moins un doit être configuré) :
     *  1. HMAC-SHA256 : header X-SingPay-Signature = HMAC-SHA256(body, SINGPAY_WEBHOOK_HMAC_SECRET)
     *  2. Token secret  : query param ?token= ou header X-SingPay-Token (fallback)
     *
     * Si aucun secret n'est configuré → 503 (ne jamais traiter sans auth).
     */
    @PostMapping("/singpay")
    public ResponseEntity<Void> handleSingPayWebhook(
            @RequestBody String rawBody,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "X-SingPay-Signature", required = false) String hmacHeader,
            @RequestHeader(value = "X-SingPay-Token",     required = false) String tokenHeader) {

        // ── 1. Vérifier qu'au moins un mécanisme d'auth est configuré ─────────
        boolean hmacConfigured  = singpayHmacSecret  != null && !singpayHmacSecret.isBlank();
        boolean tokenConfigured = singpayWebhookToken != null && !singpayWebhookToken.isBlank();

        if (!hmacConfigured && !tokenConfigured) {
            log.error("SingPay webhook reçu mais aucun secret configuré (SINGPAY_WEBHOOK_HMAC_SECRET / SINGPAY_WEBHOOK_SECRET) — rejeté 503");
            return ResponseEntity.status(503).build();
        }

        // ── 2. Vérification HMAC (prioritaire) ───────────────────────────────
        if (hmacConfigured) {
            if (hmacHeader == null || hmacHeader.isBlank()) {
                log.warn("SingPay webhook sans header X-SingPay-Signature — rejeté");
                return ResponseEntity.status(401).build();
            }
            if (!verifyHmacSha256(rawBody, hmacHeader, singpayHmacSecret)) {
                log.warn("SingPay webhook signature HMAC invalide — rejeté");
                return ResponseEntity.status(401).build();
            }
        } else {
            // ── 3. Fallback : token simple (comparaison à temps constant) ────
            String providedToken = tokenHeader != null ? tokenHeader : tokenParam;
            if (!constantTimeEquals(singpayWebhookToken, providedToken)) {
                log.warn("SingPay webhook token invalide — rejeté");
                return ResponseEntity.status(401).build();
            }
        }

        // ── 4. Désérialisation et traitement ─────────────────────────────────
        SingPayWebhookPayload payload;
        try {
            payload = new com.fasterxml.jackson.databind.ObjectMapper()
                .readValue(rawBody, SingPayWebhookPayload.class);
        } catch (Exception e) {
            log.warn("SingPay webhook payload invalide : {}", e.getMessage());
            return ResponseEntity.ok().build();
        }

        if (payload == null || payload.getTransaction() == null) {
            return ResponseEntity.ok().build();
        }

        SingPayCallback callback = payload.getTransaction();
        String reference = callback.getReference();

        if (reference == null || reference.isBlank()) return ResponseEntity.ok().build();
        if (!"Terminate".equals(callback.getStatus()))  return ResponseEntity.ok().build();

        try {
            TxnStatus newStatus;
            String failureReason = null;

            if ("Success".equals(callback.getResult())) {
                newStatus = TxnStatus.SUCCESS;
            } else {
                newStatus    = TxnStatus.FAILED;
                failureReason = callback.getResult();
            }

            orchestrator.applyWebhookUpdate(reference, newStatus,
                                            callback.getAirtelMoneyId(), failureReason);
            log.info("SingPay webhook traité — ref: {}, statut: {}", reference, newStatus);
        } catch (Exception e) {
            log.error("Erreur traitement SingPay webhook pour {}: {}", reference, e.getMessage());
        }

        return ResponseEntity.ok().build();
    }

    // ── Helpers cryptographiques ──────────────────────────────────────────────

    private boolean verifyHmacSha256(String body, String expectedHex, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] computed = mac.doFinal(body.getBytes(StandardCharsets.UTF_8));
            String computedHex = HexFormat.of().formatHex(computed);
            return MessageDigest.isEqual(
                computedHex.getBytes(StandardCharsets.UTF_8),
                expectedHex.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Erreur HMAC : {}", e.getMessage());
            return false;
        }
    }

    /** Comparaison à temps constant — résistante aux timing attacks. */
    private boolean constantTimeEquals(String expected, String provided) {
        if (expected == null || provided == null) return false;
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    /**
     * POST /api/webhooks/stripe
     * Vérifie la signature Stripe puis traite checkout.session.completed / expired.
     * Toujours répondre 200 pour éviter les rejeux Stripe (sauf 400 si signature invalide).
     */
    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (stripeWebhookSecret == null || stripeWebhookSecret.isBlank()) {
            log.error("Stripe webhook reçu mais STRIPE_WEBHOOK_SECRET non configuré — rejeté (500)");
            return ResponseEntity.internalServerError().build();
        }
        if (sigHeader == null || sigHeader.isBlank()) {
            log.warn("Stripe webhook sans en-tête Stripe-Signature — rejeté");
            return ResponseEntity.badRequest().build();
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeWebhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Signature Stripe invalide : {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.info("Stripe webhook vérifié — type: {}, id: {}", event.getType(), event.getId());

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                event.getDataObjectDeserializer()
                    .getObject()
                    .filter(obj -> obj instanceof Session)
                    .map(obj -> (Session) obj)
                    .ifPresent(session -> {
                        String internalIdStr = session.getMetadata().get("internal_txn_id");
                        if (internalIdStr == null) {
                            log.warn("Stripe webhook: internal_txn_id absent des métadonnées");
                            return;
                        }
                        try {
                            Long txnId = Long.parseLong(internalIdStr);
                            orchestrator.applyProviderSuccess(txnId, session.getId());
                            log.info("Transaction #{} marquée SUCCESS via webhook Stripe", txnId);
                        } catch (NumberFormatException ex) {
                            log.error("internal_txn_id non numérique : {}", internalIdStr);
                        }
                    });
            }
            case "checkout.session.expired" -> {
                event.getDataObjectDeserializer()
                    .getObject()
                    .filter(obj -> obj instanceof Session)
                    .map(obj -> (Session) obj)
                    .ifPresent(session -> {
                        String internalIdStr = session.getMetadata().get("internal_txn_id");
                        if (internalIdStr == null) return;
                        try {
                            Long txnId = Long.parseLong(internalIdStr);
                            orchestrator.applyProviderFailed(txnId, session.getId(),
                                "Session Stripe expirée");
                            log.info("Transaction #{} marquée FAILED (session expirée)", txnId);
                        } catch (NumberFormatException ex) {
                            log.error("internal_txn_id non numérique : {}", internalIdStr);
                        }
                    });
            }
            case "payment_intent.succeeded" -> {
                event.getDataObjectDeserializer()
                    .getObject()
                    .filter(obj -> obj instanceof PaymentIntent)
                    .map(obj -> (PaymentIntent) obj)
                    .ifPresent(intent -> {
                        String internalIdStr = intent.getMetadata().get("internal_txn_id");
                        if (internalIdStr == null) {
                            log.warn("Stripe webhook: internal_txn_id absent du PaymentIntent");
                            return;
                        }
                        try {
                            Long txnId = Long.parseLong(internalIdStr);
                            orchestrator.applyProviderSuccess(txnId, intent.getId());
                            log.info("Transaction #{} marquée SUCCESS via webhook PaymentIntent", txnId);
                        } catch (NumberFormatException ex) {
                            log.error("internal_txn_id non numérique : {}", internalIdStr);
                        }
                    });
            }
            case "payment_intent.payment_failed" -> {
                event.getDataObjectDeserializer()
                    .getObject()
                    .filter(obj -> obj instanceof PaymentIntent)
                    .map(obj -> (PaymentIntent) obj)
                    .ifPresent(intent -> {
                        String internalIdStr = intent.getMetadata().get("internal_txn_id");
                        if (internalIdStr == null) return;
                        try {
                            Long txnId = Long.parseLong(internalIdStr);
                            orchestrator.applyProviderFailed(txnId, intent.getId(), "Paiement Stripe échoué");
                            log.info("Transaction #{} marquée FAILED via webhook PaymentIntent", txnId);
                        } catch (NumberFormatException ex) {
                            log.error("internal_txn_id non numérique : {}", internalIdStr);
                        }
                    });
            }
            default -> log.debug("Événement Stripe ignoré : {}", event.getType());
        }

        return ResponseEntity.ok().build();
    }
}
