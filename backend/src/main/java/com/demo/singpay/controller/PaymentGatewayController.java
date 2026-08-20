package com.demo.singpay.controller;

import com.demo.singpay.dto.CreateIntentRequest;
import com.demo.singpay.dto.CreateIntentResponse;
import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.exception.DuplicateTransactionException;
import com.demo.singpay.exception.PaymentMethodUnavailableException;
import com.demo.singpay.exception.PaymentProviderUnavailableException;
import com.demo.singpay.model.User;
import com.demo.singpay.service.PaymentOrchestrator;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentGatewayController {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayController.class);

    private final PaymentOrchestrator orchestrator;

    public PaymentGatewayController(PaymentOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/initiate")
    public ResponseEntity<?> initiate(@Valid @RequestBody PaymentInitRequest request,
                                       @AuthenticationPrincipal User currentUser) {
        log.info("Initiation paiement — user: {}, méthode: {}, ref: {}, montant: {} {}",
                 currentUser.getId(), request.getMethod(), request.getOrderReference(),
                 request.getAmount(), request.getCurrency());
        try {
            PaymentInitResponse response = orchestrator.initiate(request, currentUser.getId());
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

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getStatus(@PathVariable Long id,
                                        @AuthenticationPrincipal User currentUser) {
        try {
            PaymentStatusResponse status = orchestrator.getStatus(id, currentUser.getId());
            return ResponseEntity.ok(status);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "FORBIDDEN", "message", "Accès refusé."));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "NOT_FOUND", "message", e.getMessage()));
        }
    }

    @PostMapping("/create-intent")
    public ResponseEntity<?> createIntent(@Valid @RequestBody CreateIntentRequest request,
                                           @AuthenticationPrincipal User currentUser) {
        log.info("Création PaymentIntent — user: {}, ref: {}, montant: {} {}",
                 currentUser.getId(), request.getOrderReference(), request.getAmount(), request.getCurrency());
        try {
            CreateIntentResponse response = orchestrator.createStripeIntent(request, currentUser.getId());
            return ResponseEntity.ok(response);

        } catch (DuplicateTransactionException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "DUPLICATE_TRANSACTION", "message", e.getMessage()));

        } catch (PaymentProviderUnavailableException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "PROVIDER_UNAVAILABLE", "message", e.getMessage()));

        } catch (Exception e) {
            log.error("Erreur création PaymentIntent: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "PAYMENT_ERROR",
                             "message", "Impossible de créer le formulaire de paiement."));
        }
    }

    @GetMapping("/{id}/verify")
    public ResponseEntity<?> verify(@PathVariable Long id,
                                     @AuthenticationPrincipal User currentUser) {
        try {
            PaymentStatusResponse status = orchestrator.verifyAndUpdate(id, currentUser.getId());
            return ResponseEntity.ok(status);
        } catch (AccessDeniedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "FORBIDDEN", "message", "Accès refusé."));
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
