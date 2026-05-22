package com.demo.singpay.controller;

import com.demo.singpay.model.Order;
import com.demo.singpay.model.SingPayCallback;
import com.demo.singpay.model.SingPayWebhookPayload;
import com.demo.singpay.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final OrderRepository orderRepo;

    public WebhookController(OrderRepository orderRepo) {
        this.orderRepo = orderRepo;
    }

    /**
     * POST /webhook/singpay
     *
     * SingPay appelle cet endpoint à chaque changement de statut de la transaction.
     * On met à jour la commande en base quand le statut est Terminate.
     *
     * ⚠️  Toujours répondre HTTP 200, même en cas d'erreur interne,
     *     pour éviter que SingPay rejoue le callback indéfiniment.
     */
    @PostMapping("/singpay")
    public ResponseEntity<Void> handleCallback(@RequestBody SingPayWebhookPayload payload) {
        SingPayCallback callback = payload.getTransaction();

        String reference = callback.getReference();
        String status    = callback.getStatus();
        String result    = callback.getResult();

        log.info("Callback SingPay reçu — ref: {}, status: {}, result: {}",
                 reference, status, result);

        // ── Ignorer les statuts intermédiaires (Start, Partenaire, ...) ──────
        if (!"Terminate".equals(status)) {
            return ResponseEntity.ok().build();
        }

        // ── Chercher la commande ──────────────────────────────────────────────
        Optional<Order> opt = orderRepo.findByReference(reference);
        if (opt.isEmpty()) {
            log.warn("Callback SingPay pour une référence inconnue: {}", reference);
            return ResponseEntity.ok().build(); // répondre 200 quand même
        }

        Order order = opt.get();

        // ── Idempotence : ignorer si déjà traité ──────────────────────────────
        if (!"PENDING".equals(order.getStatus())) {
            log.info("Commande {} déjà traitée (statut: {}), callback ignoré",
                     reference, order.getStatus());
            return ResponseEntity.ok().build();
        }

        // ── Vérification de sécurité : le montant correspond ─────────────────
        if (callback.getAmount() != null) {
            if (!callback.getAmount().equals(order.getAmount())) {
                log.error("ALERTE montant incohérent pour {} — attendu: {}, reçu: {}",
                          reference, order.getAmount(), callback.getAmount());
                order.setStatus("FRAUD_SUSPECTED");
                order.setUpdatedAt(LocalDateTime.now());
                orderRepo.save(order);
                return ResponseEntity.ok().build();
            }
        }

        // ── Mise à jour du statut ─────────────────────────────────────────────
        if ("Success".equals(result)) {
            order.setStatus("PAID");
            order.setAirtelMoneyId(callback.getAirtelMoneyId());
            log.info("Commande {} payée avec succès (ID Airtel: {})",
                     reference, callback.getAirtelMoneyId());

            // TODO: déclencher ici la logique métier post-paiement :
            //   - envoyer un email de confirmation
            //   - activer l'abonnement
            //   - appeler un event bus / service métier

        } else {
            order.setStatus("FAILED_" + result);
            log.warn("Paiement échoué pour la commande {} — raison: {}", reference, result);
        }

        order.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(order);

        return ResponseEntity.ok().build();
    }
}
