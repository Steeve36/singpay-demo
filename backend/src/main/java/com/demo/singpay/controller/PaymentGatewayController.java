package com.demo.singpay.controller;

import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.exception.DuplicateTransactionException;
import com.demo.singpay.exception.PaymentMethodUnavailableException;
import com.demo.singpay.exception.PaymentProviderUnavailableException;
import com.demo.singpay.service.PaymentOrchestrator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints du système de paiement multi-passerelles.
 * Ces routes sont distinctes des anciens /api/payment/* qui restent inchangés.
 */
@RestController
@RequestMapping("/api/payments")
public class PaymentGatewayController {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayController.class);

    private final PaymentOrchestrator orchestrator;

    public PaymentGatewayController(PaymentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    /**
     * POST /api/payments/initiate
     * Initie une transaction. Idempotent via idempotencyKey.
     */
    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@Valid @RequestBody PaymentInitRequest request) {
        log.info("Initiation paiement — méthode: {}, ref: {}, montant: {} {}",
                 request.getMethod(), request.getOrderReference(),
                 request.getAmount(), request.getCurrency());
        try {
            PaymentInitResponse response = orchestrator.initiate(request);
            return ResponseEntity.ok(response);

        } catch (DuplicateTransactionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "DUPLICATE_TRANSACTION", "message", e.getMessage()));

        } catch (PaymentMethodUnavailableException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "METHOD_UNAVAILABLE", "message", e.getMessage()));

        } catch (PaymentProviderUnavailableException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "PROVIDER_UNAVAILABLE", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Erreur inattendue lors de l'initiation du paiement: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "PAYMENT_ERROR",
                             "message", "Une erreur est survenue. Veuillez réessayer."));
        }
    }

    /**
     * GET /api/payments/{id}/status
     * Polling du statut d'une transaction par son ID interne (lecture DB).
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long id) {
        try {
            PaymentStatusResponse status = orchestrator.getStatus(id);
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
        }
    }

    /**
     * GET /api/payments/{id}/verify
     * Vérifie le statut en live auprès du provider (ex: Stripe) et met à jour la DB.
     * Appelé par le frontend au retour d'une page de paiement externe (Stripe Checkout).
     */
    @GetMapping("/{id}/verify")
    public ResponseEntity<?> verify(@PathVariable Long id) {
        try {
            PaymentStatusResponse status = orchestrator.verifyAndUpdate(id);
            return ResponseEntity.ok(status);
        } catch (PaymentProviderUnavailableException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "PROVIDER_UNAVAILABLE", "message", e.getMessage()));
        } catch (Exception e) {
            log.warn("Verify échoué pour transaction #{} : {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "VERIFY_FAILED", "message", e.getMessage()));
        }
    }
}
