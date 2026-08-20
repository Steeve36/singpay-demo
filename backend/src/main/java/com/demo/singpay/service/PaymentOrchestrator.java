package com.demo.singpay.service;

import com.demo.singpay.dto.CreateIntentRequest;
import com.demo.singpay.dto.CreateIntentResponse;
import com.demo.singpay.dto.PaymentInitRequest;
import com.demo.singpay.dto.PaymentInitResponse;
import com.demo.singpay.dto.PaymentStatusResponse;
import com.demo.singpay.exception.DuplicateTransactionException;
import com.demo.singpay.exception.PaymentException;
import com.demo.singpay.model.PaymentTransaction;
import com.demo.singpay.model.Product;
import org.springframework.security.access.AccessDeniedException;
import com.demo.singpay.model.enums.PaymentMethod;
import com.demo.singpay.model.enums.PaymentProviderEnum;
import com.demo.singpay.model.enums.TxnStatus;
import com.demo.singpay.provider.PaymentProviderPort;
import com.demo.singpay.provider.StripeProvider;
import com.demo.singpay.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class PaymentOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrchestrator.class);
    private static final DateTimeFormatter AUDIT_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private final PaymentRouter router;
    private final PaymentTransactionService txnService;
    private final StripeProvider stripeProvider;
    private final ProductRepository productRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PaymentOrchestrator(PaymentRouter router,
                                PaymentTransactionService txnService,
                                StripeProvider stripeProvider,
                                ProductRepository productRepo) {
        this.router         = router;
        this.txnService     = txnService;
        this.stripeProvider = stripeProvider;
        this.productRepo    = productRepo;
    }

    /**
     * Valide que le montant de la requête correspond au prix catalogue côté serveur.
     * Empêche le price tampering (payer 1 FCFA pour un produit à 50 000 FCFA).
     */
    private void validateAmountAgainstCatalog(String productSlug, Integer requestedAmount, String currency) {
        if (productSlug == null || productSlug.isBlank()) {
            throw new PaymentException("Identifiant produit requis pour la validation du prix.");
        }
        Product product = productRepo.findBySlugAndActiveTrue(productSlug)
            .orElseThrow(() -> new PaymentException("Produit introuvable ou inactif : " + productSlug));

        if (!product.getCurrency().equalsIgnoreCase(currency)) {
            throw new PaymentException("Devise incohérente pour le produit " + productSlug);
        }
        if (!product.getPriceXaf().equals(requestedAmount)) {
            log.error("PRICE TAMPERING DÉTECTÉ — produit: {}, prix attendu: {}, reçu: {}",
                      productSlug, product.getPriceXaf(), requestedAmount);
            throw new PaymentException("Montant invalide pour le produit " + productSlug + ".");
        }
    }

    /**
     * Initie un paiement : idempotence, persistance, délégation au provider.
     * Chaque étape DB utilise REQUIRES_NEW via txnService pour committer avant
     * l'appel HTTP au provider.
     */
    public PaymentInitResponse initiate(PaymentInitRequest request, Long userId) {
        // ── Idempotence ────────────────────────────────────────────────────────
        txnService.findByIdempotencyKey(request.getIdempotencyKey())
            .ifPresent(existing -> {
                if (existing.getStatus() == TxnStatus.PENDING
                        || existing.getStatus() == TxnStatus.PROCESSING) {
                    log.info("Clé d'idempotence {} déjà en cours (statut: {}), réponse réutilisée",
                             request.getIdempotencyKey(), existing.getStatus());
                    throw new DuplicateTransactionException(request.getIdempotencyKey());
                }
            });

        // ── Validation montant ─────────────────────────────────────────────────
        if (request.getAmount() == null || request.getAmount() < 1) {
            throw new PaymentException("Montant invalide : " + request.getAmount());
        }
        validateAmountAgainstCatalog(request.getProductSlug(), request.getAmount(), request.getCurrency());

        // ── Routing ────────────────────────────────────────────────────────────
        PaymentProviderPort provider = router.route(request.getMethod());

        // ── Persistance PENDING (commit immédiat via REQUIRES_NEW) ─────────────
        PaymentTransaction txn = new PaymentTransaction();
        txn.setIdempotencyKey(request.getIdempotencyKey());
        txn.setOrderReference(request.getOrderReference());
        txn.setMethod(request.getMethod());
        txn.setProvider(provider.getProviderName());
        txn.setStatus(TxnStatus.PENDING);
        txn.setAmount(request.getAmount());
        txn.setCurrency(request.getCurrency());
        txn.setCustomerName(request.getCustomerName());
        txn.setCustomerEmail(request.getCustomerEmail());
        txn.setUserId(userId);
        txn.setAuditLog(auditEntry("PENDING", "Transaction créée"));
        txn = txnService.savePending(txn);
        request.setInternalTxnId(txn.getId());

        log.info("PaymentTransaction #{} créée — méthode: {}, provider: {}, montant: {} {}",
                 txn.getId(), txn.getMethod(), txn.getProvider(),
                 txn.getAmount(), txn.getCurrency());

        // ── Appel provider (hors transaction DB) ──────────────────────────────
        try {
            PaymentInitResponse response = provider.initiate(request);

            appendAudit(txn, "PROCESSING", "Initié par " + provider.getProviderName());
            txnService.updateProcessing(txn.getId(), response.getProviderRef(), txn.getAuditLog());

            log.info("Transaction #{} en cours — providerRef: {}",
                     txn.getId(), response.getProviderRef());
            return response;

        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            appendAudit(txn, "FAILED", reason);
            txnService.updateFailed(txn.getId(), reason, txn.getAuditLog());
            log.error("Échec initiation transaction #{}: {}", txn.getId(), reason);
            throw e;
        }
    }

    /** Retourne le statut courant d'une transaction depuis la DB, avec vérification du propriétaire. */
    @Transactional(readOnly = true)
    public PaymentStatusResponse getStatus(Long transactionId, Long userId) {
        if (transactionId == null) {
            throw new PaymentException("transactionId requis");
        }
        PaymentTransaction txn = txnService.findById(transactionId)
            .orElseThrow(() -> new PaymentException("Transaction introuvable : " + transactionId));
        if (txn.getUserId() != null && !txn.getUserId().equals(userId)) {
            throw new AccessDeniedException("Accès refusé à la transaction " + transactionId);
        }
        return PaymentStatusResponse.from(txn);
    }

    /**
     * Vérifie en live le statut d'un paiement auprès du provider et met à jour la DB.
     * Utilisé par l'endpoint /verify, notamment au retour de Stripe Checkout.
     * L'abstraction PaymentProviderPort est respectée : swapper le provider CB ne
     * nécessite aucune modification ici.
     */
    public PaymentStatusResponse verifyAndUpdate(Long transactionId, Long userId) {
        PaymentTransaction txn = txnService.findById(transactionId)
            .orElseThrow(() -> new PaymentException("Transaction introuvable : " + transactionId));
        if (txn.getUserId() != null && !txn.getUserId().equals(userId)) {
            throw new AccessDeniedException("Accès refusé à la transaction " + transactionId);
        }

        if (txn.getStatus() != TxnStatus.PENDING && txn.getStatus() != TxnStatus.PROCESSING) {
            return PaymentStatusResponse.from(txn);
        }

        PaymentProviderPort provider = router.route(txn.getMethod());
        PaymentStatusResponse live = provider.getStatus(transactionId, txn.getProviderRef());

        if (live.getStatus() == TxnStatus.SUCCESS) {
            appendAudit(txn, "SUCCESS", "Confirmé par " + provider.getProviderName());
            txnService.updateSuccess(txn.getId(), live.getProviderRef(), txn.getAuditLog());
            txn.setStatus(TxnStatus.SUCCESS);
        } else if (live.getStatus() == TxnStatus.FAILED) {
            String reason = live.getFailureReason();
            appendAudit(txn, "FAILED", reason);
            txnService.updateFailed(txn.getId(), reason, txn.getAuditLog());
            txn.setStatus(TxnStatus.FAILED);
            txn.setFailureReason(reason);
        }

        return PaymentStatusResponse.from(txn);
    }

    /**
     * Crée un Stripe PaymentIntent pour le checkout intégré (Stripe Elements).
     * Idempotent via idempotencyKey. Retourne le client_secret au frontend.
     */
    public CreateIntentResponse createStripeIntent(CreateIntentRequest request, Long userId) {
        // ── Idempotence ────────────────────────────────────────────────────────
        txnService.findByIdempotencyKey(request.getIdempotencyKey())
            .ifPresent(existing -> {
                if (existing.getStatus() == TxnStatus.PENDING
                        || existing.getStatus() == TxnStatus.PROCESSING) {
                    log.info("Clé d'idempotence {} déjà en cours (statut: {}), refusé",
                             request.getIdempotencyKey(), existing.getStatus());
                    throw new DuplicateTransactionException(request.getIdempotencyKey());
                }
            });

        // ── Validation montant ─────────────────────────────────────────────────
        if (request.getAmount() == null || request.getAmount() < 1) {
            throw new PaymentException("Montant invalide : " + request.getAmount());
        }
        validateAmountAgainstCatalog(request.getProductSlug(), request.getAmount(), request.getCurrency());

        // ── Persistance PENDING ────────────────────────────────────────────────
        PaymentTransaction txn = new PaymentTransaction();
        txn.setIdempotencyKey(request.getIdempotencyKey());
        txn.setOrderReference(request.getOrderReference());
        txn.setMethod(PaymentMethod.CB);
        txn.setProvider(PaymentProviderEnum.STRIPE);
        txn.setStatus(TxnStatus.PENDING);
        txn.setAmount(request.getAmount());
        txn.setCurrency(request.getCurrency());
        txn.setCustomerName(request.getCustomerName());
        txn.setCustomerEmail(request.getCustomerEmail());
        txn.setUserId(userId);
        txn.setAuditLog(auditEntry("PENDING", "PaymentIntent créé"));
        txn = txnService.savePending(txn);

        log.info("PaymentTransaction #{} créée pour PaymentIntent — ref: {}, montant: {} {}",
                 txn.getId(), txn.getOrderReference(), txn.getAmount(), txn.getCurrency());

        // ── Appel Stripe ───────────────────────────────────────────────────────
        try {
            CreateIntentResponse response = stripeProvider.createPaymentIntent(request, txn.getId());

            // Extraire le PI ID depuis le client_secret (format: pi_xxx_secret_yyy)
            String piId = response.getClientSecret().split("_secret_")[0];
            appendAudit(txn, "PROCESSING", "PaymentIntent Stripe créé");
            txnService.updateProcessing(txn.getId(), piId, txn.getAuditLog());

            return response;

        } catch (Exception e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            appendAudit(txn, "FAILED", reason);
            txnService.updateFailed(txn.getId(), reason, txn.getAuditLog());
            log.error("Échec création PaymentIntent #{}: {}", txn.getId(), reason);
            throw e;
        }
    }

    /**
     * Met à jour le statut d'une transaction suite à un webhook entrant (SingPay — par référence).
     */
    public void applyWebhookUpdate(String orderReference, TxnStatus newStatus,
                                    String providerRef, String failureReason) {
        txnService.applyWebhook(orderReference, newStatus, providerRef, failureReason,
            buildAuditEntry(newStatus.name(), "Webhook reçu"));
        log.info("Transaction {} mise à jour via webhook : {}", orderReference, newStatus);
    }

    /**
     * Marque une transaction comme SUCCESS suite à un webhook Stripe (par txnId).
     */
    public void applyProviderSuccess(Long txnId, String providerRef) {
        txnService.findById(txnId).ifPresentOrElse(txn -> {
            if (txn.getStatus() == TxnStatus.SUCCESS
                    || txn.getStatus() == TxnStatus.FAILED
                    || txn.getStatus() == TxnStatus.REFUNDED) {
                log.info("Transaction #{} déjà en état terminal ({}) — webhook Stripe ignoré",
                         txnId, txn.getStatus());
                return;
            }
            appendAudit(txn, "SUCCESS", "Confirmé via webhook Stripe");
            txnService.updateSuccess(txnId, providerRef, txn.getAuditLog());
        }, () -> log.warn("applyProviderSuccess: transaction #{} introuvable", txnId));
    }

    /**
     * Marque une transaction comme FAILED suite à un webhook Stripe (par txnId).
     */
    public void applyProviderFailed(Long txnId, String providerRef, String reason) {
        txnService.findById(txnId).ifPresentOrElse(txn -> {
            if (txn.getStatus() == TxnStatus.SUCCESS
                    || txn.getStatus() == TxnStatus.FAILED
                    || txn.getStatus() == TxnStatus.REFUNDED) {
                log.info("Transaction #{} déjà en état terminal ({}) — webhook Stripe ignoré",
                         txnId, txn.getStatus());
                return;
            }
            appendAudit(txn, "FAILED", reason);
            txnService.updateFailed(txnId, reason, txn.getAuditLog());
        }, () -> log.warn("applyProviderFailed: transaction #{} introuvable", txnId));
    }

    // ── Helpers audit (Jackson — injection-safe) ───────────────────────────────

    private String auditEntry(String status, String note) {
        try {
            ArrayNode arr = objectMapper.createArrayNode();
            arr.add(buildEntryNode(status, note));
            return objectMapper.writeValueAsString(arr);
        } catch (Exception e) {
            return "[]";
        }
    }

    private String buildAuditEntry(String status, String note) {
        try {
            return objectMapper.writeValueAsString(buildEntryNode(status, note));
        } catch (Exception e) {
            return "{}";
        }
    }

    private void appendAudit(PaymentTransaction txn, String status, String note) {
        try {
            String existing = txn.getAuditLog();
            ArrayNode arr = (existing == null || existing.isBlank())
                ? objectMapper.createArrayNode()
                : (ArrayNode) objectMapper.readTree(existing);
            arr.add(buildEntryNode(status, note));
            txn.setAuditLog(objectMapper.writeValueAsString(arr));
        } catch (Exception e) {
            log.warn("Impossible d'ajouter une entrée audit : {}", e.getMessage());
        }
    }

    private ObjectNode buildEntryNode(String status, String note) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("ts",     LocalDateTime.now().format(AUDIT_FMT));
        node.put("status", status);
        node.put("note",   note != null ? note : "");
        return node;
    }
}
