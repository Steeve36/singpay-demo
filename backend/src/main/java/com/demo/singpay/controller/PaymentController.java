package com.demo.singpay.controller;

import com.demo.singpay.model.*;
import com.demo.singpay.repository.OrderRepository;
import com.demo.singpay.service.SingPayService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/payment")
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final SingPayService   singPay;
    private final OrderRepository  orderRepo;

    public PaymentController(SingPayService singPay, OrderRepository orderRepo) {
        this.singPay   = singPay;
        this.orderRepo = orderRepo;
    }

    /**
     * POST /api/payment/create-link
     * Angular appelle cet endpoint pour obtenir le lien de paiement SingPay.
     */
    @PostMapping("/create-link")
    public ResponseEntity<ExtLinkResponse> createLink(
            @Valid @RequestBody CreatePaymentRequest req) {

        // ── Idempotence : on refuse une référence déjà utilisée ──────────────
        if (orderRepo.existsByReference(req.getReference())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Une commande avec cette référence existe déjà : " + req.getReference()
            );
        }

        // ── Validation métier basique ─────────────────────────────────────────
        if (req.getAmount() <= 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Le montant doit être supérieur à 0");
        }

        // ── Appel SingPay /ext ────────────────────────────────────────────────
        ExtLinkResponse singPayLink;
        try {
            singPayLink = singPay.createPaymentLink(req.getReference(), req.getAmount());
        } catch (Exception e) {
            log.error("Erreur SingPay /ext pour la référence {}: {}", req.getReference(), e.getMessage());
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Impossible de joindre SingPay");
        }

        // ── Persister la commande en base ─────────────────────────────────────
        Order order = new Order();
        order.setReference(req.getReference());
        order.setAmount(req.getAmount());
        order.setCustomerName(req.getCustomerName());
        order.setCustomerEmail(req.getCustomerEmail());
        order.setStatus("PENDING");
        order.setPaymentLinkExpiry(singPayLink.getExp());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(order);

        log.info("Commande {} créée — lien SingPay généré (exp: {})",
                 req.getReference(), singPayLink.getExp());

        return ResponseEntity.ok(singPayLink);
    }

    /**
     * GET /api/payment/order/{reference}
     * Retourne les détails d'une commande depuis la base de données locale.
     * Utilisé par les pages de résultat pour afficher le récapitulatif.
     */
    @GetMapping("/order/{reference}")
    public ResponseEntity<Order> getOrder(@PathVariable String reference) {
        return orderRepo.findByReference(reference)
            .map(ResponseEntity::ok)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Commande introuvable : " + reference));
    }

    /**
     * GET /api/payment/status/{reference}
     * Vérifie le statut d'une transaction après la redirection SingPay.
     */
    @GetMapping("/status/{reference}")
    public ResponseEntity<TransactionStatus> getStatus(
            @PathVariable String reference) {

        TransactionStatus status;
        try {
            status = singPay.getTransactionByReference(reference);
        } catch (Exception e) {
            log.error("Erreur lors de la vérification du statut pour {} : {}", reference, e.getMessage());
            throw new ResponseStatusException(
                HttpStatus.BAD_GATEWAY, "Impossible de vérifier le statut");
        }

        return ResponseEntity.ok(status);
    }
}
