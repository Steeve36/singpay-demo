package com.demo.singpay.controller;

import com.demo.singpay.model.SingPayCallback;
import com.demo.singpay.model.SingPayWebhookPayload;
import com.demo.singpay.model.enums.TxnStatus;
import com.demo.singpay.service.PaymentOrchestrator;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Webhooks entrants pour le système multi-passerelles.
 *
 * Note SingPay : le webhook SingPay /webhook/singpay existant reste actif pour
 * la rétrocompatibilité avec l'ancien système (table orders). Ce contrôleur met
 * en plus à jour la table payment_transactions.
 *
 * Stripe : signature vérifiée via Webhook.constructEvent(). Traite
 * checkout.session.completed et checkout.session.expired.
 */
@RestController
@RequestMapping("/api/webhooks")
public class WebhookGatewayController {

    private static final Logger log = LoggerFactory.getLogger(WebhookGatewayController.class);

    private final PaymentOrchestrator orchestrator;
    private final String stripeWebhookSecret;

    public WebhookGatewayController(
            PaymentOrchestrator orchestrator,
            @Value("${stripe.webhook-secret:}") String stripeWebhookSecret) {
        this.orchestrator         = orchestrator;
        this.stripeWebhookSecret  = stripeWebhookSecret;
    }

    /**
     * POST /api/webhooks/singpay
     * Récepteur secondaire pour mettre à jour payment_transactions.
     * SingPay appelle /webhook/singpay (ancien endpoint), ce contrôleur est
     * appelé en interne par WebhookController après traitement de l'Order.
     *
     * Peut aussi être enregistré directement comme webhook SingPay alternatif.
     * Répond toujours HTTP 200 pour éviter les rejeux SingPay.
     */
    @PostMapping("/singpay")
    public ResponseEntity<Void> handleSingPayWebhook(@RequestBody SingPayWebhookPayload payload) {
        if (payload == null || payload.getTransaction() == null) {
            return ResponseEntity.ok().build();
        }

        SingPayCallback callback = payload.getTransaction();
        String reference = callback.getReference();

        if (reference == null || reference.isBlank()) {
            return ResponseEntity.ok().build();
        }
        if (!"Terminate".equals(callback.getStatus())) {
            return ResponseEntity.ok().build();
        }

        try {
            TxnStatus newStatus;
            String failureReason = null;

            if ("Success".equals(callback.getResult())) {
                newStatus = TxnStatus.SUCCESS;
            } else {
                newStatus = TxnStatus.FAILED;
                failureReason = callback.getResult();
            }

            orchestrator.applyWebhookUpdate(
                reference,
                newStatus,
                callback.getAirtelMoneyId(),
                failureReason
            );
        } catch (Exception e) {
            log.error("Erreur lors de la mise à jour PaymentTransaction pour {}: {}",
                      reference, e.getMessage());
        }

        return ResponseEntity.ok().build();
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
            log.warn("Stripe webhook reçu mais STRIPE_WEBHOOK_SECRET non configuré — ignoré");
            return ResponseEntity.ok().build();
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
            default -> log.debug("Événement Stripe ignoré : {}", event.getType());
        }

        return ResponseEntity.ok().build();
    }
}
