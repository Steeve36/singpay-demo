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
import java.util.Map;
import java.util.Optional;

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
     * POST /api/payment/ussd
     * Initie un paiement USSD Push. Angular poll ensuite GET /api/payment/order/{ref}
     * toutes les 5s pour savoir quand la transaction est terminée.
     */
    @PostMapping("/ussd")
    public ResponseEntity<?> initierUssd(@Valid @RequestBody UssdPaymentRequest req) {

        // Idempotence — refuser une référence déjà connue
        if (orderRepo.existsByReference(req.getReference())) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("message", "Référence déjà utilisée: " + req.getReference()));
        }

        // Validation MSISDN selon les préfixes opérateurs au Gabon
        if (!isValidMsisdn(req.getPhone(), req.getOperateur())) {
            return ResponseEntity.badRequest()
                .body(Map.of("message",
                    "Numéro de téléphone invalide pour l'opérateur " + req.getOperateur()));
        }

        // Appel SingPay
        String singpayTxnId;
        try {
            singpayTxnId = singPay.initierPaiementUssd(
                req.getOperateur(), req.getPhone(), req.getAmount(), req.getReference());
        } catch (RuntimeException e) {
            log.error("Échec initiation USSD pour {}: {}", req.getReference(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("message", e.getMessage()));
        }

        // Persister la commande en PENDING
        Order order = new Order();
        order.setReference(req.getReference());
        order.setAmount(req.getAmount());
        order.setOperateur(req.getOperateur().toUpperCase());
        order.setClientMsisdn(req.getPhone());
        order.setCustomerName(req.getCustomerName());
        order.setCustomerEmail(req.getCustomerEmail());
        order.setSingpayTransactionId(singpayTxnId != null ? singpayTxnId : "");
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(order);

        log.info("USSD Push initié — ref: {}, opérateur: {}, singpayTxnId: {}",
                 req.getReference(), req.getOperateur(), singpayTxnId);

        return ResponseEntity.ok(Map.of(
            "reference",     req.getReference(),
            "transactionId", singpayTxnId != null ? singpayTxnId : ""
        ));
    }

    /**
     * GET /api/payment/order/{reference}
     * Retourne les détails d'une commande depuis la base de données locale.
     * Le MSISDN est masqué avant tout retour vers Angular.
     */
    @GetMapping("/order/{reference}")
    public ResponseEntity<?> getOrder(@PathVariable String reference) {
        Optional<Order> opt = orderRepo.findByReference(reference);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Order order = opt.get();
        return ResponseEntity.ok(Map.of(
            "reference",    order.getReference(),
            "status",       order.getStatus(),
            "amount",       order.getAmount(),
            "operateur",    order.getOperateur()      != null ? order.getOperateur()      : "",
            "clientMsisdn", maskMsisdn(order.getClientMsisdn()),
            "airtelMoneyId", order.getAirtelMoneyId() != null ? order.getAirtelMoneyId() : "",
            "customerName",  order.getCustomerName()  != null ? order.getCustomerName()  : "",
            "customerEmail", order.getCustomerEmail() != null ? order.getCustomerEmail() : "",
            "createdAt",    order.getCreatedAt().toString(),
            "updatedAt",    order.getUpdatedAt().toString()
        ));
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

    /**
     * Validation des numéros de téléphone selon les préfixes opérateurs au Gabon.
     * Airtel : 074, 076, 077 + 6 chiffres
     * Moov   : 062, 065, 066 + 6 chiffres
     * Maviance : accepte les deux opérateurs (format générique)
     */
    private boolean isValidMsisdn(String phone, String operateur) {
        if (phone == null || phone.isBlank()) return false;
        String p = phone.replaceAll("[^0-9]", "");
        return switch (operateur.toUpperCase()) {
            case "AIRTEL"   -> p.matches("^(074|076|077)\\d{6}$");
            case "MOOV"     -> p.matches("^(062|065|066)\\d{6}$");
            case "MAVIANCE" -> p.matches("^0[67]\\d{7}$");
            default         -> false;
        };
    }

    /**
     * Masque le numéro mobile : "074001234" → "074****34"
     * Ne jamais retourner le numéro complet à Angular.
     */
    private String maskMsisdn(String msisdn) {
        if (msisdn == null || msisdn.length() < 6) return "***";
        return msisdn.substring(0, 3) + "****" + msisdn.substring(msisdn.length() - 2);
    }
}
